package com.example.kitatrack.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kitatrack.data.repository.BackupRepository
import com.example.kitatrack.data.repository.BackupValidationResult
import com.example.kitatrack.data.repository.RestoreMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = false,
    val selectedBackupName: String? = null,
    val validation: BackupValidationResult? = null,
    val pendingBackupJson: String? = null,
    val shouldConfirmRestore: Boolean = false,
    val message: String? = null
)

class SettingsViewModel(private val repository: BackupRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun createCsv(onReady: (Result<String>) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)
            val result = repository.exportTransactionsToCsv()
            _uiState.value = _uiState.value.copy(isLoading = false)
            onReady(result)
        }
    }

    fun createJson(onReady: (Result<String>) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)
            val result = repository.exportFullBackupToJson()
            _uiState.value = _uiState.value.copy(isLoading = false)
            onReady(result)
        }
    }

    fun validateBackup(fileName: String, json: String) {
        val validation = repository.validateBackupJson(json)
        _uiState.value = _uiState.value.copy(
            selectedBackupName = fileName,
            validation = validation,
            pendingBackupJson = if (validation.isValid) json else null,
            shouldConfirmRestore = validation.isValid,
            message = validation.message
        )
    }

    fun restoreValidatedBackup(mode: RestoreMode) {
        val json = _uiState.value.pendingBackupJson ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)
            val result = repository.restoreFromBackup(json, mode)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                pendingBackupJson = if (result.isSuccess) null else json,
                message = result.fold(
                    {
                        if (mode == RestoreMode.MERGE_NEWEST_WINS) "Backup merged successfully." else "Backup restored successfully."
                    },
                    { it.message ?: "Restore failed. Your current data was not changed." }
                )
            )
        }
    }

    fun resetAllData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)
            val result = repository.resetAllData()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                message = result.fold({ "All local data was reset." }, { it.message ?: "Reset failed. Your current data was not changed." })
            )
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun consumeRestoreConfirmation() {
        _uiState.value = _uiState.value.copy(shouldConfirmRestore = false)
    }

    class Factory(private val repository: BackupRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(repository) as T
    }
}
