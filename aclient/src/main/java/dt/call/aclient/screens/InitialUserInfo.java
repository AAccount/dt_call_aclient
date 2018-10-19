package dt.call.aclient.screens;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.goterl.lazycode.lazysodium.interfaces.Box;

import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.background.async.LoginAsync;
import dt.call.aclient.sodium.SodiumUtils;

public class InitialUserInfo extends AppCompatActivity implements View.OnClickListener
{
	private static final String tag = "InitialUserInfo";

	private EditText uname;
	private Button privateKeyButton;
	private FloatingActionButton next;
	private BroadcastReceiver broadcastReceiver;
	private boolean gotPrivateKey = false;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_initial_user_info);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		uname = findViewById(R.id.initial_user_uname);
		privateKeyButton = findViewById(R.id.initial_user_private_key);
		privateKeyButton.setOnClickListener(this);
		privateKeyButton.setAllCaps(false);
		next = findViewById(R.id.initial_user_next);
		next.setOnClickListener(this);

		//load the saved information if it's there and preset the edittexts
		SharedPreferences sharedPreferences = getSharedPreferences(Const.PREFSFILE, MODE_PRIVATE);
		Vars.uname = sharedPreferences.getString(Const.PREF_UNAME, "");
		Vars.privateSodium = Utils.readDataDataFile(Const.INTERNAL_PRIVATEKEY_FILE, Box.SECRETKEYBYTES, this);
		if(Vars.privateSodium != null)
		{
			privateKeyButton.setText(getString(R.string.initial_user_got_user_private));
			gotPrivateKey = true;
		}

		if(!Vars.uname.equals(""))
		{
			uname.setText(Vars.uname);
		}

		//login can take up to ??30?? seconds. NEVER let the ui thread stall even if you can't do anything while waiting
		broadcastReceiver = new BroadcastReceiver()
		{
			@Override
			public void onReceive(Context context, Intent intent)
			{
				if(intent.getAction().equals(Const.BROADCAST_LOGIN))
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
		initialUserFilter.addAction(Const.BROADCAST_LOGIN);
		registerReceiver(broadcastReceiver, initialUserFilter);

		//check for storage permission to get your private key
		if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
		{
			AlertDialog.Builder mkdialog = new AlertDialog.Builder(this);
			mkdialog.setMessage(getString(R.string.alert_storage_perm))
					.setPositiveButton(R.string.alert_ok, new DialogInterface.OnClickListener()
					{
						@Override
						public void onClick(DialogInterface dialog, int which)
						{
							String[] perms = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
							ActivityCompat.requestPermissions(InitialUserInfo.this, perms, Const.PERM_STORAGE);
							dialog.cancel();
						}
					});
			AlertDialog showOkAlert = mkdialog.create();
			showOkAlert.show();
		}
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
			Vars.uname = uname.getText().toString();

			//don't continue if the user name and password are missing
			if(Vars.uname.equals("") || !gotPrivateKey)
			{
				Utils.showOk(this, getString(R.string.alert_initial_user_missing_uinfo));
				return;
			}

			LoginAsync.noNotificationOnFail = true;
			new LoginAsync().execute();
		}
		else if(v == privateKeyButton)
		{
			//https://stackoverflow.com/questions/7856959/android-file-chooser
			// Open a file chooser dialog. Alert dialog if no file managers found
			Intent fileDialog = new Intent(Intent.ACTION_GET_CONTENT);
			fileDialog.setType("*/*");
			fileDialog.addCategory(Intent.CATEGORY_OPENABLE);
			try
			{
				startActivityForResult(Intent.createChooser(fileDialog, getString(R.string.file_picker_user_private)), Const.SELECT_PRIVATE_SODIUM);
			}
			catch (ActivityNotFoundException a)
			{
				Utils.showOk(this, getString(R.string.alert_initial_server_no_caja));
			}
		}
	}

	@Override
	//when the file picker is done
	protected void onActivityResult(int requestCode, int result, Intent data)
	{
		//Only attempt to get the private key file path if Intent data has stuff in it.
		//	It won't have stuff in it if the user just clicks back.
		if(requestCode == Const.SELECT_PRIVATE_SODIUM && data != null)
		{
			Uri uri = data.getData();

			final byte[] keybytes = SodiumUtils.readKeyFileBytes(uri, this);
			final byte[] userPrivateKey = SodiumUtils.interpretKey(keybytes, true);
			if(userPrivateKey != null)
			{
				privateKeyButton.setText(getString(R.string.initial_user_got_user_private));
				gotPrivateKey = true;
			}
			else
			{
				Utils.showOk(this, getString(R.string.alert_corrupted_key));
			}
		}
	}

	private void processLoginResult(boolean ok)
	{
		if(ok)
		{
			String enteredUname = uname.getText().toString();

			//because the login was successful, save the info
			SharedPreferences sharedPreferences = getSharedPreferences(Const.PREFSFILE, MODE_PRIVATE);
			SharedPreferences.Editor editor = sharedPreferences.edit();
			editor.putString(Const.PREF_UNAME, enteredUname);
			editor.apply();

			final boolean writeok = Utils.writeDataDataFile(Const.INTERNAL_PRIVATEKEY_FILE, Vars.privateSodium, this);
			if(!writeok)
			{
				Utils.showOk(this, getString(R.string.initial_user_write_user_private_exception));
				//the app will still work but you'll have to reenter your private key when you start the app again
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

	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults)
	{
		switch(requestCode)
		{
			case Const.PERM_STORAGE:
			{
				if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED)
				{
					/*
					 * If you can't even read your own private key, there's no way to log in.
					 * Exit the app like a sore loser.
					 */
					Utils.quit(this);
				}
			}
		}
	}
}
