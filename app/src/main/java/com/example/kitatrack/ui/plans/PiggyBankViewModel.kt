package com.example.kitatrack.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kitatrack.data.local.model.PiggyBankProgress
import com.example.kitatrack.data.repository.PiggyBankRepository
import com.example.kitatrack.data.repository.TransactionRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PiggyBankViewModel(private val repository: PiggyBankRepository, transactionRepository: TransactionRepository) : ViewModel() {
    private val recentIncome = transactionRepository.getIncomeBetween(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30), System.currentTimeMillis())
    val piggyBanks = kotlinx.coroutines.flow.combine(repository.getAllPiggyBanks(), recentIncome, repository.getUnresolvedMissedContributions()) { items, income30Days, unresolved ->
        items.map { bank ->
            val remaining = (bank.targetAmount - bank.currentAmount).coerceAtLeast(0)
            val days = bank.targetDate?.let { TimeUnit.MILLISECONDS.toDays(it - System.currentTimeMillis()) }
            val daily = if (days != null && days > 0) remaining / days else null
            val weekly = daily?.times(7)
            val estimatedMonthlyAllocation = income30Days * bank.selectedAllocationPercent / 100
            val monthsRemaining = if (days != null && days > 0) days / 30.0 else null
            val onTrack = if (bank.targetDate == null || estimatedMonthlyAllocation <= 0) null else {
                estimatedMonthlyAllocation * (monthsRemaining ?: 0.0) >= remaining
            }
            val missedForBank = unresolved.filter { it.piggyBankId == bank.id }
            val status = when {
                bank.currentAmount >= bank.targetAmount -> "Completed"
                missedForBank.isNotEmpty() -> "Needs adjustment"
                bank.targetDate != null && days != null && days < 0 -> "Past target date"
                onTrack == true -> "On track"
                onTrack == false -> "Behind schedule"
                bank.targetDate == null -> "In progress"
                else -> "Not enough income history"
            }
            PiggyBankProgress(
                id = bank.id,
                name = bank.name,
                targetAmount = bank.targetAmount,
                currentAmount = bank.currentAmount,
                remainingAmount = remaining,
                progressPercent = if (bank.targetAmount == 0L) 0 else ((bank.currentAmount * 100) / bank.targetAmount).toInt(),
                weeklyIncomePrediction = bank.weeklyIncomePrediction,
                selectedAllocationPercent = bank.selectedAllocationPercent,
                minAllocationPercent = bank.minAllocationPercent,
                maxAllocationPercent = bank.maxAllocationPercent,
                isGoalPossible = bank.isGoalPossible,
                targetDate = bank.targetDate,
                daysRemaining = days,
                requiredDailySaving = daily,
                requiredWeeklySaving = weekly,
                estimatedMonthlyAllocation = estimatedMonthlyAllocation,
                estimatedWeeklySavingAmount = bank.weeklyIncomePrediction * bank.selectedAllocationPercent / 100,
                isOnTrack = onTrack,
                statusLabel = status,
                unresolvedMissedCount = missedForBank.size,
                unresolvedMissedAmount = missedForBank.sumOf { it.missedAmount },
                isActive = bank.isActive,
                notes = bank.notes
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(id: Long?, name: String, target: Long, current: Long, weeklyIncomePrediction: Long, selectedPercent: Int, targetDate: Long?, notes: String?, allowOverSaving: Boolean, active: Boolean, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch { onResult(repository.save(id, name, target, current, weeklyIncomePrediction, selectedPercent, targetDate, notes, allowOverSaving, active)) }
    }
    fun calculatePlan(target: Long, current: Long, weeklyIncomePrediction: Long, targetDate: Long?, editingId: Long?, currentItems: List<PiggyBankProgress>): com.example.kitatrack.data.local.model.PiggyBankAllocationPlan? {
        val date = targetDate ?: return null
        val other = currentItems.filter { it.id != editingId && it.isActive }.sumOf { it.selectedAllocationPercent }
        return repository.calculateAllocationPlan(target, current, weeklyIncomePrediction, date, other)
    }
    fun archive(id: Long) = viewModelScope.launch { repository.archive(id) }
    fun completeGoal(id: Long, onResult: (Result<Long>) -> Unit) = viewModelScope.launch { onResult(repository.completeGoal(id)) }
    fun adjust(id: Long, amount: Long, add: Boolean, onResult: (Result<Unit>) -> Unit) = viewModelScope.launch { onResult(repository.manualAdjust(id, amount, add)) }
    fun missedFor(id: Long) = repository.getMissedContributionsForPiggyBank(id)
    fun refreshMissed(id: Long) = viewModelScope.launch { repository.refreshMissedContributions(id) }
    fun recordNoIncomeWeek(id: Long, weekStart: Long, actualContribution: Long, onResult: (Result<Unit>) -> Unit) =
        viewModelScope.launch { onResult(repository.recordNoIncomeWeek(id, weekStart, actualContribution)) }
    fun applyAdjustment(id: Long, type: String, onResult: (Result<Unit>) -> Unit) =
        viewModelScope.launch { onResult(repository.applyAdjustment(id, type)) }

    class Factory(private val repository: PiggyBankRepository, private val transactionRepository: TransactionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PiggyBankViewModel(repository, transactionRepository) as T
    }
}
