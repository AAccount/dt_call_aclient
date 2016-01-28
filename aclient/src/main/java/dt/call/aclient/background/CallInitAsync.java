package dt.call.aclient.background;

import android.os.AsyncTask;

import java.io.IOException;

import dt.call.aclient.CallState;
import dt.call.aclient.Const;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;

/**
 * Created by Daniel on 1/24/16.
 */
public class CallInitAsync extends AsyncTask<String, String, Boolean>
{
	private static final String tag = "CallInitAsync";
	private String who;

	public CallInitAsync(String cwho)
	{
		who = cwho;
	}

	@Override
	protected Boolean doInBackground(String... params)
	{
		String request = Const.cap + Utils.getTimestamp() + "|call|" + who + "|" + Vars.sessionid;
		Utils.logcat(Const.LOGD, tag, "Call request: " + request);
		try
		{
			Vars.commandSocket.getOutputStream().write(request.getBytes());
			Vars.callWith = who;
			Vars.state = CallState.INIT;
			return true;
		}
		catch (IOException e)
		{
			Utils.logcat(Const.LOGE, tag, e.getStackTrace().toString());
			return false;
		}
	}
}
