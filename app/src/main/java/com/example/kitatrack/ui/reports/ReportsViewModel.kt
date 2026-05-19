package com.example.kitatrack.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kitatrack.data.local.entity.MonthlyAiSummaryEntity
import com.example.kitatrack.data.local.model.DailyExpenseSummary
import com.example.kitatrack.data.local.model.MonthlyBalanceSummary
import com.example.kitatrack.data.local.model.NamedAmount
import com.example.kitatrack.data.repository.AiMonthAvailability
import com.example.kitatrack.data.repository.AppSettingsRepository
import com.example.kitatrack.data.repository.MonthlyAiSummaryRepository
import com.example.kitatrack.data.repository.TransactionRepository
import com.example.kitatrack.util.DateRanges
import java.util.Calendar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReportsUiState(
    val monthLabel: String = "",
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1,
    val income: Long = 0,
    val expenses: Long = 0,
    val net: Long = 0,
    val expenseByCategory: List<NamedAmount> = emptyList(),
    val dailySpending: List<DailyExpenseSummary> = emptyList(),
    val topCategories: List<NamedAmount> = emptyList(),
    val monthlyTrend: List<MonthlyBalanceSummary> = emptyList(),
    val aiSummary: MonthlyAiSummaryEntity? = null,
    val aiEnabled: Boolean = false,
    val aiAvailability: AiMonthAvailability = AiMonthAvailability.CURRENT_MONTH,
    val isGeneratingAiSummary: Boolean = false,
    val aiError: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(
    private val repository: TransactionRepository,
    private val aiSummaryRepository: MonthlyAiSummaryRepository,
    private val appSettingsRepository: AppSettingsRepository
) : ViewModel() {
    private val selectedMonth = MutableStateFlow(Calendar.getInstance())
    private val isGeneratingAiSummary = MutableStateFlow(false)
    private val aiError = MutableStateFlow<String?>(null)

    val uiState = selectedMonth.flatMapLatest { calendar ->
        val monthRange = DateRanges.monthRange(calendar)
        val trendStart = (calendar.clone() as Calendar).apply {
            add(Calendar.MONTH, -5)
        }
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val trendRange = DateRanges.monthRange(trendStart).first..monthRange.last
        combine(
            repository.getIncomeBetween(monthRange.first, monthRange.last),
            repository.getExpensesBetween(monthRange.first, monthRange.last),
            repository.getExpenseTotalsByCategoryBetween(monthRange.first, monthRange.last),
            repository.getDailyExpensesBetween(monthRange.first, monthRange.last),
            repository.getMonthlyBalanceSummariesBetween(trendRange.first, trendRange.last),
            aiSummaryRepository.observeSummary(year, month),
            appSettingsRepository.observeSettings(),
            isGeneratingAiSummary,
            aiError
        ) { values ->
            val income = values[0] as Long
            val expenses = values[1] as Long
            @Suppress("UNCHECKED_CAST") val categories = values[2] as List<NamedAmount>
            @Suppress("UNCHECKED_CAST") val daily = values[3] as List<DailyExpenseSummary>
            @Suppress("UNCHECKED_CAST") val trend = values[4] as List<MonthlyBalanceSummary>
            val summary = values[5] as MonthlyAiSummaryEntity?
            val settings = values[6] as? com.example.kitatrack.data.local.entity.AppSettingsEntity
            val generating = values[7] as Boolean
            val error = values[8] as String?
            ReportsUiState(
                monthLabel = DateRanges.monthLabel(calendar),
                selectedYear = year,
                selectedMonth = month,
                income = income,
                expenses = expenses,
                net = income - expenses,
                expenseByCategory = categories,
                dailySpending = daily,
                topCategories = categories.take(5),
                monthlyTrend = fillMissingMonths(trendStart, trend),
                aiSummary = summary,
                aiEnabled = settings?.aiSummaryEnabled == true,
                aiAvailability = aiSummaryRepository.monthAvailability(year, month),
                isGeneratingAiSummary = generating,
                aiError = error
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState())

    fun previousMonth() = shiftMonth(-1)
    fun nextMonth() = shiftMonth(1)
    fun currentMonth() { selectedMonth.value = Calendar.getInstance() }

    fun generateAiSummary() {
        val calendar = selectedMonth.value
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        viewModelScope.launch {
            aiError.value = null
            isGeneratingAiSummary.value = true
            val result = aiSummaryRepository.generateSummary(year, month)
            isGeneratingAiSummary.value = false
            result.onFailure { aiError.value = it.message ?: "AI summary could not be generated. Your reports are still available." }
        }
    }

    fun clearAiError() { aiError.value = null }

    private fun shiftMonth(delta: Int) {
        aiError.value = null
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

    class Factory(
        private val repository: TransactionRepository,
        private val aiSummaryRepository: MonthlyAiSummaryRepository,
        private val appSettingsRepository: AppSettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ReportsViewModel(repository, aiSummaryRepository, appSettingsRepository) as T
    }
}
