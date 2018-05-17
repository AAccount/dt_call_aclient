package dt.call.aclient.background;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;

import dt.call.aclient.Const;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;

/**
 * Created by Daniel on 1/15/17.
 *
 * Workaround for android's CONNECTIVITY_ACTION disappearing in 7.0+
 * Recreate the same functionality by manually boradcasting a "connectivity action"
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
		final Intent hasInternet = new Intent(Const.BROADCAST_HAS_INTERNET);
		hasInternet.setClass(Vars.applicationContext, BackgroundManager.class);
		sendBroadcast(hasInternet);
		return false;
	}

	@Override
	public boolean onStopJob(JobParameters params)
	{
		return false;
	}
}
