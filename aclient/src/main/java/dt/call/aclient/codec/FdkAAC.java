package dt.call.aclient.codec;

public class FdkAAC
{
	static
	{
		System.loadLibrary("fdkaac-aclient");
	}

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

	/**
	 * Gets the error from AACENC_ERROR aacEncEncode(
	 const HANDLE_AACENCODER   hAacEncoder,
	 const AACENC_BufDesc     *inBufDesc,
	 const AACENC_BufDesc     *outBufDesc,
	 const AACENC_InArgs      *inargs,
	 AACENC_OutArgs           *outargs
	 );
	 * @return AACENC_ERROR error code
	 */
	public static native int getEncodeError();
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
