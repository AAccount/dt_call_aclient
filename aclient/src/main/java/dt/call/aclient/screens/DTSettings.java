package dt.call.aclient.screens;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.v7.app.AppCompatActivity;

import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.sodium.SodiumUtils;

/**
 * Created by Daniel on 07/08/2016
 */
public class DTSettings extends AppCompatActivity
{
	//Not actual preferences but used to be able to change the server public and user private key here
	private static final String FAKEPREF_SERVER_PUBLICKEY = "server_sodium_public";
	private static final String FAKEPREF_USER_PRIVATEKEY = "private_key";

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
		private Preference sodiumPublicPicker;
		private Preference privateKeyPicker;
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
			sodiumPublicPicker = findPreference(FAKEPREF_SERVER_PUBLICKEY);
			sodiumPublicPicker.setOnPreferenceClickListener(this);
			privateKeyPicker = findPreference(FAKEPREF_USER_PRIVATEKEY);
			privateKeyPicker.setOnPreferenceClickListener(this);

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
			if(preference == privateKeyPicker || preference == sodiumPublicPicker)
			{
				//choose the appropriate selection key and popup title
				int selectionKey;
				String selectionTitle;
				if(preference == privateKeyPicker)
				{
					selectionKey = Const.SELECT_SELF_PRIVATE_SODIUM;
					selectionTitle = getString(R.string.file_picker_user_private);
				}
				else //if(preference == sodiumPublicPicker)
				{
					selectionKey = Const.SELECT_SERVER_PUBLIC_SODIUM;
					selectionTitle = getString(R.string.file_picker_server_sodium_public);
				}

				//https://stackoverflow.com/questions/7856959/android-file-chooser
				// Open a file chooser dialog. Alert dialog if no file managers found
				Intent fileDialog = new Intent(Intent.ACTION_GET_CONTENT);
				fileDialog.setType("*/*");
				fileDialog.addCategory(Intent.CATEGORY_OPENABLE);
				try
				{
					startActivityForResult(Intent.createChooser(fileDialog, selectionTitle), selectionKey);
				}
				catch (ActivityNotFoundException a)
				{
					Utils.showOk(Vars.applicationContext, getString(R.string.alert_initial_server_no_caja));
				}
			}
			return false;
		}

		@Override
		public void onActivityResult(int requestCode, int result, Intent data)
		{
			//Only attempt to get the certificate file path if Intent data has stuff in it.
			//	It won't have stuff in it if the user just clicks back.
			if(requestCode == Const.SELECT_SELF_PRIVATE_SODIUM && data != null)
			{
				Uri uri = data.getData();
				final byte[] keybytes = SodiumUtils.readKeyFileBytes(uri, getActivity());
				final byte[] key = SodiumUtils.interpretKey(keybytes, true);
				if(key != null)
				{
					Utils.applyFiller(Vars.selfPrivateSodium);
					Vars.selfPrivateSodium = key;
					final boolean writeok = Utils.writeDataDataFile(Const.INTERNAL_PRIVATEKEY_FILE, Vars.selfPrivateSodium, getActivity());
					if(!writeok)
					{
						Utils.showOk(getActivity(), getString(R.string.initial_user_write_user_private_exception));
					}
				}
			}

			else if(requestCode == Const.SELECT_SERVER_PUBLIC_SODIUM && data != null)
			{
				Uri uri = data.getData();
				final byte[] keybytes = SodiumUtils.readKeyFileBytes(uri, getActivity());
				final byte[] key = SodiumUtils.interpretKey(keybytes, false);
				if(key != null)
				{
					Vars.serverPublicSodium = key;
					final boolean writeok = Utils.writeDataDataFile(Const.INTERNAL_SERVER_PUBLICKEY_FILE, Vars.serverPublicSodium, getActivity());
					if(!writeok)
					{
						Utils.showOk(getActivity(), getString(R.string.initial_server_write_server_public_exception));
					}
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
					Utils.showOk(getActivity(), getString(R.string.alert_initial_server_invalid_port));
					return false;
				}
			}

			//other settings don't need validating
			return true;
		}
	}

}
