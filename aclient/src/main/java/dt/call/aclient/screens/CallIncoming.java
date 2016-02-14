package dt.call.aclient.screens;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
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
import dt.call.aclient.background.Async.CallAcceptAsync;
import dt.call.aclient.background.Async.CallRejectAsync;

public class CallIncoming extends AppCompatActivity implements View.OnClickListener
{

	private static final String tag = "CallIncoming";

	private FloatingActionButton accept, reject;
	private TextView callerid;
	private BroadcastReceiver myReceiver;
	private Timer counter = new Timer();

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
		if(Vars.callWith.hasNickname())
		{
			callerid.setText(Vars.callWith.getNickname());
		}
		else
		{
			callerid.setText(Vars.callWith.getName());
		}

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
					//call incoming
					Intent goHome = new Intent(CallIncoming.this, UserHome.class);
					goHome.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					startActivity(goHome);
				}
			}
		};

		//take the initiative to stop ringing after a minute + 3 seconds in case the connection went bad
		//and the server never says anything.
		TimerTask counterTask = new TimerTask()
		{
			@Override
			public void run()
			{
				counter.cancel(); //it's served its purpose
				onStop();
			}
		};
		counter.schedule(counterTask, 0, 63*1000);
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		registerReceiver(myReceiver, new IntentFilter(Const.BROADCAST_CALL));
		//TODO: start the ringtone
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
		//TODO: stop the ringtone
	}

	@Override
	public void onClick(View v)
	{
		if(v == accept)
		{
			new CallAcceptAsync().execute();
			Intent go2CallMain = new Intent(this, CallMain.class);
			go2CallMain.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(go2CallMain);
		}
		else if (v == reject)
		{
			new CallRejectAsync().execute();
			Intent goHome = new Intent(this, UserHome.class);
			goHome.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(goHome);
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
}
