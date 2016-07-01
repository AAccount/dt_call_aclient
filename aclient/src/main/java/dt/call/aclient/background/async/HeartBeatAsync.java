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
			Utils.logcat(Const.LOGD, tag, "trying to send heart beat");
			Vars.commandSocket.getOutputStream().write(Const.JBYTE.getBytes());
			Vars.mediaSocket.getOutputStream().write(Const.JBYTE.getBytes());
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
			{//kill both connections to force stop threads listening/using into an exception
				if(Vars.commandSocket != null)
				{
					Vars.commandSocket.close();
				}
				if(Vars.mediaSocket != null)
				{
					Vars.mediaSocket.close();
				}
				Vars.commandSocket = null;
				Vars.mediaSocket = null;
			}
			catch (Exception e2)
			{
				Utils.logcat(Const.LOGE, tag, "inner exception problem");
				Utils.dumpException(tag, e2);
			}
			return false;
		}
	}
}
