package dt.call.aclient.background.Async;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.AsyncTask;

import java.net.InetAddress;

import dt.call.aclient.Const;
import dt.call.aclient.Utils;

/**
 * Created by Daniel on 3/28/16.
 */
public class CheckInternetAsync extends AsyncTask <Context, String, Boolean>
{
	private static final String tag = "CheckInternetAsync";

	protected Boolean doInBackground(Context... params)
	{
		//https://stackoverflow.com/questions/9570237/android-check-internet-connection
		ConnectivityManager cm = (ConnectivityManager) params[0].getSystemService(Context.CONNECTIVITY_SERVICE);
		if(cm.getActiveNetworkInfo() == null)
		{
			Utils.logcat(Const.LOGD, tag, "no network connection");
			return false;
		}
		try
		{
			InetAddress ipAddr = InetAddress.getByName("google.com");
			if(ipAddr.equals(""))
			{
				Utils.logcat(Const.LOGD, tag, "yes network connection, but no access to the outside world");
				return false;
			}
			else
			{
				return true;
			}
		}
		catch (Exception e)
		{
			Utils.dumpException(tag, e);
			return false;
		}
	}
}
