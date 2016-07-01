package dt.call.aclient;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.util.Base64;
import android.util.Log;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Socket;

import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.TimeZone;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import dt.call.aclient.background.AlarmReceiver;
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

	//Make sure the timestamp sent to the server is relative to the server's timezone
	//otherwise the server's +-5min window will fail
	public static long generateServerTimestamp()
	{
		TimeZone localTZ = TimeZone.getDefault();
		TimeZone eastern = TimeZone.getTimeZone("America/Toronto"); //change this to match your server's local time
		long now = Utils.getLocalTimestamp();
		int offset =  localTZ.getOffset(now) - eastern.getOffset(now);
		return now - offset;
	}

	public static long getLocalTimestamp()
	{
		return System.currentTimeMillis()/1000L;
	}

	public static boolean validTS(long ts)
	{
		long now = generateServerTimestamp();
		long fivemins = 60*5;
		long diff = now-ts;

		return Math.abs(diff) <= fivemins;
	}

	public static Socket mkSocket(String host, int port, final String expected64) throws CertificateException
	{
		TrustManager[] trustOnlyServerCert = new TrustManager[]
		{new X509TrustManager()
				{
					@Override
					public void checkClientTrusted(X509Certificate[] chain, String alg)
					{
					}

					@Override
					public void checkServerTrusted(X509Certificate[] chain, String alg) throws CertificateException
					{
						//Get the certificate encoded as ascii text. Normally a certificate can be opened
						//	by a text editor anyways.
						byte[] serverCertDump = chain[0].getEncoded();
						String server64 = Base64.encodeToString(serverCertDump, Base64.NO_PADDING & Base64.NO_WRAP);

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
			Socket socket = mkssl.createSocket(host, port);
			socket.setKeepAlive(true);
			return socket;
		}
		catch (Exception e)
		{
			dumpException(tag, e);
			return null;
		}
	}

	//for temporary spammy logging. don't junk up the db
	public static void logcat(int type, String tag, String message, boolean nodb)
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

			if(!nodb)
			{
				SQLiteDb sqLiteDb = SQLiteDb.getInstance(Vars.applicationContext);
				sqLiteDb.insertLog(new DBLog(tag, message));
			}
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

	//changes the message in the ongoing notification
	public static void updateNotification(String message, PendingIntent go2)
	{
		Vars.stateNotificationBuilder
				.setContentText(message)
				.setContentIntent(go2);
		Vars.notificationManager.notify(Const.stateNotificationId, Vars.stateNotificationBuilder.build());
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
		if(Vars.retries == null || Vars.pendingRetries == null || Vars.heartbeat == null || Vars.pendingHeartbeat == null)
		{
			Vars.retries = new Intent(Vars.applicationContext, AlarmReceiver.class);
			Vars.retries.putExtra(Const.ALARM_ACTION, Const.ALARM_ACTION_RETRY);
			Vars.pendingRetries = PendingIntent.getBroadcast(Vars.applicationContext, Const.ALARM_RETRY_ID, Vars.retries, PendingIntent.FLAG_UPDATE_CURRENT);
			Vars.heartbeat = new Intent(Vars.applicationContext, AlarmReceiver.class);
			Vars.heartbeat.putExtra(Const.ALARM_ACTION, Const.ALARM_ACTION_HEARTBEAT);
			Vars.pendingHeartbeat = PendingIntent.getBroadcast(Vars.applicationContext, Const.ALARM_HEARTBEAT_ID, Vars.heartbeat, PendingIntent.FLAG_UPDATE_CURRENT);
		}
	}
}
