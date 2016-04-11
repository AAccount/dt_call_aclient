package dt.call.aclient;

import dt.call.aclient.sqlite.Contact;

/**
 * Created by Daniel on 1/17/16.
 *
 * Similar to server's const.h holds various constants to force standardizing their names
 */
public class Const
{
	//shared preference keys
	public static final String PREFSFILE = "call_prefs"; //file name of the shared preferences
	public static final String ADDR = "server_address";
	public static final String COMMANDPORT = "command_port";
	public static final String MEDIAPORT = "media_port";
	public static final String CERT64 = "certificate_getEncoded_string_base64";
	public static final String CERTFNAME = "certificate_file_name";
	public static final String UNAME = "username";
	public static final String PASSWD = "password";

	//Java 1byte workaround
	public static final String JBYTE = "D";

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
	public static final boolean SHOUDLOG = true;
	public static final int LOGE = 1;
	public static final int LOGD = 2;
	public static final int LOGW = 3;

	//when cmd listener dies
	public static final String BROADCAST_BK_CMDDEAD = "cmd_dead";
	public static final String BROADCAST_BK_HEARTBEAT = "heartbeat";
	public static final String BROADCAST_BK_HEARTBEAT_DOIT = "do_heartbeat";

	//call history types
	public static final int outgoing = 1;
	public static final int incoming = 2;

	//broadcast intent shared by call main and incoming call screen
	//both need the call end signal
	// (either the person hung or changed his mind and cancelled before you answered)
	//only call main responds to call accept
	public static final String BROADCAST_CALL = "notify_call_info";
	public static final String BROADCAST_CALL_RESP = "call_response";
	public static final String BROADCAST_CALL_START = "start";
	public static final String BROADCAST_CALL_END = "end";

	//broadcasting login result
	public static final String BROADCAST_LOGIN = "broadcast_login";
	public static final String BROADCAST_LOGIN_RESULT = "login_result";

	//log related strings
	public static final String EXTRA_LOG = "log_obj";

	//persistent notification id
	public static final int stateNotificationId = 1;
}