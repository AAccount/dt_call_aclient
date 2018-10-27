package dt.call.aclient.background;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;

import dt.call.aclient.Const;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.background.async.LoginAsync;

/**
 * Created by Daniel on 1/22/16.
 *
 * A leftover for pre N phones that use the old style of internet connection detection
 */
public class BackgroundManager extends BroadcastReceiver
{
	private static final String tag = "BackgroundManager";

	@Override
	public void onReceive(final Context context, Intent intent)
	{

		//after idling for a long time, Vars.applicationContext goes null sometimes
		//the show must go on
		//double check to make sure these things are setup
		Vars.applicationContext = context.getApplicationContext();

		if(Vars.uname == null || Vars.selfPrivateSodium == null || Vars.serverAddress == null)
		{
			//sometimes Vars.(prefs stuff) disappears after idling in the background for a while
			Utils.logcat(Const.LOGW, tag, "Reinitializing Vars from prefs file");
			Utils.loadPrefs();
		}

		final String action = intent.getAction();
		Utils.logcat(Const.LOGD, tag, "background manager received: " + action);

		if (!Const.NEEDS_MANUAL_INTERNET_DETECTION && action.equals(ConnectivityManager.CONNECTIVITY_ACTION))
		{
			final boolean extra = intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
			if(extra)
			{
				Utils.logcat(Const.LOGD, tag, "skipping due to legacy extra info");
				return;
			}

			if(Utils.hasInternet())
			{
				//internet reconnected case
				if(Vars.commandSocket == null)
				{
					/* For the Moto G3 on CM13.1 sometimes the cell signal dies momentarily (tower switch?) while driving
					 * and then comes back. It only announces a connectivity action when it reconnects (not when it dies).
					 * It appears the connection is still good. Don't try to relogin unless going from real no internet --> internet.
					 *
					 * Relogging in while the connections are still good and command lister is active causes undefined weird behavior.
					 * Command listenter reliably dies when there is no internet. Null command socket should be a good indicator if this
					 * is really the case of no internet --> internet.
					 */
					BackgroundManager2.getInstance().clearWaiting();

					Utils.logcat(Const.LOGD, tag, "internet was reconnected by legacy android automatic detection");
					new LoginAsync().execute();
				}
			}
		}
	}
}