package dt.call.aclient.Voip;
/**
 * Created by Daniel on 12/22/19.
 *
 */
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.SocketTimeoutException;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;

import dt.call.aclient.CallState;
import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.background.CmdListener;
import dt.call.aclient.codec.Opus;
import dt.call.aclient.pool.DatagramPacketPool;
import dt.call.aclient.sodium.SodiumUtils;

public class Voice
{
	private static final String tag = "Voice";

	private static final int WAVBUFFERSIZE = Opus.getWavFrameSize();
	private static final int SAMPLES = Opus.getSampleRate();
	private static final int S16 = AudioFormat.ENCODING_PCM_16BIT;
	private static final int STREAMCALL = AudioManager.STREAM_VOICE_CALL;

	private boolean micMute = false;
	private Thread playbackThread = null, recordThread = null;

	private AudioManager audioManager;

	private SodiumUDP sodiumUDP;

	private static Voice instance = null;
	public static Voice getInstance()
	{
		if(instance == null)
		{
			instance = new Voice();
		}
		return instance;
	}

	private Voice()
	{
		audioManager = (AudioManager) Vars.applicationContext.getSystemService(Context.AUDIO_SERVICE);
		sodiumUDP = new SodiumUDP(Vars.serverAddress, Vars.mediaPort);
	}

	public void setVoiceKey(byte[] key)
	{
		sodiumUDP.setVoiceSymmetricKey(key);
	}

	public String stats()
	{
		return sodiumUDP.stats();
	}

	public boolean connect()
	{
		return sodiumUDP.connect();
	}

	public synchronized void start()
	{
		//now that the call is ACTUALLY starting put android into communications mode
		//communications mode will prevent the ringtone from playing
		audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
		audioManager.setSpeakerphoneOn(false);

		sodiumUDP.start();

		//initialize the opus library before creating the threads so it will be ready when the threads start
		Opus.init();
		startMediaEncodeThread();
		startMediaDecodeThread();
	}

	public void stop()
	{
		sodiumUDP.close();
		if(playbackThread != null)
		{
			playbackThread.interrupt();
		}
		playbackThread = null;

		if(recordThread != null)
		{
			recordThread.interrupt();
		}
		recordThread = null;

		audioManager.setMode(AudioManager.MODE_NORMAL);

		instance = null;
	}

	private void startMediaEncodeThread()
	{
		recordThread = new Thread(new Runnable()
		{
			private final int STEREO = AudioFormat.CHANNEL_IN_STEREO;
			private final int MIC = MediaRecorder.AudioSource.DEFAULT;

			@Override
			public void run()
			{
				Utils.logcat(Const.LOGD, tag, "MediaCodec encoder thread has started");

				AudioRecord wavRecorder = new AudioRecord(MIC, SAMPLES, STEREO, S16, WAVBUFFERSIZE);
				wavRecorder.startRecording();

				//my dying i9300 on CM12.1 sometimes can't get the audio record on its first try
				int recorderRetries = 5;
				while(wavRecorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING && recorderRetries > 0)
				{
					wavRecorder.stop();
					wavRecorder.release();
					wavRecorder = new AudioRecord(MIC, SAMPLES, STEREO, S16, WAVBUFFERSIZE);
					wavRecorder.startRecording();
					Utils.logcat(Const.LOGW, tag, "audiorecord failed to initialized. retried");
					recorderRetries--;
				}

				if(recorderRetries == 0)
				{
					Utils.logcat(Const.LOGE, tag, "couldn't get the microphone from the cell phone. hanging up");
					final Intent callEnd = new Intent(Const.BROADCAST_CALL);
					callEnd.putExtra(Const.BROADCAST_CALL_RESP, Const.BROADCAST_CALL_END);
					Vars.applicationContext.sendBroadcast(callEnd);
					stop();
					return;
				}

				final short[] wavbuffer = new short[WAVBUFFERSIZE];
				final byte[] encodedbuffer = new byte[WAVBUFFERSIZE];

				while (Vars.state == CallState.INCALL)
				{
					Arrays.fill(wavbuffer, (short)0);
					int totalRead = 0, dataRead;
					while (totalRead < WAVBUFFERSIZE)
					{
						dataRead = wavRecorder.read(wavbuffer, totalRead, WAVBUFFERSIZE - totalRead);
						totalRead = totalRead + dataRead;
					}

					double recdb = db(wavbuffer);
					if(micMute || recdb < 0)
					{
						//if muting, erase the recorded audio
						//need to record during mute because a cell phone can generate zeros faster than real time talking
						//	so you can't just skip the recording and send placeholder zeros in a loop
						Arrays.fill(wavbuffer, (short)0);
					}

					Arrays.fill(encodedbuffer, (byte)0);
					final int encodeLength = Opus.encode(wavbuffer, encodedbuffer);
					if(encodeLength < 1)
					{
						Utils.logcat(Const.LOGE, tag, Opus.getError(encodeLength));
					}
					sodiumUDP.write(encodedbuffer, encodeLength);
				}
				SodiumUtils.applyFiller(encodedbuffer);
				SodiumUtils.applyFiller(wavbuffer);
				Opus.closeOpus();
				wavRecorder.stop();
				wavRecorder.release();
				Utils.logcat(Const.LOGD, tag, "MediaCodec encoder thread has stopped");
			}
		});
		recordThread.setName("Media_Encoder");
		recordThread.start();
	}

	private void startMediaDecodeThread()
	{
		playbackThread = new Thread(new Runnable()
		{
			private static final int STEREO = AudioFormat.CHANNEL_OUT_STEREO;

			@Override
			public void run()
			{
				Utils.logcat(Const.LOGD, tag, "MediaCodec decoder thread has started");

				final AudioTrack wavPlayer = new AudioTrack(STREAMCALL, SAMPLES, STEREO, S16, WAVBUFFERSIZE, AudioTrack.MODE_STREAM);
				wavPlayer.play();

				final byte[] encbuffer = new byte[WAVBUFFERSIZE];
				final short[] wavbuffer = new short[WAVBUFFERSIZE];

				while(Vars.state == CallState.INCALL)
				{
					Arrays.fill(encbuffer, (byte)0);
					int encodedLength = sodiumUDP.read(encbuffer);
					if(encodedLength < 1)
					{
						continue;
					}
					Arrays.fill(wavbuffer, (short) 0);
					final int frames = Opus.decode(encbuffer, encodedLength, wavbuffer);
					if(frames < 1)
					{
						Utils.logcat(Const.LOGE, tag, Opus.getError(frames));
						continue;
					}

					wavPlayer.write(wavbuffer, 0, WAVBUFFERSIZE);
				}
				SodiumUtils.applyFiller(encbuffer);
				SodiumUtils.applyFiller(wavbuffer);
				wavPlayer.pause();
				wavPlayer.flush();
				wavPlayer.stop();
				wavPlayer.release();
				Opus.closeOpus();
				Utils.logcat(Const.LOGD, tag, "MediaCodec decoder thread has stopped, state:" + Vars.state);
			}
		});
		playbackThread.setName("Media_Decoder");
		playbackThread.start();
	}

	public void toggleMic()
	{
		micMute = !micMute;
		final Intent micChange = new Intent(Const.BROADCAST_CALL);
		micChange.putExtra(Const.BROADCAST_CALL_MIC, Boolean.toString(micMute));
		Vars.applicationContext.sendBroadcast(micChange);
	}

	private double db(short[] sound)
	{
		double sum = 0L;
		for(short sample : sound)
		{
			double percent = sample / (double)Short.MAX_VALUE;
			sum = sum + (percent*percent);
		}
		double rms = Math.sqrt(sum);
		double db = 20L*Math.log10(rms);
//		Log.d("db", "db " + db);
		return db;
	}
}
