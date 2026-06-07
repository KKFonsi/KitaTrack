package com.example.kitatrack.ui.settings

import android.Manifest
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.kitatrack.KitaTrackApplication
import com.example.kitatrack.R
import com.example.kitatrack.data.local.entity.ReminderEntity
import com.example.kitatrack.data.repository.RestoreMode
import com.example.kitatrack.reminders.NotificationHelper
import com.example.kitatrack.util.ThemePreferences
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class SettingsFragment : Fragment(R.layout.fragment_settings) {
    private val app by lazy { requireActivity().application as KitaTrackApplication }
    private val viewModel by viewModels<SettingsViewModel> { SettingsViewModel.Factory(app.backupRepository, app.appSettingsRepository, app.reminderRepository) }
    private var pendingExportContent: String? = null
    private var pendingExportSuccessMessage: String = ""
    private var bindingSettings = false

    private val createCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        writeExport(uri)
    }
    private val createJson = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        writeExport(uri)
    }
    private val openJson = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult showMessage("Restore cancelled.")
        runCatching {
            requireContext().contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("File could not be opened.")
        }.onSuccess { json ->
            viewModel.validateBackup(displayName(uri), json)
        }.onFailure {
            showMessage(it.message ?: "Invalid backup file selected.")
        }
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshPermissionStatus(requireView())
        if (it) viewModel.rescheduleReminders() else showMessage("Notifications are off. KitaTrack can still track your data, but reminders will not appear until notifications are allowed.")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindThemeToggle(view)
        bindActionRow(view.findViewById(R.id.export_csv_button), "CSV", "Export to CSV", "Open in Excel or Google Sheets.", R.color.kitatrack_chip_green_background, R.color.kitatrack_primary_green)
        bindActionRow(view.findViewById(R.id.export_json_button), "↓", "Export JSON Backup", "Full backup - restores all app data.", R.color.kitatrack_soft_mint, R.color.kitatrack_primary_green)
        bindActionRow(view.findViewById(R.id.import_json_button), "↑", "Import JSON Backup", "Merge with or replace current data.", R.color.kitatrack_chip_yellow_background, R.color.kitatrack_warning_yellow)
        bindActionRow(view.findViewById(R.id.reset_data_button), "DEL", "Reset Local Data", "Removes everything. Cannot be undone.", R.color.kitatrack_chip_red_background, R.color.kitatrack_expense_red)

        view.findViewById<View>(R.id.export_csv_button).setOnClickListener {
            viewModel.createCsv { result ->
                result.onSuccess {
                    pendingExportContent = it
                    pendingExportSuccessMessage = "CSV exported successfully."
                    createCsv.launch("kitatrack_transactions_${dateStamp()}.csv")
                }.onFailure { showMessage(it.message ?: "CSV export failed.") }
            }
        }
        view.findViewById<View>(R.id.export_json_button).setOnClickListener {
            viewModel.createJson { result ->
                result.onSuccess {
                    pendingExportContent = it
                    pendingExportSuccessMessage = "Backup exported successfully."
                    createJson.launch("kitatrack_backup_${dateStamp()}.json")
                }.onFailure { showMessage(it.message ?: "Backup export failed.") }
            }
        }
        view.findViewById<View>(R.id.import_json_button).setOnClickListener {
            openJson.launch(arrayOf("application/json", "text/*"))
        }
        view.findViewById<View>(R.id.reset_data_button).setOnClickListener {
            showResetConfirmation()
        }
        setupReminderControls(view)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    view.findViewById<ProgressBar>(R.id.backup_progress).isVisible = state.isLoading
                    view.findViewById<TextView>(R.id.selected_file_value).text =
                        state.selectedBackupName ?: "No file selected"
                    view.findViewById<TextView>(R.id.validation_status_value).text =
                        state.validation?.message ?: "Select a JSON backup to validate it."
                    if (state.shouldConfirmRestore && state.validation?.isValid == true && state.pendingBackupJson != null) {
                        viewModel.consumeRestoreConfirmation()
                        showRestoreConfirmation()
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.messages.collect { showMessage(it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.reminderSettings.collect { settings ->
                    bindingSettings = true
                    view.findViewById<SwitchMaterial>(R.id.master_reminders_switch).isChecked = settings.remindersEnabled
                    view.findViewById<SwitchMaterial>(R.id.debt_reminders_switch).isChecked = settings.debtRemindersEnabled
                    view.findViewById<SwitchMaterial>(R.id.subscription_reminders_switch).isChecked = settings.subscriptionRemindersEnabled
                    view.findViewById<SwitchMaterial>(R.id.budget_alerts_switch).isChecked = settings.budgetAlertsEnabled
                    view.findViewById<SwitchMaterial>(R.id.piggy_reminders_switch).isChecked = settings.piggyBankRemindersEnabled
                    view.findViewById<SwitchMaterial>(R.id.missed_reminders_switch).isChecked = settings.missedContributionRemindersEnabled
                    view.findViewById<SwitchMaterial>(R.id.ai_monthly_summary_switch).isChecked = settings.aiSummaryEnabled
                    view.findViewById<AutoCompleteTextView>(R.id.reminder_timing_input).setText(timingLabel(settings.defaultReminderTiming), false)
                    bindingSettings = false
                }
            }
        }
    }

    private fun bindThemeToggle(view: View) {
        view.findViewById<MaterialButton>(R.id.settings_theme_toggle_button).apply {
            updateThemeToggleIcon(this)
            setOnClickListener {
                ThemePreferences.setDarkMode(requireContext(), !ThemePreferences.isDarkModeActive(requireContext()))
            }
        }
    }

    private fun updateThemeToggleIcon(button: MaterialButton) {
        val isDark = ThemePreferences.isDarkModeActive(requireContext())
        button.setIconResource(if (isDark) R.drawable.ic_theme_sun else R.drawable.ic_theme_moon)
        button.contentDescription = if (isDark) "Switch to day mode" else "Switch to dark mode"
    }

    private fun bindActionRow(row: View, icon: String, title: String, description: String, iconBackground: Int, accent: Int) {
        row.findViewById<TextView>(R.id.settings_action_icon).apply {
            text = icon
            setTextColor(ContextCompat.getColor(requireContext(), accent))
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), iconBackground))
        }
        row.findViewById<TextView>(R.id.settings_action_title).apply {
            text = title
            setTextColor(ContextCompat.getColor(requireContext(), accent.takeIf { title.startsWith("Reset") } ?: R.color.kitatrack_primary_text))
        }
        row.findViewById<TextView>(R.id.settings_action_description).text = description
    }

    override fun onResume() {
        super.onResume()
        view?.let { refreshPermissionStatus(it) }
    }

    private fun setupReminderControls(view: View) {
        refreshPermissionStatus(view)
        view.findViewById<MaterialButton>(R.id.request_notifications_button).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                showMessage("Notifications are already available on this Android version.")
            }
        }
        val timingInput = view.findViewById<AutoCompleteTextView>(R.id.reminder_timing_input)
        val labels = listOf("Same day", "1 day before", "3 days before", "1 week before")
        val values = listOf(ReminderEntity.TIMING_SAME_DAY, ReminderEntity.TIMING_ONE_DAY_BEFORE, ReminderEntity.TIMING_THREE_DAYS_BEFORE, ReminderEntity.TIMING_ONE_WEEK_BEFORE)
        timingInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, labels))
        timingInput.threshold = 0
        timingInput.keyListener = null
        timingInput.setOnClickListener { timingInput.showDropDown() }
        timingInput.setOnItemClickListener { _, _, pos, _ ->
            if (bindingSettings) return@setOnItemClickListener
            viewModel.saveReminderSettings { it.copy(defaultReminderTiming = values[pos]) }
        }
        view.findViewById<SwitchMaterial>(R.id.master_reminders_switch).setOnCheckedChangeListener { _, checked -> if (!bindingSettings) viewModel.saveReminderSettings { it.copy(remindersEnabled = checked) } }
        view.findViewById<SwitchMaterial>(R.id.debt_reminders_switch).setOnCheckedChangeListener { _, checked -> if (!bindingSettings) viewModel.saveReminderSettings { it.copy(debtRemindersEnabled = checked) } }
        view.findViewById<SwitchMaterial>(R.id.subscription_reminders_switch).setOnCheckedChangeListener { _, checked -> if (!bindingSettings) viewModel.saveReminderSettings { it.copy(subscriptionRemindersEnabled = checked) } }
        view.findViewById<SwitchMaterial>(R.id.budget_alerts_switch).setOnCheckedChangeListener { _, checked -> if (!bindingSettings) viewModel.saveReminderSettings { it.copy(budgetAlertsEnabled = checked) } }
        view.findViewById<SwitchMaterial>(R.id.piggy_reminders_switch).setOnCheckedChangeListener { _, checked -> if (!bindingSettings) viewModel.saveReminderSettings { it.copy(piggyBankRemindersEnabled = checked) } }
        view.findViewById<SwitchMaterial>(R.id.missed_reminders_switch).setOnCheckedChangeListener { _, checked -> if (!bindingSettings) viewModel.saveReminderSettings { it.copy(missedContributionRemindersEnabled = checked) } }
        view.findViewById<SwitchMaterial>(R.id.ai_monthly_summary_switch).setOnCheckedChangeListener { _, checked -> if (!bindingSettings) viewModel.saveAppSettings { it.copy(aiSummaryEnabled = checked) } }
        view.findViewById<MaterialButton>(R.id.refresh_reminders_button).setOnClickListener { viewModel.rescheduleReminders() }
    }

    private fun refreshPermissionStatus(view: View) {
        val allowed = NotificationHelper.canNotify(requireContext())
        val permissionNeeded = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        view.findViewById<View>(R.id.notification_permission_banner).isVisible = !allowed
        view.findViewById<TextView>(R.id.notification_permission_value).text =
            if (allowed) "Notifications are allowed." else "Reminders won't fire until you allow them in system settings."
        view.findViewById<MaterialButton>(R.id.request_notifications_button).isVisible =
            permissionNeeded
    }

    private fun timingLabel(value: String): String = when (value) {
        ReminderEntity.TIMING_SAME_DAY -> "Same day"
        ReminderEntity.TIMING_THREE_DAYS_BEFORE -> "3 days before"
        ReminderEntity.TIMING_ONE_WEEK_BEFORE -> "1 week before"
        else -> "1 day before"
    }

    private fun writeExport(uri: Uri?) {
        val content = pendingExportContent
        if (uri == null || content == null) return showMessage("Export cancelled.")
        runCatching {
            requireContext().contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(content) }
                ?: error("File could not be created.")
        }.onSuccess { showMessage(pendingExportSuccessMessage) }
            .onFailure { showMessage(it.message ?: "Export failed.") }
        pendingExportContent = null
    }

    private fun showRestoreConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Import backup?")
            .setMessage("Choose how to import this backup. Merge keeps your current data and uses the newest version when records overlap. Replace clears current data first. Consider exporting your current backup before either action.")
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Replace") { _, _ -> viewModel.restoreValidatedBackup(RestoreMode.REPLACE) }
            .setPositiveButton("Merge") { _, _ -> viewModel.restoreValidatedBackup(RestoreMode.MERGE_NEWEST_WINS) }
            .show()
    }

    private fun showResetConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Reset local data?")
            .setMessage("This will permanently remove current KitaTrack transactions, categories, summaries, and saved local data from this device. Export a backup first if you may want to restore it later.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Reset") { _, _ ->
                viewModel.resetAllData()
                lifecycleScope.launch {
                    app.categoryRepository.ensureDefaultCategories()
                }
            }
            .show()
    }

    private fun showMessage(message: String) {
        view?.let { Snackbar.make(it, message, Snackbar.LENGTH_LONG).show() }
    }

    private fun dateStamp(): String = SimpleDateFormat("yyyy_MM_dd", Locale.US).format(Date())

    private fun displayName(uri: Uri): String {
        requireContext().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
        }
        return uri.lastPathSegment ?: "Selected backup"
    }
}
