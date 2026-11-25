package com.example.data.repository

import com.example.data.local.CategoryEntity
import com.example.data.local.ExpenseEntity
import com.example.data.local.LedgerDao
import com.example.data.local.LedgerDatabase
import com.example.data.local.RecurringExpenseEntity
import com.example.data.model.RecurrenceFrequency
import com.example.util.DateUtils
import kotlinx.coroutines.flow.Flow

class LedgerRepository(
    private val dao: LedgerDao,
) {
    val categories: Flow<List<CategoryEntity>> = dao.observeCategories()
    val expenses: Flow<List<ExpenseEntity>> = dao.observeAllExpenses()
    val recurringExpenses: Flow<List<RecurringExpenseEntity>> = dao.observeRecurringExpenses()

    private var hasSeededDefaults = false

    suspend fun initialize(todayEpochDay: Long = DateUtils.todayEpochDay()): Int {
        if (!hasSeededDefaults) {
            hasSeededDefaults = true
            if (dao.getCategoryCount() == 0) {
                dao.insertCategoriesIgnore(LedgerDatabase.StarterCategories)
            }
        }
        return dao.processDueRecurringExpenses(todayEpochDay)
    }

    suspend fun processDueRecurringExpenses(todayEpochDay: Long = DateUtils.todayEpochDay()): Int {
        return dao.processDueRecurringExpenses(todayEpochDay)
    }

    // --- Categories ---
    suspend fun createCategory(name: String, colorHex: String?): Result<Long> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Category name cannot be empty"))
        val existing = dao.findCategoryByName(trimmed)
        if (existing != null) {
            return Result.failure(IllegalArgumentException("A category named \"$trimmed\" already exists"))
        }
        val normalizedColor = normalizeOptionalHex(colorHex)
            ?: return Result.failure(IllegalArgumentException("Invalid hex color code. Use #RRGGBB format."))
        return runCatching {
            dao.insertCategory(
                CategoryEntity(
                    name = trimmed,
                    colorHex = normalizedColor.ifEmpty { null },
                ),
            )
        }
    }

    suspend fun updateCategory(category: CategoryEntity, newName: String, newColorHex: String?): Result<Unit> {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Category name cannot be empty"))
        val existing = dao.findCategoryByName(trimmed)
        if (existing != null && existing.id != category.id) {
            return Result.failure(IllegalArgumentException("A category named \"$trimmed\" already exists"))
        }
        val normalizedColor = normalizeOptionalHex(newColorHex)
            ?: return Result.failure(IllegalArgumentException("Invalid hex color code. Use #RRGGBB format."))
        return runCatching {
            dao.updateCategory(
                category.copy(
                    name = trimmed,
                    colorHex = normalizedColor.ifEmpty { null },
                ),
            )
        }
    }

    suspend fun deleteCategory(category: CategoryEntity): Result<Unit> = runCatching {
        dao.deleteCategory(category)
    }

    suspend fun setCategoryMonthlyBudget(categoryId: Long, budgetCents: Long?): Result<Unit> {
        val normalized = budgetCents?.takeIf { it > 0L }
        return runCatching {
            dao.updateCategoryBudget(categoryId, normalized)
        }
    }

    /**
     * Checks if [categoryId] has a monthly budget limit and whether its current month's spend
     * exceeds that limit. Returns (CategoryEntity, overageCents) when breached, or null otherwise.
     */
    suspend fun checkCategoryMonthlyBreach(
        categoryId: Long?,
        expenseDateEpochDay: Long,
        todayEpochDay: Long = DateUtils.todayEpochDay(),
    ): Pair<CategoryEntity, Long>? {
        if (categoryId == null) return null
        val monthStart = DateUtils.startOfMonthEpochDay(todayEpochDay)
        val monthEnd = DateUtils.endOfMonthEpochDay(todayEpochDay)
        if (expenseDateEpochDay !in monthStart..monthEnd) return null
        val category = dao.getCategoryById(categoryId) ?: return null
        val limit = category.monthlyBudgetCents?.takeIf { it > 0L } ?: return null
        val spent = dao.getCategorySpendInRange(categoryId, monthStart, monthEnd)
        return if (spent > limit) category to (spent - limit) else null
    }

    // --- Expenses ---
    suspend fun saveExpense(
        existingId: Long? = null,
        title: String,
        amountCents: Long,
        categoryId: Long?,
        dateEpochDay: Long,
        note: String,
        recurringExpenseId: Long? = null,
        isAutoLogged: Boolean = false,
        createdAtMillis: Long = System.currentTimeMillis(),
    ): Result<Long> {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) return Result.failure(IllegalArgumentException("Expense name is required"))
        if (amountCents <= 0L) return Result.failure(IllegalArgumentException("Amount must be greater than zero"))
        return runCatching {
            val entity = ExpenseEntity(
                id = existingId ?: 0L,
                title = trimmedTitle,
                amountCents = amountCents,
                categoryId = categoryId,
                dateEpochDay = dateEpochDay,
                note = note.trim(),
                recurringExpenseId = recurringExpenseId,
                isAutoLogged = isAutoLogged,
                createdAtMillis = createdAtMillis,
            )
            if (existingId != null && existingId > 0L) {
                dao.updateExpense(entity)
                existingId
            } else {
                dao.insertExpense(entity)
            }
        }
    }

    suspend fun deleteExpense(expense: ExpenseEntity): Result<Unit> = runCatching {
        dao.deleteExpense(expense)
    }

    suspend fun restoreExpense(expense: ExpenseEntity): Result<Long> = runCatching {
        dao.insertExpense(expense)
    }

    // --- Recurring Expenses ---
    suspend fun saveRecurringExpense(
        existing: RecurringExpenseEntity? = null,
        name: String,
        amountCents: Long,
        categoryId: Long?,
        frequency: RecurrenceFrequency,
        startDateEpochDay: Long,
        isActive: Boolean = true,
        note: String = "",
        todayEpochDay: Long = DateUtils.todayEpochDay(),
    ): Result<Int> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Expense name is required"))
        if (amountCents <= 0L) return Result.failure(IllegalArgumentException("Amount must be greater than zero"))

        return runCatching {
            val nextDue = if (existing == null) {
                startDateEpochDay
            } else if (
                existing.startDateEpochDay != startDateEpochDay ||
                existing.frequency != frequency
            ) {
                // Recompute next due date from the updated schedule while skipping already-logged dates
                val lastLogged = existing.lastLoggedDateEpochDay
                if (lastLogged == null) {
                    startDateEpochDay
                } else {
                    var candidate = startDateEpochDay
                    var guard = 0
                    while (candidate <= lastLogged && guard < 1000) {
                        candidate = DateUtils.nextDueDate(startDateEpochDay, candidate, frequency)
                        guard++
                    }
                    candidate
                }
            } else {
                existing.nextDueDateEpochDay
            }

            val entity = RecurringExpenseEntity(
                id = existing?.id ?: 0L,
                name = trimmed,
                amountCents = amountCents,
                categoryId = categoryId,
                frequency = frequency,
                startDateEpochDay = startDateEpochDay,
                nextDueDateEpochDay = nextDue,
                lastLoggedDateEpochDay = existing?.lastLoggedDateEpochDay,
                isActive = isActive,
                note = note.trim(),
                createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis(),
            )

            if (existing != null) {
                dao.updateRecurringExpense(entity)
            } else {
                dao.insertRecurringExpense(entity)
            }
            // Immediately log any due entries (e.g., if startDateEpochDay <= today)
            dao.processDueRecurringExpenses(todayEpochDay)
        }
    }

    suspend fun toggleRecurringActive(
        rule: RecurringExpenseEntity,
        active: Boolean,
        todayEpochDay: Long = DateUtils.todayEpochDay(),
    ): Result<Int> = runCatching {
        dao.updateRecurringExpense(rule.copy(isActive = active))
        if (active) dao.processDueRecurringExpenses(todayEpochDay) else 0
    }

    suspend fun deleteRecurringExpense(rule: RecurringExpenseEntity): Result<Unit> = runCatching {
        dao.deleteRecurringExpense(rule)
    }

    /**
     * Returns "" when optional color is blank/null, normalized "#RRGGBB" when valid, or null when malformed.
     */
    private fun normalizeOptionalHex(raw: String?): String? {
        if (raw.isNullOrBlank()) return ""
        val trimmed = raw.trim().uppercase()
        val withHash = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
        return if (Regex("^#[0-9A-F]{6}$").matches(withHash)) withHash else null
    }
}
