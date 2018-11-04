package dt.call.aclient.background.async;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.background.CmdListener;
import dt.call.aclient.sodium.SodiumSocket;
import dt.call.aclient.sodium.SodiumUtils;

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
	public static boolean noNotificationOnFail = false;
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

			//setup tcp connection
			Vars.commandSocket = new SodiumSocket(Vars.serverAddress, Vars.commandPort, Vars.serverPublicSodium);

			//request login challenge
			String login = Utils.currentTimeSeconds() + "|login1|" + Vars.uname;
			Vars.commandSocket.write(login);

			//read in login challenge
			String loginChallenge = Vars.commandSocket.readString(Const.SIZE_COMMAND);

			//process login challenge response
			String[] loginChallengeContents = loginChallenge.split("\\|");
			if(loginChallengeContents.length != Const.LOGIN_MAX_SEGMENTS)
			{
				Utils.logcat(Const.LOGW, tag, "login1 response imporoperly formatted");
				onPostExecute(false); //not a legitimate server response
				return false;
			}
			if(!loginChallengeContents[1].equals("login1resp"))
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
			String challenge = loginChallengeContents[2];
			byte[] challengeBytes = Utils.destringify(challenge);

			//answer the challenge
			byte[] decrypted = SodiumUtils.asymmetricDecrypt(challengeBytes, Vars.serverPublicSodium, Vars.selfPrivateSodium);
			if(decrypted == null)
			{
				Utils.logcat(Const.LOGW, tag, "sodium asymmetric decryption failed");
				onPostExecute(false);
				return false;
			}
			String challengeDec = new String(decrypted, "UTF8");
			String loginChallengeResponse = Utils.currentTimeSeconds() + "|login2|" + Vars.uname + "|" + challengeDec;
			Vars.commandSocket.write(loginChallengeResponse);

			//see if the server liked the challenge response
			String answerResponse = Vars.commandSocket.readString(Const.SIZE_COMMAND);

			//check reaction response
			String[] answerResponseContents = answerResponse.split("\\|");
			if(answerResponseContents.length != Const.LOGIN_MAX_SEGMENTS)
			{
				Utils.logcat(Const.LOGW, tag, "login2 response imporoperly formatted");
				onPostExecute(false); //not a legitimate server response
				return false; //not a legitimate server response
			}
			if(!answerResponseContents[1].equals("login2resp"))
			{
				Utils.logcat(Const.LOGW, tag, "login2 response CONTENTS imporperly formateed");
				onPostExecute(false); //not a legitimate server response
				return false; //server response doesn't make sense
			}
			ts = Long.valueOf(answerResponseContents[0]);
			if(!Utils.validTS(ts))
			{
				Utils.logcat(Const.LOGW, tag, "login2 had an unacceptable timestamp");
				onPostExecute(false);
				return false;
			}

			Vars.sessionKey = answerResponseContents[2];

			//must set the notification right away because the stupid foreground service workaround needs it ASAP
			noNotificationOnFail = false;
			Utils.setNotification(R.string.state_popup_idle, R.color.material_green, Vars.go2HomePending);

			Intent cmdListenerIntent = new Intent(Vars.applicationContext, CmdListener.class);
			if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
			{
				Vars.applicationContext.startForegroundService(cmdListenerIntent);
			}
			else
			{
				Vars.applicationContext.startService(cmdListenerIntent);
			}

			manager.cancel(Vars.pendingHeartbeat);
			manager.cancel(Vars.pendingHeartbeat2ndary);
			Utils.setExactWakeup(Vars.pendingHeartbeat, Vars.pendingHeartbeat2ndary);

			onPostExecute(true);
			return true;
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
		if(!result && !noNotificationOnFail) //don't show the notification for initial login fails
		{
			Utils.setNotification(R.string.state_popup_offline, R.color.material_grey, Vars.go2HomePending);
			Utils.setExactWakeup(Vars.pendingRetries, Vars.pendingRetries2ndary);
			//background manager will check if there is internet or not when the retry kicks in and will act accordingly

			noNotificationOnFail = false; //reset
		}

		synchronized (loginLock)
		{
			tryingLogin = false;
		}
	}
}
