package com.example.util

import com.example.data.model.RecurrenceFrequency
import com.example.data.preferences.CurrencyFormatConfig
import com.example.data.preferences.CurrencyOption
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToLong

object DateUtils {
    private const val MILLIS_PER_DAY = 86_400_000L
    private val utcTimeZone: TimeZone = TimeZone.getTimeZone("UTC")

    /**
     * Returns today's local calendar date encoded as days since 1970-01-01 (UTC midnight convention).
     * Works safely on API 24+ without timezone drift when comparing dates.
     */
    fun todayEpochDay(): Long {
        val local = Calendar.getInstance()
        return epochDayFromYmd(
            year = local.get(Calendar.YEAR),
            month = local.get(Calendar.MONTH) + 1,
            day = local.get(Calendar.DAY_OF_MONTH),
        )
    }

    /**
     * Converts (year, 1-based month, dayOfMonth) into an epoch day count.
     */
    fun epochDayFromYmd(year: Int, month: Int, day: Int): Long {
        val cal = utcCalendar().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
        }
        return floorDiv(cal.timeInMillis, MILLIS_PER_DAY)
    }

    /**
     * Converts an epoch day count into Triple(year, 1-based month, dayOfMonth).
     */
    fun ymdFromEpochDay(epochDay: Long): Triple<Int, Int, Int> {
        val cal = utcCalendar().apply {
            timeInMillis = epochDay * MILLIS_PER_DAY
        }
        return Triple(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
    }

    /**
     * Material 3 DatePicker returns UTC midnight timestamps in milliseconds.
     */
    fun utcMillisToEpochDay(utcMillis: Long): Long = floorDiv(utcMillis, MILLIS_PER_DAY)

    fun epochDayToUtcMillis(epochDay: Long): Long = epochDay * MILLIS_PER_DAY

    fun startOfMonthEpochDay(referenceEpochDay: Long = todayEpochDay()): Long {
        val (year, month, _) = ymdFromEpochDay(referenceEpochDay)
        return epochDayFromYmd(year, month, 1)
    }

    fun endOfMonthEpochDay(referenceEpochDay: Long = todayEpochDay()): Long {
        val (year, month, _) = ymdFromEpochDay(referenceEpochDay)
        val cal = utcCalendar().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return epochDayFromYmd(year, month, maxDay)
    }

    fun startOfYearEpochDay(referenceEpochDay: Long = todayEpochDay()): Long {
        val (year, _, _) = ymdFromEpochDay(referenceEpochDay)
        return epochDayFromYmd(year, 1, 1)
    }

    /**
     * Advances a recurring expense due date cleanly while preserving the anchor day-of-month
     * (e.g., Jan 31 -> Feb 28 -> Mar 31) and leap-year safety.
     */
    fun nextDueDate(
        anchorStartEpochDay: Long,
        currentDueEpochDay: Long,
        frequency: RecurrenceFrequency,
    ): Long {
        val (_, anchorMonth, anchorDay) = ymdFromEpochDay(anchorStartEpochDay)
        val (curYear, curMonth, _) = ymdFromEpochDay(currentDueEpochDay)

        return when (frequency) {
            RecurrenceFrequency.DAILY -> currentDueEpochDay + 1L
            RecurrenceFrequency.WEEKLY -> currentDueEpochDay + 7L
            RecurrenceFrequency.MONTHLY -> {
                var targetYear = curYear
                var targetMonth = curMonth + 1
                if (targetMonth > 12) {
                    targetMonth = 1
                    targetYear += 1
                }
                val maxDay = daysInMonth(targetYear, targetMonth)
                epochDayFromYmd(targetYear, targetMonth, min(anchorDay, maxDay))
            }
            RecurrenceFrequency.YEARLY -> {
                val targetYear = curYear + 1
                val maxDay = daysInMonth(targetYear, anchorMonth)
                epochDayFromYmd(targetYear, anchorMonth, min(anchorDay, maxDay))
            }
        }
    }

    fun daysInMonth(year: Int, month: Int): Int {
        val cal = utcCalendar().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    fun startOfWeekEpochDay(epochDay: Long, startOnSunday: Boolean = false): Long {
        val cal = utcCalendar().apply {
            timeInMillis = epochDay * MILLIS_PER_DAY
        }
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // Sun=1 .. Sat=7
        val diff = if (startOnSunday) {
            dayOfWeek - 1
        } else {
            (dayOfWeek + 5) % 7
        }
        return epochDay - diff
    }

    fun formatRelativeDateHeader(epochDay: Long, today: Long = todayEpochDay()): String {
        return when (epochDay) {
            today -> "Today"
            today - 1L -> "Yesterday"
            today + 1L -> "Tomorrow"
            else -> formatWithPattern(epochDay, "EEE, MMM d, yyyy")
        }
    }

    fun formatShortDate(epochDay: Long): String = formatWithPattern(epochDay, "MMM d, yyyy")

    fun formatCompactDay(epochDay: Long): String = formatWithPattern(epochDay, "MMM d")

    fun formatMonthShort(epochDay: Long): String = formatWithPattern(epochDay, "MMM ''yy")

    fun formatMonthYear(epochDay: Long): String = formatWithPattern(epochDay, "MMMM yyyy")

    fun formatDateRange(startEpochDay: Long, endEpochDay: Long): String {
        if (startEpochDay <= 0L || endEpochDay <= 0L) return ""
        return if (startEpochDay == endEpochDay) {
            formatShortDate(startEpochDay)
        } else {
            "${formatCompactDay(startEpochDay)} – ${formatShortDate(endEpochDay)}"
        }
    }

    fun formatDueCountdown(nextDueEpochDay: Long, today: Long = todayEpochDay()): String {
        val diff = nextDueEpochDay - today
        return when {
            diff < 0L -> "Overdue by ${abs(diff)}d"
            diff == 0L -> "Due today"
            diff == 1L -> "Due tomorrow"
            diff < 7L -> "In $diff days"
            else -> "Due ${formatCompactDay(nextDueEpochDay)}"
        }
    }

    private fun formatWithPattern(epochDay: Long, pattern: String): String {
        val formatter = SimpleDateFormat(pattern, Locale.getDefault()).apply {
            timeZone = utcTimeZone
        }
        return formatter.format(Date(epochDay * MILLIS_PER_DAY))
    }

    private fun utcCalendar(): Calendar = Calendar.getInstance(utcTimeZone).apply {
        clear()
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun floorDiv(a: Long, b: Long): Long {
        var r = a / b
        if ((a xor b) < 0L && r * b != a) r--
        return r
    }
}

object CurrencyUtils {

    fun formatCents(
        amountCents: Long,
        config: CurrencyFormatConfig = CurrencyFormatConfig(),
    ): String = formatCents(
        amountCents = amountCents,
        currency = config.currency,
        showDecimals = config.showDecimals,
    )

    fun formatCents(
        amountCents: Long,
        currency: CurrencyOption,
        showDecimals: Boolean = true,
    ): String {
        val majorAmount = amountCents / 100.0
        val fractionDigits = if (showDecimals) currency.defaultFractionDigits else 0
        val negative = majorAmount < 0.0
        val absMajor = abs(majorAmount)

        val formattedNumber = if (currency == CurrencyOption.INR) {
            formatIndianNumber(absMajor, fractionDigits)
        } else {
            val nf = NumberFormat.getNumberInstance(currency.locale).apply {
                minimumFractionDigits = fractionDigits
                maximumFractionDigits = fractionDigits
                isGroupingUsed = true
                roundingMode = java.math.RoundingMode.HALF_UP
            }
            nf.format(absMajor)
        }

        val spacer = if (currency.symbol.length > 2) " " else ""
        val prefix = if (negative) "-${currency.symbol}$spacer" else "${currency.symbol}$spacer"
        return "$prefix$formattedNumber"
    }

    fun formatCompactCents(
        amountCents: Long,
        config: CurrencyFormatConfig = CurrencyFormatConfig(),
    ): String {
        val currency = config.currency
        val major = amountCents / 100.0
        val sym = currency.symbol
        val spacer = if (sym.length > 2) " " else ""
        return when {
            major >= 1_000_000 -> String.format(Locale.US, "%s%s%.1fM", sym, spacer, major / 1_000_000.0)
            major >= 1_000 -> String.format(Locale.US, "%s%s%.1fk", sym, spacer, major / 1_000.0)
            major == major.toLong().toDouble() -> String.format(Locale.US, "%s%s%d", sym, spacer, major.toLong())
            else -> String.format(Locale.US, "%s%s%.0f", sym, spacer, major)
        }
    }

    fun parseDollarsToCents(input: String): Long? {
        val cleaned = input.trim()
            .replace(",", "")
            .replace("$", "")
            .replace("₹", "")
            .replace("€", "")
            .replace("£", "")
            .replace("¥", "")
            .replace("CHF", "")
            .replace("AED", "")
            .trim()
        if (cleaned.isEmpty()) return null
        val value = cleaned.toDoubleOrNull() ?: return null
        if (value <= 0.0 || value > 999_999_999.99) return null
        return (value * 100.0).roundToLong()
    }

    fun centsToInputString(
        amountCents: Long,
        currency: CurrencyOption = CurrencyOption.USD,
    ): String {
        return if (currency.defaultFractionDigits == 0) {
            (amountCents / 100.0).roundToLong().toString()
        } else {
            String.format(Locale.US, "%.2f", amountCents / 100.0)
        }
    }

    /**
     * Formats numbers with authentic Indian 3-2-2 lakh/crore grouping (e.g., 1,25,000.00).
     */
    private fun formatIndianNumber(value: Double, fractionDigits: Int): String {
        val rounded = if (fractionDigits == 0) {
            value.roundToLong().toDouble()
        } else {
            (value * 100.0).roundToLong() / 100.0
        }
        val whole = rounded.toLong()
        val wholeStr = whole.toString()
        val groupedWhole = if (wholeStr.length <= 3) {
            wholeStr
        } else {
            val lastThree = wholeStr.takeLast(3)
            val rest = wholeStr.dropLast(3)
            val restGrouped = rest.reversed().chunked(2).joinToString(",").reversed()
            "$restGrouped,$lastThree"
        }
        if (fractionDigits == 0) return groupedWhole
        val decFormat = DecimalFormat(".00", DecimalFormatSymbols(Locale.US))
        val fractionPart = decFormat.format(rounded - whole)
        return groupedWhole + fractionPart
    }
}
