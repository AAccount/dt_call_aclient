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

import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.background.BackgroundManager;

public class InitialServer extends AppCompatActivity implements View.OnClickListener
{
	private static final String tag = "Initial Server";

	private EditText addr, commandPort, mediaPort;
	private Button cert, sodium;
	private FloatingActionButton next;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

		Vars.applicationContext = getApplicationContext();

		//turn on BackgroundManager just in case it was turned off by the logout button
		ComponentName backgroundManager = new ComponentName(this, BackgroundManager.class);
		getPackageManager().setComponentEnabledSetting(backgroundManager, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

		Utils.loadPrefs();

		if(!Vars.uname.equals("") && !(Vars.privateSodium == null))
		{
			Utils.logcat(Const.LOGD, tag, "Skipping to the home screen");

			//don't need to start the command listener or do the login async. that will be started on the home screen

			//jump to the home screen
			Intent skip2Home = new Intent(this, UserHome.class);
			startActivity(skip2Home);
			return;
		}

		//Because this is the launcher activity, the name in AndroidManifest is what shows
		//	up in the start menu. Don't want "Server Settings" as the app name
		ActionBar ab = getSupportActionBar();
		ab.setTitle(getString(R.string.initial_server_title));

		//setup server information activity if the information hasn't already been saved
		setContentView(R.layout.activity_initial_server);
		addr = (EditText)findViewById(R.id.initial_server_addr);
		commandPort = (EditText)findViewById(R.id.initial_server_command);
		mediaPort = (EditText)findViewById(R.id.initial_server_media);
		cert = (Button)findViewById(R.id.initial_server_certificate);
		cert.setOnClickListener(this);
		cert.setAllCaps(false);
		sodium = (Button)findViewById(R.id.initial_server_sodium);
		sodium.setOnClickListener(this);
		sodium.setAllCaps(false);
		next = (FloatingActionButton)findViewById(R.id.initial_server_next);
		next.setOnClickListener(this);

		if(!Vars.serverAddress.equals(""))
		{
			addr.setText(Vars.serverAddress);
		}
		if(Vars.commandPort != 0)
		{
			commandPort.setText(String.valueOf(Vars.commandPort)); //have to do valueof or it treats the port as a literal R. resource id
		}
		if(Vars.mediaPort != 0)
		{
			mediaPort.setText(String.valueOf(Vars.mediaPort));
		}
		if(!Vars.certName.equals("") && !Vars.certDump.equals(""))
		{
			cert.setText(Vars.certName);
		}
		if(!Vars.serverPublicSodiumName.equals("") && !Vars.serverPublicSodiumDump.equals(""))
		{
			sodium.setText(Vars.serverPublicSodiumName);
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		//check for storage access permission. no point of a tls1.2 connection if the server is someone else
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
							ActivityCompat.requestPermissions(InitialServer.this, perms, Const.PERM_STORAGE);
							dialog.cancel();
						}
					});
			AlertDialog showOkAlert = mkdialog.create();
			showOkAlert.show();
		}
	}

	@Override
	public void onClick(View v)
	{
		if(v == cert)
		{
			//https://stackoverflow.com/questions/7856959/android-file-chooser
			// Open a file chooser dialog. Alert dialog if no file managers found
			Intent fileDialog = new Intent(Intent.ACTION_GET_CONTENT);
			fileDialog.setType("*/*");
			fileDialog.addCategory(Intent.CATEGORY_OPENABLE);
			try
			{
				startActivityForResult(Intent.createChooser(fileDialog, getString(R.string.file_picker_server_public)), Const.SELECT_SERVER_SSLCERT);
			}
			catch (ActivityNotFoundException a)
			{
				Utils.showOk(this, getString(R.string.alert_initial_server_no_caja));
			}
		}
		if(v == sodium)
		{
			//https://stackoverflow.com/questions/7856959/android-file-chooser
			// Open a file chooser dialog. Alert dialog if no file managers found
			Intent fileDialog = new Intent(Intent.ACTION_GET_CONTENT);
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
			String commandString, mediaString;
			Vars.serverAddress = addr.getText().toString();
			commandString = commandPort.getText().toString();
			mediaString = mediaPort.getText().toString();

			//check to make sure all the required information is filled in
			boolean allFilled = !Vars.serverAddress.equals("") && !commandString.equals("") && !mediaString.equals("") && !Vars.certDump.equals("") && Vars.serverPublicSodium != null;
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
			SharedPreferences sharedPreferences = getSharedPreferences(Const.PREFSFILE, Context.MODE_PRIVATE);
			SharedPreferences.Editor editor = sharedPreferences.edit();
			editor.putString(Const.PREF_ADDR, Vars.serverAddress);
			editor.putString(Const.PREF_COMMANDPORT, commandString);
			editor.putString(Const.PREF_MEDIAPORT, mediaString);
			editor.putString(Const.PREF_CERTDUMP, Vars.certDump);
			editor.putString(Const.PREF_CERTFNAME, Vars.certName);
			editor.putString(Const.PREF_SODIUM_DUMP, Vars.serverPublicSodiumDump);
			editor.putString(Const.PREF_SODIUM_DUMP_NAME, Vars.serverPublicSodiumName);
			editor.apply();

			//go to the user information screen
			Intent initUser = new Intent(this, InitialUserInfo.class);
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
		if(requestCode == Const.SELECT_SERVER_SSLCERT && data != null)
		{
			if(Utils.readServerPublicKey(uri, this))
			{
				cert.setText(Vars.certName);
			}
			else
			{
				Utils.showOk(this, getString(R.string.alert_corrupted_cert));
			}
		}
		else if(requestCode == Const.SELECT_SERVER_PUBLIC_SODIUM && data != null)
		{
			if(Utils.readServerSodiumPublic(uri, this))
			{
				sodium.setText(Vars.serverPublicSodiumName);
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
