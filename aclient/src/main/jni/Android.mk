LOCAL_PATH := $(call my-dir)

# precompiled static libraries generated with build-static.sh for simplicity
#   none of the original source was changed
# jni interface inspired by: https://github.com/kevinho/opencore-amr-android

# fdk aac precompiled static library
# http://downloads.sourceforge.net/opencore-amr/fdk-aac-0.1.5.tar.gz
include $(CLEAR_VARS)
LOCAL_MODULE := libfdkaac
LOCAL_MODULE_FILENAME := libfdkaac
LOCAL_SRC_FILES := prebuild/$(TARGET_ARCH_ABI)/libfdk-aac.a
LOCAL_EXPORT_C_INCLUDES := include/fdk-aac
include $(PREBUILT_STATIC_LIBRARY)

# aac codec wrapper
include $(CLEAR_VARS)
LOCAL_C_INCLUDES += include/fdk-aac
LOCAL_MODULE    := libfdkaac-aclient
LOCAL_MODULE_FILENAME    := libfdkaac-aclient
LOCAL_SRC_FILES := fdkaac-jni.c
LOCAL_STATIC_LIBRARIES := libfdkaac
LOCAL_LDLIBS := -llog
LOCAL_CFLAGS += -O2
include $(BUILD_SHARED_LIBRARY)