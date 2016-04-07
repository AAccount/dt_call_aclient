package dt.call.aclient.background.Async;

import android.content.Context;
import android.os.AsyncTask;

import java.security.cert.CertificateException;

import dt.call.aclient.CallState;
import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;

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
			String endit = Const.JBYTE + Utils.generateServerTimestamp() + "|end|" + Vars.callWith.getName() + "|" + Vars.sessionid;
			Utils.logcat(Const.LOGD, tag, endit);
			Vars.commandSocket.getOutputStream().write(endit.getBytes());

			//reset the media socket to kill the media read/write threads
			Utils.logcat(Const.LOGD, tag, "Killing old media port");
			Vars.mediaSocket.close();

			Utils.logcat(Const.LOGD, tag, "Making new media port");
			Vars.mediaSocket = Utils.mkSocket(Vars.serverAddress, Vars.mediaPort, Vars.expectedCertDump);
			String associateMedia = Const.JBYTE + Utils.generateServerTimestamp() + "|" + Vars.sessionid;
			Utils.logcat(Const.LOGD, tag, associateMedia);
			Vars.mediaSocket.getOutputStream().write(associateMedia.getBytes());
			result = true;
		}
		catch (CertificateException c)
		{
			Utils.logcat(Const.LOGE, tag, "Tring to reestablish media port but somehow the certificate is wrong");
			result = false;
		}
		catch (Exception i)
		{
			Utils.dumpException(tag, i);
			result = false;
		}

		//clean up the internal variables
		Vars.state = CallState.NONE;
		Vars.callWith = Const.nobody;

		Utils.updateNotification(context.getString(R.string.state_popup_idle), Vars.go2HomePending);
		return result;
	}
}
