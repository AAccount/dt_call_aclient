package dt.call.aclient;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationChannel;
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
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.goterl.lazycode.lazysodium.LazySodiumAndroid;
import com.goterl.lazycode.lazysodium.SodiumAndroid;
import com.goterl.lazycode.lazysodium.interfaces.Box;
import com.goterl.lazycode.lazysodium.interfaces.SecretBox;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;

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
	private static int UNSIGNED_CHAR_MAX = 0xff;
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

			Vars.notificationManager.notify(Const.STATE_NOTIFICATION_ID, Vars.stateNotificationBuilder.build());
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
			Vars.notificationManager.notify(Const.STATE_NOTIFICATION_ID, Vars.stateNotificationBuilder.build());
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
		Utils.applyFiller(Vars.privateSodium);

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
			dumpException(tag, e);
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
		Vars.privateSodium = readDataDataFile(Const.INTERNAL_PRIVATEKEY_FILE, Box.SECRETKEYBYTES, Vars.applicationContext);
	}

	public static byte[] interpretSodiumKey(byte[] dump, boolean isPrivate)
	{
		//dump the file contents
		final int headerLength = isPrivate ? Const.SODIUM_PRIVATE_HEADER.length() : Const.SODIUM_PUBLIC_HEADER.length();
		final int expectedLength = headerLength + Box.PUBLICKEYBYTES*Const.STRINGIFY_EXPANSION;
		if(dump == null || dump.length != expectedLength)
		{
			applyFiller(dump);
			return null;
		}

		//see if the file has the correct header
		final byte[] dumpHeader = new byte[headerLength];
		System.arraycopy(dump, 0, dumpHeader, 0, dumpHeader.length);
		final byte[] headerBytes = isPrivate ? Const.SODIUM_PRIVATE_HEADER.getBytes() : Const.SODIUM_PUBLIC_HEADER.getBytes();
		if(!Arrays.equals(dumpHeader, headerBytes))
		{
			return null;
		}


		final byte[] keyStringified = new byte[Box.PUBLICKEYBYTES*Const.STRINGIFY_EXPANSION];
		System.arraycopy(dump, headerLength, keyStringified, 0, keyStringified.length);
		applyFiller(dump);

		//check if the stringified key length makes sense
		if((keyStringified.length % Const.STRINGIFY_EXPANSION) != 0)
		{
			applyFiller(keyStringified);
			return null;
		}

		//turn the stringified binary into actual binary
		final int ASCII_OFFSET = 48;
		byte[] result = new byte[keyStringified.length/Const.STRINGIFY_EXPANSION];
		for(int i=0; i<keyStringified.length; i=i+Const.STRINGIFY_EXPANSION)
		{
			int hundreds = (keyStringified[i]-ASCII_OFFSET)*100;
			int tens = (keyStringified[i+1]-ASCII_OFFSET)*10;
			int ones = (keyStringified[i+2]-ASCII_OFFSET);
			int actual = hundreds + tens + ones;
			result[i/Const.STRINGIFY_EXPANSION] = (byte)(actual & UNSIGNED_CHAR_MAX);
			hundreds = tens = ones = 0;
		}
		return result;
	}

	public static byte[] readSodiumKeyFileBytes(Uri uri, Context context)
	{
		try
		{
			//read the public key and convert to a string
			final ContentResolver resolver = context.getContentResolver();
			final InputStream inputStream = resolver.openInputStream(uri);
			final int longerHeader = Math.max(Const.SODIUM_PRIVATE_HEADER.length(), Const.SODIUM_PUBLIC_HEADER.length());
			final int maximumRead = longerHeader + Box.PUBLICKEYBYTES*Const.STRINGIFY_EXPANSION;
			final byte[] fileBytes = new byte[maximumRead];
			final int amountRead = inputStream.read(fileBytes);
			inputStream.close();

			final byte[] result = new byte[amountRead];
			System.arraycopy(fileBytes, 0, result, 0, amountRead);
			Utils.applyFiller(fileBytes);

			return result;
		}
		catch(Exception e)
		{
			dumpException(tag, e);
			return null;
		}
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
			applyFiller(fileContents);
			return true;
		}
		catch (IOException e)
		{
			Utils.dumpException(tag, e);
			applyFiller(fileContents);
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
			result[i/increment] = (byte)(UNSIGNED_CHAR_MAX & Integer.valueOf(digit));
		}
		return result;
	}

	public static String stringify(byte[] bytes)
	{
		StringBuilder resultBuilder = new StringBuilder(bytes.length*Const.STRINGIFY_EXPANSION);
		for (byte aByte : bytes)
		{
			int unsignedchar = aByte & UNSIGNED_CHAR_MAX;
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

	//break up an into into "byte sized" chunks: 123456: [12, 34, 56]
	public static byte[] disassembleInt(int input, int accuracy /*how many bytes to use*/)
	{
		byte[] result = new byte[accuracy];
		for(int i=0; i<accuracy; i++)
		{
			result[i] = (byte)((input >> (Const.SIZEOF_USEABLE_JBYTE *(accuracy-1-i))) & Byte.MAX_VALUE);
		}
		return result;
	}

	//merge together a split up in [12, 34, 56] as 123456
	public static int reassembleInt(byte[] disassembled)
	{
		int result = 0;
		for(int i=0; i<disassembled.length; i++)
		{
			result = result +((int)disassembled[i]) << (Const.SIZEOF_USEABLE_JBYTE *(disassembled.length-1-i));
		}
		return result;
	}

	public static byte[] sodiumAsymEncrypt(byte[] message, byte[] receiverPublic, byte[] myPrivate)
	{
		return sodiumEncrypt(message, true, receiverPublic, myPrivate);
	}

	public static byte[] sodiumSymEncrypt(byte[] message, byte[] symkey)
	{
		return sodiumEncrypt(message, false, null, symkey);
	}

	private static byte[] sodiumEncrypt(byte[] message, boolean asym, byte[] receiverPublic, byte[] myPrivate)
	{
		//setup nonce (like a password salt)
		int nonceLength = 0;
		if(asym)
		{
			nonceLength = Box.NONCEBYTES;
		}
		else
		{
			nonceLength = SecretBox.NONCEBYTES;
		}
		byte[] nonce = lazySodium.randomBytesBuf(nonceLength);

		//setup cipher text
		int cipherTextLength = 0;
		boolean libsodiumOK = false;
		byte[]cipherText;
		if(asym)
		{
			cipherTextLength = Box.MACBYTES + message.length;
			cipherText = new byte[cipherTextLength];
			libsodiumOK = lazySodium.cryptoBoxEasy(cipherText, message, message.length, nonce, receiverPublic, myPrivate);
		}
		else
		{
			cipherTextLength = SecretBox.MACBYTES + message.length;
			cipherText = new byte[cipherTextLength];
			libsodiumOK = lazySodium.cryptoSecretBoxEasy(cipherText, message, message.length, nonce, myPrivate);
		}

		//something went wrong with the encryption
		if(!libsodiumOK)
		{
			logcat(Const.LOGE, tag, "sodium encryption failed, asym: " + asym + " return code: " + libsodiumOK);;
			return null;
		}

		//glue all the information together for sending
		//[nonce|message length|encrypted message]
		final byte[] messageLengthDissasembled = Utils.disassembleInt(message.length, Const.JAVA_MAX_PRECISION_INT);
		final byte[] finalSetup = new byte[nonceLength+Const.JAVA_MAX_PRECISION_INT+cipherTextLength];
		System.arraycopy(nonce, 0, finalSetup, 0, nonceLength);
		System.arraycopy(messageLengthDissasembled, 0, finalSetup, nonceLength, Const.JAVA_MAX_PRECISION_INT);
		System.arraycopy(cipherText, 0, finalSetup, nonceLength+Const.JAVA_MAX_PRECISION_INT, cipherTextLength);
		return finalSetup;
	}

	public static byte[] sodiumAsymDecrypt(byte[] setup, byte[] senderPublic, byte[] myPrivate)
	{
		return sodiumDecrypt(setup, true, senderPublic, myPrivate);
	}

	public static byte[] sodiumSymDecrypt(byte[] setup, byte[] symkey)
	{
		return sodiumDecrypt(setup, false, null, symkey);
	}

	private static byte[] sodiumDecrypt(byte[] setup, boolean asym, byte[] senderPublic, byte[] myPrivate)
	{
		//[nonce|message length|encrypted message]
		int nonceLength = 0;
		if(asym)
		{
			nonceLength = Box.NONCEBYTES;
		}
		else
		{
			nonceLength = SecretBox.NONCEBYTES;
		}

		//check if the nonce and message length are there
		if(setup.length < (nonceLength + Const.JAVA_MAX_PRECISION_INT))
		{
			return null;
		}

		//reassemble the nonce
		final byte[] nonce = new byte[nonceLength];
		System.arraycopy(setup, 0, nonce, 0, nonceLength);

		//get the message length and check it
		final byte[] messageLengthDisassembled = new byte[Const.JAVA_MAX_PRECISION_INT];
		System.arraycopy(setup, nonceLength, messageLengthDisassembled, 0, Const.JAVA_MAX_PRECISION_INT);
		final int messageLength = Utils.reassembleInt(messageLengthDisassembled);
		final int cipherLength = setup.length - nonceLength - Const.JAVA_MAX_PRECISION_INT;
		final boolean messageCompressed = messageLength > cipherLength;
		final boolean messageMIA = messageLength < 1;
		if(messageCompressed || messageMIA)
		{
			return null;
		}

		//get the cipher text
		final byte[] cipherText = new byte[cipherLength];
		System.arraycopy(setup, nonceLength+Const.JAVA_MAX_PRECISION_INT, cipherText, 0, cipherLength);
		final byte[] messageStorage= new byte[cipherLength];//store the message in somewhere it is guaranteed to fit in case messageLength is bogus/malicious

		boolean libsodiumOK = false;
		if(asym)
		{
			libsodiumOK = lazySodium.cryptoBoxOpenEasy(messageStorage, cipherText, cipherLength, nonce, senderPublic, myPrivate);
		}
		else
		{
			libsodiumOK = lazySodium.cryptoSecretBoxOpenEasy(messageStorage, cipherText, cipherLength, nonce, myPrivate);
		}

		if(!libsodiumOK)
		{
			logcat(Const.LOGE, tag, "sodium decryption failed, asym: " + asym + " return code: " + libsodiumOK);;
			return null;
		}

		//now that the message has been successfully decrypted, take in on blind faith messageLength was ok
		//	up to the next function to make sure the decryption contents aren't truncated by a malicious messageLength
		final byte[] message = new byte[messageLength];
		System.arraycopy(messageStorage, 0, message, 0, messageLength);
		return message;
	}

	public static byte[] trimArray(byte[] input, int trimmed)
	{
		byte[] result = new byte[trimmed];
		System.arraycopy(input, 0, result, 0, trimmed);
		return result;
	}
}
