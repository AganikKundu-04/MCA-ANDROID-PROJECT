package com.example.ui.state

import com.example.data.local.CategoryEntity
import com.example.data.local.ExpenseEntity
import com.example.util.DateUtils
import kotlin.math.max

enum class BudgetHealthStatus {
    ON_TRACK,
    WARNING,
    BREACHED,
}

data class CategoryBudgetItem(
    val category: CategoryEntity,
    val monthlyLimitCents: Long?,
    val spentThisMonthCents: Long,
    val remainingCents: Long?,
    val overageCents: Long,
    val progressRatio: Float,
    val percentUsed: Int,
    val status: BudgetHealthStatus?,
)

data class BudgetDashboardSummary(
    val monthLabel: String = "",
    val totalBudgetedLimitCents: Long = 0L,
    val totalSpentInBudgetedCents: Long = 0L,
    val budgetedCategoryCount: Int = 0,
    val breachedItems: List<CategoryBudgetItem> = emptyList(),
    val warningItems: List<CategoryBudgetItem> = emptyList(),
    val budgetedItems: List<CategoryBudgetItem> = emptyList(),
    val unbudgetedItems: List<CategoryBudgetItem> = emptyList(),
) {
    val hasBreaches: Boolean get() = breachedItems.isNotEmpty()
    val hasWarnings: Boolean get() = warningItems.isNotEmpty()
    val totalOverageCents: Long get() = breachedItems.sumOf { it.overageCents }
}

object BudgetAnalytics {
    private const val WARNING_THRESHOLD_PERCENT = 80

    fun evaluateMonthlyBudgets(
        categories: List<CategoryEntity>,
        expenses: List<ExpenseEntity>,
        referenceEpochDay: Long = DateUtils.todayEpochDay(),
    ): BudgetDashboardSummary {
        val monthStart = DateUtils.startOfMonthEpochDay(referenceEpochDay)
        val monthEnd = DateUtils.endOfMonthEpochDay(referenceEpochDay)

        val monthSpendByCategory: Map<Long, Long> = expenses
            .asSequence()
            .filter { it.categoryId != null && it.dateEpochDay in monthStart..monthEnd }
            .groupBy { it.categoryId!! }
            .mapValues { (_, list) -> list.sumOf { it.amountCents } }

        val items = categories.map { category ->
            val spent = monthSpendByCategory[category.id] ?: 0L
            val limit = category.monthlyBudgetCents?.takeIf { it > 0L }
            if (limit != null) {
                val ratio = spent.toFloat() / limit.toFloat()
                val pct = ((spent * 100L) / limit).toInt()
                val overage = max(0L, spent - limit)
                val status = when {
                    spent > limit -> BudgetHealthStatus.BREACHED
                    pct >= WARNING_THRESHOLD_PERCENT -> BudgetHealthStatus.WARNING
                    else -> BudgetHealthStatus.ON_TRACK
                }
                CategoryBudgetItem(
                    category = category,
                    monthlyLimitCents = limit,
                    spentThisMonthCents = spent,
                    remainingCents = limit - spent,
                    overageCents = overage,
                    progressRatio = ratio,
                    percentUsed = pct,
                    status = status,
                )
            } else {
                CategoryBudgetItem(
                    category = category,
                    monthlyLimitCents = null,
                    spentThisMonthCents = spent,
                    remainingCents = null,
                    overageCents = 0L,
                    progressRatio = 0f,
                    percentUsed = 0,
                    status = null,
                )
            }
        }

        val budgeted = items
            .filter { it.monthlyLimitCents != null }
            .sortedWith(
                compareByDescending<CategoryBudgetItem> {
                    when (it.status) {
                        BudgetHealthStatus.BREACHED -> 3
                        BudgetHealthStatus.WARNING -> 2
                        BudgetHealthStatus.ON_TRACK -> 1
                        null -> 0
                    }
                }
                    .thenByDescending { it.overageCents }
                    .thenByDescending { it.percentUsed }
                    .thenBy { it.category.name.lowercase() },
            )

        val unbudgeted = items
            .filter { it.monthlyLimitCents == null }
            .sortedBy { it.category.name.lowercase() }

        val breached = budgeted.filter { it.status == BudgetHealthStatus.BREACHED }
        val warning = budgeted.filter { it.status == BudgetHealthStatus.WARNING }

        return BudgetDashboardSummary(
            monthLabel = DateUtils.formatMonthYear(referenceEpochDay),
            totalBudgetedLimitCents = budgeted.sumOf { it.monthlyLimitCents ?: 0L },
            totalSpentInBudgetedCents = budgeted.sumOf { it.spentThisMonthCents },
            budgetedCategoryCount = budgeted.size,
            breachedItems = breached,
            warningItems = warning,
            budgetedItems = budgeted,
            unbudgetedItems = unbudgeted,
        )
    }
}
