package dt.call.aclient.Voip;
/**
 * Created by Daniel on 12/22/19.
 *
 */
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Vibrator;

import java.io.InputStream;

import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.codec.Opus;

public class SoundEffects
{
	private static final String tag = "Voip.SoundEffects";
	private static final int DIAL_TONE_SIZE = 32000;
	private static final int END_TONE_SIZE = 10858;
	private static final int WAV_FILE_HEADER = 44; //.wav files actually have a 44 byte header

	private static final int S16 = AudioFormat.ENCODING_PCM_16BIT;
	private static final int STREAMCALL = AudioManager.STREAM_VOICE_CALL;

	private AudioManager audioManager;
	private AudioTrack dialTone = null;
	private boolean playedEndTone=false;

	private Ringtone ringtone = null;
	private Vibrator vibrator = null;

	private static SoundEffects instance = null;
	public static SoundEffects getInstance()
	{
		if(instance == null)
		{
			instance = new SoundEffects();
		}
		return instance;
	}

	private SoundEffects()
	{
		audioManager = (AudioManager) Vars.applicationContext.getSystemService(Context.AUDIO_SERVICE);
	}

	public void playDialtone()
	{
		audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
		audioManager.setSpeakerphoneOn(false);
		byte[] dialToneDump = new byte[DIAL_TONE_SIZE]; //right click the file and get the exact size
		try
		{
			final InputStream dialToneStream = Vars.applicationContext.getResources().openRawResource(R.raw.dialtone8000);
			final int amount = dialToneStream.read(dialToneDump);
			final int actualSize = amount-WAV_FILE_HEADER;
			dialToneStream.close();

			//only create the dial tone audio track if it's needed. otherwise it leaks resources if you create but never use it
			dialTone = new AudioTrack(STREAMCALL, 8000, AudioFormat.CHANNEL_OUT_MONO, S16, DIAL_TONE_SIZE, AudioTrack.MODE_STATIC);
			dialTone.write(dialToneDump, WAV_FILE_HEADER, actualSize);
			dialTone.setLoopPoints(0, actualSize/2, -1);
			dialTone.play();
		}
		catch(Exception e)
		{
			Utils.dumpException(tag, e);
		}
	}

	public void stopDialtone()
	{
		if(dialTone != null)
		{
			dialTone.pause();
			dialTone.flush();
			dialTone.stop();
			dialTone.release();
			dialTone = null;
		}
	}

	public void playRingtone()
	{
		switch(audioManager.getRingerMode())
		{
			case AudioManager.RINGER_MODE_NORMAL:
				Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
				ringtone = RingtoneManager.getRingtone(Vars.applicationContext, ringtoneUri);
				ringtone.play();
				break;
			case AudioManager.RINGER_MODE_VIBRATE:
				vibrator = (Vibrator)Vars.applicationContext.getSystemService(Context.VIBRATOR_SERVICE);
				if(vibrator == null)
				{
					break;
				}
				final long[] vibratePattern = new long[] {0, 400, 200};
				vibrator.vibrate(vibratePattern, 0);
				break;
			//no need for the dead silent case. if it is dead silent just light up the screen with no nothing
		}
	}

	public void stopRingtone()
	{
		if(ringtone != null && ringtone.isPlaying())
		{
			ringtone.stop();
			ringtone = null;
		}

		if(vibrator != null)
		{
			vibrator.cancel();
			vibrator = null;
		}
	}

	public void playEndTone()
	{
		if(!playedEndTone)
		{
			playedEndTone = true; //actually played twice: once for each media send and once for media receive's call to onStop
			try
			{
				final byte[] endToneDump = new byte[END_TONE_SIZE]; //right click the file and get the exact size
				final InputStream endToneStream = Vars.applicationContext.getResources().openRawResource(R.raw.end8000);
				final int amount = endToneStream.read(endToneDump);
				final int actualSize = amount - WAV_FILE_HEADER;
				endToneStream.close();

				final AudioTrack endTonePlayer = new AudioTrack(STREAMCALL, 8000, AudioFormat.CHANNEL_OUT_MONO, S16, actualSize, AudioTrack.MODE_STATIC);
				endTonePlayer.write(endToneDump, WAV_FILE_HEADER, actualSize);
				endTonePlayer.play();
				int playbackPos = endTonePlayer.getPlaybackHeadPosition();
				while(playbackPos < actualSize/2)
				{
					playbackPos = endTonePlayer.getPlaybackHeadPosition();
				}
				endTonePlayer.pause();
				endTonePlayer.flush();
				endTonePlayer.stop();
				endTonePlayer.release();
			}
			catch(Exception e)
			{
				//nothing useful you can do if the notification end tone fails to play
			}
		}
	}
}
