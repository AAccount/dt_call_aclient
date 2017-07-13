#include <jni.h>
#include <include/vo-amrwbenc/enc_if.h>

void* encInternals = NULL; //internal structure used by wideband encoder

JNIEXPORT void JNICALL
Java_dt_call_aclient_amrwb_AmrWBEncoder_init(JNIEnv *env, jclass type) {

    if(encInternals != NULL) //just in case to prevent memory leaks
    {
        E_IF_exit(encInternals);
    }
    encInternals = E_IF_init();
}

JNIEXPORT jint JNICALL
Java_dt_call_aclient_amrwb_AmrWBEncoder_encode(JNIEnv *env, jclass type, jshortArray wav_, jbyteArray amr_)
{
    jsize wavLen = (*env)->GetArrayLength(env, wav_);
    jshort wavJni[wavLen]; //if this isn't 320, you will get corrupted frames which sound like angry morse code
    (*env)->GetShortArrayRegion(env, wav_, 0, wavLen, wavJni);

    jsize amrLen = (*env)->GetArrayLength(env, amr_);
    jbyte amrJni[amrLen];

    int VOAMRWB_MD2385 = 8;
    jint length = E_IF_encode(encInternals, VOAMRWB_MD2385, (const short*)wavJni, (unsigned char*)amrJni, 0);
    (*env)->SetByteArrayRegion(env, amr_, 0, amrLen, amrJni);

    return length; //no surprise it's 61
}

JNIEXPORT void JNICALL
Java_dt_call_aclient_amrwb_AmrWBEncoder_exit(JNIEnv *env, jclass type)
{
    E_IF_exit(encInternals);
}