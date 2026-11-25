package com.example.ml;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Java JNI bridge to cactus_jni (which dynamically binds to Cactus Compute's libcactus.so).
 * Handles missing native binaries gracefully without crashing the process.
 */
public final class CactusNative {

    private static final boolean JNI_SHIM_LOADED;

    static {
        boolean loaded;
        try {
            System.loadLibrary("cactus_jni");
            loaded = true;
        } catch (UnsatisfiedLinkError | SecurityException ex) {
            loaded = false;
        }
        JNI_SHIM_LOADED = loaded;
    }

    private CactusNative() {
    }

    public static boolean isJniShimLoaded() {
        return JNI_SHIM_LOADED;
    }

    public static boolean isCactusLibraryAvailable() {
        if (!JNI_SHIM_LOADED) {
            return false;
        }
        try {
            return nativeIsCactusAvailable();
        } catch (UnsatisfiedLinkError ex) {
            return false;
        }
    }

    public static long loadModel(@NonNull byte[] modelBytes) {
        if (!JNI_SHIM_LOADED || modelBytes.length == 0) {
            return 0L;
        }
        try {
            return nativeLoadModel(modelBytes);
        } catch (UnsatisfiedLinkError ex) {
            return 0L;
        }
    }

    @Nullable
    public static String describe(
            long modelHandle,
            @NonNull byte[] rgbPixels,
            int width,
            int height,
            @NonNull String optionsJson
    ) {
        if (!JNI_SHIM_LOADED || modelHandle == 0L) {
            return null;
        }
        try {
            return nativeDescribe(modelHandle, rgbPixels, width, height, optionsJson);
        } catch (UnsatisfiedLinkError ex) {
            return null;
        }
    }

    public static void freeModel(long modelHandle) {
        if (!JNI_SHIM_LOADED || modelHandle == 0L) {
            return;
        }
        try {
            nativeFreeModel(modelHandle);
        } catch (UnsatisfiedLinkError ignored) {
            // Safe no-op during teardown
        }
    }

    private static native boolean nativeIsCactusAvailable();

    private static native long nativeLoadModel(@NonNull byte[] modelBytes);

    @Nullable
    private static native String nativeDescribe(
            long modelHandle,
            @NonNull byte[] rgbPixels,
            int width,
            int height,
            @NonNull String optionsJson
    );

    private static native void nativeFreeModel(long modelHandle);
}
