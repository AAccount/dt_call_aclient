package dt.call.aclient.background;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.concurrent.ExecutionException;

import dt.call.aclient.Const;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.background.async.HeartBeatAsync;
import dt.call.aclient.background.async.KillSocketsAsync;
import dt.call.aclient.background.async.LoginAsync;
import dt.call.aclient.sqlite.DB;
import dt.call.aclient.sqlite.DBLog;

/**
 * Created by Daniel on 4/13/16.
 *
 * Properly handle signin retries and heartbeat through recurring alarm instead of
 * unreliable timer task or Thread.sleep(5mins)
 */
public class AlarmReceiver extends BroadcastReceiver
{
	private static final String tag = "AlarmReceiver";

	//initially retry every 1, 5 then eventually 10 mins.
	private static final String INITIAL = "initial";
	private static final String SECOND = "second";
	private static final String INDEFINITE = "indefinite";
	private static String retryStage = INITIAL;
	private static int retried = 0;

	private DB db;

	@Override
	public void onReceive(Context context, Intent intent)
	{
		db = new DB(context);
		AlarmManager manager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);

		String action = intent.getStringExtra(Const.ALARM_ACTION);
		if(action.equals(Const.ALARM_ACTION_HEARTBEAT))
		{
			db.insertLog(new DBLog(tag, "received heart beat alarm"));
			Utils.logcat(Const.LOGD, tag, "received heart beat alarm");
			if(!Vars.hasInternet)
			{
				db.insertLog(new DBLog(tag, "no internet to try hearbeat on. SHOULD'VE EVEN BEEN TOLD TO DO SO"));
				Utils.logcat(Const.LOGW, tag, "no internet to try hearbeat on. SHOULD'VE EVEN BEEN TOLD TO DO SO");
				return;
			}
			new HeartBeatAsync().execute();

		}
		else if (action.equals(Const.ALARM_ACTION_RETRY))
		{
			db.insertLog(new DBLog(tag, "received retry alarm; stage: " + retryStage + " @ " + retried + " times"));
			Utils.logcat(Const.LOGD, tag, "received retry alarm; stage: " + retryStage + " @ " + retried + " times");
			try
			{
				boolean loginok = new LoginAsync(Vars.uname, Vars.passwd, false).execute().get();
				if(!loginok)
				{
					retried++;

					/**
					 * Used tiered retry approach: initially try every minute. If that doesn't work out assume there is
					 * a temporary problem with either the server or that person's internet. Don't waste battery and try
					 * 5 mins. If after retrying every 5 mins doesn't work, then try every 10.
					 */
					if(retried == 5 && !retryStage.equals(INDEFINITE))
					{
						if(retryStage.equals(INITIAL))
						{
							manager.cancel(Vars.pendingRetries);
							manager.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), Const.FIVE_MINS, Vars.pendingRetries);
							retryStage = SECOND;
							retried = 0;
						}
						else if(retryStage.equals(SECOND))
						{
							manager.cancel(Vars.pendingRetries);
							manager.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), Const.TEN_MINS, Vars.pendingRetries);
							retryStage = INDEFINITE;
							retried = 0;
						}
					}
				}
				else
				{
					manager.cancel(Vars.pendingRetries);
				}
			}
			catch (Exception e)
			{
				db.insertLog(new DBLog(tag, "problems trying login in: " + e.getClass().getName()));
				Utils.dumpException(tag, e);
			}
		}
		else
		{
			db.insertLog(new DBLog(tag, "received ??unknown?? alarm"));
		}
	}
}
