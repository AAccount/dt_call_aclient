//
// Created by Daniel on 10/29/18.
//

#include <jni.h>
#include <string.h>
#include <android/log.h>
#include <stdbool.h>
#include <opus-1.3/include/opus.h>
#include <fcntl.h>
#include <unistd.h>
#include <malloc.h>

OpusEncoder* enc = NULL;
OpusDecoder* dec = NULL;

const char* TAG = "opus jni";
const int STEREO2CH = 2;
const int SAMPLERATE = 24000;
const int ENCODE_BITRATE = 32000;
const int NO_URANDOM = -1;
int urandom = NO_URANDOM;

const int RECOMMENDED_BUFFER_SIZE = 4000;
unsigned char* encodeOutput = NULL;
jshort* decodeOutput = NULL;

void jniApplyFiller(void* buffer, int size)
{
	const ssize_t result = read(urandom, buffer, size);
	if(result < 0)
	{
		__android_log_print(ANDROID_LOG_ERROR, TAG, "jni apply filler failed with %d, urandom %d", result, urandom);
		memset(buffer, 0, size);
	}
}

JNIEXPORT void JNICALL
Java_dt_call_aclient_codec_Opus_close(JNIEnv* env, jclass type)
{
	if(enc != NULL)
	{
		opus_encoder_destroy(enc);
		enc = NULL;
	}

	if(encodeOutput != NULL)
	{
		jniApplyFiller(encodeOutput, RECOMMENDED_BUFFER_SIZE);
		free(encodeOutput);
		encodeOutput = NULL;
	}

	if(dec != NULL)
	{
		opus_decoder_destroy(dec);
		dec = NULL;
	}

	if(decodeOutput != NULL)
	{
		jniApplyFiller(decodeOutput, sizeof(jshort) * RECOMMENDED_BUFFER_SIZE);
		free(decodeOutput);
		decodeOutput = NULL;
	}

	if(urandom != NO_URANDOM)
	{
		close(urandom);
		urandom = NO_URANDOM;
	}
}

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
	Java_dt_call_aclient_codec_Opus_close(env, type);
	if(urandom < 0)
	{
		urandom = open("/dev/urandom", O_RDONLY);
	}

	int encerror;
	enc = opus_encoder_create(SAMPLERATE, STEREO2CH, OPUS_APPLICATION_VOIP, &encerror);
	if(encerror != OPUS_OK)
	{
		__android_log_print(ANDROID_LOG_ERROR, TAG, "encoder create failed: %d", encerror);
	}
	opus_encoder_ctl(enc, OPUS_SET_BITRATE(ENCODE_BITRATE));
	encodeOutput = (unsigned char*)malloc(RECOMMENDED_BUFFER_SIZE);
	memset(encodeOutput, 0, RECOMMENDED_BUFFER_SIZE);

	int decerror;
	dec = opus_decoder_create(SAMPLERATE, STEREO2CH, &decerror);
	if(decerror != OPUS_OK)
	{
		__android_log_print(ANDROID_LOG_ERROR, TAG, "decoder create failed: %d", decerror);
	}
	const size_t decodeOutputSize = sizeof(jshort)*RECOMMENDED_BUFFER_SIZE;
	decodeOutput = (jshort*)malloc(decodeOutputSize);
	memset(decodeOutput, 0, decodeOutputSize);

}

JNIEXPORT jint JNICALL
Java_dt_call_aclient_codec_Opus_encode(JNIEnv* env, jclass type, jshortArray wav_, jbyteArray opus_)
{
	jshort* wav = (*env)->GetShortArrayElements(env, wav_, NULL);
	const jsize wavSamplesPerChannel = (*env)->GetArrayLength(env, wav_)/STEREO2CH;

	memset(encodeOutput, 0, RECOMMENDED_BUFFER_SIZE);
	const int length = opus_encode(enc, wav, wavSamplesPerChannel, encodeOutput, RECOMMENDED_BUFFER_SIZE);
	if(length > 0)
	{
		const jsize opusSize = (*env)->GetArrayLength(env, opus_);
		const int copyAmount = opusSize < length ? opusSize : length;
		(*env)->SetByteArrayRegion(env, opus_, 0, copyAmount, (jbyte*)encodeOutput);
	}

	jniApplyFiller(wav, wavSamplesPerChannel * STEREO2CH * sizeof(jshort));
	(*env)->ReleaseShortArrayElements(env, wav_, wav, 0);
	return length;
}

JNIEXPORT jint JNICALL
Java_dt_call_aclient_codec_Opus_decode(JNIEnv* env, jclass type, jbyteArray opus_, jint opusSize, jshortArray wav_)
{
	jbyte* opus = (*env)->GetByteArrayElements(env, opus_, NULL);

	const jsize totalSamples = (*env)->GetArrayLength(env, wav_);
	memset(decodeOutput, 0, sizeof(jshort)*RECOMMENDED_BUFFER_SIZE);

	const int decodedSamples = opus_decode(dec, (unsigned char*)opus, opusSize, decodeOutput, totalSamples/STEREO2CH, false)*STEREO2CH;
	if(decodedSamples > 0)
	{
		const int copyAmount = totalSamples < decodedSamples ? totalSamples : decodedSamples;
		(*env)->SetShortArrayRegion(env, wav_, 0, copyAmount, decodeOutput);
	}

	const jsize actualOpusSize = (*env)->GetArrayLength(env, opus_);
	jniApplyFiller(opus, actualOpusSize);
	(*env)->ReleaseByteArrayElements(env, opus_, opus, 0);
	return decodedSamples;
}
