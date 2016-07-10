package dt.call.aclient.screens;

import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
/**
 * Created by Daniel on 07/08/2016
 */
public class Settings extends AppCompatActivity
{

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getFragmentManager().beginTransaction().replace(R.id.settings_placeholder, new SettingsFragment()).commit();
	}

	public static class SettingsFragment extends PreferenceFragment implements Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener
	{
		private static final String tag = "SettingsFragment";
		private static final int FILE_SELECT_CODE = 1;
		private Preference certPicker;
		private Preference cmdPortPicker;
		private Preference mediaPortPicker;

		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);
			//don't use the default preference file name of dt.call... use the actual one.
			//all preferences that are to show up in a preference fragment MUST BE STRINGS
			getPreferenceManager().setSharedPreferencesName(Const.PREFSFILE);
			getPreferenceManager().setSharedPreferencesMode(MODE_PRIVATE);
			addPreferencesFromResource(R.xml.settings_fragment);

			//setup the file picker for the certificate preference
			certPicker = findPreference(Const.PREF_CERTFNAME);
			certPicker.setOnPreferenceClickListener(this);

			//setup the command and media ports to make sure the port number is between 1 and 65536
			cmdPortPicker = findPreference(Const.PREF_COMMANDPORT);
			cmdPortPicker.setOnPreferenceChangeListener(this);
			mediaPortPicker = findPreference(Const.PREF_MEDIAPORT);
			mediaPortPicker.setOnPreferenceChangeListener(this);
		}

		@Override
		public boolean onPreferenceClick(Preference preference)
		{
			//mostly copied and pasted from InitlalServer
			if(preference == certPicker)
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
					Utils.showOk(Vars.applicationContext, getString(R.string.alert_initial_server_no_caja));
				}
			}
			return false;
		}

		@Override
		//mostly copied and pasted from InitlalServer
		public void onActivityResult(int requestCode, int result, Intent data)
		{
			//Only attempt to get the certificate file path if Intent data has stuff in it.
			//	It won't have stuff in it if the user just clicks back.
			if(requestCode == FILE_SELECT_CODE && data != null)
			{
				Uri uri = data.getData();

				//https://stackoverflow.com/questions/7492672/java-string-split-by-multiple-character-delimiter
				String[] expanded = uri.getPath().split("[\\/:\\:]");
				String cert64, certFile;
				ContentResolver resolver = getActivity().getContentResolver();

				try
				{
					InputStream certInputStream = resolver.openInputStream(uri);
					X509Certificate expectedCert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(certInputStream);
					byte[] expectedDump = expectedCert.getEncoded();
					cert64 = Base64.encodeToString(expectedDump, Base64.NO_PADDING & Base64.NO_WRAP);
					Vars.expectedCertDump = cert64;

					//store the certificate file name for esthetic purposes
					certFile = expanded[expanded.length-1];
					SharedPreferences sharedPreferences = getActivity().getSharedPreferences(Const.PREFSFILE, Context.MODE_PRIVATE);
					SharedPreferences.Editor editor = sharedPreferences.edit();
					editor.putString(Const.PREF_CERT64, cert64);
					editor.putString(Const.PREF_CERTFNAME, certFile);
					editor.apply();

				}
				catch (FileNotFoundException | CertificateException e)
				{
					//file somehow disappeared between picking and trying to use in the app
					//	there's nothing you can do about it
					Utils.showOk(getActivity(), getString(R.string.alert_initial_server_corrupted_cert));
				}
			}
		}

		@Override
		//validate the port #
		public boolean onPreferenceChange(Preference preference, Object newValue)
		{
			if(preference == cmdPortPicker || preference == mediaPortPicker)
			{
				try
				{
					int newPort = Integer.valueOf((String) newValue);
					if(newPort > 1 && newPort < 65536)
					{
						return true;
					}
					else
					{
						Utils.showOk(getActivity(), getString(R.string.alert_initial_server_invalid_port));
						return false;
					}
				}
				catch (Exception e)
				{
					Utils.dumpException(tag, e);
					return false;
				}
			}

			//other settings don't need validating
			return true;
		}
	}

}
