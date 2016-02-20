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
import android.media.MediaCodec;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import org.xiph.vorbis.decoder.DecodeFeed;
import org.xiph.vorbis.decoder.DecodeStreamInfo;
import org.xiph.vorbis.decoder.VorbisDecoder;
import org.xiph.vorbis.encoder.EncodeFeed;
import org.xiph.vorbis.encoder.VorbisEncoder;
import org.xiph.vorbis.player.VorbisPlayer;
import org.xiph.vorbis.recorder.VorbisRecorder;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import dt.call.aclient.CallState;
import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.background.Async.CallEndAsync;
import dt.call.aclient.background.Async.CallTimeoutAsync;
import dt.call.aclient.background.Pipe2Server;
import dt.call.aclient.background.PipeFromServer;

public class CallMain extends AppCompatActivity implements View.OnClickListener, SensorEventListener
{
	//https://stackoverflow.com/questions/26990816/mediarecorder-issue-on-android-lollipop
	//https://stackoverflow.com/questions/14437571/recording-audio-not-to-file-on-android
	private static final String tag = "CallMain";

	//various vorbis audio quality presets
	private static final int STEREO = 2;
	private static final int MONO = 1; //it's voice data... stereo is overkill
	private static final int BR96k = 96*1000; //2010 self test couldn't tell above 96kbps for top40 music
	private static final int BR128k = 128*1000; //typical pirated mp3 bitrate
	private static final int BR320k = 320*1000; //considered "higher quality" mp3
	private static final int FREQ441 = 44100; //standard sampling frequency

	//wave audio presets
	private static final int WAVSRC = MediaRecorder.AudioSource.MIC;
	private static final int WAVSTERO = AudioFormat.CHANNEL_IN_STEREO;
	private static final int WAVFORMAT = AudioFormat.ENCODING_PCM_16BIT;
	private static final int STREAMCALL = AudioManager.STREAM_VOICE_CALL;

	private FloatingActionButton end, mic, speaker;
	private boolean micMute = false, onSpeaker = false;
	private boolean screenShowing;
	private TextView status, callerid, time;
	private int min=0, sec=0;
	private Timer counter = new Timer();
	private BroadcastReceiver myReceiver;
	private AudioManager audioManager;
	private SensorManager sensorManager;
	private Sensor proximity;
	private VorbisRecorder vorbisRecorder;
	private VorbisPlayer vorbisPlayer;

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


		//set the ui to call mode: if you got to this screen after accepting an incoming call
		if(Vars.state == CallState.INCALL)
		{
			callMode();
		}

		//proximity sensor
		sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

		//https://stackoverflow.com/questions/12857817/how-to-play-audio-via-ear-phones-only-using-mediaplayer-android
		//make it possible to use the earpiece and to switch back and forth
		audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		audioManager.setMode(AudioManager.MODE_IN_CALL);
		audioManager.setSpeakerphoneOn(false);

		//Start showing the counter for how long it's taking to answer the call or how long
		//	the call has been going for
		TimerTask counterTask = new TimerTask()
		{
			@Override
			public void run()
			{
				if(sec == 60)
				{
					min++;
					sec = 0;
					if(Vars.state == CallState.INIT)
					{
						//if the person hasn't answered after 60 seconds give up. it's probably not going to happen.
						new CallTimeoutAsync().execute();
						Vars.state = CallState.NONE; //guarantee state == NONE. don't leave it to chance
						onStop();
					}
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
					callMode();
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
		 * Vars.state = NONE will be set before calling onStop() manually to guarnatee that onStop() sees
		 * the call state as none. The 2 async calls: end, timeout will set state = NONE, BUT BECAUSE they're
		 * async there is a chance that onStop() is called before the async is. Don't leave it to dumb luck.
		 */
		if(Vars.state == CallState.NONE)
		{
			//stop the sending audio to the server
			vorbisRecorder.stop();

			//stop getting audio from the server
			vorbisPlayer.stop();

			//no longer in a call
			audioManager.setMode(AudioManager.MODE_NORMAL);
			counter.cancel();

			try
			{
				unregisterReceiver(myReceiver);
			}
			catch (IllegalArgumentException i)
			{
				Utils.logcat(Const.LOGW, tag, "don't unregister you get a leak, do unregister you get an exception... " + i.getMessage());
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
			new CallEndAsync(getApplicationContext()).execute();
			Vars.state = CallState.NONE; //guarantee onStop sees state == NONE
			onStop();
		}
		else if (v == mic)
		{
			micMute = !micMute;
			if(micMute)
			{
				mic.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_mic_off_white_48dp));
				vorbisRecorder.stop();
			}
			else
			{
				mic.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_mic_white_48dp));
				startRecorder();
			}
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

	private void callMode()
	{
		//setup the ui to call mode
		getSupportActionBar().setBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(CallMain.this, R.color.material_green)));
		status.setText(getString(R.string.call_main_status_incall));
		mic.setEnabled(true);
		speaker.setEnabled(true);
		startRecorder();

		//same idea here: this class is only relevant here
		class VorbisDecoderAsync extends AsyncTask<String, String, String>
		{
			private static final String tag = "VorbisDecoderAsync(CallMain)";
			private AudioTrack wavPlayer;

			@Override
			protected String doInBackground(String... params)
			{
				wavPlayer = new AudioTrack(STREAMCALL, FREQ441, WAVSTERO, WAVFORMAT, 1024*2, AudioTrack.MODE_STREAM);
				DecodeFeed decodeFeed = new DecodeFeed()
				{
					@Override
					public int readVorbisData(byte[] buffer, int amountToWrite)
					{
						try
						{
							Vars.mediaSocket.getInputStream().read(buffer, 0, amountToWrite);
						}
						catch (IOException i)
						{
							Utils.logcat(Const.LOGE, tag, "ioexception reading media: " + Utils.dumpException(i));
						}
						return 0;
					}

					@Override
					public void writePCMData(short[] pcmData, int amountToRead)
					{
						wavPlayer.write(pcmData, 0, amountToRead);
					}

					@Override
					public void stop()
					{
						wavPlayer.stop();
						wavPlayer.release();
					}

					@Override
					public void startReadingHeader()
					{

					}

					@Override
					public void start(DecodeStreamInfo decodeStreamInfo)
					{

					}
				};
				Handler playbackHandler = new Handler()
				{
					@Override
					public void handleMessage(Message msg)
					{
						switch (msg.what)
						{
							case VorbisPlayer.PLAYING_FAILED:
								Utils.logcat(Const.LOGE, tag, "vorbis player failed");
								break;
							case VorbisPlayer.PLAYING_FINISHED:
								Utils.logcat(Const.LOGD, tag, "vorbis player finished");
								break;
							case VorbisPlayer.PLAYING_STARTED:
								Utils.logcat(Const.LOGD, tag, "vorbis player started");
								break;
						}
					}
				};
				vorbisPlayer = new VorbisPlayer(decodeFeed, playbackHandler);
				return null;
			}
		};
		new VorbisDecoderAsync().execute();

	}

	//its own function because there are 2 places where the recorder is started: mic on/off button
	//and the initial start of the record
	private void startRecorder()
	{
		//can't do network on the main thread. create an inner class because this functionality is only
		//relevant in CallMain and also would be nice to keep 1 less thing (VorbisRecorder) out of Vars
		class VorbisEncoderAsync extends AsyncTask<String, String, String>
		{
			private static final String tag = "VorbisEncoderAsync(CallMain)";
			private AudioRecord wavRecorder;

			@Override
			protected String doInBackground(String... params)
			{
				//https://stackoverflow.com/questions/8499042/android-audiorecord-example
				wavRecorder = new AudioRecord(WAVSRC, FREQ441, WAVSTERO, WAVFORMAT, 1024*2);
				EncodeFeed encodeFeed = new EncodeFeed()
				{
					@Override
					public long readPCMData(byte[] pcmDataBuffer, int amountToWrite)
					{
						wavRecorder.read(pcmDataBuffer, 0, amountToWrite);
						return 0;
					}

					@Override
					public int writeVorbisData(byte[] vorbisData, int amountToRead)
					{
						try
						{
							Vars.mediaSocket.getOutputStream().write(vorbisData, 0, amountToRead);
						}
						catch (IOException i)
						{
							Utils.logcat(Const.LOGE, tag, "ioexception writing vorbis to server: " + Utils.dumpException(i));
						}
						return 0;
					}

					@Override
					public void stop()
					{
						wavRecorder.stop();
						wavRecorder.release();
					}

					@Override
					public void stopEncoding()
					{

					}

					@Override
					public void start()
					{
					}
				};
				Handler recordHandler = new Handler()
				{
					@Override
					public void handleMessage(Message msg)
					{//copied directly from the vorbis demo
						switch (msg.what)
						{
							case VorbisRecorder.START_ENCODING:
								Utils.logcat(Const.LOGD, tag, "Starting to encode");
								break;
							case VorbisRecorder.STOP_ENCODING:
								Utils.logcat(Const.LOGD, tag, "Stopping the encoder");
								break;
							case VorbisRecorder.UNSUPPORTED_AUDIO_TRACK_RECORD_PARAMETERS:
								Utils.logcat(Const.LOGE, tag, "You're device does not support this configuration");
								break;
							case VorbisRecorder.ERROR_INITIALIZING:
								Utils.logcat(Const.LOGE, tag, "There was an error initializing.  Try changing the recording configuration");
								break;
							case VorbisRecorder.FAILED_FOR_UNKNOWN_REASON:
								Utils.logcat(Const.LOGE, tag, "The encoder failed for an unknown reason!");
								break;
							case VorbisRecorder.FINISHED_SUCCESSFULLY:
								Utils.logcat(Const.LOGD, tag, "The encoder has finished successfully");
								break;
						}
					}
				};
				vorbisRecorder = new VorbisRecorder(encodeFeed, recordHandler);
				vorbisRecorder.start(FREQ441, STEREO, BR128k);
				return null;
			}
		}
		new VorbisEncoderAsync().execute();
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
		float distance = event.values[0];
		if(distance == 0)
		{
			//with there being no good information on turning the screen on and off
			//go with the next best thing of disabling all the buttons
			Utils.logcat(Const.LOGD, tag, "proximity sensor NEAR: " + distance);
			screenShowing = false;
			end.setEnabled(false);
			mic.setEnabled(false);
			speaker.setEnabled(false);
		}
		else
		{
			Utils.logcat(Const.LOGD, tag, "proximity sensor FAR: " + distance);
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
