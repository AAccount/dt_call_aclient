package dt.call.aclient.background.async;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.cert.CertificateException;

import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.background.CmdListener;

/**
 * Created by Daniel on 1/21/16.
 *
 * Does the login on a separate thread because you're not allowed to do network on the main
 *
 * @ April 7, 2016
 * Does all the setup to make call client work: logs in, setups up command listener, setups up heartbeat.
 * Do it all in 1 place instead of in user home, initial user info, background manager.
 */
public class LoginAsync extends AsyncTask<Boolean, String, Boolean>
{
	private String uname, passwd;

	private static final String tag = "Login Async Task";
	private static final Object loginLock = new Object();
	private static boolean tryingLogin;
	private static final int TIMEOUT = 30*1000;

	/**
	 *  @param cuname User name to login with
	 * @param cpasswd Plain text password to login with
	 */
	public LoginAsync(String cuname, String cpasswd)
	{
		uname = cuname;
		passwd = cpasswd;
	}

	@Override
	protected Boolean doInBackground(Boolean... params)
	{
		try
		{
			//only handle 1 login request at a time
			synchronized(loginLock)
			{
				if(tryingLogin)
				{
					Utils.logcat(Const.LOGW, tag, "already trying a login. ignoring request");
					onPostExecute(false);
					return false;
				}
				tryingLogin = true;
			}

			//send login command
			Vars.commandSocket = Utils.mkSocket(Vars.serverAddress, Vars.commandPort, Vars.expectedCertDump);
			String login = Utils.currentTimeSeconds() + "|login|" + uname + "|" + passwd;
			Vars.commandSocket.getOutputStream().write(login.getBytes());

			//read response
			byte[] responseRaw = new byte[Const.BUFFERSIZE];
			int length = Vars.commandSocket.getInputStream().read(responseRaw);

			//on the off chance the socket crapped out right from the get go, now you'll know
			if(length < 0)
			{
				Utils.logcat(Const.LOGE, tag, "Socket closed before a response could be read");
				onPostExecute(false);
				return false;
			}

			//there's actual stuff to process, process it!
			String loginresp = new String(responseRaw, 0, length);
			Utils.logcat(Const.LOGD, tag, loginresp);

			//process login response
			String[] respContents = loginresp.split("\\|");
			if(respContents.length != 4)
			{
				Utils.logcat(Const.LOGW, tag, "Server response imporoperly formatted");
				onPostExecute(false); //not a legitimate server response
				return false;
			}
			if(!(respContents[1].equals("resp") && respContents[2].equals("login")))
			{
				Utils.logcat(Const.LOGW, tag, "Server response CONTENTS imporperly formated");
				onPostExecute(false); //server response doesn't make sense
				return false;
			}
			long ts = Long.valueOf(respContents[0]);
			if(!Utils.validTS(ts))
			{
				Utils.logcat(Const.LOGW, tag, "Server had an unacceptable timestamp");
				onPostExecute(false);
				return false;
			}
			Vars.sessionid = Long.valueOf(respContents[3]);

			//establish media socket
			Vars.mediaSocket = Utils.mkSocket(Vars.serverAddress, Vars.mediaPort, Vars.expectedCertDump);
			String associateMedia = Utils.currentTimeSeconds() + "|" + Vars.sessionid;
			Vars.mediaSocket.getOutputStream().write(associateMedia.getBytes());

			Intent cmdListenerIntent = new Intent(Vars.applicationContext, CmdListener.class);
			Vars.applicationContext.startService(cmdListenerIntent);

			Utils.initAlarmVars(); //double check it's not null before usage
			AlarmManager manager = (AlarmManager) Vars.applicationContext.getSystemService(Context.ALARM_SERVICE);
			manager.cancel(Vars.pendingHeartbeat);
			Utils.setExactWakeup(Const.HEARTBEAT_FREQ, Vars.pendingHeartbeat);

			onPostExecute(true);
			return true;
		}
		catch (CertificateException c)
		{
			Utils.logcat(Const.LOGE, tag, "server certificate didn't match the expected");
			onPostExecute(false);
			return false;
		}
		catch (Exception i)
		{
			Utils.dumpException(tag, i);
			onPostExecute(false);
			return false;
		}
	}

	protected void onPostExecute(boolean result)
	{
		//broadcast to background manager first. that way it always knows what the current state of your login and if
		//it needs to try again. background will rebroadcast to the ui. if no ui is listening no harm.
		Intent loginResult = new Intent(Const.BROADCAST_LOGIN_BG);
		loginResult.putExtra(Const.BROADCAST_LOGIN_RESULT, result);
		Vars.applicationContext.sendBroadcast(loginResult);

		//update the persistent notification with the login results
		if(result)
		{
			Utils.setNotification(R.string.state_popup_idle, R.color.material_green, Vars.go2HomePending);
		}
		else
		{
			Utils.setNotification(R.string.state_popup_offline, R.color.material_grey, Vars.go2HomePending);
		}

		synchronized (loginLock)
		{
			tryingLogin = false;
		}
	}
}
