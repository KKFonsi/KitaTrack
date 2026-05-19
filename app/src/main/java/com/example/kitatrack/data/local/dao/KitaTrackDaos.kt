package com.example.kitatrack.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.kitatrack.data.local.entity.CategoryEntity
import com.example.kitatrack.data.local.entity.TransactionEntity
import com.example.kitatrack.data.local.entity.BudgetEntity
import com.example.kitatrack.data.local.entity.PiggyBankEntity
import com.example.kitatrack.data.local.entity.PiggyBankTransactionEntity
import com.example.kitatrack.data.local.entity.PiggyBankMissedContributionEntity
import com.example.kitatrack.data.local.entity.DebtEntity
import com.example.kitatrack.data.local.entity.DebtTransactionEntity
import com.example.kitatrack.data.local.entity.SubscriptionEntity
import com.example.kitatrack.data.local.entity.SubscriptionTransactionEntity
import com.example.kitatrack.data.local.entity.MonthlySummaryEntity
import com.example.kitatrack.data.local.entity.AppSettingsEntity
import com.example.kitatrack.data.local.model.TransactionWithCategory
import com.example.kitatrack.data.local.model.NamedAmount
import com.example.kitatrack.data.local.model.DailyExpenseSummary
import com.example.kitatrack.data.local.model.MonthlyBalanceSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert suspend fun insert(transaction: TransactionEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(transactions: List<TransactionEntity>)
    @Update suspend fun update(transaction: TransactionEntity)
    @Delete suspend fun delete(transaction: TransactionEntity)

    @Query(
        """
        SELECT transactions.*, categories.name AS categoryName
        FROM transactions
        LEFT JOIN categories ON categories.id = transactions.categoryId
        ORDER BY occurredAt DESC, createdAt DESC
        """
    )
    fun getAllWithCategory(): Flow<List<TransactionWithCategory>>

    @Query(
        """
        SELECT transactions.*, categories.name AS categoryName
        FROM transactions
        LEFT JOIN categories ON categories.id = transactions.categoryId
        ORDER BY occurredAt ASC, createdAt ASC, id ASC
        """
    )
    suspend fun getAllForExport(): List<TransactionWithCategory>

    @Query(
        """
        SELECT transactions.*, categories.name AS categoryName
        FROM transactions
        LEFT JOIN categories ON categories.id = transactions.categoryId
        ORDER BY occurredAt DESC, createdAt DESC
        LIMIT :limit
        """
    )
    fun getRecentWithCategory(limit: Int): Flow<List<TransactionWithCategory>>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    fun getById(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getByIdOnce(id: Long): TransactionEntity?

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'INCOME'")
    fun getTotalIncome(): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'EXPENSE'")
    fun getTotalExpenses(): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0)
        FROM transactions
        WHERE type = 'INCOME' AND occurredAt BETWEEN :startMillis AND :endMillis
        """
    )
    fun getIncomeBetween(startMillis: Long, endMillis: Long): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0)
        FROM transactions
        WHERE transactions.type = 'EXPENSE' AND occurredAt BETWEEN :startMillis AND :endMillis
        """
    )
    fun getExpensesBetween(startMillis: Long, endMillis: Long): Flow<Long>

    @Query(
        """
        SELECT transactions.*, categories.name AS categoryName
        FROM transactions
        LEFT JOIN categories ON categories.id = transactions.categoryId
        WHERE occurredAt BETWEEN :startMillis AND :endMillis
        ORDER BY occurredAt DESC, createdAt DESC
        """
    )
    fun getTransactionsBetween(startMillis: Long, endMillis: Long): Flow<List<TransactionWithCategory>>

    @Query(
        """
        SELECT transactions.*, categories.name AS categoryName
        FROM transactions
        LEFT JOIN categories ON categories.id = transactions.categoryId
        WHERE transactions.type = 'EXPENSE' AND occurredAt BETWEEN :startMillis AND :endMillis
        ORDER BY amount DESC, occurredAt DESC
        LIMIT 1
        """
    )
    fun getHighestExpenseBetween(startMillis: Long, endMillis: Long): Flow<TransactionWithCategory?>

    @Query("SELECT COUNT(*) FROM transactions WHERE occurredAt BETWEEN :startMillis AND :endMillis")
    fun getTransactionCountBetween(startMillis: Long, endMillis: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions WHERE type = 'INCOME' AND occurredAt BETWEEN :startMillis AND :endMillis")
    fun getIncomeCountBetween(startMillis: Long, endMillis: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions WHERE type = 'EXPENSE' AND occurredAt BETWEEN :startMillis AND :endMillis")
    fun getExpenseCountBetween(startMillis: Long, endMillis: Long): Flow<Int>

    @Query(
        """
        SELECT categories.name AS name, COALESCE(SUM(transactions.amount), 0) AS totalAmount
        FROM transactions
        LEFT JOIN categories ON categories.id = transactions.categoryId
        WHERE transactions.type = 'EXPENSE' AND transactions.occurredAt BETWEEN :startMillis AND :endMillis
        GROUP BY categories.id, categories.name
        ORDER BY totalAmount DESC, categories.name COLLATE NOCASE ASC
        """
    )
    fun getExpenseTotalsByCategoryBetween(startMillis: Long, endMillis: Long): Flow<List<NamedAmount>>

    @Query(
        """
        SELECT categories.name AS name, COALESCE(SUM(transactions.amount), 0) AS totalAmount
        FROM transactions
        LEFT JOIN categories ON categories.id = transactions.categoryId
        WHERE transactions.type = 'INCOME' AND transactions.occurredAt BETWEEN :startMillis AND :endMillis
        GROUP BY categories.id, categories.name
        ORDER BY totalAmount DESC, categories.name COLLATE NOCASE ASC
        """
    )
    fun getIncomeTotalsBySourceBetween(startMillis: Long, endMillis: Long): Flow<List<NamedAmount>>

    @Query(
        """
        SELECT CAST(strftime('%d', occurredAt / 1000, 'unixepoch', 'localtime') AS INTEGER) AS dayOfMonth,
               COALESCE(SUM(amount), 0) AS totalAmount
        FROM transactions
        WHERE type = 'EXPENSE' AND occurredAt BETWEEN :startMillis AND :endMillis
        GROUP BY dayOfMonth
        ORDER BY dayOfMonth ASC
        """
    )
    fun getDailyExpensesBetween(startMillis: Long, endMillis: Long): Flow<List<DailyExpenseSummary>>

    @Query(
        """
        SELECT strftime('%Y-%m', occurredAt / 1000, 'unixepoch', 'localtime') AS monthKey,
               COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END), 0) AS totalIncome,
               COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END), 0) AS totalExpenses
        FROM transactions
        WHERE occurredAt BETWEEN :startMillis AND :endMillis
        GROUP BY monthKey
        ORDER BY monthKey ASC
        """
    )
    fun getMonthlyBalanceSummariesBetween(startMillis: Long, endMillis: Long): Flow<List<MonthlyBalanceSummary>>

    @Query("DELETE FROM transactions")
    suspend fun clearAll()
}

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAll(categories: List<CategoryEntity>)

    @Update suspend fun update(category: CategoryEntity)
    @Delete suspend fun delete(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE isArchived = 0 ORDER BY type ASC, isDefault DESC, name COLLATE NOCASE ASC")
    fun getAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY id ASC")
    suspend fun getAllForExport(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE type = 'EXPENSE' AND isArchived = 0 ORDER BY isDefault DESC, name COLLATE NOCASE ASC")
    fun getExpenseCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE type = 'INCOME_SOURCE' AND isArchived = 0 ORDER BY isDefault DESC, name COLLATE NOCASE ASC")
    fun getIncomeSources(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE LOWER(name) = LOWER(:name) AND type = :type LIMIT 1")
    suspend fun getByNameAndType(name: String, type: String): CategoryEntity?

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId")
    suspend fun countTransactionsUsingCategory(categoryId: Long): Int

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM categories WHERE isDefault = 1 AND type = :type")
    suspend fun countDefaultsByType(type: String): Int

    @Query("DELETE FROM categories")
    suspend fun clearAll()
}

@Dao
interface BudgetDao {
    @Insert suspend fun insert(item: BudgetEntity): Long
    @Update suspend fun update(item: BudgetEntity)
    @Delete suspend fun delete(item: BudgetEntity)
    @Query("SELECT * FROM budgets WHERE id = :id LIMIT 1") suspend fun getById(id: Long): BudgetEntity?
    @Query("SELECT * FROM budgets ORDER BY isActive DESC, updatedAt DESC, name COLLATE NOCASE ASC") fun getAll(): Flow<List<BudgetEntity>>
    @Query("SELECT * FROM budgets WHERE isActive = 1 ORDER BY updatedAt DESC") fun getActive(): Flow<List<BudgetEntity>>
    @Query("SELECT * FROM budgets ORDER BY id ASC") suspend fun getAllForExport(): List<BudgetEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<BudgetEntity>)
    @Query("DELETE FROM budgets") suspend fun clearAll()
}
@Dao
interface PiggyBankDao {
    @Insert suspend fun insert(item: PiggyBankEntity): Long
    @Update suspend fun update(item: PiggyBankEntity)
    @Delete suspend fun delete(item: PiggyBankEntity)
    @Query("SELECT * FROM piggy_banks WHERE id = :id LIMIT 1") suspend fun getById(id: Long): PiggyBankEntity?
    @Query("SELECT * FROM piggy_banks WHERE isArchived = 0 ORDER BY isActive DESC, updatedAt DESC") fun getAll(): Flow<List<PiggyBankEntity>>
    @Query("SELECT * FROM piggy_banks WHERE isActive = 1 AND isArchived = 0 ORDER BY updatedAt DESC") fun getActive(): Flow<List<PiggyBankEntity>>
    @Query("SELECT * FROM piggy_banks ORDER BY id ASC") suspend fun getAllForExport(): List<PiggyBankEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<PiggyBankEntity>)
    @Query("DELETE FROM piggy_banks") suspend fun clearAll()
}
@Dao
interface PiggyBankTransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(item: PiggyBankTransactionEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<PiggyBankTransactionEntity>)
    @Query("SELECT * FROM piggy_bank_transactions WHERE piggyBankId = :piggyBankId ORDER BY date DESC, createdAt DESC") fun getForPiggyBank(piggyBankId: Long): Flow<List<PiggyBankTransactionEntity>>
    @Query("SELECT COALESCE(SUM(amount), 0) FROM piggy_bank_transactions WHERE transactionType = 'AUTO_ALLOCATION' AND date BETWEEN :startMillis AND :endMillis")
    fun getAutoAllocationTotalBetween(startMillis: Long, endMillis: Long): Flow<Long>
    @Query("SELECT COALESCE(SUM(amount), 0) FROM piggy_bank_transactions WHERE sourceTransactionId = :transactionId AND transactionType = 'AUTO_ALLOCATION'")
    suspend fun getAllocationForSourceTransaction(transactionId: Long): Long
    @Query("SELECT COALESCE(SUM(amount), 0) FROM piggy_bank_transactions WHERE piggyBankId = :piggyBankId AND transactionType IN ('AUTO_ALLOCATION','MANUAL_ADD') AND date BETWEEN :startMillis AND :endMillis")
    suspend fun getContributionTotalBetween(piggyBankId: Long, startMillis: Long, endMillis: Long): Long
    @Query("SELECT * FROM piggy_bank_transactions ORDER BY id ASC") suspend fun getAllForExport(): List<PiggyBankTransactionEntity>
    @Query("DELETE FROM piggy_bank_transactions") suspend fun clearAll()
}
@Dao
interface PiggyBankMissedContributionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(item: PiggyBankMissedContributionEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<PiggyBankMissedContributionEntity>)
    @Update suspend fun update(item: PiggyBankMissedContributionEntity)
    @Query("SELECT * FROM piggy_bank_missed_contributions WHERE piggyBankId = :piggyBankId ORDER BY expectedDate DESC")
    fun getForPiggyBank(piggyBankId: Long): Flow<List<PiggyBankMissedContributionEntity>>
    @Query("SELECT * FROM piggy_bank_missed_contributions WHERE piggyBankId = :piggyBankId AND expectedDate = :expectedDate LIMIT 1")
    suspend fun getForWeek(piggyBankId: Long, expectedDate: Long): PiggyBankMissedContributionEntity?
    @Query("SELECT * FROM piggy_bank_missed_contributions WHERE status IN ('MISSED','PARTIAL') ORDER BY expectedDate DESC")
    fun getUnresolved(): Flow<List<PiggyBankMissedContributionEntity>>
    @Query("SELECT * FROM piggy_bank_missed_contributions ORDER BY id ASC")
    suspend fun getAllForExport(): List<PiggyBankMissedContributionEntity>
    @Query("DELETE FROM piggy_bank_missed_contributions") suspend fun clearAll()
}
@Dao
interface DebtDao {
    @Insert suspend fun insert(item: DebtEntity): Long
    @Update suspend fun update(item: DebtEntity)
    @Delete suspend fun delete(item: DebtEntity)
    @Query("SELECT * FROM debts WHERE id = :id LIMIT 1") suspend fun getById(id: Long): DebtEntity?
    @Query("SELECT * FROM debts WHERE isArchived = 0 ORDER BY isActive DESC, nextDueDate ASC, updatedAt DESC")
    fun getAll(): Flow<List<DebtEntity>>
    @Query("SELECT * FROM debts WHERE isActive = 1 AND isArchived = 0 ORDER BY nextDueDate ASC")
    fun getActive(): Flow<List<DebtEntity>>
    @Query("SELECT * FROM debts ORDER BY id ASC") suspend fun getAllForExport(): List<DebtEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<DebtEntity>)
    @Query("DELETE FROM debts") suspend fun clearAll()
}
@Dao
interface DebtTransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(item: DebtTransactionEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<DebtTransactionEntity>)
    @Query("SELECT * FROM debt_transactions WHERE debtId = :debtId ORDER BY date DESC, createdAt DESC")
    fun getForDebt(debtId: Long): Flow<List<DebtTransactionEntity>>
    @Query("SELECT * FROM debt_transactions ORDER BY id ASC") suspend fun getAllForExport(): List<DebtTransactionEntity>
    @Query("SELECT COALESCE(SUM(amount), 0) FROM debt_transactions WHERE transactionType = 'RESERVE_ALLOCATION' AND date BETWEEN :startMillis AND :endMillis")
    fun getReserveAllocationBetween(startMillis: Long, endMillis: Long): Flow<Long>
    @Query("DELETE FROM debt_transactions") suspend fun clearAll()
}
@Dao
interface SubscriptionDao {
    @Insert suspend fun insert(item: SubscriptionEntity): Long
    @Update suspend fun update(item: SubscriptionEntity)
    @Delete suspend fun delete(item: SubscriptionEntity)
    @Query("SELECT * FROM subscriptions WHERE id = :id LIMIT 1") suspend fun getById(id: Long): SubscriptionEntity?
    @Query("SELECT * FROM subscriptions ORDER BY isArchived ASC, isActive DESC, nextBillingDate ASC, name ASC")
    fun getAll(): Flow<List<SubscriptionEntity>>
    @Query("SELECT * FROM subscriptions WHERE isActive = 1 AND isArchived = 0 ORDER BY nextBillingDate ASC")
    fun getActive(): Flow<List<SubscriptionEntity>>
    @Query("SELECT * FROM subscriptions ORDER BY id ASC") suspend fun getAllForExport(): List<SubscriptionEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<SubscriptionEntity>)
    @Query("DELETE FROM subscriptions") suspend fun clearAll()
}
@Dao
interface SubscriptionTransactionDao {
    @Insert suspend fun insert(item: SubscriptionTransactionEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<SubscriptionTransactionEntity>)
    @Query("SELECT * FROM subscription_transactions WHERE subscriptionId = :subscriptionId ORDER BY date DESC, createdAt DESC")
    fun getForSubscription(subscriptionId: Long): Flow<List<SubscriptionTransactionEntity>>
    @Query("SELECT * FROM subscription_transactions ORDER BY id ASC") suspend fun getAllForExport(): List<SubscriptionTransactionEntity>
    @Query("DELETE FROM subscription_transactions") suspend fun clearAll()
}
@Dao
interface MonthlySummaryDao {
    @Query("SELECT * FROM monthly_summaries ORDER BY monthKey ASC") suspend fun getAllForExport(): List<MonthlySummaryEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<MonthlySummaryEntity>)
    @Query("DELETE FROM monthly_summaries") suspend fun clearAll()
}
@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1") suspend fun getForExport(): AppSettingsEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(item: AppSettingsEntity)
    @Query("DELETE FROM app_settings") suspend fun clearAll()
}
