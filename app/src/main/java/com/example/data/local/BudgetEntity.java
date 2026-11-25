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
 * Monthly spending budget.
 * When categoryId is null, represents the overall monthly budget; otherwise a per-category monthly limit.
 */
@Entity(
        tableName = "budgets",
        foreignKeys = {
                @ForeignKey(
                        entity = CategoryEntity.class,
                        parentColumns = "id",
                        childColumns = "category_id",
                        onDelete = ForeignKey.CASCADE
                )
        },
        indices = {
                @Index(value = {"category_id", "month_key"}, unique = true)
        }
)
public class BudgetEntity {

    public static final String DEFAULT_MONTH_KEY = "DEFAULT";

    @PrimaryKey(autoGenerate = true)
    public long id;

    @Nullable
    @ColumnInfo(name = "category_id")
    public Long categoryId;

    @NonNull
    @ColumnInfo(name = "month_key")
    public String monthKey;

    @ColumnInfo(name = "limit_paise")
    public long limitPaise;

    public BudgetEntity(
            long id,
            @Nullable Long categoryId,
            @NonNull String monthKey,
            long limitPaise
    ) {
        this.id = id;
        this.categoryId = categoryId;
        this.monthKey = monthKey;
        this.limitPaise = limitPaise;
    }

    @Ignore
    public BudgetEntity(
            @Nullable Long categoryId,
            @NonNull String monthKey,
            long limitPaise
    ) {
        this(0L, categoryId, monthKey, limitPaise);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BudgetEntity)) return false;
        BudgetEntity that = (BudgetEntity) o;
        return id == that.id
                && limitPaise == that.limitPaise
                && Objects.equals(categoryId, that.categoryId)
                && monthKey.equals(that.monthKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, categoryId, monthKey, limitPaise);
    }
}
