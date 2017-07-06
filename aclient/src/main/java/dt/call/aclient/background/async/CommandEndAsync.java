package dt.call.aclient.background.async;

import android.os.AsyncTask;

import java.io.IOException;

import dt.call.aclient.CallState;
import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;

/**
 * Created by Daniel on 1/30/16.
 */
public class CommandEndAsync extends AsyncTask<String, String, Boolean>
{
	private static final String tag = "CommandEndAsync";

	public void doInForeground()
	{//to avoid copying and pasting, allow the "main attraction" of this class to be run outside of the async framework
		doInBackground();
	}

	@Override
	protected Boolean doInBackground(String... params)
	{
		boolean result;
		try
		{
			//CallMain.onStop turns out to be the only place that calls this async. however, this async will actually be called twice
			// 1: user ends call --> onStop, 2: android detects the screen is going away --> onStop.
			// the second one sends a bogus ts|end|(nobody)|sessionKey command because the Vars.callwith has already been cleared after the
			// first onStop. In that case, recognize the second onStop and don't do it. Don't waste resources redoing a redone socket
			if(Vars.callWith.equals(Const.nobody))
			{
				return true;
			}
			String end = Utils.currentTimeSeconds() + "|end|" + Vars.callWith.getName() + "|" + Vars.sessionKey;
			Vars.commandSocket.getOutputStream().write(end.getBytes());
			result = true;
		}
		catch (IOException i)
		{
			Utils.dumpException(tag, i);
			result = false;
		}

		//clean up the internal variables
		Vars.state = CallState.NONE;
		Vars.callWith = Const.nobody;

		Utils.setNotification(R.string.state_popup_idle, R.color.material_green, Vars.go2HomePending);
		return result;
	}
}
