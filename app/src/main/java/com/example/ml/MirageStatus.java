package com.example.ml;

import androidx.annotation.NonNull;

/**
 * Runtime health & availability status of the on-device Mirage vision-language engine.
 */
public final class MirageStatus {

    public final boolean modelAssetPresent;
    public final boolean nativeLibraryPresent;
    public final boolean modelLoadedInMemory;
    public final boolean usingSimulatedFallback;
    @NonNull
    public final String engineLabel;
    @NonNull
    public final String detailMessage;

    public MirageStatus(
            boolean modelAssetPresent,
            boolean nativeLibraryPresent,
            boolean modelLoadedInMemory,
            boolean usingSimulatedFallback,
            @NonNull String engineLabel,
            @NonNull String detailMessage
    ) {
        this.modelAssetPresent = modelAssetPresent;
        this.nativeLibraryPresent = nativeLibraryPresent;
        this.modelLoadedInMemory = modelLoadedInMemory;
        this.usingSimulatedFallback = usingSimulatedFallback;
        this.engineLabel = engineLabel;
        this.detailMessage = detailMessage;
    }

    public boolean isNativeReady() {
        return modelAssetPresent && nativeLibraryPresent;
    }
}
