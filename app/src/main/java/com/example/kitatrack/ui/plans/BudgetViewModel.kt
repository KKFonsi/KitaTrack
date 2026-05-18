package com.example.kitatrack.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kitatrack.data.local.entity.CategoryEntity
import com.example.kitatrack.data.local.model.BudgetProgress
import com.example.kitatrack.data.repository.BudgetRepository
import com.example.kitatrack.data.repository.CategoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BudgetUiState(
    val budgets: List<BudgetProgress> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val message: String? = null
)

class BudgetViewModel(
    private val budgetRepository: BudgetRepository,
    categoryRepository: CategoryRepository
) : ViewModel() {
    val uiState = combine(
        budgetRepository.getBudgetProgress(),
        categoryRepository.getExpenseCategories()
    ) { budgets, categories -> BudgetUiState(budgets, categories) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetUiState())

    fun save(id: Long?, name: String, type: String, amountText: String, categoryId: Long?, active: Boolean, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val amount = amountText.toBigDecimalOrNull()
            val centavos = amount?.movePointRight(2)?.toLong()
            val result = if (centavos == null) Result.failure(IllegalArgumentException("Budget amount must be valid."))
            else if (id == null) budgetRepository.createBudget(name, type, centavos, categoryId, active)
            else budgetRepository.updateBudget(id, name, type, centavos, categoryId, active)
            onResult(result)
        }
    }

    fun delete(id: Long) = viewModelScope.launch { budgetRepository.deleteBudget(id) }

    class Factory(private val budgetRepository: BudgetRepository, private val categoryRepository: CategoryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = BudgetViewModel(budgetRepository, categoryRepository) as T
    }
}
