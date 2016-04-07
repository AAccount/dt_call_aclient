package dt.call.aclient.screens;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;

import java.util.concurrent.ExecutionException;

import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.background.Async.KillSocketsAsync;
import dt.call.aclient.background.Async.LoginAsync;
import dt.call.aclient.background.CmdListener;

public class InitialUserInfo extends AppCompatActivity implements View.OnClickListener
{
	private static final String tag = "InitialUserInfo";
	private EditText uname, passwd;
	private FloatingActionButton next;

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
		String savedUname = sharedPreferences.getString(Const.UNAME, "");
		String savedPasswd = sharedPreferences.getString(Const.PASSWD, "");

		if(!savedUname.equals(""))
		{
			uname.setText(savedUname);
		}
		if(!savedPasswd.equals(""))
		{
			passwd.setText(savedPasswd);
		}
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

			try
			{
				boolean loginOk = new LoginAsync(enteredUname, enteredPasswd).execute().get();
				if(loginOk)
				{
					//because the login was successful, save the info
					SharedPreferences sharedPreferences = getSharedPreferences(Const.PREFSFILE, MODE_PRIVATE);
					SharedPreferences.Editor editor = sharedPreferences.edit();
					editor.putString(Const.UNAME, enteredUname);
					editor.putString(Const.PASSWD, enteredPasswd);
					editor.apply();

					//save it to the session variables too, to avoid always doing a disk lookup with shared prefs
					Vars.uname = enteredUname;
					Vars.passwd = enteredPasswd;

					//start the command listener --> the one who does the login is responsible for starting the command listener
					synchronized (Vars.cmdListenerLock)
					{
						if (Vars.cmdListenerRunning)
						{//for whatever freak reason
							Utils.logcat(Const.LOGW, tag, "command listener was already running. before logging in ??");
							Vars.dontRestart = true;
							new KillSocketsAsync().execute().get(); //the result is meaningless but can't continue with this currently running
							Vars.cmdListenerRunning = false;
						}

						Intent cmdListenerIntent = new Intent(this, CmdListener.class);
						startService(cmdListenerIntent);
						Vars.cmdListenerRunning = true;

						Intent startHeartbeat = new Intent(Const.BROADCAST_BK_HEARTBEAT);
						startHeartbeat.putExtra(Const.BROADCAST_BK_HEARTBEAT_DOIT, true);
						sendBroadcast(startHeartbeat);
					}

					//go to the user information screen
					Intent go2Home = new Intent(InitialUserInfo.this, UserHome.class);
					startActivity(go2Home);
				}
				else
				{
					Utils.showOk(this, getString(R.string.alert_login_failed));
				}
			}
			catch (Exception e)
			{
				Utils.dumpException(tag, e);
				Utils.showOk(this, getString(R.string.alert_login_failed));
			}
		}
	}
}
