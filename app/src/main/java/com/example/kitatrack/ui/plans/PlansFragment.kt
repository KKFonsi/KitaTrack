package com.example.kitatrack.ui.plans

import android.os.Bundle
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.content.res.ColorStateList
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.kitatrack.KitaTrackApplication
import com.example.kitatrack.R
import com.example.kitatrack.data.local.model.BudgetProgress
import com.example.kitatrack.data.local.model.DebtProgress
import com.example.kitatrack.data.local.model.SubscriptionProgress
import com.example.kitatrack.data.repository.BudgetRepository
import com.example.kitatrack.data.repository.DebtRepository
import com.example.kitatrack.data.repository.SubscriptionRepository
import com.example.kitatrack.util.CategoryIconMapper
import com.example.kitatrack.util.Formatters
import com.example.kitatrack.util.ThemePreferences
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class PlansFragment : Fragment(R.layout.fragment_plans) {
    private val app by lazy { requireActivity().application as KitaTrackApplication }
    private val viewModel by viewModels<BudgetViewModel> { BudgetViewModel.Factory(app.budgetRepository, app.categoryRepository) }
    private val debtViewModel by viewModels<DebtViewModel> { DebtViewModel.Factory(app.debtRepository) }
    private val subscriptionViewModel by viewModels<SubscriptionViewModel> { SubscriptionViewModel.Factory(app.subscriptionRepository) }
    private val piggyViewModel by viewModels<PiggyBankViewModel> { PiggyBankViewModel.Factory(app.piggyBankRepository, app.transactionRepository) }
    private var latestState = BudgetUiState()
    private val refreshedPiggyIds = mutableSetOf<Long>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        bindThemeToggle(view)
        view.findViewById<MaterialButton>(R.id.add_budget_button).setOnClickListener { showBudgetDialog(null) }
        view.findViewById<MaterialButton>(R.id.add_debt_button).setOnClickListener { showDebtDialog(null) }
        view.findViewById<MaterialButton>(R.id.add_subscription_button).setOnClickListener { showSubscriptionDialog(null) }
        view.findViewById<MaterialButton>(R.id.add_piggy_button).setOnClickListener { showPiggyDialog(null) }
        view.findViewById<MaterialButton>(R.id.manage_categories_button).setOnClickListener {
            findNavController().navigate(R.id.action_plansFragment_to_categoriesFragment)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.uiState.collect {
                    latestState = it
                    renderBudgets(view, it.budgets)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                debtViewModel.debts.collect { renderDebts(view, it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                subscriptionViewModel.subscriptions.collect { renderSubscriptions(view, it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                piggyViewModel.piggyBanks.collect { renderPiggyBanks(view, it) }
            }
        }
    }

    private fun bindThemeToggle(view: View) {
        view.findViewById<MaterialButton>(R.id.plans_theme_toggle_button).apply {
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

    private fun renderDebts(view: View, items: List<DebtProgress>) {
        val container = view.findViewById<LinearLayout>(R.id.debt_list)
        val empty = view.findViewById<TextView>(R.id.debt_empty_state)
        view.findViewById<TextView>(R.id.debt_count_badge).text = items.size.toString()
        container.removeAllViews()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        items.sortedWith(compareByDescending<DebtProgress> { it.isOverdue }.thenByDescending { it.isUpcoming }.thenBy { it.debt.nextDueDate ?: Long.MAX_VALUE }).forEach { item ->
            val debt = item.debt
            val card = layoutInflater.inflate(R.layout.item_debt_card, container, false)
            card.findViewById<TextView>(R.id.debt_name).text = debt.name
            card.findViewById<TextView>(R.id.debt_meta).text =
                "${if (debt.debtType == DebtRepository.TYPE_I_OWE) "Money I owe" else "Owed to me"} - ${debt.personName ?: "No person"}"
            card.findViewById<TextView>(R.id.debt_amounts).text = buildString {
                append("${Formatters.peso(debt.remainingAmount)} remaining\n")
                append("${Formatters.peso(debt.amountPaid)} paid of ${Formatters.peso(debt.totalAmount)}")
                if (debt.debtType == DebtRepository.TYPE_I_OWE && debt.reservedAmount > 0) {
                    append("\n${Formatters.peso(debt.reservedAmount)} reserved")
                }
            }
            card.findViewById<ProgressBar>(R.id.debt_progress).apply {
                progress = item.progressPercent.coerceAtMost(100)
                progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), when {
                    item.isOverdue -> R.color.kitatrack_expense_red
                    item.isUpcoming -> R.color.kitatrack_warning_yellow
                    debt.status == DebtRepository.STATUS_PAID -> R.color.kitatrack_primary_green
                    else -> R.color.kitatrack_primary_green
                }))
            }
            card.findViewById<TextView>(R.id.debt_status).text = buildString {
                append(item.statusLabel)
                append(" - Due ${item.dueLabel}")
                debt.installmentAmount?.let { append(" - ${Formatters.peso(it)} payment") }
            }
            card.findViewById<TextView>(R.id.debt_amounts).visibility = View.GONE
            card.findViewById<LinearLayout>(R.id.debt_figures_row).visibility = View.VISIBLE
            card.findViewById<TextView>(R.id.debt_meta).text =
                "${if (debt.debtType == DebtRepository.TYPE_I_OWE) "Money I owe" else "Money owed to me"} - ${debt.personName ?: "No source"}"
            card.findViewById<TextView>(R.id.debt_remaining_value).apply {
                text = Formatters.peso(debt.remainingAmount)
                setTextColor(ContextCompat.getColor(requireContext(), if (debt.debtType == DebtRepository.TYPE_I_OWE) R.color.kitatrack_expense_red else R.color.kitatrack_primary_green))
            }
            card.findViewById<TextView>(R.id.debt_payment_value).text = Formatters.peso(paymentDueForThisTerm(debt))
            card.findViewById<TextView>(R.id.debt_paid_value).text = Formatters.peso(debt.amountPaid)
            card.findViewById<TextView>(R.id.debt_status).text = item.statusLabel
            card.findViewById<TextView>(R.id.debt_due_chip).apply {
                visibility = View.VISIBLE
                text = "Due ${item.dueLabel}"
                tintChip(this, if (item.isOverdue) R.color.kitatrack_chip_red_background else R.color.kitatrack_chip_neutral_background, if (item.isOverdue) R.color.kitatrack_expense_red else R.color.kitatrack_secondary_text)
            }
            card.findViewById<TextView>(R.id.debt_percent).apply {
                visibility = View.VISIBLE
                text = "${item.progressPercent.coerceAtMost(100)}% paid off"
            }
            card.setOnClickListener { showDebtDetails(item) }
            card.setOnLongClickListener {
                showDebtActions(item)
                true
            }
            container.addView(card)
        }
    }

    private fun showDebtDetails(item: DebtProgress) {
        val d = item.debt
        val isPaid = d.remainingAmount <= 0 || d.status == DebtRepository.STATUS_PAID
        val view = layoutInflater.inflate(R.layout.bottom_sheet_plan_detail, null, false)
        val sheet = showPlanSheet(view, null)
        view.findViewById<TextView>(R.id.detail_title).text = d.name
        view.findViewById<TextView>(R.id.detail_subtitle).text = "Debt - ${d.personName ?: "No source"}"
        view.findViewById<TextView>(R.id.detail_status_badge).apply {
            text = item.statusLabel
            tintChip(this, if (item.isOverdue) R.color.kitatrack_chip_red_background else R.color.kitatrack_chip_green_background, if (item.isOverdue) R.color.kitatrack_expense_red else R.color.kitatrack_primary_green)
        }
        view.findViewById<TextView>(R.id.detail_direction_badge).apply {
            visibility = View.VISIBLE
            text = if (d.debtType == DebtRepository.TYPE_I_OWE) "Money I owe" else "Money others owe me"
            tintChip(this, if (d.debtType == DebtRepository.TYPE_I_OWE) R.color.kitatrack_chip_red_background else R.color.kitatrack_chip_green_background, if (d.debtType == DebtRepository.TYPE_I_OWE) R.color.kitatrack_expense_red else R.color.kitatrack_primary_green)
        }
        view.findViewById<TextView>(R.id.detail_main_amount).apply {
            text = Formatters.peso(d.remainingAmount)
            setTextColor(ContextCompat.getColor(requireContext(), if (d.debtType == DebtRepository.TYPE_I_OWE) R.color.kitatrack_expense_red else R.color.kitatrack_primary_green))
        }
        view.findViewById<TextView>(R.id.detail_amount_context).text = "remaining of ${Formatters.peso(d.totalAmount)}"
        view.findViewById<ProgressBar>(R.id.detail_progress).apply {
            progress = item.progressPercent.coerceAtMost(100)
            progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), if (item.isOverdue) R.color.kitatrack_expense_red else R.color.kitatrack_primary_green))
        }
        view.findViewById<TextView>(R.id.detail_progress_caption).text = "${item.progressPercent.coerceAtMost(100)}% paid off"
        val stats = view.findViewById<LinearLayout>(R.id.detail_stats_container)
        addStatRow(stats, "Amount paid", Formatters.peso(d.amountPaid))
        if (d.debtType == DebtRepository.TYPE_I_OWE) addStatRow(stats, "Debt reserve", Formatters.peso(d.reservedAmount))
        addStatRow(stats, "Payment amount", Formatters.peso(paymentDueForThisTerm(d)))
        addStatRow(stats, "Frequency", d.paymentFrequency.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() })
        addStatRow(stats, "Next due", item.dueLabel)
        view.findViewById<MaterialButton>(R.id.detail_primary_button).apply {
            visibility = if (isPaid) View.GONE else View.VISIBLE
            text = "Pay ${Formatters.peso(paymentDueForThisTerm(d))}"
            setOnClickListener {
                sheet.dismiss()
                showDebtPaymentDialog(item)
            }
        }
        view.findViewById<MaterialButton>(R.id.detail_secondary_button).visibility = View.GONE
        view.findViewById<MaterialButton>(R.id.detail_edit_button).apply {
            text = if (isPaid) "Archive" else "Edit"
            setOnClickListener {
                sheet.dismiss()
                if (isPaid) debtViewModel.archive(d.id) else showDebtDialog(item)
            }
        }
        view.findViewById<MaterialButton>(R.id.detail_close_button).setOnClickListener { sheet.dismiss() }
    }

    private fun showDebtActions(item: DebtProgress) {
        val debt = item.debt
        val actions = if (debt.debtType == DebtRepository.TYPE_I_OWE) {
            arrayOf("Record payment from reserve", "Record payment from Main Balance", "Add to reserve", "Remove from reserve", "Archive")
        } else {
            arrayOf("Record payment received", "Archive")
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(debt.name)
            .setItems(actions) { _, which ->
                if (debt.debtType == DebtRepository.TYPE_I_OWE) {
                    when (which) {
                        0 -> showDebtPaymentDialog(item, true)
                        1 -> showDebtPaymentDialog(item, false)
                        2 -> showDebtAmountDialog(item, "Add to Debt Reserve") { amount -> debtViewModel.reserve(debt.id, amount, true, debtResultHandler("Debt reserve updated.")) }
                        3 -> showDebtAmountDialog(item, "Remove from Debt Reserve") { amount -> debtViewModel.reserve(debt.id, amount, false, debtResultHandler("Debt reserve updated.")) }
                        4 -> debtViewModel.archive(debt.id)
                    }
                } else {
                    when (which) {
                        0 -> showDebtAmountDialog(item, "Payment received") { amount -> debtViewModel.payment(debt.id, amount, false, debtResultHandler("Debt payment recorded.")) }
                        1 -> debtViewModel.archive(debt.id)
                    }
                }
            }.show()
    }

    private fun showDebtPaymentDialog(item: DebtProgress, preferReserve: Boolean = true) {
        val debt = item.debt
        val dueAmount = paymentDueForThisTerm(debt)
        if (dueAmount <= 0L) {
            Snackbar.make(requireView(), "Debt is already paid.", Snackbar.LENGTH_SHORT).show()
            return
        }
        if (debt.debtType == DebtRepository.TYPE_OWED_TO_ME) {
            showDebtPaymentConfirmation(
                item = item,
                title = "Record payment received",
                amount = dueAmount,
                fromReserve = false,
                summary = "Amount to record: ${Formatters.peso(dueAmount)}\nTotal debt: ${Formatters.peso(debt.totalAmount)}"
            )
            return
        }

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4, 4, 4, 0)
        }
        val methodLayout = TextInputLayout(requireContext()).apply {
            hint = "Payment method"
            endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
        }
        val methodInput = AutoCompleteTextView(requireContext()).apply {
            inputType = android.text.InputType.TYPE_NULL
            keyListener = null
        }
        methodLayout.addView(methodInput)
        val details = TextView(requireContext()).apply {
            setTextColor(ContextCompat.getColor(requireContext(), R.color.kitatrack_secondary_text))
            textSize = 14f
            setPadding(0, 14, 0, 0)
        }
        container.addView(methodLayout)
        container.addView(details)

        val labels = listOf("Debt Reserve", "Main Balance")
        methodInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, labels))
        configureDropdown(methodInput)
        var fromReserve = preferReserve && debt.reservedAmount >= dueAmount
        fun updateDetails() {
            methodInput.setText(if (fromReserve) labels[0] else labels[1], false)
            details.text = buildString {
                if (fromReserve) {
                    append("Debt Reserve available: ${Formatters.peso(debt.reservedAmount)}\n")
                    append("Main Balance impact: ${Formatters.peso(0)} because this money was already reserved.\n")
                } else {
                    append("Source: Main Balance\n")
                    append("Main Balance impact: -${Formatters.peso(dueAmount)}\n")
                }
                append("Amount due this term: ${Formatters.peso(dueAmount)}\n")
                append("Total debt: ${Formatters.peso(debt.totalAmount)}")
                if (dueAmount == debt.remainingAmount) append("\nFinal payment")
            }
        }
        methodInput.setOnItemClickListener { _, _, pos, _ ->
            fromReserve = pos == 0
            updateDetails()
        }
        updateDetails()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Pay ${debt.name}")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Pay") { _, _ ->
                debtViewModel.payment(debt.id, dueAmount, fromReserve, debtResultHandler("Debt payment recorded."))
            }
            .show()
    }

    private fun showDebtPaymentConfirmation(
        item: DebtProgress,
        title: String,
        amount: Long,
        fromReserve: Boolean,
        summary: String
    ) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(summary)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                debtViewModel.payment(item.debt.id, amount, fromReserve, debtResultHandler("Debt payment recorded."))
            }
            .show()
    }

    private fun paymentDueForThisTerm(debt: com.example.kitatrack.data.local.entity.DebtEntity): Long {
        val remaining = debt.remainingAmount.coerceAtLeast(0)
        val scheduled = debt.installmentAmount?.takeIf { it > 0 } ?: remaining
        return minOf(remaining, scheduled)
    }

    private fun debtResultHandler(successMessage: String = "Debt saved."): (Result<Unit>) -> Unit = { result ->
        result.onSuccess {
            Snackbar.make(requireView(), successMessage, Snackbar.LENGTH_SHORT).show()
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) { app.reminderRepository.rescheduleAll() }
        }
        result.onFailure { e -> Snackbar.make(requireView(), e.message ?: "Something went wrong. Try again.", Snackbar.LENGTH_LONG).show() }
    }

    private fun showDebtAmountDialog(item: DebtProgress, title: String, onAmount: (Long) -> Unit) {
        val input = TextInputEditText(requireContext()).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage("Remaining balance: ${Formatters.peso(item.debt.remainingAmount)}")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val amount = input.text.toString().toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: 0L
                onAmount(amount)
            }.show()
    }

    private fun showAmountDialog(title: String, defaultAmount: Long? = null, message: String? = null, onAmount: (Long) -> Unit) {
        val input = TextInputEditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            defaultAmount?.let { setText(it.toBigDecimal().movePointLeft(2).stripTrailingZeros().toPlainString()) }
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val amount = input.text.toString().toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: 0L
                onAmount(amount)
            }.show()
    }

    private fun showDebtDialog(existing: DebtProgress?) {
        val dialog = layoutInflater.inflate(R.layout.dialog_debt, null, false)
        val name = dialog.findViewById<TextInputEditText>(R.id.debt_name_input)
        val person = dialog.findViewById<TextInputEditText>(R.id.debt_person_input)
        val total = dialog.findViewById<TextInputEditText>(R.id.debt_total_input)
        val paid = dialog.findViewById<TextInputEditText>(R.id.debt_paid_input)
        val installment = dialog.findViewById<TextInputEditText>(R.id.debt_installment_input)
        val intervalLayout = dialog.findViewById<TextInputLayout>(R.id.debt_interval_layout)
        val interval = dialog.findViewById<TextInputEditText>(R.id.debt_interval_input)
        val due = dialog.findViewById<TextInputEditText>(R.id.debt_due_input)
        val notes = dialog.findViewById<TextInputEditText>(R.id.debt_notes_input)
        val autoReserve = dialog.findViewById<SwitchMaterial>(R.id.debt_auto_reserve_switch)
        val reminder = dialog.findViewById<SwitchMaterial>(R.id.debt_reminder_switch)
        val active = dialog.findViewById<SwitchMaterial>(R.id.debt_active_switch)
        val context = dialog.findViewById<TextView>(R.id.debt_type_context)
        dialog.findViewById<TextView>(R.id.debt_sheet_title).text = if (existing == null) "Add Debt" else "Edit Debt"
        val typeChips = listOf(
            dialog.findViewById<MaterialButton>(R.id.debt_i_owe_chip) to DebtRepository.TYPE_I_OWE,
            dialog.findViewById<MaterialButton>(R.id.debt_owed_to_me_chip) to DebtRepository.TYPE_OWED_TO_ME
        )
        val frequencyChips = listOf(
            dialog.findViewById<MaterialButton>(R.id.debt_freq_one_time_chip) to DebtRepository.FREQ_ONE_TIME,
            dialog.findViewById<MaterialButton>(R.id.debt_freq_weekly_chip) to DebtRepository.FREQ_WEEKLY,
            dialog.findViewById<MaterialButton>(R.id.debt_freq_bi_weekly_chip) to DebtRepository.FREQ_BI_WEEKLY,
            dialog.findViewById<MaterialButton>(R.id.debt_freq_monthly_chip) to DebtRepository.FREQ_MONTHLY,
            dialog.findViewById<MaterialButton>(R.id.debt_freq_daily_chip) to DebtRepository.FREQ_DAILY,
            dialog.findViewById<MaterialButton>(R.id.debt_freq_custom_chip) to DebtRepository.FREQ_CUSTOM
        )
        var selectedType = existing?.debt?.debtType ?: DebtRepository.TYPE_I_OWE
        var selectedFreq = when (existing?.debt?.paymentFrequency) {
            DebtRepository.FREQ_EVERY_X_DAYS -> DebtRepository.FREQ_CUSTOM
            null -> DebtRepository.FREQ_ONE_TIME
            else -> existing.debt.paymentFrequency
        }
        var selectedDue = existing?.debt?.nextDueDate ?: existing?.debt?.dueDate
        name.setText(existing?.debt?.name)
        person.setText(existing?.debt?.personName)
        total.setText(existing?.debt?.totalAmount?.toBigDecimal()?.movePointLeft(2)?.stripTrailingZeros()?.toPlainString())
        paid.setText(existing?.debt?.amountPaid?.toBigDecimal()?.movePointLeft(2)?.stripTrailingZeros()?.toPlainString())
        installment.setText(existing?.debt?.installmentAmount?.toBigDecimal()?.movePointLeft(2)?.stripTrailingZeros()?.toPlainString())
        interval.setText(existing?.debt?.customIntervalDays?.toString())
        selectedDue?.let { due.setText(Formatters.date(it)) }
        notes.setText(existing?.debt?.notes)
        autoReserve.isChecked = existing?.debt?.autoReserveEnabled ?: true
        reminder.isChecked = existing?.debt?.reminderEnabled ?: false
        active.isChecked = existing?.debt?.isActive ?: true
        fun updateVisibility() {
            intervalLayout.visibility = if (selectedFreq == DebtRepository.FREQ_CUSTOM) View.VISIBLE else View.GONE
            autoReserve.visibility = if (selectedType == DebtRepository.TYPE_I_OWE) View.VISIBLE else View.GONE
            context.text = if (selectedType == DebtRepository.TYPE_I_OWE) {
                "Creates a Debt Reserve: money is set aside from your balance to pay this debt."
            } else {
                "Tracked separately. It does not affect Main Balance until you record the received money."
            }
            typeChips.forEach { (chip, value) -> styleChoiceChip(chip, value == selectedType) }
            frequencyChips.forEach { (chip, value) -> styleChoiceChip(chip, value == selectedFreq) }
        }
        updateVisibility()
        typeChips.forEach { (chip, value) ->
            chip.setOnClickListener {
                selectedType = value
                updateVisibility()
            }
        }
        frequencyChips.forEach { (chip, value) ->
            chip.setOnClickListener {
                selectedFreq = value
                updateVisibility()
            }
        }
        due.setOnClickListener {
            val cal = java.util.Calendar.getInstance()
            android.app.DatePickerDialog(requireContext(), { _, y, m, d ->
                selectedDue = java.util.Calendar.getInstance().apply { set(y, m, d, 12, 0, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
                due.setText(Formatters.date(selectedDue!!))
            }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }
        val sheet = showPlanSheet(dialog, R.id.debt_dialog_scroll)
        dialog.findViewById<MaterialButton>(R.id.debt_cancel_button).setOnClickListener { sheet.dismiss() }
        dialog.findViewById<MaterialButton>(R.id.debt_save_button).setOnClickListener {
                debtViewModel.save(
                    existing?.debt?.id,
                    name.text.toString(),
                    person.text.toString(),
                    selectedType,
                    total.text.toString().toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: 0L,
                    paid.text.toString().toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: 0L,
                    installment.text.toString().toBigDecimalOrNull()?.movePointRight(2)?.toLong(),
                    selectedFreq,
                    interval.text.toString().toIntOrNull(),
                    selectedDue,
                    null,
                    0,
                    autoReserve.isChecked,
                    reminder.isChecked,
                    if (reminder.isChecked) 3 else null,
                    notes.text.toString(),
                    active.isChecked,
                    debtResultHandler("Debt saved.")
                )
            sheet.dismiss()
        }
    }

    private fun configureDropdown(dropdown: AutoCompleteTextView) {
        dropdown.threshold = 0
        dropdown.keyListener = null
        dropdown.setOnClickListener {
            dropdown.post { dropdown.showDropDown() }
        }
        dropdown.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) dropdown.post { dropdown.showDropDown() }
        }
    }

    private fun tintChip(chip: TextView, backgroundColor: Int, textColor: Int) {
        chip.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), backgroundColor))
        chip.setTextColor(ContextCompat.getColor(requireContext(), textColor))
    }

    private fun addStatRow(
        container: LinearLayout,
        label: String,
        value: String,
        valueColor: Int = R.color.kitatrack_primary_text,
        valueBackground: Int? = null
    ) {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(13), 0, dp(13))
        }
        row.addView(TextView(requireContext()).apply {
            text = label.uppercase()
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(requireContext(), R.color.kitatrack_secondary_text))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(requireContext()).apply {
            text = value
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.END
            setTextColor(ContextCompat.getColor(requireContext(), valueColor))
            valueBackground?.let {
                background = ContextCompat.getDrawable(requireContext(), R.drawable.kt_chip_green)
                backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), it))
                setPadding(dp(10), dp(5), dp(10), dp(5))
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        if (container.childCount > 0) {
            container.addView(View(requireContext()).apply {
                setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.kitatrack_divider))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            })
        }
        container.addView(row)
    }

    private fun addActionRow(
        container: LinearLayout,
        label: String,
        iconRes: Int,
        danger: Boolean = false,
        onClick: () -> Unit
    ) {
        if (container.childCount > 0) {
            container.addView(View(requireContext()).apply {
                setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.kitatrack_divider))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            })
        }
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(11), dp(8), dp(11))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        val iconBox = FrameLayout(requireContext()).apply {
            background = ContextCompat.getDrawable(requireContext(), R.drawable.kt_icon_circle)
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), if (danger) R.color.kitatrack_chip_red_background else R.color.kitatrack_chip_neutral_background))
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
        }
        iconBox.addView(ImageView(requireContext()).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), if (danger) R.color.kitatrack_expense_red else R.color.kitatrack_secondary_text))
            layoutParams = FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER)
        })
        row.addView(iconBox)
        row.addView(TextView(requireContext()).apply {
            text = label
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(requireContext(), if (danger) R.color.kitatrack_expense_red else R.color.kitatrack_primary_text))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
            }
        })
        row.addView(ImageView(requireContext()).apply {
            setImageResource(R.drawable.ic_chevron_right_24)
            imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), if (danger) R.color.kitatrack_expense_red else R.color.kitatrack_muted_text))
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
        })
        container.addView(row)
    }

    private fun priorityBackgroundColor(value: String): Int = when (value.uppercase()) {
        SubscriptionRepository.IMPORTANCE_HIGH, SubscriptionRepository.IMPORTANCE_ESSENTIAL -> R.color.kitatrack_chip_red_background
        SubscriptionRepository.IMPORTANCE_MEDIUM -> R.color.kitatrack_chip_yellow_background
        else -> R.color.kitatrack_chip_neutral_background
    }

    private fun priorityTextColor(value: String): Int = when (value.uppercase()) {
        SubscriptionRepository.IMPORTANCE_HIGH, SubscriptionRepository.IMPORTANCE_ESSENTIAL -> R.color.kitatrack_expense_red
        SubscriptionRepository.IMPORTANCE_MEDIUM -> R.color.kitatrack_warning_yellow
        else -> R.color.kitatrack_secondary_text
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showPlanSheet(content: View, scrollId: Int?, maxHeightRatio: Float = 0.58f): BottomSheetDialog {
        val sheet = BottomSheetDialog(requireContext())
        sheet.setContentView(content)
        sheet.setOnShowListener {
            sheet.window?.setDimAmount(0.42f)
            sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.apply {
                setBackgroundColor(Color.TRANSPARENT)
            }
            scrollId?.let { id ->
                content.findViewById<NestedScrollView>(id)?.let { scroll ->
                    scroll.layoutParams = scroll.layoutParams.apply {
                        height = (resources.displayMetrics.heightPixels * maxHeightRatio).toInt()
                    }
                }
            }
        }
        sheet.show()
        return sheet
    }

    private fun styleChoiceChip(button: MaterialButton, selected: Boolean) {
        button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), if (selected) R.color.kitatrack_primary_green else R.color.kitatrack_secondary_background))
        button.setTextColor(ContextCompat.getColor(requireContext(), if (selected) R.color.white else R.color.kitatrack_secondary_text))
        button.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), if (selected) R.color.kitatrack_primary_green else R.color.kitatrack_subtle_border))
        button.strokeWidth = resources.getDimensionPixelSize(if (selected) R.dimen.kt_chip_selected_stroke else R.dimen.kt_chip_stroke)
        button.isAllCaps = false
        button.stateListAnimator = null
    }

    private fun stylePriorityChip(button: MaterialButton, selected: Boolean, value: String) {
        val background = if (!selected) {
            R.color.kitatrack_secondary_background
        } else {
            when (value) {
                SubscriptionRepository.IMPORTANCE_HIGH, SubscriptionRepository.IMPORTANCE_ESSENTIAL -> R.color.kitatrack_chip_red_background
                SubscriptionRepository.IMPORTANCE_MEDIUM -> R.color.kitatrack_chip_yellow_background
                else -> R.color.kitatrack_chip_neutral_background
            }
        }
        val text = if (!selected) {
            R.color.kitatrack_secondary_text
        } else {
            when (value) {
                SubscriptionRepository.IMPORTANCE_HIGH, SubscriptionRepository.IMPORTANCE_ESSENTIAL -> R.color.kitatrack_expense_red
                SubscriptionRepository.IMPORTANCE_MEDIUM -> R.color.kitatrack_warning_yellow
                else -> R.color.kitatrack_secondary_text
            }
        }
        button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), background))
        button.setTextColor(ContextCompat.getColor(requireContext(), text))
        button.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), if (selected) background else R.color.kitatrack_subtle_border))
        button.strokeWidth = resources.getDimensionPixelSize(R.dimen.kt_chip_stroke)
        button.isAllCaps = false
        button.stateListAnimator = null
    }

    private fun renderSubscriptions(view: View, items: List<SubscriptionProgress>) {
        val container = view.findViewById<LinearLayout>(R.id.subscription_list)
        val empty = view.findViewById<TextView>(R.id.subscription_empty_state)
        view.findViewById<TextView>(R.id.subscription_count_badge).text = items.size.toString()
        container.removeAllViews()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        items.sortedWith(compareByDescending<SubscriptionProgress> { it.isOverdue }.thenByDescending { it.isUpcoming }.thenBy { it.subscription.nextBillingDate ?: Long.MAX_VALUE }).forEach { item ->
            val sub = item.subscription
            val card = layoutInflater.inflate(R.layout.item_subscription_card, container, false)
            card.findViewById<TextView>(R.id.subscription_name).text = sub.name
            card.findViewById<TextView>(R.id.subscription_amount).text = Formatters.peso(sub.amount)
            card.findViewById<TextView>(R.id.subscription_meta).text =
                "${item.cycleLabel} - Next ${item.dueLabel}"
            card.findViewById<TextView>(R.id.subscription_priority).apply {
                visibility = View.VISIBLE
                text = "${sub.importance.lowercase().replaceFirstChar { it.uppercase() }} priority"
                tintChip(this, when (sub.importance.uppercase()) {
                    "HIGH" -> R.color.kitatrack_chip_red_background
                    "LOW" -> R.color.kitatrack_chip_neutral_background
                    else -> R.color.kitatrack_chip_yellow_background
                }, when (sub.importance.uppercase()) {
                    "HIGH" -> R.color.kitatrack_expense_red
                    else -> R.color.kitatrack_secondary_text
                })
            }
            card.findViewById<ProgressBar>(R.id.subscription_reserve_progress).apply {
                visibility = if (sub.reserveEnabled) View.VISIBLE else View.GONE
                progress = item.reservePercent
                progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), when {
                    item.isOverdue -> R.color.kitatrack_expense_red
                    item.isUpcoming && !item.isFunded -> R.color.kitatrack_warning_yellow
                    else -> R.color.kitatrack_primary_green
                }))
            }
            card.findViewById<TextView>(R.id.subscription_status).apply {
                text = if (sub.reserveEnabled) "Reserved" else "Reserve Off"
                tintChip(this, if (sub.reserveEnabled) R.color.kitatrack_chip_green_background else R.color.kitatrack_chip_neutral_background, if (sub.reserveEnabled) R.color.kitatrack_primary_green else R.color.kitatrack_secondary_text)
            }
            card.setOnClickListener { showSubscriptionDetails(item) }
            container.addView(card)
        }
    }

    private fun showSubscriptionDetails(item: SubscriptionProgress) {
        val sub = item.subscription
        val view = layoutInflater.inflate(R.layout.bottom_sheet_plan_detail, null, false)
        val sheet = showPlanSheet(view, null)
        view.findViewById<TextView>(R.id.detail_title).text = sub.name
        view.findViewById<TextView>(R.id.detail_subtitle).text = "Subscription - ${item.cycleLabel}"
        view.findViewById<TextView>(R.id.detail_status_badge).apply {
            text = when {
                item.isFunded -> "Funded"
                else -> item.statusLabel
            }
            tintChip(this, if (item.isFunded) R.color.kitatrack_chip_green_background else R.color.kitatrack_chip_neutral_background, if (item.isFunded) R.color.kitatrack_primary_green else R.color.kitatrack_secondary_text)
        }
        view.findViewById<TextView>(R.id.detail_main_amount).text = Formatters.peso(sub.amount)
        view.findViewById<TextView>(R.id.detail_amount_context).text = "/ ${item.cycleLabel.lowercase()}"
        view.findViewById<ProgressBar>(R.id.detail_progress).visibility = View.GONE
        view.findViewById<TextView>(R.id.detail_progress_caption).visibility = View.GONE
        val stats = view.findViewById<LinearLayout>(R.id.detail_stats_container)
        addStatRow(stats, "Next billing", item.dueLabel)
        addStatRow(stats, "Reserve", if (sub.reserveEnabled) "On - ${Formatters.peso(sub.reservedAmount)} saved" else "Off", if (sub.reserveEnabled) R.color.kitatrack_primary_green else R.color.kitatrack_secondary_text)
        addStatRow(stats, "Reminder", if (sub.reminderEnabled) "On" else "Off")
        addStatRow(stats, "Priority", "${sub.importance.lowercase().replaceFirstChar { it.uppercase() }} priority", priorityTextColor(sub.importance), priorityBackgroundColor(sub.importance))
        view.findViewById<MaterialButton>(R.id.detail_primary_button).visibility = View.GONE
        view.findViewById<MaterialButton>(R.id.detail_secondary_button).apply {
            visibility = View.VISIBLE
            text = "Actions"
            setOnClickListener { showSubscriptionActions(item) }
        }
        view.findViewById<MaterialButton>(R.id.detail_edit_button).setOnClickListener {
            sheet.dismiss()
            showSubscriptionDialog(item)
        }
        view.findViewById<MaterialButton>(R.id.detail_close_button).setOnClickListener { sheet.dismiss() }
    }

    private fun showSubscriptionActions(item: SubscriptionProgress) {
        val sub = item.subscription
        val view = layoutInflater.inflate(R.layout.bottom_sheet_subscription_actions, null, false)
        val sheet = showPlanSheet(view, null, 0.62f)
        view.findViewById<TextView>(R.id.subscription_actions_title).text = sub.name
        val list = view.findViewById<LinearLayout>(R.id.subscription_actions_list)
        if (sub.reserveEnabled) {
            addActionRow(list, "Mark paid from reserve", R.drawable.ic_action_paid_reserve) {
                sheet.dismiss()
                showAmountDialog("Payment amount", sub.amount) { amount ->
                    subscriptionViewModel.payment(sub.id, amount, true, subscriptionResultHandler("Subscription payment recorded."))
                }
            }
        }
        addActionRow(list, "Mark paid from Main Balance", R.drawable.ic_action_paid_main_balance) {
            sheet.dismiss()
            showAmountDialog("Payment amount", sub.amount) { amount ->
                subscriptionViewModel.payment(sub.id, amount, false, subscriptionResultHandler("Subscription payment recorded."))
            }
        }
        if (sub.reserveEnabled) {
            addActionRow(list, "Add reserve", R.drawable.ic_action_add_reserve) {
                sheet.dismiss()
                showAmountDialog("Amount to reserve") { amount ->
                    subscriptionViewModel.reserve(sub.id, amount, true, subscriptionResultHandler("Subscription reserve updated."))
                }
            }
            addActionRow(list, "Remove reserve", R.drawable.ic_action_remove_reserve) {
                sheet.dismiss()
                showAmountDialog("Amount to remove from reserve") { amount ->
                    subscriptionViewModel.reserve(sub.id, amount, false, subscriptionResultHandler("Subscription reserve updated."))
                }
            }
        }
        addActionRow(list, "Archive", R.drawable.ic_action_archive, danger = true) {
            sheet.dismiss()
            subscriptionViewModel.archive(sub.id)
        }
        view.findViewById<MaterialButton>(R.id.subscription_actions_cancel).setOnClickListener { sheet.dismiss() }
    }

    private fun subscriptionResultHandler(successMessage: String = "Subscription saved."): (Result<Unit>) -> Unit = { result ->
        result.fold(
            onSuccess = { view?.let { Snackbar.make(it, successMessage, Snackbar.LENGTH_SHORT).show() } },
            onFailure = { error -> view?.let { Snackbar.make(it, error.message ?: "Something went wrong. Try again.", Snackbar.LENGTH_LONG).show() } }
        )
    }

    private fun showSubscriptionDialog(existing: SubscriptionProgress?) {
        val dialog = layoutInflater.inflate(R.layout.dialog_subscription, null, false)
        val name = dialog.findViewById<TextInputEditText>(R.id.subscription_name_input)
        val amount = dialog.findViewById<TextInputEditText>(R.id.subscription_amount_input)
        val intervalLayout = dialog.findViewById<TextInputLayout>(R.id.subscription_interval_layout)
        val interval = dialog.findViewById<TextInputEditText>(R.id.subscription_interval_input)
        val due = dialog.findViewById<TextInputEditText>(R.id.subscription_due_input)
        val category = dialog.findViewById<AutoCompleteTextView>(R.id.subscription_category_input)
        val notes = dialog.findViewById<TextInputEditText>(R.id.subscription_notes_input)
        val reserve = dialog.findViewById<SwitchMaterial>(R.id.subscription_reserve_switch)
        val reminder = dialog.findViewById<SwitchMaterial>(R.id.subscription_reminder_switch)
        val active = dialog.findViewById<SwitchMaterial>(R.id.subscription_active_switch)
        dialog.findViewById<TextView>(R.id.subscription_sheet_title).text = if (existing == null) "Add Subscription" else "Edit Subscription"
        val cycleChips = listOf(
            dialog.findViewById<MaterialButton>(R.id.subscription_cycle_monthly_chip) to SubscriptionRepository.CYCLE_MONTHLY,
            dialog.findViewById<MaterialButton>(R.id.subscription_cycle_yearly_chip) to SubscriptionRepository.CYCLE_YEARLY,
            dialog.findViewById<MaterialButton>(R.id.subscription_cycle_weekly_chip) to SubscriptionRepository.CYCLE_WEEKLY,
            dialog.findViewById<MaterialButton>(R.id.subscription_cycle_custom_chip) to SubscriptionRepository.CYCLE_CUSTOM
        )
        val priorityChips = listOf(
            dialog.findViewById<MaterialButton>(R.id.subscription_priority_high_chip) to SubscriptionRepository.IMPORTANCE_HIGH,
            dialog.findViewById<MaterialButton>(R.id.subscription_priority_medium_chip) to SubscriptionRepository.IMPORTANCE_MEDIUM,
            dialog.findViewById<MaterialButton>(R.id.subscription_priority_low_chip) to SubscriptionRepository.IMPORTANCE_LOW,
            dialog.findViewById<MaterialButton>(R.id.subscription_priority_essential_chip) to SubscriptionRepository.IMPORTANCE_ESSENTIAL
        )
        val categories = latestState.categories
        var selectedCycle = when (existing?.subscription?.billingCycle) {
            SubscriptionRepository.CYCLE_EVERY_X_DAYS -> SubscriptionRepository.CYCLE_CUSTOM
            null -> SubscriptionRepository.CYCLE_MONTHLY
            else -> existing.subscription.billingCycle
        }
        var selectedImportance = existing?.subscription?.importance ?: SubscriptionRepository.IMPORTANCE_MEDIUM
        var selectedCategory = categories.firstOrNull { it.id == existing?.subscription?.categoryId }
        var selectedDue = existing?.subscription?.nextBillingDate
        category.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categories.map { it.name }))
        configureDropdown(category)
        name.setText(existing?.subscription?.name)
        amount.setText(existing?.subscription?.amount?.toBigDecimal()?.movePointLeft(2)?.stripTrailingZeros()?.toPlainString())
        interval.setText(existing?.subscription?.customIntervalDays?.toString())
        selectedDue?.let { due.setText(Formatters.date(it)) }
        notes.setText(existing?.subscription?.notes)
        category.setText(selectedCategory?.name.orEmpty(), false)
        reserve.isChecked = existing?.subscription?.reserveEnabled ?: false
        reminder.isChecked = existing?.subscription?.reminderEnabled ?: false
        active.isChecked = existing?.subscription?.isActive ?: true
        fun updateVisibility() {
            intervalLayout.visibility = if (selectedCycle == SubscriptionRepository.CYCLE_CUSTOM) View.VISIBLE else View.GONE
            cycleChips.forEach { (chip, value) -> styleChoiceChip(chip, value == selectedCycle) }
            priorityChips.forEach { (chip, value) -> stylePriorityChip(chip, value == selectedImportance, value) }
        }
        updateVisibility()
        cycleChips.forEach { (chip, value) ->
            chip.setOnClickListener {
                selectedCycle = value
                updateVisibility()
            }
        }
        priorityChips.forEach { (chip, value) ->
            chip.setOnClickListener {
                selectedImportance = value
                updateVisibility()
            }
        }
        category.setOnItemClickListener { _, _, pos, _ -> selectedCategory = categories.getOrNull(pos) }
        due.setOnClickListener {
            val cal = java.util.Calendar.getInstance()
            android.app.DatePickerDialog(requireContext(), { _, y, m, d ->
                selectedDue = java.util.Calendar.getInstance().apply { set(y, m, d, 12, 0, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
                due.setText(Formatters.date(selectedDue!!))
            }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }
        val sheet = showPlanSheet(dialog, R.id.subscription_dialog_scroll)
        dialog.findViewById<MaterialButton>(R.id.subscription_cancel_button).setOnClickListener { sheet.dismiss() }
        dialog.findViewById<MaterialButton>(R.id.subscription_save_button).setOnClickListener {
                subscriptionViewModel.save(
                    existing?.subscription?.id,
                    name.text.toString(),
                    amount.text.toString().toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: 0L,
                    selectedCategory?.id,
                    selectedCycle,
                    interval.text.toString().toIntOrNull(),
                    selectedDue,
                    selectedImportance,
                    reserve.isChecked,
                    reminder.isChecked,
                    notes.text.toString(),
                    active.isChecked,
                    subscriptionResultHandler("Subscription saved.")
                )
            sheet.dismiss()
        }
    }

    private fun renderPiggyBanks(view: View, items: List<com.example.kitatrack.data.local.model.PiggyBankProgress>) {
        val container = view.findViewById<LinearLayout>(R.id.piggy_list)
        val empty = view.findViewById<TextView>(R.id.piggy_empty_state)
        view.findViewById<TextView>(R.id.piggy_count_badge).text = items.size.toString()
        container.removeAllViews()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        items.forEach { item ->
            if (refreshedPiggyIds.add(item.id)) piggyViewModel.refreshMissed(item.id)
            val card = layoutInflater.inflate(R.layout.item_piggy_bank_card, container, false)
            val goalReached = isPiggyGoalReached(item)
            card.findViewById<TextView>(R.id.piggy_name).text = item.name
            card.findViewById<TextView>(R.id.piggy_status).apply {
                visibility = View.VISIBLE
                text = if (goalReached) "Goal reached" else item.statusLabel
                val needsAttention = !goalReached && (item.isOnTrack == false || item.unresolvedMissedCount > 0)
                tintChip(this, if (needsAttention) R.color.kitatrack_chip_yellow_background else R.color.kitatrack_chip_green_background, if (needsAttention) R.color.kitatrack_secondary_text else R.color.kitatrack_primary_green)
            }
            card.findViewById<TextView>(R.id.piggy_amounts).text = "${Formatters.peso(item.currentAmount)} saved of ${Formatters.peso(item.targetAmount)}"
            card.findViewById<TextView>(R.id.piggy_saved_value).text = Formatters.peso(item.currentAmount)
            card.findViewById<TextView>(R.id.piggy_saved_context).text = "saved of ${Formatters.peso(item.targetAmount)}"
            card.findViewById<TextView>(R.id.piggy_meta).text = "${Formatters.peso(item.remainingAmount)} remaining"
            card.findViewById<ProgressBar>(R.id.piggy_progress).apply {
                progress = item.progressPercent.coerceAtMost(100)
                progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), if (item.isOnTrack == false) R.color.kitatrack_warning_yellow else R.color.kitatrack_primary_green))
            }
            card.findViewById<TextView>(R.id.piggy_saved_chip).apply {
                visibility = View.VISIBLE
                text = "${item.progressPercent.coerceAtMost(100)}% saved"
            }
            card.findViewById<TextView>(R.id.piggy_allocation_chip).apply {
                visibility = View.VISIBLE
                text = "${item.selectedAllocationPercent}% allocation"
            }
            card.findViewById<TextView>(R.id.piggy_warning).apply {
                visibility = if (item.unresolvedMissedCount > 0) View.VISIBLE else View.GONE
                text = if (item.unresolvedMissedCount > 0) {
                    "${item.unresolvedMissedCount} contribution${if (item.unresolvedMissedCount == 1) "" else "s"} need adjustment"
                } else ""
            }
            card.findViewById<MaterialButton>(R.id.piggy_complete_button).apply {
                visibility = if (goalReached && item.isActive) View.VISIBLE else View.GONE
                setOnClickListener { showPiggyCompleteConfirmation(item) }
            }
            card.setOnClickListener { showPiggyDetails(item) }
            card.setOnLongClickListener {
                showPiggyActions(item)
                true
            }
            container.addView(card)
        }
    }

    private fun showPiggyDialog(existing: com.example.kitatrack.data.local.model.PiggyBankProgress?) {
        val dialog = layoutInflater.inflate(R.layout.dialog_piggy_bank, null, false)
        val name = dialog.findViewById<TextInputEditText>(R.id.piggy_name_input)
        val target = dialog.findViewById<TextInputEditText>(R.id.piggy_target_input)
        val weeklyIncome = dialog.findViewById<TextInputEditText>(R.id.piggy_weekly_income_input)
        val notes = dialog.findViewById<TextInputEditText>(R.id.piggy_notes_input)
        val targetDate = dialog.findViewById<TextInputEditText>(R.id.piggy_target_date_input)
        val planText = dialog.findViewById<TextView>(R.id.piggy_plan_text)
        val slider = dialog.findViewById<com.google.android.material.slider.Slider>(R.id.piggy_percent_slider)
        val selectedPercentText = dialog.findViewById<TextView>(R.id.piggy_selected_percent_text)
        val over = dialog.findViewById<SwitchMaterial>(R.id.piggy_over_save_switch)
        val active = dialog.findViewById<SwitchMaterial>(R.id.piggy_active_switch)
        dialog.findViewById<TextView>(R.id.piggy_sheet_title).text = if (existing == null) "Add Piggy Bank" else "Edit Piggy Bank"
        name.setText(existing?.name)
        target.setText(existing?.targetAmount?.toBigDecimal()?.movePointLeft(2)?.stripTrailingZeros()?.toPlainString())
        weeklyIncome.setText(existing?.weeklyIncomePrediction?.toBigDecimal()?.movePointLeft(2)?.stripTrailingZeros()?.toPlainString())
        notes.setText(existing?.notes)
        active.isChecked = existing?.isActive ?: true
        existing?.targetDate?.let { targetDate.setText(Formatters.date(it)) }
        var selectedTargetDate = existing?.targetDate
        lateinit var refreshPlan: () -> Unit
        targetDate.setOnClickListener {
            val cal = java.util.Calendar.getInstance()
            android.app.DatePickerDialog(requireContext(), { _, y, m, d ->
                selectedTargetDate = java.util.Calendar.getInstance().apply { set(y, m, d, 12, 0, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
                targetDate.setText(Formatters.date(selectedTargetDate!!))
                refreshPlan()
            }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }
        var selectedPercent = existing?.selectedAllocationPercent ?: 0
        refreshPlan = refresh@{
            val targetValue = target.text.toString().toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: 0L
            val weeklyValue = weeklyIncome.text.toString().toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: 0L
            val currentValue = existing?.currentAmount ?: 0L
            val plan = piggyViewModel.calculatePlan(targetValue, currentValue, weeklyValue, selectedTargetDate, existing?.id, piggyViewModel.piggyBanks.value)
            if (plan == null || !plan.isValid) {
                planText.text = "Complete the required fields to calculate this goal."
                slider.visibility = View.GONE
                selectedPercentText.visibility = View.GONE
                return@refresh
            }
            if (!plan.isPossible) {
                planText.text = plan.warning
                slider.visibility = View.GONE
                selectedPercentText.visibility = View.GONE
                return@refresh
            }
            selectedPercent = selectedPercent.coerceIn(plan.minPercent, plan.maxPercent)
            planText.text = "Required weekly savings: ${Formatters.peso(plan.requiredWeeklySaving)}\nValid allocation range: ${plan.minPercent}% to ${plan.maxPercent}%"
            slider.visibility = View.VISIBLE
            selectedPercentText.visibility = View.VISIBLE
            slider.valueFrom = plan.minPercent.toFloat()
            slider.valueTo = plan.maxPercent.toFloat().coerceAtLeast(plan.minPercent.toFloat())
            slider.value = selectedPercent.toFloat()
            selectedPercentText.text = "Selected allocation: $selectedPercent%\nEstimated weekly saving: ${Formatters.peso(weeklyValue * selectedPercent / 100)}"
        }
        slider.addOnChangeListener { _, value, _ ->
            selectedPercent = value.toInt()
            val weeklyValue = weeklyIncome.text.toString().toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: 0L
            selectedPercentText.text = "Selected allocation: $selectedPercent%\nEstimated weekly saving: ${Formatters.peso(weeklyValue * selectedPercent / 100)}"
        }
        listOf(target, weeklyIncome).forEach { field ->
            field.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = refreshPlan()
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        refreshPlan()
        val sheet = showPlanSheet(dialog, R.id.piggy_dialog_scroll)
        dialog.findViewById<MaterialButton>(R.id.piggy_cancel_button).setOnClickListener { sheet.dismiss() }
        dialog.findViewById<MaterialButton>(R.id.piggy_save_button).setOnClickListener {
                val targetValue = target.text.toString().toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: 0L
                val weeklyValue = weeklyIncome.text.toString().toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: 0L
                piggyViewModel.save(existing?.id, name.text.toString(), targetValue, existing?.currentAmount ?: 0L, weeklyValue, selectedPercent, selectedTargetDate, notes.text.toString(), over.isChecked, active.isChecked) {
                    it.onSuccess { Snackbar.make(requireView(), "Piggy Bank saved.", Snackbar.LENGTH_SHORT).show() }
                    it.onFailure { e -> Snackbar.make(requireView(), e.message ?: "Something went wrong. Try again.", Snackbar.LENGTH_LONG).show() }
                }
            sheet.dismiss()
        }
    }

    private fun isPiggyGoalReached(item: com.example.kitatrack.data.local.model.PiggyBankProgress): Boolean =
        item.targetAmount > 0L && item.currentAmount >= item.targetAmount

    private fun showPiggyDetails(item: com.example.kitatrack.data.local.model.PiggyBankProgress) {
        piggyViewModel.refreshMissed(item.id)
        val goalReached = isPiggyGoalReached(item)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_plan_detail, null, false)
        val sheet = showPlanSheet(view, null)
        view.findViewById<TextView>(R.id.detail_title).text = item.name
        view.findViewById<TextView>(R.id.detail_subtitle).text = "Piggy Bank"
        view.findViewById<TextView>(R.id.detail_status_badge).apply {
            text = if (goalReached) "Goal reached" else if (item.isOnTrack == false || item.unresolvedMissedCount > 0) "Behind" else "On track"
            val attention = !goalReached && (item.isOnTrack == false || item.unresolvedMissedCount > 0)
            tintChip(this, if (attention) R.color.kitatrack_chip_yellow_background else R.color.kitatrack_chip_green_background, if (attention) R.color.kitatrack_secondary_text else R.color.kitatrack_primary_green)
        }
        view.findViewById<TextView>(R.id.detail_main_amount).apply {
            text = Formatters.peso(item.currentAmount)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.kitatrack_primary_green))
        }
        view.findViewById<TextView>(R.id.detail_amount_context).text = "saved of ${Formatters.peso(item.targetAmount)}"
        view.findViewById<ProgressBar>(R.id.detail_progress).apply {
            progress = item.progressPercent.coerceAtMost(100)
            progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), if (item.isOnTrack == false) R.color.kitatrack_warning_yellow else R.color.kitatrack_primary_green))
        }
        view.findViewById<TextView>(R.id.detail_progress_caption).text = "${item.progressPercent.coerceAtMost(100)}% saved"
        val stats = view.findViewById<LinearLayout>(R.id.detail_stats_container)
        addStatRow(stats, "Remaining", Formatters.peso(item.remainingAmount), if (goalReached) R.color.kitatrack_primary_green else R.color.kitatrack_expense_red)
        addStatRow(stats, "Target date", item.targetDate?.let { Formatters.date(it) } ?: "No date")
        addStatRow(stats, "Days remaining", item.daysRemaining?.let { "$it days" } ?: "No date")
        addStatRow(stats, "Required weekly", item.requiredWeeklySaving?.let { Formatters.peso(it) } ?: "Not set")
        addStatRow(stats, "Current weekly", item.estimatedWeeklySavingAmount?.let { Formatters.peso(it) } ?: "Not set", R.color.kitatrack_primary_green)
        view.findViewById<MaterialButton>(R.id.detail_primary_button).apply {
            visibility = if (goalReached && item.isActive) View.VISIBLE else View.GONE
            text = "Complete Goal"
            setOnClickListener {
                sheet.dismiss()
                showPiggyCompleteConfirmation(item)
            }
        }
        view.findViewById<MaterialButton>(R.id.detail_secondary_button).apply {
            visibility = View.VISIBLE
            text = if (item.unresolvedMissedCount > 0) "Adjust Plan" else "Record No Money Week"
            setOnClickListener {
                sheet.dismiss()
                if (item.unresolvedMissedCount > 0) showPiggyAdjustmentDialog(item) else showMissedAllowanceDialog(item)
            }
        }
        view.findViewById<MaterialButton>(R.id.detail_edit_button).setOnClickListener {
            sheet.dismiss()
            showPiggyDialog(item)
        }
        view.findViewById<MaterialButton>(R.id.detail_close_button).setOnClickListener { sheet.dismiss() }
    }

    private fun showPiggyCompleteConfirmation(item: com.example.kitatrack.data.local.model.PiggyBankProgress) {
        if (!isPiggyGoalReached(item)) {
            Snackbar.make(requireView(), "Goal is not complete yet.", Snackbar.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Complete Piggy Bank Goal?")
            .setMessage(
                "You reached this savings goal. Completing it will move ${Formatters.peso(item.currentAmount)} from Piggy Bank reserve back to Main Balance so it is available to spend.\n\n" +
                    "This is not income and not an expense."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Move to Main Balance") { _, _ ->
                piggyViewModel.completeGoal(item.id) { result ->
                    result.onSuccess {
                        Snackbar.make(requireView(), "Savings moved to Main Balance.", Snackbar.LENGTH_SHORT).show()
                    }
                    result.onFailure { e ->
                        Snackbar.make(requireView(), e.message ?: "Something went wrong. Try again.", Snackbar.LENGTH_LONG).show()
                    }
                }
            }
            .show()
    }

    private fun showPiggyActions(item: com.example.kitatrack.data.local.model.PiggyBankProgress) {
        val actions = if (isPiggyGoalReached(item)) {
            arrayOf("Complete Goal", "Add money", "Remove money", "Adjust missed contribution", "Archive")
        } else {
            arrayOf("Add money", "Remove money", "Adjust missed contribution", "Archive")
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(item.name)
            .setItems(actions) { _, which ->
                when (actions[which]) {
                    "Complete Goal" -> showPiggyCompleteConfirmation(item)
                    "Add money" -> showPiggyAdjustDialog(item, true)
                    "Remove money" -> showPiggyAdjustDialog(item, false)
                    "Adjust missed contribution" -> showPiggyAdjustmentDialog(item)
                    "Archive" -> piggyViewModel.archive(item.id)
                }
            }.show()
    }

    private fun buildPiggyDetailMessage(item: com.example.kitatrack.data.local.model.PiggyBankProgress): String = buildString {
        append("${Formatters.peso(item.currentAmount)} saved of ${Formatters.peso(item.targetAmount)}\n")
        append("${Formatters.peso(item.remainingAmount)} remaining\n")
        item.targetDate?.let { append("Target: ${Formatters.date(it)}\n") }
        item.daysRemaining?.let { append("Days remaining: $it\n") }
        item.requiredWeeklySaving?.let { append("Required weekly savings: ${Formatters.peso(it)}\n") }
        item.estimatedWeeklySavingAmount?.let { append("Current weekly contribution: ${Formatters.peso(it)}\n") }
        if (item.unresolvedMissedCount > 0) {
            append("\nMissed Contribution Summary\n")
            append("You missed ${Formatters.peso(item.unresolvedMissedAmount)} in planned savings.\n")
            append("Affected goal: ${item.name}\n")
            append(projectionImpactText(item))
            append("This is only a planning gap. It is not an expense or debt.\n")
        }
        append("Status: ${item.statusLabel}")
    }

    private fun projectionImpactText(item: com.example.kitatrack.data.local.model.PiggyBankProgress): String {
        val remainingWeeks = remainingWeeks(item).coerceAtLeast(1)
        val extraPerWeek = kotlin.math.ceil(item.unresolvedMissedAmount / remainingWeeks.toDouble()).toLong()
        val weekly = item.estimatedWeeklySavingAmount ?: 0L
        val delayWeeks = if (weekly > 0) kotlin.math.ceil(item.unresolvedMissedAmount / weekly.toDouble()).toInt() else 0
        return "To stay on schedule, add ${Formatters.peso(extraPerWeek)} per week for the next $remainingWeeks weeks.\n" +
            if (delayWeeks > 0) "At your current pace, this goal may be delayed by $delayWeeks week${if (delayWeeks == 1) "" else "s"}.\n" else ""
    }

    private fun remainingWeeks(item: com.example.kitatrack.data.local.model.PiggyBankProgress): Int {
        val days = item.daysRemaining ?: 0L
        return kotlin.math.ceil(days.coerceAtLeast(1) / 7.0).toInt()
    }

    private fun showPiggyAdjustmentDialog(item: com.example.kitatrack.data.local.model.PiggyBankProgress) {
        if (item.unresolvedMissedCount <= 0) {
            Snackbar.make(requireView(), "No missed contributions.", Snackbar.LENGTH_SHORT).show()
            return
        }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 0)
        }
        val expected = item.estimatedWeeklySavingAmount ?: 0L
        val remainingWeeks = remainingWeeks(item).coerceAtLeast(1)
        val extraPerWeek = kotlin.math.ceil(item.unresolvedMissedAmount / remainingWeeks.toDouble()).toLong()
        val newWeeklyTarget = expected + extraPerWeek
        val delayWeeks = if (expected > 0) kotlin.math.ceil(item.unresolvedMissedAmount / expected.toDouble()).toInt() else 0
        val projectedDate = item.targetDate?.plus(java.util.concurrent.TimeUnit.DAYS.toMillis((delayWeeks * 7).toLong()))

        fun addText(textValue: String, color: Int = R.color.kitatrack_secondary_text, sizeSp: Float = 14f, bold: Boolean = false) {
            content.addView(TextView(requireContext()).apply {
                text = textValue
                textSize = sizeSp
                setTextColor(ContextCompat.getColor(requireContext(), color))
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, 6, 0, 6)
            })
        }
        fun addAction(title: String, description: String, detail: String, type: String) {
            content.addView(MaterialButton(requireContext()).apply {
                text = "$title\n$description\n$detail"
                isAllCaps = false
                textAlignment = View.TEXT_ALIGNMENT_TEXT_START
                setPadding(20, 14, 20, 14)
                setOnClickListener { applyPiggyAdjustment(item, type) }
            })
        }

        addText("Missed contribution summary", R.color.kitatrack_primary_text, 16f, true)
        addText("You missed ${Formatters.peso(item.unresolvedMissedAmount)} in planned savings. This does not change your Main Balance.")
        addText("Expected contribution: ${Formatters.peso(expected)}")
        addText("Affected piggy bank: ${item.name}")
        addText("Choose how KitaTrack should update the plan.", R.color.kitatrack_muted_text)
        addAction("Catch up gradually", "Keep your target date and slightly increase future contributions.", "Extra needed: ${Formatters.peso(extraPerWeek)} / week | New weekly target: ${Formatters.peso(newWeeklyTarget)}", com.example.kitatrack.data.repository.PiggyBankRepository.ADJUST_CATCH_UP)
        addAction("Extend deadline", "Keep your current contribution amount and move the projected completion date.", "Estimated delay: $delayWeeks week${if (delayWeeks == 1) "" else "s"}${projectedDate?.let { " | New date: ${Formatters.date(it)}" } ?: ""}", com.example.kitatrack.data.repository.PiggyBankRepository.ADJUST_EXTEND_DEADLINE)
        addAction("Skip this contribution", "Record this as missed and continue without catch-up.", "${item.unresolvedMissedCount} missed contribution${if (item.unresolvedMissedCount == 1) "" else "s"} will be marked as skipped.", com.example.kitatrack.data.repository.PiggyBankRepository.ADJUST_SKIP)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Adjust Piggy Bank Plan")
            .setView(content)
            .setNegativeButton("Close", null)
            .show()
    }

    private fun applyPiggyAdjustment(item: com.example.kitatrack.data.local.model.PiggyBankProgress, type: String) {
        piggyViewModel.applyAdjustment(item.id, type) { result ->
            result.onSuccess { Snackbar.make(requireView(), "Piggy Bank updated.", Snackbar.LENGTH_SHORT).show() }
            result.onFailure { e -> Snackbar.make(requireView(), e.message ?: "Something went wrong. Try again.", Snackbar.LENGTH_LONG).show() }
        }
    }

    private fun showMissedAllowanceDialog(item: com.example.kitatrack.data.local.model.PiggyBankProgress) {
        val expected = item.estimatedWeeklySavingAmount ?: 0L
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 0)
        }
        container.addView(TextView(requireContext()).apply {
            text = "If you had no allowance this week, leave this at PHP 0. This records a planning gap only; it does not create income or reduce Main Balance.\n\nExpected contribution: ${Formatters.peso(expected)}\nAffected piggy bank: ${item.name}"
            setTextColor(ContextCompat.getColor(requireContext(), R.color.kitatrack_secondary_text))
            textSize = 14f
        })
        val actual = TextInputEditText(requireContext()).apply {
            hint = "Actual contribution this week"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText("0")
        }
        container.addView(actual)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Record No Money Week")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val amount = actual.text.toString().toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: 0L
                piggyViewModel.recordNoIncomeWeek(item.id, com.example.kitatrack.util.DateRanges.currentWeek().first, amount) { result ->
                    result.onSuccess { Snackbar.make(requireView(), "Planning gap saved.", Snackbar.LENGTH_SHORT).show() }
                    result.onFailure { e -> Snackbar.make(requireView(), e.message ?: "Something went wrong. Try again.", Snackbar.LENGTH_LONG).show() }
                }
            }.show()
    }
    private fun showPiggyAdjustDialog(item: com.example.kitatrack.data.local.model.PiggyBankProgress, add: Boolean) {
        val input = TextInputEditText(requireContext()).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (add) "Add money" else "Remove money")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(if (add) "Add" else "Remove") { _, _ ->
                val amount = input.text.toString().toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: 0L
                piggyViewModel.adjust(item.id, amount, add) { result ->
                    result.onSuccess { Snackbar.make(requireView(), "Piggy Bank updated.", Snackbar.LENGTH_SHORT).show() }
                    result.onFailure { e -> Snackbar.make(requireView(), e.message ?: "Something went wrong. Try again.", Snackbar.LENGTH_LONG).show() }
                }
            }.show()
    }

    private fun renderBudgets(view: View, budgets: List<BudgetProgress>) {
        val container = view.findViewById<LinearLayout>(R.id.budget_list)
        val empty = view.findViewById<TextView>(R.id.budget_empty_state)
        view.findViewById<TextView>(R.id.budget_count_badge).text = budgets.size.toString()
        container.removeAllViews()
        empty.visibility = if (budgets.isEmpty()) View.VISIBLE else View.GONE
        budgets.forEach { budget ->
            val card = layoutInflater.inflate(R.layout.item_budget_card, container, false)
            card.findViewById<ImageView>(R.id.budget_category_icon)
                .setImageResource(budget.categoryName?.let { CategoryIconMapper.expenseIconFor(it) } ?: R.drawable.ic_plans_budget)
            card.findViewById<TextView>(R.id.budget_name).text = budget.name
            card.findViewById<TextView>(R.id.budget_meta).text =
                "${budget.periodLabel} - ${budget.categoryName ?: "Overall"}${if (!budget.isActive) " - Inactive" else ""}"
            card.findViewById<TextView>(R.id.budget_amounts).text =
                if (budget.remainingAmount >= 0) {
                    ""
                } else {
                    ""
                }
            card.findViewById<TextView>(R.id.budget_status).text = when {
                !budget.isActive -> "Inactive"
                budget.adjustedLimitAmount <= 0L && budget.reserveImpactAmount > 0L -> budget.periodLabel
                budget.isOverLimit -> "Over budget"
                budget.isNearLimit -> "Near limit"
                budget.reserveImpactAmount > 0L -> budget.periodLabel
                else -> "On track"
            }
            card.findViewById<TextView>(R.id.budget_meta).text = budget.categoryName ?: "Overall"
            card.findViewById<TextView>(R.id.budget_left).apply {
                text = if (budget.remainingAmount >= 0) "${Formatters.peso(budget.remainingAmount)} left" else "${Formatters.peso(-budget.remainingAmount)} over"
                setTextColor(ContextCompat.getColor(requireContext(), if (budget.remainingAmount >= 0) R.color.kitatrack_primary_text else R.color.kitatrack_expense_red))
            }
            card.findViewById<TextView>(R.id.budget_used).text =
                "${Formatters.peso(budget.usedAmount)} of ${Formatters.peso(budget.adjustedLimitAmount)}"
            card.findViewById<TextView>(R.id.budget_reserved_value).text = Formatters.peso(budget.reserveImpactAmount)
            card.findViewById<TextView>(R.id.budget_status).text = budget.periodLabel
            card.findViewById<ProgressBar>(R.id.budget_progress).apply {
                progress = budget.usagePercent.coerceAtMost(100)
                progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), when {
                    budget.isOverLimit -> R.color.kitatrack_expense_red
                    budget.isNearLimit -> R.color.kitatrack_warning_yellow
                    else -> R.color.kitatrack_primary_green
                }))
            }
            card.setOnClickListener { showBudgetDetails(budget) }
            card.setOnLongClickListener {
                MaterialAlertDialogBuilder(requireContext()).setTitle("Delete budget?")
                    .setMessage("This deletes the budget only. Transactions will stay unchanged.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ -> viewModel.delete(budget.budgetId) }.show()
                true
            }
            container.addView(card)
        }
    }

    private fun showBudgetDetails(budget: BudgetProgress) {
        val view = layoutInflater.inflate(R.layout.bottom_sheet_plan_detail, null, false)
        val sheet = showPlanSheet(view, null)
        val status = budgetStatusLabel(budget)
        val danger = budget.isOverLimit
        view.findViewById<TextView>(R.id.detail_title).text = budget.name
        view.findViewById<TextView>(R.id.detail_subtitle).text = "${budget.periodLabel} - ${budget.categoryName ?: "Overall"}"
        view.findViewById<TextView>(R.id.detail_status_badge).apply {
            text = status
            tintChip(this, when {
                budget.isOverLimit -> R.color.kitatrack_chip_red_background
                budget.isNearLimit -> R.color.kitatrack_chip_yellow_background
                else -> R.color.kitatrack_chip_green_background
            }, when {
                budget.isOverLimit -> R.color.kitatrack_expense_red
                budget.isNearLimit -> R.color.kitatrack_secondary_text
                else -> R.color.kitatrack_primary_green
            })
        }
        view.findViewById<TextView>(R.id.detail_main_amount).apply {
            text = Formatters.peso(budget.usedAmount)
            setTextColor(ContextCompat.getColor(requireContext(), if (danger) R.color.kitatrack_expense_red else R.color.kitatrack_primary_text))
        }
        view.findViewById<TextView>(R.id.detail_amount_context).text = "used of ${Formatters.peso(budget.adjustedLimitAmount)}"
        view.findViewById<ProgressBar>(R.id.detail_progress).apply {
            progress = budget.usagePercent.coerceAtMost(100)
            progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), when {
                budget.isOverLimit -> R.color.kitatrack_expense_red
                budget.isNearLimit -> R.color.kitatrack_warning_yellow
                else -> R.color.kitatrack_primary_green
            }))
        }
        view.findViewById<TextView>(R.id.detail_progress_caption).text =
            if (budget.remainingAmount >= 0) "${Formatters.peso(budget.remainingAmount)} remaining" else "${Formatters.peso(-budget.remainingAmount)} over budget"
        val stats = view.findViewById<LinearLayout>(R.id.detail_stats_container)
        addStatRow(stats, "Budget limit", Formatters.peso(budget.adjustedLimitAmount))
        addStatRow(stats, "Used", Formatters.peso(budget.usedAmount), if (danger) R.color.kitatrack_expense_red else R.color.kitatrack_primary_text)
        addStatRow(stats, "Remaining", if (budget.remainingAmount >= 0) Formatters.peso(budget.remainingAmount) else "-${Formatters.peso(-budget.remainingAmount)}", if (danger) R.color.kitatrack_expense_red else R.color.kitatrack_primary_green)
        addStatRow(stats, "Period", budget.periodLabel)
        budget.categoryName?.let { addStatRow(stats, "Category", it) }
        view.findViewById<MaterialButton>(R.id.detail_primary_button).visibility = View.GONE
        view.findViewById<MaterialButton>(R.id.detail_secondary_button).visibility = View.GONE
        view.findViewById<MaterialButton>(R.id.detail_edit_button).setOnClickListener {
            sheet.dismiss()
            showBudgetDialog(budget)
        }
        view.findViewById<MaterialButton>(R.id.detail_close_button).setOnClickListener { sheet.dismiss() }
    }

    private fun budgetStatusLabel(budget: BudgetProgress): String = when {
        !budget.isActive -> "Inactive"
        budget.isOverLimit -> "Over budget"
        budget.isNearLimit -> "Near limit"
        else -> "On track"
    }

    private fun showBudgetDialog(existing: BudgetProgress?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_budget, null, false)
        val name = dialogView.findViewById<TextInputEditText>(R.id.budget_name_input)
        val amount = dialogView.findViewById<TextInputEditText>(R.id.budget_amount_input)
        val category = dialogView.findViewById<AutoCompleteTextView>(R.id.budget_category_input)
        val active = dialogView.findViewById<SwitchMaterial>(R.id.budget_active_switch)
        dialogView.findViewById<TextView>(R.id.budget_sheet_title).text = if (existing == null) "Add Budget" else "Edit Budget"
        val weeklyChip = dialogView.findViewById<MaterialButton>(R.id.budget_weekly_chip)
        val monthlyChip = dialogView.findViewById<MaterialButton>(R.id.budget_monthly_chip)
        val budgetCategories = latestState.categories
        val categoryLabels = listOf("Overall") + budgetCategories.map { it.name }
        category.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categoryLabels))
        configureDropdown(category)
        var selectedPeriod = when (existing?.budgetType) {
            BudgetRepository.TYPE_MONTHLY_OVERALL, BudgetRepository.TYPE_CATEGORY_MONTHLY -> BudgetRepository.TYPE_MONTHLY_OVERALL
            else -> BudgetRepository.TYPE_WEEKLY_OVERALL
        }
        var selectedCategory = budgetCategories.firstOrNull { it.name == existing?.categoryName }
        name.setText(existing?.name)
        amount.setText(existing?.limitAmount?.toBigDecimal()?.movePointLeft(2)?.stripTrailingZeros()?.toPlainString())
        category.setText(selectedCategory?.name ?: "Overall", false)
        active.isChecked = existing?.isActive ?: true
        fun selectedBudgetType(): String {
            return when {
                selectedPeriod == BudgetRepository.TYPE_MONTHLY_OVERALL && selectedCategory != null -> BudgetRepository.TYPE_CATEGORY_MONTHLY
                selectedPeriod == BudgetRepository.TYPE_MONTHLY_OVERALL -> BudgetRepository.TYPE_MONTHLY_OVERALL
                selectedCategory != null -> BudgetRepository.TYPE_CATEGORY_WEEKLY
                else -> BudgetRepository.TYPE_WEEKLY_OVERALL
            }
        }
        fun updatePeriodChips() {
            styleChoiceChip(weeklyChip, selectedPeriod == BudgetRepository.TYPE_WEEKLY_OVERALL)
            styleChoiceChip(monthlyChip, selectedPeriod == BudgetRepository.TYPE_MONTHLY_OVERALL)
        }
        updatePeriodChips()
        weeklyChip.setOnClickListener {
            selectedPeriod = BudgetRepository.TYPE_WEEKLY_OVERALL
            updatePeriodChips()
        }
        monthlyChip.setOnClickListener {
            selectedPeriod = BudgetRepository.TYPE_MONTHLY_OVERALL
            updatePeriodChips()
        }
        category.setOnClickListener {
            if (budgetCategories.isEmpty()) {
                Snackbar.make(requireView(), "No expense categories found.", Snackbar.LENGTH_SHORT).show()
            } else {
                category.post { category.showDropDown() }
            }
        }
        category.setOnItemClickListener { _, _, pos, _ ->
            selectedCategory = if (pos == 0) null else budgetCategories.getOrNull(pos - 1)
        }
        val sheet = showPlanSheet(dialogView, null)
        dialogView.findViewById<MaterialButton>(R.id.budget_cancel_button).setOnClickListener { sheet.dismiss() }
        dialogView.findViewById<MaterialButton>(R.id.budget_save_button).setOnClickListener {
            viewModel.save(existing?.budgetId, name.text.toString(), selectedBudgetType(), amount.text.toString(), selectedCategory?.id, active.isChecked) {
                it.onSuccess { Snackbar.make(requireView(), "Budget saved.", Snackbar.LENGTH_SHORT).show() }
                it.onFailure { e -> Snackbar.make(requireView(), e.message ?: "Something went wrong. Try again.", Snackbar.LENGTH_LONG).show() }
            }
            sheet.dismiss()
        }
    }
}

