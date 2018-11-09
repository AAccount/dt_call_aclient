//
// Created by Daniel on 10/29/18.
//

#include <jni.h>
#include <string.h>
#include <android/log.h>
#include <stdbool.h>
#include <alloca.h>
#include <opus-1.3/include/opus.h>

OpusEncoder* enc = NULL;
OpusDecoder* dec = NULL;

const char* TAG = "opus jni";
const int STEREO2CH = 2;
const int SAMPLERATE = 24000;
const int ENCODE_BITRATE = 32000;

JNIEXPORT jint JNICALL
Java_dt_call_aclient_codec_Opus_getWavFrameSize(JNIEnv* env, jclass type)
{
    //legal values: 120, 240, 480, 960, 1920, and 2880
    return 2880;
}

JNIEXPORT jint JNICALL
Java_dt_call_aclient_codec_Opus_getSampleRate(JNIEnv *env, jclass type)
{
    return SAMPLERATE;
}

JNIEXPORT void JNICALL
Java_dt_call_aclient_codec_Opus_init(JNIEnv* env, jclass type)
{
    if(enc != NULL)
    {
        opus_encoder_destroy(enc);
    }
    int encerror;
    enc = opus_encoder_create(SAMPLERATE, STEREO2CH, OPUS_APPLICATION_VOIP, &encerror);
    if(encerror != OPUS_OK)
    {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "encoder create failed: %d", encerror);
    }
    opus_encoder_ctl(enc, OPUS_SET_BITRATE(ENCODE_BITRATE));

    if(dec != NULL)
    {
        opus_decoder_destroy(dec);
    }
    int decerror;
    dec = opus_decoder_create(SAMPLERATE, STEREO2CH, &decerror);
    if(decerror != OPUS_OK)
    {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "decoder create failed: %d", decerror);
    }

}

JNIEXPORT jint JNICALL
Java_dt_call_aclient_codec_Opus_encode(JNIEnv* env, jclass type, jshortArray wav_, jbyteArray opus_)
{
    jshort* wav = (*env)->GetShortArrayElements(env, wav_, NULL);
    const jsize wavSamplesPerChannel = (*env)->GetArrayLength(env, wav_)/STEREO2CH;

    const int RECOMMENDED_BUFFER_SIZE = 4000;
    unsigned char* output = (unsigned char*)alloca(RECOMMENDED_BUFFER_SIZE);
    const int length = opus_encode(enc, wav, wavSamplesPerChannel, output, RECOMMENDED_BUFFER_SIZE);
    if(length > 0)
    {
        const jsize opusSize = (*env)->GetArrayLength(env, opus_);
        const int copyAmount = opusSize < length ? opusSize : length;
        (*env)->SetByteArrayRegion(env, opus_, 0, copyAmount, (jbyte*)output);
    }

    memset(wav, 0, (size_t)wavSamplesPerChannel*STEREO2CH);
    memset(output, 0, RECOMMENDED_BUFFER_SIZE);
    (*env)->ReleaseShortArrayElements(env, wav_, wav, 0);
    return length;
}

JNIEXPORT void JNICALL
Java_dt_call_aclient_codec_Opus_closeEncoder(JNIEnv* env, jclass type)
{
    opus_encoder_destroy(enc);
    enc = NULL;
}

JNIEXPORT jint JNICALL
Java_dt_call_aclient_codec_Opus_decode(JNIEnv* env, jclass type, jbyteArray opus_, jint opusSize, jshortArray wav_)
{
    jbyte* opus = (*env)->GetByteArrayElements(env, opus_, NULL);

    const jsize totalSamples = (*env)->GetArrayLength(env, wav_);
    jshort* output = (jshort*)alloca(totalSamples*sizeof(jshort));

    const int decodedSamples = opus_decode(dec, (unsigned char*)opus, opusSize, output, totalSamples/STEREO2CH, false)*STEREO2CH;
    if(decodedSamples > 0)
    {
        const int copyAmount = totalSamples < decodedSamples ? totalSamples : decodedSamples;
        (*env)->SetShortArrayRegion(env, wav_, 0, copyAmount, output);
    }

    memset(opus, 0, (size_t)opusSize); //unused part of the array will already be zeroed
    memset(output, 0, totalSamples*sizeof(jshort));
    (*env)->ReleaseByteArrayElements(env, opus_, opus, 0);
    return decodedSamples;
}

JNIEXPORT void JNICALL
Java_dt_call_aclient_codec_Opus_closeDecoder(JNIEnv* env, jclass type)
{
    opus_decoder_destroy(dec);
    dec = NULL;
}