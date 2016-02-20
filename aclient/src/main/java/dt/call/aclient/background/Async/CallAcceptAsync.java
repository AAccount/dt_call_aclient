package dt.call.aclient.background.Async;

import android.os.AsyncTask;

import java.io.IOException;

import dt.call.aclient.Const;
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
			String acceptResp = Const.cap + Utils.getTimestamp() + "|accept|" + involved + "|" + Vars.sessionid;
			Vars.commandSocket.getOutputStream().write(acceptResp.getBytes());

			//no need to set Vars.callwith or Vars.callState because this is just accepting
			//you still need to wait for call start to switch to state = CallState.INCALL
			return true;
		}
		catch (IOException e)
		{
			Utils.logcat(Const.LOGE, tag, "ioexception: " + Utils.dumpException(e));
			return false;
		}
	}
}
