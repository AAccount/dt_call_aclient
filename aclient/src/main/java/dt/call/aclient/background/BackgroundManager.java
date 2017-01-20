package dt.call.aclient.background;

import android.app.AlarmManager;
import android.app.PendingIntent;
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
import dt.call.aclient.screens.InitialServer;

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
		if(Vars.applicationContext == null)
		{
			//sometimes intents come in when the app is in the process of shutting down so all the contexts won't work.
			//it's shutting down anyways. no point of starting something
			return;
		}

		Utils.initAlarmVars(); //double check to make sure these things are setup
		AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

		if(Vars.uname == null || Vars.passwd == null)
		{
			//if the person hasn't logged in then there's no way to start the command listener
			//	since you won't have a command socket to listen on
			Utils.logcat(Const.LOGW, tag, "user name and password aren't available?");
		}

		String action = intent.getAction();

		//to prevent timing problems, if android's connectivity_action AND workaround HAS_INTERNET broadcast are available
		//to signal return of internet connectivity, only listen for one of them.
		String connectivity;
		if(Build.VERSION.SDK_INT >= Const.MINVER_MANUAL_HAS_INTERNET)
		{
			connectivity = Const.BROADCAST_HAS_INTERNET;
		}
		else
		{
			connectivity = ConnectivityManager.CONNECTIVITY_ACTION;
		}

		if(action.equals(connectivity))
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
					manager.cancel(Vars.pendingRetries);

					Utils.logcat(Const.LOGD, tag, "internet was reconnected by broadcast: " + connectivity);
					new LoginAsync(Vars.uname, Vars.passwd).execute();
				}
			}
			else
			{
				Utils.logcat(Const.LOGD, tag, "android detected internet loss");
				handleNoInternet(context);
			}
			//command listener does a better of job of figuring when the internet died than android's connectivity manager.
			//android's connectivity manager doesn't always react to subway internet loss
		}
		else if (action.equals(Const.BROADCAST_BK_CMDDEAD))
		{
			String loge = "command listener dead received\n";

			//set persistent notification as offline for now while reconnect is trying
			Utils.setNotification(R.string.state_popup_offline, R.color.material_grey, Vars.go2HomePending);

			//pending intents cancelled by command listener to prevent a timing problem where sockets are closed at the same
			//time a heart beat pending intent is fired.

			/****************************************************************************************************************/
			//all of this just to address the stupid java socket issue where it might just endlessly die/reconnect
			//initialize the quick dead count and timestamp if this is the first time
			long now = System.currentTimeMillis();
			long deadDiff =  now - Vars.lastDead;
			Vars.lastDead = now;
			if(deadDiff <= Const.QUICK_DEAD_THRESHOLD)
			{
				Vars.quickDeadCount++;
				loge = loge + "Another quick death (java socket stupidity) occured. Current count: " + Vars.quickDeadCount + "\n";
			}
			else
			{
				Vars.quickDeadCount = 0;
			}

			//with the latest quick death, was it 1 too many? if so restart the app
			//https://stackoverflow.com/questions/6609414/how-to-programatically-restart-android-app
			if(Vars.quickDeadCount == Const.QUICK_DEAD_MAX)
			{
				loge = loge + "Too many quick deaths (java socket stupidities). Restarting the app\n";
				Utils.logcat(Const.LOGE, tag, loge);
				//self restart, give it a 5 seconds to quit
				Intent selfStart = new Intent(Vars.applicationContext, InitialServer.class);
				int pendingSelfId = 999;
				PendingIntent selfStartPending = PendingIntent.getActivity(Vars.applicationContext, pendingSelfId, selfStart, PendingIntent.FLAG_CANCEL_CURRENT);
				manager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis()+Const.RESTART_DELAY, selfStartPending);

				//hopefully 5 seconds will be enough to get out
				Utils.quit();
				return;
			}
			else
			{ //app does not need to restart. still record the accumulated error messages
				Utils.logcat(Const.LOGE, tag, loge);
			}
			/****************************************************************************************************************/

			//if the network is dead then don't bother
			if(!Utils.hasInternet())
			{
				Utils.logcat(Const.LOGD, tag, "No internet detected from commnad listener dead");
				handleNoInternet(context);
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
		else if (action.equals(Const.ALARM_ACTION_RETRY))
		{
			Utils.logcat(Const.LOGD, tag, "login retry received");

			//no point of a retry if there is no internet to try on
			if(!Utils.hasInternet())
			{
				Utils.logcat(Const.LOGD, tag, "no internet for sign in retry");
				handleNoInternet(context);
				return;
			}

			new LoginAsync(Vars.uname, Vars.passwd).execute();

		}
		else if(action.equals(Const.BROADCAST_LOGIN_BG))
		{
			boolean ok = intent.getBooleanExtra(Const.BROADCAST_LOGIN_RESULT, false);
			Utils.logcat(Const.LOGD, tag, "got login result of: " + ok);

			Intent loginResult = new Intent(Const.BROADCAST_LOGIN_FG);
			loginResult.putExtra(Const.BROADCAST_LOGIN_RESULT, ok);
			context.sendBroadcast(loginResult);

			if(!ok)
			{
				Utils.setExactWakeup(Const.RETRY_FREQ, Vars.pendingRetries);
			}
		}
	}

	private void handleNoInternet(Context mainContext)
	{
		AlarmManager manager = (AlarmManager)mainContext.getSystemService(Context.ALARM_SERVICE);
		manager.cancel(Vars.pendingHeartbeat);
		manager.cancel(Vars.pendingRetries);

		//for android 7.0+ manually trigger a "connectivity action" when the internet comes back to sign on again
		if(Build.VERSION.SDK_INT >= Const.MINVER_MANUAL_HAS_INTERNET)
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