package com.example.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Supported payment modes for tracked spending.
 */
public enum PaymentMode {
    UPI("UPI"),
    CARD("Card"),
    CASH("Cash");

    @NonNull
    private final String displayName;

    PaymentMode(@NonNull String displayName) {
        this.displayName = displayName;
    }

    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    @NonNull
    public static PaymentMode fromStorageValue(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) {
            return UPI;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (PaymentMode mode : values()) {
            if (mode.name().equals(normalized) || mode.displayName.toUpperCase(Locale.ROOT).equals(normalized)) {
                return mode;
            }
        }
        return UPI;
    }
}
