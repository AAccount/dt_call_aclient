package dt.call.aclient.screens;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;

import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.background.async.LoginAsync;

public class InitialUserInfo extends AppCompatActivity implements View.OnClickListener
{
	private static final String tag = "InitialUserInfo";
	private EditText uname, passwd;
	private FloatingActionButton next;
	private BroadcastReceiver broadcastReceiver;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_initial_user_info);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		uname = (EditText)findViewById(R.id.initial_user_uname);
		passwd = (EditText)findViewById(R.id.initial_user_passwd);
		next = (FloatingActionButton)findViewById(R.id.initial_user_next);
		next.setOnClickListener(this);

		//load the saved information if it's there and preset the edittexts
		SharedPreferences sharedPreferences = getSharedPreferences(Const.PREFSFILE, MODE_PRIVATE);
		String savedUname = sharedPreferences.getString(Const.PREF_UNAME, "");
		String savedPasswd = sharedPreferences.getString(Const.PREF_PASSWD, "");

		if(!savedUname.equals(""))
		{
			uname.setText(savedUname);
		}
		if(!savedPasswd.equals(""))
		{
			passwd.setText(savedPasswd);
		}

		//login can take up to ??30?? seconds. NEVER let the ui thread stall even if you can't do anything while waiting
		broadcastReceiver = new BroadcastReceiver()
		{
			@Override
			public void onReceive(Context context, Intent intent)
			{
				if(intent.getAction().equals(Const.BROADCAST_LOGIN_FG))
				{
					final boolean ok = intent.getBooleanExtra(Const.BROADCAST_LOGIN_RESULT, false);
					processLoginResult(ok);
				}
			}
		};
	}

	@Override
	public void onResume()
	{
		super.onResume();
		IntentFilter initialUserFilter = new IntentFilter();
		initialUserFilter.addAction(Const.BROADCAST_LOGIN_FG);
		registerReceiver(broadcastReceiver, initialUserFilter);
	}

	@Override
	protected void onStop()
	{
		super.onStop();
		unregisterReceiver(broadcastReceiver);
	}

	@Override
	public void onClick(View v)
	{
		if(v == next)
		{
			String enteredUname = uname.getText().toString();
			String enteredPasswd = passwd.getText().toString();

			//don't continue if the user name and password are missing
			if(enteredUname.equals("") || enteredPasswd.equals(""))
			{
				Utils.showOk(this, getString(R.string.alert_initial_user_missing_uinfo));
				return;
			}

			new LoginAsync(enteredUname, enteredPasswd, Const.BROADCAST_LOGIN_FG).execute();

		}
	}

	private void processLoginResult(boolean ok)
	{
		if(ok)
		{
			String enteredUname = uname.getText().toString();
			String enteredPasswd = passwd.getText().toString();

			//because the login was successful, save the info
			SharedPreferences sharedPreferences = getSharedPreferences(Const.PREFSFILE, MODE_PRIVATE);
			SharedPreferences.Editor editor = sharedPreferences.edit();
			editor.putString(Const.PREF_UNAME, enteredUname);
			editor.putString(Const.PREF_PASSWD, enteredPasswd);
			editor.apply();

			//save it to the session variables too, to avoid always doing a disk lookup with shared prefs
			Vars.uname = enteredUname;
			Vars.passwd = enteredPasswd;

			//go to the user information screen
			Intent go2Home = new Intent(InitialUserInfo.this, UserHome.class);
			startActivity(go2Home);
		}
		else
		{
			Utils.showOk(this, getString(R.string.alert_login_failed));
		}
	}
}
