package com.example.kitatrack.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kitatrack.data.local.model.BudgetProgress
import com.example.kitatrack.data.local.model.TransactionWithCategory
import com.example.kitatrack.data.repository.BudgetRepository
import com.example.kitatrack.data.repository.PiggyBankRepository
import com.example.kitatrack.data.repository.TransactionRepository
import com.example.kitatrack.data.repository.DebtRepository
import com.example.kitatrack.data.repository.SubscriptionRepository
import com.example.kitatrack.util.DateRanges
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.TimeUnit

data class DashboardUiState(
    val mainBalance: Long = 0,
    val monthlyIncome: Long = 0,
    val monthlyExpenses: Long = 0,
    val monthlyNet: Long = 0,
    val highestExpense: TransactionWithCategory? = null,
    val topCategoryName: String? = null,
    val transactionCount: Int = 0,
    val weeklyBudget: BudgetProgress? = null,
    val monthlyBudget: BudgetProgress? = null,
    val budgetPreviews: List<BudgetProgress> = emptyList(),
    val dashboardInsight: String = "No urgent money alerts today.",
    val piggyBankTotal: Long = 0,
    val debtReserve: Long = 0,
    val totalDebtIOwe: Long = 0,
    val totalOwedToMe: Long = 0,
    val overdueDebtCount: Int = 0,
    val upcomingDebtCount: Int = 0,
    val upcomingDebtReserveShortfall: Long = 0,
    val nearestDebtName: String? = null,
    val subscriptionReserve: Long = 0,
    val upcomingSubscriptionCount: Int = 0,
    val overdueSubscriptionCount: Int = 0,
    val upcomingSubscriptionReserveShortfall: Long = 0,
    val reserveDisabledUpcomingSubscriptionCount: Int = 0,
    val activeSubscriptionCount: Int = 0,
    val nextSubscriptionName: String? = null,
    val monthlySubscriptionEstimate: Long = 0,
    val totalMoneyTracked: Long = 0,
    val activePiggyBanks: Int = 0,
    val piggyBanksNeedingAdjustment: Int = 0,
    val missedPiggyContributionTotal: Long = 0,
    val piggyGoalName: String? = null,
    val piggyGoalCurrentAmount: Long = 0,
    val piggyGoalTargetAmount: Long = 0,
    val piggyGoalPercent: Int = 0
)

class DashboardViewModel(repository: TransactionRepository, budgetRepository: BudgetRepository, piggyBankRepository: PiggyBankRepository, debtRepository: DebtRepository, subscriptionRepository: SubscriptionRepository) : ViewModel() {
    private val month = DateRanges.currentMonth()
    private val baseTotals = combine(repository.getTotalIncome(), repository.getTotalExpenses(), repository.getIncomeBetween(month.first, month.last), repository.getExpensesBetween(month.first, month.last)) { a, b, c, d -> listOf(a, b, c, d) }
    private val insights = combine(repository.getHighestExpenseBetween(month.first, month.last), repository.getExpenseTotalsByCategoryBetween(month.first, month.last), repository.getTransactionCountBetween(month.first, month.last)) { highest, categories, count -> Triple(highest, categories, count) }
    private val totals = combine(baseTotals, insights) { base, insight -> DashboardTotals(base[0], base[1], base[2], base[3], insight.first, insight.second, insight.third) }

    private val reserveState = combine(piggyBankRepository.getAllPiggyBanks(), piggyBankRepository.getUnresolvedMissedContributions(), debtRepository.getAllDebts(), subscriptionRepository.getAllSubscriptions()) { piggyBanks, missed, debts, subscriptions ->
        ReserveState(piggyBanks, missed, debts, subscriptions)
    }

    val uiState = combine(totals, budgetRepository.getBudgetProgress(), reserveState) { amounts, budgets, reserves ->
        val piggyBanks = reserves.piggyBanks
        val missed = reserves.missed
        val debts = reserves.debts
        val subscriptions = reserves.subscriptions
        val active = budgets.filter { it.isActive }
        val activePiggies = piggyBanks.filter { it.isActive && !it.isArchived }
        val previewPiggy = activePiggies
            .filter { it.targetAmount > 0L }
            .minByOrNull { ((it.currentAmount * 100.0) / it.targetAmount).toInt().coerceIn(0, 100) }
            ?: activePiggies.firstOrNull()
        val activeDebts = debts.filter { it.isActive && !it.isArchived }
        val activeSubscriptions = subscriptions.filter { it.isActive && !it.isArchived && it.status !in setOf(SubscriptionRepository.STATUS_CANCELLED, SubscriptionRepository.STATUS_PAUSED, SubscriptionRepository.STATUS_ARCHIVED) }
        val piggyTotal = activePiggies.sumOf { it.currentAmount }
        val debtReserve = activeDebts.filter { it.debtType == DebtRepository.TYPE_I_OWE }.sumOf { it.reservedAmount }
        val subscriptionReserve = activeSubscriptions.filter { it.reserveEnabled }.sumOf { it.reservedAmount }
        val now = System.currentTimeMillis()
        val dueSoonWindow = TimeUnit.DAYS.toMillis(7)
        val overdueDebts = activeDebts.filter { it.remainingAmount > 0 && it.debtType == DebtRepository.TYPE_I_OWE && (it.nextDueDate ?: it.dueDate ?: Long.MAX_VALUE) < now }
        val upcomingDebts = activeDebts.filter {
            val due = it.nextDueDate ?: it.dueDate ?: Long.MAX_VALUE
            it.remainingAmount > 0 && it.debtType == DebtRepository.TYPE_I_OWE && due >= now && due - now <= dueSoonWindow
        }
        val overdueSubscriptions = activeSubscriptions.filter { (it.nextBillingDate ?: Long.MAX_VALUE) < now }
        val upcomingSubscriptions = activeSubscriptions.filter { (it.nextBillingDate ?: Long.MAX_VALUE) >= now && (it.nextBillingDate ?: Long.MAX_VALUE) - now <= dueSoonWindow }
        val prioritizedBudgets = active.sortedWith(budgetPreviewComparator())
        val totalReserved = debtReserve + piggyTotal + subscriptionReserve
        val mainBalance = amounts.totalIncome - amounts.totalExpenses - totalReserved
        val upcomingDebtShortfall = upcomingDebts.sumOf {
            val dueAmount = (it.installmentAmount ?: it.remainingAmount).coerceAtMost(it.remainingAmount)
            (dueAmount - it.reservedAmount).coerceAtLeast(0)
        }
        val upcomingSubscriptionShortfall = upcomingSubscriptions
            .filter { it.reserveEnabled }
            .sumOf { (it.amount - it.reservedAmount).coerceAtLeast(0) }
        val reserveDisabledUpcomingSubscriptions = upcomingSubscriptions.count { !it.reserveEnabled }
        val piggyBanksNeedingAdjustment = missed.map { it.piggyBankId }.distinct().size
        val missedPiggyContributionTotal = missed.sumOf { it.missedAmount }
        val dashboardInsight = DashboardInsightGenerator.generate(
            DashboardInsightInput(
                mainBalance = mainBalance,
                monthlyIncome = amounts.monthIncome,
                monthlyExpenses = amounts.monthExpenses,
                monthlyNet = amounts.monthIncome - amounts.monthExpenses,
                totalReserved = totalReserved,
                totalMoneyTracked = amounts.totalIncome - amounts.totalExpenses,
                budgets = prioritizedBudgets,
                debtReserve = debtReserve,
                upcomingDebtCount = upcomingDebts.size,
                upcomingDebtReserveShortfall = upcomingDebtShortfall,
                subscriptionReserve = subscriptionReserve,
                upcomingSubscriptionCount = upcomingSubscriptions.size,
                upcomingSubscriptionReserveShortfall = upcomingSubscriptionShortfall,
                reserveDisabledUpcomingSubscriptionCount = reserveDisabledUpcomingSubscriptions,
                activeSubscriptionCount = activeSubscriptions.size,
                activePiggyBanks = activePiggies.size,
                piggyBanksNeedingAdjustment = piggyBanksNeedingAdjustment,
                missedPiggyContributionTotal = missedPiggyContributionTotal
            )
        )
        DashboardUiState(
            mainBalance = mainBalance,
            monthlyIncome = amounts.monthIncome,
            monthlyExpenses = amounts.monthExpenses,
            monthlyNet = amounts.monthIncome - amounts.monthExpenses,
            highestExpense = amounts.highestExpense,
            topCategoryName = amounts.categories.firstOrNull()?.name,
            transactionCount = amounts.transactionCount,
            weeklyBudget = active.firstOrNull { it.budgetType == BudgetRepository.TYPE_WEEKLY_OVERALL },
            monthlyBudget = active.firstOrNull { it.budgetType == BudgetRepository.TYPE_MONTHLY_OVERALL },
            budgetPreviews = prioritizedBudgets.take(3),
            dashboardInsight = dashboardInsight,
            piggyBankTotal = piggyTotal,
            debtReserve = debtReserve,
            totalDebtIOwe = activeDebts.filter { it.debtType == DebtRepository.TYPE_I_OWE }.sumOf { it.remainingAmount },
            totalOwedToMe = activeDebts.filter { it.debtType == DebtRepository.TYPE_OWED_TO_ME }.sumOf { it.remainingAmount },
            overdueDebtCount = overdueDebts.size,
            upcomingDebtCount = upcomingDebts.size,
            upcomingDebtReserveShortfall = upcomingDebtShortfall,
            nearestDebtName = activeDebts.filter { it.debtType == DebtRepository.TYPE_I_OWE && it.remainingAmount > 0 }.minByOrNull { it.nextDueDate ?: it.dueDate ?: Long.MAX_VALUE }?.name,
            subscriptionReserve = subscriptionReserve,
            upcomingSubscriptionCount = upcomingSubscriptions.size,
            overdueSubscriptionCount = overdueSubscriptions.size,
            upcomingSubscriptionReserveShortfall = upcomingSubscriptionShortfall,
            reserveDisabledUpcomingSubscriptionCount = reserveDisabledUpcomingSubscriptions,
            activeSubscriptionCount = activeSubscriptions.size,
            nextSubscriptionName = activeSubscriptions.minByOrNull { it.nextBillingDate ?: Long.MAX_VALUE }?.name,
            monthlySubscriptionEstimate = activeSubscriptions.sumOf { if (it.billingCycle == SubscriptionRepository.CYCLE_YEARLY) it.amount / 12 else it.amount },
            totalMoneyTracked = amounts.totalIncome - amounts.totalExpenses,
            activePiggyBanks = activePiggies.size,
            piggyBanksNeedingAdjustment = piggyBanksNeedingAdjustment,
            missedPiggyContributionTotal = missedPiggyContributionTotal,
            piggyGoalName = previewPiggy?.name,
            piggyGoalCurrentAmount = previewPiggy?.currentAmount ?: 0L,
            piggyGoalTargetAmount = previewPiggy?.targetAmount ?: 0L,
            piggyGoalPercent = previewPiggy
                ?.takeIf { it.targetAmount > 0L }
                ?.let { ((it.currentAmount * 100) / it.targetAmount).toInt().coerceIn(0, 100) }
                ?: 0
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    class Factory(private val repository: TransactionRepository, private val budgetRepository: BudgetRepository, private val piggyBankRepository: PiggyBankRepository, private val debtRepository: DebtRepository, private val subscriptionRepository: SubscriptionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DashboardViewModel(repository, budgetRepository, piggyBankRepository, debtRepository, subscriptionRepository) as T
    }
}

private fun budgetPreviewComparator() = compareByDescending<BudgetProgress> {
    it.isOverLimit || it.isNearLimit
}.thenByDescending {
    it.budgetType == BudgetRepository.TYPE_CATEGORY_WEEKLY || it.budgetType == BudgetRepository.TYPE_WEEKLY_OVERALL
}.thenByDescending {
    it.budgetType == BudgetRepository.TYPE_CATEGORY_WEEKLY
}.thenByDescending {
    it.usagePercent
}.thenBy {
    it.name.lowercase()
}

private data class DashboardTotals(
    val totalIncome: Long, val totalExpenses: Long, val monthIncome: Long, val monthExpenses: Long,
    val highestExpense: TransactionWithCategory?, val categories: List<com.example.kitatrack.data.local.model.NamedAmount>,
    val transactionCount: Int
)

private data class ReserveState(
    val piggyBanks: List<com.example.kitatrack.data.local.entity.PiggyBankEntity>,
    val missed: List<com.example.kitatrack.data.local.entity.PiggyBankMissedContributionEntity>,
    val debts: List<com.example.kitatrack.data.local.entity.DebtEntity>,
    val subscriptions: List<com.example.kitatrack.data.local.entity.SubscriptionEntity>
)
