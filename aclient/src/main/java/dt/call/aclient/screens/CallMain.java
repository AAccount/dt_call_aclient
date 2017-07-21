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
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.net.DatagramPacket;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import dt.call.aclient.CallState;
import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.background.async.CommandEndAsync;
import dt.call.aclient.fdkaac.FdkAAC;

public class CallMain extends AppCompatActivity implements View.OnClickListener, SensorEventListener
{
	private static final String tag = "CallMain";

	private static final int HEADERS = 52;

	private static final int SAMPLES = 44100;
	private static final int S16 = AudioFormat.ENCODING_PCM_16BIT;
	private static final int STREAMCALL = AudioManager.STREAM_VOICE_CALL;

	private static final int WAVBUFFERSIZE = FdkAAC.getWavFrameSize();
	private static final int AACBUFFERSIZE = 1000; //extra big just to be safe
	private static final int DIAL_TONE_SIZE = 32000;

	//ui stuff
	private FloatingActionButton end, mic, speaker;
	private Button noiseReduction, echoCancel, stats;
	private volatile boolean micMute = false;
	private boolean micStatusNew = false;
	private boolean onSpeaker = false;
	private boolean screenShowing;
	private TextView status;
	private TextView time;
	private TextView callerid;
	private int min=0, sec=0;
	private Timer counter = new Timer();
	private BroadcastReceiver myReceiver;
	private int garbage=0, tx=0, rx=0, txCount=0, rxCount=0;
	private String missingLabel, garbageLabel, txLabel, rxLabel;
	private boolean showStats = false;

	//proximity sensor stuff
	private SensorManager sensorManager;
	private Sensor proximity;

	//related to audio playback and recording
	private AudioManager audioManager;
	private AudioRecord wavRecorder = null;

	//for dial tone when initiating a call
	private AudioTrack dialTone = new AudioTrack(STREAMCALL, 8000, AudioFormat.CHANNEL_OUT_MONO, S16, DIAL_TONE_SIZE, AudioTrack.MODE_STATIC);

	private Key aesKeyObj;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_call_main);

		//allow this screen to show even when there is a password/pattern lock screen
		Window window = this.getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
		window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

		end = (FloatingActionButton)findViewById(R.id.call_main_end_call);
		end.setOnClickListener(this);
		mic = (FloatingActionButton)findViewById(R.id.call_main_mic);
		mic.setOnClickListener(this);
		mic.setEnabled(false);
		speaker = (FloatingActionButton)findViewById(R.id.call_main_spk);
		speaker.setOnClickListener(this);
		speaker.setEnabled(false);
		noiseReduction = (Button)findViewById(R.id.call_main_noise_reduction);
		noiseReduction.setOnClickListener(this);
		echoCancel = (Button)findViewById(R.id.call_main_echo_cancel);
		echoCancel.setOnClickListener(this);
		stats = (Button)findViewById(R.id.call_main_stats);
		stats.setOnClickListener(this);
		status = (TextView)findViewById(R.id.call_main_status); //by default ringing. change it when in a call
		callerid = (TextView) findViewById(R.id.call_main_callerid);
		time = (TextView)findViewById(R.id.call_main_time);
		callerid.setText(Vars.callWith.toString());

		/**
		 * The stuff under here might look like a lot which has the potential to seriously slow down onCreate()
		 * but it's really just long because defining some of the setup is long (like encode feed, decode feed
		 * broadcast receiver etc...)
		 *
		 * It's not as bad as it looks
		 */

		//proximity sensor
		sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

		//Start showing the counter for how long it's taking to answer the call or how long
		//	the call has been going for
		TimerTask counterTask = new TimerTask()
		{
			@Override
			public void run()
			{

				if(sec == 59)
				{//seconds should never hit 60. 60 is time to regroup into minutes
					sec = 0;
					min++;
				}
				else
				{
					sec++;
				}

				//if the person hasn't answered after 60 seconds give up. it's probably not going to happen.
				if((Vars.state == CallState.INIT) && (sec == Const.CALL_TIMEOUT))
				{
					new CommandEndAsync().execute();
					Vars.state = CallState.NONE; //guarantee state == NONE. don't leave it to chance
					onStop();
				}

				if(screenShowing)
				{
					runOnUiThread(new Runnable()
					{
						@Override
						public void run()
						{
							updateTime();
						}
					});
				}

				if(showStats)
				{
					String rxDisp=formatInternetMeteric(rx), txDisp=formatInternetMeteric(tx);
					int missing = txCount-rxCount;
					final String latestStats = missingLabel + ": " + missing + " " + garbageLabel + ": " + garbage + "\n"
							+rxLabel + ": " + rxDisp + " "  + txLabel + ": " + txDisp;
					runOnUiThread(new Runnable()
					{
						@Override
						public void run()
						{
							callerid.setText(latestStats);
						}
					});
				}
			}
		};
		counter.schedule(counterTask, 0, 1000);

		//Setup the strings for displaying tx rx skip stats
		missingLabel = getString(R.string.call_main_stat_mia);
		txLabel = getString(R.string.call_main_stat_tx);
		rxLabel = getString(R.string.call_main_stat_rx);
		garbageLabel = getString(R.string.call_main_stat_garbage);

		if(!Vars.SHOUDLOG)
		{
			stats.setVisibility(View.INVISIBLE);
		}

		//listen for call accepted or rejected
		myReceiver = new BroadcastReceiver()
		{
			@Override
			public void onReceive(Context context, Intent intent)
			{
				String response = intent.getStringExtra(Const.BROADCAST_CALL_RESP);
				if(response.equals(Const.BROADCAST_CALL_START))
				{
					min = 0;
					sec = 0;
					changeToCallMode();
				}
				else if(response.equals(Const.BROADCAST_CALL_END))
				{
					//whether the call was rejected or time to end, it's the same result
					//so share the same variable to avoid 2 sendBroadcast chunks of code that are almost the same

					//media read/write are stopped in command listener when it got the call end
					//Vars.state would've already been set by the server command that's broadcasting a call end
					onStop();
				}
			}
		};
		registerReceiver(myReceiver, new IntentFilter(Const.BROADCAST_CALL));

		/**
		 * Audio setup from here
		 */

		//https://stackoverflow.com/questions/12857817/how-to-play-audio-via-ear-phones-only-using-mediaplayer-android
		//make it possible to use the earpiece and to switch back and forth
		audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
		audioManager.setSpeakerphoneOn(false);

		//now that the setup has been complete:
		//set the ui to call mode: if you got to this screen after accepting an incoming call
		if(Vars.state == CallState.INCALL)
		{
			changeToCallMode();
		}
		else //otherwise you're the one placing a call. play a dial tone for user feedback
		{
			byte[] dialToneDump = new byte[DIAL_TONE_SIZE]; //right click the file and get the exact size
			try
			{
				InputStream dialToneStream = getResources().openRawResource(R.raw.dialtone8000);
				int amount = dialToneStream.read(dialToneDump);
				dialTone.write(dialToneDump, 0, amount);
				dialTone.setLoopPoints(0, DIAL_TONE_SIZE/2, -1);
				dialTone.play();
			}
			catch (Exception e)
			{
				Utils.dumpException(tag, e);
			}
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		screenShowing = true;
		sensorManager.registerListener(this, proximity, SensorManager.SENSOR_DELAY_NORMAL);
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		screenShowing = false;
		sensorManager.unregisterListener(this);
	}

	@Override
	protected  void onStop()
	{
		super.onStop();
		screenShowing = false;

		/**
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
				dialTone.stop();
				dialTone.release();
				dialTone = null;
			}
			catch (Exception e)
			{
				//will happen if you're on the receiving end or if the call has been connected
			}

			//no longer in a call
			audioManager.setMode(AudioManager.MODE_NORMAL);
			counter.cancel();

			try
			{
				unregisterReceiver(myReceiver);
			}
			catch (IllegalArgumentException i)
			{
				Utils.logcat(Const.LOGW, tag, "don't unregister you get a leak, do unregister you get an exception... ");
			}

			//go back to the home screen and clear the back history so there is no way to come back to
			//call main
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
			Vars.state = CallState.NONE; //guarantee onStop sees state == NONE
			onStop();
		}
		else if (v == mic)
		{
			micMute = !micMute;
			micStatusNew = true;

			//update the mic icon in the encoder thread when the actual mute/unmute happens.
			//it can take up to 1 second to change the status because of the sleep when going from mute-->unmute
			//notify the user of this so their "dirty laundry" doesn't unintentionally get aired
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
		else if (v == noiseReduction)
		{
			if(NoiseSuppressor.isAvailable())
			{
				NoiseSuppressor.create(wavRecorder.getAudioSessionId());
				noiseReduction.setTextColor(ContextCompat.getColor(this, R.color.material_green));
			}
			else
			{
				noiseReduction.setTextColor(ContextCompat.getColor(this, R.color.material_red));
			}
			noiseReduction.setEnabled(false);
		}
		else if (v == echoCancel)
		{
			if(AcousticEchoCanceler.isAvailable())
			{
				AcousticEchoCanceler.create(wavRecorder.getAudioSessionId());
				echoCancel.setTextColor(ContextCompat.getColor(this, R.color.material_green));
			}
			else
			{
				echoCancel.setTextColor(ContextCompat.getColor(this, R.color.material_red));
			}
			echoCancel.setEnabled(false);
		}
		else if (v == stats)
		{
			showStats = !showStats;
			if(showStats)
			{
				stats.setTextColor(ContextCompat.getColor(this, R.color.material_green));
			}
			else
			{
				stats.setTextColor(ContextCompat.getColor(this, android.R.color.white));
				callerid.setText(Vars.callWith.toString());
			}
		}
	}

	private void updateTime()
	{
		if(sec < 10)
		{
			time.setText(min + ":0" + sec);
		}
		else
		{
			time.setText(min + ":" + sec);
		}
	}

	private void changeToCallMode()
	{
		aesKeyObj = new SecretKeySpec(Vars.aesKey, "AES");
		Arrays.fill(Vars.aesKey, (byte)0); //any secretly detained packets will forever be gibberish after the call is over
		try
		{
			dialTone.stop();
			dialTone.release();
			dialTone = null;
		}
		catch (Exception e)
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
			catch (NullPointerException n)
			{
				Utils.dumpException(tag, n);
			}
		}

		status.setText(getString(R.string.call_main_status_incall));
		mic.setEnabled(true);
		speaker.setEnabled(true);
		noiseReduction.setEnabled(true);
		echoCancel.setEnabled(true);

		//initialize the aac library before creating the threads so it will be ready when the threads start
		FdkAAC.initAAC();
		startMediaEncodeThread();
		startMediaDecodeThread();
	}

	private void startMediaEncodeThread()
	{
		Thread recordThread = new Thread(new Runnable()
		{
			private static final String tag = "EncodingThread";

			private static final int STEREOIN = AudioFormat.CHANNEL_IN_STEREO;
			private static final int MIC = MediaRecorder.AudioSource.DEFAULT;
			volatile int error = 0; //java never sees this change because the jni does it. mark it volatile to prevent false assumptions

			@Override
			public void run()
			{
				Utils.logcat(Const.LOGD, tag, "MediaCodec encoder thread has started");

				//setup the wave audio recorder. since it is released and restarted, it needs to be setup here and not onCreate
				wavRecorder = new AudioRecord(MIC, SAMPLES, STEREOIN, S16, WAVBUFFERSIZE);
				wavRecorder.startRecording();

				//my dying i9300 on CM12.1 sometimes can't get the audio record on its first try
				int recorderRetries = 5;
				while(wavRecorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING && recorderRetries > 0)
				{
					wavRecorder.stop();
					wavRecorder.release();
					wavRecorder = new AudioRecord(MIC, SAMPLES, STEREOIN, S16, WAVBUFFERSIZE);
					wavRecorder.startRecording();
					Utils.logcat(Const.LOGW, tag, "audiorecord failed to initialized. retried");
					recorderRetries--;
				}

				//if the wav recorder can't be initialized hang up and try again
				//nothing i can do when the cell phone itself has problems
				if(recorderRetries == 0)
				{
					Utils.logcat(Const.LOGE, tag, "couldn't get the microphone from the cell phone. hanging up");
					endThread();
				}

				byte[] accumulator = new byte[Const.MEDIA_SIZE-100];
				int accPos = 0;
				while (Vars.state == CallState.INCALL)
				{
					byte[] aacbuffer = new byte[AACBUFFERSIZE];
					short[] wavbuffer = new short[WAVBUFFERSIZE];

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

					int totalRead = 0, dataRead;
					while (totalRead < WAVBUFFERSIZE)
					{//although unlikely to be necessary, bufferSize the mic input
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

					/**
					 *Avoid sending tons of tiny packets wasting resources for headers.
					 */
					int encodeLength = FdkAAC.encode(wavbuffer, aacbuffer, error);
					if(error != 0)
					{
						Log.d(tag, "encoding error of: " + error);
					}

					//if the current aac chunk won't fit in the accumulator, send the packet and restart the accumulator
					if((accPos + 2 + encodeLength) > accumulator.length)
					{
						try
						{
							byte[] accumulatorTrimmed = new byte[accPos];
							System.arraycopy(accumulator, 0, accumulatorTrimmed, 0, accPos);
							byte[] accumulatorEncrypted = encrypt(accumulatorTrimmed);

							DatagramPacket packet = new DatagramPacket(accumulatorEncrypted, accumulatorEncrypted.length);
							Vars.mediaUdp.send(packet);
							tx = tx + accumulatorEncrypted.length + HEADERS;
							txCount++;
						}
						catch (Exception e)
						{
							Utils.logcat(Const.LOGE, tag, "Cannot send aac out the media socket");
							Utils.dumpException(tag, e);

							//if the socket died it's impossible to continue the call.
							//the other person will get a dropped call and you can start the call again.
							//
							//kill the sockets so that UserHome's crash recovery will reinitialize them
							endThread();
						}
						accPos = 0;
						Arrays.fill(accumulator, (byte)0);
					}

					//write the aac chunk size as a "header" before writing the actual aac data
					byte first = (byte)(encodeLength >> 7); //split up large numbers like 1234 into 12, 34
					byte second = (byte)(encodeLength & Byte.MAX_VALUE);
					accumulator[accPos] = first;
					accumulator[accPos+1] = second;
					System.arraycopy(aacbuffer, 0 , accumulator, accPos+2, encodeLength);
					accPos = accPos + 2 + encodeLength;
				}
				FdkAAC.closeEncoder();
				wavRecorder.stop();
				wavRecorder.release();
				Utils.logcat(Const.LOGD, tag, "MediaCodec encoder thread has stopped");
			}

			private void endThread()
			{
				Vars.state = CallState.NONE;
				//must guarantee that the sockets are killed before going to the home screen. otherwise
				//the userhome's crash recovery won't kick in. don't leave it to dumb luck (race condition)
				Utils.killSockets();

				try
				{
					onStop();
				}
				catch (Exception e)
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
		Thread playbackThread = new Thread(new Runnable()
		{
			private static final String tag = "DecodingThread";
			private static final int STEREOOUT = AudioFormat.CHANNEL_OUT_STEREO;

			//realtime strategy variables
			private final int BUFFER = AudioTrack.getMinBufferSize(SAMPLES, STEREOOUT, S16);

			@Override
			public void run()
			{
				Utils.logcat(Const.LOGD, tag, "MediaCodec decoder thread has started");
				Log.d(tag, "recommended buffer size: " + BUFFER);

				//setup the wave audio track with enhancements if available
				AudioTrack wavPlayer = new AudioTrack(STREAMCALL, SAMPLES, STEREOOUT, S16, BUFFER, AudioTrack.MODE_STREAM);
				wavPlayer.play();

				while(Vars.state == CallState.INCALL)
				{
					short[] wavbuffer = new short[WAVBUFFERSIZE];
					try
					{
						//read encrypted aac
						byte[] inputBuffer = new byte[Const.MEDIA_SIZE];
						DatagramPacket received = new DatagramPacket(inputBuffer, Const.MEDIA_SIZE);
						Vars.mediaUdp.receive(received);

						//decrypt
						rx = rx + received.getLength() + HEADERS;
						rxCount++;
						byte[] accumulator = new byte[received.getLength()];
						System.arraycopy(received.getData(), 0, accumulator, 0, received.getLength());
						byte[] accumulatorDec = decrypt(accumulator); //contents [size1|aac chunk 1|size2|aac chunk 2|...|sizeN|aac chunk N]
						if(accumulatorDec == null)
						{
							Utils.logcat(Const.LOGD, tag, "Invalid decryption");
							continue;
						}

						int readPos = 0;
						while(readPos < accumulatorDec.length)
						{
							//retrieve the size from the first 2 bytes
							int aacLength = (accumulatorDec[readPos] << 7) + (accumulatorDec[readPos+1]);

							//extract the aac chunk
							byte[] aacbuffer = new byte[aacLength];
							System.arraycopy(accumulatorDec, readPos+2, aacbuffer, 0, aacLength);

							//decode aac chunk
							int error = FdkAAC.decode(aacbuffer, wavbuffer);
							if(error != 0)
							{
								Utils.logcat(Const.LOGW, tag, "aac jni decoder error of: " + error);
							}
							else
							{
								wavPlayer.write(wavbuffer, 0, WAVBUFFERSIZE);
							}

							//advance the accumulator read position
							readPos = readPos + 2 + aacLength;
						}
					}
					catch (Exception i) //io or null pointer depending on when the connection dies
					{
						Utils.logcat(Const.LOGE, tag, "exception reading media: ");
						Utils.dumpException(tag, i);

						//if the socket died it's impossible to continue the call.
						//the other person will get a dropped call and you can start the call again.
						//
						//kill the sockets so that UserHome's crash recovery will reinitialize them
						Vars.state = CallState.NONE;
						//must guarantee that the sockets are killed before going to the home screen. otherwise
						//the userhome's crash recovery won't kick in. don't leave it to dumb luck (race condition)
						Utils.killSockets();

						try
						{
							onStop();
						}
						catch (Exception e)
						{
							//see encoder thread for why onStop() is called in a try
						}
					}
				}
				FdkAAC.closeDecoder();
				wavPlayer.stop();
				wavPlayer.flush(); //must flush after stop
				wavPlayer.release(); //mandatory cleanup to prevent wavPlayer from outliving its usefulness
				Utils.logcat(Const.LOGD, tag, "MediaCodec decoder thread has stopped, state:" + Vars.state);
			}
		});
		playbackThread.setName("Media_Decoder");
		playbackThread.start();
	}

	@Override
	public void onBackPressed()
	{
		/*
		 * Do nothing. If you're in a call then there's no reason to go back to the User Home
		 */
	}

	@Override
	public void onSensorChanged(SensorEvent event)
	{
		float x = event.values[0];

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

	private byte[] encrypt(byte[] in)
	{
		byte[] result;
		try
		{
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, aesKeyObj);
			byte[] init = cipher.getIV();
			byte[] enc = cipher.doFinal(in);
			result = new byte[1+init.length + enc.length]; //|length;init;encrypted|
			result[0] = (byte)init.length;
			System.arraycopy(init, 0, result, 1, init.length);
			System.arraycopy(enc, 0, result, 1 + init.length, enc.length);
			return result;
		}
		catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e)
		{
			Utils.dumpException(tag, e);
		}
		return null;
	}

	private byte[] decrypt(byte[] in)
	{//udp packet: [nonce length (byte 0) | nonce (byte 1 --> L)| aes payload (bytes L+1 --> end)]
		try
		{
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			byte initLength = in[0];
			if(initLength < 1)
			{
				throw new InvalidAlgorithmParameterException("Garbage udp packet");
			}

			byte[] init = new byte[initLength];
			System.arraycopy(in, 1, init, 0, in[0]);
			cipher.init(Cipher.DECRYPT_MODE, aesKeyObj, new IvParameterSpec(init));
			return cipher.doFinal(in, init.length+1, in.length-init.length-1);
		}
		catch (NoSuchAlgorithmException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException | NoSuchPaddingException | InvalidAlgorithmParameterException e)
		{
			Utils.dumpException(tag, e);
			garbage++;
		}

		return null;
	}

	private String formatInternetMeteric(int n)
	{
		DecimalFormat decimalFormat = new DecimalFormat("#.###");
		if(n > 1000000)
		{
			return decimalFormat.format((float)n / (float)1000000) + "M";
		}
		else if (n > 1000)
		{
			return (n/1000) + "K";
		}
		else
		{
			return Integer.toString(n);
		}
	}
}
