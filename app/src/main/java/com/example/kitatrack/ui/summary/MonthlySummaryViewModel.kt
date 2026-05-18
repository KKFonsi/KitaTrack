package com.example.kitatrack.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kitatrack.data.local.model.NamedAmount
import com.example.kitatrack.data.local.model.TransactionWithCategory
import com.example.kitatrack.data.repository.TransactionRepository
import com.example.kitatrack.data.repository.PiggyBankRepository
import com.example.kitatrack.util.DateRanges
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class MonthlySummaryUiState(
    val monthLabel: String = "",
    val totalIncome: Long = 0,
    val totalExpenses: Long = 0,
    val net: Long = 0,
    val piggyBankAllocations: Long = 0,
    val mainBalanceImpact: Long = 0,
    val highestExpense: TransactionWithCategory? = null,
    val topCategory: NamedAmount? = null,
    val transactionCount: Int = 0,
    val incomeCount: Int = 0,
    val expenseCount: Int = 0,
    val averageDailyExpense: Long = 0,
    val largestExpenses: List<TransactionWithCategory> = emptyList(),
    val incomeSources: List<NamedAmount> = emptyList(),
    val expenseCategories: List<NamedAmount> = emptyList(),
    val transactions: List<TransactionWithCategory> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class MonthlySummaryViewModel(private val repository: TransactionRepository, private val piggyBankRepository: PiggyBankRepository) : ViewModel() {
    private val selectedMonth = MutableStateFlow(Calendar.getInstance())

    val uiState = selectedMonth.flatMapLatest { calendar ->
        val range = DateRanges.monthRange(calendar)
        val primary = combine(
            repository.getIncomeBetween(range.first, range.last),
            repository.getExpensesBetween(range.first, range.last),
            repository.getHighestExpenseBetween(range.first, range.last),
            repository.getTransactionCountBetween(range.first, range.last),
            repository.getIncomeCountBetween(range.first, range.last)
        ) { income, expenses, highest, totalCount, incomeCount ->
            PrimarySummary(income, expenses, highest, totalCount, incomeCount)
        }
        val secondary = combine(
            repository.getExpenseCountBetween(range.first, range.last),
            repository.getExpenseTotalsByCategoryBetween(range.first, range.last),
            repository.getIncomeTotalsBySourceBetween(range.first, range.last),
            repository.getTransactionsBetween(range.first, range.last)
        ) { expenseCount, expenseGroups, incomeGroups, txs ->
            SecondarySummary(expenseCount, expenseGroups, incomeGroups, txs)
        }
        combine(primary, secondary, piggyBankRepository.getMonthlyAutoAllocation(range.first, range.last)) { main, extra, piggyAllocations ->
            val txs = extra.transactions
            val expenseDays = txs.filter { it.transaction.type == "EXPENSE" }
                .map { dayKey(it.transaction.occurredAt) }
                .distinct()
                .size
            MonthlySummaryUiState(
                monthLabel = DateRanges.monthLabel(calendar),
                totalIncome = main.income,
                totalExpenses = main.expenses,
                net = main.income - main.expenses,
                piggyBankAllocations = piggyAllocations,
                mainBalanceImpact = main.income - main.expenses - piggyAllocations,
                highestExpense = main.highest,
                topCategory = extra.expenseGroups.firstOrNull(),
                transactionCount = main.totalCount,
                incomeCount = main.incomeCount,
                expenseCount = extra.expenseCount,
                averageDailyExpense = if (expenseDays == 0) 0 else main.expenses / expenseDays,
                largestExpenses = txs.filter { it.transaction.type == "EXPENSE" }
                    .sortedByDescending { it.transaction.amount }
                    .take(5),
                incomeSources = extra.incomeGroups,
                expenseCategories = extra.expenseGroups,
                transactions = txs
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthlySummaryUiState())

    fun previousMonth() = shiftMonth(-1)
    fun nextMonth() = shiftMonth(1)
    fun currentMonth() { selectedMonth.value = Calendar.getInstance() }

    private fun shiftMonth(delta: Int) {
        selectedMonth.value = (selectedMonth.value.clone() as Calendar).apply { add(Calendar.MONTH, delta) }
    }

    private fun dayKey(millis: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.DAY_OF_YEAR)}"
    }

    class Factory(private val repository: TransactionRepository, private val piggyBankRepository: PiggyBankRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MonthlySummaryViewModel(repository, piggyBankRepository) as T
    }
}

private data class PrimarySummary(
    val income: Long,
    val expenses: Long,
    val highest: TransactionWithCategory?,
    val totalCount: Int,
    val incomeCount: Int
)

private data class SecondarySummary(
    val expenseCount: Int,
    val expenseGroups: List<NamedAmount>,
    val incomeGroups: List<NamedAmount>,
    val transactions: List<TransactionWithCategory>
)
