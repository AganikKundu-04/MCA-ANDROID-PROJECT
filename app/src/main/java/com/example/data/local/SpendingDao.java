package com.example.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;

/**
 * Room DAO exposing reactive LiveData streams for UI and synchronous methods for background executors.
 */
@Dao
public interface SpendingDao {

    // ---- Categories ----

    @Query("SELECT * FROM categories ORDER BY CASE WHEN name = 'Other' THEN 1 ELSE 0 END, name ASC")
    @NonNull
    LiveData<List<CategoryEntity>> observeCategories();

    @Query("SELECT * FROM categories ORDER BY CASE WHEN name = 'Other' THEN 1 ELSE 0 END, name ASC")
    @NonNull
    List<CategoryEntity> getAllCategoriesSync();

    @Query("SELECT * FROM categories WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    @Nullable
    CategoryEntity findCategoryByNameSync(@NonNull String name);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertCategory(@NonNull CategoryEntity category);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertCategories(@NonNull List<CategoryEntity> categories);

    @Query("SELECT COUNT(*) FROM categories")
    int countCategoriesSync();

    // ---- Expenses ----

    @Query("SELECT e.id, e.amount_paise, e.merchant_title, e.category_id, e.payment_mode, "
            + "e.timestamp, e.notes, e.utr_reference, e.created_at, "
            + "c.name AS category_name, c.color_hex AS category_color_hex "
            + "FROM expenses e "
            + "LEFT JOIN categories c ON e.category_id = c.id "
            + "WHERE e.timestamp >= :startMillis AND e.timestamp < :endMillis "
            + "ORDER BY e.timestamp DESC, e.id DESC")
    @NonNull
    LiveData<List<ExpenseWithCategory>> observeExpensesInRange(long startMillis, long endMillis);

    @Query("SELECT e.id, e.amount_paise, e.merchant_title, e.category_id, e.payment_mode, "
            + "e.timestamp, e.notes, e.utr_reference, e.created_at, "
            + "c.name AS category_name, c.color_hex AS category_color_hex "
            + "FROM expenses e "
            + "LEFT JOIN categories c ON e.category_id = c.id "
            + "ORDER BY e.timestamp DESC, e.id DESC")
    @NonNull
    LiveData<List<ExpenseWithCategory>> observeAllExpenses();

    @Query("SELECT e.id, e.amount_paise, e.merchant_title, e.category_id, e.payment_mode, "
            + "e.timestamp, e.notes, e.utr_reference, e.created_at, "
            + "c.name AS category_name, c.color_hex AS category_color_hex "
            + "FROM expenses e "
            + "LEFT JOIN categories c ON e.category_id = c.id "
            + "ORDER BY e.timestamp DESC, e.id DESC")
    @NonNull
    List<ExpenseWithCategory> getAllExpensesSync();

    @Query("SELECT COALESCE(SUM(amount_paise), 0) FROM expenses "
            + "WHERE timestamp >= :startMillis AND timestamp < :endMillis")
    @NonNull
    LiveData<Long> observeTotalSpendInRange(long startMillis, long endMillis);

    @Query("SELECT e.category_id AS category_id, "
            + "COALESCE(c.name, 'Other') AS category_name, "
            + "COALESCE(c.color_hex, '#64748B') AS color_hex, "
            + "COALESCE(SUM(e.amount_paise), 0) AS total_paise, "
            + "COUNT(e.id) AS tx_count "
            + "FROM expenses e "
            + "LEFT JOIN categories c ON e.category_id = c.id "
            + "WHERE e.timestamp >= :startMillis AND e.timestamp < :endMillis "
            + "GROUP BY e.category_id, c.name, c.color_hex "
            + "ORDER BY total_paise DESC")
    @NonNull
    LiveData<List<CategorySpendTuple>> observeCategoryBreakdownInRange(long startMillis, long endMillis);

    @Query("SELECT merchant_title, SUM(amount_paise) AS total_paise, COUNT(id) AS tx_count "
            + "FROM expenses "
            + "WHERE timestamp >= :startMillis AND timestamp < :endMillis "
            + "GROUP BY merchant_title "
            + "ORDER BY total_paise DESC "
            + "LIMIT :limit")
    @NonNull
    LiveData<List<MerchantSpendTuple>> observeTopMerchantsInRange(long startMillis, long endMillis, int limit);

    @Query("SELECT * FROM expenses WHERE utr_reference = :utrReference LIMIT 1")
    @Nullable
    ExpenseEntity findExpenseByUtrSync(@NonNull String utrReference);

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    @Nullable
    ExpenseEntity findExpenseByIdSync(long id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertExpense(@NonNull ExpenseEntity expense);

    @Update
    int updateExpense(@NonNull ExpenseEntity expense);

    @Delete
    int deleteExpense(@NonNull ExpenseEntity expense);

    // ---- Budgets ----

    @Query("SELECT * FROM budgets WHERE month_key = :monthKey")
    @NonNull
    LiveData<List<BudgetEntity>> observeBudgetsForMonth(@NonNull String monthKey);

    @Query("SELECT * FROM budgets WHERE category_id IS NULL AND month_key = :monthKey LIMIT 1")
    @NonNull
    LiveData<BudgetEntity> observeOverallBudget(@NonNull String monthKey);

    @Query("SELECT * FROM budgets WHERE category_id IS NULL AND month_key = :monthKey LIMIT 1")
    @Nullable
    BudgetEntity findOverallBudgetSync(@NonNull String monthKey);

    @Query("SELECT * FROM budgets WHERE category_id = :categoryId AND month_key = :monthKey LIMIT 1")
    @Nullable
    BudgetEntity findCategoryBudgetSync(long categoryId, @NonNull String monthKey);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertBudget(@NonNull BudgetEntity budget);

    @Update
    int updateBudget(@NonNull BudgetEntity budget);

    @Query("DELETE FROM budgets WHERE category_id IS NULL AND month_key = :monthKey")
    void deleteOverallBudgetSync(@NonNull String monthKey);

    @Query("DELETE FROM budgets WHERE category_id = :categoryId AND month_key = :monthKey")
    void deleteCategoryBudgetSync(long categoryId, @NonNull String monthKey);

    @Transaction
    default void upsertOverallBudgetSync(@NonNull String monthKey, long limitPaise) {
        if (limitPaise <= 0L) {
            deleteOverallBudgetSync(monthKey);
            return;
        }
        BudgetEntity existing = findOverallBudgetSync(monthKey);
        if (existing != null) {
            existing.limitPaise = limitPaise;
            updateBudget(existing);
        } else {
            insertBudget(new BudgetEntity(null, monthKey, limitPaise));
        }
    }

    @Transaction
    default void upsertCategoryBudgetSync(long categoryId, @NonNull String monthKey, long limitPaise) {
        if (limitPaise <= 0L) {
            deleteCategoryBudgetSync(categoryId, monthKey);
            return;
        }
        BudgetEntity existing = findCategoryBudgetSync(categoryId, monthKey);
        if (existing != null) {
            existing.limitPaise = limitPaise;
            updateBudget(existing);
        } else {
            insertBudget(new BudgetEntity(categoryId, monthKey, limitPaise));
        }
    }
}
