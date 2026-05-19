package com.example.kitatrack.ui.plans

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
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
import com.example.kitatrack.util.Formatters
import com.google.android.material.button.MaterialButton
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

    private fun renderDebts(view: View, items: List<DebtProgress>) {
        val container = view.findViewById<LinearLayout>(R.id.debt_list)
        val empty = view.findViewById<TextView>(R.id.debt_empty_state)
        container.removeAllViews()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        items.sortedWith(compareByDescending<DebtProgress> { it.isOverdue }.thenByDescending { it.isUpcoming }.thenBy { it.debt.nextDueDate ?: Long.MAX_VALUE }).forEach { item ->
            val debt = item.debt
            val card = layoutInflater.inflate(R.layout.item_debt_card, container, false)
            card.findViewById<TextView>(R.id.debt_name).text = debt.name
            card.findViewById<TextView>(R.id.debt_meta).text =
                "${if (debt.debtType == DebtRepository.TYPE_I_OWE) "Money I owe" else "Owed to me"} · ${debt.personName ?: "No person"}"
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
                append(" · Due ${item.dueLabel}")
                debt.installmentAmount?.let { append(" · ${Formatters.peso(it)} payment") }
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
        val message = buildString {
            append("${if (d.debtType == DebtRepository.TYPE_I_OWE) "Money I owe" else "Money owed to me"}\n")
            d.personName?.let { append("Person: $it\n") }
            append("Total: ${Formatters.peso(d.totalAmount)}\n")
            append("Paid: ${Formatters.peso(d.amountPaid)}\n")
            append("Remaining: ${Formatters.peso(d.remainingAmount)}\n")
            if (d.debtType == DebtRepository.TYPE_I_OWE) append("Debt Reserve: ${Formatters.peso(d.reservedAmount)}\n")
            d.installmentAmount?.let { append("Payment amount: ${Formatters.peso(it)}\n") }
            append("Frequency: ${d.paymentFrequency.replace("_", " ")}\n")
            append("Next due: ${item.dueLabel}\n")
            append("Status: ${item.statusLabel}")
        }
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(d.name)
            .setMessage(message)
            .setNegativeButton("Close", null)
        if (isPaid) {
            builder.setPositiveButton("Archive") { _, _ -> debtViewModel.archive(d.id) }
        } else {
            builder
                .setNeutralButton("Pay") { _, _ -> showDebtPaymentDialog(item) }
                .setPositiveButton("Edit") { _, _ -> showDebtDialog(item) }
        }
        builder.show()
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
                        2 -> showDebtAmountDialog(item, "Add to Debt Reserve") { amount -> debtViewModel.reserve(debt.id, amount, true, debtResultHandler()) }
                        3 -> showDebtAmountDialog(item, "Remove from Debt Reserve") { amount -> debtViewModel.reserve(debt.id, amount, false, debtResultHandler()) }
                        4 -> debtViewModel.archive(debt.id)
                    }
                } else {
                    when (which) {
                        0 -> showDebtAmountDialog(item, "Payment received") { amount -> debtViewModel.payment(debt.id, amount, false, debtResultHandler()) }
                        1 -> debtViewModel.archive(debt.id)
                    }
                }
            }.show()
    }

    private fun showDebtPaymentDialog(item: DebtProgress, preferReserve: Boolean = true) {
        val debt = item.debt
        val dueAmount = paymentDueForThisTerm(debt)
        if (dueAmount <= 0L) {
            Snackbar.make(requireView(), "This debt is already fully paid.", Snackbar.LENGTH_LONG).show()
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
                debtViewModel.payment(debt.id, dueAmount, fromReserve, debtResultHandler())
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
                debtViewModel.payment(item.debt.id, amount, fromReserve, debtResultHandler())
            }
            .show()
    }

    private fun paymentDueForThisTerm(debt: com.example.kitatrack.data.local.entity.DebtEntity): Long {
        val remaining = debt.remainingAmount.coerceAtLeast(0)
        val scheduled = debt.installmentAmount?.takeIf { it > 0 } ?: remaining
        return minOf(remaining, scheduled)
    }

    private fun debtResultHandler(): (Result<Unit>) -> Unit = { result ->
        result.onSuccess {
            Snackbar.make(requireView(), "Debt updated.", Snackbar.LENGTH_LONG).show()
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) { app.reminderRepository.rescheduleAll() }
        }
        result.onFailure { e -> Snackbar.make(requireView(), e.message ?: "Debt could not be updated.", Snackbar.LENGTH_LONG).show() }
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
        val type = dialog.findViewById<AutoCompleteTextView>(R.id.debt_type_input)
        val total = dialog.findViewById<TextInputEditText>(R.id.debt_total_input)
        val paid = dialog.findViewById<TextInputEditText>(R.id.debt_paid_input)
        val installment = dialog.findViewById<TextInputEditText>(R.id.debt_installment_input)
        val frequency = dialog.findViewById<AutoCompleteTextView>(R.id.debt_frequency_input)
        val intervalLayout = dialog.findViewById<TextInputLayout>(R.id.debt_interval_layout)
        val interval = dialog.findViewById<TextInputEditText>(R.id.debt_interval_input)
        val due = dialog.findViewById<TextInputEditText>(R.id.debt_due_input)
        val notes = dialog.findViewById<TextInputEditText>(R.id.debt_notes_input)
        val autoReserve = dialog.findViewById<SwitchMaterial>(R.id.debt_auto_reserve_switch)
        val reminder = dialog.findViewById<SwitchMaterial>(R.id.debt_reminder_switch)
        val active = dialog.findViewById<SwitchMaterial>(R.id.debt_active_switch)
        val typeLabels = listOf("Money I owe", "Money others owe me")
        val typeValues = listOf(DebtRepository.TYPE_I_OWE, DebtRepository.TYPE_OWED_TO_ME)
        val freqLabels = listOf("One-time", "Daily", "Weekly", "Bi-weekly", "Monthly", "Every X days", "Custom")
        val freqValues = listOf(DebtRepository.FREQ_ONE_TIME, DebtRepository.FREQ_DAILY, DebtRepository.FREQ_WEEKLY, DebtRepository.FREQ_BI_WEEKLY, DebtRepository.FREQ_MONTHLY, DebtRepository.FREQ_EVERY_X_DAYS, DebtRepository.FREQ_CUSTOM)
        var selectedType = existing?.debt?.debtType ?: DebtRepository.TYPE_I_OWE
        var selectedFreq = existing?.debt?.paymentFrequency ?: DebtRepository.FREQ_ONE_TIME
        var selectedDue = existing?.debt?.nextDueDate ?: existing?.debt?.dueDate
        type.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, typeLabels))
        frequency.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, freqLabels))
        configureDropdown(type)
        configureDropdown(frequency)
        name.setText(existing?.debt?.name)
        person.setText(existing?.debt?.personName)
        total.setText(existing?.debt?.totalAmount?.toBigDecimal()?.movePointLeft(2)?.stripTrailingZeros()?.toPlainString())
        paid.setText(existing?.debt?.amountPaid?.toBigDecimal()?.movePointLeft(2)?.stripTrailingZeros()?.toPlainString() ?: "0")
        installment.setText(existing?.debt?.installmentAmount?.toBigDecimal()?.movePointLeft(2)?.stripTrailingZeros()?.toPlainString())
        interval.setText(existing?.debt?.customIntervalDays?.toString())
        selectedDue?.let { due.setText(Formatters.date(it)) }
        notes.setText(existing?.debt?.notes)
        type.setText(typeLabels[typeValues.indexOf(selectedType).coerceAtLeast(0)], false)
        frequency.setText(freqLabels[freqValues.indexOf(selectedFreq).coerceAtLeast(0)], false)
        autoReserve.isChecked = existing?.debt?.autoReserveEnabled ?: true
        reminder.isChecked = existing?.debt?.reminderEnabled ?: false
        active.isChecked = existing?.debt?.isActive ?: true
        fun updateVisibility() {
            intervalLayout.visibility = if (selectedFreq in setOf(DebtRepository.FREQ_EVERY_X_DAYS, DebtRepository.FREQ_CUSTOM)) View.VISIBLE else View.GONE
            autoReserve.visibility = if (selectedType == DebtRepository.TYPE_I_OWE) View.VISIBLE else View.GONE
        }
        updateVisibility()
        type.setOnItemClickListener { _, _, pos, _ -> selectedType = typeValues[pos]; updateVisibility() }
        frequency.setOnItemClickListener { _, _, pos, _ -> selectedFreq = freqValues[pos]; updateVisibility() }
        due.setOnClickListener {
            val cal = java.util.Calendar.getInstance()
            android.app.DatePickerDialog(requireContext(), { _, y, m, d ->
                selectedDue = java.util.Calendar.getInstance().apply { set(y, m, d, 12, 0, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
                due.setText(Formatters.date(selectedDue!!))
            }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }
        val alert = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) "Add Debt" else "Edit Debt")
            .setView(dialog)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
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
                    debtResultHandler()
                )
            }.create()
        alert.setOnShowListener {
            dialog.findViewById<androidx.core.widget.NestedScrollView>(R.id.debt_dialog_scroll)?.let { scroll ->
                scroll.layoutParams = scroll.layoutParams.apply { height = (resources.displayMetrics.heightPixels * 0.62f).toInt() }
            }
        }
        alert.show()
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

    private fun renderSubscriptions(view: View, items: List<SubscriptionProgress>) {
        val container = view.findViewById<LinearLayout>(R.id.subscription_list)
        val empty = view.findViewById<TextView>(R.id.subscription_empty_state)
        container.removeAllViews()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        items.sortedWith(compareByDescending<SubscriptionProgress> { it.isOverdue }.thenByDescending { it.isUpcoming }.thenBy { it.subscription.nextBillingDate ?: Long.MAX_VALUE }).forEach { item ->
            val sub = item.subscription
            val card = layoutInflater.inflate(R.layout.item_subscription_card, container, false)
            card.findViewById<TextView>(R.id.subscription_name).text = sub.name
            card.findViewById<TextView>(R.id.subscription_amount).text = Formatters.peso(sub.amount)
            card.findViewById<TextView>(R.id.subscription_meta).text =
                "${item.cycleLabel} | Next billing: ${item.dueLabel} | ${sub.importance.lowercase().replaceFirstChar { it.uppercase() }}"
            card.findViewById<ProgressBar>(R.id.subscription_reserve_progress).apply {
                visibility = if (sub.reserveEnabled) View.VISIBLE else View.GONE
                progress = item.reservePercent
                progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), when {
                    item.isOverdue -> R.color.kitatrack_expense_red
                    item.isUpcoming && !item.isFunded -> R.color.kitatrack_warning_yellow
                    else -> R.color.kitatrack_primary_green
                }))
            }
            card.findViewById<TextView>(R.id.subscription_status).text =
                if (sub.reserveEnabled) "Reserve On | ${Formatters.peso(sub.reservedAmount)} reserved | ${item.statusLabel}"
                else "Reserve Off | Reminder ${if (sub.reminderEnabled) "On" else "Off"} | ${item.statusLabel}"
            card.setOnClickListener { showSubscriptionDetails(item) }
            container.addView(card)
        }
    }

    private fun showSubscriptionDetails(item: SubscriptionProgress) {
        val sub = item.subscription
        val message = buildString {
            append("${Formatters.peso(sub.amount)} | ${item.cycleLabel}\n")
            append("Next billing: ${item.dueLabel}\n")
            append("Reserve: ${if (sub.reserveEnabled) "On (${Formatters.peso(sub.reservedAmount)} saved)" else "Off"}\n")
            append("Reminder: ${if (sub.reminderEnabled) "On" else "Off"}\n")
            append("Importance: ${sub.importance}\n")
            append("Status: ${item.statusLabel}")
            sub.notes?.let { append("\n\n$it") }
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(sub.name)
            .setMessage(message)
            .setNegativeButton("Close", null)
            .setNeutralButton("Actions") { _, _ -> showSubscriptionActions(item) }
            .setPositiveButton("Edit") { _, _ -> showSubscriptionDialog(item) }
            .show()
    }

    private fun showSubscriptionActions(item: SubscriptionProgress) {
        val sub = item.subscription
        val actions = if (sub.reserveEnabled) {
            arrayOf("Mark paid from reserve", "Mark paid from Main Balance", "Add reserve", "Remove reserve", "Archive")
        } else {
            arrayOf("Mark paid from Main Balance", "Archive")
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(sub.name)
            .setItems(actions) { _, index ->
                when (actions[index]) {
                    "Mark paid from reserve" -> showAmountDialog("Payment amount", sub.amount) { amount ->
                        subscriptionViewModel.payment(sub.id, amount, true, subscriptionResultHandler())
                    }
                    "Mark paid from Main Balance" -> showAmountDialog("Payment amount", sub.amount) { amount ->
                        subscriptionViewModel.payment(sub.id, amount, false, subscriptionResultHandler())
                    }
                    "Add reserve" -> showAmountDialog("Amount to reserve") { amount ->
                        subscriptionViewModel.reserve(sub.id, amount, true, subscriptionResultHandler())
                    }
                    "Remove reserve" -> showAmountDialog("Amount to remove from reserve") { amount ->
                        subscriptionViewModel.reserve(sub.id, amount, false, subscriptionResultHandler())
                    }
                    "Archive" -> subscriptionViewModel.archive(sub.id)
                }
            }.show()
    }

    private fun subscriptionResultHandler(): (Result<Unit>) -> Unit = { result ->
        result.fold(
            onSuccess = { view?.let { Snackbar.make(it, "Subscription updated.", Snackbar.LENGTH_SHORT).show() } },
            onFailure = { error -> view?.let { Snackbar.make(it, error.message ?: "Subscription action failed.", Snackbar.LENGTH_LONG).show() } }
        )
    }

    private fun showSubscriptionDialog(existing: SubscriptionProgress?) {
        val dialog = layoutInflater.inflate(R.layout.dialog_subscription, null, false)
        val name = dialog.findViewById<TextInputEditText>(R.id.subscription_name_input)
        val amount = dialog.findViewById<TextInputEditText>(R.id.subscription_amount_input)
        val cycle = dialog.findViewById<AutoCompleteTextView>(R.id.subscription_cycle_input)
        val intervalLayout = dialog.findViewById<TextInputLayout>(R.id.subscription_interval_layout)
        val interval = dialog.findViewById<TextInputEditText>(R.id.subscription_interval_input)
        val due = dialog.findViewById<TextInputEditText>(R.id.subscription_due_input)
        val category = dialog.findViewById<AutoCompleteTextView>(R.id.subscription_category_input)
        val importance = dialog.findViewById<AutoCompleteTextView>(R.id.subscription_importance_input)
        val notes = dialog.findViewById<TextInputEditText>(R.id.subscription_notes_input)
        val reserve = dialog.findViewById<SwitchMaterial>(R.id.subscription_reserve_switch)
        val reminder = dialog.findViewById<SwitchMaterial>(R.id.subscription_reminder_switch)
        val active = dialog.findViewById<SwitchMaterial>(R.id.subscription_active_switch)
        val cycleLabels = listOf("Weekly", "Monthly", "Yearly", "Every X days", "Custom")
        val cycleValues = listOf(SubscriptionRepository.CYCLE_WEEKLY, SubscriptionRepository.CYCLE_MONTHLY, SubscriptionRepository.CYCLE_YEARLY, SubscriptionRepository.CYCLE_EVERY_X_DAYS, SubscriptionRepository.CYCLE_CUSTOM)
        val importanceLabels = listOf("Low", "Medium", "High", "Essential")
        val importanceValues = listOf(SubscriptionRepository.IMPORTANCE_LOW, SubscriptionRepository.IMPORTANCE_MEDIUM, SubscriptionRepository.IMPORTANCE_HIGH, SubscriptionRepository.IMPORTANCE_ESSENTIAL)
        val categories = latestState.categories
        var selectedCycle = existing?.subscription?.billingCycle ?: SubscriptionRepository.CYCLE_MONTHLY
        var selectedImportance = existing?.subscription?.importance ?: SubscriptionRepository.IMPORTANCE_MEDIUM
        var selectedCategory = categories.firstOrNull { it.id == existing?.subscription?.categoryId }
        var selectedDue = existing?.subscription?.nextBillingDate
        cycle.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, cycleLabels))
        importance.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, importanceLabels))
        category.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categories.map { it.name }))
        configureDropdown(cycle); configureDropdown(importance); configureDropdown(category)
        name.setText(existing?.subscription?.name)
        amount.setText(existing?.subscription?.amount?.toBigDecimal()?.movePointLeft(2)?.stripTrailingZeros()?.toPlainString())
        interval.setText(existing?.subscription?.customIntervalDays?.toString())
        selectedDue?.let { due.setText(Formatters.date(it)) }
        notes.setText(existing?.subscription?.notes)
        cycle.setText(cycleLabels[cycleValues.indexOf(selectedCycle).coerceAtLeast(0)], false)
        importance.setText(importanceLabels[importanceValues.indexOf(selectedImportance).coerceAtLeast(0)], false)
        category.setText(selectedCategory?.name.orEmpty(), false)
        reserve.isChecked = existing?.subscription?.reserveEnabled ?: false
        reminder.isChecked = existing?.subscription?.reminderEnabled ?: false
        active.isChecked = existing?.subscription?.isActive ?: true
        fun updateVisibility() {
            intervalLayout.visibility = if (selectedCycle in setOf(SubscriptionRepository.CYCLE_EVERY_X_DAYS, SubscriptionRepository.CYCLE_CUSTOM)) View.VISIBLE else View.GONE
        }
        updateVisibility()
        cycle.setOnItemClickListener { _, _, pos, _ -> selectedCycle = cycleValues[pos]; updateVisibility() }
        importance.setOnItemClickListener { _, _, pos, _ -> selectedImportance = importanceValues[pos] }
        category.setOnItemClickListener { _, _, pos, _ -> selectedCategory = categories.getOrNull(pos) }
        due.setOnClickListener {
            val cal = java.util.Calendar.getInstance()
            android.app.DatePickerDialog(requireContext(), { _, y, m, d ->
                selectedDue = java.util.Calendar.getInstance().apply { set(y, m, d, 12, 0, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
                due.setText(Formatters.date(selectedDue!!))
            }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }
        val alert = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) "Add Subscription" else "Edit Subscription")
            .setView(dialog)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
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
                    subscriptionResultHandler()
                )
            }.create()
        alert.setOnShowListener {
            dialog.findViewById<androidx.core.widget.NestedScrollView>(R.id.subscription_dialog_scroll)?.let { scroll ->
                scroll.layoutParams = scroll.layoutParams.apply { height = (resources.displayMetrics.heightPixels * 0.62f).toInt() }
            }
        }
        alert.show()
    }

    private fun renderPiggyBanks(view: View, items: List<com.example.kitatrack.data.local.model.PiggyBankProgress>) {
        val container = view.findViewById<LinearLayout>(R.id.piggy_list)
        val empty = view.findViewById<TextView>(R.id.piggy_empty_state)
        container.removeAllViews()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        items.forEach { item ->
            if (refreshedPiggyIds.add(item.id)) piggyViewModel.refreshMissed(item.id)
            val card = layoutInflater.inflate(R.layout.item_piggy_bank_card, container, false)
            card.findViewById<TextView>(R.id.piggy_name).text = item.name
            card.findViewById<TextView>(R.id.piggy_amounts).text = "${Formatters.peso(item.currentAmount)} / ${Formatters.peso(item.targetAmount)}\n${Formatters.peso(item.remainingAmount)} remaining"
            card.findViewById<ProgressBar>(R.id.piggy_progress).progress = item.progressPercent.coerceAtMost(100)
            card.findViewById<TextView>(R.id.piggy_meta).text = "Progress: ${item.progressPercent}% | Allocation: ${item.selectedAllocationPercent}% | ${item.statusLabel}"
            card.findViewById<TextView>(R.id.piggy_warning).apply {
                visibility = if (item.unresolvedMissedCount > 0) View.VISIBLE else View.GONE
                text = if (item.unresolvedMissedCount > 0) {
                    "${item.unresolvedMissedCount} planned contribution${if (item.unresolvedMissedCount == 1) "" else "s"} need adjustment | ${Formatters.peso(item.unresolvedMissedAmount)} missed"
                } else ""
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
        val alertDialog = MaterialAlertDialogBuilder(requireContext()).setTitle(if (existing == null) "Add Piggy Bank" else "Edit Piggy Bank")
            .setView(dialog).setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val targetValue = target.text.toString().toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: 0L
                val weeklyValue = weeklyIncome.text.toString().toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: 0L
                piggyViewModel.save(existing?.id, name.text.toString(), targetValue, existing?.currentAmount ?: 0L, weeklyValue, selectedPercent, selectedTargetDate, notes.text.toString(), over.isChecked, active.isChecked) {
                    it.onFailure { e -> Snackbar.make(requireView(), e.message ?: "Piggy bank could not be saved.", Snackbar.LENGTH_LONG).show() }
                }
            }.create()
        alertDialog.setOnShowListener {
            dialog.findViewById<androidx.core.widget.NestedScrollView>(R.id.piggy_dialog_scroll)?.let { scroll ->
                val maxHeight = (resources.displayMetrics.heightPixels * 0.62f).toInt()
                scroll.layoutParams = scroll.layoutParams.apply { height = maxHeight }
            }
        }
        alertDialog.show()
    }

    private fun showPiggyDetails(item: com.example.kitatrack.data.local.model.PiggyBankProgress) {
        piggyViewModel.refreshMissed(item.id)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(item.name)
            .setMessage(buildPiggyDetailMessage(item))
            .setNegativeButton("Close", null)
            .setNeutralButton("Record No Money Week") { _, _ -> showMissedAllowanceDialog(item) }
            .setPositiveButton(if (item.unresolvedMissedCount > 0) "Adjust Plan" else "Edit") { _, _ ->
                if (item.unresolvedMissedCount > 0) showPiggyAdjustmentDialog(item) else showPiggyDialog(item)
            }
            .show()
    }

    private fun showPiggyActions(item: com.example.kitatrack.data.local.model.PiggyBankProgress) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(item.name)
            .setItems(arrayOf("Add money", "Remove money", "Adjust missed contribution", "Archive")) { _, which ->
                when (which) {
                    0 -> showPiggyAdjustDialog(item, true)
                    1 -> showPiggyAdjustDialog(item, false)
                    2 -> showPiggyAdjustmentDialog(item)
                    3 -> piggyViewModel.archive(item.id)
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
            Snackbar.make(requireView(), "No missed contributions for this goal.", Snackbar.LENGTH_LONG).show()
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
            result.onSuccess { Snackbar.make(requireView(), "Piggy bank plan updated.", Snackbar.LENGTH_LONG).show() }
            result.onFailure { e -> Snackbar.make(requireView(), e.message ?: "Could not update piggy bank plan.", Snackbar.LENGTH_LONG).show() }
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
                    result.onSuccess { Snackbar.make(requireView(), "No-money week recorded.", Snackbar.LENGTH_LONG).show() }
                    result.onFailure { e -> Snackbar.make(requireView(), e.message ?: "Could not record no-money week.", Snackbar.LENGTH_LONG).show() }
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
                    result.onFailure { e -> Snackbar.make(requireView(), e.message ?: "Could not update piggy bank.", Snackbar.LENGTH_LONG).show() }
                }
            }.show()
    }

    private fun renderBudgets(view: View, budgets: List<BudgetProgress>) {
        val container = view.findViewById<LinearLayout>(R.id.budget_list)
        val empty = view.findViewById<TextView>(R.id.budget_empty_state)
        container.removeAllViews()
        empty.visibility = if (budgets.isEmpty()) View.VISIBLE else View.GONE
        budgets.forEach { budget ->
            val card = layoutInflater.inflate(R.layout.item_budget_card, container, false)
            card.findViewById<TextView>(R.id.budget_name).text = budget.name
            card.findViewById<TextView>(R.id.budget_meta).text =
                "${budget.periodLabel} · ${budget.categoryName ?: "Overall"}${if (!budget.isActive) " · Inactive" else ""}"
            card.findViewById<TextView>(R.id.budget_amounts).text =
                if (budget.remainingAmount >= 0) {
                    "${Formatters.peso(budget.remainingAmount)} left\n" +
                        "${Formatters.peso(budget.usedAmount)} used of ${Formatters.peso(budget.adjustedLimitAmount)} usable"
                } else {
                    "${Formatters.peso(-budget.remainingAmount)} over\n" +
                        "${Formatters.peso(budget.usedAmount)} used of ${Formatters.peso(budget.adjustedLimitAmount)} usable"
                }
            card.findViewById<TextView>(R.id.budget_status).text = when {
                !budget.isActive -> "Inactive"
                budget.adjustedLimitAmount <= 0L && budget.reserveImpactAmount > 0L -> "No spendable budget after reserves"
                budget.isOverLimit -> "Over budget"
                budget.isNearLimit -> "Near limit"
                budget.reserveImpactAmount > 0L -> "${Formatters.peso(budget.reserveImpactAmount)} reserved this period"
                else -> "On track"
            }
            card.findViewById<ProgressBar>(R.id.budget_progress).apply {
                progress = budget.usagePercent.coerceAtMost(100)
                progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), when {
                    budget.isOverLimit -> R.color.kitatrack_expense_red
                    budget.isNearLimit -> R.color.kitatrack_warning_yellow
                    else -> R.color.kitatrack_primary_green
                }))
            }
            card.setOnClickListener { showBudgetDialog(budget) }
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

    private fun showBudgetDialog(existing: BudgetProgress?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_budget, null, false)
        val name = dialogView.findViewById<TextInputEditText>(R.id.budget_name_input)
        val amount = dialogView.findViewById<TextInputEditText>(R.id.budget_amount_input)
        val type = dialogView.findViewById<AutoCompleteTextView>(R.id.budget_type_input)
        val category = dialogView.findViewById<AutoCompleteTextView>(R.id.budget_category_input)
        val categoryLayout = dialogView.findViewById<TextInputLayout>(R.id.budget_category_layout)
        val categoryHelp = dialogView.findViewById<TextView>(R.id.budget_category_help)
        val active = dialogView.findViewById<SwitchMaterial>(R.id.budget_active_switch)
        val types = listOf(
            "Weekly spending limit",
            "Monthly spending limit",
            "Weekly category budget",
            "Monthly category budget"
        )
        val values = listOf(BudgetRepository.TYPE_WEEKLY_OVERALL, BudgetRepository.TYPE_MONTHLY_OVERALL, BudgetRepository.TYPE_CATEGORY_WEEKLY, BudgetRepository.TYPE_CATEGORY_MONTHLY)
        val budgetCategories = latestState.categories
        type.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, types))
        category.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, budgetCategories.map { it.name }))
        configureDropdown(type)
        configureDropdown(category)
        var selectedType = existing?.budgetType ?: values[0]
        var selectedCategory = budgetCategories.firstOrNull { it.name == existing?.categoryName }
        name.setText(existing?.name)
        amount.setText(existing?.limitAmount?.toBigDecimal()?.movePointLeft(2)?.stripTrailingZeros()?.toPlainString())
        type.setText(types[values.indexOf(selectedType).coerceAtLeast(0)], false)
        category.setText(selectedCategory?.name.orEmpty(), false)
        active.isChecked = existing?.isActive ?: true
        fun updateCategoryVisibility() {
            val categoryBudget = selectedType in BudgetRepository.CATEGORY_TYPES
            categoryLayout.visibility = if (categoryBudget) View.VISIBLE else View.GONE
            categoryHelp.visibility = if (categoryBudget) View.VISIBLE else View.GONE
            if (!categoryBudget) {
                selectedCategory = null
                category.setText("", false)
            }
        }
        updateCategoryVisibility()
        type.setOnItemClickListener { _, _, pos, _ ->
            selectedType = values[pos]
            updateCategoryVisibility()
        }
        category.setOnClickListener {
            if (budgetCategories.isEmpty()) {
                Snackbar.make(requireView(), "No expense categories found. Add one in Manage Categories.", Snackbar.LENGTH_LONG).show()
            } else {
                category.post { category.showDropDown() }
            }
        }
        category.setOnItemClickListener { _, _, pos, _ -> selectedCategory = budgetCategories.getOrNull(pos) }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) "Add budget" else "Edit budget")
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                viewModel.save(existing?.budgetId, name.text.toString(), selectedType, amount.text.toString(), selectedCategory?.id, active.isChecked) {
                    it.onFailure { e -> Snackbar.make(requireView(), e.message ?: "Budget could not be saved.", Snackbar.LENGTH_LONG).show() }
                }
            }.show()
    }
}
