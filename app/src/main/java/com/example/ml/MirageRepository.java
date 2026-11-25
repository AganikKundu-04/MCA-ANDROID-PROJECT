package com.example.ml;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.concurrent.CompletableFuture;

/**
 * Decoupled boundary between UI/domain and the on-device Mirage vision-language engine.
 */
public interface MirageRepository {

    final class ScanResult {
        @NonNull
        public final ParsedReceipt receipt;
        @Nullable
        public final Bitmap previewThumbnail;
        public final boolean smartCropped;
        public final boolean usedSimulatedFallback;
        public final long inferenceDurationMs;

        public ScanResult(
                @NonNull ParsedReceipt receipt,
                @Nullable Bitmap previewThumbnail,
                boolean smartCropped,
                boolean usedSimulatedFallback,
                long inferenceDurationMs
        ) {
            this.receipt = receipt;
            this.previewThumbnail = previewThumbnail;
            this.smartCropped = smartCropped;
            this.usedSimulatedFallback = usedSimulatedFallback;
            this.inferenceDurationMs = inferenceDurationMs;
        }
    }

    @NonNull
    CompletableFuture<ScanResult> scanReceiptFile(@NonNull File imageFile);

    @NonNull
    MirageStatus getStatus();

    void releaseResources();
}
