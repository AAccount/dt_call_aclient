package dt.call.aclient.screens;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import java.io.InputStream;
import java.security.PublicKey;

import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.sqlite.SQLiteDb;

public class PublicKeyDetails extends AppCompatActivity
{
	private String correspondingUser;
	private PublicKey newPublicKey = null;
	private TextView dumpArea;
	private String userDisplayName;
	private boolean readonly = false;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_public_key_details);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		dumpArea = (TextView)findViewById(R.id.public_key_details_dump);
		correspondingUser = getIntent().getStringExtra(Const.EXTRA_UNAME);
		String certCore = Vars.publicKeyDumps.get(correspondingUser);
		String publickeyDump;
		if(certCore == null)
		{
			publickeyDump = getResources().getString(R.string.public_key_details_none);
		}
		else
		{
			publickeyDump = certCore;
		}
		dumpArea.setText(publickeyDump);

		if(userDisplayName == null)
		{
			userDisplayName = correspondingUser;
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
							ActivityCompat.requestPermissions(PublicKeyDetails.this, perms, Const.STORAGE_PERM);
							dialog.cancel();
						}
					});
			AlertDialog showOkAlert = mkdialog.create();
			showOkAlert.show();
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults)
	{
		switch(requestCode)
		{
			case Const.STORAGE_PERM:
			{
				if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED)
				{
					/*
					 * If you can't read the disk, there's no way to change the person's public key.
					 */
					Utils.showOk(this, getString(R.string.public_key_details_noperm_fsread));
					readonly = true;
					invalidateOptionsMenu();
				}
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		//reuse the contact edit menu
		getMenuInflater().inflate(R.menu.menu_main_edit, menu);
		if(readonly)
		{
			MenuItem editButton = menu.findItem(R.id.menu_edit_edit);
			editButton.setVisible(false);
		}
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		String placeholder = getResources().getString(R.string.public_key_details_none);
		String dumpText = dumpArea.getText().toString();

		switch (item.getItemId())
		{
			case R.id.menu_edit_done:
				if(!dumpText.equals(placeholder))
				{
					SQLiteDb.getInstance(this).insertPublicKey(correspondingUser, dumpText);
					Vars.publicKeyDumps.put(correspondingUser, dumpText);
					Vars.publicKeyTable.put(correspondingUser, newPublicKey);
					String disp = Vars.contactTable.get(correspondingUser);
					Utils.showOk(this, getString(R.string.public_key_details_saved) + " " + disp);
				}
				break;
			case R.id.menu_edit_edit:
				// Open a file chooser dialog. Alert dialog if no file managers found
				Intent fileDialog = new Intent(Intent.ACTION_GET_CONTENT);
				fileDialog.setType("*/*");
				fileDialog.addCategory(Intent.CATEGORY_OPENABLE);
				try
				{
					startActivityForResult(Intent.createChooser(fileDialog, userDisplayName +getString(R.string.file_picker_server_public)), Const.USER_PUBLIC_KEY_SELECT);
				}
				catch (ActivityNotFoundException a)
				{
					Utils.showOk(this, getString(R.string.alert_initial_server_no_caja));
				}
				break;
			case R.id.menu_edit_rm:
				if(!dumpText.equals(placeholder))
				{
					SQLiteDb.getInstance(this).deletePublicKey(correspondingUser);
					Vars.publicKeyDumps.remove(correspondingUser);
					Vars.publicKeyTable.remove(correspondingUser);
					dumpArea.setText(placeholder);
				}
				break;
		}
		return true;
	}

	@Override
	//when the file picker is done
	protected void onActivityResult(int requestCode, int result, Intent data)
	{
		//Only attempt to get the KEY file path if Intent data has stuff in it.
		//	It won't have stuff in it if the user just clicks back.
		if(requestCode == Const.USER_PUBLIC_KEY_SELECT && data != null)
		{
			Uri uri = data.getData();

			//read the key from file
			byte[] keyBytes = Utils.readKey(uri, getApplicationContext());
			if(keyBytes == null)
			{
				Utils.showOk(this, getString(R.string.alert_corrupted_key));
				return;
			}

			//interpret the file contents as a public key
			String keyString = new String(keyBytes);
			newPublicKey = Utils.interpretDump(keyString);
			if(newPublicKey == null)
			{
				Utils.showOk(this, getString(R.string.alert_corrupted_key));
				return;
			}

			//only update the viewing area when everything's ok
			dumpArea.setText(keyString);
		}
	}
}
