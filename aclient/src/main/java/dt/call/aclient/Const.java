package dt.call.aclient;

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
	public static final String cap = "G";

	//nobody: the default value for when not in a call
	public static final String nobody = "(nobody)";

	//broadcast intent for user home related strings
	public static final String NOTIFYHOME = "notify_home";
	public static final String TYPE = "type";
	public static final String TYPELOOKUP = "type_lookup";
	public static final String LOOKUPNAME = "lookup_name";
	public static final String LOOKUPRESULT = "lookup_result";
	public static final String TYPEINIT = "type_init";
	public static final String CANINIT = "can_init";

	//log.e/d/i wrapper to avoid wasting cpu for logging
	public static final boolean SHOUDLOG = true;
	public static final int LOGE = 1;
	public static final int LOGD = 2;
	public static final int LOGW = 3;

	//when cmd listener dies
	public static final String CMDDEAD = "cmd_dead";

	//call history types
	public static final int outgoing = 1;
	public static final int incoming = 2;
}
