package dt.call.aclient.background.Async;

import android.os.AsyncTask;

import java.io.IOException;

import dt.call.aclient.CallState;
import dt.call.aclient.Const;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.sqlite.Contact;

/**
 * Created by Daniel on 1/24/16.
 */
public class CallInitAsync extends AsyncTask<String, String, Boolean>
{
	private static final String tag = "CallInitAsync";
	private Contact who;

	public CallInitAsync(Contact cwho)
	{
		who = cwho;
	}

	@Override
	protected Boolean doInBackground(String... params)
	{
		String request = Const.JBYTE + Utils.generateServerTimestamp() + "|call|" + who.getName() + "|" + Vars.sessionid;
		Utils.logcat(Const.LOGD, tag, "Call request: " + request);
		try
		{
			Vars.commandSocket.getOutputStream().write(request.getBytes());
			Vars.callWith = who;
			return true;
		}
		catch (Exception e)
		{
			Utils.dumpException(tag, e);
			Vars.callWith = Const.nobody;
			Vars.state = CallState.NONE;
			return false;
		}
	}
}
