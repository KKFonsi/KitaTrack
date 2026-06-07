package com.example.kitatrack.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kitatrack.data.local.entity.CategoryEntity
import com.example.kitatrack.data.repository.CategoryRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

class CategoriesViewModel(private val repository: CategoryRepository) : ViewModel() {
    val expenseCategories = repository.getExpenseCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val incomeSources = repository.getIncomeSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    fun add(name: String, type: String) = viewModelScope.launch {
        repository.addCustomCategory(name, type)
            .onSuccess { _messages.emit(if (type == CategoryRepository.TYPE_INCOME_SOURCE) "Income source added." else "Category added.") }
            .onFailure { _messages.emit(it.message ?: "Something went wrong. Try again.") }
    }

    fun rename(category: CategoryEntity, newName: String) = viewModelScope.launch {
        repository.renameCustomCategory(category, newName)
            .onSuccess { _messages.emit("Category updated.") }
            .onFailure { _messages.emit(it.message ?: "Something went wrong. Try again.") }
    }

    fun delete(category: CategoryEntity) = viewModelScope.launch {
        repository.deleteCustomCategory(category)
            .onSuccess { _messages.emit("Category deleted.") }
            .onFailure { _messages.emit(it.message ?: "Something went wrong. Try again.") }
    }

    class Factory(private val repository: CategoryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CategoriesViewModel(repository) as T
    }
}
