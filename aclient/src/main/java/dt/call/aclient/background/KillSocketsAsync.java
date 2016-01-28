package dt.call.aclient.background;

import android.os.AsyncTask;

import java.io.IOException;

import dt.call.aclient.Const;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;

/**
 * Created by Daniel on 1/25/16.
 */
public class KillSocketsAsync extends AsyncTask<String, String, String>
{
	private static final String tag = "kill sockets async";

	@Override
	protected String doInBackground(String... params)
	{
		try
		{//kill both connections to force stop threads listening/using into an exception
			if(Vars.commandSocket != null)
			{
				Vars.commandSocket.close();
			}
			if(Vars.mediaSocket != null)
			{
				Vars.mediaSocket.close();
			}
		}
		catch (IOException e)
		{
			Utils.logcat(Const.LOGE, tag, "problems closing the 2 sockets " + e.getMessage());
		}
		return null;
	}
}
