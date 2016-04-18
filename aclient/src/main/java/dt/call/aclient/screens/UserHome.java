package dt.call.aclient.screens;

import android.Manifest;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.view.ViewGroup.LayoutParams;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.background.async.LoginAsync;
import dt.call.aclient.background.BackgroundManager;
import dt.call.aclient.background.async.CallInitAsync;
import dt.call.aclient.background.async.KillSocketsAsync;
import dt.call.aclient.background.async.LookupAsync;
import dt.call.aclient.sqlite.Contact;
import dt.call.aclient.sqlite.DB;
import dt.call.aclient.sqlite.DBLog;
import dt.call.aclient.sqlite.History;

public class UserHome extends AppCompatActivity implements View.OnClickListener, View.OnLongClickListener
{
	private static final String tag = "UserHome";
	private static final int CHRENAME = 1;
	private static final int CHRM = 2;
	private static final int MIC_PERM = 1;

	private EditText actionbox;
	private FloatingActionButton call, add;
	private LinearLayout contactList;
	private boolean inEdit = false;
	private Contact contactInEdit; //whenever renaming a contact just change its nickname here and pass around this object
	private DB db;
	private BroadcastReceiver myReceiver;
	private ProgressDialog loginProgress;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_user_home);
		actionbox = (EditText)findViewById(R.id.user_home_actionbox);
		call = (FloatingActionButton)findViewById(R.id.user_home_call);
		call.setOnClickListener(this);
		add = (FloatingActionButton)findViewById(R.id.user_home_add);
		add.setOnClickListener(this);
		contactList = (LinearLayout)findViewById(R.id.user_home_contact_list);
		db = new DB(this);

		//build the contacts list
		ArrayList<Contact> allContacts = db.getContacts();
		Vars.contactTable = new HashMap<String, String>();
		for(Contact contact : allContacts)
		{
			addToContactList(contact);
			Vars.contactTable.put(contact.getName(), contact.getNickname());
		}

		//setup the pending intents that make the ongoing notification bring you to the
		//right screen based on what you're doing
		if(Vars.go2HomePending == null)
		{
			Intent go2Home = new Intent(getApplicationContext(), UserHome.class);
			Vars.go2HomePending = PendingIntent.getActivity(this, 0, go2Home, PendingIntent.FLAG_UPDATE_CURRENT);
		}
		if(Vars.go2CallMainPending == null)
		{
			Intent go2CallMain = new Intent(getApplicationContext(), CallMain.class);
			Vars.go2CallMainPending = PendingIntent.getActivity(this, 0, go2CallMain, PendingIntent.FLAG_UPDATE_CURRENT);
		}

		//TODO: manage this properly for meaningful results
		//setup the ongoing notification shared accross screens that shows
		//	the state of the app: signed in, no internet, in call etc...
		//
		//if this is the first time the notification is being setup do it from scratch.
		//otherwise just update it to relfect home
		if(Vars.stateNotificationBuilder == null || Vars.notificationManager == null)
		{
			Vars.stateNotificationBuilder = new Notification.Builder(getApplicationContext())
					.setContentTitle(getString(R.string.app_name))
					.setContentText(getString(R.string.state_popup_idle))
					.setSmallIcon(R.drawable.ic_vpn_lock_white_48dp)
					.setContentIntent(Vars.go2HomePending)
					.setOngoing(true);
			Vars.notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			Vars.notificationManager.notify(Const.stateNotificationId, Vars.stateNotificationBuilder.build());
		}
		else
		{
			Vars.stateNotificationBuilder.setContentText(getString(R.string.state_popup_idle))
					.setContentIntent(Vars.go2HomePending);
			Vars.notificationManager.notify(Const.stateNotificationId, Vars.stateNotificationBuilder.build());
		}

		//receives the server response from clicking the 2 FAB buttons
		//	Click +: whether new contact to add is valid
		//	Click phone: whether the person you want to call is available
		myReceiver = new BroadcastReceiver()
		{
			@Override
			public void onReceive(Context context, Intent intent)
			{
				Utils.logcat(Const.LOGD, tag, "got a broadcasted intent");

				if(intent.getAction().equals(Const.BROADCAST_HOME))
				{
					String type = intent.getStringExtra(Const.BROADCAST_HOME_TYPE);

					//Result of clicking the "+" for adding a contact
					if (type.equals(Const.BROADCAST_HOME_TYPE_LOOKUP))
					{
						String user = intent.getStringExtra(Const.BROADCAST_HOME_LOOKUP_NAME);
						String result = intent.getStringExtra(Const.BROADCAST_HOME_LOOKUP_RESULT);
						if (result.equals("exists"))
						{
							Contact newGuy = new Contact(user);
							db.insertContact(newGuy);
							addToContactList(newGuy);
							actionbox.setText("");
							Vars.contactTable.put(user, "");
						}
						else
						{
							Utils.showOk(UserHome.this, getString(R.string.alert_user_home_contact_notexist));
							//don't reset the actionbox text. coulda just been a typo
						}
					}

					//Result of clicking the phone button. If the user you're trying to call doesn't exist
					//	the server treats it as not available. It IS the server's job to verify all input given to it.
					else if (type.equals(Const.BROADCAST_HOME_TYPE_INIT))
					{
						boolean canInit = intent.getBooleanExtra(Const.BROADCAST_HOME_INIT_CANINIT, false);
						if (canInit)
						{
							Utils.logcat(Const.LOGD, tag, "Starting call with " + Vars.callWith);
							Intent startCall = new Intent(UserHome.this, CallMain.class);
							startActivity(startCall);
						}
						else
						{
							Utils.logcat(Const.LOGD, tag, "Can't start call");
							Utils.showOk(UserHome.this, getString(R.string.alert_user_home_cant_dial));
						}
					}
				}
				else if(intent.getAction().equals(Const.BROADCAST_LOGIN))
				{
					boolean ok = intent.getBooleanExtra(Const.BROADCAST_LOGIN_RESULT, false);
					loginProgress.dismiss();
					if(!ok)
					{
						Utils.logcat(Const.LOGW, tag, "received login failed");
						db.insertLog(new DBLog(tag, "received login failed intent"));
						Utils.showOk(UserHome.this, getString(R.string.alert_login_failed));
					}
				}
			}
		};

		//setup the command listener if it isn't already there
		//	it will already be there if you're coming back to the UserHome screen from doing something else

		//for some types of crashes it goes back to the UserHome screen but with no save data (and missing connections)
		if(Vars.commandSocket == null || Vars.mediaSocket == null)
		{
			loginProgress = ProgressDialog.show(UserHome.this, null, getString(R.string.progress_login));

			SharedPreferences sharedPreferences = getSharedPreferences(Const.PREFSFILE, Context.MODE_PRIVATE);
			Vars.uname = sharedPreferences.getString(Const.PREF_UNAME, "");
			Vars.passwd = sharedPreferences.getString(Const.PREF_PASSWD, "");
			Vars.serverAddress = sharedPreferences.getString(Const.PREF_ADDR, "");
			Vars.commandPort = sharedPreferences.getInt(Const.PREF_COMMANDPORT, 0);
			Vars.mediaPort = sharedPreferences.getInt(Const.PREF_MEDIAPORT, 0);
			Vars.expectedCertDump = sharedPreferences.getString(Const.PREF_CERT64, "");
			Vars.SHOUDLOG = sharedPreferences.getBoolean(Const.PREF_LOG, false);
			new LoginAsync(Vars.uname, Vars.passwd, true).execute();
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		//receiver must be reregistered when loading this screen from the back button
		IntentFilter homeFilters = new IntentFilter();
		homeFilters.addAction(Const.BROADCAST_HOME);
		homeFilters.addAction(Const.BROADCAST_LOGIN);
		registerReceiver(myReceiver, homeFilters);

		//check to make sure mic permission is set... can't call without a mic
		if(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
		{
			AlertDialog.Builder mkdialog = new AlertDialog.Builder(this);
			mkdialog.setMessage(getString(R.string.aler_user_home_mic_perm))
					.setPositiveButton(R.string.alert_ok, new DialogInterface.OnClickListener()
					{
						@Override
						public void onClick(DialogInterface dialog, int which)
						{
							String[] perms = new String[]{Manifest.permission.RECORD_AUDIO};
							ActivityCompat.requestPermissions(UserHome.this, perms, MIC_PERM);
							dialog.cancel();
						}
					});
			AlertDialog showOkAlert = mkdialog.create();
			showOkAlert.show();
		}
	}

	@Override
	protected void onPause()
	{
		super.onPause();

		//don't leak the receiver when leaving this screen
		unregisterReceiver(myReceiver);
	}

	@Override
	public void onClick(View v)
	{
		if(v instanceof Button) //any of the contact buttons. The FABs don't count as regular buttons
		{
			Contact contact = (Contact)v.getTag();
			actionbox.setText(contact.getName());
		}
		else if (v == add)
		{
			String actionBoxName = actionbox.getText().toString();
			Contact actionBoxContact = new Contact(actionBoxName);

			//check to see if anything was entered in the action box to begin with
			if(actionbox.getText().toString().equals(""))
			{
				return;
			}

			//check to see if the new contact to add already exists
			if(db.contactExists(actionBoxContact))
			{
				Utils.showOk(this, getString(R.string.alert_user_home_duplicate));
				actionbox.setText("");
				return;
			}
			new LookupAsync().execute(actionBoxContact); //result will be processed in myReceiver BroadcastReceiver
		}
		else if (v == call)
		{
			String who = actionbox.getText().toString();
			if(who.equals("")) //in case you pressed call while the action box was empty
			{
				return;
			}

			Contact contact = new Contact(who, Vars.contactTable.get(who));
			new CallInitAsync(contact).execute();
			long now = Utils.getLocalTimestamp();
			//don't need the nickname because db only records user name
			//	db doesn' need to record nickname because it will be figured out when drawing the history table
			History history = new History(now, contact, Const.outgoing);
			db.insertHistory(history);

		}
	}

	@Override
	public boolean onLongClick(View v)
	{
		if(v instanceof Button)
		{
			inEdit = true;
			contactInEdit = (Contact)v.getTag(); //for prefilling in the edit popup with the current nickname
			invalidateOptionsMenu();
		}
		return false;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		if(inEdit)
		{
			getMenuInflater().inflate(R.menu.menu_main_edit, menu);
			try
			{
				getSupportActionBar().setBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(this, R.color.colorAccent)));
			}
			catch (NullPointerException n)
			{
				Utils.logcat(Const.LOGE, tag, "null pointer changing action bar to highlight color: ");
				Utils.dumpException(tag, n);
			}
		}
		else
		{
			getMenuInflater().inflate(R.menu.menu_main_set, menu);
			try
			{
				getSupportActionBar().setBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(this, R.color.colorPrimary)));
			}
			catch (NullPointerException n)
			{
				Utils.logcat(Const.LOGE, tag, "null pointer changing action bar to normal color: ");
				Utils.dumpException(tag, n);
			}
		}
		return super.onCreateOptionsMenu(menu);
	}

	//its only purpose is to be called when you click the logout button
	//for whatever reason you can't access android internals like notification manager inside onOptionsItemSelected
	private void quit()
	{
		//get rid of the status notification
		Vars.notificationManager.cancelAll();

		//Kill alarms
		AlarmManager manager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		manager.cancel(Vars.pendingHeartbeat);
		manager.cancel(Vars.pendingRetries);

		//prevent background manager from restarting command listener when sockets kill async is called
		ComponentName backgroundManager = new ComponentName(this, BackgroundManager.class);
		getPackageManager().setComponentEnabledSetting(backgroundManager, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);

		//get rid of the sockets
		new KillSocketsAsync().execute();

		//https://stackoverflow.com/questions/3226495/android-exit-application-code
		//basically a way to get out of aclient
		Intent intent = new Intent(Intent.ACTION_MAIN);
		intent.addCategory(Intent.CATEGORY_HOME);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(intent);
		System.exit(0); //actually close the app so it will start fresh from login
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch(item.getItemId())
		{
			case R.id.menu_main_dblogs:
				Intent seeLogs = new Intent(UserHome.this, LogViewer.class);
				startActivity(seeLogs);
				return true;
			case R.id.menu_main_history:
				//TODO: once the history screen is made
				return true;
			case R.id.menu_main_logout:
				quit();
				return true;
			//use the same actions whether settings is form the main menu or edit menu
			case R.id.menu_main_settings:
				//TODO: once the settings screen is made
				return true;
			case R.id.menu_edit_done:
				inEdit = false;
				invalidateOptionsMenu();
				return true;
			case R.id.menu_edit_edit:
				//This popup is more than just your average Show OK popup

				//setup the change nickname popup strings
				String name = contactInEdit.getName();
				String currentNick = contactInEdit.getNickname();
				String instructions = getString(R.string.alert_user_home_rename_instructions).replace("CONTACT", name);

				//setup the nickname popup
				View alertCustom = View.inflate(this, R.layout.alert_rename_contact, null);
				TextView instructionsView = (TextView)alertCustom.findViewById(R.id.alert_rename_instructions);
				instructionsView.setText(instructions);
				final EditText chNick = (EditText)alertCustom.findViewById(R.id.alert_rename_rename);
				chNick.setText(currentNick);

				//build the alert dialog now that everything is prefilled
				AlertDialog.Builder mkdialog = new AlertDialog.Builder(UserHome.this);
				mkdialog.setView(alertCustom)
						.setPositiveButton(R.string.alert_user_home_rename_button_ch, new DialogInterface.OnClickListener()
						{
							@Override
							public void onClick(DialogInterface dialog, int which)
							{
								String newNick = chNick.getText().toString();
								contactInEdit.setNickname(newNick);
								db.changeNickname(contactInEdit);
								refreshContacts(CHRENAME, contactInEdit);
								inEdit = false;
								invalidateOptionsMenu();

								//don't forget to update the in memory contact list
								Vars.contactTable.remove(contactInEdit.getName());
								Vars.contactTable.put(contactInEdit.getName(), contactInEdit.getNickname());
							}
						})
						.setNegativeButton(R.string.alert_user_home_rename_button_nvm, new DialogInterface.OnClickListener()
						{
							@Override
							public void onClick(DialogInterface dialog, int which)
							{
								dialog.cancel();
								inEdit = false;
								invalidateOptionsMenu();
							}
						});
				AlertDialog chnick = mkdialog.create();
				chnick.show();
				return true;
			case R.id.menu_edit_rm:
				db.deleteContact(contactInEdit);
				refreshContacts(CHRM, contactInEdit);
				inEdit = false;
				invalidateOptionsMenu();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	//adds a new row to the contacts table view
	private void addToContactList(Contact contact)
	{
		Button contactView = new Button(this);
		contactView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
		contactView.setText(contact.toString());
		contactView.setAllCaps(false);
		contactView.setOnClickListener(UserHome.this);
		contactView.setOnLongClickListener(UserHome.this);
		contactView.setTag(contact);
		contactList.addView(contactView);
	}

	//updates the contact list after an edit or removal
	//avoid just redoing the entire list. edit the one you already have
	private void refreshContacts(int mode, Contact changed)
	{
		for(int i=0; i < contactList.getChildCount(); i++)
		{
			Button childView = (Button)contactList.getChildAt(i);
			Contact tag = (Contact)childView.getTag();
			if(tag.equals(changed))
			{
				if (mode == CHRENAME)
				{
					childView.setText(changed.toString());
					childView.setTag(changed);
					return;
				}
				else if (mode == CHRM)
				{
					contactList.removeViewAt(i);
					actionbox.setText(""); //blank out action box which probably has this guy in it
					return;
				}
			}
		}
	}

	@Override
	public void onBackPressed()
	{
		/*
		 * Do nothing. There's nowhere to go back to
		 *
		 */
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults)
	{
		switch(requestCode)
		{
			case MIC_PERM:
			{
				if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED)
				{
					//with mic denied, this app can't do anything useful
					quit();
				}
			}
		}

	}
}
