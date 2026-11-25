package com.example.ui.dashboard;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.data.local.ExpenseWithCategory;
import com.example.databinding.ItemDateHeaderBinding;
import com.example.databinding.ItemExpenseBinding;
import com.example.domain.DateGroupingUtil;
import com.example.domain.MoneyFormatter;
import com.example.domain.PaymentMode;
import com.example.ui.common.ColorUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * RecyclerView ListAdapter displaying expenses grouped under "Today", "Yesterday", or formatted dates.
 */
public class GroupedTransactionAdapter
        extends ListAdapter<GroupedTransactionAdapter.RowItem, RecyclerView.ViewHolder> {

    public static final int VIEW_TYPE_HEADER = 0;
    public static final int VIEW_TYPE_EXPENSE = 1;

    public interface OnExpenseClickListener {
        void onExpenseClicked(@NonNull ExpenseWithCategory expense);
    }

    public abstract static class RowItem {
        public abstract int getViewType();

        public abstract long getStableId();
    }

    public static final class DateHeaderRow extends RowItem {
        public final long dayStartMillis;
        @NonNull
        public final String title;
        public final long dayTotalPaise;

        public DateHeaderRow(long dayStartMillis, @NonNull String title, long dayTotalPaise) {
            this.dayStartMillis = dayStartMillis;
            this.title = title;
            this.dayTotalPaise = dayTotalPaise;
        }

        @Override
        public int getViewType() {
            return VIEW_TYPE_HEADER;
        }

        @Override
        public long getStableId() {
            return -dayStartMillis;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DateHeaderRow)) return false;
            DateHeaderRow that = (DateHeaderRow) o;
            return dayStartMillis == that.dayStartMillis
                    && dayTotalPaise == that.dayTotalPaise
                    && title.equals(that.title);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dayStartMillis, title, dayTotalPaise);
        }
    }

    public static final class ExpenseRow extends RowItem {
        @NonNull
        public final ExpenseWithCategory expense;

        public ExpenseRow(@NonNull ExpenseWithCategory expense) {
            this.expense = expense;
        }

        @Override
        public int getViewType() {
            return VIEW_TYPE_EXPENSE;
        }

        @Override
        public long getStableId() {
            return expense.id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ExpenseRow)) return false;
            ExpenseRow that = (ExpenseRow) o;
            return expense.equals(that.expense);
        }

        @Override
        public int hashCode() {
            return expense.hashCode();
        }
    }

    private static final DiffUtil.ItemCallback<RowItem> DIFF_CALLBACK = new DiffUtil.ItemCallback<RowItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull RowItem oldItem, @NonNull RowItem newItem) {
            return oldItem.getViewType() == newItem.getViewType()
                    && oldItem.getStableId() == newItem.getStableId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull RowItem oldItem, @NonNull RowItem newItem) {
            return oldItem.equals(newItem);
        }
    };

    @Nullable
    private final OnExpenseClickListener clickListener;

    public GroupedTransactionAdapter(@Nullable OnExpenseClickListener clickListener) {
        super(DIFF_CALLBACK);
        this.clickListener = clickListener;
    }

    @NonNull
    public static List<RowItem> buildGroupedRows(@NonNull List<ExpenseWithCategory> expenses, long nowMillis) {
        Map<Long, List<ExpenseWithCategory>> byDay = new LinkedHashMap<>();
        for (ExpenseWithCategory expense : expenses) {
            long dayStart = DateGroupingUtil.startOfDayMillis(expense.timestamp);
            List<ExpenseWithCategory> bucket = byDay.get(dayStart);
            if (bucket == null) {
                bucket = new ArrayList<>();
                byDay.put(dayStart, bucket);
            }
            bucket.add(expense);
        }

        List<RowItem> rows = new ArrayList<>();
        for (Map.Entry<Long, List<ExpenseWithCategory>> entry : byDay.entrySet()) {
            long dayStart = entry.getKey();
            List<ExpenseWithCategory> bucket = entry.getValue();
            long daySum = 0L;
            for (ExpenseWithCategory e : bucket) {
                daySum += e.amountPaise;
            }
            String headerTitle = DateGroupingUtil.formatDayHeader(dayStart, nowMillis);
            rows.add(new DateHeaderRow(dayStart, headerTitle, daySum));
            for (ExpenseWithCategory e : bucket) {
                rows.add(new ExpenseRow(e));
            }
        }
        return rows;
    }

    @Nullable
    public ExpenseWithCategory getExpenseAt(int position) {
        if (position < 0 || position >= getItemCount()) {
            return null;
        }
        RowItem item = getItem(position);
        if (item instanceof ExpenseRow) {
            return ((ExpenseRow) item).expense;
        }
        return null;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).getViewType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_HEADER) {
            ItemDateHeaderBinding binding = ItemDateHeaderBinding.inflate(inflater, parent, false);
            return new HeaderViewHolder(binding);
        }
        ItemExpenseBinding binding = ItemExpenseBinding.inflate(inflater, parent, false);
        return new ExpenseViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        RowItem item = getItem(position);
        if (holder instanceof HeaderViewHolder && item instanceof DateHeaderRow) {
            ((HeaderViewHolder) holder).bind((DateHeaderRow) item);
        } else if (holder instanceof ExpenseViewHolder && item instanceof ExpenseRow) {
            ((ExpenseViewHolder) holder).bind(((ExpenseRow) item).expense, clickListener);
        }
    }

    static final class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final ItemDateHeaderBinding binding;

        HeaderViewHolder(@NonNull ItemDateHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull DateHeaderRow row) {
            binding.tvDateHeader.setText(row.title);
            binding.tvDateDayTotal.setText(MoneyFormatter.formatInr(row.dayTotalPaise));
        }
    }

    static final class ExpenseViewHolder extends RecyclerView.ViewHolder {
        private final ItemExpenseBinding binding;

        ExpenseViewHolder(@NonNull ItemExpenseBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(
                @NonNull ExpenseWithCategory expense,
                @Nullable OnExpenseClickListener clickListener
        ) {
            int dotColor = ColorUtil.parseHexOrDefault(expense.getResolvedCategoryColorHex());
            binding.viewCategoryDot.setBackground(ColorUtil.createCircleDot(dotColor));
            binding.tvMerchant.setText(expense.merchantTitle);

            String modeLabel = PaymentMode.fromStorageValue(expense.paymentMode).getDisplayName();
            String timeLabel = DateGroupingUtil.formatTimeShort(expense.timestamp);
            StringBuilder meta = new StringBuilder();
            meta.append(expense.getResolvedCategoryName())
                    .append(" · ")
                    .append(modeLabel)
                    .append(" · ")
                    .append(timeLabel);
            if (expense.utrReference != null && !expense.utrReference.isEmpty()) {
                meta.append(" · UTR ").append(expense.utrReference);
            }
            binding.tvExpenseMeta.setText(meta.toString());
            binding.tvAmount.setText(MoneyFormatter.formatInr(expense.amountPaise));

            binding.getRoot().setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onExpenseClicked(expense);
                }
            });
        }
    }
}
