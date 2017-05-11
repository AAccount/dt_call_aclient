package dt.call.aclient.screens;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.Timer;
import java.util.TimerTask;

import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.background.async.CallAcceptAsync;
import dt.call.aclient.background.async.CallRejectAsync;
import dt.call.aclient.sqlite.*;

public class CallIncoming extends AppCompatActivity implements View.OnClickListener
{

	private static final String tag = "CallIncoming";
	private static final long[] vibratePattern = new long[] {0, 400, 200};

	private FloatingActionButton accept, reject;
	private TextView callerid;
	private BroadcastReceiver myReceiver;
	private Timer counter = new Timer();
	private Uri ringtoneUri = null;
	private Ringtone ringtone = null;
	private Vibrator vibrator = null;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_call_incoming);

		//if the screen is off, turn it on and allow the screen to show when there is a lock screen
		//with password
		//https://stackoverflow.com/questions/20785608/starting-activity-from-service-on-lock-screen-turns-on-the-screen-but-does-not-s
		Window window = this.getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
		window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
		window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

		accept = (FloatingActionButton)findViewById(R.id.call_incoming_accept);
		accept.setOnClickListener(this);
		reject = (FloatingActionButton)findViewById(R.id.call_incoming_reject);
		reject.setOnClickListener(this);
		callerid = (TextView)findViewById(R.id.call_incoming_callerid);
		callerid.setText(Vars.callWith.toString());


		//when someone calls you but then decides to cancel before you've picked up.
		//or on a call timeout.
		myReceiver = new BroadcastReceiver()
		{
			@Override
			public void onReceive(Context context, Intent intent)
			{
				Utils.logcat(Const.LOGD, tag, "received a broadcast intent");
				String response = intent.getStringExtra(Const.BROADCAST_CALL_RESP);
				if(response.equals(Const.BROADCAST_CALL_END))
				{
					//go back to the home screen and clear the back history so there is no way to come back to
					//call CALLINCOMING
					goHome();
				}
				else if(response.equals(Const.BROADCAST_CALL_START))
				{
					Intent go2CallMain = new Intent(CallIncoming.this, CallMain.class);
					go2CallMain.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					startActivity(go2CallMain);
				}
			}
		};

		//take the initiative to stop ringing after a minute in case the connection went bad
		//and the server never says anything.
		//
		//because this timer is intended only for a single use, use the first run delay (param 2 of counter.schedule)
		//to prevent the timer from running immediately. set an arbitrary short period to give the timer time to get killed.
		//if the initial delay is run then the task will run immediately (record a missed call in onCreate) and possibly once again
		//when it's time to file the call as a missed call
		TimerTask counterTask = new TimerTask()
		{
			@Override
			public void run()
			{
				counter.cancel(); //it's served its purpose
				counter = null;
				SQLiteDb sqLiteDb = SQLiteDb.getInstance(getApplicationContext());
				sqLiteDb.insertHistory(new History(System.currentTimeMillis(), Vars.callWith, Const.CALLMISSED));
				onStop();
				goHome();
			}
		};
		counter.schedule(counterTask, Const.CALL_TIMEOUT*1000, 30*1000/*give 30 seconds for the task to complete*/);
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		registerReceiver(myReceiver, new IntentFilter(Const.BROADCAST_CALL));

		//safety checks before making a big scene
		if(ringtone != null && ringtone.isPlaying())
		{
			ringtone.stop();
		}
		if(vibrator != null)
		{
			vibrator.cancel();
		}

		//now time to make a scene
		AudioManager audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		switch(audioManager.getRingerMode())
		{
			case AudioManager.RINGER_MODE_NORMAL:
				ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
				ringtone = RingtoneManager.getRingtone(getApplicationContext(), ringtoneUri);
				ringtone.play();
				break;
			case AudioManager.RINGER_MODE_VIBRATE:
				vibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
				vibrator.vibrate(vibratePattern, 0);
				break;
			//no need for the dead silent case. if it is dead silent just light up the screen with no nothing
		}

	}

	@Override
	protected void onStop()
	{
		super.onStop();
		try
		{
			unregisterReceiver(myReceiver);
		}
		catch(IllegalArgumentException a)
		{
			Utils.logcat(Const.LOGW, tag, "don't unregister you get a leak, do unregister you get an exception... " + a.getMessage());
		}

		//stop the show
		if(ringtone != null && ringtone.isPlaying())
		{
			ringtone.stop();
		}

		if(vibrator != null)
		{
			vibrator.cancel();
		}

		//double check the counter to timeout is stopped or it will leak and crash when it's supposed to stop and this screen is gone
		if(counter != null)
		{
			counter.cancel();
			counter.purge();
		}
	}

	@Override
	public void onClick(View v)
	{
		//the only 2 buttons are accept and reject so no matter which button is clicked, it's safe to do
		//the reject and accept's common stuff no matter what. there will also every only be 1 click
		//(can't reject and change your mind you actually wanted to accpet)
		SQLiteDb sqLiteDb = SQLiteDb.getInstance(getApplicationContext());
		sqLiteDb.insertHistory(new History(System.currentTimeMillis(), Vars.callWith, Const.CALLINCOMING));
		if(counter != null)
		{
			counter.cancel(); //stop the "minute to answer" counter
			counter.purge();
			counter = null;
		}

		if(v == accept)
		{
			new CallAcceptAsync().execute();
			//need to wait for the server to say it's time to talk. don't assume it's immediately ready.
		}
		else if (v == reject)
		{
			new CallRejectAsync().execute();
			goHome();
		}
	}

	@Override
	public void onBackPressed()
	{
		/*
		 * Do nothing. There's nowhere to go back to
		 *
		 */
	}

	//go back to the UserHome screen. These few lines seem to be called in a few places.
	// reuse this code
	private void goHome()
	{
		Intent goHome = new Intent(this, UserHome.class);
		goHome.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(goHome);
	}
}
