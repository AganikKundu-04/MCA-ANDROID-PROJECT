package com.example.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Objects;

/**
 * Expense category persisted in Room.
 */
@Entity(
        tableName = "categories",
        indices = {@Index(value = {"name"}, unique = true)}
)
public class CategoryEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    @ColumnInfo(name = "name")
    public String name;

    @NonNull
    @ColumnInfo(name = "color_hex")
    public String colorHex;

    @NonNull
    @ColumnInfo(name = "icon_key")
    public String iconKey;

    public CategoryEntity(
            long id,
            @NonNull String name,
            @NonNull String colorHex,
            @NonNull String iconKey
    ) {
        this.id = id;
        this.name = name;
        this.colorHex = colorHex;
        this.iconKey = iconKey;
    }

    @Ignore
    public CategoryEntity(
            @NonNull String name,
            @NonNull String colorHex,
            @NonNull String iconKey
    ) {
        this(0L, name, colorHex, iconKey);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CategoryEntity)) return false;
        CategoryEntity that = (CategoryEntity) o;
        return id == that.id
                && name.equals(that.name)
                && colorHex.equals(that.colorHex)
                && iconKey.equals(that.iconKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, colorHex, iconKey);
    }
}
