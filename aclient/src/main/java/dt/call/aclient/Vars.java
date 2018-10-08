package dt.call.aclient;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.security.PublicKey;
import java.util.HashMap;

import javax.net.ssl.SSLSocket;

/**
 * Created by Daniel on 1/18/16.
 *
 * Holds various session related variables
 * These variables are not permanent and change with each sign in.
 * Therefore they are not stored on disk.
 */
public class Vars
{
	public static boolean SHOUDLOG = true;

	public static String sessionKey = "";

	//2 sockets
	public static Socket commandSocket = null;
	public static byte[] tcpKey = null;
	public static DatagramSocket mediaUdp = null;
	public static InetAddress callServer = null;

	//call related information
	public volatile static CallState state = CallState.NONE;
	public static String callWith;
	public static PowerManager.WakeLock incomingCallLock = null;
	public static PowerManager.WakeLock incallA9Workaround = null;
	public static byte[] sodiumSymmetricKey = null;

	//contacts hash table to avoid having to lookup the db for incoming calls
	public static HashMap<String, String> contactTable = null;
	public static HashMap<String, byte[]> publicSodiumTable = null;
	public static HashMap<String, String> publicSodiumDumps = null;

	//server information
	public static String serverAddress;
	public static int commandPort;
	public static int mediaPort;
	public static String certDump;
	public static String certName;
	public static PublicKey serverTlsKey = null;
	public static String serverPublicSodiumName;
	public static String serverPublicSodiumDump;
	public static byte[] serverPublicSodium = null;

	//user information (to be filled in when available)
	public static String uname = null;
	public static byte[] privateSodium = null;
	public static String privateSodiumDump = null;
	public static String privateSodiumName = null;

	//Ongoing notification with state information
	public static NotificationCompat.Builder stateNotificationBuilder = null;
	public static NotificationManager notificationManager = null;
	public static PendingIntent go2HomePending = null;
	public static PendingIntent go2CallMainPending = null;
	public static PendingIntent go2CallIncomingPending = null;

	//for every little annoying thing that needs a context... here's one
	public static Context applicationContext = null;

	//alarm manager pending intents
	//https://stackoverflow.com/questions/18649728/android-cannot-pass-intent-extras-though-alarmmanager
	//http://javatechig.com/android/repeat-alarm-example-in-android
	public static Intent retries = null;
	public static PendingIntent pendingRetries = null;
	public static PendingIntent pendingRetries2ndary = null;
	public static Intent heartbeat = null;
	public static PendingIntent pendingHeartbeat = null;
	public static PendingIntent pendingHeartbeat2ndary = null;
}
