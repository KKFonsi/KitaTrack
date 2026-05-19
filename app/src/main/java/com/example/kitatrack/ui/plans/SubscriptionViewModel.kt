package com.example.kitatrack.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kitatrack.data.repository.SubscriptionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SubscriptionViewModel(private val repository: SubscriptionRepository) : ViewModel() {
    val subscriptions = repository.getSubscriptionProgress().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(
        existingId: Long?,
        name: String,
        amount: Long,
        categoryId: Long?,
        billingCycle: String,
        customIntervalDays: Int?,
        nextBillingDate: Long?,
        importance: String,
        reserveEnabled: Boolean,
        reminderEnabled: Boolean,
        notes: String?,
        isActive: Boolean,
        onResult: (Result<Unit>) -> Unit
    ) = viewModelScope.launch {
        onResult(repository.saveSubscription(existingId, name, amount, categoryId, billingCycle, customIntervalDays, nextBillingDate, importance, reserveEnabled, reminderEnabled, notes, isActive))
    }

    fun payment(id: Long, amount: Long, fromReserve: Boolean, onResult: (Result<Unit>) -> Unit) = viewModelScope.launch {
        onResult(repository.recordPayment(id, amount, fromReserve))
    }

    fun reserve(id: Long, amount: Long, add: Boolean, onResult: (Result<Unit>) -> Unit) = viewModelScope.launch {
        onResult(repository.adjustReserve(id, amount, add))
    }

    fun archive(id: Long) = viewModelScope.launch { repository.archive(id) }

    class Factory(private val repository: SubscriptionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SubscriptionViewModel(repository) as T
    }
}
