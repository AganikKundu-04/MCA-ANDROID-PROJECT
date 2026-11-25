package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.util.DateUtils
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    // --- Categories ---
    @Query("SELECT * FROM categories ORDER BY LOWER(name) ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getCategoryById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findCategoryByName(name: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategoriesIgnore(categories: List<CategoryEntity>)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Query("UPDATE categories SET monthlyBudgetCents = :monthlyBudgetCents WHERE id = :categoryId")
    suspend fun updateCategoryBudget(categoryId: Long, monthlyBudgetCents: Long?)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCategoryCount(): Int

    // --- Expenses ---
    @Query("SELECT * FROM expenses ORDER BY dateEpochDay DESC, createdAtMillis DESC, id DESC")
    fun observeAllExpenses(): Flow<List<ExpenseEntity>>

    @Query(
        "SELECT COALESCE(SUM(amountCents), 0) FROM expenses " +
            "WHERE categoryId = :categoryId AND dateEpochDay BETWEEN :startEpochDay AND :endEpochDay",
    )
    suspend fun getCategorySpendInRange(
        categoryId: Long,
        startEpochDay: Long,
        endEpochDay: Long,
    ): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    // --- Recurring Expenses ---
    @Query("SELECT * FROM recurring_expenses ORDER BY isActive DESC, nextDueDateEpochDay ASC, id DESC")
    fun observeRecurringExpenses(): Flow<List<RecurringExpenseEntity>>

    @Query("SELECT * FROM recurring_expenses WHERE isActive = 1 AND nextDueDateEpochDay <= :todayEpochDay")
    suspend fun getDueRecurringExpenses(todayEpochDay: Long): List<RecurringExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurringExpense(recurringExpense: RecurringExpenseEntity): Long

    @Update
    suspend fun updateRecurringExpense(recurringExpense: RecurringExpenseEntity)

    @Delete
    suspend fun deleteRecurringExpense(recurringExpense: RecurringExpenseEntity)

    /**
     * Atomically evaluates every active recurring schedule whose nextDueDateEpochDay <= todayEpochDay,
     * logs an ExpenseEntity for each due date up through today, and advances nextDueDateEpochDay.
     * Running inside a Room @Transaction guarantees idempotency and prevents duplicate auto-logs.
     */
    @Transaction
    suspend fun processDueRecurringExpenses(todayEpochDay: Long): Int {
        val dueRules = getDueRecurringExpenses(todayEpochDay)
        if (dueRules.isEmpty()) return 0

        var totalLogged = 0
        val maxOccurrencesPerRule = 366 // Guard against extreme historical start dates

        for (rule in dueRules) {
            var nextDue = rule.nextDueDateEpochDay
            var lastLogged = rule.lastLoggedDateEpochDay
            var loggedForRule = 0

            while (nextDue <= todayEpochDay && loggedForRule < maxOccurrencesPerRule) {
                insertExpense(
                    ExpenseEntity(
                        title = rule.name,
                        amountCents = rule.amountCents,
                        categoryId = rule.categoryId,
                        dateEpochDay = nextDue,
                        note = rule.note,
                        recurringExpenseId = rule.id,
                        isAutoLogged = true,
                        createdAtMillis = System.currentTimeMillis() + loggedForRule,
                    ),
                )
                lastLogged = nextDue
                nextDue = DateUtils.nextDueDate(
                    anchorStartEpochDay = rule.startDateEpochDay,
                    currentDueEpochDay = nextDue,
                    frequency = rule.frequency,
                )
                loggedForRule++
                totalLogged++
            }

            // If capped, fast-forward nextDue past today so future runs stay clean
            while (nextDue <= todayEpochDay) {
                nextDue = DateUtils.nextDueDate(
                    anchorStartEpochDay = rule.startDateEpochDay,
                    currentDueEpochDay = nextDue,
                    frequency = rule.frequency,
                )
            }

            if (loggedForRule > 0) {
                updateRecurringExpense(
                    rule.copy(
                        nextDueDateEpochDay = nextDue,
                        lastLoggedDateEpochDay = lastLogged,
                    ),
                )
            }
        }
        return totalLogged
    }
}
