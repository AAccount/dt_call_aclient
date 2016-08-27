package dt.call.aclient.background;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;

import dt.call.aclient.CallState;
import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.background.async.HeartBeatAsync;
import dt.call.aclient.background.async.KillSocketsAsync;
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
			Utils.logcat(Const.LOGW, tag, "user name and password aren't available??, NOT CONTINUING");
			return;
		}

		String action = intent.getAction();
		if(action.equals(ConnectivityManager.CONNECTIVITY_ACTION))
		{
			Utils.logcat(Const.LOGD, tag, "Got a connectivity event from android");
			manager.cancel(Vars.pendingHeartbeat);
			manager.cancel(Vars.pendingRetries);
			new KillSocketsAsync().execute();

			if(Utils.hasInternet())
			{
				//internet reconnected case
				Utils.logcat(Const.LOGD, tag, "Internet was reconnected");
				new LoginAsync(Vars.uname, Vars.passwd, Const.BROADCAST_LOGIN_BG).execute();
			}
			else
			{
				Utils.logcat(Const.LOGD, tag, "android detected internet loss");
			}
			//command listener does a better of job of figuring when the internet died than android's connectivity manager.
			//android's connectivity manager doesn't always get subway internet loss
		}
		else if (action.equals(Const.BROADCAST_BK_CMDDEAD))
		{
			Utils.logcat(Const.LOGD, tag, "command listener dead received");

			//set persistent notification as offline for now while reconnect is trying
			Utils.setNotification(R.string.state_popup_offline, R.color.material_grey, Vars.go2HomePending);

			//cleanup the pending intents and make sure the old sockets are gone before making new ones
			manager.cancel(Vars.pendingHeartbeat);
			manager.cancel(Vars.pendingRetries);
			new KillSocketsAsync().execute(); //make sure everything is good and dead

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
			if(Vars.quickDeadCount == Const.QUICK_DEAD_MAX)
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

			//if the network is dead then don't bother
			if(!Utils.hasInternet())
			{
				Utils.logcat(Const.LOGD, tag, "No internet detected from commnad listener dead");
				return;
			}

			new LoginAsync(Vars.uname, Vars.passwd, Const.BROADCAST_LOGIN_BG).execute();
		}
		else if(action.equals(Const.ALARM_ACTION_HEARTBEAT))
		{
			Utils.logcat(Const.LOGD, tag, "received heart beat alarm");
			if (!Utils.hasInternet())
			{
				Utils.logcat(Const.LOGW, tag, "no internet to try hearbeat on. SHOULD'VE EVEN BEEN TOLD TO DO SO");

				//no point of continuing the heart beat service if there is no internet
				manager.cancel(Vars.pendingHeartbeat);
				return;
			}

			//only send out a heartbeat if not in a call or waiting for one. don't want to send garbage on the media port
			// when there might be call data. it will probably produce weird sound
			if (Vars.state == CallState.NONE)
			{
				new HeartBeatAsync().execute();
			}

		}
		else if (action.equals(Const.ALARM_ACTION_RETRY))
		{
			Utils.logcat(Const.LOGD, tag, "login retry received");

			//no point of a retry if there is no internet to try on
			if(!Utils.hasInternet())
			{
				Utils.logcat(Const.LOGD, tag, "no internet for sign in retry");
				manager.cancel(Vars.pendingRetries);
				return;
			}

			new LoginAsync(Vars.uname, Vars.passwd, Const.BROADCAST_LOGIN_BG).execute();

		}
		else if(action.equals(Const.BROADCAST_LOGIN_BG))
		{
			Utils.logcat(Const.LOGD, tag, "BackgroundManager initiated login process received a callback");
			boolean ok = intent.getBooleanExtra(Const.BROADCAST_LOGIN_RESULT, false);
			if(ok)
			{
				manager.cancel(Vars.pendingRetries);
			}
			//else keep the alarm should run again in the next 5 minutes
		}
	}
}