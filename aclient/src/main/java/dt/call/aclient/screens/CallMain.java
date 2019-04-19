package dt.call.aclient.screens;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.Vibrator;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.SocketTimeoutException;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;

import dt.call.aclient.CallState;
import dt.call.aclient.Const;
import dt.call.aclient.background.CmdListener;
import dt.call.aclient.background.async.CommandAcceptAsync;
import dt.call.aclient.pool.DatagramPacketPool;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.background.async.CommandEndAsync;
import dt.call.aclient.codec.Opus;
import dt.call.aclient.sodium.SodiumUtils;

public class CallMain extends AppCompatActivity implements View.OnClickListener, SensorEventListener
{
	private static final String WAKELOCK_INCALLA9 = "dt.call.aclient:incalla9";
	private static final String tag = "CallMain";
	public static final String DIALING_MODE = "DIALING_MODE";

	private static final int HEADERS = 52;

	private static final int SAMPLES = Opus.getSampleRate();
	private static final int S16 = AudioFormat.ENCODING_PCM_16BIT;
	private static final int STREAMCALL = AudioManager.STREAM_VOICE_CALL;

	private static final int WAVBUFFERSIZE = Opus.getWavFrameSize();
	private static final int DIAL_TONE_SIZE = 32000;
	private static final int END_TONE_SIZE = 10858;
	private static final int WAV_FILE_HEADER = 44; //.wav files actually have a 44 byte header

	private static final int OORANGE_LIMIT = 100;

	//ui stuff
	private FloatingActionButton end, mic, speaker, accept;
	private Button stats;
	private volatile boolean micMute = false;
	private boolean micStatusNew = false;
	private boolean onSpeaker = false;
	private boolean screenShowing;
	private ImageView userImage;
	private TextView status, time, callerid;
	private int min=0, sec=0;
	private Timer counter = new Timer();
	private BroadcastReceiver myReceiver;
	private int garbage=0, txData=0, rxData=0, rxSeq=0, txSeq=0, skipped=0, oorange=0;
	private String missingLabel, garbageLabel, txLabel, rxLabel, rxSeqLabel, txSeqLabel, skippedLabel, oorangeLabel;
	private boolean showStats = false;
	private boolean isDialing;

	private Thread playbackThread = null, recordThread = null;

	private SensorManager sensorManager;
	private Sensor proximity = null;

	private AudioManager audioManager;

	private AudioTrack dialTone = null;
	private boolean playedEndTone=false;

	private final DecimalFormat decimalFormat = new DecimalFormat("#.###");
	private final StringBuilder statsBuilder = new StringBuilder();
	private final StringBuilder timeBuilder = new StringBuilder();

	//reconnect udp variables
	private boolean reconnectionAttempted = false;
	private long lastReceivedTimestamp = System.currentTimeMillis();
	private final Object rxtsLock = new Object();
	private int reconenctTries = 0;

	private Ringtone ringtone = null;
	private Vibrator vibrator = null;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_call_main);

		isDialing = getIntent().getBooleanExtra(DIALING_MODE, true);

		//allow this screen to show even when there is a password/pattern lock screen
		Window window = this.getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
		window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
		window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

		//cell phone now awake. release the wakelock
		if(Vars.incomingCallLock != null)
		{
			Vars.incomingCallLock.release();
			Vars.incomingCallLock = null;
		}

		end = findViewById(R.id.call_main_end_call);
		end.setOnClickListener(this);
		mic = findViewById(R.id.call_main_mic);
		mic.setOnClickListener(this);
		mic.setEnabled(false);
		speaker = findViewById(R.id.call_main_spk);
		speaker.setOnClickListener(this);
		speaker.setEnabled(false);
		accept = findViewById(R.id.call_main_accept);
		accept.setOnClickListener(this);
		accept.setEnabled(!isDialing);
		stats = findViewById(R.id.call_main_stats);
		stats.setOnClickListener(this);
		status = findViewById(R.id.call_main_status); //by default ringing. change it when in a call
		callerid = findViewById(R.id.call_main_callerid);
		callerid.setText(Utils.getCallerID(Vars.callWith));
		time = findViewById(R.id.call_main_time);
		userImage = findViewById(R.id.call_main_user_image);

		sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		proximity = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY) : null;

		TimerTask counterTask = new TimerTask()
		{
			@Override
			public void run()
			{

				//if the person hasn't answered after 20 seconds give up. it's probably not going to happen.
				if((Vars.state == CallState.INIT) && (sec == Const.CALL_TIMEOUT))
				{
					new CommandEndAsync().execute();
					onStopWrapper();
				}

				if(Vars.state == CallState.INCALL)
				{
					synchronized(rxtsLock)
					{
						final long A_SECOND = 1000L; //usual delay between receives is ~60.2milliseconds
						final long now = System.currentTimeMillis();
						final long btw = now - lastReceivedTimestamp;
						if(btw > A_SECOND && Vars.mediaUdp != null)
						{
							Utils.logcat(Const.LOGD, tag, "delay since last received more than 1s: " + btw);
							Vars.mediaUdp.close();
						}
					}
				}

				updateTime();
				updateStats();
			}
		};
		counter.schedule(counterTask, 0, 1000);

		missingLabel = getString(R.string.call_main_stat_mia);
		txLabel = getString(R.string.call_main_stat_tx);
		rxLabel = getString(R.string.call_main_stat_rx);
		garbageLabel = getString(R.string.call_main_stat_garbage);
		rxSeqLabel = getString(R.string.call_main_stat_rx_seq);
		txSeqLabel = getString(R.string.call_main_stat_tx_seq);
		skippedLabel = getString(R.string.call_main_stat_skipped);
		oorangeLabel = getString(R.string.call_main_stat_oorange);

		myReceiver = new BroadcastReceiver()
		{
			@Override
			public void onReceive(Context context, Intent intent)
			{
				String response = intent.getStringExtra(Const.BROADCAST_CALL_RESP);
				if(response.equals(Const.BROADCAST_CALL_START))
				{
					min = sec = 0;
					changeToCallMode();
				}
				else if(response.equals(Const.BROADCAST_CALL_END))
				{
					//whether the call was rejected or time to end, it's the same result
					//so share the same variable to avoid 2 sendBroadcast chunks of code that are almost the same

					//media read/write are stopped in command listener when it got the call end
					//Vars.state would've already been set by the server command that's broadcasting a call end
					onStopWrapper();
				}
			}
		};
		registerReceiver(myReceiver, new IntentFilter(Const.BROADCAST_CALL));

		audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		if(isDialing && Vars.state == CallState.INIT)
		{
			audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
			audioManager.setSpeakerphoneOn(false);
			byte[] dialToneDump = new byte[DIAL_TONE_SIZE]; //right click the file and get the exact size
			try
			{
				final InputStream dialToneStream = getResources().openRawResource(R.raw.dialtone8000);
				final int amount = dialToneStream.read(dialToneDump);
				final int actualSize = amount-WAV_FILE_HEADER;
				dialToneStream.close();

				//only create the dial tone audio track if it's needed. otherwise it leaks resources if you create but never use it
				dialTone = new AudioTrack(STREAMCALL, 8000, AudioFormat.CHANNEL_OUT_MONO, S16, DIAL_TONE_SIZE, AudioTrack.MODE_STATIC);
				dialTone.write(dialToneDump, WAV_FILE_HEADER, actualSize);
				dialTone.setLoopPoints(0, actualSize/2, -1);
				dialTone.play();
			}
			catch(Exception e)
			{
				Utils.dumpException(tag, e);
			}
		}

		//android 9.0 (and probably newer) has a stupid "power saving feature" where sending data
		//	is heavily throttled when the screen is off. No workaround exists whether using JNI to
		//	send data or using a foreground service.
		//ABSOLUTELY MUST keep the screen on while talking to keep the conversation going
		//https://issuetracker.google.com/issues/115563758
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
		{
			final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
			Vars.incallA9Workaround = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, WAKELOCK_INCALLA9);
			Vars.incallA9Workaround.acquire();

			//some kind of indication that the battery killing android 9 workaround is being used
			status.setTextColor(ContextCompat.getColor(this, R.color.material_yellow));
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		screenShowing = true;
		if(proximity != null)
		{
			sensorManager.registerListener(this, proximity, SensorManager.SENSOR_DELAY_NORMAL);
		}

		if(!isDialing && Vars.state == CallState.INIT)
		{
			stopRingtone();
			switch(audioManager.getRingerMode())
			{
				case AudioManager.RINGER_MODE_NORMAL:
					Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
					ringtone = RingtoneManager.getRingtone(getApplicationContext(), ringtoneUri);
					ringtone.play();
					break;
				case AudioManager.RINGER_MODE_VIBRATE:
					vibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
					if(vibrator == null) break;
					final long[] vibratePattern = new long[] {0, 400, 200};
					vibrator.vibrate(vibratePattern, 0);
					break;
				//no need for the dead silent case. if it is dead silent just light up the screen with no nothing
			}
		}
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		screenShowing = false;
		sensorManager.unregisterListener(this);
	}

	protected void onStopWrapper()
	{
		try
		{
			//onStop() is only ever manually called when the call ends and you want to leave this screen
			Vars.state = CallState.NONE;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
			{
				runOnUiThread(new Runnable()
				{
					@Override
					public void run()
					{
						onStop();
					}
				});
			}
			else
			{
				onStop();
			}
		}
		catch(Exception e)
		{
			//if the screen is already gone and this is the second time it's being called, not much you can do
		}
	}

	@Override
	protected void onStop()
	{
		super.onStop();
		screenShowing = false;

		/*
		 * onStop() is called when the CallMain screen isn't visible anymore. Either from ending a call
		 * or from going to another app during a call. To make sure the call doesn't stop, only do the
		 * cleanup if you're leaving the screen because the call ended.
		 *
		 * Vars.state = NONE will be set before calling onStop() manually to guarantee that onStop() sees
		 * the call state as none. The 2 async calls: end, timeout will set state = NONE, BUT BECAUSE they're
		 * async there is a chance that onStop() is called before the async is. Don't leave it to dumb luck.
		 */
		if(Vars.state == CallState.NONE)
		{
			new CommandEndAsync().execute();

			//for cases when you make a call but decide you don't want to anymore
			try
			{
				stopAudioTrack(dialTone);
			}
			catch(Exception e)
			{
				//will happen if you're on the receiving end or if the call has been connected
			}

			stopRingtone();

			audioManager.setMode(AudioManager.MODE_NORMAL);

			//double check the counter to timeout is stopped or it will leak and crash when it's supposed to stop and this screen is gone
			if(counter != null)
			{
				counter.cancel();
				counter.purge();
			}
			try
			{
				unregisterReceiver(myReceiver);
			}
			catch(IllegalArgumentException i)
			{
				Utils.logcat(Const.LOGW, tag, "don't unregister you get a leak, do unregister you get an exception... ");
			}

			//play a notification tone when the call ends
			if(!playedEndTone)
			{
				playedEndTone = true; //actually played twice: once for each media send and once for media receive's call to onStop
				try
				{
					final byte[] endToneDump = new byte[END_TONE_SIZE]; //right click the file and get the exact size
					final InputStream endToneStream = getResources().openRawResource(R.raw.end8000);
					final int amount = endToneStream.read(endToneDump);
					final int actualSize = amount - WAV_FILE_HEADER;
					endToneStream.close();

					final AudioTrack endTonePlayer = new AudioTrack(STREAMCALL, 8000, AudioFormat.CHANNEL_OUT_MONO, S16, actualSize, AudioTrack.MODE_STATIC);
					endTonePlayer.write(endToneDump, WAV_FILE_HEADER, actualSize);
					endTonePlayer.play();
					int playbackPos = endTonePlayer.getPlaybackHeadPosition();
					while(playbackPos < actualSize/2)
					{
						playbackPos = endTonePlayer.getPlaybackHeadPosition();
					}
					stopAudioTrack(endTonePlayer);
				}
				catch(Exception e)
				{
					//nothing useful you can do if the notification end tone fails to play
				}
			}

			//overwrite the voice sodium symmetric key memory contents
			SodiumUtils.applyFiller(Vars.voiceSymmetricKey);
			if(Vars.mediaUdp != null && !Vars.mediaUdp.isClosed())
			{
				Vars.mediaUdp.close();
				Vars.mediaUdp = null;
			}

			//might be stuck in the blocking queue if the connection dies
			if(playbackThread != null)
			{
				playbackThread.interrupt();
			}

			if(recordThread != null)
			{
				recordThread.interrupt();
			}

			if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && Vars.incallA9Workaround != null)
			{
				Vars.incallA9Workaround.release();
				Vars.incallA9Workaround = null;
			}

			//go back to the home screen and clear the back history so there is no way to come back
			Intent goHome = new Intent(this, UserHome.class);
			goHome.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(goHome);
		}
	}

	@Override
	public void onClick(View v)
	{
		if(v == end)
		{
			onStopWrapper();
		}
		else if (v == mic)
		{
			micMute = !micMute;
			micStatusNew = true;

			//update the mic icon in the encoder thread when the actual mute/unmute happens.
			//it can take up to 1 second to change the status because of the sleep when going from mute-->unmute
			Toast checkMic = Toast.makeText(this, getString(R.string.call_main_toast_micstatus), Toast.LENGTH_LONG);
			checkMic.show();
		}
		else if (v == speaker)
		{
			onSpeaker = !onSpeaker;
			if(onSpeaker)
			{
				speaker.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_volume_up_white_48dp));
				audioManager.setSpeakerphoneOn(true);
			}
			else
			{
				speaker.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_phone_in_talk_white_48dp));
				audioManager.setSpeakerphoneOn(false);
			}
		}
		else if (v == stats)
		{
			showStats = !showStats;
			if(showStats)
			{
				stats.setTextColor(ContextCompat.getColor(this, R.color.material_green));
				userImage.setVisibility(View.INVISIBLE);
			}
			else
			{
				stats.setTextColor(ContextCompat.getColor(this, android.R.color.white));
				callerid.setText(Utils.getCallerID(Vars.callWith));
				userImage.setVisibility(View.VISIBLE);
			}
		}
		else if(v == accept)
		{
			new CommandAcceptAsync().execute();
		}
	}

	private void updateTime()
	{
		if(sec == 59)
		{
			sec = 0;
			min++;
		}
		else
		{
			sec++;
		}
		if(screenShowing)
		{
			runOnUiThread(new Runnable()
			{
				@Override
				public void run()
				{
					timeBuilder.setLength(0);
					if(sec < 10)
					{
						time.setText(timeBuilder.append(min).append(":0").append(sec).toString());
					}
					else
					{
						time.setText(timeBuilder.append(min).append(":").append(sec).toString());
					}
				}
			});
		}
	}

	private void updateStats()
	{
		if(screenShowing && showStats)
		{
			final String rxDisp=formatInternetMeteric(rxData), txDisp=formatInternetMeteric(txData);
			final int missing = txSeq-rxSeq;
			statsBuilder.setLength(0);
			statsBuilder
					.append(missingLabel).append(": ").append(missing > 0 ? missing : 0).append(" ").append(garbageLabel).append(": ").append(garbage).append("\n")
					.append(rxLabel).append(":").append(rxDisp).append(" ").append(txLabel).append(":").append(txDisp).append("\n")
					.append(rxSeqLabel).append(":").append(rxSeq).append(" ").append(txSeqLabel).append(":").append(txSeq).append("\n")
					.append(skippedLabel).append(":").append(skipped).append(" ").append(oorangeLabel).append(": ").append(oorange);
			runOnUiThread(new Runnable()
			{
				@Override
				public void run()
				{
					callerid.setText(statsBuilder.toString());
				}
			});
		}
	}

	private void stopRingtone()
	{
		if(ringtone != null && ringtone.isPlaying())
		{
			ringtone.stop();
			ringtone = null;
		}

		if(vibrator != null)
		{
			vibrator.cancel();
			vibrator = null;
		}
	}

	private void changeToCallMode()
	{
		accept.setEnabled(false);
		stopRingtone();
		try
		{
			stopAudioTrack(dialTone);
		}
		catch(Exception e)
		{
			//will happen when you're on the receiving end and the dial tone was never played
		}

		//it is impossible to change the status bar @ run time below 5.0, only the action bar color.
		//results in a weird blue/green look. only change the look for >= 5.0
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
		{
			try
			{//apparently this line has the possibility of a null pointer exception as warned by android studio...???
				getSupportActionBar().setBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(CallMain.this, R.color.material_green)));

				Window window = getWindow();
				window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
				window.setStatusBarColor(ContextCompat.getColor(CallMain.this, R.color.material_dark_green));
				window.setNavigationBarColor(ContextCompat.getColor(CallMain.this, R.color.material_dark_green));
			}
			catch(NullPointerException n)
			{
				Utils.dumpException(tag, n);
			}
		}

		status.setText(getString(R.string.call_main_status_incall));
		mic.setEnabled(true);
		speaker.setEnabled(true);

		//now that the call is ACTUALLY starting put android into communications mode
		//communications mode will prevent the ringtone from playing
		audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
		audioManager.setSpeakerphoneOn(false);

		//initialize the opus library before creating the threads so it will be ready when the threads start
		Opus.init();
		startMediaEncodeThread();
		startMediaDecodeThread();
	}

	private void startMediaEncodeThread()
	{
		recordThread = new Thread(new Runnable()
		{
			private static final String tag = "EncodingThread";

			private static final int STEREO = AudioFormat.CHANNEL_IN_STEREO;
			private static final int MIC = MediaRecorder.AudioSource.DEFAULT;

			private final LinkedBlockingQueue<DatagramPacket> sendQ = new LinkedBlockingQueue<>();
			private DatagramPacketPool packetPool = new DatagramPacketPool(Vars.callServer, Vars.mediaPort);

			private final Thread internalNetworkThread = new Thread(new Runnable()
			{
				private static final String tag = "EncodeNetwork";

				@Override
				public void run()
				{
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
								endThread();
								break;
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
					endThread();
				}

				internalNetworkThread.setName("Media_Encoder_Network");
				internalNetworkThread.start();

				final byte[] packetBuffer = new byte[Const.SIZE_MAX_UDP];
				final short[] wavbuffer = new short[WAVBUFFERSIZE];
				final byte[] encodedbuffer = new byte[WAVBUFFERSIZE];

				while (Vars.state == CallState.INCALL)
				{

					if (micStatusNew)
					{
						runOnUiThread(new Runnable()
						{
							@Override
							public void run()
							{
								if(micMute)
								{
									mic.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_mic_off_white_48dp));
								}
								else
								{
									mic.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_mic_white_48dp));
								}
							}
						});
						micStatusNew = false;
					}

					Arrays.fill(wavbuffer, (short)0);
					int totalRead = 0, dataRead;
					while (totalRead < WAVBUFFERSIZE)
					{
						dataRead = wavRecorder.read(wavbuffer, totalRead, WAVBUFFERSIZE - totalRead);
						totalRead = totalRead + dataRead;
					}

					if(micMute)
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
							Utils.dumpException(tag, e);
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
				internalNetworkThread.interrupt();
				Utils.logcat(Const.LOGD, tag, "MediaCodec encoder thread has stopped");
			}

			private void endThread()
			{
				try
				{
					onStopWrapper();
				}
				catch(Exception e)
				{
					//don't know whether encode or decode will call onStop() first. the second one will get a null exception
					//because the main ui thead will be gone after the first onStop() is called. catch the exception
				}
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
					while(Vars.state == CallState.INCALL)
					{
						DatagramPacket received;
						try
						{
							received = packetPool.getDatagramPacket();
							Vars.mediaUdp.receive(received);
							long now = System.currentTimeMillis();
							synchronized (rxtsLock)
							{
								lastReceivedTimestamp = now;
							}

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
								endThread();
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
				stopAudioTrack(wavPlayer);
				Opus.closeOpus();
				networkThread.interrupt();
				Utils.logcat(Const.LOGD, tag, "MediaCodec decoder thread has stopped, state:" + Vars.state);
			}

			private void endThread()
			{
				try
				{
					onStopWrapper();
				}
				catch(Exception e)
				{
					//don't know whether encode or decode will call onStop() first. the second one will get a null exception
					//because the main ui thread will be gone after the first onStop() is called. catch the exception
				}
			}
		});
		playbackThread.setName("Media_Decoder");
		playbackThread.start();
	}

	public synchronized boolean reconnectUDP()
	{
		if(Vars.state == CallState.INCALL)
		{
			final int MAX_UDP_RECONNECTS = 10;
			if(reconenctTries > MAX_UDP_RECONNECTS)
			{
				return false;
			}

			reconenctTries ++;
			if(reconnectionAttempted)
			{
				reconnectionAttempted = false;
				return true;
			}
			else
			{
				boolean reconnected = CmdListener.registerVoiceUDP();
				reconnectionAttempted = true;
				return reconnected;
			}
		}
		return false;
	}

	@Override
	public void onBackPressed()
	{
		// Do nothing. If you're in a call then there's no reason to go back to the User Home
	}

	@Override
	public void onSensorChanged(SensorEvent event)
	{
		final float x = event.values[0];

		if(x < 5)
		{
			//with there being no good information on turning the screen on and off
			//go with the next best thing of disabling all the buttons
			screenShowing = false;
			end.setEnabled(false);
			mic.setEnabled(false);
			speaker.setEnabled(false);
		}
		else
		{
			screenShowing = true;
			end.setEnabled(true);
			mic.setEnabled(true);
			speaker.setEnabled(true);
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy)
	{
		//not relevant for proximity sensor so do nothing
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

	private void stopAudioTrack(AudioTrack audioTrack)
	{
		audioTrack.pause();
		audioTrack.flush();
		audioTrack.stop();
		audioTrack.release();
	}
}