package com.example.kitatrack.ui.quickadd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kitatrack.data.local.entity.CategoryEntity
import com.example.kitatrack.data.local.entity.TransactionEntity
import com.example.kitatrack.data.repository.CategoryRepository
import com.example.kitatrack.data.repository.IncomeAllocationUseCase
import com.example.kitatrack.data.repository.TransactionRepository
import com.example.kitatrack.util.Formatters
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface QuickAddResult {
    data class Success(val message: String) : QuickAddResult
    data class Error(val message: String) : QuickAddResult
}

class QuickAddViewModel(
    private val transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    private val incomeAllocationUseCase: IncomeAllocationUseCase
) : ViewModel() {
    val expenseCategories = categoryRepository.getExpenseCategories()
    val incomeSources = categoryRepository.getIncomeSources()
    private val _preview = MutableStateFlow("Enter an amount to preview allocation.")
    val preview = _preview.asStateFlow()
    private val _result = MutableSharedFlow<QuickAddResult>()
    val result = _result.asSharedFlow()
    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()

    fun previewIncome(amountText: String) {
        viewModelScope.launch {
            val amount = amountText.toBigDecimalOrNull()
            if (amount == null || amount <= java.math.BigDecimal.ZERO) {
                _preview.value = "Enter an amount to preview allocation."
                return@launch
            }
            val centavos = amount.toCentavosOrNull() ?: run {
                _preview.value = "Enter a valid amount to preview allocation."
                return@launch
            }
            val preview = incomeAllocationUseCase.previewIncome(centavos)
            _preview.value = buildString {
                append("Allocation Preview\n")
                append("Income: ${Formatters.peso(preview.originalIncomeAmount)}\n\n")
                append("Debt Reserve: ${Formatters.peso(preview.debtAllocatedTotal)}\n")
                append("Piggy Banks: ${Formatters.peso(preview.piggyBankAllocatedTotal)}\n")
                append("Subscription Reserve: ${Formatters.peso(preview.subscriptionAllocatedTotal)}\n")
                append("Main Balance: ${Formatters.peso(preview.mainBalanceAmount)}")
                if (preview.warnings.isNotEmpty()) append("\n\n${preview.warnings.joinToString("\n")}")
            }
        }
    }

    fun save(type: String, amountText: String, category: CategoryEntity?, description: String, date: Long, notes: String?) {
        viewModelScope.launch {
            if (_isSaving.value) return@launch
            val amount = amountText.toBigDecimalOrNull()
            when {
                amount == null || amount <= java.math.BigDecimal.ZERO -> _result.emit(QuickAddResult.Error("Enter a valid amount."))
                category == null -> _result.emit(QuickAddResult.Error(if (type == QuickAddActivity.TYPE_INCOME) "Select an income source." else "Select a category."))
                type == QuickAddActivity.TYPE_EXPENSE && description.trim().isBlank() -> _result.emit(QuickAddResult.Error("Name cannot be empty."))
                else -> {
                    val centavos = amount.toCentavosOrNull()
                    if (centavos == null) {
                        _result.emit(QuickAddResult.Error("Enter a valid amount."))
                        return@launch
                    }
                    _isSaving.value = true
                    try {
                        val now = System.currentTimeMillis()
                        val txId = transactionRepository.insert(
                            TransactionEntity(
                                amount = centavos,
                                type = type,
                                categoryId = category.id,
                                description = if (type == QuickAddActivity.TYPE_INCOME) "" else description.trim(),
                                note = if (type == QuickAddActivity.TYPE_EXPENSE) notes?.trim()?.ifBlank { null } else null,
                                occurredAt = date,
                                createdAt = now,
                                updatedAt = now
                            )
                        )
                        if (type == QuickAddActivity.TYPE_INCOME) {
                            incomeAllocationUseCase.allocateIncome(txId, centavos, date)
                        }
                        _result.emit(QuickAddResult.Success(if (type == QuickAddActivity.TYPE_INCOME) "Income added. Allocations updated." else "Expense added."))
                    } catch (e: Exception) {
                        _result.emit(QuickAddResult.Error("Something went wrong. Try again."))
                    } finally {
                        _isSaving.value = false
                    }
                }
            }
        }
    }

    private fun java.math.BigDecimal.toCentavosOrNull(): Long? = runCatching {
        movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
    }.getOrNull()?.takeIf { it > 0 }

    class Factory(
        private val transactionRepository: TransactionRepository,
        private val categoryRepository: CategoryRepository,
        private val incomeAllocationUseCase: IncomeAllocationUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            QuickAddViewModel(transactionRepository, categoryRepository, incomeAllocationUseCase) as T
    }
}
