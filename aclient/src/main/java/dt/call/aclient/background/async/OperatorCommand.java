package dt.call.aclient.background.async;

import android.os.AsyncTask;

import dt.call.aclient.CallState;
import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;

/**
 * Created by Daniel on 1/31/16.
 */
public class OperatorCommand extends AsyncTask<Integer, String, Boolean>
{
	public static final int ACCEPT = 1;
	public static final int CALL = 2;
	public static final int END = 3;

	private static final String tag = "OperatorCommand";

	public void doInForeground(int operation)
	{//to avoid copying and pasting, allow the "main attraction" of this class to be run outside of the async framework
		doInBackground(operation);
	}

	@Override
	protected Boolean doInBackground(Integer... params)
	{
		final int intCommand = params[0];
		String command = "";
		switch(intCommand)
		{
			case ACCEPT:
				command = "|accept|";
				break;
			case CALL:
				command = "|call|";
				break;
			case END:
				command = "|end|";
				break;
		}
		final String outgoingBase = Utils.currentTimeSeconds() + command + Vars.callWith + "|";
		Utils.logcat(Const.LOGD, tag, outgoingBase+"...");
		try
		{
			final String outgoing = outgoingBase + Vars.sessionKey;
			Vars.commandSocket.write(outgoing);
		}
		catch (Exception e)
		{
			Utils.dumpException(tag, e);
			cleanup();
		}

		if(intCommand == END)
		{
			cleanup();
			Utils.setNotification(R.string.state_popup_idle, R.color.material_green, Vars.go2HomePending);
		}
		return true;
	}

	private void cleanup()
	{
		Vars.state = CallState.NONE;
		Vars.callWith = Const.nobody;
	}
}
