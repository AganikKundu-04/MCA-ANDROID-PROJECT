package com.example.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.domain.CategoryClassifier;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Offline Room database storing expenses, categories, and monthly budgets.
 */
@Database(
        entities = {ExpenseEntity.class, CategoryEntity.class, BudgetEntity.class},
        version = 1,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DB_NAME = "ledgerflow_offline.db";
    private static volatile AppDatabase INSTANCE;

    @NonNull
    public abstract SpendingDao spendingDao();

    @NonNull
    public static List<CategoryEntity> defaultCategories() {
        return Arrays.asList(
                new CategoryEntity(CategoryClassifier.CATEGORY_FOOD, "#0F766E", "food"),
                new CategoryEntity(CategoryClassifier.CATEGORY_TRANSPORT, "#1D4ED8", "transport"),
                new CategoryEntity(CategoryClassifier.CATEGORY_SHOPPING, "#B45309", "shopping"),
                new CategoryEntity(CategoryClassifier.CATEGORY_BILLS, "#334155", "bills"),
                new CategoryEntity(CategoryClassifier.CATEGORY_GROCERIES, "#15803D", "groceries"),
                new CategoryEntity(CategoryClassifier.CATEGORY_HEALTH, "#B91C1C", "health"),
                new CategoryEntity(CategoryClassifier.CATEGORY_ENTERTAINMENT, "#0E7490", "entertainment"),
                new CategoryEntity(CategoryClassifier.CATEGORY_OTHER, "#64748B", "other")
        );
    }

    @NonNull
    public static AppDatabase getInstance(@NonNull Context context, @NonNull ExecutorService ioExecutor) {
        AppDatabase local = INSTANCE;
        if (local != null) {
            return local;
        }
        synchronized (AppDatabase.class) {
            if (INSTANCE == null) {
                INSTANCE = Room.databaseBuilder(
                                context.getApplicationContext(),
                                AppDatabase.class,
                                DB_NAME
                        )
                        .addCallback(new Callback() {
                            @Override
                            public void onCreate(@NonNull SupportSQLiteDatabase db) {
                                super.onCreate(db);
                                ioExecutor.execute(() -> {
                                    AppDatabase created = INSTANCE;
                                    if (created != null) {
                                        created.spendingDao().insertCategories(defaultCategories());
                                    }
                                });
                            }
                        })
                        .fallbackToDestructiveMigration(true)
                        .build();
            }
            return INSTANCE;
        }
    }
}
