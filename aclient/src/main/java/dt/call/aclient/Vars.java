package dt.call.aclient;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;

import java.net.Socket;
import java.util.HashMap;

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
	public static long sessionid = -1;

	//2 sockets
	public static Socket commandSocket;
	public static Socket mediaSocket;

	//call related information
	public static CallState state = CallState.NONE;
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
}
