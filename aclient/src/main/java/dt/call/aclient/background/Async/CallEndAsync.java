package dt.call.aclient.background.Async;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import java.io.IOException;
import java.security.cert.CertificateException;

import dt.call.aclient.CallState;
import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.screens.UserHome;

/**
 * Created by Daniel on 1/30/16.
 */
public class CallEndAsync extends AsyncTask<String, String, Boolean>
{
	private static final String tag = "CallEndAsync";
	private Context context;

	public CallEndAsync(Context ccontext)
	{
		context = ccontext;
	}

	@Override
	protected Boolean doInBackground(String... params)
	{
		boolean result;
		try
		{
			//tell the server to end the call
			String endit = Const.cap + Utils.getTimestamp() + "|end|" + Vars.callWith.getName() + "|" + Vars.sessionid;
			Vars.commandSocket.getOutputStream().write(endit.getBytes());

			//reset the media socket to kill the media read/write threads
			Vars.mediaSocket = Utils.mkSocket(Vars.serverAddress, Vars.mediaPort, Vars.expectedCertDump);
			String associateMedia = Const.cap + Utils.getTimestamp() + "|" + Vars.sessionid;
			Vars.mediaSocket.getOutputStream().write(associateMedia.getBytes());
			result = true;
		}
		catch (CertificateException c)
		{
			Utils.logcat(Const.LOGE, tag, "Tring to reestablish media port but somehow the certificate is wrong");
			result = false;
		}
		catch (IOException i)
		{
			Utils.logcat(Const.LOGE, tag, "IO exception restarting media socket");
			result = false;
		}

		//clean up the internal variables
		Vars.state = CallState.NONE;
		Vars.callWith = Const.nobody;

		Utils.updateNotification(context.getString(R.string.state_popup_idle), Vars.go2HomePending);
		return result;
	}
}
