package com.example.kitatrack.ui.addtransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kitatrack.data.local.entity.CategoryEntity
import com.example.kitatrack.data.local.entity.TransactionEntity
import com.example.kitatrack.data.repository.CategoryRepository
import com.example.kitatrack.data.repository.TransactionRepository
import com.example.kitatrack.data.repository.BudgetRepository
import com.example.kitatrack.data.repository.PiggyBankRepository
import com.example.kitatrack.data.repository.DebtRepository
import com.example.kitatrack.data.repository.SubscriptionRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class TransactionType { INCOME, EXPENSE }

sealed interface SaveTransactionResult {
    data class Success(val budgetWarning: String? = null) : SaveTransactionResult
    data class Error(val message: String) : SaveTransactionResult
}

class AddTransactionViewModel(
    private val transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    private val piggyBankRepository: PiggyBankRepository,
    private val debtRepository: DebtRepository,
    private val subscriptionRepository: SubscriptionRepository
) : ViewModel() {
    val expenseCategories = categoryRepository.getExpenseCategories()
    val incomeSources = categoryRepository.getIncomeSources()
    private val _saveResults = MutableSharedFlow<SaveTransactionResult>()
    val saveResults = _saveResults.asSharedFlow()

    fun save(
        existingId: Long?,
        type: TransactionType?,
        amountText: String,
        category: CategoryEntity?,
        description: String,
        occurredAt: Long?,
        note: String?,
        piggyBankIdForExpense: Long? = null
    ) {
        viewModelScope.launch {
            val amount = amountText.toBigDecimalOrNull()
            when {
                type == null -> _saveResults.emit(SaveTransactionResult.Error("Select income or expense."))
                amount == null || amount <= java.math.BigDecimal.ZERO ->
                    _saveResults.emit(SaveTransactionResult.Error("Amount must be greater than 0."))
                category == null -> _saveResults.emit(
                    SaveTransactionResult.Error(if (type == TransactionType.INCOME) "Select a source of funds." else "Select a category.")
                )
                type == TransactionType.EXPENSE && description.trim().isBlank() ->
                    _saveResults.emit(SaveTransactionResult.Error("Description cannot be empty."))
                occurredAt == null -> _saveResults.emit(SaveTransactionResult.Error("Select a valid date."))
                else -> {
                    val now = System.currentTimeMillis()
                    val amountInCentavos = amount.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
                    if (existingId == null) {
                        val insertedId = transactionRepository.insert(
                            TransactionEntity(
                                amount = amountInCentavos,
                                type = type.name,
                                categoryId = category.id,
                                description = if (type == TransactionType.INCOME) "" else description.trim(),
                                note = if (type == TransactionType.INCOME) null else note?.trim()?.ifBlank { null },
                                occurredAt = occurredAt,
                                createdAt = now,
                                updatedAt = now
                            )
                        )
                        if (type == TransactionType.INCOME) {
                            val debtAllocated = debtRepository.allocateFromIncome(insertedId, amountInCentavos, occurredAt)
                            val piggyAllocated = piggyBankRepository.allocateFromIncome(insertedId, amountInCentavos - debtAllocated, occurredAt)
                            subscriptionRepository.allocateFromIncome(insertedId, amountInCentavos - debtAllocated - piggyAllocated, occurredAt)
                        } else if (piggyBankIdForExpense != null) {
                            piggyBankRepository.deductExpense(piggyBankIdForExpense, amountInCentavos, insertedId)
                        }
                    } else {
                        val existing = transactionRepository.getTransactionOnce(existingId)
                        if (existing == null) {
                            _saveResults.emit(SaveTransactionResult.Error("Transaction could not be found."))
                            return@launch
                        }
                        val current = existing.copy(
                            id = existingId,
                            amount = amountInCentavos,
                            type = type.name,
                            categoryId = category.id,
                            description = if (type == TransactionType.INCOME) "" else description.trim(),
                            note = if (type == TransactionType.INCOME) null else note?.trim()?.ifBlank { null },
                            occurredAt = occurredAt,
                            updatedAt = now
                        )
                        transactionRepository.update(current)
                    }
                    val warning = if (type == TransactionType.EXPENSE) currentBudgetWarning() else null
                    _saveResults.emit(SaveTransactionResult.Success(warning))
                }
            }
        }
    }
    fun piggyBanks() = piggyBankRepository.getActivePiggyBanks()

    private suspend fun currentBudgetWarning(): String? {
        return budgetRepository.getBudgetProgress().first()
            .filter { it.isActive }
            .firstOrNull { it.isOverLimit || it.isNearLimit }
            ?.let {
                if (it.isOverLimit) "${it.name} is over budget by ${com.example.kitatrack.util.Formatters.peso(-it.remainingAmount)}."
                else "You have used ${it.usagePercent}% of ${it.name}."
            }
    }

    class Factory(
        private val transactionRepository: TransactionRepository,
        private val categoryRepository: CategoryRepository,
        private val budgetRepository: BudgetRepository,
        private val piggyBankRepository: PiggyBankRepository,
        private val debtRepository: DebtRepository,
        private val subscriptionRepository: SubscriptionRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AddTransactionViewModel(transactionRepository, categoryRepository, budgetRepository, piggyBankRepository, debtRepository, subscriptionRepository) as T
    }
}
