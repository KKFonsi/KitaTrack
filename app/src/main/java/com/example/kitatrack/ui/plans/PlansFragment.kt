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
import com.example.kitatrack.data.repository.BudgetRepository
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
    private val piggyViewModel by viewModels<PiggyBankViewModel> { PiggyBankViewModel.Factory(app.piggyBankRepository, app.transactionRepository) }
    private var latestState = BudgetUiState()
    private val refreshedPiggyIds = mutableSetOf<Long>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<MaterialButton>(R.id.add_budget_button).setOnClickListener { showBudgetDialog(null) }
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
                piggyViewModel.piggyBanks.collect { renderPiggyBanks(view, it) }
            }
        }
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
            card.findViewById<TextView>(R.id.piggy_meta).text = "Progress: ${item.progressPercent}% • Allocation: ${item.selectedAllocationPercent}% • ${item.statusLabel}"
            card.findViewById<TextView>(R.id.piggy_warning).apply {
                visibility = if (item.unresolvedMissedCount > 0) View.VISIBLE else View.GONE
                text = if (item.unresolvedMissedCount > 0) {
                    "${item.unresolvedMissedCount} missed contribution${if (item.unresolvedMissedCount == 1) "" else "s"} need adjustment • ${Formatters.peso(item.unresolvedMissedAmount)} planned savings missed"
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
            planText.text = "Required weekly savings: ${Formatters.peso(plan.requiredWeeklySaving)}\nValid allocation range: ${plan.minPercent}%–${plan.maxPercent}%"
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
        MaterialAlertDialogBuilder(requireContext()).setTitle(if (existing == null) "Add Piggy Bank" else "Edit Piggy Bank")
            .setView(dialog).setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val targetValue = target.text.toString().toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: 0L
                val weeklyValue = weeklyIncome.text.toString().toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: 0L
                piggyViewModel.save(existing?.id, name.text.toString(), targetValue, existing?.currentAmount ?: 0L, weeklyValue, selectedPercent, selectedTargetDate, notes.text.toString(), over.isChecked, active.isChecked) {
                    it.onFailure { e -> Snackbar.make(requireView(), e.message ?: "Piggy bank could not be saved.", Snackbar.LENGTH_LONG).show() }
                }
            }.show()
    }

    private fun showPiggyDetails(item: com.example.kitatrack.data.local.model.PiggyBankProgress) {
        piggyViewModel.refreshMissed(item.id)
        val message = buildString {
            append("${Formatters.peso(item.currentAmount)} saved of ${Formatters.peso(item.targetAmount)}\n")
            append("${Formatters.peso(item.remainingAmount)} remaining\n")
            item.targetDate?.let { append("Target: ${Formatters.date(it)}\n") }
            item.daysRemaining?.let { append("Days remaining: $it\n") }
            item.requiredWeeklySaving?.let { append("Required weekly savings: ${Formatters.peso(it)}\n") }
            item.estimatedMonthlyAllocation?.let { append("Estimated monthly allocation: ${Formatters.peso(it)}\n") }
            if (item.unresolvedMissedCount > 0) append("Missed contributions: ${item.unresolvedMissedCount} (${Formatters.peso(item.unresolvedMissedAmount)})\n")
            append("Status: ${item.statusLabel}")
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(item.name)
            .setMessage(message)
            .setNegativeButton("Close", null)
            .setNeutralButton("Missed allowance") { _, _ -> showMissedAllowanceDialog(item) }
            .setPositiveButton("Edit") { _, _ -> showPiggyDialog(item) }
            .show()
    }

    private fun showPiggyActions(item: com.example.kitatrack.data.local.model.PiggyBankProgress) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(item.name)
            .setItems(arrayOf("Add money", "Remove money", "Catch up gradually", "Extend deadline", "Skip contribution", "Archive")) { _, which ->
                when (which) {
                    0 -> showPiggyAdjustDialog(item, true)
                    1 -> showPiggyAdjustDialog(item, false)
                    2 -> applyPiggyAdjustment(item, com.example.kitatrack.data.repository.PiggyBankRepository.ADJUST_CATCH_UP)
                    3 -> applyPiggyAdjustment(item, com.example.kitatrack.data.repository.PiggyBankRepository.ADJUST_EXTEND_DEADLINE)
                    4 -> applyPiggyAdjustment(item, com.example.kitatrack.data.repository.PiggyBankRepository.ADJUST_SKIP)
                    5 -> piggyViewModel.archive(item.id)
                }
            }.show()
    }

    private fun applyPiggyAdjustment(item: com.example.kitatrack.data.local.model.PiggyBankProgress, type: String) {
        piggyViewModel.applyAdjustment(item.id, type) { result ->
            result.onSuccess { Snackbar.make(requireView(), "Piggy bank plan updated.", Snackbar.LENGTH_LONG).show() }
            result.onFailure { e -> Snackbar.make(requireView(), e.message ?: "Could not update piggy bank plan.", Snackbar.LENGTH_LONG).show() }
        }
    }

    private fun showMissedAllowanceDialog(item: com.example.kitatrack.data.local.model.PiggyBankProgress) {
        val actual = TextInputEditText(requireContext()).apply {
            hint = "Actual contribution this week"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText("0")
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Record missed allowance")
            .setMessage("This records a planning gap only. It does not create income or change your Main Balance.")
            .setView(actual)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val amount = actual.text.toString().toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: 0L
                piggyViewModel.recordNoIncomeWeek(item.id, com.example.kitatrack.util.DateRanges.currentWeek().first, amount) { result ->
                    result.onSuccess { Snackbar.make(requireView(), "Missed allowance recorded.", Snackbar.LENGTH_LONG).show() }
                    result.onFailure { e -> Snackbar.make(requireView(), e.message ?: "Could not record missed allowance.", Snackbar.LENGTH_LONG).show() }
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
                "${budget.periodLabel} • ${budget.categoryName ?: "Overall"}${if (!budget.isActive) " • Inactive" else ""}"
            card.findViewById<TextView>(R.id.budget_amounts).text =
                if (budget.remainingAmount >= 0) {
                    "${Formatters.peso(budget.remainingAmount)} left of ${Formatters.peso(budget.limitAmount)}\n${Formatters.peso(budget.usedAmount)} used"
                } else {
                    "${Formatters.peso(budget.usedAmount)} used of ${Formatters.peso(budget.limitAmount)}\nOver by ${Formatters.peso(-budget.remainingAmount)}"
                }
            card.findViewById<TextView>(R.id.budget_status).text = when {
                !budget.isActive -> "Inactive"
                budget.isOverLimit -> "Over budget"
                budget.isNearLimit -> "Near limit"
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
        type.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, types))
        category.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, latestState.categories.map { it.name }))
        var selectedType = existing?.budgetType ?: values[0]
        var selectedCategory = latestState.categories.firstOrNull { it.name == existing?.categoryName }
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
        category.setOnItemClickListener { _, _, pos, _ -> selectedCategory = latestState.categories[pos] }
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
