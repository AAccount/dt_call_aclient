package dt.call.aclient.codec;

public class Opus
{
	static
	{
		System.loadLibrary("opus-aclient");
	}

	/**
	 * Find out how many frames/samples (both L+R channel) you need to give for the codec to work
	 * @return the required frame size for encoding and decoding to work
	 */
	public static native int getWavFrameSize();

	/**
	 * Find out the sample rate required for the codec to work
	 * @return the required sample rate
	 */
	public static native int getSampleRate();

	public static native void init();

	/**
	 * Encode raw wav into opus
	 * @param wav Raw wave audio. MUST be in stereo 24000Hz. In addition, the "correct" array size should be found out by getWavFrameSize()
	 * @param opus Encoded opus audio from the wave. Should supply a cautiously large buffer.
	 * @return Actual size of the encoded audio.
	 */
	public static native int encode(short[] wav, byte[] opus);
	public static native void closeEncoder();

	/**
	 * Decode opus audio into raw wave
	 * @param opus Encoded opus audio. Must be the exact size. No padding at the end.
	 * @param opusSize How much of the opus byte array is useable data
	 * @param wav Where the raw wave will be dumped into. Should get back the size specified by getWavFrameSize()
	 * @return Decoder errors (if there are any). Otherwise 0
	 */
	public static native int decode(byte[] opus, int opusSize, short[] wav);
	public static native void closeDecoder();

	public static String getError(int error)
	{
		switch(error)
		{
			case -1: return "OPUS_BAD_ARG: One or more invalid/out of range arguments";
			case -2: return "OPUS_BUFFER_TOO_SMALL: Not enough bytes allocated in the buffer";
			case -3: return "OPUS_INTERNAL_ERROR: An internal error was detected";
			case -4: return "OPUS_INVALID_PACKET: The compressed data passed is corrupted";
			case -5: return "OPUS_UNIMPLEMENTED: Invalid/unsupported request number";
			case -6: return "OPUS_INVALID_STATE: An encoder or decoder structure is invalid or already freed";
			case -7: return "OPUS_ALLOC_FAIL: Memory allocation has failed";
		}
		return "invalid opus error of:" + error;
	}
}