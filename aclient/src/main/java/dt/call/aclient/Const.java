package dt.call.aclient;

import android.os.Build;

import dt.call.aclient.sqlite.Contact;

/**
 * Created by Daniel on 1/17/16.
 *
 * Similar to server's const.h holds various constants to force standardizing their names
 */
public class Const
{
	public static final String PACKAGE_NAME = "dt.call.aclient";

	//shared preference keys
	public static final String PREFSFILE = "call_prefs"; //file name of the shared preferences
	public static final String PREF_ADDR = "server_address";
	public static final String PREF_COMMANDPORT = "command_port";
	public static final String PREF_MEDIAPORT = "media_port";
	public static final String PREF_CERT64 = "certificate_getEncoded_string_base64";
	public static final String PREF_CERTFNAME = "certificate_file_name";
	public static final String PREF_UNAME = "username";
	public static final String PREF_PASSWD = "password";
	public static final String PREF_LOG = "log";

	//Java 1byte workaround
	public static final String JBYTE = "D";
	public static final int BUFFERSIZE = 1024; //maximum size for command buffer. same name and size as seen in server's const.h

	//nobody: the default value for when not in a call
	public static final Contact nobody = new Contact("(nobody)");

	//broadcast intent for user home related strings
	public static final String BROADCAST_HOME = "notify_home";
	public static final String BROADCAST_HOME_TYPE = "type";
	public static final String BROADCAST_HOME_TYPE_LOOKUP = "type_lookup";
	public static final String BROADCAST_HOME_LOOKUP_NAME = "lookup_name";
	public static final String BROADCAST_HOME_LOOKUP_RESULT = "lookup_result";
	public static final String BROADCAST_HOME_TYPE_INIT = "type_init";
	public static final String BROADCAST_HOME_INIT_CANINIT = "can_init";

	//log.e/d/i wrapper to avoid wasting cpu for logging
	public static final int LOGE = 1;
	public static final int LOGD = 2;
	public static final int LOGW = 3;
	public static final int LOG_LIMIT = 350;

	//when cmd listener dies
	public static final String BROADCAST_BK_CMDDEAD = "dt.call.aclient.cmd_dead";

	//call history types
	public static final int CALLOUTGOING = 1;
	public static final int CALLINCOMING = 2;
	public static final int CALLMISSED = 3;

	//broadcast intent shared by call main and incoming call screen
	//both need the call end signal
	// (either the person hung or changed his mind and cancelled before you answered)
	//only call main responds to call accept
	public static final String BROADCAST_CALL = "dt.call.aclient.notify_call_info";
	public static final String BROADCAST_CALL_RESP = "call_response";
	public static final String BROADCAST_CALL_START = "start";
	public static final String BROADCAST_CALL_END = "end";

	//broadcasting login result
	public static final String BROADCAST_LOGIN_FG = "dt.call.aclient.broadcast_login_foreground";
	public static final String BROADCAST_LOGIN_BG = "dt.call.aclient.broadcast_login_background";
	public static final String BROADCAST_LOGIN_RESULT = "login_result";

	//app specific broadcast of internet reconnected
	public static final String BROADCAST_HAS_INTERNET = "dt.call.aclient.HAS_INTERNET";
	public static final int MINVER_MANUAL_HAS_INTERNET = Build.VERSION_CODES.M;

	//log related strings
	public static final String EXTRA_LOG = "log_obj";

	//persistent notification id
	public static final int stateNotificationId = 1;

	//related to alarm receiver and alarm stuff
	public static final int ALARM_RETRY_ID = 1234;
	public static final int ALARM_HEARTBEAT_ID = 999;
	public static final String ALARM_ACTION_RETRY = "do_retries";
	public static final String ALARM_ACTION_HEARTBEAT = "do_heartbeat";
	public static final int RETRY_FREQ = 5*60*1000;
	public static final int HEARTBEAT_FREQ = 5*60*1000;

	//timeout (IN SECONDS) before giving up on calling someone
	public static final int CALL_TIMEOUT = 20;

	//for keeping track of when the last cmd listener dead happened. allows the app to detect the stupid java socket connect/die quick cycle
	public static final int QUICK_DEAD_MAX = 20;
	public static final long QUICK_DEAD_THRESHOLD = 10000;
	public static final int RESTART_DELAY = 5000; //5 seconds delay for time to quit before restarting
}