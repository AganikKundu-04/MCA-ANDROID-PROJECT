package com.example.ui.dashboard;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.data.local.BudgetEntity;
import com.example.data.local.CategorySpendTuple;
import com.example.data.local.ExpenseEntity;
import com.example.data.local.ExpenseWithCategory;
import com.example.data.repository.SpendingRepository;
import com.example.domain.DateGroupingUtil;

import java.util.List;

/**
 * ViewModel backing DashboardFragment with reactive LiveData for the current calendar month.
 */
public class DashboardViewModel extends ViewModel {

    @NonNull
    private final SpendingRepository repository;
    private final long monthStartMillis;
    private final long monthEndMillis;

    @NonNull
    private final LiveData<Long> currentMonthTotalPaise;
    @NonNull
    private final LiveData<BudgetEntity> overallBudget;
    @NonNull
    private final LiveData<List<CategorySpendTuple>> categoryBreakdown;
    @NonNull
    private final LiveData<List<ExpenseWithCategory>> monthExpenses;

    public DashboardViewModel(@NonNull SpendingRepository repository) {
        this.repository = repository;
        long now = System.currentTimeMillis();
        this.monthStartMillis = DateGroupingUtil.startOfMonthMillis(now);
        this.monthEndMillis = DateGroupingUtil.endOfMonthExclusiveMillis(now);

        this.currentMonthTotalPaise = repository.observeTotalSpendInRange(monthStartMillis, monthEndMillis);
        this.overallBudget = repository.observeOverallBudget();
        this.categoryBreakdown = repository.observeCategoryBreakdownInRange(monthStartMillis, monthEndMillis);
        this.monthExpenses = repository.observeExpensesInRange(monthStartMillis, monthEndMillis);
    }

    @NonNull
    public String getCurrentMonthLabel() {
        return DateGroupingUtil.formatMonthLabel(monthStartMillis);
    }

    @NonNull
    public LiveData<Long> getCurrentMonthTotalPaise() {
        return currentMonthTotalPaise;
    }

    @NonNull
    public LiveData<BudgetEntity> getOverallBudget() {
        return overallBudget;
    }

    @NonNull
    public LiveData<List<CategorySpendTuple>> getCategoryBreakdown() {
        return categoryBreakdown;
    }

    @NonNull
    public LiveData<List<ExpenseWithCategory>> getMonthExpenses() {
        return monthExpenses;
    }

    public void deleteExpense(@NonNull ExpenseEntity expense) {
        repository.deleteExpense(expense);
    }

    public void restoreExpense(@NonNull ExpenseEntity expense) {
        repository.saveExpense(expense);
    }

    public static final class Factory implements ViewModelProvider.Factory {
        @NonNull
        private final SpendingRepository repository;

        public Factory(@NonNull SpendingRepository repository) {
            this.repository = repository;
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (modelClass.isAssignableFrom(DashboardViewModel.class)) {
                return (T) new DashboardViewModel(repository);
            }
            throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
        }
    }
}
