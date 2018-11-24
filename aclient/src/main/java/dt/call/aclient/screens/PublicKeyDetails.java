package dt.call.aclient.screens;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.sodium.SodiumUtils;
import dt.call.aclient.sqlite.SQLiteDb;

public class PublicKeyDetails extends AppCompatActivity
{
	private String correspondingUser;
	private byte[] newPublicKey = null;
	private TextView dumpArea;
	private String userDisplayName;
	private boolean readonly = false;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_public_key_details);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		dumpArea = findViewById(R.id.public_key_details_dump);
		correspondingUser = getIntent().getStringExtra(Const.EXTRA_UNAME);

		//get the public key (if one is known) and regenerate the text dump
		byte[] publickey = Vars.publicSodiumTable.get(correspondingUser);
		if(publickey == null)
		{
			dumpArea.setText(getString(R.string.public_key_details_none));
		}
		else
		{
			final String publicKeyString = SodiumUtils.SODIUM_PUBLIC_HEADER + Utils.stringify(publickey);
			dumpArea.setText(publicKeyString);
		}

		//get the contact nick name if it exists
		userDisplayName = Vars.contactTable.get(correspondingUser);
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
							ActivityCompat.requestPermissions(PublicKeyDetails.this, perms, Const.PERM_STORAGE);
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
			case Const.PERM_STORAGE:
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
		switch (item.getItemId())
		{
			case R.id.menu_edit_done:
				if(newPublicKey != null)
				{
					SQLiteDb.getInstance(this).insertPublicKey(correspondingUser, newPublicKey);
					Vars.publicSodiumTable.put(correspondingUser, newPublicKey);
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
					startActivityForResult(Intent.createChooser(fileDialog, userDisplayName +getString(R.string.file_picker_user_public)), Const.SELECT_USER_PUBLIC_SODIUM);
				}
				catch (ActivityNotFoundException a)
				{
					Utils.showOk(this, getString(R.string.alert_initial_server_no_caja));
				}
				break;
			case R.id.menu_edit_rm:
				final String DISPLAYNAME = "DISPLAYNAME";
				final String message = getString(R.string.public_key_details_confirm_delete).replace(DISPLAYNAME, userDisplayName);
				final AlertDialog.Builder mkdialog = new AlertDialog.Builder(this);
				mkdialog.setMessage(message)
						.setPositiveButton(R.string.alert_yes, new DialogInterface.OnClickListener()
						{
							@Override
							public void onClick(DialogInterface dialog, int which)
							{
								SQLiteDb.getInstance(PublicKeyDetails.this).deletePublicKey(correspondingUser);
								Vars.publicSodiumTable.remove(correspondingUser);
								dumpArea.setText(getString(R.string.public_key_details_none));
							}
						})
						.setNegativeButton(R.string.alert_no, new DialogInterface.OnClickListener()
						{
							@Override
							public void onClick(DialogInterface dialog, int which)
							{
								dialog.cancel();
							}
						});
				final AlertDialog showOkAlert = mkdialog.create();
				showOkAlert.show();
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
		if(requestCode == Const.SELECT_USER_PUBLIC_SODIUM && data != null)
		{
			final Uri uri = data.getData();
			final byte[] keybytes = SodiumUtils.readKeyFileBytes(uri, this);
			newPublicKey = SodiumUtils.interpretKey(keybytes, false);
			if(newPublicKey == null)
			{
				Utils.showOk(this, getString(R.string.alert_corrupted_key));
				return;
			}

			//only update the viewing area when everything's ok
			final String publicKeyString = SodiumUtils.SODIUM_PUBLIC_HEADER + Utils.stringify(newPublicKey);
			dumpArea.setText(publicKeyString);
		}
	}
}
