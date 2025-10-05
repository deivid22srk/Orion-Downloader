#include <jni.h>
#include <string>
#include <memory>
#include "download_engine.h"
#include <android/log.h>

#define LOG_TAG "OrionJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

static std::shared_ptr<orion::DownloadEngine> g_engine;
static JavaVM* g_jvm = nullptr;
static jobject g_callback_obj = nullptr;
static jmethodID g_progress_method = nullptr;

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    LOGD("JNI_OnLoad called");
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL
Java_com_orion_downloader_core_DownloadEngine_nativeInit(JNIEnv* env, jobject thiz) {
    LOGD("nativeInit called");
    g_engine = std::make_shared<orion::DownloadEngine>();
}

extern "C" JNIEXPORT void JNICALL
Java_com_orion_downloader_core_DownloadEngine_nativeDestroy(JNIEnv* env, jobject thiz) {
    LOGD("nativeDestroy called");
    if (g_callback_obj) {
        env->DeleteGlobalRef(g_callback_obj);
        g_callback_obj = nullptr;
    }
    g_engine.reset();
}

extern "C" JNIEXPORT void JNICALL
Java_com_orion_downloader_core_DownloadEngine_nativeSetProgressCallback(
    JNIEnv* env, jobject thiz, jobject callback) {
    
    if (g_callback_obj) {
        env->DeleteGlobalRef(g_callback_obj);
    }
    
    if (callback) {
        g_callback_obj = env->NewGlobalRef(callback);
        jclass callback_class = env->GetObjectClass(callback);
        g_progress_method = env->GetMethodID(callback_class, "onProgress", "(JJDI)V");
        LOGD("Progress callback set");
    } else {
        g_callback_obj = nullptr;
        g_progress_method = nullptr;
    }
}

static void progressCallback(const orion::DownloadProgress& progress) {
    if (!g_jvm || !g_callback_obj || !g_progress_method) {
        return;
    }

    JNIEnv* env;
    bool attached = false;
    int status = g_jvm->GetEnv((void**)&env, JNI_VERSION_1_6);
    
    if (status == JNI_EDETACHED) {
        if (g_jvm->AttachCurrentThread(&env, nullptr) != 0) {
            return;
        }
        attached = true;
    }

    env->CallVoidMethod(
        g_callback_obj,
        g_progress_method,
        static_cast<jlong>(progress.downloaded_bytes),
        static_cast<jlong>(progress.total_bytes),
        static_cast<jdouble>(progress.speed_bps),
        static_cast<jint>(progress.active_connections)
    );

    if (attached) {
        g_jvm->DetachCurrentThread();
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_orion_downloader_core_DownloadEngine_nativeStartDownload(
    JNIEnv* env, jobject thiz, jstring url, jstring output_path, jint num_connections) {
    
    if (!g_engine) {
        LOGD("Engine not initialized");
        return JNI_FALSE;
    }

    const char* url_str = env->GetStringUTFChars(url, nullptr);
    const char* path_str = env->GetStringUTFChars(output_path, nullptr);

    LOGD("Starting download: %s -> %s (connections: %d)", url_str, path_str, num_connections);

    bool result = g_engine->startDownload(
        std::string(url_str),
        std::string(path_str),
        num_connections,
        progressCallback
    );

    env->ReleaseStringUTFChars(url, url_str);
    env->ReleaseStringUTFChars(output_path, path_str);

    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_orion_downloader_core_DownloadEngine_nativePauseDownload(JNIEnv* env, jobject thiz) {
    if (g_engine) {
        g_engine->pauseDownload();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_orion_downloader_core_DownloadEngine_nativeResumeDownload(JNIEnv* env, jobject thiz) {
    if (g_engine) {
        g_engine->resumeDownload();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_orion_downloader_core_DownloadEngine_nativeCancelDownload(JNIEnv* env, jobject thiz) {
    if (g_engine) {
        g_engine->cancelDownload();
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_orion_downloader_core_DownloadEngine_nativeIsDownloading(JNIEnv* env, jobject thiz) {
    if (g_engine) {
        return g_engine->isDownloading() ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_orion_downloader_core_DownloadEngine_nativeIsPaused(JNIEnv* env, jobject thiz) {
    if (g_engine) {
        return g_engine->isPaused() ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_orion_downloader_core_DownloadEngine_nativeGetProgress(JNIEnv* env, jobject thiz) {
    if (!g_engine) {
        return nullptr;
    }

    auto progress = g_engine->getProgress();
    jlongArray result = env->NewLongArray(3);
    if (result) {
        jlong values[3] = {
            progress.downloaded_bytes,
            progress.total_bytes,
            static_cast<jlong>(progress.speed_bps)
        };
        env->SetLongArrayRegion(result, 0, 3, values);
    }
    return result;
}
