package dt.call.aclient.background.async;

import android.os.AsyncTask;

import dt.call.aclient.Utils;
import dt.call.aclient.Vars;

/**
 * Created by Daniel on 1/31/16.
 */
public class CommandAcceptAsync extends AsyncTask<String, String, Boolean>
{
	private static final String tag = "CommandAcceptAsync";

	@Override
	protected Boolean doInBackground(String... params)
	{
		try
		{
			String involved = Vars.callWith;
			String acceptResp = Utils.currentTimeSeconds() + "|accept|" + involved + "|" + Vars.sessionKey;
			byte[] acceptRespEnc = Utils.sodiumSymEncrypt(acceptResp.getBytes(), Vars.tcpKey);
			Vars.commandSocket.getOutputStream().write(acceptRespEnc);

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
