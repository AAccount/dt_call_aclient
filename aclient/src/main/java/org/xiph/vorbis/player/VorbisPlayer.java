package org.xiph.vorbis.player;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Process;

import org.xiph.vorbis.decoder.DecodeFeed;
import org.xiph.vorbis.decoder.DecodeStreamInfo;
import org.xiph.vorbis.decoder.VorbisDecoder;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;

import dt.call.aclient.Const;
import dt.call.aclient.Utils;

/**
 * The VorbisPlayer is responsible for decoding a vorbis bitsream into raw PCM data to play to an {@link AudioTrack}
 * <p/>
 * <p/>
 * <p/>
 * This class is primarily intended as a demonstration of how to work with the JNI java interface {@link VorbisDecoder}
 * <p/>
 * User: vincent
 * Date: 3/28/13
 * Time: 10:17 AM
 *
 * Modified for aclient to change handler messages to logcat outputs.
 * Modify the logcat output to use Utils.logcat to cut down on logcat spam when not debugging
 */
public class VorbisPlayer implements Runnable
{
	/**
	 * Playing state which can either be stopped, playing, or reading the header before playing
	 */
	private static enum PlayerState
	{
		PLAYING, STOPPED, READING_HEADER, BUFFERING
	}

	/**
	 * Logging tag
	 */
	private static final String TAG = "VorbisPlayer";

	/**
	 * The decode feed to read and write pcm/vorbis data respectively
	 */
	private final DecodeFeed decodeFeed;

	/**
	 * Current state of the vorbis player
	 */
	private AtomicReference<PlayerState> currentState = new AtomicReference<PlayerState>(PlayerState.STOPPED);

	/**
	 * Custom class to easily decode from a file and write to an {@link AudioTrack}
	 */
	private class FileDecodeFeed implements DecodeFeed
	{
		/**
		 * The audio track to write the raw pcm bytes to
		 */
		private AudioTrack audioTrack;

		/**
		 * The input stream to decode from
		 */
		private InputStream inputStream;
		/**
		 * The file to decode ogg/vorbis data from
		 */
		private final File fileToDecode;

		/**
		 * Creates a decode feed that reads from a file and writes to an {@link AudioTrack}
		 *
		 * @param fileToDecode the file to decode
		 */
		private FileDecodeFeed(File fileToDecode) throws FileNotFoundException
		{
			if (fileToDecode == null)
			{
				throw new IllegalArgumentException("File to decode must not be null.");
			}
			this.fileToDecode = fileToDecode;
		}

		@Override
		public synchronized int readVorbisData(byte[] buffer, int amountToWrite)
		{
			//If the player is not playing or reading the header, return 0 to end the native decode method
			if (currentState.get() == PlayerState.STOPPED)
			{
				return 0;
			}

			//Otherwise read from the file
			try
			{
				int read = inputStream.read(buffer, 0, amountToWrite);
				return read == -1 ? 0 : read;
			}
			catch (IOException e)
			{
				//There was a problem reading from the file
				Utils.logcat(Const.LOGE, TAG, "Failed to read vorbis data from file.  Aborting: ");
				Utils.dumpException(TAG, e);
				stop();
				return 0;
			}
		}

		@Override
		public synchronized void writePCMData(short[] pcmData, int amountToRead)
		{
			//If we received data and are playing, write to the audio track
			if (pcmData != null && amountToRead > 0 && audioTrack != null && isPlaying())
			{
				audioTrack.write(pcmData, 0, amountToRead);
			}
		}

		@Override
		public void stop()
		{
			if (isPlaying() || isReadingHeader())
			{
				//Closes the file input stream
				if (inputStream != null)
				{
					try
					{
						inputStream.close();
					}
					catch (IOException e)
					{
						Utils.logcat(Const.LOGE, TAG, "Failed to close file input stream: ");
						Utils.dumpException(TAG, e);
					}
					inputStream = null;
				}

				//Stop the audio track
				if (audioTrack != null)
				{
					audioTrack.stop();
					audioTrack.release();
					audioTrack = null;
				}
			}

			//Set our state to stopped
			currentState.set(PlayerState.STOPPED);
		}

		@Override
		public void start(DecodeStreamInfo decodeStreamInfo)
		{
			if (currentState.get() != PlayerState.READING_HEADER)
			{
				throw new IllegalStateException("Must read header first!");
			}
			if (decodeStreamInfo.getChannels() != 1 && decodeStreamInfo.getChannels() != 2)
			{
				throw new IllegalArgumentException("Channels can only be one or two");
			}
			if (decodeStreamInfo.getSampleRate() <= 0)
			{
				throw new IllegalArgumentException("Invalid sample rate, must be above 0");
			}

			//Create the audio track
			int channelConfiguration = decodeStreamInfo.getChannels() == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
			int minSize = AudioTrack.getMinBufferSize((int) decodeStreamInfo.getSampleRate(), channelConfiguration, AudioFormat.ENCODING_PCM_16BIT);
			audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, (int) decodeStreamInfo.getSampleRate(), channelConfiguration, AudioFormat.ENCODING_PCM_16BIT, minSize, AudioTrack.MODE_STREAM);
			audioTrack.play();

			//We're starting to read actual content
			currentState.set(PlayerState.PLAYING);
		}

		@Override
		public void startReadingHeader()
		{
			if (inputStream == null && isStopped())
			{
				Utils.logcat(Const.LOGD, TAG, "playing started");
				try
				{
					inputStream = new BufferedInputStream(new FileInputStream(fileToDecode));
					currentState.set(PlayerState.READING_HEADER);
				}
				catch (FileNotFoundException e)
				{
					Utils.logcat(Const.LOGE, TAG, "Failed to find file to decode: ");
					Utils.dumpException(TAG, e);
					stop();
				}
			}
		}

	}

	/**
	 * Custom class to easily buffer and decode from a stream and write to an {@link AudioTrack}
	 */
	private class BufferedDecodeFeed implements DecodeFeed
	{
		/**
		 * The audio track to write the raw pcm bytes to
		 */
		private AudioTrack audioTrack;

		/**
		 * The initial buffer size
		 */
		private final long bufferSize;

		/**
		 * The input stream to decode from
		 */
		private InputStream inputStream;

		/**
		 * The amount of written pcm data to the audio track
		 */
		private long writtenPCMData = 0;

		/**
		 * Creates a decode feed that reads from a file and writes to an {@link AudioTrack}
		 *
		 * @param streamToDecode the stream to decode
		 */
		private BufferedDecodeFeed(InputStream streamToDecode, long bufferSize)
		{
			if (streamToDecode == null)
			{
				throw new IllegalArgumentException("Stream to decode must not be null.");
			}
			this.inputStream = streamToDecode;
			this.bufferSize = bufferSize;
		}

		@Override
		public int readVorbisData(byte[] buffer, int amountToWrite)
		{
			//If the player is not playing or reading the header, return 0 to end the native decode method
			if (currentState.get() == PlayerState.STOPPED)
			{
				return 0;
			}

			//Otherwise read from the file
			try
			{
				Utils.logcat(Const.LOGD, TAG, "Reading...");
				int read = inputStream.read(buffer, 0, amountToWrite);
				Utils.logcat(Const.LOGD, TAG, "Read...");
				return read == -1 ? 0 : read;
			}
			catch (IOException e)
			{
				//There was a problem reading from the file
				Utils.logcat(Const.LOGE, TAG, "Failed to read vorbis data from file.  Aborting: ");
				Utils.dumpException(TAG, e);
				return 0;
			}
		}

		@Override
		public void writePCMData(short[] pcmData, int amountToRead)
		{
			//If we received data and are playing, write to the audio track
			Utils.logcat(Const.LOGD, TAG, "Writing data to track");
			if (pcmData != null && amountToRead > 0 && audioTrack != null && (isPlaying() || isBuffering()))
			{
				audioTrack.write(pcmData, 0, amountToRead);
				writtenPCMData += amountToRead;
				if (writtenPCMData >= bufferSize)
				{
					audioTrack.play();
					currentState.set(PlayerState.PLAYING);
				}
			}
		}

		@Override
		public void stop()
		{
			if (!isStopped())
			{
				//If we were in a state of buffering before we actually started playing, start playing and write some silence to the track
				if (currentState.get() == PlayerState.BUFFERING)
				{
					audioTrack.play();
					audioTrack.write(new byte[20000], 0, 20000);
				}

				//Closes the file input stream
				if (inputStream != null)
				{
					try
					{
						inputStream.close();
					}
					catch (IOException e)
					{
						Utils.logcat(Const.LOGE, TAG, "Failed to close file input stream: ");
						Utils.dumpException(TAG, e);
					}
					inputStream = null;
				}

				//Stop the audio track
				if (audioTrack != null)
				{
					audioTrack.stop();
					audioTrack.release();
					audioTrack = null;
				}
			}

			//Set our state to stopped
			currentState.set(PlayerState.STOPPED);
		}

		@Override
		public void start(DecodeStreamInfo decodeStreamInfo)
		{
			if (currentState.get() != PlayerState.READING_HEADER)
			{
				throw new IllegalStateException("Must read header first!");
			}
			if (decodeStreamInfo.getChannels() != 1 && decodeStreamInfo.getChannels() != 2)
			{
				throw new IllegalArgumentException("Channels can only be one or two");
			}
			if (decodeStreamInfo.getSampleRate() <= 0)
			{
				throw new IllegalArgumentException("Invalid sample rate, must be above 0");
			}

			//Create the audio track
			int channelConfiguration = decodeStreamInfo.getChannels() == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
			int minSize = AudioTrack.getMinBufferSize((int) decodeStreamInfo.getSampleRate(), channelConfiguration, AudioFormat.ENCODING_PCM_16BIT);
			audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, (int) decodeStreamInfo.getSampleRate(), channelConfiguration, AudioFormat.ENCODING_PCM_16BIT, minSize, AudioTrack.MODE_STREAM);
			audioTrack.play();

			//We're starting to read actual content
			currentState.set(PlayerState.BUFFERING);
		}

		@Override
		public void startReadingHeader()
		{
			if (isStopped())
			{
				Utils.logcat(Const.LOGD, TAG, "playing started");
				currentState.set(PlayerState.READING_HEADER);
			}
		}

	}

	/**
	 * Constructs a new instance of the player with default parameters other than it will decode from a file
	 *
	 * @param fileToPlay the file to play
	 * @throws FileNotFoundException thrown if the file could not be located/opened to playing
	 */
	public VorbisPlayer(File fileToPlay) throws FileNotFoundException
	{
		if (fileToPlay == null)
		{
			throw new IllegalArgumentException("File to play must not be null.");
		}
		this.decodeFeed = new FileDecodeFeed(fileToPlay);
	}

	/**
	 * Constructs a player that will read from an {@link InputStream} and write to an {@link AudioTrack}
	 *
	 * @param audioDataStream the audio data stream to read from
	 */
	public VorbisPlayer(InputStream audioDataStream)
	{
		if (audioDataStream == null)
		{
			throw new IllegalArgumentException("Input stream must not be null.");
		}
		this.decodeFeed = new BufferedDecodeFeed(audioDataStream, 24000);
	}

	/**
	 * Constructs a player with a custom {@link DecodeFeed}
	 *
	 * @param decodeFeed the custom decode feed
	 */
	public VorbisPlayer(DecodeFeed decodeFeed)
	{
		if (decodeFeed == null)
		{
			throw new IllegalArgumentException("Decode feed must not be null.");
		}
		this.decodeFeed = decodeFeed;
	}

	/**
	 * Starts the audio recorder with a given sample rate and channels
	 */
	@SuppressWarnings("all")
	public synchronized void start()
	{
		if (isStopped())
		{
			new Thread(this).start();
		}
	}

	/**
	 * Stops the player and notifies the decode feed
	 */
	public synchronized void stop()
	{
		decodeFeed.stop();
	}

	@Override
	public void run()
	{
		//Start the native decoder
		android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
		int result = VorbisDecoder.startDecoding(decodeFeed);
		switch (result)
		{
			case DecodeFeed.SUCCESS:
				Utils.logcat(Const.LOGE, TAG, "Successfully finished decoding");
				break;
			case DecodeFeed.INVALID_OGG_BITSTREAM:
				Utils.logcat(Const.LOGE, TAG, "Invalid ogg bitstream error received");
				break;
			case DecodeFeed.ERROR_READING_FIRST_PAGE:
				Utils.logcat(Const.LOGE, TAG, "Error reading first page error received");
				break;
			case DecodeFeed.ERROR_READING_INITIAL_HEADER_PACKET:
				Utils.logcat(Const.LOGE, TAG, "Error reading initial header packet error received");
				break;
			case DecodeFeed.NOT_VORBIS_HEADER:
				Utils.logcat(Const.LOGE, TAG, "Not a vorbis header error received");
				break;
			case DecodeFeed.CORRUPT_SECONDARY_HEADER:
				Utils.logcat(Const.LOGE, TAG, "Corrupt secondary header error received");
				break;
			case DecodeFeed.PREMATURE_END_OF_FILE:
				Utils.logcat(Const.LOGE, TAG, "Premature end of file error received");
				break;
		}
	}

	/**
	 * Checks whether the player is currently playing
	 *
	 * @return <code>true</code> if playing, <code>false</code> otherwise
	 */
	public synchronized boolean isPlaying()
	{
		return currentState.get() == PlayerState.PLAYING;
	}

	/**
	 * Checks whether the player is currently stopped (not playing)
	 *
	 * @return <code>true</code> if playing, <code>false</code> otherwise
	 */
	public synchronized boolean isStopped()
	{
		return currentState.get() == PlayerState.STOPPED;
	}

	/**
	 * Checks whether the player is currently reading the header
	 *
	 * @return <code>true</code> if reading the header, <code>false</code> otherwise
	 */
	public synchronized boolean isReadingHeader()
	{
		return currentState.get() == PlayerState.READING_HEADER;
	}

	/**
	 * Checks whether the player is currently buffering
	 *
	 * @return <code>true</code> if buffering, <code>false</code> otherwise
	 */
	public synchronized boolean isBuffering()
	{
		return currentState.get() == PlayerState.BUFFERING;
	}
}
