package com.example.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;

import java.util.Objects;

/**
 * Aggregated spend per category for donut chart and category budget tracking.
 */
public class CategorySpendTuple {

    @Nullable
    @ColumnInfo(name = "category_id")
    public Long categoryId;

    @NonNull
    @ColumnInfo(name = "category_name")
    public String categoryName;

    @NonNull
    @ColumnInfo(name = "color_hex")
    public String colorHex;

    @ColumnInfo(name = "total_paise")
    public long totalPaise;

    @ColumnInfo(name = "tx_count")
    public int transactionCount;

    public CategorySpendTuple(
            @Nullable Long categoryId,
            @NonNull String categoryName,
            @NonNull String colorHex,
            long totalPaise,
            int transactionCount
    ) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.colorHex = colorHex;
        this.totalPaise = totalPaise;
        this.transactionCount = transactionCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CategorySpendTuple)) return false;
        CategorySpendTuple that = (CategorySpendTuple) o;
        return totalPaise == that.totalPaise
                && transactionCount == that.transactionCount
                && Objects.equals(categoryId, that.categoryId)
                && categoryName.equals(that.categoryName)
                && colorHex.equals(that.colorHex);
    }

    @Override
    public int hashCode() {
        return Objects.hash(categoryId, categoryName, colorHex, totalPaise, transactionCount);
    }
}
