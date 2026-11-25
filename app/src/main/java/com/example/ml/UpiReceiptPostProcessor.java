package com.example.ml;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.domain.MoneyFormatter;
import com.example.domain.PaymentMode;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic hybrid post-processor repairing 2-bit Mirage VLM quantization quirks on Indian UPI
 * payment screenshots (GPay, PhonePe, Paytm, BHIM, bank debit receipts).
 */
public final class UpiReceiptPostProcessor {

    private static final Pattern CODE_FENCE_PATTERN =
            Pattern.compile("```(?:json)?\\s*(\\{.*?\\})\\s*```", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern UTR_LABELED_PATTERN =
            Pattern.compile("(?:utr|upi\\s*ref(?:erence)?|ref(?:erence)?\\s*(?:no|id|#)?|txn\\s*id|transaction\\s*id)\\s*[:#\\-]?\\s*([0-9][0-9\\s-]{10,16}[0-9])",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern UTR_RAW_12_PATTERN = Pattern.compile("(?<!\\d)(\\d{12})(?!\\d)");
    private static final Pattern AMOUNT_LABELED_PATTERN =
            Pattern.compile("(?:amount|paid|sent|debited|total|received)\\s*[:=]?\\s*([₹?R2sIN.\\s]*[0-9][0-9,]*(?:\\.[0-9]{1,2})?)",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern CURRENCY_TOKEN_PATTERN =
            Pattern.compile("(?:₹|Rs\\.?|INR|\\?|(?<![A-Za-z])R(?=[0-9\\s]))\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)");
    private static final Pattern LEADING_TWO_GLYPH_PATTERN =
            Pattern.compile("^2\\s+([0-9][0-9,]*(?:\\.[0-9]{1,2})?)$");
    private static final Pattern MERCHANT_LINE_PATTERN =
            Pattern.compile("(?:paid\\s+to|payment\\s+to|sent\\s+to|to|merchant|payee|received\\s+from)\\s*[:\\-]?\\s*([^\\n\\r,}]{2,60})",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern VPA_PATTERN =
            Pattern.compile("([a-zA-Z0-9._-]+)@[a-zA-Z0-9._-]+");
    private static final Pattern JSON_KEY_VALUE_FALLBACK =
            Pattern.compile("[\"' ]?([a-zA-Z_]+)[\"' ]?\\s*:\\s*(?:\"([^\"]*)\"|'([^']*)'|([^,}\\n\\r]+))");

    private static final String[] DATE_PATTERNS = new String[]{
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd",
            "dd MMM yyyy, h:mm a",
            "dd MMM yyyy h:mm a",
            "dd MMM yyyy, HH:mm",
            "d MMM yyyy, h:mm a",
            "dd MMM yyyy",
            "d MMM yyyy",
            "dd/MM/yyyy HH:mm",
            "dd/MM/yyyy h:mm a",
            "dd/MM/yyyy",
            "dd-MM-yyyy HH:mm",
            "dd-MM-yyyy"
    };

    private UpiReceiptPostProcessor() {
    }

    @NonNull
    public static ParsedReceipt process(@Nullable String rawModelOutput, long fallbackNowMillis) {
        String safeRaw = rawModelOutput != null ? rawModelOutput.trim() : "";
        List<String> warnings = new ArrayList<>();

        if (safeRaw.isEmpty()) {
            warnings.add("Empty OCR output — please enter transaction details manually.");
            return new ParsedReceipt(
                    0L,
                    "",
                    ParsedReceipt.ReceiptStatus.UNKNOWN,
                    ParsedReceipt.TransactionDirection.UNKNOWN,
                    fallbackNowMillis,
                    null,
                    PaymentMode.UPI,
                    "",
                    true,
                    false,
                    warnings
            );
        }

        ExtractedFields fields = parseJsonOrKeyValues(safeRaw);

        // 1. Amount extraction & 2-bit ₹ glyph repair
        AmountResult amountResult = resolveAmount(fields.amountRaw, safeRaw);
        if (amountResult.quantizationRepaired) {
            warnings.add("Verify amount: repaired possible ₹ symbol misread.");
        }
        if (amountResult.amountPaise <= 0L) {
            warnings.add("Could not confidently detect amount.");
        }

        // 2. Merchant extraction & VPA cleanup
        String merchant = resolveMerchant(fields.merchantRaw, safeRaw);
        if (merchant.isEmpty()) {
            warnings.add("Merchant name was not detected.");
        }

        // 3. Status (SUCCESS / FAILED / PENDING)
        ParsedReceipt.ReceiptStatus status = resolveStatus(fields.statusRaw, safeRaw);
        if (status == ParsedReceipt.ReceiptStatus.FAILED) {
            warnings.add("Payment appears FAILED — verify before saving.");
        } else if (status == ParsedReceipt.ReceiptStatus.PENDING) {
            warnings.add("Payment is PENDING — amount may not have settled.");
        }

        // 4. Direction (DEBIT / CREDIT / UNKNOWN)
        ParsedReceipt.TransactionDirection direction = resolveDirection(fields.directionRaw, safeRaw);
        if (direction == ParsedReceipt.TransactionDirection.CREDIT) {
            warnings.add("Incoming CREDIT detected — not an outgoing expense.");
        }

        // 5. 12-digit UPI UTR extraction
        String utr = resolveUtr(fields.referenceRaw, safeRaw);

        // 6. Date/time parsing
        long timestampMillis = resolveTimestamp(fields.dateTimeRaw, fallbackNowMillis);

        // 7. Payment mode inference
        PaymentMode paymentMode = resolvePaymentMode(fields.paymentModeRaw, safeRaw, utr);

        boolean partial = amountResult.amountPaise <= 0L || merchant.isEmpty() || !fields.parsedStructuredJson;
        if (!fields.parsedStructuredJson && amountResult.amountPaise > 0L) {
            warnings.add("Extracted from unstructured text — double-check fields.");
        }

        return new ParsedReceipt(
                amountResult.amountPaise,
                merchant,
                status,
                direction,
                timestampMillis,
                utr,
                paymentMode,
                safeRaw,
                partial,
                amountResult.quantizationRepaired,
                warnings
        );
    }

    private static final class ExtractedFields {
        @Nullable String amountRaw;
        @Nullable String merchantRaw;
        @Nullable String statusRaw;
        @Nullable String directionRaw;
        @Nullable String dateTimeRaw;
        @Nullable String referenceRaw;
        @Nullable String paymentModeRaw;
        boolean parsedStructuredJson;
    }

    private static final class AmountResult {
        final long amountPaise;
        final boolean quantizationRepaired;

        AmountResult(long amountPaise, boolean quantizationRepaired) {
            this.amountPaise = amountPaise;
            this.quantizationRepaired = quantizationRepaired;
        }
    }

    @NonNull
    private static ExtractedFields parseJsonOrKeyValues(@NonNull String raw) {
        ExtractedFields out = new ExtractedFields();
        String jsonCandidate = extractJsonObjectCandidate(raw);
        if (jsonCandidate != null) {
            JSONObject obj = tryParseRepairedJson(jsonCandidate);
            if (obj != null) {
                out.parsedStructuredJson = true;
                out.amountRaw = optStringAny(obj, "amount", "total", "value", "amount_inr");
                out.merchantRaw = optStringAny(obj, "merchant", "payee", "recipient", "to", "name");
                out.statusRaw = optStringAny(obj, "status", "payment_status", "state");
                out.directionRaw = optStringAny(obj, "direction", "type", "flow");
                out.dateTimeRaw = optStringAny(obj, "date_time", "datetime", "date", "timestamp");
                out.referenceRaw = optStringAny(obj, "reference_id", "utr", "upi_ref", "ref_id", "transaction_id");
                out.paymentModeRaw = optStringAny(obj, "payment_mode", "mode", "method");
                return out;
            }
        }

        // Fallback key: value extractor for truncated/malformed JSON from 2-bit quantization
        Matcher kvMatcher = JSON_KEY_VALUE_FALLBACK.matcher(raw);
        while (kvMatcher.find()) {
            String key = kvMatcher.group(1);
            if (key == null) continue;
            String val = firstNonNull(kvMatcher.group(2), kvMatcher.group(3), kvMatcher.group(4));
            if (val == null) continue;
            val = val.trim();
            if (val.equalsIgnoreCase("null") || val.isEmpty()) continue;

            switch (key.toLowerCase(Locale.ROOT)) {
                case "amount":
                case "total":
                case "amount_inr":
                    if (out.amountRaw == null) out.amountRaw = val;
                    break;
                case "merchant":
                case "payee":
                case "recipient":
                case "to":
                    if (out.merchantRaw == null) out.merchantRaw = val;
                    break;
                case "status":
                case "payment_status":
                    if (out.statusRaw == null) out.statusRaw = val;
                    break;
                case "direction":
                case "type":
                    if (out.directionRaw == null) out.directionRaw = val;
                    break;
                case "date_time":
                case "datetime":
                case "date":
                case "timestamp":
                    if (out.dateTimeRaw == null) out.dateTimeRaw = val;
                    break;
                case "reference_id":
                case "utr":
                case "upi_ref":
                case "ref_id":
                case "transaction_id":
                    if (out.referenceRaw == null) out.referenceRaw = val;
                    break;
                case "payment_mode":
                case "mode":
                case "method":
                    if (out.paymentModeRaw == null) out.paymentModeRaw = val;
                    break;
                default:
                    break;
            }
        }
        return out;
    }

    @Nullable
    private static String extractJsonObjectCandidate(@NonNull String raw) {
        Matcher fenced = CODE_FENCE_PATTERN.matcher(raw);
        if (fenced.find()) {
            return fenced.group(1);
        }
        int start = raw.indexOf('{');
        if (start < 0) {
            return null;
        }
        int end = raw.lastIndexOf('}');
        if (end > start) {
            return raw.substring(start, end + 1);
        }
        // Truncated JSON missing closing brace
        return raw.substring(start) + "}";
    }

    @Nullable
    private static JSONObject tryParseRepairedJson(@NonNull String candidate) {
        try {
            return new JSONObject(candidate);
        } catch (JSONException ignored) {
            // Attempt deterministic repairs for common 2-bit VLM JSON issues:
            // trailing commas, smart quotes, unquoted keys, single-quoted values.
            String repaired = candidate
                    .replace('\u201C', '"')
                    .replace('\u201D', '"')
                    .replace('\u2018', '\'')
                    .replace('\u2019', '\'')
                    .replaceAll(",\\s*}", "}")
                    .replaceAll("([{,]\\s*)([a-zA-Z_][a-zA-Z0-9_]*)\\s*:", "$1\"$2\":")
                    .replaceAll(":\\s*'([^']*)'", ": \"$1\"");
            try {
                return new JSONObject(repaired);
            } catch (JSONException ignoredAgain) {
                return null;
            }
        }
    }

    @NonNull
    private static AmountResult resolveAmount(@Nullable String amountField, @NonNull String fullRaw) {
        if (amountField != null && !amountField.trim().isEmpty()) {
            AmountResult fromField = parseAmountToken(amountField);
            if (fromField.amountPaise > 0L) {
                return fromField;
            }
        }

        Matcher currencyMatcher = CURRENCY_TOKEN_PATTERN.matcher(fullRaw);
        if (currencyMatcher.find()) {
            String fullMatch = currencyMatcher.group(0);
            String numPart = currencyMatcher.group(1);
            boolean repaired = fullMatch != null && (fullMatch.startsWith("?") || fullMatch.startsWith("R"));
            long paise = MoneyFormatter.parseRupeesToPaise(numPart);
            if (paise > 0L) {
                return new AmountResult(paise, repaired);
            }
        }

        Matcher labeledMatcher = AMOUNT_LABELED_PATTERN.matcher(fullRaw);
        if (labeledMatcher.find()) {
            String candidate = labeledMatcher.group(1);
            if (candidate != null) {
                AmountResult fromLabel = parseAmountToken(candidate);
                if (fromLabel.amountPaise > 0L) {
                    return fromLabel;
                }
            }
        }

        return new AmountResult(0L, false);
    }

    @NonNull
    static AmountResult parseAmountToken(@NonNull String rawToken) {
        String trimmed = rawToken.trim();
        boolean repaired = false;

        // Pattern 1: "2 450.00" or "2 1,250" (classic 2-bit VLM misreading ₹ as '2' + space)
        Matcher leadingTwo = LEADING_TWO_GLYPH_PATTERN.matcher(trimmed);
        if (leadingTwo.matches()) {
            trimmed = leadingTwo.group(1);
            repaired = true;
        } else if (trimmed.startsWith("2₹") || trimmed.startsWith("₹2 ")) {
            trimmed = trimmed.substring(2).trim();
            repaired = true;
        } else if (trimmed.startsWith("?") || trimmed.startsWith("¿")) {
            trimmed = trimmed.substring(1).trim();
            repaired = true;
        } else if (trimmed.matches("^R\\s*[0-9].*")) {
            // Single 'R' before digits (₹ misread as 'R')
            trimmed = trimmed.substring(1).trim();
            repaired = true;
        } else if (trimmed.matches("(?i)^(?:rs\\.?|inr)\\s*[0-9].*")) {
            trimmed = trimmed.replaceFirst("(?i)^(?:rs\\.?|inr)\\s*", "").trim();
        } else if (trimmed.startsWith("₹")) {
            trimmed = trimmed.substring(1).trim();
        }

        // Remove trailing currency suffix or stray OCR punctuation
        trimmed = trimmed.replaceAll("(?i)\\s*(?:inr|rs\\.?|only|/-)$", "").trim();
        // Keep only first valid numeric chunk if extra text trailed in
        Matcher numExtract = Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]{1,2})?)").matcher(trimmed);
        if (numExtract.find()) {
            long paise = MoneyFormatter.parseRupeesToPaise(numExtract.group(1));
            return new AmountResult(Math.max(0L, paise), repaired);
        }
        return new AmountResult(0L, repaired);
    }

    @NonNull
    private static String resolveMerchant(@Nullable String merchantField, @NonNull String fullRaw) {
        String candidate = merchantField;
        if (candidate == null || candidate.trim().isEmpty()) {
            Matcher lineMatcher = MERCHANT_LINE_PATTERN.matcher(fullRaw);
            if (lineMatcher.find()) {
                candidate = lineMatcher.group(1);
            }
        }
        if (candidate == null || candidate.trim().isEmpty()) {
            Matcher vpaMatcher = VPA_PATTERN.matcher(fullRaw);
            if (vpaMatcher.find()) {
                candidate = vpaMatcher.group(0);
            }
        }
        return cleanMerchantName(candidate);
    }

    @NonNull
    public static String cleanMerchantName(@Nullable String rawMerchant) {
        if (rawMerchant == null) {
            return "";
        }
        String s = rawMerchant.trim()
                .replaceAll("^[\"'`]+|[\"'`]+$", "")
                .replaceAll("(?i)^(?:paid\\s+to|payment\\s+to|sent\\s+to|transfer\\s+to|received\\s+from|to)\\s*[:\\-]?\\s*", "")
                .trim();

        if (s.isEmpty() || s.equalsIgnoreCase("null") || s.equalsIgnoreCase("unknown")) {
            return "";
        }

        // If merchant is a raw UPI VPA like "swiggy.online@icici" or "zomato@hdfcbank", clean handle
        Matcher vpaMatcher = VPA_PATTERN.matcher(s);
        if (vpaMatcher.matches()) {
            String localPart = vpaMatcher.group(1);
            if (localPart != null) {
                s = localPart
                        .replaceAll("(?i)[._-]?(?:pay|upi|online|merchant|razorpay|paytm|gpay|okaxis|okicici)$", "")
                        .replace('.', ' ')
                        .replace('_', ' ')
                        .replace('-', ' ')
                        .replaceAll("\\s+", " ")
                        .trim();
                if (s.isEmpty()) {
                    s = localPart;
                }
            }
        } else {
            // Strip parenthetical VPA suffix: "Swiggy (swiggy@icici)" -> "Swiggy"
            s = s.replaceAll("\\s*\\([^)]*@[^)]*\\)", "").trim();
            // Strip inline VPA appended after merchant name: "Zomato zomato@okaxis"
            s = s.replaceAll("\\s+[a-zA-Z0-9._-]+@[a-zA-Z0-9._-]+", "").trim();
        }

        // Strip trailing status noise if VLM concatenated it
        s = s.replaceAll("(?i)\\s+(?:paid|completed|successful|success|failed|pending)$", "").trim();
        s = s.replaceAll("\\s+", " ");

        // Title-case all-lowercase or all-uppercase single/multi words cleanly
        if (s.equals(s.toLowerCase(Locale.ROOT)) || (s.length() > 4 && s.equals(s.toUpperCase(Locale.ROOT)))) {
            s = toTitleCase(s);
        }
        return s;
    }

    @NonNull
    private static ParsedReceipt.ReceiptStatus resolveStatus(@Nullable String statusField, @NonNull String fullRaw) {
        String primary = statusField != null ? statusField.toUpperCase(Locale.ROOT) : "";
        String combined = (primary + " " + fullRaw).toUpperCase(Locale.ROOT);

        if (containsAny(primary, "FAIL", "DECLIN", "UNSUCCESS", "REVERS", "CANCEL", "ERROR")
                || containsAny(combined, "PAYMENT FAILED", "TRANSACTION FAILED", "DECLINED", "UNSUCCESSFUL", "REVERSED")) {
            return ParsedReceipt.ReceiptStatus.FAILED;
        }
        if (containsAny(primary, "PEND", "PROCESS", "AWAIT", "HOLD")
                || containsAny(combined, "PAYMENT PENDING", "TRANSACTION PENDING", "PROCESSING", "IN PROGRESS")) {
            return ParsedReceipt.ReceiptStatus.PENDING;
        }
        if (containsAny(primary, "SUCCESS", "COMPLET", "PAID", "DONE", "SETTLED", "OK")
                || containsAny(combined, "PAYMENT SUCCESSFUL", "PAID TO", "COMPLETED", "TRANSACTION SUCCESSFUL", "SENT SUCCESSFULLY", "DEBITED")) {
            return ParsedReceipt.ReceiptStatus.SUCCESS;
        }
        return ParsedReceipt.ReceiptStatus.UNKNOWN;
    }

    @NonNull
    private static ParsedReceipt.TransactionDirection resolveDirection(
            @Nullable String directionField,
            @NonNull String fullRaw
    ) {
        String primary = directionField != null ? directionField.toUpperCase(Locale.ROOT) : "";
        String combined = (primary + " " + fullRaw).toUpperCase(Locale.ROOT);

        if (containsAny(primary, "CREDIT", "INCOMING", "RECEIV", "REFUND", "CASHBACK")
                || containsAny(combined, "RECEIVED FROM", "CREDITED TO", "MONEY RECEIVED", "CASHBACK RECEIVED", "REFUND RECEIVED")) {
            return ParsedReceipt.TransactionDirection.CREDIT;
        }
        if (containsAny(primary, "DEBIT", "OUTGOING", "PAID", "SENT", "EXPENSE")
                || containsAny(combined, "PAID TO", "PAYMENT TO", "SENT TO", "DEBITED FROM", "DEBITED")) {
            return ParsedReceipt.TransactionDirection.DEBIT;
        }
        return ParsedReceipt.TransactionDirection.UNKNOWN;
    }

    @Nullable
    private static String resolveUtr(@Nullable String refField, @NonNull String fullRaw) {
        if (refField != null && !refField.trim().isEmpty()) {
            String digitsOnly = refField.replaceAll("\\D", "");
            if (digitsOnly.length() == 12) {
                return digitsOnly;
            }
            Matcher inField = UTR_RAW_12_PATTERN.matcher(refField);
            if (inField.find()) {
                return inField.group(1);
            }
        }

        Matcher labeled = UTR_LABELED_PATTERN.matcher(fullRaw);
        if (labeled.find()) {
            String captured = labeled.group(1);
            if (captured != null) {
                String digits = captured.replaceAll("\\D", "");
                if (digits.length() == 12) {
                    return digits;
                }
            }
        }

        Matcher raw12 = UTR_RAW_12_PATTERN.matcher(fullRaw);
        if (raw12.find()) {
            return raw12.group(1);
        }
        return null;
    }

    private static long resolveTimestamp(@Nullable String dateTimeField, long fallbackNowMillis) {
        if (dateTimeField == null || dateTimeField.trim().isEmpty()) {
            return fallbackNowMillis;
        }
        String cleaned = dateTimeField.trim()
                .replace(" at ", " ")
                .replace(" IST", "")
                .replaceAll("\\s+", " ");
        for (String pattern : DATE_PATTERNS) {
            SimpleDateFormat fmt = new SimpleDateFormat(pattern, Locale.ENGLISH);
            fmt.setLenient(false);
            try {
                Date parsed = fmt.parse(cleaned);
                if (parsed != null) {
                    return parsed.getTime();
                }
            } catch (ParseException ignored) {
                // Try next format
            }
        }
        return fallbackNowMillis;
    }

    @NonNull
    private static PaymentMode resolvePaymentMode(
            @Nullable String modeField,
            @NonNull String fullRaw,
            @Nullable String utr
    ) {
        if (modeField != null && !modeField.trim().isEmpty()) {
            String upper = modeField.toUpperCase(Locale.ROOT);
            if (upper.contains("CARD") || upper.contains("VISA") || upper.contains("MASTERCARD") || upper.contains("RUPAY")) {
                return PaymentMode.CARD;
            }
            if (upper.contains("CASH")) {
                return PaymentMode.CASH;
            }
            if (upper.contains("UPI")) {
                return PaymentMode.UPI;
            }
        }
        String upperRaw = fullRaw.toUpperCase(Locale.ROOT);
        if (utr != null || upperRaw.contains("UPI") || upperRaw.contains("GPAY")
                || upperRaw.contains("GOOGLE PAY") || upperRaw.contains("PHONEPE")
                || upperRaw.contains("PAYTM") || upperRaw.contains("@")) {
            return PaymentMode.UPI;
        }
        if (upperRaw.contains("CREDIT CARD") || upperRaw.contains("DEBIT CARD")
                || upperRaw.contains("VISA") || upperRaw.contains("MASTERCARD")) {
            return PaymentMode.CARD;
        }
        return PaymentMode.UPI;
    }

    @Nullable
    private static String optStringAny(@NonNull JSONObject obj, @NonNull String... keys) {
        for (String key : keys) {
            if (obj.has(key) && !obj.isNull(key)) {
                Object val = obj.opt(key);
                if (val != null) {
                    String s = String.valueOf(val).trim();
                    if (!s.isEmpty() && !s.equalsIgnoreCase("null")) {
                        return s;
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    private static String firstNonNull(@Nullable String a, @Nullable String b, @Nullable String c) {
        if (a != null) return a;
        if (b != null) return b;
        return c;
    }

    private static boolean containsAny(@NonNull String haystack, @NonNull String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private static String toTitleCase(@NonNull String input) {
        String[] parts = input.split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) {
                sb.append(p.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.toString();
    }
}
