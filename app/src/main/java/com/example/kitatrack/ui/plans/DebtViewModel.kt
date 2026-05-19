package com.example.kitatrack.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kitatrack.data.repository.DebtRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DebtViewModel(private val repository: DebtRepository) : ViewModel() {
    val debts = repository.getDebtProgress().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(
        id: Long?,
        name: String,
        personName: String?,
        type: String,
        total: Long,
        paid: Long,
        installment: Long?,
        frequency: String,
        intervalDays: Int?,
        dueDate: Long?,
        endDate: Long?,
        priority: Int,
        autoReserve: Boolean,
        reminderEnabled: Boolean,
        reminderDays: Int?,
        notes: String?,
        active: Boolean,
        onResult: (Result<Unit>) -> Unit
    ) = viewModelScope.launch {
        onResult(repository.saveDebt(id, name, personName, type, total, paid, installment, frequency, intervalDays, dueDate, endDate, priority, autoReserve, reminderEnabled, reminderDays, notes, active))
    }

    fun payment(id: Long, amount: Long, fromReserve: Boolean, onResult: (Result<Unit>) -> Unit) =
        viewModelScope.launch { onResult(repository.recordPayment(id, amount, fromReserve)) }

    fun reserve(id: Long, amount: Long, add: Boolean, onResult: (Result<Unit>) -> Unit) =
        viewModelScope.launch { onResult(repository.adjustReserve(id, amount, add)) }

    fun archive(id: Long) = viewModelScope.launch { repository.archive(id) }

    class Factory(private val repository: DebtRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DebtViewModel(repository) as T
    }
}
