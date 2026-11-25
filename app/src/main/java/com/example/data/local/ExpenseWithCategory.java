package com.example.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;

import java.util.Objects;

/**
 * Joined read projection combining an ExpenseEntity with its resolved Category display metadata.
 */
public class ExpenseWithCategory {

    @ColumnInfo(name = "id")
    public long id;

    @ColumnInfo(name = "amount_paise")
    public long amountPaise;

    @NonNull
    @ColumnInfo(name = "merchant_title")
    public String merchantTitle;

    @Nullable
    @ColumnInfo(name = "category_id")
    public Long categoryId;

    @NonNull
    @ColumnInfo(name = "payment_mode")
    public String paymentMode;

    @ColumnInfo(name = "timestamp")
    public long timestamp;

    @Nullable
    @ColumnInfo(name = "notes")
    public String notes;

    @Nullable
    @ColumnInfo(name = "utr_reference")
    public String utrReference;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @Nullable
    @ColumnInfo(name = "category_name")
    public String categoryName;

    @Nullable
    @ColumnInfo(name = "category_color_hex")
    public String categoryColorHex;

    public ExpenseWithCategory(
            long id,
            long amountPaise,
            @NonNull String merchantTitle,
            @Nullable Long categoryId,
            @NonNull String paymentMode,
            long timestamp,
            @Nullable String notes,
            @Nullable String utrReference,
            long createdAt,
            @Nullable String categoryName,
            @Nullable String categoryColorHex
    ) {
        this.id = id;
        this.amountPaise = amountPaise;
        this.merchantTitle = merchantTitle;
        this.categoryId = categoryId;
        this.paymentMode = paymentMode;
        this.timestamp = timestamp;
        this.notes = notes;
        this.utrReference = utrReference;
        this.createdAt = createdAt;
        this.categoryName = categoryName;
        this.categoryColorHex = categoryColorHex;
    }

    @NonNull
    public String getResolvedCategoryName() {
        return (categoryName != null && !categoryName.isEmpty()) ? categoryName : "Other";
    }

    @NonNull
    public String getResolvedCategoryColorHex() {
        return (categoryColorHex != null && !categoryColorHex.isEmpty()) ? categoryColorHex : "#64748B";
    }

    @NonNull
    public ExpenseEntity toEntity() {
        return new ExpenseEntity(
                id,
                amountPaise,
                merchantTitle,
                categoryId,
                paymentMode,
                timestamp,
                notes,
                utrReference,
                createdAt
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExpenseWithCategory)) return false;
        ExpenseWithCategory that = (ExpenseWithCategory) o;
        return id == that.id
                && amountPaise == that.amountPaise
                && timestamp == that.timestamp
                && createdAt == that.createdAt
                && merchantTitle.equals(that.merchantTitle)
                && Objects.equals(categoryId, that.categoryId)
                && paymentMode.equals(that.paymentMode)
                && Objects.equals(notes, that.notes)
                && Objects.equals(utrReference, that.utrReference)
                && Objects.equals(categoryName, that.categoryName)
                && Objects.equals(categoryColorHex, that.categoryColorHex);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, amountPaise, merchantTitle, categoryId, paymentMode, timestamp,
                notes, utrReference, createdAt, categoryName, categoryColorHex);
    }
}
