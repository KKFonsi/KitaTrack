package com.example.kitatrack.data.local

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.kitatrack.data.local.dao.AppSettingsDao
import com.example.kitatrack.data.local.dao.BudgetDao
import com.example.kitatrack.data.local.dao.CategoryDao
import com.example.kitatrack.data.local.dao.DebtDao
import com.example.kitatrack.data.local.dao.DebtTransactionDao
import com.example.kitatrack.data.local.dao.MonthlySummaryDao
import com.example.kitatrack.data.local.dao.PiggyBankDao
import com.example.kitatrack.data.local.dao.PiggyBankTransactionDao
import com.example.kitatrack.data.local.dao.PiggyBankMissedContributionDao
import com.example.kitatrack.data.local.dao.SubscriptionDao
import com.example.kitatrack.data.local.dao.SubscriptionTransactionDao
import com.example.kitatrack.data.local.dao.TransactionDao
import com.example.kitatrack.data.local.entity.AppSettingsEntity
import com.example.kitatrack.data.local.entity.BudgetEntity
import com.example.kitatrack.data.local.entity.CategoryEntity
import com.example.kitatrack.data.local.entity.DebtEntity
import com.example.kitatrack.data.local.entity.DebtTransactionEntity
import com.example.kitatrack.data.local.entity.MonthlySummaryEntity
import com.example.kitatrack.data.local.entity.PiggyBankEntity
import com.example.kitatrack.data.local.entity.PiggyBankTransactionEntity
import com.example.kitatrack.data.local.entity.PiggyBankMissedContributionEntity
import com.example.kitatrack.data.local.entity.SubscriptionEntity
import com.example.kitatrack.data.local.entity.SubscriptionTransactionEntity
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
        DebtTransactionEntity::class,
        SubscriptionEntity::class,
        SubscriptionTransactionEntity::class,
        MonthlySummaryEntity::class,
        AppSettingsEntity::class
    ],
    version = 9,
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
    abstract fun debtTransactionDao(): DebtTransactionDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun subscriptionTransactionDao(): SubscriptionTransactionDao
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

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS debts_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        personName TEXT,
                        debtType TEXT NOT NULL,
                        totalAmount INTEGER NOT NULL,
                        amountPaid INTEGER NOT NULL,
                        remainingAmount INTEGER NOT NULL,
                        reservedAmount INTEGER NOT NULL,
                        dueDate INTEGER,
                        nextDueDate INTEGER,
                        startDate INTEGER,
                        endDate INTEGER,
                        paymentFrequency TEXT NOT NULL,
                        customIntervalDays INTEGER,
                        installmentAmount INTEGER,
                        isRecurring INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        notes TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        isActive INTEGER NOT NULL,
                        isArchived INTEGER NOT NULL,
                        completedAt INTEGER,
                        priority INTEGER NOT NULL,
                        autoReserveEnabled INTEGER NOT NULL,
                        reservePercent INTEGER,
                        fixedReserveAmount INTEGER,
                        lastPaymentDate INTEGER,
                        reminderEnabled INTEGER NOT NULL,
                        reminderTimingDays INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO debts_new (
                        id, name, personName, debtType, totalAmount, amountPaid, remainingAmount,
                        reservedAmount, dueDate, nextDueDate, startDate, endDate, paymentFrequency,
                        customIntervalDays, installmentAmount, isRecurring, status, notes, createdAt,
                        updatedAt, isActive, isArchived, completedAt, priority, autoReserveEnabled,
                        reservePercent, fixedReserveAmount, lastPaymentDate, reminderEnabled, reminderTimingDays
                    )
                    SELECT
                        id, name, NULL, 'I_OWE', totalAmount, MAX(totalAmount - remainingAmount, 0), remainingAmount,
                        0, dueDate, dueDate, NULL, NULL, 'ONE_TIME',
                        NULL, NULL, 0,
                        CASE WHEN remainingAmount <= 0 THEN 'PAID' ELSE 'ACTIVE' END,
                        NULL, 0, 0, isActive, 0, NULL, 0, 1,
                        NULL, NULL, NULL, 0, NULL
                    FROM debts
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE debts")
                db.execSQL("ALTER TABLE debts_new RENAME TO debts")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS debt_transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        debtId INTEGER NOT NULL,
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

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS subscriptions_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        categoryId INTEGER,
                        billingCycle TEXT NOT NULL,
                        customIntervalDays INTEGER,
                        nextBillingDate INTEGER,
                        startDate INTEGER,
                        endDate INTEGER,
                        reserveEnabled INTEGER NOT NULL,
                        reservedAmount INTEGER NOT NULL,
                        importance TEXT NOT NULL,
                        status TEXT NOT NULL,
                        notes TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        isActive INTEGER NOT NULL,
                        isArchived INTEGER NOT NULL,
                        lastPaidDate INTEGER,
                        completedAt INTEGER,
                        autoPay INTEGER NOT NULL,
                        paymentMethod TEXT,
                        reminderEnabled INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO subscriptions_new (
                        id, name, amount, categoryId, billingCycle, customIntervalDays,
                        nextBillingDate, startDate, endDate, reserveEnabled, reservedAmount,
                        importance, status, notes, createdAt, updatedAt, isActive, isArchived,
                        lastPaidDate, completedAt, autoPay, paymentMethod, reminderEnabled
                    )
                    SELECT
                        id, name, amount, NULL,
                        CASE WHEN billingCycle = '' THEN 'MONTHLY' ELSE billingCycle END,
                        NULL, nextDueDate, NULL, NULL, reserveEnabled, 0,
                        'MEDIUM',
                        CASE WHEN isActive = 1 THEN 'ACTIVE' ELSE 'PAUSED' END,
                        NULL, 0, 0, isActive, 0, NULL, NULL, 0, NULL, 0
                    FROM subscriptions
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE subscriptions")
                db.execSQL("ALTER TABLE subscriptions_new RENAME TO subscriptions")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS subscription_transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        subscriptionId INTEGER NOT NULL,
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
    }
}
