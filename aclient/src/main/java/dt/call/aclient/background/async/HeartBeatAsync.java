package dt.call.aclient.background.async;

import android.app.AlarmManager;
import android.content.Context;
import android.os.AsyncTask;

import dt.call.aclient.Const;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.sqlite.SQLiteDb;
import dt.call.aclient.sqlite.DBLog;

/**
 * Created by Daniel on 4/17/16.
 */
public class HeartBeatAsync extends AsyncTask<String, String, Boolean>
{
	private static final String tag = "HeartBeatAsync";
	@Override
	protected Boolean doInBackground(String... params)
	{
		try
		{
			Vars.commandSocket.getOutputStream().write(Const.JBYTE.getBytes());
			Vars.mediaSocket.getOutputStream().write(Const.JBYTE.getBytes());
			Utils.setExactWakeup(Const.HEARTBEAT_FREQ, Vars.pendingHeartbeat);
			Utils.logcat(Const.LOGD, tag, "heart beat sent and ok");
			return true;
		}
		catch (Exception e)
		{
			Utils.logcat(Const.LOGE, tag, "heart beat failed");
			Utils.dumpException(tag, e);
			AlarmManager manager = (AlarmManager)Vars.applicationContext.getSystemService(Context.ALARM_SERVICE);
			manager.cancel(Vars.pendingHeartbeat); //login will reestablish this

			//KillSocketsAsync
			try
			{
				if(Vars.commandSocket != null)
				{
					Vars.commandSocket.close();
				}
			}
			catch (Exception e2)
			{
				Utils.logcat(Const.LOGE, tag, "inner exception problem");
				Utils.dumpException(tag, e2);
			}
			Vars.commandSocket = null;

			try
			{
				if(Vars.mediaSocket != null)
				{
					Vars.mediaSocket.close();
				}
			}
			catch (Exception e3)
			{
				Utils.logcat(Const.LOGE, tag, "inner exception problem");
				Utils.dumpException(tag, e3);
			}
			Vars.mediaSocket = null;
			return false;
		}
	}
}
