package com.example.ui.history;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.R;
import com.example.SpendingApp;
import com.example.data.local.CategoryEntity;
import com.example.data.local.MerchantSpendTuple;
import com.example.databinding.FragmentHistoryBinding;
import com.example.databinding.ItemMerchantSummaryBinding;
import com.example.domain.MoneyFormatter;
import com.example.ui.common.ColorUtil;
import com.example.ui.dashboard.GroupedTransactionAdapter;
import com.example.ui.expense.AddExpenseBottomSheet;
import com.google.android.material.chip.Chip;

import java.util.List;

/**
 * Filterable spending history and analytics screen with debounced SearchView,
 * date range chips, category ChipGroup, Month-over-Month delta, and Top Merchants breakdown.
 */
public class HistoryFragment extends Fragment {

    private static final long SEARCH_DEBOUNCE_MS = 220L;

    private FragmentHistoryBinding binding;
    private HistoryViewModel viewModel;
    private GroupedTransactionAdapter adapter;
    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    @Nullable
    private Runnable pendingSearchRunnable;

    private long latestCurrentMonthPaise = 0L;
    private long latestPrevMonthPaise = 0L;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentHistoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SpendingApp app = (SpendingApp) requireActivity().getApplication();
        viewModel = new ViewModelProvider(
                this,
                new HistoryViewModel.Factory(app.getAppContainer().spendingRepository)
        ).get(HistoryViewModel.class);

        adapter = new GroupedTransactionAdapter(expense ->
                AddExpenseBottomSheet.newForEdit(expense.id)
                        .show(getParentFragmentManager(), "edit_expense_sheet")
        );
        binding.recyclerHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerHistory.setAdapter(adapter);

        setupDateRangeChips();
        setupDebouncedSearch();
        observeViewModel();
    }

    private void setupDateRangeChips() {
        binding.chipGroupDateRange.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipRangeMonth) {
                viewModel.setDateRangeFilter(HistoryViewModel.DateRangeFilter.THIS_MONTH);
            } else if (id == R.id.chipRange30d) {
                viewModel.setDateRangeFilter(HistoryViewModel.DateRangeFilter.LAST_30_DAYS);
            } else if (id == R.id.chipRange90d) {
                viewModel.setDateRangeFilter(HistoryViewModel.DateRangeFilter.LAST_90_DAYS);
            } else if (id == R.id.chipRangeAll) {
                viewModel.setDateRangeFilter(HistoryViewModel.DateRangeFilter.ALL_TIME);
            }
        });
    }

    private void setupDebouncedSearch() {
        binding.searchViewHistory.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                dispatchSearchImmediate(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (pendingSearchRunnable != null) {
                    debounceHandler.removeCallbacks(pendingSearchRunnable);
                }
                final String q = newText != null ? newText : "";
                pendingSearchRunnable = () -> viewModel.setSearchQuery(q);
                debounceHandler.postDelayed(pendingSearchRunnable, SEARCH_DEBOUNCE_MS);
                return true;
            }
        });
    }

    private void dispatchSearchImmediate(@Nullable String query) {
        if (pendingSearchRunnable != null) {
            debounceHandler.removeCallbacks(pendingSearchRunnable);
        }
        viewModel.setSearchQuery(query);
    }

    private void observeViewModel() {
        viewModel.getCategories().observe(getViewLifecycleOwner(), this::populateCategoryChips);

        viewModel.getCurrentMonthTotalPaise().observe(getViewLifecycleOwner(), val -> {
            latestCurrentMonthPaise = val != null ? val : 0L;
            renderMonthOverMonth();
        });

        viewModel.getPreviousMonthTotalPaise().observe(getViewLifecycleOwner(), val -> {
            latestPrevMonthPaise = val != null ? val : 0L;
            renderMonthOverMonth();
        });

        viewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            int count = state.filteredExpenses.size();
            binding.tvFilteredCount.setText(count + (count == 1 ? " Transaction" : " Transactions"));
            binding.tvFilteredTotal.setText(MoneyFormatter.formatInr(state.filteredTotalPaise));

            boolean empty = state.filteredExpenses.isEmpty();
            binding.cardHistoryEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.recyclerHistory.setVisibility(empty ? View.GONE : View.VISIBLE);

            List<GroupedTransactionAdapter.RowItem> rows =
                    GroupedTransactionAdapter.buildGroupedRows(state.filteredExpenses, System.currentTimeMillis());
            adapter.submitList(rows);

            renderTopMerchants(state.topMerchants);
        });
    }

    private void populateCategoryChips(@Nullable List<CategoryEntity> categories) {
        binding.chipGroupCategories.removeAllViews();

        Chip allChip = createFilterChip(getString(R.string.history_all_categories), null);
        allChip.setChecked(true);
        binding.chipGroupCategories.addView(allChip);

        if (categories != null) {
            for (CategoryEntity cat : categories) {
                binding.chipGroupCategories.addView(createFilterChip(cat.name, cat.id));
            }
        }
    }

    @NonNull
    private Chip createFilterChip(@NonNull String label, @Nullable Long categoryId) {
        Chip chip = new Chip(requireContext());
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setCheckable(true);
        chip.setOnClickListener(v -> viewModel.setSelectedCategoryId(categoryId));
        return chip;
    }

    private void renderMonthOverMonth() {
        binding.tvMomCurrent.setText(MoneyFormatter.formatInr(latestCurrentMonthPaise));
        binding.tvMomPrevious.setText(MoneyFormatter.formatInr(latestPrevMonthPaise));

        if (latestPrevMonthPaise <= 0L) {
            if (latestCurrentMonthPaise <= 0L) {
                binding.tvMomDelta.setText("No spend");
            } else {
                binding.tvMomDelta.setText("New month");
            }
            binding.tvMomDelta.setTextColor(ContextCompat.getColor(requireContext(), R.color.lf_on_surface_variant));
            binding.tvMomDelta.setBackground(ColorUtil.createPillBadge(
                    requireContext(), ContextCompat.getColor(requireContext(), R.color.lf_surface_variant)));
            return;
        }

        long delta = latestCurrentMonthPaise - latestPrevMonthPaise;
        long absPct = Math.round((Math.abs((double) delta) * 100.0) / (double) latestPrevMonthPaise);
        if (delta > 0L) {
            binding.tvMomDelta.setText("+" + absPct + "%");
            binding.tvMomDelta.setTextColor(ContextCompat.getColor(requireContext(), R.color.lf_budget_exceeded));
            binding.tvMomDelta.setBackground(ColorUtil.createPillBadge(
                    requireContext(), ContextCompat.getColor(requireContext(), R.color.lf_budget_exceeded_container)));
        } else if (delta < 0L) {
            binding.tvMomDelta.setText("-" + absPct + "%");
            binding.tvMomDelta.setTextColor(ContextCompat.getColor(requireContext(), R.color.lf_budget_healthy));
            binding.tvMomDelta.setBackground(ColorUtil.createPillBadge(
                    requireContext(), ContextCompat.getColor(requireContext(), R.color.lf_budget_healthy_container)));
        } else {
            binding.tvMomDelta.setText("0%");
            binding.tvMomDelta.setTextColor(ContextCompat.getColor(requireContext(), R.color.lf_on_surface_variant));
            binding.tvMomDelta.setBackground(ColorUtil.createPillBadge(
                    requireContext(), ContextCompat.getColor(requireContext(), R.color.lf_surface_variant)));
        }
    }

    private void renderTopMerchants(@NonNull List<MerchantSpendTuple> merchants) {
        binding.containerTopMerchants.removeAllViews();
        if (merchants.isEmpty()) {
            binding.cardTopMerchants.setVisibility(View.GONE);
            return;
        }
        binding.cardTopMerchants.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < merchants.size(); i++) {
            MerchantSpendTuple merchant = merchants.get(i);
            ItemMerchantSummaryBinding row =
                    ItemMerchantSummaryBinding.inflate(inflater, binding.containerTopMerchants, false);
            row.tvMerchantRank.setText((i + 1) + ".");
            row.tvMerchantName.setText(merchant.merchantTitle);
            row.tvMerchantTxCount.setText(
                    merchant.transactionCount + (merchant.transactionCount == 1 ? " payment" : " payments"));
            row.tvMerchantTotal.setText(MoneyFormatter.formatInr(merchant.totalPaise));
            binding.containerTopMerchants.addView(row.getRoot());
            if (i < merchants.size() - 1) {
                View divider = new View(requireContext());
                ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Math.max(1, Math.round(getResources().getDisplayMetrics().density * 0.5f))
                );
                lp.setMarginStart(Math.round(getResources().getDisplayMetrics().density * 24f));
                divider.setLayoutParams(lp);
                divider.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.lf_separator));
                binding.containerTopMerchants.addView(divider);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (pendingSearchRunnable != null) {
            debounceHandler.removeCallbacks(pendingSearchRunnable);
            pendingSearchRunnable = null;
        }
        binding = null;
    }
}
