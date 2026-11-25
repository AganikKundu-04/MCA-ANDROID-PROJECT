package com.example.ui.settings;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.data.local.BudgetEntity;
import com.example.data.local.CategoryEntity;
import com.example.data.local.CategorySpendTuple;
import com.example.data.local.ExpenseWithCategory;
import com.example.data.repository.SpendingRepository;
import com.example.domain.CsvExporter;
import com.example.domain.DateGroupingUtil;
import com.example.ml.MirageRepository;
import com.example.ml.MirageStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * ViewModel backing SettingsFragment: budgets, CSV export data preparation, and Mirage engine status.
 */
public class SettingsViewModel extends ViewModel {

    public static final class CsvExportPayload {
        public final int rowCount;
        @NonNull
        public final String csvContent;

        public CsvExportPayload(int rowCount, @NonNull String csvContent) {
            this.rowCount = rowCount;
            this.csvContent = csvContent;
        }
    }

    @NonNull
    private final SpendingRepository spendingRepository;
    @NonNull
    private final MirageRepository mirageRepository;
    @NonNull
    private final LiveData<BudgetEntity> overallBudget;
    @NonNull
    private final LiveData<List<BudgetEntity>> allBudgets;
    @NonNull
    private final LiveData<List<CategoryEntity>> categories;
    @NonNull
    private final LiveData<List<CategorySpendTuple>> currentMonthBreakdown;

    public SettingsViewModel(
            @NonNull SpendingRepository spendingRepository,
            @NonNull MirageRepository mirageRepository
    ) {
        this.spendingRepository = spendingRepository;
        this.mirageRepository = mirageRepository;
        this.overallBudget = spendingRepository.observeOverallBudget();
        this.allBudgets = spendingRepository.observeAllBudgets();
        this.categories = spendingRepository.observeCategories();

        long now = System.currentTimeMillis();
        long start = DateGroupingUtil.startOfMonthMillis(now);
        long end = DateGroupingUtil.endOfMonthExclusiveMillis(now);
        this.currentMonthBreakdown = spendingRepository.observeCategoryBreakdownInRange(start, end);
    }

    @NonNull
    public LiveData<BudgetEntity> getOverallBudget() {
        return overallBudget;
    }

    @NonNull
    public LiveData<List<BudgetEntity>> getAllBudgets() {
        return allBudgets;
    }

    @NonNull
    public LiveData<List<CategoryEntity>> getCategories() {
        return categories;
    }

    @NonNull
    public LiveData<List<CategorySpendTuple>> getCurrentMonthBreakdown() {
        return currentMonthBreakdown;
    }

    @NonNull
    public MirageStatus getMirageStatus() {
        return mirageRepository.getStatus();
    }

    public void saveOverallBudget(long limitPaise) {
        spendingRepository.setOverallMonthlyBudget(limitPaise);
    }

    public void saveCategoryBudget(long categoryId, long limitPaise) {
        spendingRepository.setCategoryMonthlyBudget(categoryId, limitPaise);
    }

    @NonNull
    public CompletableFuture<CsvExportPayload> buildCsvExportAsync() {
        return spendingRepository.getAllExpensesForExport().thenApply(expenses -> {
            List<CsvExporter.CsvRow> rows = new ArrayList<>(expenses.size());
            for (ExpenseWithCategory e : expenses) {
                rows.add(new CsvExporter.CsvRow(
                        e.timestamp,
                        e.merchantTitle,
                        e.getResolvedCategoryName(),
                        e.paymentMode,
                        e.amountPaise,
                        e.utrReference,
                        e.notes
                ));
            }
            return new CsvExportPayload(rows.size(), CsvExporter.export(rows));
        });
    }

    public static final class Factory implements ViewModelProvider.Factory {
        @NonNull
        private final SpendingRepository spendingRepository;
        @NonNull
        private final MirageRepository mirageRepository;

        public Factory(
                @NonNull SpendingRepository spendingRepository,
                @NonNull MirageRepository mirageRepository
        ) {
            this.spendingRepository = spendingRepository;
            this.mirageRepository = mirageRepository;
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (modelClass.isAssignableFrom(SettingsViewModel.class)) {
                return (T) new SettingsViewModel(spendingRepository, mirageRepository);
            }
            throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
        }
    }
}
