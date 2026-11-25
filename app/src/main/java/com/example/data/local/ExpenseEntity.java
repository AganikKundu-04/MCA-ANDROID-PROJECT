package com.example.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Objects;

/**
 * Spending transaction stored strictly in minor currency units (paise).
 * Includes a non-unique nullable index on utr_reference for fast duplicate screenshot detection.
 */
@Entity(
        tableName = "expenses",
        foreignKeys = {
                @ForeignKey(
                        entity = CategoryEntity.class,
                        parentColumns = "id",
                        childColumns = "category_id",
                        onDelete = ForeignKey.SET_NULL
                )
        },
        indices = {
                @Index(value = {"category_id"}),
                @Index(value = {"timestamp"}),
                @Index(value = {"utr_reference"}, unique = false)
        }
)
public class ExpenseEntity {

    @PrimaryKey(autoGenerate = true)
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

    public ExpenseEntity(
            long id,
            long amountPaise,
            @NonNull String merchantTitle,
            @Nullable Long categoryId,
            @NonNull String paymentMode,
            long timestamp,
            @Nullable String notes,
            @Nullable String utrReference,
            long createdAt
    ) {
        this.id = id;
        this.amountPaise = amountPaise;
        this.merchantTitle = merchantTitle;
        this.categoryId = categoryId;
        this.paymentMode = paymentMode;
        this.timestamp = timestamp;
        this.notes = notes;
        this.utrReference = (utrReference != null && !utrReference.trim().isEmpty())
                ? utrReference.trim()
                : null;
        this.createdAt = createdAt;
    }

    @Ignore
    public ExpenseEntity(
            long amountPaise,
            @NonNull String merchantTitle,
            @Nullable Long categoryId,
            @NonNull String paymentMode,
            long timestamp,
            @Nullable String notes,
            @Nullable String utrReference
    ) {
        this(0L, amountPaise, merchantTitle, categoryId, paymentMode, timestamp, notes, utrReference,
                System.currentTimeMillis());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExpenseEntity)) return false;
        ExpenseEntity that = (ExpenseEntity) o;
        return id == that.id
                && amountPaise == that.amountPaise
                && timestamp == that.timestamp
                && createdAt == that.createdAt
                && merchantTitle.equals(that.merchantTitle)
                && Objects.equals(categoryId, that.categoryId)
                && paymentMode.equals(that.paymentMode)
                && Objects.equals(notes, that.notes)
                && Objects.equals(utrReference, that.utrReference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, amountPaise, merchantTitle, categoryId, paymentMode, timestamp,
                notes, utrReference, createdAt);
    }
}
