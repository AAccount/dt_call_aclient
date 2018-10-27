package dt.call.aclient.background;

import android.app.job.JobParameters;
import android.app.job.JobService;

import dt.call.aclient.Const;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;

/**
 * Created by Daniel on 1/15/17.
 *
 * Workaround for android's CONNECTIVITY_ACTION disappearing in 7.0+
 * Recreate the same functionality by manually broadcasting a "connectivity action"
 * when the internet comes back.
 */

public class JobServiceReceiver extends JobService
{
	private static final String tag = "JobServiceReceiver";

	@Override
	public boolean onStartJob(JobParameters params)
	{
		Utils.logcat(Const.LOGD, tag, "received job");
		Vars.applicationContext = getApplicationContext();
		if(Vars.uname == null || Vars.selfPrivateSodium == null || Vars.serverAddress == null)
		{
			//sometimes Vars.(prefs stuff) disappears after idling in the background for a while
			Utils.logcat(Const.LOGW, tag, "Reinitializing Vars from prefs file");
			Utils.loadPrefs();
		}
//		Vars.applicationContext = getApplicationContext();
//		final Intent hasInternet = new Intent(Const.BROADCAST_HAS_INTERNET);
//		hasInternet.setClass(Vars.applicationContext, BackgroundManager.class);
//		sendBroadcast(hasInternet);
		BackgroundManager2.getInstance().addEvent(Const.BROADCAST_HAS_INTERNET);
		return false;
	}

	@Override
	public boolean onStopJob(JobParameters params)
	{
		return false;
	}
}
