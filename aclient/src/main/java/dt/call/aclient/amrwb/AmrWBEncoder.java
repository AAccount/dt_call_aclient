package dt.call.aclient.amrwb;

/**
 * Created by Daniel on 7/12/17.
 */

public class AmrWBEncoder
{
	static
	{
		System.loadLibrary("amrwb-enc");
	}

	//bitrate hardcoded to the highest 23.85kbit/s

	public static native void init();

	/**
	 * Encodes a chunk of raw wav into amr.
	 * You MUST feed in 320 shorts (which produces 61 bytes).
	 * 320 shorts is a SACRED amount. DO NOT violate it!!!
	 * @param wav raw wave data to be turned into amr wideband
	 * @param amr buffer to store the encoded amr
	 * @return amount of bytes the encoded wav produces
	 */
	public static native int encode(short[] wav, byte[] amr);
	public static native void exit();
}
