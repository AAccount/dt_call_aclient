package dt.call.aclient.background.async;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import dt.call.aclient.Const;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;

/**
 * Created by Daniel on 4/17/16.
 */
public class HeartBeatAsync extends AsyncTask<String, String, Boolean>
{
	private static final String tag = "HeartBeatAsync";
	@Override
	protected Boolean doInBackground(String... params)
	{
		AlarmManager manager = (AlarmManager)Vars.applicationContext.getSystemService(Context.ALARM_SERVICE);
		manager.cancel(Vars.pendingHeartbeat); //login will reestablish this
		manager.cancel(Vars.pendingHeartbeat2ndary);

		try
		{
			Vars.commandSocket.getOutputStream().write(Const.JBYTE.getBytes());
			Utils.setExactWakeup(Vars.pendingHeartbeat, Vars.pendingHeartbeat2ndary);
			Utils.logcat(Const.LOGD, tag, "heart beat sent and ok");
			return true;
		}
		catch (Exception e)
		{
			Utils.logcat(Const.LOGE, tag, "heart beat failed");
			Utils.dumpException(tag, e);

			Utils.killSockets();

			//if idling for a long time, killing sockets will not trigger command listener to broadcast a (formerly) command listener dead
			// (now called relogin). do it here too.
			Intent relogin = new Intent(Const.BROADCAST_RELOGIN);
			Vars.applicationContext.sendBroadcast(relogin);
			return false;
		}
	}
}
