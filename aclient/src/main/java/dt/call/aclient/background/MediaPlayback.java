package dt.call.aclient.background;

import android.app.IntentService;
import android.content.Intent;
import android.provider.MediaStore;

/**
 * Created by Daniel on 1/19/16.
 */
public class MediaPlayback extends IntentService
{
	private static final String tag = "MediaPlayback";

	public MediaPlayback()
	{
		super(tag);
	}

	@Override
	protected void onHandleIntent(Intent intent)
	{

	}
}
