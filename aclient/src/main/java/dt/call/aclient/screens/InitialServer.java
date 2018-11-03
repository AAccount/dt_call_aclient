package dt.call.aclient.screens;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.goterl.lazycode.lazysodium.interfaces.Box;

import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.background.BackgroundManager;
import dt.call.aclient.sodium.SodiumUtils;

public class InitialServer extends AppCompatActivity implements View.OnClickListener
{
	private static final String tag = "Initial Server";

	private EditText addr, commandPort, mediaPort;
	private Button sodium;
	private FloatingActionButton next;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

		Vars.applicationContext = getApplicationContext();
		Utils.startBG2();

		//turn on BackgroundManager just in case it was turned off by the logout button
		final ComponentName backgroundManager = new ComponentName(this, BackgroundManager.class);
		getPackageManager().setComponentEnabledSetting(backgroundManager, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

		Utils.loadPrefs();

		if(!Vars.uname.equals("") && Vars.selfPrivateSodium != null)
		{
			Utils.logcat(Const.LOGD, tag, "Skipping to the home screen");

			//don't need to start the command listener or do the login async. that will be started on the home screen

			//jump to the home screen
			final Intent skip2Home = new Intent(this, UserHome.class);
			startActivity(skip2Home);
			return;
		}

		//Because this is the launcher activity, the name in AndroidManifest is what shows
		//	up in the start menu. Don't want "Server Settings" as the app name
		final ActionBar ab = getSupportActionBar();
		ab.setTitle(getString(R.string.initial_server_title));

		//setup server information activity if the information hasn't already been saved
		setContentView(R.layout.activity_initial_server);
		addr = findViewById(R.id.initial_server_addr);
		commandPort = findViewById(R.id.initial_server_command);
		mediaPort = findViewById(R.id.initial_server_media);
		sodium = findViewById(R.id.initial_server_sodium);
		sodium.setOnClickListener(this);
		sodium.setAllCaps(false);
		next = findViewById(R.id.initial_server_next);
		next.setOnClickListener(this);

		if(!Vars.serverAddress.equals(""))
		{
			addr.setText(Vars.serverAddress);
		}
		if(Vars.commandPort != 0)
		{
			commandPort.setText(String.valueOf(Vars.commandPort));
		}
		if(Vars.mediaPort != 0)
		{
			mediaPort.setText(String.valueOf(Vars.mediaPort));
		}
		Vars.serverPublicSodium = Utils.readDataDataFile(Const.INTERNAL_SERVER_PUBLICKEY_FILE, Box.PUBLICKEYBYTES, this);
		if(Vars.serverPublicSodium != null)
		{
			sodium.setText(getString(R.string.initial_server_got_server_public));
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		//check for storage access permission. no point of a tls1.2 connection if the server is someone else
		if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
		{
			final AlertDialog.Builder mkdialog = new AlertDialog.Builder(this);
			mkdialog.setMessage(getString(R.string.alert_storage_perm))
					.setPositiveButton(R.string.alert_ok, new DialogInterface.OnClickListener()
					{
						@Override
						public void onClick(DialogInterface dialog, int which)
						{
							String[] perms = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
							ActivityCompat.requestPermissions(InitialServer.this, perms, Const.PERM_STORAGE);
							dialog.cancel();
						}
					});
			final AlertDialog showOkAlert = mkdialog.create();
			showOkAlert.show();
		}
	}

	@Override
	public void onClick(View v)
	{
		if(v == sodium)
		{
			//https://stackoverflow.com/questions/7856959/android-file-chooser
			// Open a file chooser dialog. Alert dialog if no file managers found
			final Intent fileDialog = new Intent(Intent.ACTION_GET_CONTENT);
			fileDialog.setType("*/*");
			fileDialog.addCategory(Intent.CATEGORY_OPENABLE);
			try
			{
				startActivityForResult(Intent.createChooser(fileDialog, getString(R.string.file_picker_server_sodium_public)), Const.SELECT_SERVER_PUBLIC_SODIUM);
			}
			catch (ActivityNotFoundException a)
			{
				Utils.showOk(this, getString(R.string.alert_initial_server_no_caja));
			}
		}
		else if(v == next)
		{
			//extract UI info
			Vars.serverAddress = addr.getText().toString();
			final String commandString = commandPort.getText().toString();
			final String mediaString = mediaPort.getText().toString();

			//check to make sure all the required information is filled in
			final boolean allFilled = !Vars.serverAddress.equals("") && !commandString.equals("") && !mediaString.equals("") && Vars.serverPublicSodium != null;
			if(!allFilled)
			{
				Utils.showOk(this, getString(R.string.alert_initial_server_incomplete_server));
				return;
			}

			//check to make sure the ports are valid
			boolean commandValid, mediaValid;
			try
			{
				Vars.commandPort = Integer.valueOf(commandString);
				Vars.mediaPort = Integer.valueOf(mediaString);
				commandValid = Vars.commandPort > 0 && Vars.commandPort < 65536;
				mediaValid = Vars.mediaPort > 0 && Vars.mediaPort < 65536;
			}
			catch (NumberFormatException n)
			{
				commandValid = false;
				mediaValid = false;
			}
			if(!commandValid || ! mediaValid)
			{
				Utils.showOk(this, getString(R.string.alert_initial_server_invalid_port));
				return;
			}

			//Store all server information in shared preferences.
			final SharedPreferences sharedPreferences = getSharedPreferences(Const.PREFSFILE, Context.MODE_PRIVATE);
			final SharedPreferences.Editor editor = sharedPreferences.edit();
			editor.putString(Const.PREF_ADDR, Vars.serverAddress);
			editor.putString(Const.PREF_COMMANDPORT, commandString);
			editor.putString(Const.PREF_MEDIAPORT, mediaString);
			final boolean writeok = Utils.writeDataDataFile(Const.INTERNAL_SERVER_PUBLICKEY_FILE, Vars.serverPublicSodium, this);
			if(!writeok)
			{
				Utils.showOk(this, getString(R.string.initial_server_write_server_public_exception));
			}
			editor.apply();

			//go to the user information screen
			final Intent initUser = new Intent(this, InitialUserInfo.class);
			startActivity(initUser);
		}
	}

	@Override
	//when the file picker is done
	protected void onActivityResult(int requestCode, int result, Intent data)
	{
		//Only attempt to get the certificate file path if Intent data has stuff in it.
		//	It won't have stuff in it if the user just clicks back.
		Uri uri = data.getData();
		if(requestCode == Const.SELECT_SERVER_PUBLIC_SODIUM && data != null)
		{
			byte[] keybytes = SodiumUtils.readKeyFileBytes(uri, this);
			Vars.serverPublicSodium = SodiumUtils.interpretKey(keybytes, false);
			if(Vars.serverPublicSodium != null)
			{
				sodium.setText(getString(R.string.initial_server_got_server_public));
			}
			else
			{
				Utils.showOk(this, getString(R.string.alert_corrupted_cert));
			}
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
					 * Not verifying the server you're connecting to defeats the purpose of call encryption.
					 */
					Utils.quit(this);
				}
			}
		}
	}
}
