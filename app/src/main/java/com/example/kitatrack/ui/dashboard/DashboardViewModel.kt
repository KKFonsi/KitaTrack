package com.example.kitatrack.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kitatrack.data.local.model.BudgetProgress
import com.example.kitatrack.data.local.model.TransactionWithCategory
import com.example.kitatrack.data.repository.BudgetRepository
import com.example.kitatrack.data.repository.PiggyBankRepository
import com.example.kitatrack.data.repository.TransactionRepository
import com.example.kitatrack.util.DateRanges
import com.example.kitatrack.util.Formatters
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

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
    val budgetWarning: String? = null,
    val piggyBankTotal: Long = 0,
    val totalMoneyTracked: Long = 0,
    val activePiggyBanks: Int = 0,
    val piggyBanksNeedingAdjustment: Int = 0,
    val missedPiggyContributionTotal: Long = 0,
    val recentTransactions: List<TransactionWithCategory> = emptyList(),
    val allTransactions: List<TransactionWithCategory> = emptyList()
)

class DashboardViewModel(repository: TransactionRepository, budgetRepository: BudgetRepository, piggyBankRepository: PiggyBankRepository) : ViewModel() {
    private val month = DateRanges.currentMonth()
    private val baseTotals = combine(repository.getTotalIncome(), repository.getTotalExpenses(), repository.getIncomeBetween(month.first, month.last), repository.getExpensesBetween(month.first, month.last)) { a, b, c, d -> listOf(a, b, c, d) }
    private val insights = combine(repository.getHighestExpenseBetween(month.first, month.last), repository.getExpenseTotalsByCategoryBetween(month.first, month.last), repository.getTransactionCountBetween(month.first, month.last)) { highest, categories, count -> Triple(highest, categories, count) }
    private val totals = combine(baseTotals, insights) { base, insight -> DashboardTotals(base[0], base[1], base[2], base[3], insight.first, insight.second, insight.third) }

    private val piggyState = combine(piggyBankRepository.getAllPiggyBanks(), piggyBankRepository.getUnresolvedMissedContributions()) { piggyBanks, missed ->
        piggyBanks to missed
    }

    val uiState = combine(totals, repository.getRecentTransactions(), repository.getAllTransactions(), budgetRepository.getBudgetProgress(), piggyState) { amounts, recent, all, budgets, piggy ->
        val (piggyBanks, missed) = piggy
        val active = budgets.filter { it.isActive }
        val activePiggies = piggyBanks.filter { it.isActive && !it.isArchived }
        val piggyTotal = activePiggies.sumOf { it.currentAmount }
        val warning = active.firstOrNull { it.isOverLimit || it.isNearLimit }?.let {
            if (it.isOverLimit) "${it.name} is over budget by ${Formatters.peso(-it.remainingAmount)}."
            else "You have used ${it.usagePercent}% of ${it.name}."
        }
        DashboardUiState(
            mainBalance = amounts.totalIncome - amounts.totalExpenses - piggyTotal,
            monthlyIncome = amounts.monthIncome,
            monthlyExpenses = amounts.monthExpenses,
            monthlyNet = amounts.monthIncome - amounts.monthExpenses,
            highestExpense = amounts.highestExpense,
            topCategoryName = amounts.categories.firstOrNull()?.name,
            transactionCount = amounts.transactionCount,
            weeklyBudget = active.firstOrNull { it.budgetType == BudgetRepository.TYPE_WEEKLY_OVERALL },
            monthlyBudget = active.firstOrNull { it.budgetType == BudgetRepository.TYPE_MONTHLY_OVERALL },
            budgetWarning = warning,
            piggyBankTotal = piggyTotal,
            totalMoneyTracked = amounts.totalIncome - amounts.totalExpenses,
            activePiggyBanks = activePiggies.size,
            piggyBanksNeedingAdjustment = missed.map { it.piggyBankId }.distinct().size,
            missedPiggyContributionTotal = missed.sumOf { it.missedAmount },
            recentTransactions = recent,
            allTransactions = all
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    class Factory(private val repository: TransactionRepository, private val budgetRepository: BudgetRepository, private val piggyBankRepository: PiggyBankRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DashboardViewModel(repository, budgetRepository, piggyBankRepository) as T
    }
}

private data class DashboardTotals(
    val totalIncome: Long, val totalExpenses: Long, val monthIncome: Long, val monthExpenses: Long,
    val highestExpense: TransactionWithCategory?, val categories: List<com.example.kitatrack.data.local.model.NamedAmount>,
    val transactionCount: Int
)
