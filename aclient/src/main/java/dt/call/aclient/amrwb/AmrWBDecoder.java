package dt.call.aclient.amrwb;

/**
 * Created by Daniel on 7/11/17.
 */

public class AmrWBDecoder
{
	static
	{
		System.loadLibrary("amrwb-dec");
	}

	public static native void init();
	public static native void decode(byte[] amr, short[] wav);
	public static native void exit();
}
