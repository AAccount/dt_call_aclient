package dt.call.aclient.background;

import android.app.IntentService;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.MediaRecorder;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;

import dt.call.aclient.Const;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;

/**
 * Created by Daniel on 1/19/16.
 */
public class Media2Server extends IntentService
{
	private static final String tag = "Media2Server";
	private static final int MONO = 1; //it's voice data... stereo is overkill
	private static final int BITRATE = 96*1000; //2010 self test couldn't tell above 96kbps

	public Media2Server()
	{
		super(tag);
	}

	@Override
	protected void onHandleIntent(Intent intent)
	{
		Utils.logcat(Const.LOGD, tag, "start sending audio to server");
		try
		{
			//https://stackoverflow.com/questions/5287798/live-video-stream-between-two-android-phones
			if(Vars.mediaRecorder == null)
			{
				Vars.mediaRecorder = new MediaRecorder();
				Vars.mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
				Vars.mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.VORBIS);
				Vars.mediaRecorder.setAudioChannels(MONO);
				Vars.mediaRecorder.setAudioEncodingBitRate(BITRATE);
				Vars.mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.WEBM);
			}

			//always reset the file descriptor in case there was a reconnection and the old fd is no good
			ParcelFileDescriptor pfd = ParcelFileDescriptor.fromSocket(Vars.mediaSocket);
			Vars.mediaRecorder.setOutputFile(pfd.getFileDescriptor());
			Vars.mediaRecorder.prepare();
			Vars.mediaRecorder.start();
		}
		catch (Exception e)
		{
			Utils.logcat(Const.LOGE, tag, "can't send media to the server anymore: " + e.getCause());
			Vars.mediaRecorder.stop();
			Vars.mediaRecorder.reset();
		}
		Utils.logcat(Const.LOGD, tag, "stop sending media to the server and leave this intent service");
	}
}
