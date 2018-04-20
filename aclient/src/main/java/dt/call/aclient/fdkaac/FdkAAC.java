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
	public static final String AACENC_OK = "AACENC_OK";

	//for consistency in java and C, keep the sacred numbers in 1 place
	public static native int getWavFrameSize();

	//setup for 32kbit/s stereo @ 44100hz, CBR
	public static native void initAAC();

	/**
	 * Encode raw wav into aac
	 * @param wav Raw wave audio. MUST be in stereo 44100Hz. In addition, the "correct" array size should be found out by getWavFrameSize()
	 * @param aac Encoded aac audio from the wave. Should supply a cautiously large buffer.
	 * @return Actual size of the encoded audio.
	 */
	public static native int encode(short[] wav, byte[] aac);
	private static native int getEncodeErrorRaw();
	public static String getEncoderError()
	{
		int rawError = getEncodeErrorRaw();
		switch(rawError)
		{
			case 0x0020:
			{
				return "AACENC_INVALID_HANDLE: Handle passed to function call was invalid.";
			}
			case 0x0021:
			{
				return "AACENC_MEMORY_ERROR: Memory allocation failed.";
			}
			case 0x0022:
			{
				return "AACENC_UNSUPPORTED_PARAMETER: Parameter not available.";
			}
			case 0x0023:
			{
				return "AACENC_INVALID_CONFIG: Configuration not provided.";
			}
			case 0x0040:
			{
				return "AACENC_INIT_ERROR:  General initialization error.";
			}
			case 0x0041:
			{
				return "AACENC_INIT_AAC_ERROR: AAC library initialization error.";
			}
			case 0x0042:
			{
				return "AACENC_INIT_SBR_ERROR: SBR library initialization error.";
			}
			case 0x0043:
			{
				return "AACENC_INIT_TP_ERROR: Transport library initialization error.";
			}
			case 0x0044:
			{
				return "AACENC_INIT_META_ERROR: Meta data library initialization error.";
			}
			case 0x0060:
			{
				return "AACENC_ENCODE_ERROR: The encoding process was interrupted by an unexpected error.";
			}
			case 0x0080:
			{
				return "AACENC_ENCODE_EOF: End of file reached.";
			}
		}
		return AACENC_OK;
	}
	public static native void closeEncoder();

	/**
	 * Decode aac audio into raw wave
	 * @param aac Encoded aac audio. Must be the exact size. No padding at the end.
	 * @param wav Where the raw wave will be dumped into. Should get back the size specified by getWavFrameSize()
	 * @return Decoder errors (if there are any). Otherwise 0
	 */
	public static native int decode(byte[] aac, short[] wav);
	public static native void closeDecoder();
}
