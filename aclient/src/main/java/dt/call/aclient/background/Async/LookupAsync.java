package dt.call.aclient.background.Async;

import android.os.AsyncTask;

import java.io.IOException;

import dt.call.aclient.Const;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.sqlite.Contact;

/**
 * Created by Daniel on 1/23/16.
 */
public class LookupAsync extends AsyncTask<Contact, String, Object>
{
	private static final String tag = "LookupAsync";

	@Override
	protected Object doInBackground(Contact... params)
	{
		Contact lookupUser = params[0];
		String request = Const.JBYTE + Utils.generateServerTimestamp() + "|lookup|" + lookupUser.getName() + "|" + Vars.sessionid;
		Utils.logcat(Const.LOGD, tag, "Lookup request: " + request);
		try
		{
			Vars.commandSocket.getOutputStream().write(request.getBytes());
		}
		catch (Exception e)
		{
			Utils.dumpException(tag, e);
		}
		return null;
	}
}
