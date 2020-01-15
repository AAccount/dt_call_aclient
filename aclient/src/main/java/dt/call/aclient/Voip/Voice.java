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
	private static final String tag = "Voip.Voice";
	private static final int OORANGE_LIMIT = 100;
	private static final int WAVBUFFERSIZE = Opus.getWavFrameSize();
	private static final int HEADERS = 52;

	private static final int SAMPLES = Opus.getSampleRate();
	private static final int S16 = AudioFormat.ENCODING_PCM_16BIT;
	private static final int STREAMCALL = AudioManager.STREAM_VOICE_CALL;
	private final DecimalFormat decimalFormat = new DecimalFormat("#.###");

	private final StringBuilder statsBuilder = new StringBuilder();
	private String missingLabel, garbageLabel, txLabel, rxLabel, rxSeqLabel, txSeqLabel, skippedLabel, oorangeLabel;
	private int garbage=0, txData=0, rxData=0, rxSeq=0, txSeq=0, skipped=0, oorange=0; //int r/w atomic
	private boolean micMute = false;
	private Thread playbackThread = null, recordThread = null, receiveMonitorThread = null;

	private AudioManager audioManager;

	//reconnect udp variables
	private boolean reconnectionAttempted = false;
	private volatile long lastReceivedTimestamp = 0; //volatile makes r/w atomic
	private int reconnectTries = 0;

	private final Object stopLock = new Object();
	private boolean stopRequested = false;

	private static Voice instance = null;
	public synchronized static void start()
	{
		if(instance == null)
		{
			instance = new Voice();
			instance.startInternal();
		}
		Utils.logcat(Const.LOGE, tag, "Calling Voice.start when an instance is already running");
	}

	public synchronized static void stop()
	{
		if(instance != null)
		{
			instance.stopInternal();
			instance = null;
		}
	}

	public synchronized static void toggleMic()
	{
		if(instance != null)
		{
			instance.toggleMicInternal();
		}
	}

	public static String stats()
	{
		if(instance != null)
		{
			return instance.statsInternal();
		}
		return "";
	}

	private Voice()
	{
		audioManager = (AudioManager) Vars.applicationContext.getSystemService(Context.AUDIO_SERVICE);
		missingLabel = Vars.applicationContext.getString(R.string.call_main_stat_mia);
		txLabel = Vars.applicationContext.getString(R.string.call_main_stat_tx);
		rxLabel = Vars.applicationContext.getString(R.string.call_main_stat_rx);
		garbageLabel = Vars.applicationContext.getString(R.string.call_main_stat_garbage);
		rxSeqLabel = Vars.applicationContext.getString(R.string.call_main_stat_rx_seq);
		txSeqLabel = Vars.applicationContext.getString(R.string.call_main_stat_tx_seq);
		skippedLabel = Vars.applicationContext.getString(R.string.call_main_stat_skipped);
		oorangeLabel = Vars.applicationContext.getString(R.string.call_main_stat_oorange);
	}

	private String statsInternal()
	{
		final String rxDisp=formatInternetMeteric(rxData), txDisp=formatInternetMeteric(txData);
		final int missing = txSeq-rxSeq;
		statsBuilder.setLength(0);
		statsBuilder
				.append(missingLabel).append(": ").append(missing > 0 ? missing : 0).append(" ").append(garbageLabel).append(": ").append(garbage).append("\n")
				.append(rxLabel).append(":").append(rxDisp).append(" ").append(txLabel).append(":").append(txDisp).append("\n")
				.append(rxSeqLabel).append(":").append(rxSeq).append(" ").append(txSeqLabel).append(":").append(txSeq).append("\n")
				.append(skippedLabel).append(":").append(skipped).append(" ").append(oorangeLabel).append(": ").append(oorange);
		return statsBuilder.toString();
	}

	private String formatInternetMeteric(int n)
	{
		final int mega = 1000000;
		final int kilo = 1000;

		if(n > mega)
		{
			return decimalFormat.format((float)n / (float)mega) + "M";
		}
		else if (n > kilo)
		{
			return (n/kilo) + "K";
		}
		else
		{
			return Integer.toString(n);
		}
	}

	private void startInternal()
	{
		//now that the call is ACTUALLY starting put android into communications mode
		//communications mode will prevent the ringtone from playing
		audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
		audioManager.setSpeakerphoneOn(false);

		//initialize the opus library before creating the threads so it will be ready when the threads start
		Opus.init();
		startReceiveMonitorThread();
		startMediaEncodeThread();
		startMediaDecodeThread();
	}

	private void stopInternal()
	{
		//overwrite the voice sodium symmetric key memory contents
		SodiumUtils.applyFiller(Vars.voiceSymmetricKey);
		if(Vars.mediaUdp != null && !Vars.mediaUdp.isClosed())
		{
			Vars.mediaUdp.close();
			Vars.mediaUdp = null;
		}

		if(receiveMonitorThread != null)
		{
			receiveMonitorThread.interrupt();
		}
		receiveMonitorThread = null;

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

	private void startReceiveMonitorThread()
	{
		receiveMonitorThread = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				Utils.logcat(Const.LOGD, tag, "Network receive monitor start");
				while(Vars.state == CallState.INCALL)
				{
					final long A_SECOND = 1000000000L; //usual delay between receives is ~60.2milliseconds

						final long now = System.nanoTime();
						final long btw = now - lastReceivedTimestamp;
						if((lastReceivedTimestamp > 0) && (btw > A_SECOND) && (Vars.mediaUdp != null))
						{
							Utils.logcat(Const.LOGD, tag, "delay since last received more than 1s: " + now+ " " + lastReceivedTimestamp);
							Vars.mediaUdp.close();
						}

					try
					{
						Thread.sleep(1000);
					}
					catch(InterruptedException e)
					{
						break;
					}
				}
				Utils.logcat(Const.LOGD, tag, "Network receive monitor stopInternal");
			}
		});
		receiveMonitorThread.setName("Network receive monitor");
		receiveMonitorThread.start();
	}

	private void startMediaEncodeThread()
	{
		recordThread = new Thread(new Runnable()
		{
			private static final String tag = "EncodingThread";

			private final int STEREO = AudioFormat.CHANNEL_IN_STEREO;
			private final int MIC = MediaRecorder.AudioSource.DEFAULT;

			private final LinkedBlockingQueue<DatagramPacket> sendQ = new LinkedBlockingQueue<>();
			private DatagramPacketPool packetPool = new DatagramPacketPool(Vars.callServer, Vars.mediaPort);

			private final Thread networkThread = new Thread(new Runnable()
			{
				private static final String tag = "EncodeNetwork";

				@Override
				public void run()
				{
					Utils.logcat(Const.LOGD, tag, "encoder network thread started");
					while(Vars.state == CallState.INCALL)
					{
						DatagramPacket packet = null;
						try
						{
							packet = sendQ.take();
							Vars.mediaUdp.send(packet);
						}
						catch(IOException e) //this will happen at the end of a call, no need to reconnect.
						{
							Utils.dumpException(tag, e);
							if(!reconnectUDP())
							{
								stopOnError();
								return;
							}
							sendQ.clear(); //don't bother with the stored voice data
						}
						catch(InterruptedException e)
						{
							break;
						}
						finally
						{
							packetPool.returnDatagramPacket(packet);
						}
					}
					Utils.logcat(Const.LOGD, tag, "encoder network thread stopped");
				}
			});

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
					stopOnError();
					return;
				}

				networkThread.setName("Media_Encoder_Network");
				networkThread.start();

				final byte[] packetBuffer = new byte[Const.SIZE_MAX_UDP];
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
						continue;
					}

					Arrays.fill(packetBuffer, (byte)0);
					final byte[] txSeqDisassembled = Utils.disassembleInt(txSeq);
					System.arraycopy(txSeqDisassembled, 0, packetBuffer, 0, Const.SIZEOF_INT);
					txSeq++;
					System.arraycopy(encodedbuffer, 0 , packetBuffer, Const.SIZEOF_INT, encodeLength);

					final DatagramPacket packet = packetPool.getDatagramPacket();
					final byte[] packetBufferEncrypted = packet.getData();
					final int packetBufferEncryptedLength = SodiumUtils.symmetricEncrypt(packetBuffer, Const.SIZEOF_INT+encodeLength, Vars.voiceSymmetricKey, packetBufferEncrypted);
					if(packetBufferEncryptedLength == 0)
					{
						Utils.logcat(Const.LOGE, tag, "voice symmetric encryption failed");
					}
					else
					{
						packet.setLength(packetBufferEncryptedLength);
						try
						{
							sendQ.put(packet);
						}
						catch(InterruptedException e)
						{
							break;
						}
						txData = txData + packetBufferEncryptedLength + HEADERS;
					}
				}
				SodiumUtils.applyFiller(packetBuffer);
				SodiumUtils.applyFiller(encodedbuffer);
				SodiumUtils.applyFiller(wavbuffer);
				Opus.closeOpus();
				wavRecorder.stop();
				wavRecorder.release();
				networkThread.interrupt();
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
			private static final String tag = "DecodingThread";
			private static final int STEREO = AudioFormat.CHANNEL_OUT_STEREO;

			private final LinkedBlockingQueue<DatagramPacket> receiveQ = new LinkedBlockingQueue<>();
			private DatagramPacketPool packetPool = new DatagramPacketPool();

			private final Thread networkThread = new Thread(new Runnable()
			{
				private static final String tag = "DecodeNetwork";

				@Override
				public void run()
				{
					Utils.logcat(Const.LOGD, tag, "decoder network thread started");
					while(Vars.state == CallState.INCALL)
					{
						DatagramPacket received;
						try
						{
							received = packetPool.getDatagramPacket();
							Vars.mediaUdp.receive(received);
							lastReceivedTimestamp = System.nanoTime();
							receiveQ.put(received);
						}
						catch(SocketTimeoutException e)
						{//to prevent this thread from hanging forever, there is now a udp read timeout during calls
							Utils.dumpException(tag, e);
						}
						catch(InterruptedException | NullPointerException e)
						{
							//can get a null pointer if the connection dies, media decoder dies, but this network thread is still alive
							break;
						}
						catch(IOException e) //this will happen at the end of a call, no need to reconnect.
						{
							Utils.dumpException(tag, e);
							if(!reconnectUDP())
							{
								stopOnError();
								break;
							}
							receiveQ.clear(); //don't bother with the stored voice data
						}
					}
					Utils.logcat(Const.LOGD, tag, "decoder network thread stopped");
				}
			});

			@Override
			public void run()
			{
				Utils.logcat(Const.LOGD, tag, "MediaCodec decoder thread has started");

				final AudioTrack wavPlayer = new AudioTrack(STREAMCALL, SAMPLES, STEREO, S16, WAVBUFFERSIZE, AudioTrack.MODE_STREAM);
				wavPlayer.play();

				networkThread.setName("Media_Decoder_Network");
				networkThread.start();

				final byte[] encbuffer = new byte[WAVBUFFERSIZE];
				final short[] wavbuffer = new short[WAVBUFFERSIZE];
				final byte[] packetDecrypted = new byte[Const.SIZE_MAX_UDP];

				while(Vars.state == CallState.INCALL)
				{
					try
					{
						//read encrypted opus
						Arrays.fill(packetDecrypted, (byte)0);
						final DatagramPacket received = receiveQ.take();

						//decrypt
						rxData = rxData + received.getLength() + HEADERS;
						final int packetDecLength = SodiumUtils.symmetricDecrypt(received.getData(), received.getLength(), Vars.voiceSymmetricKey, packetDecrypted); //contents [seq#|opus chunk]
						packetPool.returnDatagramPacket(received);
						if(packetDecLength == 0)//contents [seq#|opus chunk]
						{
							Utils.logcat(Const.LOGD, tag, "Invalid decryption");
							garbage++;
							continue;
						}

						final byte[] sequenceBytes = new byte[Const.SIZEOF_INT];
						System.arraycopy(packetDecrypted, 0, sequenceBytes, 0, Const.SIZEOF_INT);
						final int sequence = Utils.reassembleInt(sequenceBytes);
						if(sequence <= rxSeq)
						{
							skipped++;
							continue;
						}

						//out of range receive sequences have happened before. still unexplained. log it as a stat
						if(Math.abs(sequence - rxSeq) > OORANGE_LIMIT)
						{
							oorange++;
						}
						else
						{
							rxSeq = sequence;
						}

						//extract the opus chunk
						Arrays.fill(encbuffer, (byte)0);
						final int encodedLength = packetDecLength - Const.SIZEOF_INT;
						System.arraycopy(packetDecrypted, Const.SIZEOF_INT, encbuffer, 0, encodedLength);

						//decode opus chunk
						Arrays.fill(wavbuffer, (short)0);
						final int frames = Opus.decode(encbuffer, encodedLength, wavbuffer);
						if(frames < 1)
						{
							Utils.logcat(Const.LOGE, tag, Opus.getError(frames));
							continue;
						}

						wavPlayer.write(wavbuffer, 0, WAVBUFFERSIZE);
					}
					catch(Exception i)
					{
						Utils.dumpException(tag, i);
					}
				}
				SodiumUtils.applyFiller(packetDecrypted);
				SodiumUtils.applyFiller(encbuffer);
				SodiumUtils.applyFiller(wavbuffer);
				wavPlayer.pause();
				wavPlayer.flush();
				wavPlayer.stop();
				wavPlayer.release();
				Opus.closeOpus();
				networkThread.interrupt();
				Utils.logcat(Const.LOGD, tag, "MediaCodec decoder thread has stopped, state:" + Vars.state);
			}
		});
		playbackThread.setName("Media_Decoder");
		playbackThread.start();
	}

	private synchronized boolean reconnectUDP()
	{
		if(Vars.state == CallState.INCALL)
		{
			final int MAX_UDP_RECONNECTS = 10;
			if(reconnectTries > MAX_UDP_RECONNECTS)
			{
				return false;
			}

			if(reconnectionAttempted)
			{
				reconnectionAttempted = false;
				return true;
			}
			else
			{
				reconnectTries++;
				boolean reconnected = CmdListener.registerVoiceUDP();
				reconnectionAttempted = true;
				return reconnected;
			}
		}
		return false;
	}

	private void toggleMicInternal()
	{
		micMute = !micMute;
		final Intent micChange = new Intent(Const.BROADCAST_CALL);
		micChange.putExtra(Const.BROADCAST_CALL_MIC, Boolean.toString(micMute));
		Vars.applicationContext.sendBroadcast(micChange);
	}

	private void stopOnError()
	{
		synchronized(stopLock)
		{
			if(!stopRequested)
			{
				stopRequested = true;
				final Intent callEnd = new Intent(Const.BROADCAST_CALL);
				callEnd.putExtra(Const.BROADCAST_CALL_RESP, Const.BROADCAST_CALL_END);
				Vars.applicationContext.sendBroadcast(callEnd);
			}
		}
		stopInternal();
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
