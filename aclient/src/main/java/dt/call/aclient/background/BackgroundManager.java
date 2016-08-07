package dt.call.aclient.background;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.SystemClock;

import dt.call.aclient.Const;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.background.async.KillSocketsAsync;
import dt.call.aclient.background.async.LoginAsync;
import dt.call.aclient.screens.InitialServer;
import dt.call.aclient.screens.UserHome;
import dt.call.aclient.sqlite.SQLiteDb;
import dt.call.aclient.sqlite.DBLog;

/**
 * Created by Daniel on 1/22/16.
 *
 * Once logged in. Manages setting up the CmdListener whenever wifi/lte drops, switches, reconnects
 * Manages the heartbeat service which preiodically checks to see if the connections are really good or not.
 */
public class BackgroundManager extends BroadcastReceiver
{
	private static final String tag = "BackgroundManager";
	private Context context;

	@Override
	public void onReceive(final Context context, Intent intent)
	{
		this.context = context;
		Utils.logcat(Const.LOGD, tag, "received broadcast intent");
		Utils.initAlarmVars(); //double check to make sure these things are setup
		AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

		if(Vars.uname == null || Vars.passwd == null)
		{
			//if the person hasn't logged in then there's no way to start the command listener
			//	since you won't have a command socket to listen on
			Utils.logcat(Const.LOGD, tag, "can't login when there is no username/password information");
			return;
		}


		if(intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION))
		{
			Utils.logcat(Const.LOGD, tag, "Got a connectivity event from android");
			if(intent.getExtras() != null && intent.getExtras().getBoolean(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false))
			{//internet lost case
				Utils.logcat(Const.LOGD, tag, "Internet was lost");

				//Apparently you can't close a socket from here because it's on the UI thread???
				Vars.dontRestart = true; //why bother when there's no internet
				new KillSocketsAsync().execute();

				Vars.hasInternet = false;
				manager.cancel(Vars.pendingHeartbeat);
				manager.cancel(Vars.pendingRetries);
			}
			else
			{
				//internet reconnected case
				// don't immediately try to reconnect on fail in case the person has to do a wifi sign in
				//	or other things
				Utils.logcat(Const.LOGD, tag, "Internet was reconnected");
				Vars.hasInternet = true;

				relogin();
			}
		}
		else if (intent.getAction().equals(Const.BROADCAST_BK_CMDDEAD))
		{
			Utils.logcat(Const.LOGD, tag, "command listener dead received");

			//all of this just to address the stupid java socket issue where it might just endlessly die/reconnect
			//initialize the quick dead count and timestamp if this is the first time
			long now = System.currentTimeMillis();
			long deadDiff =  now - Vars.lastDead;
			Vars.lastDead = now;
			if(deadDiff < Const.QUICK_DEAD_THRESHOLD)
			{
				Vars.quickDeadCount++;
				Utils.logcat(Const.LOGW, tag, "Another quick death (java socket stupidity) occured. Current count: " + Vars.quickDeadCount);
			}

			//with the latest quick death, was it 1 too many? if so restart the app
			//https://stackoverflow.com/questions/6609414/how-to-programatically-restart-android-app
			if(Vars.quickDeadCount > Const.QUICK_DEAD_MAX)
			{
				Utils.logcat(Const.LOGE, tag, "Too many quick deaths (java socket stupidities). Restarting the app");

				//self restart, give it a 5 seconds to quit
				Intent selfStart = new Intent(Vars.applicationContext, InitialServer.class);
				int pendingSelfId = 999;
				PendingIntent selfStartPending = PendingIntent.getActivity(Vars.applicationContext, pendingSelfId, selfStart, PendingIntent.FLAG_CANCEL_CURRENT);
				AlarmManager alarmManager = (AlarmManager)Vars.applicationContext.getSystemService(Context.ALARM_SERVICE);
				alarmManager.set(AlarmManager.RTC, System.currentTimeMillis()+Const.RESTART_DELAY, selfStartPending);

				//hopefully 5 seconds will be enough to get out
				Utils.quit();
				return;
			}

			if(!Vars.hasInternet)
			{
				Utils.logcat(Const.LOGD, tag, "no internet connection to restart command listener");
				return;
			}
			relogin();
		}
	}

	/**
	 * If you need to relogin, try immediately first. If that doesn't work, then start the retry
	 * process.
	 */
	private void relogin()
	{
		AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		boolean firstTry = false;
		try
		{
			firstTry = new LoginAsync(Vars.uname, Vars.passwd, false).execute().get();
		}
		catch (Exception e)
		{
			Utils.logcat(Const.LOGE, tag, "exception on first try of relogin");
		}

		if(!firstTry)
		{
			Utils.logcat(Const.LOGE, tag, "first try relogin failed (check if exception); using alarm retries");
			manager.cancel(Vars.pendingRetries);
			manager.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), Const.ONE_MIN, Vars.pendingRetries);
		}
	}
}
