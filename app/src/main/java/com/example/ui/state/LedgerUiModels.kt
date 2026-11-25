package com.example.ui.state

import com.example.data.local.CategoryEntity
import com.example.data.local.ExpenseEntity
import com.example.data.local.RecurringExpenseEntity

const val UNCATEGORIZED_FILTER_ID = -1L

enum class LedgerTab(val label: String) {
    EXPENSES("Dashboard"),
    BUDGETS("Budgets"),
    REPORTS("Reports"),
    RECURRING("Recurring"),
    CATEGORIES("Categories"),
}

data class ExpenseWithCategory(
    val expense: ExpenseEntity,
    val category: CategoryEntity?,
)

data class DailyExpenseGroup(
    val dateEpochDay: Long,
    val headerLabel: String,
    val dayTotalCents: Long,
    val items: List<ExpenseWithCategory>,
)

data class CategorySummary(
    val category: CategoryEntity,
    val expenseCount: Int,
    val totalCents: Long,
)

data class RecurringWithCategory(
    val rule: RecurringExpenseEntity,
    val category: CategoryEntity?,
)

enum class DateRangePreset(val label: String) {
    LAST_7_DAYS("7 Days"),
    THIS_MONTH("This Month"),
    LAST_30_DAYS("30 Days"),
    LAST_90_DAYS("3 Months"),
    THIS_YEAR("This Year"),
    ALL_TIME("All Time"),
    CUSTOM("Custom"),
}

enum class TimeGranularity(val label: String) {
    AUTO("Auto"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
}

data class ReportFilterState(
    val preset: DateRangePreset = DateRangePreset.THIS_MONTH,
    val customStartEpochDay: Long? = null,
    val customEndEpochDay: Long? = null,
    val selectedCategoryIds: Set<Long> = emptySet(), // empty = all categories; UNCATEGORIZED_FILTER_ID = uncategorized
    val granularity: TimeGranularity = TimeGranularity.AUTO,
)

data class CategorySlice(
    val categoryId: Long?,
    val label: String,
    val colorHex: String?,
    val amountCents: Long,
    val percentage: Float,
    val transactionCount: Int,
)

data class TimeBucketBar(
    val key: String,
    val label: String,
    val detailLabel: String,
    val amountCents: Long,
    val transactionCount: Int,
)

data class ReportSummary(
    val totalCents: Long = 0L,
    val dailyAverageCents: Long = 0L,
    val transactionCount: Int = 0,
    val topCategoryName: String = "—",
    val topCategoryColorHex: String? = null,
    val effectiveStartEpochDay: Long = 0L,
    val effectiveEndEpochDay: Long = 0L,
    val effectiveGranularity: TimeGranularity = TimeGranularity.DAILY,
    val categorySlices: List<CategorySlice> = emptyList(),
    val timeBuckets: List<TimeBucketBar> = emptyList(),
)

data class ExpensesUiState(
    val searchQuery: String = "",
    val selectedCategoryFilterId: Long? = null, // null = All, UNCATEGORIZED_FILTER_ID = Uncategorized
    val currentMonthTotalCents: Long = 0L,
    val currentMonthCount: Int = 0,
    val filteredTotalCents: Long = 0L,
    val totalExpenseCount: Int = 0,
    val activeRecurringMonthlyCents: Long = 0L,
    val groups: List<DailyExpenseGroup> = emptyList(),
)

data class RecurringUiState(
    val rules: List<RecurringWithCategory> = emptyList(),
    val activeCount: Int = 0,
    val estimatedMonthlyCents: Long = 0L,
)

sealed interface LedgerSnackbarEvent {
    data class Info(val message: String) : LedgerSnackbarEvent
    data class ExpenseDeleted(val expense: ExpenseEntity) : LedgerSnackbarEvent
}
