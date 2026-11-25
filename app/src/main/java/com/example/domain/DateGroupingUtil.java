package com.example.domain;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Date range calculations and human-friendly date section headers.
 */
public final class DateGroupingUtil {

    private static final Locale LOCALE_IN = new Locale("en", "IN");

    private DateGroupingUtil() {
    }

    public static long startOfMonthMillis(long referenceMillis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(referenceMillis);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        zeroTime(cal);
        return cal.getTimeInMillis();
    }

    public static long endOfMonthExclusiveMillis(long referenceMillis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(startOfMonthMillis(referenceMillis));
        cal.add(Calendar.MONTH, 1);
        return cal.getTimeInMillis();
    }

    public static long startOfPreviousMonthMillis(long referenceMillis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(startOfMonthMillis(referenceMillis));
        cal.add(Calendar.MONTH, -1);
        return cal.getTimeInMillis();
    }

    public static long daysAgoStartMillis(int daysAgo, long referenceMillis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(referenceMillis);
        zeroTime(cal);
        cal.add(Calendar.DAY_OF_YEAR, -Math.max(0, daysAgo));
        return cal.getTimeInMillis();
    }

    public static long startOfDayMillis(long timestampMillis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestampMillis);
        zeroTime(cal);
        return cal.getTimeInMillis();
    }

    @NonNull
    public static String formatDayHeader(long timestampMillis, long nowMillis) {
        long targetDay = startOfDayMillis(timestampMillis);
        long today = startOfDayMillis(nowMillis);
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(today);
        cal.add(Calendar.DAY_OF_YEAR, -1);
        long yesterday = cal.getTimeInMillis();

        if (targetDay == today) {
            return "Today";
        }
        if (targetDay == yesterday) {
            return "Yesterday";
        }
        SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy", LOCALE_IN);
        return fmt.format(new Date(timestampMillis));
    }

    @NonNull
    public static String formatTimeShort(long timestampMillis) {
        SimpleDateFormat fmt = new SimpleDateFormat("h:mm a", LOCALE_IN);
        return fmt.format(new Date(timestampMillis));
    }

    @NonNull
    public static String formatDateShort(long timestampMillis) {
        SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy", LOCALE_IN);
        return fmt.format(new Date(timestampMillis));
    }

    @NonNull
    public static String formatMonthLabel(long referenceMillis) {
        SimpleDateFormat fmt = new SimpleDateFormat("MMMM yyyy", LOCALE_IN);
        return fmt.format(new Date(referenceMillis));
    }

    @NonNull
    public static String toMonthKey(long referenceMillis) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM", Locale.US);
        return fmt.format(new Date(referenceMillis));
    }

    private static void zeroTime(@NonNull Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }
}
