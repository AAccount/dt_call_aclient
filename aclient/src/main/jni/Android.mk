LOCAL_PATH := $(call my-dir)

# precompiled static libraries generated with build-static.sh for simplicity
#   none of the original source was changed
# inspired by: https://github.com/kevinho/opencore-amr-android

# opencore amr wideband decoder precompiled static library
# https://sourceforge.net/projects/opencore-amr/files/opencore-amr/opencore-amr-0.1.5.tar.gz/download
LOCAL_MODULE := libopencore-amrwb
LOCAL_MODULE_FILENAME := libopencore-amrwb
LOCAL_SRC_FILES := prebuild/$(TARGET_ARCH_ABI)/libopencore-amrwb.a
LOCAL_EXPORT_C_INCLUDES := prebuild/include/opencore-amrwb
include $(PREBUILT_STATIC_LIBRARY)

# opencore amr wideband decoder wrapper
include $(CLEAR_VARS)
LOCAL_C_INCLUDES += include/opencore-amrwb
LOCAL_MODULE    := libamrwb-dec
LOCAL_MODULE_FILENAME    := libamrwb-dec
LOCAL_SRC_FILES := amrwb-dec.c
LOCAL_STATIC_LIBRARIES := libopencore-amrwb
include $(BUILD_SHARED_LIBRARY)

# visual-on amr wideband encoder precompiled static library
# https://sourceforge.net/projects/opencore-amr/files/vo-amrwbenc/vo-amrwbenc-0.1.3.tar.gz/download
include $(CLEAR_VARS)
LOCAL_MODULE := libvo-amrwbenc
LOCAL_MODULE_FILENAME := libvo-amrwbenc
LOCAL_SRC_FILES := prebuild/$(TARGET_ARCH_ABI)/libvo-amrwbenc.a
LOCAL_EXPORT_C_INCLUDES := prebuild/include/vo-amrwbenc
include $(PREBUILT_STATIC_LIBRARY)

# visual-on amr wideband encoder wrapper
include $(CLEAR_VARS)
LOCAL_C_INCLUDES += include/vo-amrwbenc
LOCAL_MODULE    := libamrwb-enc
LOCAL_MODULE_FILENAME    := libamrwb-enc
LOCAL_SRC_FILES := amrwb-enc.c
LOCAL_STATIC_LIBRARIES := libvo-amrwbenc
include $(BUILD_SHARED_LIBRARY)