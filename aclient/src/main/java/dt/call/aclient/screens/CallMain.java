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
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import org.xiph.vorbis.decoder.DecodeFeed;
import org.xiph.vorbis.decoder.DecodeStreamInfo;
import org.xiph.vorbis.encoder.EncodeFeed;
import org.xiph.vorbis.player.VorbisPlayer;
import org.xiph.vorbis.recorder.VorbisRecorder;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
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

public class CallMain extends AppCompatActivity implements View.OnClickListener, SensorEventListener
{
	//https://stackoverflow.com/questions/26990816/mediarecorder-issue-on-android-lollipop
	//https://stackoverflow.com/questions/14437571/recording-audio-not-to-file-on-android
	private static final String tag = "CallMain";
	private static final boolean ENABLE_VORBISLOG = true; //this level of logcat shouldn't normally be needed when logcat is enabled

	//various vorbis audio quality presets
	/**
	 * Vorbis chosen because
	 * 	-I can actually find a library to do it (can't use mediarecorder, mediaplayer for unknown reasons)
	 * 	-Doesn't have all the legal BS associated with other codecs
	 * 		... the worst, stupidest, most artificial, most despicable, least respectable, moronic type of BS
	 * 	-Sounds as good as aac, mp3 according to my ears
	 */
	private static final int STEREO = 2;
	private static final int MONO = 1; //it's voice data... stereo is overkill
	private static final int BR96k = 96*1000; //2010 self test couldn't tell above 96kbps for top40 music
	private static final int BR128k = 128*1000; //typical pirated mp3 bitrate
	private static final int BR320k = 320*1000; //considered "high quality" mp3
	private static final int FREQ441 = 44100; //standard sampling frequency

	//wave audio presets: uses its own variables for stereo, format etc
	private static final int WAVSRC = MediaRecorder.AudioSource.MIC;
	private static final int WAVSTERO = AudioFormat.CHANNEL_IN_STEREO;
	private static final int WAVFORMAT = AudioFormat.ENCODING_PCM_16BIT;
	private static final int STREAMCALL = AudioManager.STREAM_MUSIC;

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
		audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
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
		 * Vars.state = NONE will be set before calling onStop() manually to guarantee that onStop() sees
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
		try
		{//apparently this line has the possibility of a null pointer exception as warned by android studio...???
			getSupportActionBar().setBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(CallMain.this, R.color.material_green)));
		}
		catch (NullPointerException n)
		{
			Utils.logcat(Const.LOGE, tag, "null pointer changing action bar to green: " + Utils.dumpException(n));
		}
		status.setText(getString(R.string.call_main_status_incall));
		mic.setEnabled(true);
		speaker.setEnabled(true);
		startRecorder();


		//same idea here: this class is only relevant here
		class VorbisDecoderAsync extends AsyncTask<String, String, String>
		{
			private static final String tag = "VorbisDecoderAsync(CallMain)";
			private AudioTrack wavPlayer;

			public VorbisDecoderAsync()
			{
				wavPlayer = new AudioTrack(STREAMCALL, FREQ441, WAVSTERO, WAVFORMAT, 1024*2, AudioTrack.MODE_STREAM);
			}
			@Override
			protected String doInBackground(String... params)
			{
				DecodeFeed decodeFeed = new DecodeFeed()
				{
					@Override
					/**
					 * ==============================THIS FUNCTION IS VERY PICKY===============================
					 * ===================== READ THESE JAVADOCS BEFORE TOUCHING ==============================
					 * @param buffer: the buffer sent to the native vorbis library which will be converted to wav.
					 * @param amountToWrite: the amount of bytes the native vorbis library expects.
					 *                     you MUST send it this many bytes or else the library will CRAP OUT AND STOP
					 * @return: the amount of vorbis bytes you read. MUST: return == amountToWrite for the decoding to work
					 */
					public int readVorbisData(byte[] buffer, int amountToWrite)
					{
						int totalRead=0, dataRead;
						try
						{
							//buffer read the buffer... :-| to guarantee you have amountToWrite bytes to send to the native library
							while(totalRead < amountToWrite)
							{
								dataRead = Vars.mediaSocket.getInputStream().read(buffer, totalRead, amountToWrite-totalRead);
								totalRead = totalRead + dataRead;
								vorbisLogcat(Const.LOGD, tag, "got " + dataRead + "bytes this round, total: " + totalRead);
							}
							vorbisLogcat(Const.LOGD, tag, "buffer: " + new String(buffer));
						}
						catch (IOException i)
						{
							Utils.logcat(Const.LOGE, tag, "ioexception reading media: " + Utils.dumpException(i));
						}

						//as stated in the javadoc, you MUST fill the buffer with amountToWrite bytes of vorbis data.
						//you must confirm to the native library (using return) that you're sending amountToWrite bytes.
						//if you don't send back that much bytes, the library will fail
						if(totalRead != amountToWrite)
						{
							int missing = amountToWrite - totalRead;
							Utils.logcat(Const.LOGE, tag, "vorbis data missing " + missing + "bytes. VORBIS LIBRARY WILL FAIL IN 3 2 1...");
						}
						return totalRead;
					}

					@Override
					public void writePCMData(short[] pcmData, int amountToRead)
					{
						wavPlayer.write(pcmData, 0, amountToRead);
						wavPlayer.play();
						vorbisLogcat(Const.LOGD, tag, "converted vorbis to wav");
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
						wavPlayer.play(); //won't actually start playing because no audio has been written but just get it ready
					}
				};
				//vorbis encoder/decoder already spit out error messages when necessary.
				//there is nothing to be gained by implementing a handler for the messages other than redundant logcat
				//avoid the handler memeory error warning.
				vorbisPlayer = new VorbisPlayer(decodeFeed);
				vorbisPlayer.start();
				return null;
			}
		}
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
				EncodeFeed encodeFeed = new EncodeFeed()
				{
					@Override
					//amountToWrite is the amount of wav data that ??must?? be read to satisfy the native library
					//since you're reading from the microphone, there shouldn't be any delays or other weirdness so assume
					//	that you get the amount of wav data you expect
					public long readPCMData(byte[] pcmDataBuffer, int amountToWrite)
					{
						int amountRead;
						amountRead = wavRecorder.read(pcmDataBuffer, 0, amountToWrite);
						vorbisLogcat(Const.LOGD, tag, amountRead + " bytes of raw wav read");
						return amountRead;
					}

					@Override
					public int writeVorbisData(byte[] vorbisData, int amountToRead)
					{
						try
						{
							//BE CAREFUL!!! the vorbisData buffer isn't always filled to the max with vorbis
							//	data. make sure you don't send the extra space in the buffer (bunch of zeros).
							//	specify to socket write the amount of data in vorbisData that is ACTUALLY vorbis
							//	data and not extra space
							Vars.mediaSocket.getOutputStream().write(vorbisData, 0, amountToRead);
							vorbisLogcat(Const.LOGD, tag, "sent " + amountToRead + " voice data out");
						}
						catch (IOException i)
						{
							Utils.logcat(Const.LOGE, tag, "ioexception writing vorbis to server: " + Utils.dumpException(i));
							return 0; //network problem... aka dropped call
							//TODO: stop the call if there is a network problem
						}
						return amountToRead;
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
						//https://stackoverflow.com/questions/8499042/android-audiorecord-example
						wavRecorder = new AudioRecord(WAVSRC, FREQ441, WAVSTERO, WAVFORMAT, 1024*2);
						wavRecorder.startRecording();
					}
				};
				vorbisRecorder = new VorbisRecorder(encodeFeed);
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
		if(distance <= 5)
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

	//for debugging vorbis encoder/decoder internals. produces excessive logcat spam
	//only turn this on when necessary
	private void vorbisLogcat(int level, String tag, String message)
	{
		if(ENABLE_VORBISLOG)
		{
			Utils.logcat(level, tag, message);
		}
	}
}
