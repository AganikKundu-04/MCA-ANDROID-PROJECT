package com.example.ml;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * On-device Mirage 22 MB VLM OCR engine backed by Cactus Compute's libcactus.so via JNI.
 * Lazily loads assets/mirage.cact on a dedicated single-threaded executor and releases native memory
 * cleanly on trim-memory / teardown. Delegates to a fallback repository when drop-in binaries are absent.
 */
public class MirageOcrEngine implements MirageRepository {

    public static final String MODEL_ASSET_NAME = "mirage.cact";

    private static final String EXTRACTION_PROMPT =
            "Extract payment receipt fields as strict JSON with keys: "
                    + "amount, merchant, status (SUCCESS|FAILED|PENDING), "
                    + "direction (DEBIT|CREDIT|UNKNOWN), date_time, reference_id (12-digit UPI UTR), "
                    + "payment_mode (UPI|CARD|CASH). Return JSON only.";

    @NonNull
    private final Context appContext;
    @NonNull
    private final ExecutorService inferenceExecutor;
    @Nullable
    private final MirageRepository fallbackRepository;
    private final AtomicLong modelHandle = new AtomicLong(0L);

    public MirageOcrEngine(
            @NonNull Context context,
            @Nullable MirageRepository fallbackRepository
    ) {
        this.appContext = context.getApplicationContext();
        this.fallbackRepository = fallbackRepository;
        this.inferenceExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "mirage-ocr-thread");
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
    }

    @Override
    @NonNull
    public CompletableFuture<ScanResult> scanReceiptFile(@NonNull File imageFile) {
        return CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();
            boolean modelPresent = isModelAssetPresent();
            boolean nativePresent = CactusNative.isCactusLibraryAvailable();

            if (!modelPresent || !nativePresent) {
                if (fallbackRepository != null) {
                    return fallbackRepository.scanReceiptFile(imageFile).join();
                }
                throw new IllegalStateException("Mirage native binaries (mirage.cact / libcactus.so) are not installed.");
            }

            try {
                ImagePreprocessor.PreprocessedImage pre = ImagePreprocessor.preprocessFile(imageFile);
                long handle = ensureModelLoadedLocked();
                if (handle == 0L) {
                    throw new IllegalStateException("cactus_load returned null handle for " + MODEL_ASSET_NAME);
                }

                String optionsJson = buildDescribeOptionsJson();
                String rawOutput = CactusNative.describe(
                        handle,
                        pre.rgbPixels,
                        pre.width,
                        pre.height,
                        optionsJson
                );

                ParsedReceipt parsed = UpiReceiptPostProcessor.process(rawOutput, System.currentTimeMillis());
                long elapsed = System.currentTimeMillis() - start;
                return new ScanResult(parsed, pre.previewThumbnail, pre.smartCropped, false, elapsed);
            } catch (IOException ex) {
                throw new RuntimeException("Failed to read receipt image: " + ex.getMessage(), ex);
            }
        }, inferenceExecutor);
    }

    @Override
    @NonNull
    public MirageStatus getStatus() {
        boolean assetPresent = isModelAssetPresent();
        boolean nativePresent = CactusNative.isCactusLibraryAvailable();
        boolean loaded = modelHandle.get() != 0L;

        if (assetPresent && nativePresent) {
            return new MirageStatus(
                    true,
                    true,
                    loaded,
                    false,
                    "Cactus Mirage 22 MB (On-Device)",
                    loaded
                            ? "Model loaded in memory — 100% offline native OCR active."
                            : "Ready for lazy on-device load on first shared screenshot."
            );
        }

        StringBuilder missing = new StringBuilder();
        if (!assetPresent) {
            missing.append("assets/mirage.cact");
        }
        if (!nativePresent) {
            if (missing.length() > 0) missing.append(" & ");
            missing.append("jniLibs/arm64-v8a/libcactus.so");
        }

        if (fallbackRepository != null) {
            return new MirageStatus(
                    assetPresent,
                    nativePresent,
                    false,
                    true,
                    "Preview Fallback (Drop-In Ready)",
                    "Missing " + missing + ". Real image preprocessing & UPI post-processor run with simulated OCR until native binaries are added."
            );
        }

        return new MirageStatus(
                assetPresent,
                nativePresent,
                false,
                false,
                "Native Binaries Missing",
                "Place " + missing + " to enable on-device Mirage OCR."
        );
    }

    @Override
    public void releaseResources() {
        inferenceExecutor.execute(() -> {
            long handle = modelHandle.getAndSet(0L);
            if (handle != 0L) {
                CactusNative.freeModel(handle);
            }
        });
    }

    private long ensureModelLoadedLocked() throws IOException {
        long existing = modelHandle.get();
        if (existing != 0L) {
            return existing;
        }
        byte[] modelBytes = readModelBytes();
        long loaded = CactusNative.loadModel(modelBytes);
        if (loaded != 0L) {
            modelHandle.set(loaded);
        }
        return loaded;
    }

    private boolean isModelAssetPresent() {
        AssetManager assets = appContext.getAssets();
        try (AssetFileDescriptor afd = assets.openFd(MODEL_ASSET_NAME)) {
            return afd.getLength() > 0L;
        } catch (IOException ignored) {
            // Fallback check in case asset was compressed
            try (InputStream in = assets.open(MODEL_ASSET_NAME)) {
                return in.read() != -1;
            } catch (IOException ignoredAgain) {
                return false;
            }
        }
    }

    /**
     * Reads uncompressed mirage.cact via AssetFileDescriptor when available, caching a copy
     * into filesDir if needed for fast subsequent reads.
     */
    @NonNull
    private byte[] readModelBytes() throws IOException {
        File cachedModel = new File(appContext.getFilesDir(), MODEL_ASSET_NAME);
        AssetManager assets = appContext.getAssets();
        try (AssetFileDescriptor afd = assets.openFd(MODEL_ASSET_NAME)) {
            long expectedSize = afd.getLength();
            if (!cachedModel.exists() || cachedModel.length() != expectedSize) {
                try (InputStream in = afd.createInputStream();
                     FileOutputStream out = new FileOutputStream(cachedModel)) {
                    copyStream(in, out);
                }
            }
            return readFileFully(cachedModel);
        } catch (IOException ignored) {
            try (InputStream in = assets.open(MODEL_ASSET_NAME);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                copyStream(in, out);
                return out.toByteArray();
            }
        }
    }

    @NonNull
    private static byte[] readFileFully(@NonNull File file) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(file.length(), Integer.MAX_VALUE));
        try (InputStream in = new FileInputStream(file)) {
            copyStream(in, out);
        }
        return out.toByteArray();
    }

    private static void copyStream(@NonNull InputStream in, @NonNull java.io.OutputStream out) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        out.flush();
    }

    @NonNull
    private static String buildDescribeOptionsJson() {
        JSONObject opts = new JSONObject();
        try {
            opts.put("prompt", EXTRACTION_PROMPT);
            opts.put("max_tokens", 256);
            opts.put("temperature", 0.0);
        } catch (JSONException ignored) {
            return "{}";
        }
        return opts.toString();
    }
}
