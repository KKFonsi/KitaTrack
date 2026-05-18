package com.example.kitatrack.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kitatrack.data.local.model.DailyExpenseSummary
import com.example.kitatrack.data.local.model.MonthlyBalanceSummary
import com.example.kitatrack.data.local.model.NamedAmount
import com.example.kitatrack.data.repository.TransactionRepository
import com.example.kitatrack.util.DateRanges
import java.util.Calendar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class ReportsUiState(
    val monthLabel: String = "",
    val income: Long = 0,
    val expenses: Long = 0,
    val net: Long = 0,
    val expenseByCategory: List<NamedAmount> = emptyList(),
    val dailySpending: List<DailyExpenseSummary> = emptyList(),
    val topCategories: List<NamedAmount> = emptyList(),
    val monthlyTrend: List<MonthlyBalanceSummary> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(private val repository: TransactionRepository) : ViewModel() {
    private val selectedMonth = MutableStateFlow(Calendar.getInstance())

    val uiState = selectedMonth.flatMapLatest { calendar ->
        val monthRange = DateRanges.monthRange(calendar)
        val trendStart = (calendar.clone() as Calendar).apply {
            add(Calendar.MONTH, -5)
        }
        val trendRange = DateRanges.monthRange(trendStart).first..monthRange.last
        combine(
            repository.getIncomeBetween(monthRange.first, monthRange.last),
            repository.getExpensesBetween(monthRange.first, monthRange.last),
            repository.getExpenseTotalsByCategoryBetween(monthRange.first, monthRange.last),
            repository.getDailyExpensesBetween(monthRange.first, monthRange.last),
            repository.getMonthlyBalanceSummariesBetween(trendRange.first, trendRange.last)
        ) { income, expenses, categories, daily, trend ->
            ReportsUiState(
                monthLabel = DateRanges.monthLabel(calendar),
                income = income,
                expenses = expenses,
                net = income - expenses,
                expenseByCategory = categories,
                dailySpending = daily,
                topCategories = categories.take(5),
                monthlyTrend = fillMissingMonths(trendStart, trend)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState())

    fun previousMonth() = shiftMonth(-1)
    fun nextMonth() = shiftMonth(1)
    fun currentMonth() { selectedMonth.value = Calendar.getInstance() }

    private fun shiftMonth(delta: Int) {
        selectedMonth.value = (selectedMonth.value.clone() as Calendar).apply { add(Calendar.MONTH, delta) }
    }

    private fun fillMissingMonths(startMonth: Calendar, available: List<MonthlyBalanceSummary>): List<MonthlyBalanceSummary> {
        val byKey = available.associateBy { it.monthKey }
        return (0 until 6).map { offset ->
            val month = (startMonth.clone() as Calendar).apply { add(Calendar.MONTH, offset) }
            val key = String.format("%04d-%02d", month.get(Calendar.YEAR), month.get(Calendar.MONTH) + 1)
            byKey[key] ?: MonthlyBalanceSummary(key, 0, 0)
        }
    }

    class Factory(private val repository: TransactionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ReportsViewModel(repository) as T
    }
}
