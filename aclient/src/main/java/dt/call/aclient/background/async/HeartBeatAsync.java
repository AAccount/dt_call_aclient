package dt.call.aclient.background.async;

import android.os.AsyncTask;

import dt.call.aclient.Const;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.background.BackgroundManager2;

/**
 * Created by Daniel on 4/17/16.
 */
public class HeartBeatAsync extends AsyncTask<String, String, Boolean>
{
	private static final String tag = "HeartBeatAsync";
	@Override
	protected Boolean doInBackground(String... params)
	{
		BackgroundManager2.getInstance().clearWaiting();

		try
		{
			Vars.commandSocket.write(Const.JBYTE);
			BackgroundManager2.getInstance().addDelayedEvent(Const.EVENT_HEARTBEAT, Const.STD_TIMEOUT);
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
			BackgroundManager2.getInstance().addEvent(Const.EVENT_RELOGIN);
			return false;
		}
	}
}
