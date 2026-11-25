package com.example.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Exact INR monetary formatting and parsing backed strictly by minor units (paise as long).
 * Never uses double or float for monetary math.
 */
public final class MoneyFormatter {

    private static final Locale LOCALE_IN = new Locale("en", "IN");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100L);
    private static final long MAX_SUPPORTED_PAISE = 999_999_999_999L; // ₹9,99,99,99,999.99

    private MoneyFormatter() {
    }

    @NonNull
    public static String formatInr(long amountPaise) {
        boolean wholeRupees = (amountPaise % 100L) == 0L;
        return formatWithLocaleIn(amountPaise, wholeRupees ? 0 : 2);
    }

    @NonNull
    public static String formatInrExactDecimals(long amountPaise) {
        return formatWithLocaleIn(amountPaise, 2);
    }

    @NonNull
    private static String formatWithLocaleIn(long amountPaise, int minFractionDigits) {
        BigDecimal rupees = BigDecimal.valueOf(amountPaise).movePointLeft(2);
        NumberFormat nf = NumberFormat.getCurrencyInstance(LOCALE_IN);
        nf.setRoundingMode(RoundingMode.HALF_UP);
        nf.setMinimumFractionDigits(minFractionDigits);
        nf.setMaximumFractionDigits(2);
        String formatted = nf.format(rupees);

        // Normalize currency prefix variants across JDK/Android locales
        boolean negative = amountPaise < 0L;
        String unsigned = formatted
                .replace("-", "")
                .replace("₹", "")
                .replace("Rs.", "")
                .replace("INR", "")
                .replace("\u00A0", "")
                .trim();

        // Enforce authentic Indian 3-2-2 grouping (e.g., 1,25,000) consistently across JDK and Android
        String grouped = applyIndianGrouping(unsigned);
        return (negative ? "-₹" : "₹") + grouped;
    }

    @NonNull
    private static String applyIndianGrouping(@NonNull String formattedNumber) {
        String plain = formattedNumber.replace(",", "");
        int dotIdx = plain.indexOf('.');
        String intPart = dotIdx >= 0 ? plain.substring(0, dotIdx) : plain;
        String fracPart = dotIdx >= 0 ? plain.substring(dotIdx) : "";

        if (intPart.length() <= 3) {
            return intPart + fracPart;
        }
        String lastThree = intPart.substring(intPart.length() - 3);
        String leading = intPart.substring(0, intPart.length() - 3);
        StringBuilder sb = new StringBuilder();
        int rem = leading.length() % 2;
        if (rem > 0) {
            sb.append(leading, 0, rem).append(',');
        }
        for (int i = rem; i < leading.length(); i += 2) {
            sb.append(leading, i, i + 2).append(',');
        }
        sb.append(lastThree).append(fracPart);
        return sb.toString();
    }

    /**
     * Compact Indian scale representation for tight chart centers (e.g., ₹840, ₹12.5k, ₹1.45L).
     */
    @NonNull
    public static String formatCompactInr(long amountPaise) {
        long absPaise = Math.abs(amountPaise);
        String sign = amountPaise < 0L ? "-" : "";
        if (absPaise >= 10_000_000L * 100L) {
            // Crores (1 Cr = 1,00,00,000 INR = 1,000,000,000 paise)
            BigDecimal crores = BigDecimal.valueOf(absPaise)
                    .divide(BigDecimal.valueOf(1_000_000_000L), 2, RoundingMode.HALF_UP)
                    .stripTrailingZeros();
            return sign + "₹" + crores.toPlainString() + "Cr";
        }
        if (absPaise >= 100_000L * 100L) {
            // Lakhs (1 L = 1,00,000 INR = 10,000,000 paise)
            BigDecimal lakhs = BigDecimal.valueOf(absPaise)
                    .divide(BigDecimal.valueOf(10_000_000L), 2, RoundingMode.HALF_UP)
                    .stripTrailingZeros();
            return sign + "₹" + lakhs.toPlainString() + "L";
        }
        if (absPaise >= 10_000L * 100L) {
            // Thousands from 10k+
            BigDecimal thousands = BigDecimal.valueOf(absPaise)
                    .divide(BigDecimal.valueOf(100_000L), 1, RoundingMode.HALF_UP)
                    .stripTrailingZeros();
            return sign + "₹" + thousands.toPlainString() + "k";
        }
        return formatInr(amountPaise);
    }

    /**
     * Formats paise as a plain decimal string (e.g., "125.50") suitable for CSV exports and inputs.
     */
    @NonNull
    public static String formatPaiseToDecimal(long amountPaise) {
        return BigDecimal.valueOf(amountPaise)
                .movePointLeft(2)
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    /**
     * Formats paise for an editable text field (omits .00 for clean editing).
     */
    @NonNull
    public static String formatPaiseForInput(long amountPaise) {
        if (amountPaise <= 0L) {
            return "";
        }
        if (amountPaise % 100L == 0L) {
            return Long.toString(amountPaise / 100L);
        }
        return formatPaiseToDecimal(amountPaise);
    }

    /**
     * Parses a user-entered INR rupee string (allowing ₹, commas, spaces) into exact paise.
     * Returns -1L if the input is blank, invalid, negative, or exceeds supported bounds.
     */
    public static long parseRupeesToPaise(@Nullable String rawInput) {
        if (rawInput == null) {
            return -1L;
        }
        String cleaned = rawInput
                .replace("₹", "")
                .replace("INR", "")
                .replace("Rs.", "")
                .replace("Rs", "")
                .replace(",", "")
                .replace(" ", "")
                .trim();
        if (cleaned.isEmpty()) {
            return -1L;
        }
        try {
            BigDecimal rupees = new BigDecimal(cleaned);
            if (rupees.signum() <= 0) {
                return -1L;
            }
            BigDecimal paiseDecimal = rupees.multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP);
            if (paiseDecimal.compareTo(BigDecimal.valueOf(MAX_SUPPORTED_PAISE)) > 0) {
                return -1L;
            }
            return paiseDecimal.longValueExact();
        } catch (NumberFormatException | ArithmeticException ex) {
            return -1L;
        }
    }
}
