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
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import dt.call.aclient.CallState;
import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.background.async.CallEndAsync;
import dt.call.aclient.background.async.CallTimeoutAsync;
import dt.call.aclient.background.async.KillSocketsAsync;
import io.kvh.media.amr.AmrDecoder;
import io.kvh.media.amr.AmrEncoder;

public class CallMain extends AppCompatActivity implements View.OnClickListener, SensorEventListener
{
	private static final String tag = "CallMain";

	private static final int SAMPLESAMR = 8000;
	private static final int FORMAT = AudioFormat.ENCODING_PCM_16BIT;
	private static final int STREAMCALL = AudioManager.STREAM_VOICE_CALL;
	private static final int WAVBUFFERSIZE = 160;
	private static final int AMRBUFFERSIZE = 32;

	//ui stuff
	private FloatingActionButton end, mic, speaker;
	private volatile boolean micMute = false;
	private boolean micStatusNew = false;
	private boolean onSpeaker = false;
	private boolean screenShowing;
	private TextView status, callerid, time;
	private int min=0, sec=0;
	private Timer counter = new Timer();
	private BroadcastReceiver myReceiver;

	//proximity sensor stuff
	private SensorManager sensorManager;
	private Sensor proximity;

	//related to audio playback and recording
	private AudioManager audioManager;
	private AudioRecord wavRecorder;
	private AudioTrack wavPlayer;
	//the first time start encoding thread is called nothing would've set shouldEncode (mute button wouldn'tve been touched)

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
		status = (TextView)findViewById(R.id.call_main_status); //by default ringing. change it when in a call
		callerid = (TextView)findViewById(R.id.call_main_callerid);
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
					new CallTimeoutAsync().execute();
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
			}
		};
		counter.schedule(counterTask, 0, 1000);

		//listen for call accepted or rejected
		myReceiver = new BroadcastReceiver()
		{
			@Override
			public void onReceive(Context context, Intent intent)
			{
				Utils.logcat(Const.LOGD, tag, "received a broadcast intent");
				String response = intent.getStringExtra(Const.BROADCAST_CALL_RESP);
				if(response.equals(Const.BROADCAST_CALL_START))
				{
					min = 0;
					sec = 0;
					uiCallMode();
					startMediaDecodeThread();
					startMediaEncodeThread();
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
			uiCallMode();
			startMediaDecodeThread();
			startMediaEncodeThread();
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
			new CallEndAsync().execute();

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

	private void uiCallMode()
	{
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
	}

	private void startMediaEncodeThread()
	{
		Thread recordThread = new Thread(new Runnable()
		{
			private static final String encTag = "EncodingThread";
			private static final int CLUMPSIZE = 500;

			@Override
			public void run()
			{
				Utils.logcat(Const.LOGD, encTag, "MediaCodec encoder thread has started");
				byte[] amrbuffer = new byte[AMRBUFFERSIZE];
				short[] wavbuffer = new short[WAVBUFFERSIZE];
				BufferedOutputStream bufferedOutputStream = null;

				//https://stackoverflow.com/questions/1846077/size-of-empty-udp-and-tcp-packet
				//https://en.wikipedia.org/wiki/Silly_window_syndrome#Send-side_silly_window_avoidance
				//for each 32 bytes of voice, 40bytes of header will be written which means > 50% of data IO is
				//headers (not useful)!!! a waste of ridiculously overpriced canadian lte
				//use the wikipedia clumping strategy
				try
				{
					bufferedOutputStream = new BufferedOutputStream(Vars.mediaSocket.getOutputStream(), CLUMPSIZE);
				}
				catch (Exception e)
				{
					//no point of doing the call if the clumping strategy can't be used.
					//don't waste data falling back to sending 32bytes @ a time. hang up and try again
					Vars.state = CallState.NONE;
					try
					{//must guarantee that the sockets are killed before going to the home screen. otherwise
						//the userhome's crash recovery won't kick in. don't leave it to dumb luck (race condition)
						new KillSocketsAsync().execute().get();
						onStop(); //don't know whether encode or decode will call onStop() first. the second one will get a null exception
						//because the main ui thead will be gone after the first onStop() is called. catch the exception
					}
					catch (Exception e2)
					{
						Utils.logcat(Const.LOGE, encTag, "Trying to kill sockets because of an exception but got another exception in the process");
						Utils.dumpException(encTag, e2);
					}
				}

				//setup the wave audio recorder. since it is released and restarted, it needs to be setup here and not onCreate
				wavRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLESAMR, AudioFormat.CHANNEL_IN_MONO, FORMAT, WAVBUFFERSIZE);
				wavRecorder.startRecording();

				//my dying i9300 on CM12.1 sometimes can't get the audio record on its first try
				int recorderRetries = 5;
				while(wavRecorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING && recorderRetries > 0)
				{
					wavRecorder.stop();
					wavRecorder.release();
					wavRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLESAMR, AudioFormat.CHANNEL_IN_MONO, FORMAT, WAVBUFFERSIZE);
					wavRecorder.startRecording();
					Utils.logcat(Const.LOGW, encTag, "audiorecord failed to initialized. retried");
					recorderRetries--;
				}

				AmrEncoder.init(0);

				while(Vars.state == CallState.INCALL)
				{
					if(!micMute)
					{
						if (micStatusNew)
						{
							runOnUiThread(new Runnable()
							{
								@Override
								public void run()
								{
									mic.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_mic_white_48dp));
								}
							});
							micStatusNew = false;
						}

						int totalRead = 0, dataRead;
						while (totalRead < WAVBUFFERSIZE)
						{//although unlikely to be necessary, buffer the mic input
							dataRead = wavRecorder.read(wavbuffer, totalRead, WAVBUFFERSIZE - totalRead);
							totalRead = totalRead + dataRead;
						}

						int encodeLength = AmrEncoder.encode(AmrEncoder.Mode.MR122.ordinal(), wavbuffer, amrbuffer);
						try
						{
							bufferedOutputStream.write(amrbuffer, 0, encodeLength);
						}
						catch (Exception e)
						{
							Utils.logcat(Const.LOGE, encTag, "Cannot send amr out the media socket");
							Utils.dumpException(encTag, e);

							//if the socket died it's impossible to continue the call.
							//the other person will get a dropped call and you can start the call again.
							//
							//kill the sockets so that UserHome's crash recovery will reinitialize them
							Vars.state = CallState.NONE;
							try
							{//must guarantee that the sockets are killed before going to the home screen. otherwise
								//the userhome's crash recovery won't kick in. don't leave it to dumb luck (race condition)
								new KillSocketsAsync().execute().get();
								onStop(); //don't know whether encode or decode will call onStop() first. the second one will get a null exception
										//because the main ui thead will be gone after the first onStop() is called. catch the exception
							}
							catch (Exception e2)
							{
								Utils.logcat(Const.LOGE, encTag, "Trying to kill sockets because of an exception but got another exception in the process");
								Utils.dumpException(encTag, e2);
							}
						}
					}
					else
					{
						//instead of going into a crazy while no-op loop, wait 1 second before checking
						//if the mute has been released. this is the simplest implementation i can think of
						//that doesn't involve thread starting/stopping and making sure only 1 encode thread is running
						if (micStatusNew)
						{
							runOnUiThread(new Runnable()
							{
								@Override
								public void run()
								{
									mic.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_mic_off_white_48dp));
								}
							});
							micStatusNew = false;
						}
						SystemClock.sleep(1000);
					}
				}

				//various object cleanups
				AmrEncoder.exit();
				wavRecorder.stop();
				wavRecorder.release();
				Utils.logcat(Const.LOGD, encTag, "MediaCodec encoder thread has stopped");
			}
		});
		recordThread.setName("Media_Encoder");
		recordThread.start();
	}

	private void startMediaDecodeThread()
	{
		Thread playbackThread = new Thread(new Runnable()
		{
			private static final String decTag = "DecodingThread";

			@Override
			public void run()
			{
				Utils.logcat(Const.LOGD, decTag, "MediaCodec decoder thread has started");
				byte[] amrbuffer = new byte[AMRBUFFERSIZE];
				short[] wavbuffer = new short[WAVBUFFERSIZE];

				//setup the wave audio track
				int buffer = AudioTrack.getMinBufferSize(SAMPLESAMR, AudioFormat.CHANNEL_OUT_MONO, FORMAT);
				Utils.logcat(Const.LOGD, decTag, "wave audio buffer for mono @ 8000sample/sec: " + buffer);
				wavPlayer = new AudioTrack(STREAMCALL, SAMPLESAMR, AudioFormat.CHANNEL_OUT_MONO, FORMAT, buffer, AudioTrack.MODE_STREAM);
				wavPlayer.play();

				/*
				 * Detect when a bunch of audio starts rushing in. This happens because the network connection that went bad is suddenly "good"
				 * and all the missing packets are flooding. This will create an audio backlog and delay the audio from the real conversation.
				 * Discard the audio flood and just ask "what did you say. my connection was bad"
				 */
				long start, elapsed, minNanos = (long)(32/3700*1000000000); //~ (5629(avg)+1764(stddev))/2
				long amrstate = AmrDecoder.init();
				while(Vars.state == CallState.INCALL)
				{
					int totalRead=0, dataRead;
					try
					{
						//guarantee you have AMRBUFFERSIZE(32) bytes to send to the native library
						start = System.nanoTime();
						while(totalRead < AMRBUFFERSIZE)
						{
							dataRead = Vars.mediaSocket.getInputStream().read(amrbuffer, totalRead, AMRBUFFERSIZE -totalRead);
							totalRead = totalRead + dataRead;

							if(dataRead == -1)
							{
								throw new IOException("read from media socket thought it was the end of file (-1)");
							}
						}
						elapsed = System.nanoTime() - start;
						if(elapsed > minNanos)
						{
							AmrDecoder.decode(amrstate, amrbuffer, wavbuffer);
							wavPlayer.write(wavbuffer, 0, WAVBUFFERSIZE);
						}
						else
						{
							Utils.logcat(Const.LOGD, decTag, "Detected rushing in of audio... probably from network lag. Discard to get to realtime position");
						}
					}
					catch (Exception i) //io or null pointer depending on when the connection dies
					{
						Utils.logcat(Const.LOGE, decTag, "exception reading media: ");
						Utils.dumpException(decTag, i);

						//if the socket died it's impossible to continue the call.
						//the other person will get a dropped call and you can start the call again.
						//
						//kill the sockets so that UserHome's crash recovery will reinitialize them
						Vars.state = CallState.NONE;
						try
						{//must guarantee that the sockets are killed before going to the home screen. otherwise
							//the userhome's crash recovery won't kick in. don't leave it to dumb luck (race condition)
							new KillSocketsAsync().execute().get();
							onStop(); //see encoder thread for why onStop() is called in a try
						}
						catch (Exception e)
						{
							Utils.logcat(Const.LOGE, decTag, "Trying to kill sockets because of an exception but got another exception in the process");
							Utils.dumpException(decTag, e);
						}
					}
				}
				AmrDecoder.exit(amrstate);
				wavPlayer.stop();
				wavPlayer.flush(); //must flush after stop
				wavPlayer.release(); //mandatory cleanup to prevent wavPlayer from outliving its usefulness
				wavPlayer = null;
				Utils.logcat(Const.LOGD, decTag, "MediaCodec decoder thread has stopped, state:" + Vars.state);
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
		float y = event.values[1];
		float z = event.values[2];

		if(x <= 5)
		{
			//with there being no good information on turning the screen on and off
			//go with the next best thing of disabling all the buttons
			Utils.logcat(Const.LOGD, tag, "proximity sensor NEAR: " + x + " " + y + " "+ z);
			screenShowing = false;
			end.setEnabled(false);
			mic.setEnabled(false);
			speaker.setEnabled(false);
		}
		else
		{
			Utils.logcat(Const.LOGD, tag, "proximity sensor FAR: " + x + " " + y + " "+ z);
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
}
