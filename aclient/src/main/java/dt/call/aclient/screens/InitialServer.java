package dt.call.aclient.screens;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.background.BackgroundManager;

public class InitialServer extends AppCompatActivity implements View.OnClickListener
{
	private static final String tag = "Initial Server";
	private static final int FILE_SELECT_CODE = 1;
	private static final int STORAGE_PERM = 1;

	private EditText addr, commandPort, mediaPort;
	private Button cert;
	private FloatingActionButton next;
	private String certFile="", cert64 ="";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

		Vars.applicationContext = getApplicationContext();

		//turn on BackgroundManager just in case it was turned off by the logout button
		ComponentName backgroundManager = new ComponentName(this, BackgroundManager.class);
		getPackageManager().setComponentEnabledSetting(backgroundManager, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

		//if the information is already there, go straight to the home screen
		SharedPreferences sharedPreferences = getSharedPreferences(Const.PREFSFILE, Context.MODE_PRIVATE);
		//you need this stuff anyways for this screen
		String savedAddr = sharedPreferences.getString(Const.PREF_ADDR, "");
		int savedCommand = sharedPreferences.getInt(Const.PREF_COMMANDPORT, 0);
		int savedMedia = sharedPreferences.getInt(Const.PREF_MEDIAPORT, 0);
		String savedCertFName = sharedPreferences.getString(Const.PREF_CERTFNAME, "");
		cert64 = sharedPreferences.getString(Const.PREF_CERT64, "");
		//you need this stuff for the next screen. if it's already there then skip to the home screen
		String savedUname = sharedPreferences.getString(Const.PREF_UNAME, "");
		String savedPasswd = sharedPreferences.getString(Const.PREF_PASSWD, "");
		Vars.SHOUDLOG = sharedPreferences.getBoolean(Const.PREF_LOG, false);

		if(!savedUname.equals("") && !savedPasswd.equals(""))
		{
			Utils.logcat(Const.LOGD, tag, "Skipping to the home screen");

			//set all the session variables
			Vars.serverAddress = savedAddr;
			Vars.commandPort = savedCommand;
			Vars.mediaPort = savedMedia;
			Vars.expectedCertDump = cert64;
			Vars.uname = savedUname;
			Vars.passwd = savedPasswd;

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
		next = (FloatingActionButton)findViewById(R.id.initial_server_next);
		next.setOnClickListener(this);

		if(!savedAddr.equals(""))
		{
			addr.setText(savedAddr);
			Vars.serverAddress = savedAddr;
		}
		if(savedCommand != 0)
		{
			commandPort.setText(String.valueOf(savedCommand)); //have to do valueof or it treats the port as a literal R. resource id
			Vars.commandPort = savedCommand;
		}
		if(savedMedia != 0)
		{
			mediaPort.setText(String.valueOf(savedMedia));
			Vars.mediaPort = savedMedia;
		}
		if(!savedCertFName.equals(""))
		{
			cert.setText(savedCertFName);
			certFile = savedCertFName; //save here so that when clickng next "" isn't written to saved prefs
			Vars.expectedCertDump = cert64;
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
			mkdialog.setMessage(getString(R.string.alert_initial_server_storage_perm))
					.setPositiveButton(R.string.alert_ok, new DialogInterface.OnClickListener()
					{
						@Override
						public void onClick(DialogInterface dialog, int which)
						{
							String[] perms = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
							ActivityCompat.requestPermissions(InitialServer.this, perms, STORAGE_PERM);
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
				startActivityForResult(Intent.createChooser(fileDialog, "Choose cert"), FILE_SELECT_CODE);
			}
			catch (ActivityNotFoundException a)
			{
				Utils.showOk(this, getString(R.string.alert_initial_server_no_caja));
			}
		}
		else if(v == next)
		{
			//extract UI info
			String addrString, commandString, mediaString;
			addrString = addr.getText().toString();
			commandString = commandPort.getText().toString();
			mediaString = mediaPort.getText().toString();

			//check to make sure all the required information is filled in
			boolean allFilled = !addrString.equals("") && !commandString.equals("") && !mediaString.equals("") && !cert64.equals("");
			if(!allFilled)
			{
				Utils.showOk(this, getString(R.string.alert_initial_server_incomplete_server));
				return;
			}

			//check to make sure the ports are valid
			boolean commandValid, mediaValid;
			int commandInt=0, mediaInt=0;
			try
			{
				commandInt = Integer.valueOf(commandString);
				mediaInt = Integer.valueOf(mediaString);
				commandValid = commandInt > 0 && commandInt < 65536;
				mediaValid = mediaInt > 0 && mediaInt < 65536;
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
			editor.putString(Const.PREF_ADDR, addrString);
			editor.putInt(Const.PREF_COMMANDPORT, commandInt);
			editor.putInt(Const.PREF_MEDIAPORT, mediaInt);
			editor.putString(Const.PREF_CERT64, cert64);
			editor.putString(Const.PREF_CERTFNAME, certFile);
			editor.apply();

			//setup all the Vars
			Vars.serverAddress = addrString;
			Vars.commandPort = commandInt;
			Vars.mediaPort = mediaInt;
			Vars.expectedCertDump = cert64;

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
		if(requestCode == FILE_SELECT_CODE && data != null)
		{
			Uri uri = data.getData();

			//https://stackoverflow.com/questions/7492672/java-string-split-by-multiple-character-delimiter
			String[] expanded = uri.getPath().split("[\\/:\\:]");

			ContentResolver resolver = getContentResolver();
			try
			{
				InputStream certInputStream = resolver.openInputStream(uri);
				X509Certificate expectedCert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(certInputStream);
				byte[] expectedDump = expectedCert.getEncoded();
				cert64 = Base64.encodeToString(expectedDump, Base64.NO_PADDING & Base64.NO_WRAP);
				Vars.expectedCertDump = cert64;

				//store the certificate file name for esthetic purposes
				certFile = expanded[expanded.length-1];
				cert.setText(certFile);

			}
			catch (FileNotFoundException | CertificateException e)
			{
				//file somehow disappeared between picking and trying to use in the app
				//	there's nothing you can do about it
				Utils.showOk(this, getString(R.string.alert_initial_server_corrupted_cert));
			}
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults)
	{
		switch(requestCode)
		{
			case STORAGE_PERM:
			{
				if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED)
				{
					/**
					 * Not verifying the server you're connecting to defeats the purpose of call encryption.
					 * If another server is impersonating the expected one, you've just lost your password,
					 * and possibly opened yourself to call tapping. Just quit the app.
					 */

					//prevent background manager from restarting command listener when sockets kill async is called
					ComponentName backgroundManager = new ComponentName(this, BackgroundManager.class);
					getPackageManager().setComponentEnabledSetting(backgroundManager, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);

					//https://stackoverflow.com/questions/3226495/android-exit-application-code
					//basically a way to get out of aclient
					Intent intent = new Intent(Intent.ACTION_MAIN);
					intent.addCategory(Intent.CATEGORY_HOME);
					intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					startActivity(intent);
					System.exit(0); //actually close the app so it will start fresh from login
				}
			}
		}
	}
}
