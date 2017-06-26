package dt.call.aclient;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.support.v4.content.ContextCompat;
import android.util.Base64;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.KeyFactory;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import dt.call.aclient.background.BackgroundManager;
import dt.call.aclient.screens.CallIncoming;
import dt.call.aclient.screens.CallMain;
import dt.call.aclient.screens.UserHome;
import dt.call.aclient.sqlite.DBLog;
import dt.call.aclient.sqlite.SQLiteDb;

/**
 * Created by Daniel on 1/18/16.
 *
 * Static utility functions
 */
public class Utils
{
	private static final String tag = "Utils";

	//Linux time(NULL) system call automatically calculates GMT-0/UTC time
	//so does currentTimeMillis. No need to do timezone conversions
	public static long currentTimeSeconds()
	{
		return System.currentTimeMillis()/1000L;
	}

	public static boolean validTS(long ts)
	{
		long now = currentTimeSeconds();
		long fivemins = 60*5;
		long diff = now-ts;

		return Math.abs(diff) <= fivemins;
	}

	public static SSLSocket mkSocket(String host, int port, final String expected64) throws CertificateException
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
			socket.setEnabledProtocols(new String[]{"TLSv1.2"});
			socket.setEnabledCipherSuites(new String[]{"TLS_DHE_RSA_WITH_AES_256_GCM_SHA384"});
			socket.startHandshake();
			if(port == Vars.mediaPort)
			{
				socket.setTrafficClass(0x2E); //Expedited forwarding (EF)
			}
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
				dumpException(tag, e1); //although there's nothing that can really be done at this point
			}
			dumpException(tag, e);
			return null;
		}
	}

	public static void logcat(int type, String tag, String message)
	{
		if(Vars.SHOUDLOG)
		{
			if(type == Const.LOGD)
			{
				Log.d(tag, message);
			}
			else if (type == Const.LOGE)
			{
				Log.e(tag, message);
			}
			else if (type == Const.LOGW)
			{
				Log.w(tag, message);
			}
			SQLiteDb sqLiteDb = SQLiteDb.getInstance(Vars.applicationContext);
			sqLiteDb.insertLog(new DBLog(tag, message));
		}
	}

	//For showing a simple popup where the only option is OK
	//Usually for indicating some kind of error (user or server)
	public static void showOk(Context context, String message)
	{
		AlertDialog.Builder mkdialog = new AlertDialog.Builder(context);
		mkdialog.setMessage(message)
				.setPositiveButton(R.string.alert_ok, new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(DialogInterface dialog, int which)
					{
						dialog.cancel();
					}
				});
		AlertDialog showOkAlert = mkdialog.create();
		showOkAlert.show();
	}

	//sets the ongoing notification message and color. also initializes any notification related variables if they're not setup
	public static void setNotification(int stringRes, int colorRes, PendingIntent go2)
	{
		//first make sure all the pending intents are useable
		if(Vars.go2HomePending == null)
		{
			Intent go2Home = new Intent(Vars.applicationContext, UserHome.class);
			Vars.go2HomePending = PendingIntent.getActivity(Vars.applicationContext, 0, go2Home, PendingIntent.FLAG_UPDATE_CURRENT);
		}
		if(Vars.go2CallMainPending == null)
		{
			Intent go2CallMain = new Intent(Vars.applicationContext, CallMain.class);
			Vars.go2CallMainPending = PendingIntent.getActivity(Vars.applicationContext, 0, go2CallMain, PendingIntent.FLAG_UPDATE_CURRENT);
		}
		if(Vars.go2CallIncomingPending == null)
		{
			Intent go2CallIncoming = new Intent(Vars.applicationContext, CallIncoming.class);
			Vars.go2CallIncomingPending = PendingIntent.getActivity(Vars.applicationContext, 0, go2CallIncoming, PendingIntent.FLAG_UPDATE_CURRENT);
		}

		//if the ongoing notification is not setup, then set it up first
		if(Vars.stateNotificationBuilder == null || Vars.notificationManager == null)
		{
			Vars.stateNotificationBuilder = new Notification.Builder(Vars.applicationContext)
					.setContentTitle(Vars.applicationContext.getString(R.string.app_name))
					.setContentText(Vars.applicationContext.getString(stringRes))
					.setSmallIcon(R.drawable.ic_vpn_lock_white_48dp)
					.setContentIntent(Vars.go2HomePending)
					.setOngoing(true);
			if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
			{//only apply color if android >= 5.0 unfortunately
				Vars.stateNotificationBuilder.setColor(ContextCompat.getColor(Vars.applicationContext, colorRes));
			}
			Vars.notificationManager = (NotificationManager) Vars.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
			Vars.notificationManager.notify(Const.stateNotificationId, Vars.stateNotificationBuilder.build());
		}
		else
		{
			Vars.stateNotificationBuilder
					.setContentText(Vars.applicationContext.getString(stringRes))
					.setContentIntent(go2);
			if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
			{
				Vars.stateNotificationBuilder.setColor(ContextCompat.getColor(Vars.applicationContext, colorRes));
			}
			Vars.notificationManager.notify(Const.stateNotificationId, Vars.stateNotificationBuilder.build());
		}
	}

	//https://stackoverflow.com/questions/1149703/how-can-i-convert-a-stack-trace-to-a-string
	public static void dumpException(String tag, Exception e)
	{
		if(Vars.SHOUDLOG) //don't waste time dumping the exception if it isn't wanted in the first place
		{
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			String dump = sw.toString();

			logcat(Const.LOGE, tag, dump);
		}
	}

	public static void initAlarmVars()
	{
		//setup the alarm intents and pending intents
		if(Vars.retries == null || Vars.pendingRetries == null || Vars.heartbeat == null || Vars.pendingHeartbeat == null
				||Vars.pendingHeartbeat2ndary == null || Vars.pendingRetries2ndary == null)
		{
			Vars.retries = new Intent(Vars.applicationContext, BackgroundManager.class);
			Vars.retries.setAction(Const.BROADCAST_RELOGIN);
			Vars.pendingRetries = PendingIntent.getBroadcast(Vars.applicationContext, Const.ALARM_RETRY_ID, Vars.retries, PendingIntent.FLAG_UPDATE_CURRENT);
			Vars.pendingRetries2ndary = PendingIntent.getBroadcast(Vars.applicationContext, Const.ALARM_RETRY_ID, Vars.retries, PendingIntent.FLAG_UPDATE_CURRENT);

			Vars.heartbeat = new Intent(Vars.applicationContext, BackgroundManager.class);
			Vars.heartbeat.setAction(Const.ALARM_ACTION_HEARTBEAT);
			Vars.pendingHeartbeat = PendingIntent.getBroadcast(Vars.applicationContext, Const.ALARM_HEARTBEAT_ID, Vars.heartbeat, PendingIntent.FLAG_UPDATE_CURRENT);
			Vars.pendingHeartbeat2ndary = PendingIntent.getBroadcast(Vars.applicationContext, Const.ALARM_HEARTBEAT_ID, Vars.heartbeat, PendingIntent.FLAG_UPDATE_CURRENT);
		}
	}

	public static void quit()
	{
		//get rid of the status notification if it's running
		if(Vars.notificationManager != null)
		{
			Vars.notificationManager.cancelAll();
		}

		//Kill alarms
		if(Vars.pendingHeartbeat != null && Vars.pendingRetries != null)
		{
			AlarmManager manager = (AlarmManager) Vars.applicationContext.getSystemService(Context.ALARM_SERVICE);
			manager.cancel(Vars.pendingHeartbeat);
			manager.cancel(Vars.pendingRetries);
		}

		//prevent background manager from restarting command listener when sockets kill async is called
		ComponentName backgroundManager = new ComponentName(Vars.applicationContext, BackgroundManager.class);
		Vars.applicationContext.getPackageManager().setComponentEnabledSetting(backgroundManager, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);

		//get rid of the sockets
		Thread killSockets = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				killSockets();
			}
		});
		killSockets.setName("Utils.quit.killSockets");
		killSockets.start();

		//https://stackoverflow.com/questions/3226495/android-exit-application-code
		//basically a way to get out of aclient
		Intent intent = new Intent(Intent.ACTION_MAIN);
		intent.addCategory(Intent.CATEGORY_HOME);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		Vars.applicationContext.startActivity(intent);
		Process.killProcess(Process.myPid()); //using System.exit(0) produces weird crashes when restarting from java socket stupidities
											//https://stackoverflow.com/questions/6609414/how-to-programatically-restart-android-app
	}

	public static boolean hasInternet()
	{
		//double check there is internet before restarting command listener
		ConnectivityManager connectivityManager = (ConnectivityManager)Vars.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
		boolean result = (networkInfo != null) && (networkInfo.getDetailedState() == NetworkInfo.DetailedState.CONNECTED);

		//print the network info or null if there isn't any for subway debugging
		if(!result)
		{
			if (networkInfo != null)
			{
				logcat(Const.LOGD, tag, "network info: " + networkInfo.toString());
			}
			else
			{
				logcat(Const.LOGD, tag, "networkInfo is NULL 0x0");
			}
		}
		return result;
	}

	//some cell phones are too aggressive with power saving and shut down the wifi when it looks like nothing is using it.
	//this will kill the connection (sometimes silently) and cause calls not to come in but still make it look like you're signed on
	//force the use of exact wakeup alarms to really check the connection regularly... and really schedule the next login when it says.
	public static void setExactWakeup(PendingIntent operation, PendingIntent secondary)
	{
		AlarmManager alarmManager = (AlarmManager)Vars.applicationContext.getSystemService(Context.ALARM_SERVICE);
		if (Build.VERSION.SDK_INT >= 19)
		{
			alarmManager.setExact(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + Const.STD_TIMEOUT, operation);
			if(secondary != null)
			{
				alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + Const.STD_TIMEOUT, secondary);
			}
		}
		else
		{
			alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + Const.STD_TIMEOUT, operation);
		}
	}

	public static void killSockets()
	{
		try
		{
			if(Vars.commandSocket != null)
			{
				Vars.commandSocket.close();
			}
		}
		catch (Exception e)
		{
			dumpException(tag, e);
		}
		Vars.commandSocket = null;

		try
		{
			if(Vars.mediaSocket != null)
			{
				Vars.mediaSocket.close();
			}
		}
		catch (Exception e)
		{
			dumpException(tag, e);
		}
		Vars.mediaSocket = null;
	}

	//for cases when Vars.(shared prefs variable) goes missing or the initial load
	public static void loadPrefs()
	{
		SharedPreferences sharedPreferences = Vars.applicationContext.getSharedPreferences(Const.PREFSFILE, Context.MODE_PRIVATE);
		Vars.uname = sharedPreferences.getString(Const.PREF_UNAME, "");
		Vars.serverAddress = sharedPreferences.getString(Const.PREF_ADDR, "");
		try
		{
			Vars.commandPort = Integer.valueOf(sharedPreferences.getString(Const.PREF_COMMANDPORT, ""));
			Vars.mediaPort = Integer.valueOf(sharedPreferences.getString(Const.PREF_MEDIAPORT, ""));
		}
		catch (NumberFormatException n)
		{
			//usually not fatal. first time running the app no saved values so will try to convert "" to #
			dumpException(tag, n);
		}
		Vars.certName = sharedPreferences.getString(Const.PREF_CERTFNAME, "");
		Vars.certDump = sharedPreferences.getString(Const.PREF_CERTDUMP, "");
		Vars.SHOUDLOG = sharedPreferences.getBoolean(Const.PREF_LOG, Vars.SHOUDLOG);

		//load the private key dump and make it usable
		String privateKeyDump = sharedPreferences.getString(Const.PREF_PRIVATE_KEY_DUMP, "");
		if(!privateKeyDump.equals(""))
		{
			try
			{
				byte[] keyDecoded = Base64.decode(privateKeyDump, Const.BASE64_Flags);
				KeyFactory kf = KeyFactory.getInstance("RSA");
				Vars.privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(keyDecoded));
			}
			catch (Exception e)
			{
				dumpException(tag, e);
				Vars.privateKey = null;
			}
		}
	}

	//for dtsettings and initial server: read the server's public key and set it up for use
	public static boolean readServerPublicKey(Uri uri, Context context)
	{
		try
		{
			ContentResolver resolver = context.getContentResolver();
			InputStream certInputStream = resolver.openInputStream(uri);
			X509Certificate expectedCert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(certInputStream);
			byte[] expectedDump = expectedCert.getEncoded();
			Vars.certDump = Base64.encodeToString(expectedDump, Const.BASE64_Flags);

			//store the certificate file name for esthetic purposes
			String[] expanded = uri.getPath().split("[\\/:\\:]");
			Vars.certName = expanded[expanded.length-1];
			return true;
		}
		catch (FileNotFoundException | CertificateException e)
		{
			//file somehow disappeared between picking and trying to use in the app
			//	there's nothing you can do about it
			Utils.showOk(context, context.getString(R.string.alert_corrupted_cert));
			return false;
		}
	}

	//for dtsettings and initial user: read the user's private key and set it up for use
	public static boolean readUserPrivateKey(Uri uri, Context context)
	{
		try
		{
			//read the private key and convert to a string
			ContentResolver resolver = context.getContentResolver();
			InputStream privateKeyStream = resolver.openInputStream(uri);
			byte[] keyBytes = new byte[Const.BUFFERSIZE];
			privateKeyStream.read(keyBytes);
			privateKeyStream.close();

			//chop of header and footer
			Vars.privateKeyDump = new String(keyBytes);
			Vars.privateKeyDump = Vars.privateKeyDump.replace("-----BEGIN PRIVATE KEY-----\n", "");
			Vars.privateKeyDump = Vars.privateKeyDump.replace("-----END PRIVATE KEY-----", "");

			//actually turn the file into a key
			byte[] keyDecoded = Base64.decode(Vars.privateKeyDump, Const.BASE64_Flags);
			KeyFactory kf = KeyFactory.getInstance("RSA");
			Vars.privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(keyDecoded));

			//if it's gotten this far it must be a useable key, save the name
			String[] expanded = uri.getPath().split("[\\/:\\:]");
			Vars.privateKeyName = expanded[expanded.length-1];
			return true;
		}
		catch (Exception e)
		{
			//file somehow disappeared between picking and trying to use in the app
			//	there's nothing you can do about it
			Utils.dumpException(tag, e);
			Utils.showOk(context, context.getString(R.string.alert_corrupted_cert));

			return false;
		}
	}
}
