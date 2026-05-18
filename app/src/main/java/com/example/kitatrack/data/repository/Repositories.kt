package com.example.kitatrack.data.repository

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
import com.example.kitatrack.data.local.entity.CategoryEntity
import com.example.kitatrack.data.local.entity.TransactionEntity
import com.example.kitatrack.data.local.entity.BudgetEntity
import com.example.kitatrack.data.local.entity.PiggyBankEntity
import com.example.kitatrack.data.local.entity.PiggyBankTransactionEntity
import com.example.kitatrack.data.local.entity.PiggyBankMissedContributionEntity
import com.example.kitatrack.data.local.model.BudgetProgress
import com.example.kitatrack.data.local.model.PiggyBankAllocationPlan
import com.example.kitatrack.util.DateRanges
import kotlinx.coroutines.flow.combine

class TransactionRepository(private val dao: TransactionDao) {
    fun getAllTransactions() = dao.getAllWithCategory()
    fun getRecentTransactions(limit: Int = 5) = dao.getRecentWithCategory(limit)
    fun getTransaction(id: Long) = dao.getById(id)
    suspend fun getTransactionOnce(id: Long) = dao.getByIdOnce(id)
    fun getTotalIncome() = dao.getTotalIncome()
    fun getTotalExpenses() = dao.getTotalExpenses()
    fun getIncomeBetween(startMillis: Long, endMillis: Long) = dao.getIncomeBetween(startMillis, endMillis)
    fun getExpensesBetween(startMillis: Long, endMillis: Long) = dao.getExpensesBetween(startMillis, endMillis)
    fun getTransactionsBetween(startMillis: Long, endMillis: Long) = dao.getTransactionsBetween(startMillis, endMillis)
    fun getHighestExpenseBetween(startMillis: Long, endMillis: Long) = dao.getHighestExpenseBetween(startMillis, endMillis)
    fun getTransactionCountBetween(startMillis: Long, endMillis: Long) = dao.getTransactionCountBetween(startMillis, endMillis)
    fun getIncomeCountBetween(startMillis: Long, endMillis: Long) = dao.getIncomeCountBetween(startMillis, endMillis)
    fun getExpenseCountBetween(startMillis: Long, endMillis: Long) = dao.getExpenseCountBetween(startMillis, endMillis)
    fun getExpenseTotalsByCategoryBetween(startMillis: Long, endMillis: Long) = dao.getExpenseTotalsByCategoryBetween(startMillis, endMillis)
    fun getIncomeTotalsBySourceBetween(startMillis: Long, endMillis: Long) = dao.getIncomeTotalsBySourceBetween(startMillis, endMillis)
    fun getDailyExpensesBetween(startMillis: Long, endMillis: Long) = dao.getDailyExpensesBetween(startMillis, endMillis)
    fun getMonthlyBalanceSummariesBetween(startMillis: Long, endMillis: Long) =
        dao.getMonthlyBalanceSummariesBetween(startMillis, endMillis)
    suspend fun insert(transaction: TransactionEntity) = dao.insert(transaction)
    suspend fun update(transaction: TransactionEntity) = dao.update(transaction)
    suspend fun delete(transaction: TransactionEntity) = dao.delete(transaction)
}

class CategoryRepository(private val dao: CategoryDao) {
    fun getAllCategories() = dao.getAll()
    fun getExpenseCategories() = dao.getExpenseCategories()
    fun getIncomeSources() = dao.getIncomeSources()
    suspend fun getCategory(id: Long) = dao.getById(id)
    suspend fun getCategoryByNameAndType(name: String, type: String) = dao.getByNameAndType(name.trim(), type)

    suspend fun ensureDefaultCategories() {
        val now = System.currentTimeMillis()
        if (dao.countDefaultsByType(TYPE_EXPENSE) < DEFAULT_EXPENSE_CATEGORIES.size) dao.insertAll(
            DEFAULT_EXPENSE_CATEGORIES.map {
                CategoryEntity(
                    name = it,
                    type = TYPE_EXPENSE,
                    isDefault = true,
                    createdAt = now,
                    updatedAt = now
                )
            }
        )
        if (dao.countDefaultsByType(TYPE_INCOME_SOURCE) < DEFAULT_INCOME_SOURCES.size) dao.insertAll(
            DEFAULT_INCOME_SOURCES.map {
                CategoryEntity(
                    name = it,
                    type = TYPE_INCOME_SOURCE,
                    isDefault = true,
                    createdAt = now,
                    updatedAt = now
                )
            }
        )
    }

    suspend fun addCustomCategory(name: String, type: String): Result<Unit> {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return Result.failure(IllegalArgumentException("Category name cannot be empty."))
        if (dao.getByNameAndType(cleanName, type) != null) return Result.failure(IllegalArgumentException("A category with that name already exists."))
        val now = System.currentTimeMillis()
        dao.insert(
            CategoryEntity(
                name = cleanName,
                type = type,
                isDefault = false,
                createdAt = now,
                updatedAt = now
            )
        )
        return Result.success(Unit)
    }

    suspend fun renameCustomCategory(category: CategoryEntity, newName: String): Result<Unit> {
        if (category.isDefault) return Result.failure(IllegalArgumentException("Default categories cannot be renamed."))
        val cleanName = newName.trim()
        if (cleanName.isBlank()) return Result.failure(IllegalArgumentException("Category name cannot be empty."))
        val existing = dao.getByNameAndType(cleanName, category.type)
        if (existing != null && existing.id != category.id) {
            return Result.failure(IllegalArgumentException("A category with that name already exists."))
        }
        dao.update(category.copy(name = cleanName, updatedAt = System.currentTimeMillis()))
        return Result.success(Unit)
    }

    suspend fun deleteCustomCategory(category: CategoryEntity): Result<Unit> {
        if (category.isDefault) return Result.failure(IllegalArgumentException("Default categories cannot be deleted."))
        if (dao.countTransactionsUsingCategory(category.id) > 0) {
            return Result.failure(IllegalArgumentException("This category is already used by transactions."))
        }
        dao.delete(category)
        return Result.success(Unit)
    }

    companion object {
        const val TYPE_EXPENSE = "EXPENSE"
        const val TYPE_INCOME_SOURCE = "INCOME_SOURCE"
        val DEFAULT_EXPENSE_CATEGORIES = listOf(
            "Food & Drinks",
            "Transportation",
            "Load / Internet",
            "Shopping",
            "School",
            "Bills",
            "Subscriptions",
            "Gaming",
            "Health",
            "Personal Care",
            "Family",
            "Donations",
            "Savings",
            "Debt / Loans",
            "Cash Transfer",
            "Emergency",
            "Other"
        )
        val DEFAULT_INCOME_SOURCES = listOf(
            "Allowance",
            "Salary",
            "Freelance",
            "Gift",
            "Cash on hand",
            "Bank transfer",
            "Refund",
            "Other"
        )
    }
}

class BudgetRepository(
    private val dao: BudgetDao,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao
) {
    fun getAllBudgets() = dao.getAll()
    fun getActiveBudgets() = dao.getActive()

    fun getBudgetProgress() = combine(
        dao.getAll(),
        transactionDao.getAllWithCategory(),
        categoryDao.getAll()
    ) { budgets, transactions, categories ->
        val names = categories.associate { it.id to it.name }
        budgets.map { budget ->
            val range = when (budget.budgetType) {
                TYPE_WEEKLY_OVERALL, TYPE_CATEGORY_WEEKLY -> DateRanges.currentWeek()
                TYPE_MONTHLY_OVERALL, TYPE_CATEGORY_MONTHLY -> DateRanges.currentMonth()
                TYPE_CUSTOM_RANGE -> (budget.startDate ?: 0L)..(budget.endDate ?: Long.MAX_VALUE)
                else -> DateRanges.currentMonth()
            }
            val used = transactions.filter {
                it.transaction.type == "EXPENSE" &&
                    it.transaction.occurredAt in range &&
                    (budget.categoryId == null || it.transaction.categoryId == budget.categoryId)
            }.sumOf { it.transaction.amount }
            val remaining = budget.amountLimit - used
            val percent = if (budget.amountLimit <= 0) 0 else ((used * 100) / budget.amountLimit).toInt()
            BudgetProgress(
                budgetId = budget.id,
                name = budget.name,
                budgetType = budget.budgetType,
                limitAmount = budget.amountLimit,
                usedAmount = used,
                remainingAmount = remaining,
                usagePercent = percent,
                isNearLimit = percent >= 85 && percent < 100,
                isOverLimit = percent >= 100,
                categoryName = budget.categoryId?.let { names[it] },
                periodLabel = when (budget.budgetType) {
                    TYPE_WEEKLY_OVERALL, TYPE_CATEGORY_WEEKLY -> "This Week"
                    TYPE_MONTHLY_OVERALL, TYPE_CATEGORY_MONTHLY -> "This Month"
                    else -> "Custom Range"
                },
                isActive = budget.isActive
            )
        }
    }

    suspend fun createBudget(name: String, type: String, amount: Long, categoryId: Long?, active: Boolean): Result<Unit> =
        saveBudget(null, name, type, amount, categoryId, active)

    suspend fun updateBudget(id: Long, name: String, type: String, amount: Long, categoryId: Long?, active: Boolean): Result<Unit> =
        saveBudget(id, name, type, amount, categoryId, active)

    suspend fun deleteBudget(id: Long) {
        dao.getById(id)?.let { dao.delete(it) }
    }

    private suspend fun saveBudget(id: Long?, name: String, type: String, amount: Long, categoryId: Long?, active: Boolean): Result<Unit> {
        val clean = name.trim()
        if (clean.isBlank()) return Result.failure(IllegalArgumentException("Budget name cannot be empty."))
        if (amount <= 0) return Result.failure(IllegalArgumentException("Budget amount must be greater than 0."))
        if (type !in TYPES) return Result.failure(IllegalArgumentException("Select a valid budget type."))
        if (type in CATEGORY_TYPES && categoryId == null) return Result.failure(IllegalArgumentException("Select a category for this budget."))
        val budgets = dao.getAllForExport()
        if (active && budgets.any { it.id != id && it.isActive && duplicateKey(it) == duplicateKey(type, categoryId) }) {
            return Result.failure(IllegalArgumentException("An active budget of this type already exists."))
        }
        val now = System.currentTimeMillis()
        val existing = id?.let { dao.getById(it) }
        val entity = BudgetEntity(
            id = existing?.id ?: 0,
            categoryId = if (type in CATEGORY_TYPES) categoryId else null,
            name = clean,
            amountLimit = amount,
            budgetType = type,
            isActive = active,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        if (existing == null) dao.insert(entity) else dao.update(entity)
        return Result.success(Unit)
    }

    private fun duplicateKey(budget: BudgetEntity) = duplicateKey(budget.budgetType, budget.categoryId)
    private fun duplicateKey(type: String, categoryId: Long?) = if (type in CATEGORY_TYPES) "$type:$categoryId" else type

    companion object {
        const val TYPE_WEEKLY_OVERALL = "WEEKLY_OVERALL"
        const val TYPE_MONTHLY_OVERALL = "MONTHLY_OVERALL"
        const val TYPE_CATEGORY_WEEKLY = "CATEGORY_WEEKLY"
        const val TYPE_CATEGORY_MONTHLY = "CATEGORY_MONTHLY"
        const val TYPE_CUSTOM_RANGE = "CUSTOM_RANGE"
        val TYPES = setOf(TYPE_WEEKLY_OVERALL, TYPE_MONTHLY_OVERALL, TYPE_CATEGORY_WEEKLY, TYPE_CATEGORY_MONTHLY, TYPE_CUSTOM_RANGE)
        val CATEGORY_TYPES = setOf(TYPE_CATEGORY_WEEKLY, TYPE_CATEGORY_MONTHLY)
    }
}
class PiggyBankRepository(
    private val dao: PiggyBankDao,
    private val transactionDao: PiggyBankTransactionDao,
    private val missedDao: PiggyBankMissedContributionDao
) {
    fun getAllPiggyBanks() = dao.getAll()
    fun getActivePiggyBanks() = dao.getActive()
    fun getUnresolvedMissedContributions() = missedDao.getUnresolved()
    fun getMissedContributionsForPiggyBank(id: Long) = missedDao.getForPiggyBank(id)
    suspend fun totalActiveSaved(): Long = dao.getAllForExport().filter { it.isActive && !it.isArchived }.sumOf { it.currentAmount }
    suspend fun save(
        existingId: Long?,
        name: String,
        targetAmount: Long,
        currentAmount: Long,
        weeklyIncomePrediction: Long,
        selectedAllocationPercent: Int,
        targetDate: Long?,
        notes: String?,
        allowOverSaving: Boolean,
        isActive: Boolean
    ): Result<Unit> {
        if (name.trim().isBlank()) return Result.failure(IllegalArgumentException("Goal name is required."))
        if (targetAmount <= 0) return Result.failure(IllegalArgumentException("Target amount must be greater than 0."))
        if (currentAmount < 0) return Result.failure(IllegalArgumentException("Current amount cannot be negative."))
        if (!allowOverSaving && currentAmount > targetAmount) return Result.failure(IllegalArgumentException("Current amount cannot exceed target unless over-saving is enabled."))
        if (weeklyIncomePrediction <= 0) return Result.failure(IllegalArgumentException("Weekly income prediction must be greater than 0."))
        if (targetDate == null || targetDate <= System.currentTimeMillis()) return Result.failure(IllegalArgumentException("Target date must be in the future."))
        val existing = dao.getAllForExport()
        val plan = calculateAllocationPlan(targetAmount, currentAmount, weeklyIncomePrediction, targetDate, existing.filter { it.id != existingId && it.isActive && !it.isArchived }.sumOf { it.selectedAllocationPercent })
        if (!plan.isPossible && isActive) return Result.failure(IllegalArgumentException(plan.warning ?: "This goal is not currently possible."))
        if (selectedAllocationPercent !in plan.minPercent..plan.maxPercent) return Result.failure(IllegalArgumentException("Selected allocation must stay within the calculated range."))
        val now = System.currentTimeMillis()
        val old = existingId?.let { dao.getById(it) }
        val entity = PiggyBankEntity(
            id = old?.id ?: 0,
            name = name.trim(),
            targetAmount = targetAmount,
            currentAmount = currentAmount,
            weeklyIncomePrediction = weeklyIncomePrediction,
            selectedAllocationPercent = selectedAllocationPercent,
            minAllocationPercent = plan.minPercent,
            maxAllocationPercent = plan.maxPercent,
            isGoalPossible = plan.isPossible,
            targetDate = targetDate,
            createdAt = old?.createdAt ?: now,
            updatedAt = now,
            isActive = isActive,
            isArchived = old?.isArchived ?: false,
            notes = notes?.trim()?.ifBlank { null },
            allowOverSaving = allowOverSaving
        )
        val savedId = if (old == null) dao.insert(entity) else {
            dao.update(entity)
            entity.id
        }
        refreshMissedContributions(savedId)
        return Result.success(Unit)
    }
    suspend fun archive(id: Long) { dao.getById(id)?.let { dao.update(it.copy(isActive = false, isArchived = true, updatedAt = System.currentTimeMillis())) } }
    suspend fun manualAdjust(id: Long, amount: Long, add: Boolean): Result<Unit> {
        val bank = dao.getById(id) ?: return Result.failure(IllegalArgumentException("Piggy bank not found."))
        val next = if (add) bank.currentAmount + amount else bank.currentAmount - amount
        if (amount <= 0) return Result.failure(IllegalArgumentException("Amount must be greater than 0."))
        if (next < 0) return Result.failure(IllegalArgumentException("Piggy bank cannot go below 0."))
        val capped = if (!bank.allowOverSaving) minOf(next, bank.targetAmount) else next
        dao.update(bank.copy(currentAmount = capped, updatedAt = System.currentTimeMillis()))
        transactionDao.insert(PiggyBankTransactionEntity(piggyBankId = id, amount = amount, transactionType = if (add) "MANUAL_ADD" else "MANUAL_REMOVE", date = System.currentTimeMillis(), createdAt = System.currentTimeMillis()))
        refreshMissedContributions(id)
        return Result.success(Unit)
    }
    suspend fun allocateFromIncome(incomeTransactionId: Long, incomeAmount: Long, date: Long): Long {
        var allocated = 0L
        dao.getAllForExport().filter { it.isActive && !it.isArchived && it.selectedAllocationPercent > 0 }.forEach { bank ->
            val proposed = incomeAmount * bank.selectedAllocationPercent / 100
            val remainingNeed = (bank.targetAmount - bank.currentAmount).coerceAtLeast(0)
            val amount = if (bank.allowOverSaving) proposed else minOf(proposed, remainingNeed)
            if (amount > 0) {
                dao.update(bank.copy(currentAmount = bank.currentAmount + amount, updatedAt = System.currentTimeMillis()))
                transactionDao.insert(PiggyBankTransactionEntity(piggyBankId = bank.id, amount = amount, transactionType = "AUTO_ALLOCATION", sourceTransactionId = incomeTransactionId, date = date, createdAt = System.currentTimeMillis()))
                allocated += amount
                refreshMissedContributions(bank.id)
            }
        }
        return allocated
    }
    fun calculateAllocationPlan(
        targetAmount: Long,
        currentAmount: Long,
        weeklyIncomePrediction: Long,
        targetDate: Long,
        otherActiveAllocationPercent: Int
    ): PiggyBankAllocationPlan {
        if (targetAmount <= 0 || currentAmount < 0 || weeklyIncomePrediction <= 0 || targetDate <= System.currentTimeMillis()) {
            return PiggyBankAllocationPlan(false, false, 0, 0, 0.0, 0, 0, 0, 0, 0, "Complete the required fields to calculate this goal.")
        }
        val remaining = (targetAmount - currentAmount).coerceAtLeast(0)
        if (remaining == 0L) return PiggyBankAllocationPlan(true, true, 0, 0, 0.0, 0, 0, 100 - otherActiveAllocationPercent, 0, 0, null)
        val days = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(targetDate - System.currentTimeMillis()).coerceAtLeast(1)
        val weeks = days / 7.0
        val requiredWeekly = kotlin.math.ceil(remaining / weeks).toLong()
        val min = kotlin.math.ceil(requiredWeekly.toDouble() / weeklyIncomePrediction.toDouble() * 100.0).toInt().coerceAtLeast(0)
        val max = (100 - otherActiveAllocationPercent).coerceAtLeast(0)
        val possible = min <= max && min <= 100
        val selected = if (possible) min else 0
        val estimatedWeekly = weeklyIncomePrediction * selected / 100
        val warning = when {
            min > 100 -> "This goal is not possible with your current weekly income prediction and target date."
            min > max -> "This goal is not possible unless you reduce other piggy bank allocations, adjust the target amount, or extend the target date."
            else -> null
        }
        return PiggyBankAllocationPlan(true, possible, remaining, days, weeks, requiredWeekly, min, max, selected, estimatedWeekly, warning)
    }
    fun getMonthlyAutoAllocation(startMillis: Long, endMillis: Long) = transactionDao.getAutoAllocationTotalBetween(startMillis, endMillis)
    suspend fun getAllocationForTransaction(transactionId: Long) = transactionDao.getAllocationForSourceTransaction(transactionId)
    suspend fun deductExpense(id: Long, amount: Long, sourceTransactionId: Long): Result<Unit> {
        val bank = dao.getById(id) ?: return Result.failure(IllegalArgumentException("Piggy bank not found."))
        if (amount <= 0) return Result.failure(IllegalArgumentException("Amount must be greater than 0."))
        if (amount > bank.currentAmount) return Result.failure(IllegalArgumentException("Expense exceeds piggy bank savings."))
        dao.update(bank.copy(currentAmount = bank.currentAmount - amount, updatedAt = System.currentTimeMillis()))
        transactionDao.insert(PiggyBankTransactionEntity(piggyBankId = id, amount = amount, transactionType = "EXPENSE_DEDUCTION", sourceTransactionId = sourceTransactionId, date = System.currentTimeMillis(), createdAt = System.currentTimeMillis()))
        refreshMissedContributions(id)
        return Result.success(Unit)
    }

    suspend fun refreshMissedContributions(id: Long) {
        val bank = dao.getById(id) ?: return
        if (!bank.isActive || bank.isArchived || bank.selectedAllocationPercent <= 0 || bank.weeklyIncomePrediction <= 0) return
        val expected = bank.weeklyIncomePrediction * bank.selectedAllocationPercent / 100
        if (expected <= 0) return
        val now = System.currentTimeMillis()
        var weekStart = com.example.kitatrack.util.DateRanges.weekRange(java.util.Calendar.getInstance().apply { timeInMillis = bank.createdAt }).first
        val currentWeekStart = com.example.kitatrack.util.DateRanges.currentWeek().first
        while (weekStart < currentWeekStart) {
            val weekEnd = weekStart + java.util.concurrent.TimeUnit.DAYS.toMillis(7) - 1
            val actual = transactionDao.getContributionTotalBetween(bank.id, weekStart, weekEnd)
            val missed = (expected - actual).coerceAtLeast(0)
            val existing = missedDao.getForWeek(bank.id, weekStart)
            when {
                missed <= 0 && existing != null && existing.status in setOf(STATUS_MISSED, STATUS_PARTIAL) ->
                    missedDao.update(existing.copy(actualAmount = actual, missedAmount = 0, status = STATUS_RESOLVED, updatedAt = now))
                missed > 0 && existing == null ->
                    missedDao.insert(
                        PiggyBankMissedContributionEntity(
                            piggyBankId = bank.id,
                            expectedDate = weekStart,
                            expectedAmount = expected,
                            actualAmount = actual,
                            missedAmount = missed,
                            weeklyIncomePredictionAtTheTime = bank.weeklyIncomePrediction,
                            selectedAllocationPercentAtTheTime = bank.selectedAllocationPercent,
                            status = if (actual == 0L) STATUS_MISSED else STATUS_PARTIAL,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                missed > 0 && existing != null && existing.status !in setOf(STATUS_SKIPPED, STATUS_RESOLVED) ->
                    missedDao.update(existing.copy(expectedAmount = expected, actualAmount = actual, missedAmount = missed, status = if (actual == 0L) STATUS_MISSED else STATUS_PARTIAL, updatedAt = now))
            }
            weekStart += java.util.concurrent.TimeUnit.DAYS.toMillis(7)
        }
    }

    suspend fun recordNoIncomeWeek(id: Long, weekStart: Long, actualContribution: Long): Result<Unit> {
        val bank = dao.getById(id) ?: return Result.failure(IllegalArgumentException("Piggy bank not found."))
        val expected = bank.weeklyIncomePrediction * bank.selectedAllocationPercent / 100
        if (actualContribution < 0) return Result.failure(IllegalArgumentException("Actual contribution cannot be negative."))
        val missed = (expected - actualContribution).coerceAtLeast(0)
        val now = System.currentTimeMillis()
        val existing = missedDao.getForWeek(id, weekStart)
        val next = (existing ?: PiggyBankMissedContributionEntity(
            piggyBankId = id,
            expectedDate = weekStart,
            expectedAmount = expected,
            actualAmount = actualContribution,
            missedAmount = missed,
            weeklyIncomePredictionAtTheTime = bank.weeklyIncomePrediction,
            selectedAllocationPercentAtTheTime = bank.selectedAllocationPercent,
            status = if (missed == 0L) STATUS_RESOLVED else if (actualContribution == 0L) STATUS_MISSED else STATUS_PARTIAL,
            createdAt = now,
            updatedAt = now
        )).copy(
            expectedAmount = expected,
            actualAmount = actualContribution,
            missedAmount = missed,
            status = if (missed == 0L) STATUS_RESOLVED else if (actualContribution == 0L) STATUS_MISSED else STATUS_PARTIAL,
            updatedAt = now
        )
        if (existing == null) missedDao.insert(next) else missedDao.update(next)
        return Result.success(Unit)
    }

    suspend fun applyAdjustment(id: Long, type: String): Result<Unit> {
        val bank = dao.getById(id) ?: return Result.failure(IllegalArgumentException("Piggy bank not found."))
        val unresolved = missedDao.getAllForExport().filter { it.piggyBankId == id && it.status in setOf(STATUS_MISSED, STATUS_PARTIAL) }
        if (unresolved.isEmpty()) return Result.failure(IllegalArgumentException("There are no missed contributions to adjust."))
        val now = System.currentTimeMillis()
        val totalMissed = unresolved.sumOf { it.missedAmount }
        when (type) {
            ADJUST_CATCH_UP -> {
                val target = bank.targetDate ?: return Result.failure(IllegalArgumentException("Target date is required."))
                val remainingWeeks = kotlin.math.ceil((target - now).coerceAtLeast(0) / java.util.concurrent.TimeUnit.DAYS.toMillis(7).toDouble()).toInt()
                if (remainingWeeks <= 0) return Result.failure(IllegalArgumentException("There are no remaining weeks to catch up."))
                val catchUp = kotlin.math.ceil(totalMissed / remainingWeeks.toDouble()).toLong()
                val currentRequired = bank.weeklyIncomePrediction * bank.selectedAllocationPercent / 100
                val neededPercent = kotlin.math.ceil((currentRequired + catchUp).toDouble() / bank.weeklyIncomePrediction * 100).toInt()
                if (neededPercent > bank.maxAllocationPercent) return Result.failure(IllegalArgumentException("Catching up within the current deadline is not possible with your current weekly income prediction."))
                dao.update(bank.copy(selectedAllocationPercent = maxOf(bank.selectedAllocationPercent, neededPercent), minAllocationPercent = maxOf(bank.minAllocationPercent, neededPercent), updatedAt = now))
                unresolved.forEach { missedDao.update(it.copy(status = STATUS_RESOLVED, adjustmentType = ADJUST_CATCH_UP, catchUpAmountPerWeek = catchUp, affectedWeeksCount = remainingWeeks, updatedAt = now)) }
            }
            ADJUST_EXTEND_DEADLINE -> {
                val currentWeekly = bank.weeklyIncomePrediction * bank.selectedAllocationPercent / 100
                if (currentWeekly <= 0) return Result.failure(IllegalArgumentException("Selected allocation must be greater than 0."))
                val extraWeeks = kotlin.math.ceil(totalMissed / currentWeekly.toDouble()).toInt()
                val oldDate = bank.targetDate ?: return Result.failure(IllegalArgumentException("Target date is required."))
                val newDate = oldDate + java.util.concurrent.TimeUnit.DAYS.toMillis((extraWeeks * 7).toLong())
                dao.update(bank.copy(targetDate = newDate, updatedAt = now))
                unresolved.forEach { missedDao.update(it.copy(status = STATUS_RESOLVED, adjustmentType = ADJUST_EXTEND_DEADLINE, originalTargetDate = oldDate, adjustedTargetDate = newDate, affectedWeeksCount = extraWeeks, updatedAt = now)) }
            }
            ADJUST_SKIP -> unresolved.forEach { missedDao.update(it.copy(status = STATUS_SKIPPED, adjustmentType = ADJUST_SKIP, updatedAt = now)) }
            else -> return Result.failure(IllegalArgumentException("Select a valid adjustment."))
        }
        return Result.success(Unit)
    }

    companion object {
        const val STATUS_MISSED = "MISSED"
        const val STATUS_PARTIAL = "PARTIAL"
        const val STATUS_RESOLVED = "RESOLVED"
        const val STATUS_SKIPPED = "SKIPPED"
        const val ADJUST_CATCH_UP = "CATCH_UP_GRADUALLY"
        const val ADJUST_EXTEND_DEADLINE = "EXTEND_DEADLINE"
        const val ADJUST_SKIP = "SKIP_CONTRIBUTION"
    }
}
class DebtRepository(private val dao: DebtDao)
class SubscriptionRepository(private val dao: SubscriptionDao)
class MonthlySummaryRepository(private val dao: MonthlySummaryDao)
class AppSettingsRepository(private val dao: AppSettingsDao)
