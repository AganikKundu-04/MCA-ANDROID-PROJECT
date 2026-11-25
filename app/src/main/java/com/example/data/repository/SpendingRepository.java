package com.example.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.example.data.local.AppDatabase;
import com.example.data.local.BudgetEntity;
import com.example.data.local.CategoryEntity;
import com.example.data.local.CategorySpendTuple;
import com.example.data.local.ExpenseEntity;
import com.example.data.local.ExpenseWithCategory;
import com.example.data.local.MerchantSpendTuple;
import com.example.data.local.SpendingDao;
import com.example.domain.CategoryClassifier;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Single source of truth for offline spending data, executing all writes/lookups off the main thread.
 */
public class SpendingRepository {

    @NonNull
    private final SpendingDao dao;
    @NonNull
    private final ExecutorService ioExecutor;

    public SpendingRepository(@NonNull SpendingDao dao, @NonNull ExecutorService ioExecutor) {
        this.dao = dao;
        this.ioExecutor = ioExecutor;
        ensureDefaultCategoriesSeeded();
    }

    public void ensureDefaultCategoriesSeeded() {
        ioExecutor.execute(() -> {
            if (dao.countCategoriesSync() == 0) {
                dao.insertCategories(AppDatabase.defaultCategories());
            }
        });
    }

    // ---- Reactive Streams ----

    @NonNull
    public LiveData<List<CategoryEntity>> observeCategories() {
        return dao.observeCategories();
    }

    @NonNull
    public LiveData<List<ExpenseWithCategory>> observeExpensesInRange(long startMillis, long endMillis) {
        return dao.observeExpensesInRange(startMillis, endMillis);
    }

    @NonNull
    public LiveData<List<ExpenseWithCategory>> observeAllExpenses() {
        return dao.observeAllExpenses();
    }

    @NonNull
    public LiveData<Long> observeTotalSpendInRange(long startMillis, long endMillis) {
        return dao.observeTotalSpendInRange(startMillis, endMillis);
    }

    @NonNull
    public LiveData<List<CategorySpendTuple>> observeCategoryBreakdownInRange(long startMillis, long endMillis) {
        return dao.observeCategoryBreakdownInRange(startMillis, endMillis);
    }

    @NonNull
    public LiveData<List<MerchantSpendTuple>> observeTopMerchantsInRange(long startMillis, long endMillis, int limit) {
        return dao.observeTopMerchantsInRange(startMillis, endMillis, limit);
    }

    @NonNull
    public LiveData<BudgetEntity> observeOverallBudget() {
        return dao.observeOverallBudget(BudgetEntity.DEFAULT_MONTH_KEY);
    }

    @NonNull
    public LiveData<List<BudgetEntity>> observeAllBudgets() {
        return dao.observeBudgetsForMonth(BudgetEntity.DEFAULT_MONTH_KEY);
    }

    // ---- Async Background Operations ----

    @NonNull
    public CompletableFuture<Long> saveExpense(@NonNull ExpenseEntity expense) {
        return CompletableFuture.supplyAsync(() -> {
            if (expense.id > 0L) {
                dao.updateExpense(expense);
                return expense.id;
            }
            return dao.insertExpense(expense);
        }, ioExecutor);
    }

    @NonNull
    public CompletableFuture<Void> deleteExpense(@NonNull ExpenseEntity expense) {
        return CompletableFuture.runAsync(() -> dao.deleteExpense(expense), ioExecutor);
    }

    @NonNull
    public CompletableFuture<ExpenseEntity> findDuplicateByUtr(@Nullable String utrReference) {
        if (utrReference == null || utrReference.trim().isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        String normalized = utrReference.trim();
        return CompletableFuture.supplyAsync(() -> dao.findExpenseByUtrSync(normalized), ioExecutor);
    }

    @NonNull
    public CompletableFuture<ExpenseEntity> findExpenseById(long id) {
        return CompletableFuture.supplyAsync(() -> dao.findExpenseByIdSync(id), ioExecutor);
    }

    @NonNull
    public CompletableFuture<List<CategoryEntity>> getAllCategoriesAsync() {
        return CompletableFuture.supplyAsync(() -> {
            if (dao.countCategoriesSync() == 0) {
                dao.insertCategories(AppDatabase.defaultCategories());
            }
            return dao.getAllCategoriesSync();
        }, ioExecutor);
    }

    @NonNull
    public CompletableFuture<Long> resolveCategoryIdForMerchant(@Nullable String merchant, @Nullable String notes) {
        return CompletableFuture.supplyAsync(() -> {
            if (dao.countCategoriesSync() == 0) {
                dao.insertCategories(AppDatabase.defaultCategories());
            }
            String categoryName = CategoryClassifier.classify(merchant, notes);
            CategoryEntity matched = dao.findCategoryByNameSync(categoryName);
            if (matched != null) {
                return matched.id;
            }
            CategoryEntity fallback = dao.findCategoryByNameSync(CategoryClassifier.CATEGORY_OTHER);
            return fallback != null ? fallback.id : null;
        }, ioExecutor);
    }

    @NonNull
    public CompletableFuture<Void> setOverallMonthlyBudget(long limitPaise) {
        return CompletableFuture.runAsync(
                () -> dao.upsertOverallBudgetSync(BudgetEntity.DEFAULT_MONTH_KEY, limitPaise),
                ioExecutor
        );
    }

    @NonNull
    public CompletableFuture<Void> setCategoryMonthlyBudget(long categoryId, long limitPaise) {
        return CompletableFuture.runAsync(
                () -> dao.upsertCategoryBudgetSync(categoryId, BudgetEntity.DEFAULT_MONTH_KEY, limitPaise),
                ioExecutor
        );
    }

    @NonNull
    public CompletableFuture<List<ExpenseWithCategory>> getAllExpensesForExport() {
        return CompletableFuture.supplyAsync(dao::getAllExpensesSync, ioExecutor);
    }
}
