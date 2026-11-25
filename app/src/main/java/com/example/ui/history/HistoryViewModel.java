package com.example.ui.history;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.data.local.CategoryEntity;
import com.example.data.local.ExpenseWithCategory;
import com.example.data.local.MerchantSpendTuple;
import com.example.data.repository.SpendingRepository;
import com.example.domain.DateGroupingUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ViewModel backing HistoryFragment with reactive filtering (date range, category, search query),
 * Month-over-Month spend comparison, and Top Merchants aggregation.
 */
public class HistoryViewModel extends ViewModel {

    public enum DateRangeFilter {
        THIS_MONTH,
        LAST_30_DAYS,
        LAST_90_DAYS,
        ALL_TIME
    }

    public static final class HistoryUiState {
        @NonNull
        public final List<ExpenseWithCategory> filteredExpenses;
        public final long filteredTotalPaise;
        @NonNull
        public final List<MerchantSpendTuple> topMerchants;

        public HistoryUiState(
                @NonNull List<ExpenseWithCategory> filteredExpenses,
                long filteredTotalPaise,
                @NonNull List<MerchantSpendTuple> topMerchants
        ) {
            this.filteredExpenses = filteredExpenses;
            this.filteredTotalPaise = filteredTotalPaise;
            this.topMerchants = topMerchants;
        }
    }

    @NonNull
    private final LiveData<List<CategoryEntity>> categories;
    @NonNull
    private final LiveData<Long> currentMonthTotalPaise;
    @NonNull
    private final LiveData<Long> previousMonthTotalPaise;

    private final MutableLiveData<DateRangeFilter> dateRangeFilter = new MutableLiveData<>(DateRangeFilter.THIS_MONTH);
    private final MutableLiveData<Long> selectedCategoryId = new MutableLiveData<>(null);
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");
    private final MediatorLiveData<HistoryUiState> uiState = new MediatorLiveData<>();

    @Nullable
    private List<ExpenseWithCategory> allExpensesCache = null;

    public HistoryViewModel(@NonNull SpendingRepository repository) {
        this.categories = repository.observeCategories();

        long now = System.currentTimeMillis();
        long curStart = DateGroupingUtil.startOfMonthMillis(now);
        long curEnd = DateGroupingUtil.endOfMonthExclusiveMillis(now);
        long prevStart = DateGroupingUtil.startOfPreviousMonthMillis(now);

        this.currentMonthTotalPaise = repository.observeTotalSpendInRange(curStart, curEnd);
        this.previousMonthTotalPaise = repository.observeTotalSpendInRange(prevStart, curStart);

        LiveData<List<ExpenseWithCategory>> allExpensesSource = repository.observeAllExpenses();

        uiState.addSource(allExpensesSource, list -> {
            allExpensesCache = list;
            recomputeState();
        });
        uiState.addSource(dateRangeFilter, ignored -> recomputeState());
        uiState.addSource(selectedCategoryId, ignored -> recomputeState());
        uiState.addSource(searchQuery, ignored -> recomputeState());
    }

    @NonNull
    public LiveData<List<CategoryEntity>> getCategories() {
        return categories;
    }

    @NonNull
    public LiveData<Long> getCurrentMonthTotalPaise() {
        return currentMonthTotalPaise;
    }

    @NonNull
    public LiveData<Long> getPreviousMonthTotalPaise() {
        return previousMonthTotalPaise;
    }

    @NonNull
    public LiveData<HistoryUiState> getUiState() {
        return uiState;
    }

    public void setDateRangeFilter(@NonNull DateRangeFilter filter) {
        if (dateRangeFilter.getValue() != filter) {
            dateRangeFilter.setValue(filter);
        }
    }

    public void setSelectedCategoryId(@Nullable Long categoryId) {
        Long current = selectedCategoryId.getValue();
        if ((current == null && categoryId != null) || (current != null && !current.equals(categoryId))) {
            selectedCategoryId.setValue(categoryId);
        }
    }

    public void setSearchQuery(@Nullable String query) {
        String normalized = query != null ? query.trim() : "";
        if (!normalized.equals(searchQuery.getValue())) {
            searchQuery.setValue(normalized);
        }
    }

    private void recomputeState() {
        List<ExpenseWithCategory> source = allExpensesCache != null ? allExpensesCache : Collections.emptyList();
        DateRangeFilter range = dateRangeFilter.getValue() != null ? dateRangeFilter.getValue() : DateRangeFilter.THIS_MONTH;
        Long categoryFilter = selectedCategoryId.getValue();
        String query = searchQuery.getValue() != null ? searchQuery.getValue().toLowerCase(Locale.ROOT) : "";

        long now = System.currentTimeMillis();
        long minTimestamp = resolveMinTimestamp(range, now);

        List<ExpenseWithCategory> matched = new ArrayList<>();
        long sumPaise = 0L;
        Map<String, long[]> merchantAgg = new LinkedHashMap<>();

        for (ExpenseWithCategory item : source) {
            if (item.timestamp < minTimestamp) {
                continue;
            }
            if (categoryFilter != null && !categoryFilter.equals(item.categoryId)) {
                continue;
            }
            if (!query.isEmpty() && !matchesQuery(item, query)) {
                continue;
            }

            matched.add(item);
            sumPaise += item.amountPaise;

            String merchantKey = item.merchantTitle;
            long[] stats = merchantAgg.get(merchantKey);
            if (stats == null) {
                stats = new long[]{0L, 0L};
                merchantAgg.put(merchantKey, stats);
            }
            stats[0] += item.amountPaise;
            stats[1] += 1L;
        }

        List<MerchantSpendTuple> topMerchants = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : merchantAgg.entrySet()) {
            topMerchants.add(new MerchantSpendTuple(
                    entry.getKey(),
                    entry.getValue()[0],
                    (int) entry.getValue()[1]
            ));
        }
        topMerchants.sort(Comparator.comparingLong((MerchantSpendTuple t) -> t.totalPaise).reversed());
        if (topMerchants.size() > 5) {
            topMerchants = new ArrayList<>(topMerchants.subList(0, 5));
        }

        uiState.setValue(new HistoryUiState(matched, sumPaise, topMerchants));
    }

    private boolean matchesQuery(@NonNull ExpenseWithCategory item, @NonNull String lowerQuery) {
        if (item.merchantTitle.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
            return true;
        }
        if (item.getResolvedCategoryName().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
            return true;
        }
        if (item.notes != null && item.notes.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
            return true;
        }
        if (item.utrReference != null && item.utrReference.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
            return true;
        }
        return item.paymentMode.toLowerCase(Locale.ROOT).contains(lowerQuery);
    }

    private long resolveMinTimestamp(@NonNull DateRangeFilter range, long nowMillis) {
        switch (range) {
            case THIS_MONTH:
                return DateGroupingUtil.startOfMonthMillis(nowMillis);
            case LAST_30_DAYS:
                return DateGroupingUtil.daysAgoStartMillis(30, nowMillis);
            case LAST_90_DAYS:
                return DateGroupingUtil.daysAgoStartMillis(90, nowMillis);
            case ALL_TIME:
            default:
                return 0L;
        }
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
            if (modelClass.isAssignableFrom(HistoryViewModel.class)) {
                return (T) new HistoryViewModel(repository);
            }
            throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
        }
    }
}
