#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>
#include <cstring>
#include <mutex>
#include <vector>

#include "cactus.h"

#define LOG_TAG "CactusJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

std::mutex g_dl_mutex;
void* g_cactus_handle = nullptr;
cactus_load_fn g_cactus_load = nullptr;
cactus_describe_fn g_cactus_describe = nullptr;
cactus_free_fn g_cactus_free = nullptr;
bool g_dl_attempted = false;

bool ensure_cactus_symbols_locked() {
    if (g_cactus_load != nullptr && g_cactus_describe != nullptr) {
        return true;
    }
    if (g_dl_attempted && g_cactus_handle == nullptr) {
        return false;
    }
    g_dl_attempted = true;

    g_cactus_handle = dlopen("libcactus.so", RTLD_NOW | RTLD_LOCAL);
    if (g_cactus_handle == nullptr) {
        LOGI("libcactus.so not loaded (drop-in binary absent): %s", dlerror());
        return false;
    }

    g_cactus_load = reinterpret_cast<cactus_load_fn>(dlsym(g_cactus_handle, "cactus_load"));
    g_cactus_describe = reinterpret_cast<cactus_describe_fn>(dlsym(g_cactus_handle, "cactus_describe"));
    g_cactus_free = reinterpret_cast<cactus_free_fn>(dlsym(g_cactus_handle, "cactus_free"));

    if (g_cactus_load == nullptr || g_cactus_describe == nullptr) {
        LOGE("libcactus.so loaded, but required symbols cactus_load/cactus_describe missing.");
        dlclose(g_cactus_handle);
        g_cactus_handle = nullptr;
        g_cactus_load = nullptr;
        g_cactus_describe = nullptr;
        g_cactus_free = nullptr;
        return false;
    }
    LOGI("libcactus.so symbols resolved successfully.");
    return true;
}

} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_ml_CactusNative_nativeIsCactusAvailable(
        JNIEnv* /* env */,
        jclass /* clazz */
) {
    std::lock_guard<std::mutex> lock(g_dl_mutex);
    return ensure_cactus_symbols_locked() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_ml_CactusNative_nativeLoadModel(
        JNIEnv* env,
        jclass /* clazz */,
        jbyteArray modelBytes
) {
    if (modelBytes == nullptr) {
        return 0L;
    }

    cactus_load_fn loadFn = nullptr;
    {
        std::lock_guard<std::mutex> lock(g_dl_mutex);
        if (!ensure_cactus_symbols_locked()) {
            return 0L;
        }
        loadFn = g_cactus_load;
    }

    jsize len = env->GetArrayLength(modelBytes);
    if (len <= 0) {
        return 0L;
    }

    jbyte* raw = env->GetByteArrayElements(modelBytes, nullptr);
    if (raw == nullptr) {
        return 0L;
    }

    cactus_model_t* model = loadFn(
            reinterpret_cast<const uint8_t*>(raw),
            static_cast<size_t>(len)
    );
    env->ReleaseByteArrayElements(modelBytes, raw, JNI_ABORT);

    if (model == nullptr) {
        LOGE("cactus_load returned null for model size=%d", static_cast<int>(len));
        return 0L;
    }
    return reinterpret_cast<jlong>(model);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_ml_CactusNative_nativeDescribe(
        JNIEnv* env,
        jclass /* clazz */,
        jlong modelHandle,
        jbyteArray rgbPixels,
        jint width,
        jint height,
        jstring optionsJson
) {
    if (modelHandle == 0L || rgbPixels == nullptr || width <= 0 || height <= 0) {
        return nullptr;
    }

    cactus_describe_fn describeFn = nullptr;
    {
        std::lock_guard<std::mutex> lock(g_dl_mutex);
        if (!ensure_cactus_symbols_locked()) {
            return nullptr;
        }
        describeFn = g_cactus_describe;
    }

    jsize expectedBytes = width * height * 3;
    jsize actualBytes = env->GetArrayLength(rgbPixels);
    if (actualBytes < expectedBytes) {
        LOGE("RGB buffer too small: actual=%d expected=%d",
             static_cast<int>(actualBytes), static_cast<int>(expectedBytes));
        return nullptr;
    }

    jbyte* pixelPtr = env->GetByteArrayElements(rgbPixels, nullptr);
    if (pixelPtr == nullptr) {
        return nullptr;
    }

    const char* optionsCStr = optionsJson != nullptr
            ? env->GetStringUTFChars(optionsJson, nullptr)
            : "{}";

    constexpr size_t kOutBufSize = 16384;
    std::vector<char> outBuf(kOutBufSize, '\0');

    auto* model = reinterpret_cast<cactus_model_t*>(modelHandle);
    int rc = describeFn(
            model,
            reinterpret_cast<const uint8_t*>(pixelPtr),
            static_cast<int>(width),
            static_cast<int>(height),
            optionsCStr != nullptr ? optionsCStr : "{}",
            outBuf.data(),
            outBuf.size() - 1
    );

    if (optionsJson != nullptr && optionsCStr != nullptr) {
        env->ReleaseStringUTFChars(optionsJson, optionsCStr);
    }
    env->ReleaseByteArrayElements(rgbPixels, pixelPtr, JNI_ABORT);

    if (rc < 0) {
        LOGE("cactus_describe failed with code=%d", rc);
        return nullptr;
    }

    outBuf[kOutBufSize - 1] = '\0';
    return env->NewStringUTF(outBuf.data());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_ml_CactusNative_nativeFreeModel(
        JNIEnv* /* env */,
        jclass /* clazz */,
        jlong modelHandle
) {
    if (modelHandle == 0L) {
        return;
    }
    cactus_free_fn freeFn = nullptr;
    {
        std::lock_guard<std::mutex> lock(g_dl_mutex);
        freeFn = g_cactus_free;
    }
    if (freeFn != nullptr) {
        freeFn(reinterpret_cast<cactus_model_t*>(modelHandle));
    }
}
