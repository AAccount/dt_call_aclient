LOCAL_PATH := $(call my-dir)

# jni interface inspired by: https://github.com/kevinho/opencore-amr-android

#ripped and modified from aosp nougat mr2.3 platform_external_libopus
include $(CLEAR_VARS)
LOCAL_MODULE    := libopus
LOCAL_C_INCLUDES += $(LOCAL_PATH)/opus-1.3.1/include $(LOCAL_PATH)/opus-1.3.1/src $(LOCAL_PATH)/opus-1.3.1/silk \
                    $(LOCAL_PATH)/opus-1.3.1/celt $(LOCAL_PATH)/opus-1.3.1/silk/fixed
LOCAL_SRC_FILES := opus-1.3.1/celt/bands.c \
                   opus-1.3.1/celt/celt.c \
                   opus-1.3.1/celt/celt_decoder.c \
                   opus-1.3.1/celt/celt_encoder.c \
                   opus-1.3.1/celt/celt_lpc.c \
                   opus-1.3.1/celt/cwrs.c \
                   opus-1.3.1/celt/entcode.c \
                   opus-1.3.1/celt/entdec.c \
                   opus-1.3.1/celt/entenc.c \
                   opus-1.3.1/celt/kiss_fft.c \
                   opus-1.3.1/celt/laplace.c \
                   opus-1.3.1/celt/mathops.c \
                   opus-1.3.1/celt/mdct.c \
                   opus-1.3.1/celt/modes.c \
                   opus-1.3.1/celt/pitch.c \
                   opus-1.3.1/celt/quant_bands.c \
                   opus-1.3.1/celt/rate.c \
                   opus-1.3.1/celt/vq.c \
                   opus-1.3.1/silk/A2NLSF.c \
                   opus-1.3.1/silk/ana_filt_bank_1.c \
                   opus-1.3.1/silk/biquad_alt.c \
                   opus-1.3.1/silk/bwexpander_32.c \
                   opus-1.3.1/silk/bwexpander.c \
                   opus-1.3.1/silk/check_control_input.c \
                   opus-1.3.1/silk/CNG.c \
                   opus-1.3.1/silk/code_signs.c \
                   opus-1.3.1/silk/control_audio_bandwidth.c \
                   opus-1.3.1/silk/control_codec.c \
                   opus-1.3.1/silk/control_SNR.c \
                   opus-1.3.1/silk/debug.c \
                   opus-1.3.1/silk/dec_API.c \
                   opus-1.3.1/silk/decode_core.c \
                   opus-1.3.1/silk/decode_frame.c \
                   opus-1.3.1/silk/decode_indices.c \
                   opus-1.3.1/silk/decode_parameters.c \
                   opus-1.3.1/silk/decode_pitch.c \
                   opus-1.3.1/silk/decode_pulses.c \
                   opus-1.3.1/silk/decoder_set_fs.c \
                   opus-1.3.1/silk/enc_API.c \
                   opus-1.3.1/silk/encode_indices.c \
                   opus-1.3.1/silk/encode_pulses.c \
                   opus-1.3.1/silk/gain_quant.c \
                   opus-1.3.1/silk/HP_variable_cutoff.c \
                   opus-1.3.1/silk/init_decoder.c \
                   opus-1.3.1/silk/init_encoder.c \
                   opus-1.3.1/silk/inner_prod_aligned.c \
                   opus-1.3.1/silk/interpolate.c \
                   opus-1.3.1/silk/lin2log.c \
                   opus-1.3.1/silk/log2lin.c \
                   opus-1.3.1/silk/LPC_analysis_filter.c \
                   opus-1.3.1/silk/LPC_fit.c \
                   opus-1.3.1/silk/LPC_inv_pred_gain.c \
                   opus-1.3.1/silk/LP_variable_cutoff.c \
                   opus-1.3.1/silk/NLSF2A.c \
                   opus-1.3.1/silk/NLSF_decode.c \
                   opus-1.3.1/silk/NLSF_del_dec_quant.c \
                   opus-1.3.1/silk/NLSF_encode.c \
                   opus-1.3.1/silk/NLSF_stabilize.c \
                   opus-1.3.1/silk/NLSF_unpack.c \
                   opus-1.3.1/silk/NLSF_VQ.c \
                   opus-1.3.1/silk/NLSF_VQ_weights_laroia.c \
                   opus-1.3.1/silk/NSQ.c \
                   opus-1.3.1/silk/NSQ_del_dec.c \
                   opus-1.3.1/silk/pitch_est_tables.c \
                   opus-1.3.1/silk/PLC.c \
                   opus-1.3.1/silk/process_NLSFs.c \
                   opus-1.3.1/silk/quant_LTP_gains.c \
                   opus-1.3.1/silk/resampler.c \
                   opus-1.3.1/silk/resampler_down2_3.c \
                   opus-1.3.1/silk/resampler_down2.c \
                   opus-1.3.1/silk/resampler_private_AR2.c \
                   opus-1.3.1/silk/resampler_private_down_FIR.c \
                   opus-1.3.1/silk/resampler_private_IIR_FIR.c \
                   opus-1.3.1/silk/resampler_private_up2_HQ.c \
                   opus-1.3.1/silk/resampler_rom.c \
                   opus-1.3.1/silk/shell_coder.c \
                   opus-1.3.1/silk/sigm_Q15.c \
                   opus-1.3.1/silk/sort.c \
                   opus-1.3.1/silk/stereo_decode_pred.c \
                   opus-1.3.1/silk/stereo_encode_pred.c \
                   opus-1.3.1/silk/stereo_find_predictor.c \
                   opus-1.3.1/silk/stereo_LR_to_MS.c \
                   opus-1.3.1/silk/stereo_MS_to_LR.c \
                   opus-1.3.1/silk/stereo_quant_pred.c \
                   opus-1.3.1/silk/sum_sqr_shift.c \
                   opus-1.3.1/silk/table_LSF_cos.c \
                   opus-1.3.1/silk/tables_gain.c \
                   opus-1.3.1/silk/tables_LTP.c \
                   opus-1.3.1/silk/tables_NLSF_CB_NB_MB.c \
                   opus-1.3.1/silk/tables_NLSF_CB_WB.c \
                   opus-1.3.1/silk/tables_other.c \
                   opus-1.3.1/silk/tables_pitch_lag.c \
                   opus-1.3.1/silk/tables_pulses_per_block.c \
                   opus-1.3.1/silk/VAD.c \
                   opus-1.3.1/silk/VQ_WMat_EC.c \
                   opus-1.3.1/silk/fixed/apply_sine_window_FIX.c \
                   opus-1.3.1/silk/fixed/autocorr_FIX.c \
                   opus-1.3.1/silk/fixed/burg_modified_FIX.c \
                   opus-1.3.1/silk/fixed/corrMatrix_FIX.c \
                   opus-1.3.1/silk/fixed/encode_frame_FIX.c \
                   opus-1.3.1/silk/fixed/find_LPC_FIX.c \
                   opus-1.3.1/silk/fixed/find_LTP_FIX.c \
                   opus-1.3.1/silk/fixed/find_pitch_lags_FIX.c \
                   opus-1.3.1/silk/fixed/find_pred_coefs_FIX.c \
                   opus-1.3.1/silk/fixed/k2a_FIX.c \
                   opus-1.3.1/silk/fixed/k2a_Q16_FIX.c \
                   opus-1.3.1/silk/fixed/LTP_analysis_filter_FIX.c \
                   opus-1.3.1/silk/fixed/LTP_scale_ctrl_FIX.c \
                   opus-1.3.1/silk/fixed/noise_shape_analysis_FIX.c \
                   opus-1.3.1/silk/fixed/pitch_analysis_core_FIX.c \
                   opus-1.3.1/silk/fixed/process_gains_FIX.c \
                   opus-1.3.1/silk/fixed/regularize_correlations_FIX.c \
                   opus-1.3.1/silk/fixed/residual_energy16_FIX.c \
                   opus-1.3.1/silk/fixed/residual_energy_FIX.c \
                   opus-1.3.1/silk/fixed/schur64_FIX.c \
                   opus-1.3.1/silk/fixed/schur_FIX.c \
                   opus-1.3.1/silk/fixed/vector_ops_FIX.c \
                   opus-1.3.1/silk/fixed/warped_autocorrelation_FIX.c \
                   opus-1.3.1/src/analysis.c \
                   opus-1.3.1/src/mlp.c \
                   opus-1.3.1/src/mlp_data.c \
                   opus-1.3.1/src/opus.c \
                   opus-1.3.1/src/opus_decoder.c \
                   opus-1.3.1/src/opus_encoder.c \
                   opus-1.3.1/src/opus_multistream.c \
                   opus-1.3.1/src/opus_multistream_decoder.c \
                   opus-1.3.1/src/opus_multistream_encoder.c \
                   opus-1.3.1/src/repacketizer.c \
                   opus-1.3.1/src/repacketizer_demo.c
LOCAL_CFLAGS        := -DNULL=0 -DSOCKLEN_T=socklen_t -DLOCALE_NOT_USED \
                       -D_LARGEFILE_SOURCE=1 -D_FILE_OFFSET_BITS=64 \
                       -Drestrict='' -D__EMX__ -DOPUS_BUILD -DFIXED_POINT \
                       -DUSE_ALLOCA -DHAVE_LRINT -DHAVE_LRINTF -O2 -fno-math-errno
LOCAL_CPPFLAGS      := -DBSD=1 -ffast-math -O2 -funroll-loops
include $(BUILD_STATIC_LIBRARY)

# opus codec wrapper
include $(CLEAR_VARS)
LOCAL_C_INCLUDES += include/opus
LOCAL_MODULE    := libopus-aclient
LOCAL_MODULE_FILENAME    := libopus-aclient
LOCAL_SRC_FILES := opus-jni.c
LOCAL_STATIC_LIBRARIES := libopus
LOCAL_LDLIBS := -llog
LOCAL_CFLAGS += -O2
include $(BUILD_SHARED_LIBRARY)
