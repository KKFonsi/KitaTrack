package com.example.kitatrack.data.repository

import com.example.kitatrack.data.local.dao.DebtDao
import com.example.kitatrack.data.local.dao.MonthlyAiSummaryDao
import com.example.kitatrack.data.local.dao.PiggyBankDao
import com.example.kitatrack.data.local.dao.PiggyBankMissedContributionDao
import com.example.kitatrack.data.local.dao.SubscriptionDao
import com.example.kitatrack.data.local.dao.TransactionDao
import com.example.kitatrack.data.local.entity.MonthlyAiSummaryEntity
import com.example.kitatrack.data.local.model.NamedAmount
import com.example.kitatrack.data.local.model.TransactionWithCategory
import com.example.kitatrack.util.DateRanges
import com.example.kitatrack.util.Formatters
import java.security.MessageDigest
import java.util.Calendar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

data class MonthlyAiSummaryInput(
    val year: Int,
    val month: Int,
    val totalIncome: Long,
    val totalExpenses: Long,
    val netAmount: Long,
    val mainBalance: Long,
    val debtReserve: Long,
    val piggyBankTotal: Long,
    val subscriptionReserve: Long,
    val totalMoneyTracked: Long,
    val topExpenseCategories: List<NamedAmount>,
    val topIncomeSources: List<NamedAmount>,
    val highestExpense: TransactionWithCategory?,
    val incomeTransactionCount: Int,
    val expenseTransactionCount: Int,
    val missedContributionCount: Int,
    val overdueDebtCount: Int,
    val overdueSubscriptionCount: Int
)

interface AiSummaryService {
    suspend fun generateMonthlySummary(input: MonthlyAiSummaryInput): Result<String>
}

class MockAiSummaryService : AiSummaryService {
    override suspend fun generateMonthlySummary(input: MonthlyAiSummaryInput): Result<String> = runCatching {
        val monthLabel = Calendar.getInstance().apply {
            set(Calendar.YEAR, input.year)
            set(Calendar.MONTH, input.month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }.let { DateRanges.monthLabel(it) }
        val topCategory = input.topExpenseCategories.firstOrNull()?.name ?: "your regular categories"
        val reserveTotal = input.debtReserve + input.piggyBankTotal + input.subscriptionReserve
        buildString {
            append("$monthLabel showed ${Formatters.peso(input.totalIncome)} in income and ${Formatters.peso(input.totalExpenses)} in expenses, leaving a net movement of ${Formatters.peso(input.netAmount)} before looking at reserved money. ")
            append("Your safe-to-spend Main Balance was ${Formatters.peso(input.mainBalance)}, while ${Formatters.peso(reserveTotal)} stayed separated in debt, piggy bank, and subscription reserves. ")
            if (input.topExpenseCategories.isNotEmpty()) {
                append("The largest spending pressure came from $topCategory, so that is the clearest place to watch next month. ")
            }
            if (input.missedContributionCount > 0) append("You also had ${input.missedContributionCount} piggy bank planning gap${if (input.missedContributionCount == 1) "" else "s"} to review. ")
            if (input.overdueDebtCount > 0 || input.overdueSubscriptionCount > 0) append("There were overdue commitments, so protecting debt and bill reserves should stay the priority. ")
            append("Total Money Tracked was ${Formatters.peso(input.totalMoneyTracked)}, but only Main Balance should be treated as spendable.")
        }
    }
}

class MonthlyAiSummaryRepository(
    private val aiSummaryDao: MonthlyAiSummaryDao,
    private val transactionDao: TransactionDao,
    private val debtDao: DebtDao,
    private val piggyBankDao: PiggyBankDao,
    private val missedContributionDao: PiggyBankMissedContributionDao,
    private val subscriptionDao: SubscriptionDao,
    private val appSettingsRepository: AppSettingsRepository,
    private val aiService: AiSummaryService = MockAiSummaryService()
) {
    fun observeSummary(year: Int, month: Int): Flow<MonthlyAiSummaryEntity?> = aiSummaryDao.observeForMonth(year, month)

    suspend fun getSummary(year: Int, month: Int): MonthlyAiSummaryEntity? = aiSummaryDao.getForMonth(year, month)

    suspend fun generateSummary(year: Int, month: Int): Result<MonthlyAiSummaryEntity> = runCatching {
        require(isCompletedMonth(year, month)) { "AI summary will be available after this month ends." }
        val settings = appSettingsRepository.getOrCreateSettings()
        require(settings.aiSummaryEnabled) { "Enable AI Monthly Analysis in Settings to use this feature." }
        aiSummaryDao.getForMonth(year, month)?.takeIf { it.status == MonthlyAiSummaryEntity.STATUS_GENERATED }?.let { return@runCatching it }
        val input = buildInput(year, month)
        require(input.totalIncome > 0 || input.totalExpenses > 0) { "No completed month data exists for this report yet." }
        val text = aiService.generateMonthlySummary(input).getOrThrow()
        val now = System.currentTimeMillis()
        val entity = MonthlyAiSummaryEntity(
            id = aiSummaryDao.getForMonth(year, month)?.id ?: 0,
            year = year,
            month = month,
            summaryText = text,
            generatedAt = now,
            inputDataHash = input.stableHash(),
            modelName = "Local summary generator",
            promptVersion = MonthlyAiSummaryEntity.PROMPT_VERSION,
            status = MonthlyAiSummaryEntity.STATUS_GENERATED,
            createdAt = aiSummaryDao.getForMonth(year, month)?.createdAt ?: now,
            updatedAt = now
        )
        val id = aiSummaryDao.insert(entity)
        entity.copy(id = if (entity.id == 0L) id else entity.id)
    }

    suspend fun buildInput(year: Int, month: Int): MonthlyAiSummaryInput {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val range = DateRanges.monthRange(calendar)
        val income = transactionDao.getIncomeBetween(range.first, range.last).first()
        val expenses = transactionDao.getExpensesBetween(range.first, range.last).first()
        val debtReserve = debtDao.getAllForExport().filter { it.isActive && !it.isArchived && it.debtType == DebtRepository.TYPE_I_OWE }.sumOf { it.reservedAmount }
        val piggyTotal = piggyBankDao.getAllForExport().filter { it.isActive && !it.isArchived }.sumOf { it.currentAmount }
        val subscriptionReserve = subscriptionDao.getAllForExport().filter { it.isActive && !it.isArchived && it.reserveEnabled }.sumOf { it.reservedAmount }
        val mainBalance = income - expenses - debtReserve - piggyTotal - subscriptionReserve
        val topExpenses = transactionDao.getExpenseTotalsByCategoryBetween(range.first, range.last).first().take(5)
        val topIncome = transactionDao.getIncomeTotalsBySourceBetween(range.first, range.last).first().take(5)
        val highestExpense = transactionDao.getHighestExpenseBetween(range.first, range.last).first()
        val incomeCount = transactionDao.getIncomeCountBetween(range.first, range.last).first()
        val expenseCount = transactionDao.getExpenseCountBetween(range.first, range.last).first()
        val now = System.currentTimeMillis()
        val overdueDebts = debtDao.getAllForExport().count { it.isActive && !it.isArchived && it.remainingAmount > 0 && (it.nextDueDate ?: it.dueDate ?: Long.MAX_VALUE) < now }
        val overdueSubs = subscriptionDao.getAllForExport().count { it.isActive && !it.isArchived && (it.nextBillingDate ?: Long.MAX_VALUE) < now }
        val missed = missedContributionDao.getAllForExport().count { it.status == "MISSED" || it.status == "PARTIAL" }
        return MonthlyAiSummaryInput(
            year = year,
            month = month,
            totalIncome = income,
            totalExpenses = expenses,
            netAmount = income - expenses,
            mainBalance = mainBalance,
            debtReserve = debtReserve,
            piggyBankTotal = piggyTotal,
            subscriptionReserve = subscriptionReserve,
            totalMoneyTracked = mainBalance + debtReserve + piggyTotal + subscriptionReserve,
            topExpenseCategories = topExpenses,
            topIncomeSources = topIncome,
            highestExpense = highestExpense,
            incomeTransactionCount = incomeCount,
            expenseTransactionCount = expenseCount,
            missedContributionCount = missed,
            overdueDebtCount = overdueDebts,
            overdueSubscriptionCount = overdueSubs
        )
    }

    fun monthAvailability(year: Int, month: Int): AiMonthAvailability {
        val current = Calendar.getInstance()
        val selected = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val currentKey = current.get(Calendar.YEAR) * 100 + current.get(Calendar.MONTH) + 1
        val selectedKey = selected.get(Calendar.YEAR) * 100 + selected.get(Calendar.MONTH) + 1
        return when {
            selectedKey == currentKey -> AiMonthAvailability.CURRENT_MONTH
            selectedKey > currentKey -> AiMonthAvailability.FUTURE_MONTH
            else -> AiMonthAvailability.COMPLETED_MONTH
        }
    }

    private fun isCompletedMonth(year: Int, month: Int) = monthAvailability(year, month) == AiMonthAvailability.COMPLETED_MONTH

    private fun MonthlyAiSummaryInput.stableHash(): String {
        val raw = listOf(
            year, month, totalIncome, totalExpenses, mainBalance, debtReserve,
            piggyBankTotal, subscriptionReserve, totalMoneyTracked,
            topExpenseCategories.joinToString { "${it.name}:${it.totalAmount}" },
            topIncomeSources.joinToString { "${it.name}:${it.totalAmount}" },
            incomeTransactionCount, expenseTransactionCount, missedContributionCount,
            overdueDebtCount, overdueSubscriptionCount
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}

enum class AiMonthAvailability { COMPLETED_MONTH, CURRENT_MONTH, FUTURE_MONTH }
