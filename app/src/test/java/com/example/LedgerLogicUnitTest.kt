package com.example

import com.example.data.local.CategoryEntity
import com.example.data.local.ExpenseEntity
import com.example.data.model.RecurrenceFrequency
import com.example.ui.state.DateRangePreset
import com.example.ui.state.ReportAnalytics
import com.example.ui.state.ReportFilterState
import com.example.ui.state.TimeGranularity
import com.example.util.CurrencyUtils
import com.example.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerLogicUnitTest {

    @Test
    fun nextDueDate_handlesAllFrequenciesAndMonthEndAnchor() {
        // Monthly anchor on Jan 31 -> Feb 28 (non-leap 2026) -> Mar 31
        val jan31 = DateUtils.epochDayFromYmd(2026, 1, 31)
        val feb28 = DateUtils.nextDueDate(jan31, jan31, RecurrenceFrequency.MONTHLY)
        assertEquals(Triple(2026, 2, 28), DateUtils.ymdFromEpochDay(feb28))

        val mar31 = DateUtils.nextDueDate(jan31, feb28, RecurrenceFrequency.MONTHLY)
        assertEquals(Triple(2026, 3, 31), DateUtils.ymdFromEpochDay(mar31))

        // Daily & Weekly
        assertEquals(jan31 + 1L, DateUtils.nextDueDate(jan31, jan31, RecurrenceFrequency.DAILY))
        assertEquals(jan31 + 7L, DateUtils.nextDueDate(jan31, jan31, RecurrenceFrequency.WEEKLY))

        // Yearly leap-day anchor: Feb 29, 2024 -> Feb 28, 2025
        val leapDay = DateUtils.epochDayFromYmd(2024, 2, 29)
        val nextYear = DateUtils.nextDueDate(leapDay, leapDay, RecurrenceFrequency.YEARLY)
        assertEquals(Triple(2025, 2, 28), DateUtils.ymdFromEpochDay(nextYear))
    }

    @Test
    fun reportAnalytics_filtersByDateRangeAndCategory() {
        val food = CategoryEntity(id = 1L, name = "Food", colorHex = "#0F523E")
        val travel = CategoryEntity(id = 2L, name = "Travel", colorHex = null)
        val categoriesById = mapOf(1L to food, 2L to travel)

        val today = DateUtils.epochDayFromYmd(2026, 11, 22)
        val expenses = listOf(
            ExpenseEntity(id = 1, title = "Market", amountCents = 4000, categoryId = 1L, dateEpochDay = today),
            ExpenseEntity(id = 2, title = "Train", amountCents = 6000, categoryId = 2L, dateEpochDay = today - 2),
            ExpenseEntity(id = 3, title = "Old Flight", amountCents = 25000, categoryId = 2L, dateEpochDay = today - 40),
        )

        // Last 7 days, all categories -> includes Market ($40) + Train ($60) = $100
        val reportAll = ReportAnalytics.computeReport(
            expenses = expenses,
            categoriesById = categoriesById,
            filter = ReportFilterState(preset = DateRangePreset.LAST_7_DAYS),
            todayEpochDay = today,
        )
        assertEquals(10_000L, reportAll.totalCents)
        assertEquals(2, reportAll.transactionCount)
        assertEquals(2, reportAll.categorySlices.size)
        assertEquals("Travel", reportAll.topCategoryName)
        assertEquals(0.6f, reportAll.categorySlices[0].percentage, 0.001f)
        assertEquals(0.4f, reportAll.categorySlices[1].percentage, 0.001f)
        assertEquals(TimeGranularity.DAILY, reportAll.effectiveGranularity)
        assertEquals(7, reportAll.timeBuckets.size)

        // Category filter for Food only -> $40
        val reportFood = ReportAnalytics.computeReport(
            expenses = expenses,
            categoriesById = categoriesById,
            filter = ReportFilterState(
                preset = DateRangePreset.LAST_7_DAYS,
                selectedCategoryIds = setOf(1L),
            ),
            todayEpochDay = today,
        )
        assertEquals(4_000L, reportFood.totalCents)
        assertEquals(1, reportFood.transactionCount)
        assertEquals("Food", reportFood.topCategoryName)
    }

    @Test
    fun currencyParsing_validatesAndConvertsCorrectly() {
        assertEquals(1250L, CurrencyUtils.parseDollarsToCents("12.50"))
        assertEquals(999L, CurrencyUtils.parseDollarsToCents("$9.99"))
        assertEquals(25000L, CurrencyUtils.parseDollarsToCents("₹250.00"))
        assertNull(CurrencyUtils.parseDollarsToCents("0"))
        assertNull(CurrencyUtils.parseDollarsToCents("-5.00"))
        assertNull(CurrencyUtils.parseDollarsToCents("abc"))
        assertTrue(CurrencyUtils.formatCents(1250L).contains("12.50"))
    }

    @Test
    fun multiCurrencyFormatting_supportsUsdInrEurJpyAndDecimalsToggle() {
        assertEquals("$1,250.50", CurrencyUtils.formatCents(125_050L, com.example.data.preferences.CurrencyOption.USD, true))
        assertEquals("$1,251", CurrencyUtils.formatCents(125_050L, com.example.data.preferences.CurrencyOption.USD, false))
        assertEquals("₹1,25,000.00", CurrencyUtils.formatCents(12_500_000L, com.example.data.preferences.CurrencyOption.INR, true))
        assertTrue(CurrencyUtils.formatCents(125_000L, com.example.data.preferences.CurrencyOption.EUR, true).startsWith("€"))
        assertEquals("¥1,250", CurrencyUtils.formatCents(125_000L, com.example.data.preferences.CurrencyOption.JPY, true))
    }

    @Test
    fun budgetAnalytics_detectsBreachesWarningsOnTrackAndScopesToCurrentMonth() {
        val food = CategoryEntity(id = 1L, name = "Food", colorHex = "#0F523E", monthlyBudgetCents = 20_000L)
        val transit = CategoryEntity(id = 2L, name = "Transit", colorHex = "#007AFF", monthlyBudgetCents = 10_000L)
        val leisure = CategoryEntity(id = 3L, name = "Leisure", colorHex = "#AF52DE", monthlyBudgetCents = 15_000L)
        val misc = CategoryEntity(id = 4L, name = "Misc", colorHex = null, monthlyBudgetCents = null)

        val nov15 = DateUtils.epochDayFromYmd(2026, 11, 15)
        val oct20 = DateUtils.epochDayFromYmd(2026, 10, 20)

        val expenses = listOf(
            // Food: $250 in Nov -> BREACHED by $50 ($200 limit)
            ExpenseEntity(id = 1, title = "Groceries", amountCents = 15_000L, categoryId = 1L, dateEpochDay = nov15),
            ExpenseEntity(id = 2, title = "Dinner", amountCents = 10_000L, categoryId = 1L, dateEpochDay = nov15 - 2),
            // Food in Oct should NOT count toward Nov budget
            ExpenseEntity(id = 3, title = "Old Oct Groceries", amountCents = 50_000L, categoryId = 1L, dateEpochDay = oct20),
            // Transit: $85 in Nov -> WARNING (85% of $100 limit)
            ExpenseEntity(id = 4, title = "Metro Pass", amountCents = 8_500L, categoryId = 2L, dateEpochDay = nov15),
            // Leisure: $40 in Nov -> ON_TRACK (<80% of $150 limit)
            ExpenseEntity(id = 5, title = "Cinema", amountCents = 4_000L, categoryId = 3L, dateEpochDay = nov15),
            // Misc: $30 in Nov -> unbudgeted
            ExpenseEntity(id = 6, title = "Battery", amountCents = 3_000L, categoryId = 4L, dateEpochDay = nov15),
        )

        val summary = com.example.ui.state.BudgetAnalytics.evaluateMonthlyBudgets(
            categories = listOf(misc, leisure, transit, food),
            expenses = expenses,
            referenceEpochDay = nov15,
        )

        assertTrue(summary.hasBreaches)
        assertTrue(summary.hasWarnings)
        assertEquals(3, summary.budgetedCategoryCount)
        assertEquals(45_000L, summary.totalBudgetedLimitCents)
        assertEquals(37_500L, summary.totalSpentInBudgetedCents)
        assertEquals(5_000L, summary.totalOverageCents)

        // Sorted with BREACHED first, then WARNING, then ON_TRACK
        assertEquals(1, summary.breachedItems.size)
        assertEquals("Food", summary.breachedItems[0].category.name)
        assertEquals(5_000L, summary.breachedItems[0].overageCents)
        assertEquals(com.example.ui.state.BudgetHealthStatus.BREACHED, summary.budgetedItems[0].status)
        assertEquals(com.example.ui.state.BudgetHealthStatus.WARNING, summary.budgetedItems[1].status)
        assertEquals(com.example.ui.state.BudgetHealthStatus.ON_TRACK, summary.budgetedItems[2].status)

        assertEquals(1, summary.unbudgetedItems.size)
        assertEquals("Misc", summary.unbudgetedItems[0].category.name)
        assertEquals(3_000L, summary.unbudgetedItems[0].spentThisMonthCents)
    }
}
