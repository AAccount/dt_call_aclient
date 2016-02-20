package dt.call.aclient.background;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

import dt.call.aclient.Const;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.background.Async.KillSocketsAsync;
import dt.call.aclient.background.Async.LoginAsync;

/**
 * Created by Daniel on 1/22/16.
 *
 * Once logged in. Manages setting up the CmdListener whenever wifi/lte drops, switches, reconnects
 */
public class BackgroundManager extends BroadcastReceiver
{
	private static final String tag = "BackgroundLoginManager";

	//for retrying the login
	private int retries; //always reset retries before launching the timer
	private Timer retry = new Timer();

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
		 * like signin to a public wifi, or wait for a better connection because the bad connection casued
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
							if(!Vars.cmdListenerRunning)
							{
								Intent cmdListenerIntent = new Intent(context, CmdListener.class);
								context.startService(cmdListenerIntent);
								Vars.cmdListenerRunning = true;
							}
						}
						retries = 0;
					}
					else
					{//sign in failed. decrease retries by 1
						retries--;
						Utils.logcat(Const.LOGW, tag, "Sign in failed. Retries: "+retries);
					}

					if(retries == 0)
					{//out of retires or retry succeeded
						retry.cancel();
					}
				}
				catch (InterruptedException e)
				{
					Utils.logcat(Const.LOGE, tag, "async signin InterruptedException: " + Utils.dumpException(e));
				}
				catch (ExecutionException e)
				{
					Utils.logcat(Const.LOGE, tag, "async signin ExecutionException: " + Utils.dumpException(e));
				}
			}
		};

		if(intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION))
		{
			Utils.logcat(Const.LOGD, tag, "Got a connectivity event from android");
			retry.cancel(); //just in case it's already running from a previous intent

			if(intent.getExtras() != null
					&& intent.getExtras().getBoolean(ConnectivityManager.EXTRA_NO_CONNECTIVITY, Boolean.FALSE))
			{//internet lost case
				Utils.logcat(Const.LOGD, tag, "Internet was lost");

				//Apparently you can't close a socket from here because it's on the UI thread???
				new KillSocketsAsync().execute();
				Vars.hasInternet = false;
			}
			else
			{
				//internet reconnected case
				// don't immediately try to reconnect on fail incase the person has to do a wifi sign in
				//	or other things
				Utils.logcat(Const.LOGD, tag, "Internet was reconnected");
				Vars.hasInternet = true;

				//initial delay 0 because you want to try right away. if that doesn't work
				//	then you can wait a minute before trying again
				retries = 5;
				retry.schedule(login, 0, 60*1000);
			}
		}
		else if (intent.getAction().equals(Const.BROADCAST_BK_CMDDEAD))
		{
			Utils.logcat(Const.LOGD, tag, "command listener dead received");
			if(!Vars.hasInternet)
			{
				Utils.logcat(Const.LOGE, tag, "no internet connection to restart command listener");
				return;
			}
			retries = 5;
			retry.schedule(login, 0, 60*1000);
		}
	}
}
