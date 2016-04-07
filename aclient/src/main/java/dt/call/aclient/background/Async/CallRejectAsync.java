package dt.call.aclient.background.Async;

import android.os.AsyncTask;

import dt.call.aclient.CallState;
import dt.call.aclient.Const;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;

/**
 * Created by Daniel on 1/31/16.
 */
public class CallRejectAsync extends AsyncTask<String, String, Boolean>
{
	private static final String tag = "CallRejectAsync";

	@Override
	protected Boolean doInBackground(String... params)
	{
		boolean result;
		try
		{
			String involved = Vars.callWith.getName();
			String rejectResp = Const.JBYTE + Utils.generateServerTimestamp() + "|reject|" + involved + "|" + Vars.sessionid;
			Vars.commandSocket.getOutputStream().write(rejectResp.getBytes());
			result = true;
		}
		catch (Exception e)
		{
			Utils.dumpException(tag, e);
			result = false;
		}

		//set the state information because a reject will not get a reply/response from the server
		//what could the server possibly say that would be meaningful...?? "no you can't reject"
		Vars.callWith = Const.nobody;
		Vars.state = CallState.NONE;
		return result;
	}
}
