package dt.call.aclient.background.Async;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.cert.CertificateException;

import dt.call.aclient.Const;
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
	private Context context;
	private boolean asyncMode = false;
	private static final String tag = "Login Async Task";

	private static final Object loginLock = new Object();
	private static boolean tryingLogin;

	/**
	 *
	 * @param cuname User name to login with
	 * @param cpasswd Plain text password to login with
	 * @param ccontext Annoying android context
	 * @param casync Whether the result should also be sent by the broadcast intent Const.BROADCAST_LOGIN
	 */
	public LoginAsync(String cuname, String cpasswd, Context ccontext, boolean casync)
	{
		uname = cuname;
		passwd = cpasswd;
		context = ccontext;
		asyncMode = casync;
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
					return false;
				}
				tryingLogin = true;
			}

			if(Vars.cmdListenerRunning)
			{
				try
				{
					Vars.commandSocket.close();
					Vars.commandSocket = null;
					Vars.mediaSocket.close();
					Vars.mediaSocket = null;
				}
				catch (Exception e)
				{
					Utils.logcat(Const.LOGE, tag, "problems closing open sockets before attempting login: ");
					Utils.dumpException(tag, e);
				}
				Vars.cmdListenerRunning = false;
			}

			//http://stackoverflow.com/a/34228756
			//check if server is available first before committing to anything
			//	otherwise this proccess will stall. host not available trips timeout exception
			Socket diag = new Socket();
			diag.connect(new InetSocketAddress(Vars.serverAddress, Vars.commandPort), 2000);

			//send login command
			Vars.commandSocket = Utils.mkSocket(Vars.serverAddress, Vars.commandPort, Vars.expectedCertDump);
			String login = Const.JBYTE + Utils.generateServerTimestamp() + "|login|" + uname + "|" + passwd;
			Vars.commandSocket.getOutputStream().write(login.getBytes());

			//read response
			InputStream cmdin = Vars.commandSocket.getInputStream();
			BufferedReader cmdTxtIn = new BufferedReader(new InputStreamReader(cmdin));
			String loginresp = cmdTxtIn.readLine();
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
				Utils.logcat(Const.LOGW, tag, "Server response CONTENTS imporperly formateed");
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
			Utils.logcat(Const.LOGD, tag, "Established command socket with sessionid: " + Vars.sessionid);

			//establish media socket
			Vars.mediaSocket = Utils.mkSocket(Vars.serverAddress, Vars.mediaPort, Vars.expectedCertDump);
			String associateMedia = Const.JBYTE + Utils.generateServerTimestamp() + "|" + Vars.sessionid;
			Vars.mediaSocket.getOutputStream().write(associateMedia.getBytes());
			Vars.mediaSocket.getOutputStream().write("testing testing 1 2 3".getBytes()); //sometimes java socket craps out

			Intent cmdListenerIntent = new Intent(context, CmdListener.class);
			context.startService(cmdListenerIntent);
			Vars.cmdListenerRunning = true;

			Intent startHeartbeat = new Intent(Const.BROADCAST_BK_HEARTBEAT);
			startHeartbeat.putExtra(Const.BROADCAST_BK_HEARTBEAT_DOIT, true);
			context.sendBroadcast(startHeartbeat);

			onPostExecute(true);
			return true;
		}
		catch (CertificateException c)
		{
			Utils.logcat(Const.LOGD, tag, "server certificate didn't match the expected");
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
		if(asyncMode)
		{
			Intent loginResult = new Intent(Const.BROADCAST_LOGIN);
			loginResult.putExtra(Const.BROADCAST_LOGIN_RESULT, result);
			context.sendBroadcast(loginResult);
		}
		synchronized (loginLock)
		{
			tryingLogin = false;
		}
	}
}
