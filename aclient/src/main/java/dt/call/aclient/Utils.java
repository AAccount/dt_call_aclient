package dt.call.aclient;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.goterl.lazycode.lazysodium.LazySodiumAndroid;
import com.goterl.lazycode.lazysodium.SodiumAndroid;
import com.goterl.lazycode.lazysodium.interfaces.Box;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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
	private static LazySodiumAndroid lazySodium = new LazySodiumAndroid(new SodiumAndroid());;

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

			try
			{
				SQLiteDb sqLiteDb = SQLiteDb.getInstance(Vars.applicationContext);
				sqLiteDb.insertLog(new DBLog(tag, message));
			}
			catch(Exception e)
			{
				Log.e("dblog","database not writeable");
			}
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
			Vars.stateNotificationBuilder = new NotificationCompat.Builder(Vars.applicationContext, Const.STATE_NOTIFICATION_CHANNEL)
					.setContentTitle(Vars.applicationContext.getString(R.string.app_name))
					.setContentText(Vars.applicationContext.getString(stringRes))
					.setSmallIcon(R.drawable.ic_vpn_lock_white_48dp)
					.setContentIntent(Vars.go2HomePending)
					.setColor(ContextCompat.getColor(Vars.applicationContext, colorRes))
					.setColorized(true)
					.setOngoing(true)
					.setChannelId(Const.STATE_NOTIFICATION_CHANNEL);
			Vars.notificationManager = (NotificationManager) Vars.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);

			//setup channel for android 8.0+
			if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
			{
				NotificationChannel stateNotificationChannel = new NotificationChannel(Const.STATE_NOTIFICATION_CHANNEL, Const.STATE_NOTIFICATION_NAME, NotificationManager.IMPORTANCE_DEFAULT);
				stateNotificationChannel.setSound(null, null); //no sound when launching the app
				stateNotificationChannel.setShowBadge(false);
				Vars.notificationManager.createNotificationChannel(stateNotificationChannel);
			}
			Vars.stateNotification = Vars.stateNotificationBuilder.build();
			Vars.notificationManager.notify(Const.STATE_NOTIFICATION_ID, Vars.stateNotification);
		}
		else
		{
			Vars.notificationManager.cancel(Const.STATE_NOTIFICATION_ID);
			Vars.stateNotificationBuilder
					.setContentText(Vars.applicationContext.getString(stringRes))
					.setColor(ContextCompat.getColor(Vars.applicationContext, colorRes))
					.setColorized(true)
					.setChannelId(Const.STATE_NOTIFICATION_CHANNEL)
					.setContentIntent(go2);
			Vars.stateNotification = Vars.stateNotificationBuilder.build();
			Vars.notificationManager.notify(Const.STATE_NOTIFICATION_ID, Vars.stateNotification);
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

	public static void quit(AppCompatActivity caller)
	{
		//get rid of the status notification if it's running
		if(Vars.notificationManager != null)
		{
			Vars.notificationManager.cancelAll();

			//clean up after itself
			if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
			{
				Vars.notificationManager.deleteNotificationChannel(Const.STATE_NOTIFICATION_CHANNEL);
			}
		}

		//surprisingly does not turn to null after quitting.
		//"hand null" so that the notification channel is built when the app is launched again
		Vars.stateNotificationBuilder = null;
		Vars.notificationManager = null;

		//Kill alarms
		if(Vars.pendingHeartbeat != null && Vars.pendingRetries != null)
		{
			AlarmManager manager = (AlarmManager) Vars.applicationContext.getSystemService(Context.ALARM_SERVICE);
			manager.cancel(Vars.pendingHeartbeat);
			manager.cancel(Vars.pendingHeartbeat2ndary);
			manager.cancel(Vars.pendingRetries);
			manager.cancel(Vars.pendingRetries2ndary);
		}
		Vars.pendingHeartbeat = null;
		Vars.pendingHeartbeat2ndary = null;
		Vars.pendingRetries = null;
		Vars.pendingRetries2ndary = null;

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

		//overwrite private key memory
		Utils.applyFiller(Vars.selfPrivateSodium);

		//properly kill the app
		caller.finishAffinity();
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
			Vars.mediaUdp.close();
		}
		catch (Exception e)
		{
		}
		Vars.commandSocket = null;
	}

	//for cases when Vars.(shared prefs variable) goes missing or the initial load
	public static void loadPrefs()
	{
		lazySodium = new LazySodiumAndroid(new SodiumAndroid());

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
		Vars.SHOUDLOG = sharedPreferences.getBoolean(Const.PREF_LOG, Vars.SHOUDLOG);

		//load the private key dump and make it usable
		Vars.serverPublicSodium = readDataDataFile(Const.INTERNAL_SERVER_PUBLICKEY_FILE, Box.PUBLICKEYBYTES, Vars.applicationContext);
		Vars.selfPrivateSodium = readDataDataFile(Const.INTERNAL_PRIVATEKEY_FILE, Box.SECRETKEYBYTES, Vars.applicationContext);
	}

	public static void applyFiller(byte[] sensitiveStuff)
	{
		if(sensitiveStuff == null)
		{
			return;
		}
		final byte[] filler = lazySodium.randomBytesBuf(sensitiveStuff.length);
		System.arraycopy(filler, 0, sensitiveStuff, 0, sensitiveStuff.length);
	}

	public static void applyFiller(short[] sensitiveStuff)
	{
		if(sensitiveStuff == null)
		{
			return;
		}

		final byte[] filler = lazySodium.randomBytesBuf(sensitiveStuff.length*2);
		ByteBuffer.wrap(filler).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(sensitiveStuff);
	}

	public static byte[] readDataDataFile(String fileName, int length, Context context) //so named because files are in /data/data/dt.call.aclient
	{
		File file = new File(context.getFilesDir(), fileName);
		if(file.exists())
		{
			byte[] contents = new byte[length];
			try
			{
				FileInputStream fileInputStream = new FileInputStream(file);
				int read = fileInputStream.read(contents);
				if(read != length)
				{
					Utils.applyFiller(contents);
					return null;
				}
				return contents;
			}
			catch (Exception e)
			{
				Utils.dumpException(tag, e);
				applyFiller(contents);
				return null;
			}
		}
		return null;
	}

	public static boolean writeDataDataFile(String fileName, byte[] fileContents, Context context)
	{
		File privateKeyFile = new File(context.getFilesDir(), fileName);
		try
		{
			FileOutputStream fileOutputStream = new FileOutputStream(privateKeyFile, false);
			fileOutputStream.write(fileContents);
			fileOutputStream.close();
			//don't apply filler now. the fileContents may still be needed
			return true;
		}
		catch (IOException e)
		{
			Utils.dumpException(tag, e);
			return false;
		}
	}

	//turn a string of #s into actual #s assuming the string is a bunch of
	//	3 digit #s glued to each other. also turned unsigned #s into signed #s
	public static byte[] destringify(String numbers)
	{
		final int increment = Const.STRINGIFY_EXPANSION;
		byte[] result = new byte[numbers.length()/increment];
		for(int i=0; i<numbers.length(); i=i+increment)
		{
			String digit = numbers.substring(i, i+increment);
			result[i/increment] = (byte)(Const.UNSIGNED_CHAR_MAX & Integer.valueOf(digit));
		}
		return result;
	}

	public static String stringify(byte[] bytes)
	{
		return stringify(bytes, bytes.length);
	}

	public static String stringify(byte[] bytes, int length)
	{
		StringBuilder resultBuilder = new StringBuilder(bytes.length*Const.STRINGIFY_EXPANSION);
		for(int i=0; i<length; i++)
		{
			int unsignedchar = bytes[i] & Const.UNSIGNED_CHAR_MAX;
			String number = String.valueOf(unsignedchar);

			//prepend the required zeros
			if (unsignedchar < 10)
			{//for 1,2,3 to keep everything as 3 digit #s make it 001, 002 etc
				number = "00" + number;
			}
			else if (unsignedchar < 100)
			{//for 10,11,12 make it 010,011,012
				number = "0" + number;
			}
			resultBuilder.append(number);
		}
		return resultBuilder.toString();
	}

	public static String getCallerID(String userName)
	{
		//if the person is a registered contact, display the nickname. otherwise just use the user account name
		String result = Vars.contactTable.get(userName);
		if(result == null)
		{
			result = userName;
		}
		return result;
	}

	public static byte[] disassembleInt(int input)
	{
		return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(input).array();
	}

	public static int reassembleInt(byte[] disassembled)
	{
		return ByteBuffer.wrap(disassembled).order(ByteOrder.BIG_ENDIAN).getInt();
	}

	public static byte[] trimArray(byte[] input, int trimmed)
	{
		byte[] result = new byte[trimmed];
		System.arraycopy(input, 0, result, 0, trimmed);
		return result;
	}
}
