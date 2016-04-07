package dt.call.aclient.background;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;

import java.util.Timer;
import java.util.TimerTask;

import dt.call.aclient.Const;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.background.Async.KillSocketsAsync;
import dt.call.aclient.background.Async.LoginAsync;

/**
 * Created by Daniel on 1/22/16.
 * -heartbeat service added March 26 2016
 *
 * Once logged in. Manages setting up the CmdListener whenever wifi/lte drops, switches, reconnects
 * Manages the heartbeat service which preiodically checks to see if the connections are really good or not.
 */
public class BackgroundManager extends BroadcastReceiver
{
	private static final String tag = "BackgroundManager";
	private static final int frequency = 1*60; //in seconds

	private int retries; //always reset retries before launching the timer
	//https://stackoverflow.com/questions/11502693/timer-not-stopping-in-android
	private static Timer retry = new Timer();
	private static Timer heartbeat = new Timer();

	@Override
	public void onReceive(final Context context, Intent intent)
	{
		Utils.logcat(Const.LOGD, tag, "received broadcast intent");
		if(Vars.uname == null || Vars.passwd == null)
		{
			//if the person hasn't logged in then there's no way to start the command listener
			//	since you won't have a command socket to listen on
			Utils.logcat(Const.LOGD, tag, "can't login when there is no username/password information");
			return;
		}

		/**
		 * Tries 5 times to login, waiting a minute between each attempt in case you need to do something
		 * like signin to a public wifi, or wait for a better connection because a bad connection caused
		 * the failure
		 *
		 * On the exceptions, don't cancel the timer. Wait for the retires to run out
		 */
		TimerTask login = new TimerTask()
		{
			@Override
			public void run()
			{
				try
				{
					boolean didSignIn = new LoginAsync(Vars.uname, Vars.passwd).execute().get();
					if(didSignIn)
					{//the sign in succeeded. setup the cmd listener thread and stop the retries
						Utils.logcat(Const.LOGD, tag, "Sign in succeeded");
						synchronized (Vars.cmdListenerLock)
						{
							if(Vars.cmdListenerRunning)
							{
								Utils.logcat(Const.LOGW, tag, "command listener is running when a request to restart it is in progress??");
								Vars.dontRestart = true;
								new KillSocketsAsync().execute().get();
								Vars.cmdListenerRunning = false;
							}
							Intent cmdListenerIntent = new Intent(context, CmdListener.class);
							context.startService(cmdListenerIntent);
							Vars.cmdListenerRunning = true;
						}
						retries = 0;
						startHeartbeat();
					}
					else
					{//sign in failed. decrease retries by 1
						retries--;
						Utils.logcat(Const.LOGW, tag, "Sign in failed. Retries: " + retries);
					}
				}
				catch (Exception e)
				{
					Utils.dumpException(tag, e);
					retries--;
				}

				if(retries == 0)
				{//out of retires or retry succeeded
					Utils.logcat(Const.LOGW, tag, "out of retries to sing in again. will have to be done by hand");
					retry.cancel();
					retry.purge();
				}
			}
		};

		if(intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION))
		{
			Utils.logcat(Const.LOGD, tag, "Got a connectivity event from android");
			retry.cancel(); //just in case it's already running from a previous intent
			retry.purge();

			if(intent.getExtras() != null && intent.getExtras().getBoolean(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false))
			{//internet lost case
				Utils.logcat(Const.LOGD, tag, "Internet was lost");

				//Apparently you can't close a socket from here because it's on the UI thread???
				Vars.dontRestart = true; //why bother when there's no internet
				new KillSocketsAsync().execute();
				retry.cancel();
				retry.purge();
				heartbeat.cancel();
				heartbeat.purge();
				Vars.hasInternet = false;
			}
			else
			{
				//internet reconnected case
				// don't immediately try to reconnect on fail in case the person has to do a wifi sign in
				//	or other things
				Utils.logcat(Const.LOGD, tag, "Internet was reconnected");
				Vars.hasInternet = true;

				//initial delay 0 because you want to try right away. if that doesn't work
				//	then you can wait a minute before trying again
				//	Retry will restart the heartbeat service when sign in succeeds
				retries = 5;
				retry.cancel();
				retry.purge();
				retry = new Timer();
				retry.schedule(login, 0, 60*1000);
			}
		}
		else if (intent.getAction().equals(Const.BROADCAST_BK_CMDDEAD))
		{
			Utils.logcat(Const.LOGD, tag, "command listener dead received");
			if(!Vars.hasInternet)
			{
				Utils.logcat(Const.LOGW, tag, "no internet connection to restart command listener");
				return;
			}
			retries = 5;
			retry.cancel();
			retry.purge();
			retry = new Timer();
			retry.schedule(login, 0, 60*1000);
		}
		else if(intent.getAction().equals(Const.BROADCAST_BK_HEARTBEAT))
		{
			/**
			 * Because this class will only be instantiated once (by android os) it's pretty much a singleton
			 *
			 * By calling cancel first before doing anything, no matter how many heartbeat_doit = true intents are sent
			 * in a row, the heartbeat service will only ever have 1 copy of itself running. Therefore, no Vars.heartbeatRunning
			 * or similar variable is needed like command listener
			 */
			if(intent.getExtras().getBoolean(Const.BROADCAST_BK_HEARTBEAT_DOIT, false))
			{
				Utils.logcat(Const.LOGD, tag, "starting heartbeat connection diagnostics");
				startHeartbeat();
			}
			else
			{
				heartbeat.cancel();
				heartbeat.purge();
			}
		}
	}

	private void startHeartbeat()
	{
		TimerTask heartbeatTask = new TimerTask()
		{
			@Override
			public void run()
			{
				if(Vars.mediaSocket == null || Vars.commandSocket == null)
				{
					Utils.logcat(Const.LOGD, tag, "no connection to send hearbeat on");
					return;
				}

				try
				{
					Utils.logcat(Const.LOGD, tag, "sending heartbeat");
					Vars.commandSocket.getOutputStream().write(Const.JBYTE.getBytes());
					Vars.mediaSocket.getOutputStream().write(Const.JBYTE.getBytes());
				}
				catch (Exception e)
				{
					Utils.dumpException(tag, e);
					new KillSocketsAsync().execute();
					//command listener would've died and sent out its dead broadcast to login again
				}
			}
		};
		heartbeat.cancel();
		heartbeat.purge();
		heartbeat = new Timer();
		heartbeat.schedule(heartbeatTask, 0, frequency * 1000);
	}
}
