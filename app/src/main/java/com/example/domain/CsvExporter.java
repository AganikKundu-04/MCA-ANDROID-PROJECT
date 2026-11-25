package com.example.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * RFC-4180 compliant CSV serializer for exporting expenses via Storage Access Framework.
 */
public final class CsvExporter {

    private static final String HEADER = "Date,Merchant,Category,Payment Mode,Amount (INR),UTR Reference,Notes";

    private CsvExporter() {
    }

    public static final class CsvRow {
        public final long timestampMillis;
        @NonNull
        public final String merchant;
        @NonNull
        public final String category;
        @NonNull
        public final String paymentMode;
        public final long amountPaise;
        @Nullable
        public final String utrReference;
        @Nullable
        public final String notes;

        public CsvRow(
                long timestampMillis,
                @NonNull String merchant,
                @NonNull String category,
                @NonNull String paymentMode,
                long amountPaise,
                @Nullable String utrReference,
                @Nullable String notes
        ) {
            this.timestampMillis = timestampMillis;
            this.merchant = merchant;
            this.category = category;
            this.paymentMode = paymentMode;
            this.amountPaise = amountPaise;
            this.utrReference = utrReference;
            this.notes = notes;
        }
    }

    @NonNull
    public static String export(@NonNull List<CsvRow> rows) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append("\r\n");

        for (CsvRow row : rows) {
            sb.append(escapeField(dateFormat.format(new Date(row.timestampMillis)))).append(',');
            sb.append(escapeField(row.merchant)).append(',');
            sb.append(escapeField(row.category)).append(',');
            sb.append(escapeField(row.paymentMode)).append(',');
            sb.append(escapeField(MoneyFormatter.formatPaiseToDecimal(row.amountPaise))).append(',');
            sb.append(escapeField(row.utrReference)).append(',');
            sb.append(escapeField(row.notes)).append("\r\n");
        }
        return sb.toString();
    }

    @NonNull
    public static String escapeField(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String sanitized = value;
        boolean needsQuotes = sanitized.indexOf(',') >= 0
                || sanitized.indexOf('"') >= 0
                || sanitized.indexOf('\n') >= 0
                || sanitized.indexOf('\r') >= 0;
        if (needsQuotes) {
            return "\"" + sanitized.replace("\"", "\"\"") + "\"";
        }
        return sanitized;
    }
}
