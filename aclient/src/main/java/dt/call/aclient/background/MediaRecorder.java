package dt.call.aclient.background;

import android.app.IntentService;
import android.content.Intent;

/**
 * Created by Daniel on 1/19/16.
 */
public class MediaRecorder extends IntentService
{
	private static final String tag = "MediaPlayback";

	public MediaRecorder()
	{
		super(tag);
	}

	@Override
	protected void onHandleIntent(Intent intent)
	{

	}
}
