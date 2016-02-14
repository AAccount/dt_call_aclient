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
import dt.call.aclient.background.Async.LoginAsync;

public class InitialUserInfo extends AppCompatActivity implements View.OnClickListener
{
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

			//retrieve the server information you got in the previous screen from shared preferences
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
					editor.commit();

					//save it to the session variables too, to avoid always doing a disk lookup with shared prefs
					Vars.uname = enteredUname;
					Vars.passwd = enteredPasswd;

					//go to the user information screen
					Intent go2Home = new Intent(InitialUserInfo.this, UserHome.class);
					startActivity(go2Home);
				}
				else
				{
					Utils.showOk(this, getString(R.string.alert_initial_user_cant_login));
				}
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
				Utils.logcat(Const.LOGE, getString(R.string.initial_user_title), "login interrupted");
				return;
			}
			catch (ExecutionException e)
			{
				e.printStackTrace();
				Utils.logcat(Const.LOGE, getString(R.string.initial_user_title), "login execution problem");
				return;
			}
		}
	}
}
