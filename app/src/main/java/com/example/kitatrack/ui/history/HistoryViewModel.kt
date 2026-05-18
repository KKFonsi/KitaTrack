package com.example.kitatrack.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kitatrack.data.local.entity.TransactionEntity
import com.example.kitatrack.data.local.model.TransactionWithCategory
import com.example.kitatrack.data.repository.TransactionRepository
import com.example.kitatrack.data.repository.PiggyBankRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val repository: TransactionRepository, private val piggyBankRepository: PiggyBankRepository? = null) : ViewModel() {
    val transactions = repository.getAllTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(transaction: TransactionEntity) {
        viewModelScope.launch { repository.delete(transaction) }
    }

    fun withRunningBalances(items: List<TransactionWithCategory>): List<HistoryRow> {
        var balance = 0L
        val chronological = items.sortedWith(compareBy<TransactionWithCategory> { it.transaction.occurredAt }.thenBy { it.transaction.createdAt })
        val balanceMap = chronological.associate { item ->
            balance += if (item.transaction.type == "INCOME") item.transaction.amount else -item.transaction.amount
            item.transaction.id to balance
        }
        return items.map { HistoryRow(it, balanceMap[it.transaction.id] ?: 0L) }
    }
    suspend fun allocationFor(transactionId: Long): Long = piggyBankRepository?.getAllocationForTransaction(transactionId) ?: 0L

    class Factory(private val repository: TransactionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HistoryViewModel(repository) as T
    }
}

data class HistoryRow(
    val item: TransactionWithCategory,
    val balanceAfter: Long
)
