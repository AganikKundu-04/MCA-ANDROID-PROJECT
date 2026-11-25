package com.example.ui.dashboard;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.SpendingApp;
import com.example.data.local.BudgetEntity;
import com.example.data.local.CategorySpendTuple;
import com.example.data.local.ExpenseEntity;
import com.example.data.local.ExpenseWithCategory;
import com.example.databinding.FragmentDashboardBinding;
import com.example.databinding.ItemCategoryLegendBinding;
import com.example.domain.BudgetStatus;
import com.example.domain.MoneyFormatter;
import com.example.ui.MainActivity;
import com.example.ui.chart.CategoryDonutChartView;
import com.example.ui.common.ColorUtil;
import com.example.ui.expense.AddExpenseBottomSheet;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

/**
 * Primary spending overview screen:
 * - Current month total spend vs. monthly budget progress bar (80% amber, 100% crimson)
 * - Custom Canvas donut chart with touch/legend sync
 * - Date-grouped recent expenses with swipe-to-delete + Undo Snackbar
 */
public class DashboardFragment extends Fragment {

    private static final String STATE_SELECTED_SLICE_INDEX = "state_selected_slice_index";

    private FragmentDashboardBinding binding;
    private DashboardViewModel viewModel;
    private GroupedTransactionAdapter adapter;

    private long latestMonthTotalPaise = 0L;
    @Nullable
    private BudgetEntity latestOverallBudget = null;
    @NonNull
    private List<CategorySpendTuple> latestBreakdown = new ArrayList<>();
    private int pendingRestoredSliceIndex = -1;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SpendingApp app = (SpendingApp) requireActivity().getApplication();
        viewModel = new ViewModelProvider(
                this,
                new DashboardViewModel.Factory(app.getAppContainer().spendingRepository)
        ).get(DashboardViewModel.class);

        binding.tvMonthLabel.setText(viewModel.getCurrentMonthLabel());

        adapter = new GroupedTransactionAdapter(expense -> {
            AddExpenseBottomSheet.newForEdit(expense.id)
                    .show(getParentFragmentManager(), "edit_expense_sheet");
        });
        binding.recyclerRecentExpenses.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerRecentExpenses.setAdapter(adapter);
        attachSwipeToDelete();

        if (savedInstanceState != null) {
            pendingRestoredSliceIndex = savedInstanceState.getInt(STATE_SELECTED_SLICE_INDEX, -1);
        }

        binding.donutChart.setOnSliceSelectedListener((index, slice) -> renderLegend(latestBreakdown, index));

        binding.fabAddExpense.setOnClickListener(v ->
                AddExpenseBottomSheet.newForAdd()
                        .show(getParentFragmentManager(), "add_expense_sheet")
        );

        binding.btnScanReceipt.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).launchGalleryReceiptPicker();
            }
        });

        observeViewModel();
    }

    private void observeViewModel() {
        viewModel.getCurrentMonthTotalPaise().observe(getViewLifecycleOwner(), total -> {
            latestMonthTotalPaise = total != null ? total : 0L;
            renderBudgetHeader();
        });

        viewModel.getOverallBudget().observe(getViewLifecycleOwner(), budget -> {
            latestOverallBudget = budget;
            renderBudgetHeader();
        });

        viewModel.getCategoryBreakdown().observe(getViewLifecycleOwner(), breakdown -> {
            latestBreakdown = breakdown != null ? breakdown : new ArrayList<>();
            renderDonutAndLegend(latestBreakdown);
        });

        viewModel.getMonthExpenses().observe(getViewLifecycleOwner(), expenses -> {
            List<ExpenseWithCategory> safeList = expenses != null ? expenses : new ArrayList<>();
            boolean empty = safeList.isEmpty();
            binding.cardEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.tvCategorySectionLabel.setVisibility(empty ? View.GONE : View.VISIBLE);
            binding.cardCategoryBreakdown.setVisibility(empty ? View.GONE : View.VISIBLE);
            binding.tvRecentSectionLabel.setVisibility(empty ? View.GONE : View.VISIBLE);
            binding.recyclerRecentExpenses.setVisibility(empty ? View.GONE : View.VISIBLE);

            List<GroupedTransactionAdapter.RowItem> rows =
                    GroupedTransactionAdapter.buildGroupedRows(safeList, System.currentTimeMillis());
            adapter.submitList(rows);
        });
    }

    private void renderBudgetHeader() {
        binding.tvMonthTotal.setText(MoneyFormatter.formatInr(latestMonthTotalPaise));

        long limitPaise = (latestOverallBudget != null) ? latestOverallBudget.limitPaise : 0L;
        BudgetStatus status = BudgetStatus.evaluate(latestMonthTotalPaise, limitPaise);
        int accent = ColorUtil.budgetAccentColor(requireContext(), status);
        int containerColor = ColorUtil.budgetContainerColor(requireContext(), status);

        binding.progressMonthlyBudget.setIndicatorColor(accent);

        if (status == BudgetStatus.UNSET) {
            binding.progressMonthlyBudget.setProgressCompat(0, true);
            binding.tvBudgetBadge.setVisibility(View.GONE);
            binding.tvBudgetSubtitle.setText(R.string.dashboard_no_budget_hint);
            return;
        }

        int pct = BudgetStatus.calculatePercent(latestMonthTotalPaise, limitPaise);
        binding.progressMonthlyBudget.setProgressCompat(Math.min(pct, 100), true);

        binding.tvBudgetBadge.setVisibility(View.VISIBLE);
        binding.tvBudgetBadge.setTextColor(accent);
        binding.tvBudgetBadge.setBackground(ColorUtil.createPillBadge(requireContext(), containerColor));

        if (status == BudgetStatus.EXCEEDED_100) {
            long over = latestMonthTotalPaise - limitPaise;
            binding.tvBudgetBadge.setText(pct + "% · Over");
            binding.tvBudgetSubtitle.setText("Exceeded " + MoneyFormatter.formatInr(limitPaise)
                    + " budget by " + MoneyFormatter.formatInr(over));
        } else if (status == BudgetStatus.WARNING_80) {
            long remaining = limitPaise - latestMonthTotalPaise;
            binding.tvBudgetBadge.setText(pct + "% · Near limit");
            binding.tvBudgetSubtitle.setText(MoneyFormatter.formatInr(remaining)
                    + " left of " + MoneyFormatter.formatInr(limitPaise) + " monthly budget");
        } else {
            long remaining = limitPaise - latestMonthTotalPaise;
            binding.tvBudgetBadge.setText(pct + "% used");
            binding.tvBudgetSubtitle.setText(MoneyFormatter.formatInr(remaining)
                    + " remaining of " + MoneyFormatter.formatInr(limitPaise) + " budget");
        }
    }

    private void renderDonutAndLegend(@NonNull List<CategorySpendTuple> breakdown) {
        List<CategoryDonutChartView.Slice> slices = new ArrayList<>();
        for (CategorySpendTuple item : breakdown) {
            slices.add(new CategoryDonutChartView.Slice(
                    item.categoryId,
                    item.categoryName,
                    ColorUtil.parseHexOrDefault(item.colorHex),
                    item.totalPaise,
                    item.transactionCount
            ));
        }
        binding.donutChart.setSlices(slices);
        if (pendingRestoredSliceIndex >= 0 && pendingRestoredSliceIndex < slices.size()) {
            binding.donutChart.setSelectedIndex(pendingRestoredSliceIndex);
            pendingRestoredSliceIndex = -1;
        }
        renderLegend(breakdown, binding.donutChart.getSelectedIndex());
    }

    private void renderLegend(@NonNull List<CategorySpendTuple> breakdown, int selectedIndex) {
        binding.containerCategoryLegend.removeAllViews();
        if (breakdown.isEmpty()) {
            return;
        }
        long grandTotal = 0L;
        for (CategorySpendTuple t : breakdown) {
            grandTotal += t.totalPaise;
        }
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < breakdown.size(); i++) {
            CategorySpendTuple item = breakdown.get(i);
            ItemCategoryLegendBinding rowBinding =
                    ItemCategoryLegendBinding.inflate(inflater, binding.containerCategoryLegend, false);

            int color = ColorUtil.parseHexOrDefault(item.colorHex);
            rowBinding.viewLegendSwatch.setBackground(ColorUtil.createCircleDot(color));
            rowBinding.tvLegendName.setText(item.categoryName);

            int pct = grandTotal > 0L
                    ? (int) Math.round(((double) item.totalPaise * 100.0) / (double) grandTotal)
                    : 0;
            rowBinding.tvLegendPercent.setText(pct + "%");
            rowBinding.tvLegendAmount.setText(MoneyFormatter.formatInr(item.totalPaise));

            boolean isHighlighted = (i == selectedIndex);
            if (isHighlighted) {
                rowBinding.getRoot().setBackgroundResource(R.drawable.bg_hairline_surface);
            }

            final int index = i;
            rowBinding.getRoot().setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                int next = (binding.donutChart.getSelectedIndex() == index) ? -1 : index;
                binding.donutChart.setSelectedIndex(next);
                renderLegend(breakdown, next);
            });

            binding.containerCategoryLegend.addView(rowBinding.getRoot());
            if (i < breakdown.size() - 1) {
                View divider = new View(requireContext());
                ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Math.max(1, Math.round(getResources().getDisplayMetrics().density * 0.5f))
                );
                lp.setMarginStart(Math.round(getResources().getDisplayMetrics().density * 28f));
                divider.setLayoutParams(lp);
                divider.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.lf_separator));
                binding.containerCategoryLegend.addView(divider);
            }
        }
    }

    private void attachSwipeToDelete() {
        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(
                0,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT
        ) {
            private final Paint bgPaint = new Paint();

            @Override
            public int getSwipeDirs(
                    @NonNull RecyclerView recyclerView,
                    @NonNull RecyclerView.ViewHolder viewHolder
            ) {
                if (viewHolder.getItemViewType() == GroupedTransactionAdapter.VIEW_TYPE_HEADER) {
                    return 0;
                }
                return super.getSwipeDirs(recyclerView, viewHolder);
            }

            @Override
            public boolean onMove(
                    @NonNull RecyclerView recyclerView,
                    @NonNull RecyclerView.ViewHolder viewHolder,
                    @NonNull RecyclerView.ViewHolder target
            ) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                ExpenseWithCategory target = adapter.getExpenseAt(position);
                if (target == null) {
                    return;
                }
                ExpenseEntity deleted = target.toEntity();
                viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                viewModel.deleteExpense(deleted);

                Snackbar.make(binding.dashboardRoot, R.string.snackbar_expense_deleted, Snackbar.LENGTH_LONG)
                        .setAnchorView(binding.fabAddExpense)
                        .setAction(R.string.action_undo, v -> viewModel.restoreExpense(deleted))
                        .show();
            }

            @Override
            public void onChildDraw(
                    @NonNull Canvas c,
                    @NonNull RecyclerView recyclerView,
                    @NonNull RecyclerView.ViewHolder viewHolder,
                    float dX,
                    float dY,
                    int actionState,
                    boolean isCurrentlyActive
            ) {
                View itemView = viewHolder.itemView;
                if (Math.abs(dX) > 0f && binding != null) {
                    bgPaint.setColor(ContextCompat.getColor(requireContext(), R.color.lf_swipe_delete_bg));
                    if (dX > 0) {
                        c.drawRect(itemView.getLeft(), itemView.getTop(),
                                itemView.getLeft() + dX, itemView.getBottom(), bgPaint);
                    } else {
                        c.drawRect(itemView.getRight() + dX, itemView.getTop(),
                                itemView.getRight(), itemView.getBottom(), bgPaint);
                    }
                    Drawable icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_delete);
                    if (icon != null) {
                        int iconMargin = (itemView.getHeight() - icon.getIntrinsicHeight()) / 2;
                        int top = itemView.getTop() + iconMargin;
                        int bottom = top + icon.getIntrinsicHeight();
                        if (dX > 0) {
                            int left = itemView.getLeft() + iconMargin;
                            int right = left + icon.getIntrinsicWidth();
                            icon.setBounds(left, top, right, bottom);
                        } else {
                            int right = itemView.getRight() - iconMargin;
                            int left = right - icon.getIntrinsicWidth();
                            icon.setBounds(left, top, right, bottom);
                        }
                        icon.draw(c);
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        };

        new ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.recyclerRecentExpenses);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (binding != null) {
            outState.putInt(STATE_SELECTED_SLICE_INDEX, binding.donutChart.getSelectedIndex());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
