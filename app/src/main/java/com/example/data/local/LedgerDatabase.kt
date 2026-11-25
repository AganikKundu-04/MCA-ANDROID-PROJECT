package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CategoryEntity::class,
        ExpenseEntity::class,
        RecurringExpenseEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(LedgerConverters::class)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao

    companion object {
        @Volatile
        private var INSTANCE: LedgerDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN monthlyBudgetCents INTEGER DEFAULT NULL")
            }
        }

        fun getInstance(context: Context): LedgerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LedgerDatabase::class.java,
                    "ledgerflow.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        val StarterCategories: List<CategoryEntity> = listOf(
            CategoryEntity(name = "Groceries", colorHex = "#0F6B4E"),
            CategoryEntity(name = "Dining", colorHex = "#FF9500"),
            CategoryEntity(name = "Transport", colorHex = "#007AFF"),
            CategoryEntity(name = "Housing", colorHex = "#5856D6"),
            CategoryEntity(name = "Utilities", colorHex = "#0D9488"),
            CategoryEntity(name = "Entertainment", colorHex = "#FF2D55"),
        )
    }
}
