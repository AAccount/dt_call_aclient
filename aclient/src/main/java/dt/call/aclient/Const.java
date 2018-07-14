package dt.call.aclient;

import android.os.Build;
import android.util.Base64;

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
	public static final String PREF_CERTDUMP = "certificate_getEncoded_string_base64";
	public static final String PREF_CERTFNAME = "certificate_file_name";
	public static final String PREF_SODIUM_DUMP = "server_sodium_public";
	public static final String PREF_SODIUM_DUMP_NAME = "server_sodium_public_name";
	public static final String PREF_UNAME = "username";
	public static final String PREF_PRIVATE_KEY_DUMP = "private_key";
	public static final String PREF_PRIVATE_KEY_NAME = "private_key_name";
	public static final String PREF_LOG = "log";

	//file selection codes
	public static final int SELECT_SERVER_SSLCERT = 1;
	public static final int SELECT_PRIVATE_SODIUM = 2;
	public static final int SELECT_USER_PUBLIC_SODIUM = 3;
	public static final int SELECT_SERVER_PUBLIC_SODIUM = 4;
	public static final int BASE64_Flags = Base64.NO_PADDING & Base64.NO_WRAP;

	//android permission request codes
	public static final int PERM_STORAGE = 1;
	public static final int PERM_MIC = 2;

	//heartbeat byte
	public static final String JBYTE = "D";
	public static final int SIZE_COMMAND = 2048;
	public static final int SIZE_MEDIA = 1200;

	//nobody: the default value for when not in a call
	public static final String nobody = "(nobody)";

	//log.e/d/i wrapper to avoid wasting cpu for logging
	public static final int LOGE = 1;
	public static final int LOGD = 2;
	public static final int LOGW = 3;
	public static final int LOG_LIMIT = 350;

	//when cmd listener dies
	public static final String BROADCAST_RELOGIN = "dt.call.aclient.relogin";

	//broadcast intent shared by call main and incoming call screen
	//both need the call end signal
	// (either the person hung or changed his mind and cancelled before you answered)
	//only call main responds to call accept
	public static final String BROADCAST_CALL = "dt.call.aclient.notify_call_info";
	public static final String BROADCAST_CALL_RESP = "call_response";
	public static final String BROADCAST_CALL_TRY = "try";
	public static final String BROADCAST_CALL_START = "start";
	public static final String BROADCAST_CALL_END = "end";

	//broadcasting login result
	public static final String BROADCAST_LOGIN = "dt.call.aclient.broadcast_login";
	public static final String BROADCAST_LOGIN_RESULT = "login_result";

	//whether or not to use the jobservice method of internet detection
	public static final String BROADCAST_HAS_INTERNET = "dt.call.aclient.HAS_INTERNET";
	public static final boolean NEEDS_MANUAL_INTERNET_DETECTION = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;

	//log related strings
	public static final String EXTRA_LOG = "log_obj";

	//persistent notification id
	public static final int STATE_NOTIFICATION_ID = 1;
	public static final String STATE_NOTIFICATION_CHANNEL = "dt.call.aclient.state";
	public static final String STATE_NOTIFICATION_NAME = "AClient_State";

	//related to alarm receiver and alarm stuff
	public static final int ALARM_RETRY_ID = 1234;
	public static final int ALARM_HEARTBEAT_ID = 999;
	//alarm broadcast fore retry shared with broadcast for dead command listener BROADCAST_RELOGIN
	public static final String ALARM_ACTION_HEARTBEAT = "do_heartbeat";
	public static final int STD_TIMEOUT = 5*60*1000;

	//timeout (IN SECONDS) before giving up on calling someone
	public static final int CALL_TIMEOUT = 20;

	//wakelock tag
	public static final String WAKELOCK_TAG = "dt.call.aclient.wakelock";

	//command maximum segments
	public static final int COMMAND_MAX_SEGMENTS = 5;
	public static final int LOGIN_MAX_SEGMENTS = 3;

	//udp port related variables
	public static final int UDP_RETRIES = 10;
	public static final int UDP_ACK_TIMEOUT = 100; //in milliseconds
	public static final int DSCP_EXPEDITED_FWD = (0x2E << 2);
	public static final String SODIUM_PLACEHOLDER = "SODIUM_SETUP_PLACEHOLDER";

	//maximum disassembly accuracy for taking apart an int into signed chars
	public static final int JAVA_MAX_PRECISION_INT = 5;
	public static final int SIZEOF_USEABLE_JBYTE = 7; //8th bit for the sign


	public static final String SODIUM_PUBLIC_HEADER = "SODIUM PUBLIC KEY\n";
	public static final String SODIUM_PRIVATE_HEADER = "SODIUM PRIVATE KEY\n";
	public static final String EXTRA_UNAME = "user_name_extra";
}