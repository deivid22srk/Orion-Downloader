#include <jni.h>
#include <string>
#include <memory>
#include <map>
#include <mutex>
#include "download_engine.h"

static std::map<jlong, std::unique_ptr<orion::DownloadEngine>> engines;
static std::mutex engines_mutex;
static jlong next_engine_id = 1;

static JavaVM* g_jvm = nullptr;

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_orion_downloader_core_DownloadEngine_nativeGetVersion(
    JNIEnv* env,
    jobject) {
    return env->NewStringUTF("Orion-Native/2.1.0-HTTP");
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_orion_downloader_core_NativeDownloadEngine_nativeCreate(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(engines_mutex);
    jlong engine_id = next_engine_id++;
    engines[engine_id] = std::make_unique<orion::DownloadEngine>();
    return engine_id;
}

extern "C" JNIEXPORT void JNICALL
Java_com_orion_downloader_core_NativeDownloadEngine_nativeDestroy(
    JNIEnv* env,
    jobject,
    jlong engine_id) {
    std::lock_guard<std::mutex> lock(engines_mutex);
    auto it = engines.find(engine_id);
    if (it != engines.end()) {
        it->second->cancelDownload();
        engines.erase(it);
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_orion_downloader_core_NativeDownloadEngine_nativeGetContentLength(
    JNIEnv* env,
    jobject,
    jlong engine_id,
    jstring url) {
    std::lock_guard<std::mutex> lock(engines_mutex);
    auto it = engines.find(engine_id);
    if (it == engines.end()) return -1;
    
    const char* url_str = env->GetStringUTFChars(url, nullptr);
    int64_t length = it->second->getContentLength(std::string(url_str));
    env->ReleaseStringUTFChars(url, url_str);
    
    return static_cast<jlong>(length);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_orion_downloader_core_NativeDownloadEngine_nativeSupportsRangeRequests(
    JNIEnv* env,
    jobject,
    jlong engine_id,
    jstring url) {
    std::lock_guard<std::mutex> lock(engines_mutex);
    auto it = engines.find(engine_id);
    if (it == engines.end()) return JNI_FALSE;
    
    const char* url_str = env->GetStringUTFChars(url, nullptr);
    bool supports = it->second->supportsRangeRequests(std::string(url_str));
    env->ReleaseStringUTFChars(url, url_str);
    
    return supports ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_orion_downloader_core_NativeDownloadEngine_nativeStartDownload(
    JNIEnv* env,
    jobject thiz,
    jlong engine_id,
    jstring url,
    jstring output_path,
    jint num_connections,
    jobject callback) {
    
    std::lock_guard<std::mutex> lock(engines_mutex);
    auto it = engines.find(engine_id);
    if (it == engines.end()) return JNI_FALSE;
    
    const char* url_str = env->GetStringUTFChars(url, nullptr);
    const char* path_str = env->GetStringUTFChars(output_path, nullptr);
    
    jobject global_callback = env->NewGlobalRef(callback);
    jclass callback_class = env->GetObjectClass(global_callback);
    jmethodID method_id = env->GetMethodID(callback_class, "onProgress", "(JJDI)V");
    
    auto progress_callback = [global_callback, method_id](orion::DownloadProgress progress) {
        if (!g_jvm) return;
        
        JNIEnv* env;
        bool should_detach = false;
        
        if (g_jvm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
            if (g_jvm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
                should_detach = true;
            } else {
                return;
            }
        }
        
        env->CallVoidMethod(
            global_callback,
            method_id,
            static_cast<jlong>(progress.downloaded_bytes),
            static_cast<jlong>(progress.total_bytes),
            static_cast<jdouble>(progress.speed_bps),
            static_cast<jint>(progress.active_connections)
        );
        
        if (should_detach) {
            g_jvm->DetachCurrentThread();
        }
    };
    
    bool result = it->second->startDownload(
        std::string(url_str),
        std::string(path_str),
        static_cast<int>(num_connections),
        progress_callback
    );
    
    env->ReleaseStringUTFChars(url, url_str);
    env->ReleaseStringUTFChars(output_path, path_str);
    
    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_orion_downloader_core_NativeDownloadEngine_nativePauseDownload(
    JNIEnv* env,
    jobject,
    jlong engine_id) {
    std::lock_guard<std::mutex> lock(engines_mutex);
    auto it = engines.find(engine_id);
    if (it != engines.end()) {
        it->second->pauseDownload();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_orion_downloader_core_NativeDownloadEngine_nativeResumeDownload(
    JNIEnv* env,
    jobject,
    jlong engine_id) {
    std::lock_guard<std::mutex> lock(engines_mutex);
    auto it = engines.find(engine_id);
    if (it != engines.end()) {
        it->second->resumeDownload();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_orion_downloader_core_NativeDownloadEngine_nativeCancelDownload(
    JNIEnv* env,
    jobject,
    jlong engine_id) {
    std::lock_guard<std::mutex> lock(engines_mutex);
    auto it = engines.find(engine_id);
    if (it != engines.end()) {
        it->second->cancelDownload();
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_orion_downloader_core_NativeDownloadEngine_nativeIsDownloading(
    JNIEnv* env,
    jobject,
    jlong engine_id) {
    std::lock_guard<std::mutex> lock(engines_mutex);
    auto it = engines.find(engine_id);
    if (it == engines.end()) return JNI_FALSE;
    return it->second->isDownloading() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_orion_downloader_core_NativeDownloadEngine_nativeIsPaused(
    JNIEnv* env,
    jobject,
    jlong engine_id) {
    std::lock_guard<std::mutex> lock(engines_mutex);
    auto it = engines.find(engine_id);
    if (it == engines.end()) return JNI_FALSE;
    return it->second->isPaused() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_orion_downloader_core_NativeDownloadEngine_nativeGetProgress(
    JNIEnv* env,
    jobject,
    jlong engine_id) {
    std::lock_guard<std::mutex> lock(engines_mutex);
    auto it = engines.find(engine_id);
    if (it == engines.end()) return nullptr;
    
    orion::DownloadProgress progress = it->second->getProgress();
    
    jclass progress_class = env->FindClass("com/orion/downloader/core/NativeDownloadEngine$DownloadProgress");
    jmethodID constructor = env->GetMethodID(progress_class, "<init>", "(JJDI)V");
    
    return env->NewObject(
        progress_class,
        constructor,
        static_cast<jlong>(progress.downloaded_bytes),
        static_cast<jlong>(progress.total_bytes),
        static_cast<jdouble>(progress.speed_bps),
        static_cast<jint>(progress.active_connections)
    );
}
