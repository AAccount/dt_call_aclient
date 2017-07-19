package dt.call.aclient.fdkaac;

/**
 * Created by Daniel on 7/15/17.
 */

public class FdkAAC
{
	static
	{
		System.loadLibrary("fdkaac-aclient");
	}

	//for consistency in java and C, keep the scared numbers in 1 place
	public static native int getWavFrameSize();

	//setup for 32kbit/s stereo @ 44100hz, CBR
	public static native void initEncoder();
	/**
	 * Encode raw wav into aac
	 * @param wav Raw wave audio. MUST be in stereo 44100Hz. In addition, the "correct" array size should be found out by getWavFrameSize()
	 * @param aac Encoded aac audio from the wave. Should supply a cautiously large buffer.
	 * @return Actual size of the encoded audio.
	 */
	public static native int encode(short[] wav, byte[] aac);
	public static native void closeEncoder();

	public static native void initDecoder();
	/**
	 * Decode aac audio into raw wave
	 * @param aac Encoded aac audio. Must be the exact size. No padding at the end.
	 * @param wav Where the raw wave will be dumped into. Should get back the size specified by getWavFrameSize()
	 * @return Decoder errors (if there are any). Otherwise 0
	 */
	public static native int decode(byte[] aac, short[] wav);
	public static native void closeDecoder();
}
