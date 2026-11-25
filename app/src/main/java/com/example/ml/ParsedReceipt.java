package com.example.ml;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.domain.PaymentMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Structured output of Mirage OCR + UpiReceiptPostProcessor ready for user review.
 */
public final class ParsedReceipt {

    public enum ReceiptStatus {
        SUCCESS,
        FAILED,
        PENDING,
        UNKNOWN
    }

    public enum TransactionDirection {
        DEBIT,
        CREDIT,
        UNKNOWN
    }

    public final long amountPaise;
    @NonNull
    public final String merchant;
    @NonNull
    public final ReceiptStatus status;
    @NonNull
    public final TransactionDirection direction;
    public final long timestampMillis;
    @Nullable
    public final String utrReference;
    @NonNull
    public final PaymentMode paymentMode;
    @NonNull
    public final String rawOutput;
    public final boolean partialParse;
    public final boolean amountCorrectedFromQuantization;
    @NonNull
    public final List<String> warnings;

    public ParsedReceipt(
            long amountPaise,
            @NonNull String merchant,
            @NonNull ReceiptStatus status,
            @NonNull TransactionDirection direction,
            long timestampMillis,
            @Nullable String utrReference,
            @NonNull PaymentMode paymentMode,
            @NonNull String rawOutput,
            boolean partialParse,
            boolean amountCorrectedFromQuantization,
            @NonNull List<String> warnings
    ) {
        this.amountPaise = amountPaise;
        this.merchant = merchant;
        this.status = status;
        this.direction = direction;
        this.timestampMillis = timestampMillis;
        this.utrReference = utrReference;
        this.paymentMode = paymentMode;
        this.rawOutput = rawOutput;
        this.partialParse = partialParse;
        this.amountCorrectedFromQuantization = amountCorrectedFromQuantization;
        this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
    }

    public boolean hasAmount() {
        return amountPaise > 0L;
    }

    public boolean isFailedOrPending() {
        return status == ReceiptStatus.FAILED || status == ReceiptStatus.PENDING;
    }

    public boolean isIncomingCredit() {
        return direction == TransactionDirection.CREDIT;
    }
}
