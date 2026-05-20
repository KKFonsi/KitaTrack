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
import com.example.kitatrack.data.repository.IncomeAllocationUseCase
import com.example.kitatrack.util.Formatters
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class TransactionType { INCOME, EXPENSE }

sealed interface SaveTransactionResult {
    data class Success(val budgetWarning: String? = null, val allocationSummary: String? = null) : SaveTransactionResult
    data class Error(val message: String) : SaveTransactionResult
}

class AddTransactionViewModel(
    private val transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    private val piggyBankRepository: PiggyBankRepository,
    private val incomeAllocationUseCase: IncomeAllocationUseCase
) : ViewModel() {
    val expenseCategories = categoryRepository.getExpenseCategories()
    val incomeSources = categoryRepository.getIncomeSources()
    private val _saveResults = MutableSharedFlow<SaveTransactionResult>()
    val saveResults = _saveResults.asSharedFlow()
    private val _allocationPreview = MutableStateFlow("Enter an income amount to preview where the money will go.")
    val allocationPreview = _allocationPreview.asStateFlow()
    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()

    fun previewAllocation(amountText: String) {
        viewModelScope.launch {
            val amount = amountText.toBigDecimalOrNull()
            if (amount == null || amount <= java.math.BigDecimal.ZERO) {
                _allocationPreview.value = "Enter an income amount to preview where the money will go."
                return@launch
            }
            val amountInCentavos = amount.toCentavosOrNull() ?: run {
                _allocationPreview.value = "Enter a valid amount to preview allocation."
                return@launch
            }
            val result = incomeAllocationUseCase.previewIncome(amountInCentavos)
            _allocationPreview.value = result.toPreviewText()
        }
    }

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
            if (_isSaving.value) return@launch
            val amount = amountText.toBigDecimalOrNull()
            when {
                type == null -> _saveResults.emit(SaveTransactionResult.Error("Select income or expense."))
                amount == null || amount <= java.math.BigDecimal.ZERO -> _saveResults.emit(SaveTransactionResult.Error("Amount must be greater than 0."))
                category == null -> _saveResults.emit(SaveTransactionResult.Error(if (type == TransactionType.INCOME) "Select a source of funds." else "Select a category."))
                type == TransactionType.EXPENSE && description.trim().isBlank() -> _saveResults.emit(SaveTransactionResult.Error("Description cannot be empty."))
                occurredAt == null -> _saveResults.emit(SaveTransactionResult.Error("Select a valid date."))
                else -> {
                    val amountInCentavos = amount.toCentavosOrNull()
                    if (amountInCentavos == null) {
                        _saveResults.emit(SaveTransactionResult.Error("Enter a valid amount."))
                        return@launch
                    }
                    _isSaving.value = true
                    try {
                        val now = System.currentTimeMillis()
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
                            var allocationSummary: String? = null
                            if (type == TransactionType.INCOME) {
                                allocationSummary = incomeAllocationUseCase
                                    .allocateIncome(insertedId, amountInCentavos, occurredAt)
                                    .toSnackSummary()
                            } else if (piggyBankIdForExpense != null) {
                                piggyBankRepository.deductExpense(piggyBankIdForExpense, amountInCentavos, insertedId)
                            }
                            val warning = if (type == TransactionType.EXPENSE) currentBudgetWarning() else null
                            _saveResults.emit(SaveTransactionResult.Success(warning, allocationSummary))
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
                            val warning = if (type == TransactionType.EXPENSE) currentBudgetWarning() else null
                            _saveResults.emit(SaveTransactionResult.Success(warning))
                        }
                    } catch (e: Exception) {
                        _saveResults.emit(SaveTransactionResult.Error(e.message ?: "Transaction could not be saved."))
                    } finally {
                        _isSaving.value = false
                    }
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
                if (it.isOverLimit) "${it.name} is over budget by ${Formatters.peso(-it.remainingAmount)}."
                else "You have used ${it.usagePercent}% of ${it.name}."
            }
    }

    private fun java.math.BigDecimal.toCentavosOrNull(): Long? = runCatching {
        movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
    }.getOrNull()?.takeIf { it > 0 }

    private fun com.example.kitatrack.data.local.model.AllocationResult.toSnackSummary(): String? {
        if (!hasReservedMoney) return null
        return "Income saved. Debt: ${Formatters.peso(debtAllocatedTotal)} | Piggy: ${Formatters.peso(piggyBankAllocatedTotal)} | Subs: ${Formatters.peso(subscriptionAllocatedTotal)} | Main: ${Formatters.peso(mainBalanceAmount)}"
    }

    private fun com.example.kitatrack.data.local.model.AllocationResult.toPreviewText(): String {
        return buildString {
            append("Allocation Preview\n")
            append("Income: ${Formatters.peso(originalIncomeAmount)}\n\n")
            append("Debt Reserve: ${Formatters.peso(debtAllocatedTotal)}\n")
            append("Piggy Banks: ${Formatters.peso(piggyBankAllocatedTotal)}\n")
            append("Subscription Reserve: ${Formatters.peso(subscriptionAllocatedTotal)}\n")
            append("Main Balance: ${Formatters.peso(mainBalanceAmount)}\n\n")
            append("Total Money Tracked: ${Formatters.peso(originalIncomeAmount)}")
            if (warnings.isNotEmpty()) {
                append("\n\n")
                append(warnings.joinToString("\n"))
            }
        }
    }

    class Factory(
        private val transactionRepository: TransactionRepository,
        private val categoryRepository: CategoryRepository,
        private val budgetRepository: BudgetRepository,
        private val piggyBankRepository: PiggyBankRepository,
        private val incomeAllocationUseCase: IncomeAllocationUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AddTransactionViewModel(transactionRepository, categoryRepository, budgetRepository, piggyBankRepository, incomeAllocationUseCase) as T
    }
}
