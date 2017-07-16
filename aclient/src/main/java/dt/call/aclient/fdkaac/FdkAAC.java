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
	public static native int encode(short[] wav, byte[] aac);
	public static native void closeEncoder();

	public static native void initDecoder();
	public static native int decode(byte[] aac, short[] wav);
	public static native void closeDecoder();
}
