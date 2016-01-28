package dt.call.aclient.screens;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
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
import java.util.concurrent.ExecutionException;

import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.background.BackgroundManager;
import dt.call.aclient.background.CallInitAsync;
import dt.call.aclient.background.CmdListener;
import dt.call.aclient.background.KillSocketsAsync;
import dt.call.aclient.background.LookupAsync;
import dt.call.aclient.sqlite.Contact;
import dt.call.aclient.sqlite.Db;
import dt.call.aclient.sqlite.History;

public class UserHome extends AppCompatActivity implements View.OnClickListener, View.OnLongClickListener
{
	private static final String tag = "UserHome";
	private static final int CHRENAME = 1;
	private static final int CHRM = 2;

	private EditText actionbox;
	private FloatingActionButton call, add;
	private LinearLayout contactList;
	private boolean inEdit = false;
	private Contact contactInEdit; //whenever renaming a contact just change its nickname here and pass around this object
	private Db db;
	private BroadcastReceiver myReceiver;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		//setup the command listener if it isn't already there
		//	it will already be there if you're coming back to the UserHome screen from doing something else
		synchronized (Vars.cmdListenerLock)
		{
			if(!Vars.cmdListenerRunning)
			{
				Intent cmdListenerIntent = new Intent(this, CmdListener.class);
				startService(cmdListenerIntent);
				Vars.cmdListenerRunning = true;
			}
		}

		setContentView(R.layout.activity_user_home);
		actionbox = (EditText)findViewById(R.id.user_home_actionbox);
		call = (FloatingActionButton)findViewById(R.id.user_home_call);
		call.setOnClickListener(this);
		add = (FloatingActionButton)findViewById(R.id.user_home_add);
		add.setOnClickListener(this);
		contactList = (LinearLayout)findViewById(R.id.user_home_contact_list);
		db = new Db(this);

		//build the contacts list
		ArrayList<Contact> allContacts = db.getContacts();
		for(Contact contact : allContacts)
		{
			addToContactList(contact);
		}

		//setup the ongoing notification shared accross screens that shows
		//	the state of the app: signed in, no internet, in call etc...
		Intent go2Home = new Intent(this, UserHome.class);
		PendingIntent go2HomePending = PendingIntent.getActivity(this, 0, go2Home, PendingIntent.FLAG_UPDATE_CURRENT);
		Vars.stateNotificationBuilder = new Notification.Builder(getApplicationContext())
				.setContentTitle(getString(R.string.app_name))
				.setContentText("text")
				.setSmallIcon(R.drawable.pqrs)
				.setContentIntent(go2HomePending)
				.setOngoing(true);
		NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.notify(Vars.stateNotificationId, Vars.stateNotificationBuilder.build());

		//receives the server response from clicking the 2 FAB buttons
		//	Click +: whether new contact to add is valid
		//	Click phone: whether the person you want to call is available
		myReceiver = new BroadcastReceiver()
		{
			@Override
			public void onReceive(Context context, Intent intent)
			{
				Utils.logcat(Const.LOGD, tag, "got a broadcasted intent");
				String type = intent.getStringExtra(Const.TYPE);

				//Result of clicking the "+" for adding a contact
				if(type.equals(Const.TYPELOOKUP))
				{
					String user = intent.getStringExtra(Const.LOOKUPNAME);
					String result = intent.getStringExtra(Const.LOOKUPRESULT);
					if(result.equals("exists"))
					{
						Contact newGuy = new Contact(user);
						db.insertContact(newGuy);
						addToContactList(newGuy);
					}
					else
					{
						Utils.showOk(UserHome.this, getString(R.string.alert_user_home_contact_notexist));
					}
				}

				//Result of clicking the phone button. If the user you're trying to call doesn't exist
				//	the server treats it as not available. It IS the server's job to verify all input given to it.
				else if (type.equals(Const.TYPEINIT))
				{
					boolean canInit = intent.getBooleanExtra(Const.CANINIT, false);
					if(canInit)
					{
						Utils.logcat(Const.LOGD, tag, "Starting call with " + Vars.callWith);
					}
					else
					{
						Utils.logcat(Const.LOGD, tag, "Can't start call");
						Utils.showOk(UserHome.this, getString(R.string.alert_user_home_cant_dial));
					}
				}
			}
		};
	}

	protected void onResume()
	{
		super.onResume();

		//receiver must be reregistered when loading this screen from the back button
		registerReceiver(myReceiver, new IntentFilter(Const.NOTIFYHOME));
	}

	@Override
	protected void onStop()
	{
		super.onStop();

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

			//check to see if the new contact to add already exists
			if(db.contactExists(actionBoxContact))
			{
				Utils.showOk(this, getString(R.string.alert_user_home_duplicate));
				return;
			}
			new LookupAsync().execute(actionBoxContact); //result will be processed in myReceiver BroadcastReceiver
		}
		else if (v == call)
		{
			String who = actionbox.getText().toString();
			boolean didInit;
			try
			{
				didInit = new CallInitAsync(who).execute().get(); //result will be processed in myReceiver BroadcastReceiver
			}
			catch (InterruptedException e)
			{
				Utils.logcat(Const.LOGE, tag, e.getStackTrace().toString());
				didInit = false;
			}
			catch (ExecutionException e)
			{
				Utils.logcat(Const.LOGE, tag, e.getStackTrace().toString());
				didInit = false;
			}

			//if the resulting dial out attempt didn't fail for technical reasons. save it in the user's history table
			if(didInit)
			{
				long now = Utils.getTimestamp();
				//don't need the nickname because db only records user name
				//	db doesn' need to record nickname because it will be figured out when drawing the history table
				Contact calling = new Contact(Vars.callWith);
				History history = new History(now, calling, Const.outgoing);
				db.insertHistory(history);
			}
			else
			{
				Utils.showOk(this, getString(R.string.alert_tehcnical_difficulties));
			}
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
			getSupportActionBar().setBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(this, R.color.colorAccent)));
		}
		else
		{
			getMenuInflater().inflate(R.menu.menu_main_set, menu);
			getSupportActionBar().setBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(this, R.color.colorPrimary)));
		}
		return super.onCreateOptionsMenu(menu);
	}

	//its only purpose is to be called when you click the logout button
	//for whatever reason you can't access android internals liek notification manager inside onOptionsItemSelected
	private void quit()
	{
		//get rid of the status notification
		NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancelAll();

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
			case R.id.menu_main_history:
				//TODO: once the history screen is made
				return true;
			case R.id.menu_main_logout:
				quit();
				return true;
			//use the same actions whether settings is form the main menu or edit menu
			case R.id.menu_main_settings:
			case R.id.menu_edit_settings:
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
							}
						})
						.setNeutralButton(R.string.alert_user_home_rename_button_nvm, new DialogInterface.OnClickListener()
						{
							@Override
							public void onClick(DialogInterface dialog, int which)
							{
								dialog.cancel();
								inEdit = false;
								invalidateOptionsMenu();
							}
						});
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
		if(!contact.getNickname().equals(""))
		{
			contactView.setText(contact.toString());
		}
		else
		{
			contactView.setText(contact.getName());
		}
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
}
