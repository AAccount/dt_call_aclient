package dt.call.aclient.background.async;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import java.security.cert.CertificateException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.crypto.Cipher;

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
	private static final String tag = "Login Async Task";
	private static final Object loginLock = new Object();
	private static boolean tryingLogin;

	@Override
	protected Boolean doInBackground(Boolean... params)
	{
		try
		{
			Utils.initAlarmVars(); //double check it's not null before usage
			AlarmManager manager = (AlarmManager) Vars.applicationContext.getSystemService(Context.ALARM_SERVICE);
			manager.cancel(Vars.pendingRetries);
			manager.cancel(Vars.pendingRetries2ndary);

			//only handle 1 login request at a time
			synchronized(loginLock)
			{
				if(tryingLogin)
				{
					Utils.logcat(Const.LOGW, tag, "already trying a login. ignoring request");
					return false;
				}
				tryingLogin = true;
			}

			//request login challenge
			Vars.commandSocket = Utils.mkSocket(Vars.serverAddress, Vars.commandPort, Vars.certDump);
			String login = Utils.currentTimeSeconds() + "|login1|" + Vars.uname;
			Vars.commandSocket.getOutputStream().write(login.getBytes());

			//read in login challenge
			byte[] responseRaw = new byte[Const.BUFFERSIZE];
			int length = Vars.commandSocket.getInputStream().read(responseRaw);

			//on the off chance the socket crapped out right from the get go, now you'll know
			if(length < 0)
			{
				Vars.commandSocket.close();
				Vars.commandSocket = null;
				Utils.logcat(Const.LOGE, tag, "Socket closed before a response could be read");
				onPostExecute(false);
				return false;
			}

			//there's actual stuff to process, process it!
			String loginChallenge = new String(responseRaw, 0, length);

			//process login challenge response
			String[] loginChallengeContents = loginChallenge.split("\\|");
			if(loginChallengeContents.length != 4)
			{
				Utils.logcat(Const.LOGW, tag, "login1 response imporoperly formatted");
				onPostExecute(false); //not a legitimate server response
				return false;
			}
			if(!(loginChallengeContents[1].equals("resp") && loginChallengeContents[2].equals("login1")))
			{
				Utils.logcat(Const.LOGW, tag, "login1 response CONTENTS improperly formated");
				onPostExecute(false); //server response doesn't make sense
				return false;
			}
			long ts = Long.valueOf(loginChallengeContents[0]);
			if(!Utils.validTS(ts))
			{
				Utils.logcat(Const.LOGW, tag, "login1 had an unacceptable timestamp");
				onPostExecute(false);
				return false;
			}

			//get the challenge
			String challenge = loginChallengeContents[3];
			byte[] challengeNumbers = destringify(challenge);

			//answer the challenge
			Cipher rsa = Cipher.getInstance("RSA/NONE/PKCS1Padding");
			rsa.init(Cipher.DECRYPT_MODE, Vars.privateKey);
			byte[] decrypted = rsa.doFinal(challengeNumbers);
			String challengeDec = new String(decrypted, "UTF8");
			String loginChallengeResponse = Utils.currentTimeSeconds() + "|login2|" + Vars.uname + "|" + challengeDec;
			Vars.commandSocket.getOutputStream().write(loginChallengeResponse.getBytes());

			//see if the server liked the challenge response
			byte[] answerResponseBuffer = new byte[Const.BUFFERSIZE];
			length = Vars.commandSocket.getInputStream().read(answerResponseBuffer);
			String answerResponse = new String(answerResponseBuffer, 0, length);

			//check reaction response
			String[] answerResponseContents = answerResponse.split("\\|");
			if(answerResponseContents.length != 4)
			{
				Utils.logcat(Const.LOGW, tag, "login2 response imporoperly formatted");
				return false; //not a legitimate server response
			}
			if(!(answerResponseContents[1].equals("resp") && answerResponseContents[2].equals("login2")))
			{
				Utils.logcat(Const.LOGW, tag, "login2 response CONTENTS imporperly formateed");
				return false; //server response doesn't make sense
			}
			ts = Long.valueOf(answerResponseContents[0]);
			if(!Utils.validTS(ts))
			{
				Utils.logcat(Const.LOGW, tag, "login2 had an unacceptable timestamp");
				onPostExecute(false);
				return false;
			}

			Vars.sessionid = Long.valueOf(answerResponseContents[3]);

			//establish media socket
			Vars.mediaSocket = Utils.mkSocket(Vars.serverAddress, Vars.mediaPort, Vars.certDump);
			String associateMedia = Utils.currentTimeSeconds() + "|" + Vars.sessionid;
			Vars.mediaSocket.getOutputStream().write(associateMedia.getBytes());

			Intent cmdListenerIntent = new Intent(Vars.applicationContext, CmdListener.class);
			Vars.applicationContext.startService(cmdListenerIntent);

			manager.cancel(Vars.pendingHeartbeat);
			manager.cancel(Vars.pendingHeartbeat2ndary);
			Utils.setExactWakeup(Vars.pendingHeartbeat, Vars.pendingHeartbeat2ndary);

			onPostExecute(true);
			return true;
		}
		catch (CertificateException c)
		{
			Utils.killSockets();
			Utils.logcat(Const.LOGE, tag, "server certificate didn't match the expected");
			onPostExecute(false);
			return false;
		}
		catch (Exception i)
		{
			Utils.killSockets();
			Utils.dumpException(tag, i);
			onPostExecute(false);
			return false;
		}
	}

	protected void onPostExecute(boolean result)
	{
		//broadcast to background manager first. that way it always knows what the current state of your login and if
		//it needs to try again. background will rebroadcast to the ui. if no ui is listening no harm.
		Intent loginResult = new Intent(Const.BROADCAST_LOGIN);
		loginResult.putExtra(Const.BROADCAST_LOGIN_RESULT, result);
		Vars.applicationContext.sendBroadcast(loginResult);

		//update the persistent notification with the login results
		SimpleDateFormat ts = new SimpleDateFormat("HH:mm:ss.SSSS",Locale.US);
		Utils.logcat(Const.LOGD, tag, "Result of login: " + result + " @" + ts.format(new Date()));
		if(result)
		{
			Utils.setNotification(R.string.state_popup_idle, R.color.material_green, Vars.go2HomePending);
		}
		else
		{
			Utils.setNotification(R.string.state_popup_offline, R.color.material_grey, Vars.go2HomePending);
			Utils.setExactWakeup(Vars.pendingRetries, Vars.pendingRetries2ndary);
			//background manager will check if there is internet or not when the retry kicks in and will act accordingly
		}

		synchronized (loginLock)
		{
			tryingLogin = false;
		}
	}

	//turn a string of #s into actual #s assuming the string is a bunch of
	//	3 digit #s glued to each other. also turned unsigned #s into signed #s
	private byte[] destringify(String numbers)
	{
		byte[] result = new byte[numbers.length()/3];
		for(int i=0; i<numbers.length(); i=i+3)
		{
			String digit = numbers.substring(i, i+3);
			result[i/3] = (byte)(0xff & Integer.valueOf(digit));
		}
		return result;
	}
}
