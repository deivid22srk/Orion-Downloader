#include <jni.h>
#include <android/log.h>

#define LOG_TAG "OrionJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_orion_downloader_core_DownloadEngine_nativeGetVersion(JNIEnv* env, jobject thiz) {
    LOGD("nativeGetVersion called");
    return env->NewStringUTF("1.0.0-native");
}
