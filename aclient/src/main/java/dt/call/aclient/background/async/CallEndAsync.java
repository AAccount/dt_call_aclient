package dt.call.aclient.background.async;

import android.os.AsyncTask;
import android.os.SystemClock;

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

	@Override
	protected Boolean doInBackground(String... params)
	{
		boolean result;
		try
		{
			//CallMain.onStop turns out to be the only place that calls this async. however, this async will actually be called twice
			// 1: user ends call --> onStop, 2: android detects the screen is going away --> onStop.
			// the second one sends a bogus ts|end|(nobody)|sessionid command because the Vars.callwith has already been cleared after the
			// first onStop. In that case, recognize the second onStop and don't do it. Don't waste resources redoing a redone socket
			if(Vars.callWith.equals(Const.nobody))
			{
				return true;
			}

			//tell the server to end the call
			String endit = Utils.currentTimeSeconds() + "|end|" + Vars.callWith.getName() + "|" + Vars.sessionid;
			Utils.logcat(Const.LOGD, tag, endit);
			Vars.commandSocket.getOutputStream().write(endit.getBytes());
			SystemClock.sleep(1000); //give some time for the command to reach the server before "pulling the plug"

			//reset the media socket to kill the media read/write threads
			//also need to kill the media socket to flush out any data still in its buffers so the next call
			//doesn't play a few seconds off the previous call, followed by the next call or worse...
			//the old call data causes a "frameshift mutation" of the new call data and produces alien morse code
			Utils.logcat(Const.LOGD, tag, "Killing old media port");
			Vars.mediaSocket.close();

			Utils.logcat(Const.LOGD, tag, "Making new media port");
			Vars.mediaSocket = Utils.mkSocket(Vars.serverAddress, Vars.mediaPort, Vars.certDump);
			String associateMedia = Utils.currentTimeSeconds() + "|" + Vars.sessionid;
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

		Utils.setNotification(R.string.state_popup_idle, R.color.material_green, Vars.go2HomePending);
		return result;
	}
}
