package dt.call.aclient.background;

import android.app.IntentService;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;

import dt.call.aclient.Const;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;

/**
 * Created by Daniel on 1/19/16.
 */
public class MediaFromServer extends IntentService
{
	private static final String tag = "MediaFromServer";

	public MediaFromServer()
	{
		super(tag);
	}

	@Override
	protected void onHandleIntent(Intent intent)
	{
		Utils.logcat(Const.LOGD, tag, "start receiving audio from server");
		try
		{
			if(Vars.mediaPlayer == null)
			{
				Vars.mediaPlayer = new MediaPlayer();
			}
			ParcelFileDescriptor pfd = ParcelFileDescriptor.fromSocket(Vars.mediaSocket);
			Vars.mediaPlayer.setDataSource(pfd.getFileDescriptor());
			Vars.mediaPlayer.prepare();
			Vars.mediaPlayer.start();
		}
		catch (Exception e)
		{
			Utils.logcat(Const.LOGE, tag, "something bad happaened playing back media: " + e.getCause());
			Vars.mediaPlayer.stop();
			Vars.mediaPlayer.reset();
		}
		Utils.logcat(Const.LOGD, tag, "exiting the media from server thread");
	}
}
