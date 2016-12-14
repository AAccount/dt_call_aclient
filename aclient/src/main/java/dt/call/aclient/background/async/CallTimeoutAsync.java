package dt.call.aclient.background.async;

import android.os.AsyncTask;

import dt.call.aclient.CallState;
import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;

/**
 * Created by Daniel on 2/7/16.
 * Mostly copied and pasted from CallRejectAsync
 */
public class CallTimeoutAsync extends AsyncTask<String, String, Boolean>
{
	private static final String tag = "CallTimeoutAsync";

	@Override
	protected Boolean doInBackground(String... params)
	{

		boolean result;
		try
		{
			String involved = Vars.callWith.getName();
			String timeoutResp = Utils.currentTimeSeconds() + "|timeout|" + involved + "|" + Vars.sessionid;
			Vars.commandSocket.getOutputStream().write(timeoutResp.getBytes());
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

		Utils.setNotification(R.string.state_popup_idle, R.color.material_green, Vars.go2HomePending);
		return result;
	}
}
