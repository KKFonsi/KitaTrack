package com.example.kitatrack.data.local

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.kitatrack.data.local.dao.AppSettingsDao
import com.example.kitatrack.data.local.dao.BudgetDao
import com.example.kitatrack.data.local.dao.CategoryDao
import com.example.kitatrack.data.local.dao.DebtDao
import com.example.kitatrack.data.local.dao.MonthlySummaryDao
import com.example.kitatrack.data.local.dao.PiggyBankDao
import com.example.kitatrack.data.local.dao.PiggyBankTransactionDao
import com.example.kitatrack.data.local.dao.PiggyBankMissedContributionDao
import com.example.kitatrack.data.local.dao.SubscriptionDao
import com.example.kitatrack.data.local.dao.TransactionDao
import com.example.kitatrack.data.local.entity.AppSettingsEntity
import com.example.kitatrack.data.local.entity.BudgetEntity
import com.example.kitatrack.data.local.entity.CategoryEntity
import com.example.kitatrack.data.local.entity.DebtEntity
import com.example.kitatrack.data.local.entity.MonthlySummaryEntity
import com.example.kitatrack.data.local.entity.PiggyBankEntity
import com.example.kitatrack.data.local.entity.PiggyBankTransactionEntity
import com.example.kitatrack.data.local.entity.PiggyBankMissedContributionEntity
import com.example.kitatrack.data.local.entity.SubscriptionEntity
import com.example.kitatrack.data.local.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        BudgetEntity::class,
        PiggyBankEntity::class,
        PiggyBankTransactionEntity::class,
        PiggyBankMissedContributionEntity::class,
        DebtEntity::class,
        SubscriptionEntity::class,
        MonthlySummaryEntity::class,
        AppSettingsEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class KitaTrackDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun piggyBankDao(): PiggyBankDao
    abstract fun piggyBankTransactionDao(): PiggyBankTransactionDao
    abstract fun piggyBankMissedContributionDao(): PiggyBankMissedContributionDao
    abstract fun debtDao(): DebtDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun monthlySummaryDao(): MonthlySummaryDao
    abstract fun appSettingsDao(): AppSettingsDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN description TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE categories ADD COLUMN isDefault INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE categories ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE categories ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_categories_name ON categories(name)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE categories SET type = 'EXPENSE' WHERE type = 'BOTH' OR type = ''")
                db.execSQL("DROP INDEX IF EXISTS index_categories_name")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_categories_name_type ON categories(name, type)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS budgets_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        categoryId INTEGER,
                        name TEXT NOT NULL,
                        amountLimit INTEGER NOT NULL,
                        budgetType TEXT NOT NULL,
                        startDate INTEGER,
                        endDate INTEGER,
                        isActive INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO budgets_new (id, categoryId, name, amountLimit, budgetType, startDate, endDate, isActive, createdAt, updatedAt)
                    SELECT id, categoryId, name, limitAmount,
                        CASE
                            WHEN period = 'WEEKLY' AND categoryId IS NULL THEN 'WEEKLY_OVERALL'
                            WHEN period = 'MONTHLY' AND categoryId IS NULL THEN 'MONTHLY_OVERALL'
                            WHEN period = 'WEEKLY' AND categoryId IS NOT NULL THEN 'CATEGORY_WEEKLY'
                            WHEN period = 'MONTHLY' AND categoryId IS NOT NULL THEN 'CATEGORY_MONTHLY'
                            ELSE 'MONTHLY_OVERALL'
                        END,
                        NULLIF(startDate, 0), endDate, isActive, 0, 0
                    FROM budgets
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE budgets")
                db.execSQL("ALTER TABLE budgets_new RENAME TO budgets")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE piggy_banks ADD COLUMN allocationPercent INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE piggy_banks ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE piggy_banks ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE piggy_banks ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE piggy_banks ADD COLUMN notes TEXT")
                db.execSQL("ALTER TABLE piggy_banks ADD COLUMN allowOverSaving INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS piggy_bank_transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        piggyBankId INTEGER NOT NULL,
                        amount INTEGER NOT NULL,
                        transactionType TEXT NOT NULL,
                        sourceTransactionId INTEGER,
                        date INTEGER NOT NULL,
                        notes TEXT,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS piggy_banks_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        targetAmount INTEGER NOT NULL,
                        currentAmount INTEGER NOT NULL,
                        weeklyIncomePrediction INTEGER NOT NULL,
                        selectedAllocationPercent INTEGER NOT NULL,
                        minAllocationPercent INTEGER NOT NULL,
                        maxAllocationPercent INTEGER NOT NULL,
                        isGoalPossible INTEGER NOT NULL,
                        targetDate INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        isActive INTEGER NOT NULL,
                        isArchived INTEGER NOT NULL,
                        notes TEXT,
                        allowOverSaving INTEGER NOT NULL,
                        completedAt INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO piggy_banks_new (
                        id, name, targetAmount, currentAmount, weeklyIncomePrediction,
                        selectedAllocationPercent, minAllocationPercent, maxAllocationPercent,
                        isGoalPossible, targetDate, createdAt, updatedAt, isActive, isArchived,
                        notes, allowOverSaving, completedAt
                    )
                    SELECT
                        id, name, targetAmount, currentAmount, 0,
                        allocationPercent, 0, 100, 0, targetDate, createdAt, updatedAt,
                        isActive, isArchived, notes, allowOverSaving, NULL
                    FROM piggy_banks
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE piggy_banks")
                db.execSQL("ALTER TABLE piggy_banks_new RENAME TO piggy_banks")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS piggy_bank_missed_contributions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        piggyBankId INTEGER NOT NULL,
                        expectedDate INTEGER NOT NULL,
                        expectedAmount INTEGER NOT NULL,
                        actualAmount INTEGER NOT NULL,
                        missedAmount INTEGER NOT NULL,
                        weeklyIncomePredictionAtTheTime INTEGER NOT NULL,
                        selectedAllocationPercentAtTheTime INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        adjustmentType TEXT,
                        notes TEXT,
                        originalTargetDate INTEGER,
                        adjustedTargetDate INTEGER,
                        catchUpAmountPerWeek INTEGER,
                        affectedWeeksCount INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
