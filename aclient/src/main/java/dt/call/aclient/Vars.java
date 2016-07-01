package dt.call.aclient;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.net.Socket;
import java.util.HashMap;

import dt.call.aclient.background.AlarmReceiver;
import dt.call.aclient.sqlite.Contact;

/**
 * Created by Daniel on 1/18/16.
 *
 * Holds various session related variables
 * These variables are not permanent and change with each sign in.
 * Therefore they are not changed.
 */
public class Vars
{
	public static boolean SHOUDLOG = true;

	public static long sessionid = -1;

	//2 sockets
	public static Socket commandSocket;
	public static Socket mediaSocket;

	//call related information
	public volatile static CallState state = CallState.NONE;
	public static Contact callWith;

	//contacts hash table to avoid having to lookup the db for incoming calls
	public static HashMap<String, String> contactTable = null;

	//server information
	public static String serverAddress;
	public static int commandPort;
	public static int mediaPort;
	public static String expectedCertDump;

	//user information (to be filled in when available)
	public static String uname = null;
	public static String passwd = null;

	//make sure there is only 1 command listener and only start it if there's internet
	public static boolean cmdListenerRunning = false; //@ first start it's not running
	public static boolean hasInternet = true; //@ first start you have internet, otherwise how did you make the first log in
	public static boolean dontRestart = false;

	//Ongoing notification with state information
	public static Notification.Builder stateNotificationBuilder = null;
	public static NotificationManager notificationManager = null;
	public static PendingIntent go2HomePending = null;
	public static PendingIntent go2CallMainPending = null;

	//for db logging which always needs an annoying context, set it once, use it forever
	public static Context applicationContext = null;

	//alarm manager pending intents
	//https://stackoverflow.com/questions/18649728/android-cannot-pass-intent-extras-though-alarmmanager
	//http://javatechig.com/android/repeat-alarm-example-in-android
	public static Intent retries = null;
	public static PendingIntent pendingRetries = null;
	public static Intent heartbeat = null;
	public static PendingIntent pendingHeartbeat = null;


}
