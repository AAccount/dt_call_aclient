#include <jni.h>
#include <include/opencore-amrwb/dec_if.h>

void* decInternals = NULL;

JNIEXPORT void JNICALL
Java_dt_call_aclient_amrwb_AmrWBDecoder_init(JNIEnv *env, jclass type)
{
    if(decInternals != NULL)
    {
        D_IF_exit(decInternals);
    }
    decInternals = D_IF_init();
}

JNIEXPORT void JNICALL
Java_dt_call_aclient_amrwb_AmrWBDecoder_decode(JNIEnv *env, jclass type, jbyteArray amr_, jshortArray wav_)
{
    jsize amrLen = (*env)->GetArrayLength(env, amr_);
    jbyte amrJni[amrLen];
    (*env)->GetByteArrayRegion(env, amr_, 0, amrLen, amrJni);

    jsize wavLen = (*env)->GetArrayLength(env, wav_);
    short wavJni[wavLen];

    D_IF_decode(decInternals, (const unsigned char*)amrJni, (short*)wavJni, 0);
    (*env)->SetShortArrayRegion(env, wav_, 0, wavLen, wavJni);
}

JNIEXPORT void JNICALL
Java_dt_call_aclient_amrwb_AmrWBDecoder_exit(JNIEnv *env, jclass type)
{
    D_IF_exit(decInternals);
}