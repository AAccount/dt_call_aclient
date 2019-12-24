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
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;
import java.util.Timer;
import java.util.TimerTask;

import dt.call.aclient.CallState;
import dt.call.aclient.Const;
import dt.call.aclient.Voip.SoundEffects;
import dt.call.aclient.Voip.Voice;
import dt.call.aclient.background.async.OperatorCommand;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;

public class CallMain extends AppCompatActivity implements View.OnClickListener, SensorEventListener
{
	private static final String WAKELOCK_INCALLA9 = "dt.call.aclient:incalla9";
	private static final String tag = "CallMain";
	public static final String DIALING_MODE = "DIALING_MODE";

	private LinearLayout micContainer, spkContainer, acceptContainer;
	private FloatingActionButton end, mic, speaker, accept;
	private Button stats;
	private boolean onSpeaker = false;
	private boolean screenShowing;
	private ImageView userImage;
	private TextView status, time, callerid;
	private int min=0, sec=0;
	private Timer counter = new Timer();
	private BroadcastReceiver myReceiver;
	private String missingLabel, garbageLabel, txLabel, rxLabel, rxSeqLabel, txSeqLabel, skippedLabel, oorangeLabel;
	private boolean showStats = false;
	private boolean isDialing;
	private boolean selfEndedCall = false;
	private boolean finalStopRan = false;

	private AudioManager audioManager;
	private SensorManager sensorManager;
	private Sensor proximity = null;

	private final DecimalFormat decimalFormat = new DecimalFormat("#.###");
	private final StringBuilder statsBuilder = new StringBuilder();
	private final StringBuilder timeBuilder = new StringBuilder();

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
		micContainer = findViewById(R.id.call_main_mic_container);
		mic = findViewById(R.id.call_main_mic);
		mic.setOnClickListener(this);
		mic.setEnabled(false);
		spkContainer = findViewById(R.id.call_main_spk_container);
		speaker = findViewById(R.id.call_main_spk);
		speaker.setOnClickListener(this);
		speaker.setEnabled(false);
		acceptContainer = findViewById(R.id.call_main_accept_container);
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
		audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

		TimerTask counterTask = new TimerTask()
		{
			@Override
			public void run()
			{

				//if the person hasn't answered after 20 seconds give up. it's probably not going to happen.
				if((Vars.state == CallState.INIT) && (sec == Const.CALL_TIMEOUT))
				{
					new OperatorCommand().execute(OperatorCommand.END);
					onStopWrapper();
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

				String micEnable = intent.getStringExtra(Const.BROADCAST_CALL_MIC);
				if(micEnable != null)
				{
					if(micEnable.equals(Boolean.TRUE.toString()))
					{
						mic.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_mic_white_48dp));
					}
					else
					{
						mic.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_mic_off_white_48dp));
					}
				}
			}
		};
		registerReceiver(myReceiver, new IntentFilter(Const.BROADCAST_CALL));

		if(isDialing && Vars.state == CallState.INIT)
		{
			acceptContainer.setVisibility(View.GONE);
			SoundEffects.getInstance().playDialtone();
		}
		else if(!isDialing && Vars.state == CallState.INIT)
		{
			SoundEffects.getInstance().playRingtone();
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
			spkContainer.setVisibility(View.GONE);
			micContainer.setVisibility(View.GONE);
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
		if(Vars.state == CallState.NONE && !finalStopRan)
		{
			finalStopRan = true;
			if(selfEndedCall) //don't send call end to the server if you were told by the server to stop.
			{
				new OperatorCommand().execute(OperatorCommand.END);
			}

			//for cases when you make a call but decide you don't want to anymore
			SoundEffects.getInstance().stopDialtone();
			SoundEffects.getInstance().stopRingtone();
			Voice.getInstance().stop();

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

			SoundEffects.getInstance().playEndTone();

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
			selfEndedCall = true;
			onStopWrapper();
		}
		else if (v == mic)
		{
			//update the mic icon in the encoder thread when the actual mute/unmute happens.
			//it can take up to 1 second to change the status because of the sleep when going from mute-->unmute
			Toast checkMic = Toast.makeText(this, getString(R.string.call_main_toast_micstatus), Toast.LENGTH_LONG);
			checkMic.show();
			Voice.getInstance().toggleMic();
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
			new OperatorCommand().execute(OperatorCommand.ACCEPT);
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
			Voice voice = Voice.getInstance();
			final String rxDisp=formatInternetMeteric(voice.getRxData()), txDisp=formatInternetMeteric(voice.getTxData());
			final int missing = voice.getTxSeq()-voice.getRxSeq();
			statsBuilder.setLength(0);
			statsBuilder
					.append(missingLabel).append(": ").append(missing > 0 ? missing : 0).append(" ").append(garbageLabel).append(": ").append(voice.getGarbage()).append("\n")
					.append(rxLabel).append(":").append(rxDisp).append(" ").append(txLabel).append(":").append(txDisp).append("\n")
					.append(rxSeqLabel).append(":").append(voice.getRxSeq()).append(" ").append(txSeqLabel).append(":").append(voice.getTxSeq()).append("\n")
					.append(skippedLabel).append(":").append(voice.getSkipped()).append(" ").append(oorangeLabel).append(": ").append(voice.getOorange());
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

	private void changeToCallMode()
	{
		micContainer.setVisibility(View.VISIBLE);
		spkContainer.setVisibility(View.VISIBLE);
		acceptContainer.setVisibility(View.GONE);
		accept.setEnabled(false);

		if(isDialing)
		{
			SoundEffects.getInstance().stopDialtone();
		}
		else
		{
			SoundEffects.getInstance().stopRingtone();
		}

		//it is impossible to change the status bar @ run time below 5.0, only the action bar color.
		//results in a weird blue/green look. only change the look for >= 5.0
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
		{
			final ActionBar actionBar = getSupportActionBar();
			if(actionBar != null)
			{
				actionBar.setBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(CallMain.this, R.color.material_green)));
			}
			Window window = getWindow();
			window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
			window.setStatusBarColor(ContextCompat.getColor(CallMain.this, R.color.material_dark_green));
			window.setNavigationBarColor(ContextCompat.getColor(CallMain.this, R.color.material_dark_green));
		}

		status.setText(getString(R.string.call_main_status_incall));
		mic.setEnabled(true);
		speaker.setEnabled(true);

		Voice.getInstance().start();
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
}