package dt.call.aclient.background;

import android.app.IntentService;
import android.content.Intent;
import android.os.ParcelFileDescriptor;

import java.io.FileOutputStream;

import dt.call.aclient.Const;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;

/**
 * Created by Daniel on 1/19/16.
 * Redone February 15 2016 for pipe workaround
 */
public class PipeFromServer extends IntentService
{
	private static final String tag = "PipeFromServer";

	public PipeFromServer()
	{
		super(tag);
	}

	@Override
	protected void onHandleIntent(Intent intent)
	{
		Utils.logcat(Const.LOGD, tag, "start receiving audio from server by the pipe workaround");
		ParcelFileDescriptor pipeWriteEnd = new ParcelFileDescriptor(Vars.pipeFromServer[1]);
		FileOutputStream write2Pipe = new FileOutputStream(pipeWriteEnd.getFileDescriptor());
		byte[] buffer = new byte[Const.BUFFER_SIZE];
		try
		{
			while(true) //let it run until you kill the socket
			{
				Vars.mediaSocket.getInputStream().read(buffer);
				write2Pipe.write(buffer);
			}
		}
		catch (Exception e)
		{//most likely going to happen when you kill the socket and it panics
			Utils.logcat(Const.LOGE, tag, "something bad happaened playing back media: " + Utils.dumpException(e));
		}
		Utils.logcat(Const.LOGD, tag, "exiting the media from server thread");
	}
}
