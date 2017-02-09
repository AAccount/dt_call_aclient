package dt.call.aclient.background;

import android.app.AlarmManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Build;

import dt.call.aclient.CallState;
import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.background.async.HeartBeatAsync;
import dt.call.aclient.background.async.LoginAsync;

/**
 * Created by Daniel on 1/22/16.
 *
 * Once logged in. Manages setting up the CmdListener whenever wifi/lte drops, switches, reconnects
 * Manages the heartbeat service which preiodically checks to see if the connections are really good or not.
 */
public class BackgroundManager extends BroadcastReceiver
{
	private static final String tag = "BackgroundManager";

	@Override
	public void onReceive(final Context context, Intent intent)
	{

		//after idling for a long time, Vars.applicationContext goes null sometimes
		//the show must go on
		//double check to make sure these things are setup
		Vars.applicationContext = context.getApplicationContext();
		Utils.initAlarmVars();
		AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

		if(Vars.uname == null || Vars.passwd == null)
		{
			//if the person hasn't logged in then there's no way to start the command listener
			//	since you won't have a command socket to listen on
			Utils.logcat(Const.LOGW, tag, "user name and password aren't available?");
		}

		String action = intent.getAction();
		Utils.logcat(Const.LOGD, tag, "background manager received: " + action);

		//to prevent timing problems, if android's connectivity_action AND workaround HAS_INTERNET broadcast are available
		//to signal return of internet connectivity, only listen for one of them.
		if(Const.NEEDS_MANUAL_INTERNET_DETECTION && action.equals(Const.BROADCAST_HAS_INTERNET))
		{
			//because internet detection is manual, no false positives
			manager.cancel(Vars.pendingHeartbeat);
			manager.cancel(Vars.pendingHeartbeat2ndary);
			manager.cancel(Vars.pendingRetries);
			manager.cancel(Vars.pendingRetries2ndary);

			Utils.logcat(Const.LOGD, tag, "internet was reconnected by manual detection");
			new LoginAsync(Vars.uname, Vars.passwd).execute();
			return;
		}
		else if (!Const.NEEDS_MANUAL_INTERNET_DETECTION && action.equals(ConnectivityManager.CONNECTIVITY_ACTION))
		{
			if(Utils.hasInternet())
			{
				//internet reconnected case
				if(Vars.commandSocket == null)
				{
					/* For the Moto G3 on CM13.1 sometimes the cell signal dies momentarily (tower switch?) while driving
					 * and then comes back. It only announces a connectivity action when it reconnects (not when it dies).
					 * It appears the connection is still good. Don't try to relogin unless going from real no internet --> internet.
					 *
					 * Relogging in while the connections are still good and command lister is active causes undefined weird behavior.
					 * Command listenter reliably dies when there is no internet. Null command socket should be a good indicator if this
					 * is really the case of no internet --> internet.
					 */
					manager.cancel(Vars.pendingHeartbeat);
					manager.cancel(Vars.pendingHeartbeat2ndary);
					manager.cancel(Vars.pendingRetries);
					manager.cancel(Vars.pendingRetries2ndary);

					Utils.logcat(Const.LOGD, tag, "internet was reconnected by legacy android automatic detection");
					new LoginAsync(Vars.uname, Vars.passwd).execute();
				}
			}
			else
			{
				Utils.logcat(Const.LOGD, tag, "android detected internet loss");
				handleNoInternet(context);
			}
			return;
		}

		if (action.equals(Const.BROADCAST_RELOGIN))
		{
			//set persistent notification as offline for now while reconnect is trying
			Utils.setNotification(R.string.state_popup_offline, R.color.material_grey, Vars.go2HomePending);

			//pending intents cancelled by command listener to prevent a timing problem where sockets are closed at the same
			//time a heart beat pending intent is fired.

			//if the network is dead then don't bother
			if(!Utils.hasInternet())
			{
				Utils.logcat(Const.LOGD, tag, "No internet detected from commnad listener dead");
				handleNoInternet(context);
				return;
			}

			//originally expected that when heartbeat detects dead sockets and closes them, it will trigger
			// command listener to die and issue a "command listener dead". assumption is wrong if idling for
			// a long time. both command listener and heart beat broadcast a relogin when needed. to prevent double
			// logins when both send, only relogin if you have to. if it fails it will issue another relogin(retry) in 5 mins
			if(Vars.commandSocket != null || Vars.mediaSocket != null)
			{
				Utils.logcat(Const.LOGW, tag, "got sockets aren't null;");
				return;
			}

			new LoginAsync(Vars.uname, Vars.passwd).execute();
		}
		else if(action.equals(Const.ALARM_ACTION_HEARTBEAT))
		{
			if (Vars.state == CallState.NONE && Utils.hasInternet()) //check call state first then hasInternet function. prevent log spam and unnecessary effort
			{
				Utils.logcat(Const.LOGD, tag, "sending heart beat");
				new HeartBeatAsync().execute();
			}
			else if (!Utils.hasInternet())
			{
				Utils.logcat(Const.LOGW, tag, "no internet to send heart beat on");

				//heart beat is the ONLY action where the sockets could be live. get rid of them if there is no internet
				// command listener dead (dead by definition), login retry (login failed, no sockets)
				// login bg (failure will issue a retry, success means everything is ok) has_internet(coming from previously dead)
				new Thread(new Runnable()
				{
					@Override
					public void run()
					{
						Utils.killSockets();
					}
				}).start();
				handleNoInternet(context);
			}
		}
	}

	private void handleNoInternet(Context mainContext)
	{
		AlarmManager manager = (AlarmManager)mainContext.getSystemService(Context.ALARM_SERVICE);
		manager.cancel(Vars.pendingHeartbeat);
		manager.cancel(Vars.pendingHeartbeat2ndary);
		manager.cancel(Vars.pendingRetries);
		manager.cancel(Vars.pendingRetries2ndary);

		//for android 7.0+ manually trigger a "connectivity action" when the internet comes back to sign on again
		if(Const.NEEDS_MANUAL_INTERNET_DETECTION)
		{
			ComponentName jobServiceReceiver = new ComponentName(Const.PACKAGE_NAME, JobServiceReceiver.class.getName());
			JobInfo.Builder builder = new JobInfo.Builder(1, jobServiceReceiver).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
			builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
			JobScheduler jobScheduler = (JobScheduler)mainContext.getSystemService(Context.JOB_SCHEDULER_SERVICE);
			int result = jobScheduler.schedule(builder.build());
			Utils.logcat(Const.LOGD, tag, "putting in a new job with status: " + result);
		}
	}
}