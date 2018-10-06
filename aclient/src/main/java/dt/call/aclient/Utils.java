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
import android.util.Base64;
import android.util.Log;

import com.goterl.lazycode.lazysodium.LazySodiumAndroid;
import com.goterl.lazycode.lazysodium.SodiumAndroid;
import com.goterl.lazycode.lazysodium.interfaces.Box;
import com.goterl.lazycode.lazysodium.interfaces.SecretBox;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

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
		Vars.certName = sharedPreferences.getString(Const.PREF_CERTFNAME, "");
		Vars.certDump = sharedPreferences.getString(Const.PREF_CERTDUMP, "");
		Vars.SHOUDLOG = sharedPreferences.getBoolean(Const.PREF_LOG, Vars.SHOUDLOG);

		//load the private key dump and make it usable
		String privateKeyDump = sharedPreferences.getString(Const.PREF_PRIVATE_KEY_DUMP, "");
		if(!privateKeyDump.equals(""))
		{
			Vars.privateSodium = interpretSodiumPrivateKey(privateKeyDump);
		}

		if(Vars.certDump != null)
		{
			try
			{
				byte[] serverCertBytes = Base64.decode(Vars.certDump, Const.BASE64_Flags);
				X509Certificate serverCert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(serverCertBytes));
				Vars.serverTlsKey = serverCert.getPublicKey();
			}
			catch(Exception e)
			{
				dumpException(tag, e);
			}
		}

		Vars.serverPublicSodiumDump = sharedPreferences.getString(Const.PREF_SODIUM_DUMP, "");
		if(!Vars.serverPublicSodiumDump.equals(""))
		{
			Vars.serverPublicSodium = interpretSodiumPublicKey(Vars.serverPublicSodiumDump);
		}
		Vars.serverPublicSodiumName = sharedPreferences.getString(Const.PREF_SODIUM_DUMP_NAME, "");
	}

	public static byte[] interpretSodiumPublicKey(String dump)
	{
		int expectedLength = Const.SODIUM_PUBLIC_HEADER.length() + Box.PUBLICKEYBYTES*3;
		if(dump.length() != expectedLength)
		{
			return null;
		}

		dump = dump.substring(Const.SODIUM_PUBLIC_HEADER.length());
		return destringify(dump, false);
	}

	public static byte[] interpretSodiumPrivateKey(String dump)
	{
		int expectedLength = Const.SODIUM_PRIVATE_HEADER.length() + Box.SECRETKEYBYTES*3;
		if(dump.length() != expectedLength)
		{
			return null;
		}

		dump = dump.substring(Const.SODIUM_PRIVATE_HEADER.length());
		return destringify(dump, false);
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
			Vars.serverTlsKey = expectedCert.getPublicKey();

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

	public static byte[] readSodiumKeyFileBytes(Uri uri, Context context)
	{
		try
		{
			//read the public key and convert to a string
			ContentResolver resolver = context.getContentResolver();
			InputStream inputStream = resolver.openInputStream(uri);
			int longerHeader = Math.max(Const.SODIUM_PRIVATE_HEADER.length(), Const.SODIUM_PUBLIC_HEADER.length());
			byte[] fileBytes = new byte[longerHeader + Box.PUBLICKEYBYTES*3];
			int amountRead = inputStream.read(fileBytes);
			inputStream.close();
			byte[] result = new byte[amountRead];
			System.arraycopy(fileBytes, 0, result, 0, amountRead);
			return result;
		}
		catch(Exception e)
		{
			dumpException(tag, e);
			return null;
		}
	}

	//for dtsettings and initial user: read the user's private key and set it up for use
	public static boolean readUserSodiumPrivate(Uri uri, Context context)
	{
		byte[] keyBytes = readSodiumKeyFileBytes(uri, context);
		if(keyBytes == null)
		{
			return false;
		}

		//interpret the file contents as a public key
		Vars.privateSodiumDump = new String(keyBytes);
		Vars.privateSodium = Utils.interpretSodiumPrivateKey(Vars.privateSodiumDump);
		if(Vars.privateSodium == null)
		{
			return false;
		}

		String[] expanded = uri.getPath().split("[\\/:\\:]");
		Vars.privateSodiumName = expanded[expanded.length-1];
		return true;
	}

	//for dtsettings and initial server: read the server's public sodium key and set it up for use
	public static boolean readServerSodiumPublic(Uri uri, Context context)
	{
		byte[] keyBytes = readSodiumKeyFileBytes(uri, context);
		if(keyBytes == null)
		{
			return false;
		}

		//interpret the file contents as a public key
		try
		{
			Vars.serverPublicSodiumDump = new String(keyBytes, "UTF8");
		}
		catch (UnsupportedEncodingException e)
		{
			dumpException(tag, e);
			return false;
		}
		Vars.serverPublicSodium = Utils.interpretSodiumPublicKey(Vars.serverPublicSodiumDump);
		if(Vars.serverPublicSodium == null)
		{
			return false;
		}

		String[] expanded = uri.getPath().split("[\\/:\\:]");
		Vars.serverPublicSodiumName = expanded[expanded.length-1];
		return true;
	}

	//turn a string of #s into actual #s assuming the string is a bunch of
	//	3 digit #s glued to each other. also turned unsigned #s into signed #s
	public static byte[] destringify(String numbers, boolean signed)
	{
		int increment;
		if(signed)
		{
			increment = 4;
		}
		else
		{
			increment = 3;
		}

		byte[] result = new byte[numbers.length()/increment];
		for(int i=0; i<numbers.length(); i=i+increment)
		{
			String digit = numbers.substring(i, i+increment);
			result[i/increment] = (byte)(0xff & Integer.valueOf(digit));
		}
		return result;
	}

	public static String stringify(byte[] bytes, boolean signed)
	{
		String result = "";
		for(int i=0; i<bytes.length; i++)
		{
			String number = String.valueOf(Math.abs(bytes[i]));

			//prepend the required zeros
			if(Math.abs(bytes[i]) < 10)
			{//for 1,2,3 to keep everything as 3 digit #s make it 001, 002 etc
				number = "00" + number;
			}
			else if (Math.abs(bytes[i]) < 100)
			{//for 10,11,12 make it 010,011,012
				number = "0" + number;
			}

			if(signed)
			{
				//add the sign
				if (bytes[i] >= 0)
				{
					number = "+" + number;
				}
				else
				{
					number = "-" + number;
				}
			}
			result = result + number;
		}
		return result;
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

	public static byte[] sodiumAsymEncrypt(byte[] message, byte[] receiver)
	{
		return sodiumEncrypt(message, true, receiver);
	}

	public static byte[] sodiumSymEncrypt(byte[] message)
	{
		return sodiumEncrypt(message, false, null);
	}

	private static byte[] sodiumEncrypt(byte[] message, boolean asym, byte[] receiver)
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
			libsodiumOK = lazySodium.cryptoBoxEasy(cipherText, message, message.length, nonce, receiver, Vars.privateSodium);
		}
		else
		{
			cipherTextLength = SecretBox.MACBYTES + message.length;
			cipherText = new byte[cipherTextLength];
			libsodiumOK = lazySodium.cryptoSecretBoxEasy(cipherText, message, message.length, nonce, Vars.sodiumSymmetricKey);
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

	public static byte[] sodiumAsymDecrypt(byte[] setup, byte[] from)
	{
		return sodiumDecrypt(setup, true, from);
	}

	public static byte[] sodiumSymDecrypt(byte[] setup)
	{
		return sodiumDecrypt(setup, false, null);
	}

	private static byte[] sodiumDecrypt(byte[] setup, boolean asym, byte[] from)
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
			libsodiumOK = lazySodium.cryptoBoxOpenEasy(messageStorage, cipherText, cipherLength, nonce, from, Vars.privateSodium);
		}
		else
		{
			libsodiumOK = lazySodium.cryptoSecretBoxOpenEasy(messageStorage, cipherText, cipherLength, nonce, Vars.sodiumSymmetricKey);
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
}
