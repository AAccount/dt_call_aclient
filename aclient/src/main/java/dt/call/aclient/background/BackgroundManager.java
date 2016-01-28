package dt.call.aclient.background;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.AsyncTask;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

import dt.call.aclient.Const;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;

/**
 * Created by Daniel on 1/22/16.
 *
 * Once logged in. Manages setting up the CmdListener whenever wifi/lte drops, switches, reconnects
 */
public class BackgroundManager extends BroadcastReceiver
{
	private static final String tag = "BackgroundLoginManager";

	//for retrying the login
	private int retries = 5;
	private Timer retry = new Timer();

	@Override
	public void onReceive(final Context context, Intent intent)
	{
		Utils.logcat(Const.LOGD, tag, "received broadcast intent");
		if(Vars.uname == null || Vars.passwd == null)
		{
			//if the person hasn't logged in then there's no way to start the command listener
			//	since you won't have a command socket to listen on
			return;
		}

		//automatically sign in again when internet becomes available
		if(intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION))
		{
			Utils.logcat(Const.LOGD, tag, "Got a connectivity event from android");

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
				final Context pointer = context;
				TimerTask login = new TimerTask()
				{
					@Override
					public void run()
					{
						try
						{
							boolean signInOk = new LoginAsync(Vars.uname, Vars.passwd).execute().get();
							if(signInOk)
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
								Utils.logcat(Const.LOGE, tag, "Sign in failed. Retries: "+retries);
								retries--;
							}

							if(retries == 0)
							{//out of retires. give up
								Utils.logcat(Const.LOGD, tag, "Out of sign in retries");
								retry.cancel();
							}
						}
						catch (InterruptedException e)
						{
							Utils.logcat(Const.LOGE, tag, "async signin interrupted: " + e.getMessage());
						}
						catch (ExecutionException e)
						{
							Utils.logcat(Const.LOGE, tag, "async signin problem: " + e.getMessage());
						}
					}
				};
				//initial delay 0 because you want to try right away. if that doesn't work
				//	then you can wait a minute before trying again
				retry.schedule(login, 0, 60 * 1000);
			}
		}
		else if (intent.getAction().equals(Const.CMDDEAD))
		{
			Utils.logcat(Const.LOGD, tag, "command listener dead received");
			if(!Vars.hasInternet)
			{
				Utils.logcat(Const.LOGE, tag, "no internet connection to restart command listener");
				return;
			}

			//if you do have internet then try 5 times in a row.
			int retries = 5;
			while(retries > 0)
			{
				try
				{
					boolean didLogin = new LoginAsync(Vars.uname, Vars.passwd).execute().get();
					if(didLogin)
					{
						retries = 0;
						Utils.logcat(Const.LOGD, tag, "reestablished cmd listener");
					}
					else
					{
						retries--;
					}
				}
				catch (InterruptedException e)
				{
					Utils.logcat(Const.LOGE, tag, e.getStackTrace().toString());
				}
				catch (ExecutionException e)
				{
					Utils.logcat(Const.LOGE, tag, e.getStackTrace().toString());
				}
			}
		}
	}
}
