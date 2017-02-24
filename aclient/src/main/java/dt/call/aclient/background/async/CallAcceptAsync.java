package dt.call.aclient.background.async;

import android.os.AsyncTask;

import dt.call.aclient.Utils;
import dt.call.aclient.Vars;

/**
 * Created by Daniel on 1/31/16.
 */
public class CallAcceptAsync extends AsyncTask<String, String, Boolean>
{
	private static final String tag = "CallAcceptAsync";

	@Override
	protected Boolean doInBackground(String... params)
	{
		try
		{
			String involved = Vars.callWith.getName();
			String acceptResp = Utils.currentTimeSeconds() + "|accept|" + involved + "|" + Vars.sessionid;
			Vars.commandSocket.getOutputStream().write(acceptResp.getBytes());

			//no need to set Vars.callwith or Vars.callState because this is just accepting
			//you still need to wait for call start to switch to state = CallState.INCALL
			return true;
		}
		catch (Exception e)
		{
			Utils.dumpException(tag, e);
			return false;
		}
	}
}
