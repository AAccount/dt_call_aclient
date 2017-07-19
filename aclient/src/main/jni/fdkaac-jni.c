#include <jni.h>
#include <include/fdk-aac/aacenc_lib.h>
#include <include/fdk-aac/aacdecoder_lib.h>
#include <android/log.h>
#include <stdlib.h>

#define TAG "aac-jni"
#define WAVFRAME_SZ 2048 //figured out by experimentation
#define TRANSPORT TT_MP4_LATM_MCP1

HANDLE_AACENCODER encInternals;
HANDLE_AACDECODER decInternals;
AACENC_InfoStruct encInfo;

JNIEXPORT jint JNICALL
Java_dt_call_aclient_fdkaac_FdkAAC_getWavFrameSize(JNIEnv *env, jclass type)
{
    return WAVFRAME_SZ;
}

JNIEXPORT void JNICALL
Java_dt_call_aclient_fdkaac_FdkAAC_initEncoder(JNIEnv *env, jclass type)
{
    uint32_t AAC_MODULE = 0x01;
    uint32_t SBR_MODULE = 0x02;
    uint32_t STEREO = 2;
    int result = aacEncOpen(&encInternals, AAC_MODULE | SBR_MODULE, STEREO);
    if(result != AACENC_OK)
    {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "problems creating encoder");
    }

    uint32_t TRUE = 1;
    result = aacEncoder_SetParam(encInternals, AACENC_AOT, AOT_ER_AAC_ELD);
    result = result + aacEncoder_SetParam(encInternals, AACENC_SAMPLERATE, 44100);
    result = result + aacEncoder_SetParam(encInternals, AACENC_BITRATE, 32000);
    result = result + aacEncoder_SetParam(encInternals, AACENC_CHANNELMODE, MODE_2);
    result = result + aacEncoder_SetParam(encInternals, AACENC_TRANSMUX, TRANSPORT); //!!!DO NOT!!! USE RAW:: IT NEVER WORKS
    result = result + aacEncoder_SetParam(encInternals, AACENC_SBR_MODE, -1);
    result = result + aacEncoder_SetParam(encInternals, AACENC_AFTERBURNER, TRUE);
    if(result != AACENC_OK) //adding any number of 0s is still 0
    {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "problems setting encoder options");
    }

    result = aacEncEncode(encInternals, NULL, NULL, NULL, NULL);
    if(result != AACENC_OK)
    {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "problems initialize encoder");
    }

    result = aacEncInfo(encInternals, &encInfo);
    if(result != AACENC_OK)
    {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "could not retrieve encoding seed information");
    }
}

JNIEXPORT jint JNICALL
Java_dt_call_aclient_fdkaac_FdkAAC_encode(JNIEnv *env, jclass type, jshortArray wav_, jbyteArray aac_)
{
    jshort *wav = (*env)->GetShortArrayElements(env, wav_, NULL);

    AACENC_BufDesc input;
    int inputIdentifier = IN_AUDIO_DATA;
    int wavSamples = (*env)->GetArrayLength(env, wav_);
    int wavBytes = wavSamples*2;
    int inputSampleSize = 2;
    input.numBufs=1;
    input.bufs = (void**)&wav;
    input.bufferIdentifiers = &inputIdentifier;
    input.bufSizes = &wavBytes;
    input.bufElSizes = &inputSampleSize;

    AACENC_InArgs inArgs;
    inArgs.numInSamples = wavSamples;

    AACENC_BufDesc output;
    int outputIdenfitier = OUT_BITSTREAM_DATA;
    int outputBytes = 10000;
    jbyte  *outputBuffer = malloc(outputBytes);
    int outputSampleSize = 1;
    output.numBufs = 1;
    output.bufs = (void**)&outputBuffer;
    output.bufferIdentifiers = &outputIdenfitier;
    output.bufSizes = &outputBytes;
    output.bufElSizes = &outputSampleSize;
    AACENC_OutArgs outArgs;

    int result = aacEncEncode(encInternals, &input, &output, &inArgs, &outArgs);
    (*env)->SetByteArrayRegion(env, aac_, 0, outArgs.numOutBytes, outputBuffer);

    (*env)->ReleaseShortArrayElements(env, wav_, wav, 0);
    free(outputBuffer);
    return outArgs.numOutBytes;
}

JNIEXPORT void JNICALL
Java_dt_call_aclient_fdkaac_FdkAAC_closeEncoder(JNIEnv *env, jclass type)
{
    aacEncClose(&encInternals);
}

JNIEXPORT void JNICALL
Java_dt_call_aclient_fdkaac_FdkAAC_initDecoder(JNIEnv *env, jclass type)
{
    decInternals = aacDecoder_Open(TRANSPORT, 1);

    //it's the same program, compiled with the same static library with the same jni on both ends.
    // ok to cheat a little and use your own AACENC_InfoStruct for somebody else's voice
    UCHAR *info = (UCHAR*)&encInfo;
    UINT infoSz = sizeof(AACENC_InfoStruct);
    aacDecoder_ConfigRaw(decInternals, &info, &infoSz);
}

JNIEXPORT jint JNICALL
Java_dt_call_aclient_fdkaac_FdkAAC_decode(JNIEnv *env, jclass type, jbyteArray aac_, jshortArray wav_)
{
    jbyte *aac = (*env)->GetByteArrayElements(env, aac_, NULL);
    UINT aacSize = (UINT)(*env)->GetArrayLength(env, aac_);
    UINT valid = aacSize;
    UCHAR *caac = (UCHAR*)aac;
    aacDecoder_Fill(decInternals, &caac, &aacSize, &valid);

    short *wavJni = malloc(sizeof(int16_t)*WAVFRAME_SZ);
    int result = aacDecoder_DecodeFrame(decInternals, wavJni, WAVFRAME_SZ*2, 0);
    (*env)->SetShortArrayRegion(env, wav_, 0, WAVFRAME_SZ, wavJni);

    free(wavJni);
    (*env)->ReleaseByteArrayElements(env, aac_, aac, 0);
    return result;
}

JNIEXPORT void JNICALL
Java_dt_call_aclient_fdkaac_FdkAAC_closeDecoder(JNIEnv *env, jclass type)
{
    aacDecoder_Close(decInternals);
}