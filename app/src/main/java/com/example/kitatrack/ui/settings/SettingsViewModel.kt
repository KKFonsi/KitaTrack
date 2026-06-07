package com.example.kitatrack.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kitatrack.data.repository.BackupRepository
import com.example.kitatrack.data.repository.BackupValidationResult
import com.example.kitatrack.data.repository.AppSettingsRepository
import com.example.kitatrack.data.repository.ReminderRepository
import com.example.kitatrack.data.repository.RestoreMode
import com.example.kitatrack.data.local.entity.AppSettingsEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = false,
    val selectedBackupName: String? = null,
    val validation: BackupValidationResult? = null,
    val pendingBackupJson: String? = null,
    val shouldConfirmRestore: Boolean = false,
    val message: String? = null
)

class SettingsViewModel(
    private val repository: BackupRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val reminderRepository: ReminderRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    val reminderSettings = appSettingsRepository.observeSettings()
        .map { it ?: AppSettingsEntity() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettingsEntity())

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
            message = null
        )
        _messages.tryEmit(if (validation.isValid) "Backup ready to import." else "Invalid backup file.")
    }

    fun restoreValidatedBackup(mode: RestoreMode) {
        val json = _uiState.value.pendingBackupJson ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)
            val result = repository.restoreFromBackup(json, mode)
            if (result.isSuccess) reminderRepository.rescheduleAll()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                pendingBackupJson = if (result.isSuccess) null else json,
                message = null
            )
            _messages.emit(
                result.fold(
                    { "Backup imported." },
                    { "Import failed." }
                )
            )
        }
    }

    fun resetAllData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)
            val result = repository.resetAllData()
            if (result.isSuccess) reminderRepository.rescheduleAll()
            _uiState.value = _uiState.value.copy(isLoading = false, message = null)
            _messages.emit(result.fold({ "Local data reset." }, { "Reset failed." }))
        }
    }

    fun saveReminderSettings(transform: (AppSettingsEntity) -> AppSettingsEntity) {
        viewModelScope.launch {
            val current = appSettingsRepository.getOrCreateSettings()
            val updated = transform(current)
            if (updated == current) return@launch
            appSettingsRepository.save(updated)
            reminderRepository.rescheduleAll()
            _messages.emit("Reminder settings saved.")
        }
    }

    fun saveAppSettings(transform: (AppSettingsEntity) -> AppSettingsEntity) {
        viewModelScope.launch {
            val current = appSettingsRepository.getOrCreateSettings()
            val updated = transform(current)
            if (updated == current) return@launch
            appSettingsRepository.save(updated)
            _messages.emit("Changes saved.")
        }
    }

    fun rescheduleReminders() {
        viewModelScope.launch {
            reminderRepository.rescheduleAll()
            _messages.emit("Reminders refreshed.")
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun consumeRestoreConfirmation() {
        _uiState.value = _uiState.value.copy(shouldConfirmRestore = false)
    }

    class Factory(
        private val repository: BackupRepository,
        private val appSettingsRepository: AppSettingsRepository,
        private val reminderRepository: ReminderRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(repository, appSettingsRepository, reminderRepository) as T
    }
}
