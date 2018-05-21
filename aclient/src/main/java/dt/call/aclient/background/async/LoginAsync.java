package dt.call.aclient.background.async;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Base64;

import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

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

			//request login challenge
			Vars.commandSocket = mkSocket(Vars.serverAddress, Vars.commandPort, Vars.certDump);
			String login = Utils.currentTimeSeconds() + "|login1|" + Vars.uname;
			Vars.commandSocket.getOutputStream().write(login.getBytes());

			//read in login challenge
			byte[] responseRaw = new byte[Const.SIZE_COMMAND];
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
			byte[] challengeBytes = Utils.destringify(challenge, false);

			//answer the challenge
			byte[] decrypted = Utils.sodiumAsymDecrypt(challengeBytes, Vars.serverPublicSodium);
			if(decrypted == null)
			{
				Utils.logcat(Const.LOGW, tag, "sodium asymmetric decryption failed");
				onPostExecute(false);
				return false;
			}
			String challengeDec = new String(decrypted, "UTF8");
			String loginChallengeResponse = Utils.currentTimeSeconds() + "|login2|" + Vars.uname + "|" + challengeDec;
			Vars.commandSocket.getOutputStream().write(loginChallengeResponse.getBytes());

			//see if the server liked the challenge response
			byte[] answerResponseBuffer = new byte[Const.SIZE_COMMAND];
			length = Vars.commandSocket.getInputStream().read(answerResponseBuffer);
			String answerResponse = new String(answerResponseBuffer, 0, length);

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
			noNotificationOnFail = false;
			Utils.setNotification(R.string.state_popup_idle, R.color.material_green, Vars.go2HomePending);
		}
		else if(!noNotificationOnFail) //don't show the notification for initial login fails
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

	private SSLSocket mkSocket(String host, int port, final String expected64) throws CertificateException
	{
		SSLSocket socket = null;
		TrustManager[] trustOnlyServerCert = new TrustManager[]
				{new X509TrustManager()
				{
					@Override
					public void checkClientTrusted(X509Certificate[] chain, String alg)
					{
						//this IS the client. it's not going to be getting clients itself. nothing to see here
					}

					@Override
					public void checkServerTrusted(X509Certificate[] chain, String alg) throws CertificateException
					{
						//Get the certificate encoded as ascii text. Normally a certificate can be opened
						//	by a text editor anyways.
						byte[] serverCertDump = chain[0].getEncoded();
						String server64 = Base64.encodeToString(serverCertDump, Const.BASE64_Flags);

						//Trim the expected and presented server ceritificate ascii representations to prevent false
						//	positive of not matching because of randomly appended new lines or tabs or both.
						server64 = server64.trim();
						String expected64Trimmed = expected64.trim();
						if(!expected64Trimmed.equals(server64))
						{
							throw new CertificateException("Server certificate does not match expected one.");
						}

					}

					@Override
					public X509Certificate[] getAcceptedIssuers()
					{
						return null;
					}

				}
				};
		try
		{
			SSLContext context;
			context = SSLContext.getInstance("TLSv1.2");
			context.init(new KeyManager[0], trustOnlyServerCert, new SecureRandom());
			SSLSocketFactory mkssl = context.getSocketFactory();
			socket = (SSLSocket)mkssl.createSocket(host, port);
			socket.setTcpNoDelay(true); //for heartbeat to get instant ack
			socket.startHandshake();
			return socket;
		}
		catch (Exception e)
		{
			try
			{
				socket.close();
			}
			catch (Exception e1)
			{
				Utils.dumpException(tag, e1); //although there's nothing that can really be done at this point
			}
			Utils.dumpException(tag, e);
			return null;
		}
	}
}
