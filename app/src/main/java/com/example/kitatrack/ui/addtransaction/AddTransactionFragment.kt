package com.example.kitatrack.ui.addtransaction

import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.kitatrack.KitaTrackApplication
import com.example.kitatrack.R
import com.example.kitatrack.data.local.entity.CategoryEntity
import com.example.kitatrack.data.local.entity.PiggyBankEntity
import com.example.kitatrack.util.CategoryIconMapper
import com.example.kitatrack.util.Formatters
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.DecimalFormat
import java.util.Calendar
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class AddTransactionFragment : Fragment(R.layout.fragment_add_transaction) {
    private val app by lazy { requireActivity().application as KitaTrackApplication }
    private val viewModel by viewModels<AddTransactionViewModel> {
        AddTransactionViewModel.Factory(app.transactionRepository, app.categoryRepository, app.budgetRepository, app.piggyBankRepository, app.incomeAllocationUseCase)
    }

    private var categories: List<CategoryEntity> = emptyList()
    private var expenseCategories: List<CategoryEntity> = emptyList()
    private var incomeSources: List<CategoryEntity> = emptyList()
    private var piggyBanks: List<PiggyBankEntity> = emptyList()
    private var selectedDate: Long = System.currentTimeMillis()
    private var selectedCategory: CategoryEntity? = null
    private var selectedPiggyBank: PiggyBankEntity? = null
    private var pendingCategoryId: Long? = null
    private var amountText: String = "0"
    private val transactionId by lazy { arguments?.getLong("transactionId", -1L) ?: -1L }
    private val screenType by lazy {
        if (arguments?.getString("initialType", "EXPENSE") == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE
    }
    private val moneyFormat = DecimalFormat("#,##0.##")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val saveButton = view.findViewById<MaterialButton>(R.id.save_button)
        val notesLayout = view.findViewById<TextInputLayout>(R.id.notes_layout)
        val notesInput = view.findViewById<TextInputEditText>(R.id.notes_input)

        bindTopBar(view)
        applyScreenStyle(view, saveButton)
        updateDateAndAccount(view)
        updateAmountDisplay(view)
        bindNumpad(view)

        view.findViewById<View>(R.id.context_change_button).setOnClickListener { showContextActions(view) }
        view.findViewById<View>(R.id.context_row).setOnClickListener { showContextActions(view) }
        view.findViewById<View>(R.id.note_toggle_row).setOnClickListener {
            notesLayout.isVisible = !notesLayout.isVisible
            view.findViewById<TextView>(R.id.note_toggle_text).text = if (notesLayout.isVisible) "Hide note" else "Add note"
            if (notesLayout.isVisible) notesInput.requestFocus()
        }
        saveButton.setOnClickListener {
            val description = if (screenType == TransactionType.EXPENSE) {
                notesInput.text?.toString()?.trim()?.ifBlank { selectedCategory?.name.orEmpty() }.orEmpty()
            } else ""
            viewModel.save(
                existingId = transactionId.takeIf { it > 0 },
                type = screenType,
                amountText = amountText,
                category = selectedCategory,
                description = description,
                occurredAt = selectedDate,
                note = notesInput.text?.toString(),
                piggyBankIdForExpense = selectedPiggyBank?.id
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    combine(viewModel.expenseCategories, viewModel.incomeSources) { expenses, sources -> expenses to sources }
                        .collect { (expenses, sources) ->
                            expenseCategories = expenses
                            incomeSources = sources
                            renderCategoryChips(view)
                        }
                }
                launch {
                    viewModel.piggyBanks().collect { banks ->
                        piggyBanks = banks
                        updateDateAndAccount(view)
                    }
                }
                launch {
                    viewModel.isSaving.collect { saving ->
                        saveButton.isEnabled = !saving
                        saveButton.text = when {
                            saving -> "Saving..."
                            screenType == TransactionType.INCOME -> "Save Income"
                            else -> "Save Expense"
                        }
                    }
                }
                launch {
                    viewModel.saveResults.collect { result ->
                        when (result) {
                            is SaveTransactionResult.Success -> {
                                Snackbar.make(view, result.budgetWarning ?: result.message, Snackbar.LENGTH_SHORT).show()
                                viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    app.reminderRepository.rescheduleAll()
                                }
                                findNavController().popBackStack()
                            }
                            is SaveTransactionResult.Error -> Snackbar.make(view, result.message, Snackbar.LENGTH_SHORT).show()
                        }
                    }
                }
                if (transactionId > 0) {
                    launch {
                        app.transactionRepository.getTransaction(transactionId).collect { tx ->
                            tx ?: return@collect
                            amountText = tx.amount.toBigDecimal().movePointLeft(2).stripTrailingZeros().toPlainString()
                            selectedDate = tx.occurredAt
                            pendingCategoryId = tx.categoryId
                            notesInput.setText(tx.note ?: tx.description)
                            notesLayout.isVisible = tx.note?.isNotBlank() == true || tx.description.isNotBlank()
                            updateAmountDisplay(view)
                            updateDateAndAccount(view)
                            renderCategoryChips(view)
                        }
                    }
                }
            }
        }
    }

    private fun bindTopBar(view: View) {
        view.findViewById<MaterialButton>(R.id.back_button).setOnClickListener { findNavController().popBackStack() }
    }

    private fun applyScreenStyle(view: View, saveButton: MaterialButton) {
        val income = screenType == TransactionType.INCOME
        view.findViewById<TextView>(R.id.add_transaction_title).text = if (income) "Add Income" else "Add Expense"
        view.findViewById<TextView>(R.id.category_prompt).text = if (income) "Where is this from?" else "What is this for?"
        view.findViewById<MaterialCardView>(R.id.amount_card).apply {
            setCardBackgroundColor(
                ContextCompat.getColor(requireContext(), if (income) R.color.kitatrack_soft_mint else R.color.kitatrack_soft_danger_background)
            )
            strokeColor = ContextCompat.getColor(requireContext(), if (income) R.color.kitatrack_strong_border else R.color.kitatrack_chip_red_background)
        }
        view.findViewById<TextView>(R.id.amount_display).setTextColor(
            ContextCompat.getColor(requireContext(), if (income) R.color.kitatrack_primary_green else R.color.kitatrack_expense_red)
        )
        saveButton.setBackgroundColor(ContextCompat.getColor(requireContext(), if (income) R.color.kitatrack_primary_green else R.color.kitatrack_expense_red))
        saveButton.text = if (income) "Save Income" else "Save Expense"
    }

    private fun renderCategoryChips(view: View) {
        categories = if (screenType == TransactionType.INCOME) incomeSources else expenseCategories
        if (selectedCategory == null) {
            selectedCategory = pendingCategoryId?.let { id -> categories.firstOrNull { it.id == id } } ?: categories.firstOrNull()
            pendingCategoryId = null
        }
        val group = view.findViewById<ChipGroup>(R.id.category_chip_group)
        group.removeAllViews()
        categories.forEach { category ->
            group.addView(Chip(requireContext()).apply {
                text = category.name
                isCheckable = true
                isChecked = category.id == selectedCategory?.id
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                chipMinHeight = dp(38).toFloat()
                chipStrokeWidth = dp(1).toFloat()
                chipStrokeColor = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.kitatrack_subtle_border))
                setTextColor(ContextCompat.getColor(requireContext(), if (isChecked) R.color.white else R.color.kitatrack_secondary_text))
                chipIcon = ContextCompat.getDrawable(
                    requireContext(),
                    if (screenType == TransactionType.INCOME) {
                        CategoryIconMapper.incomeIconFor(category.name)
                    } else {
                        CategoryIconMapper.expenseIconFor(category.name)
                    }
                )
                isChipIconVisible = true
                chipIconSize = dp(16).toFloat()
                chipIconTint = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        requireContext(),
                        if (isChecked) R.color.white else if (screenType == TransactionType.INCOME) R.color.kitatrack_primary_green else R.color.kitatrack_expense_red
                    )
                )
                chipBackgroundColor = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        requireContext(),
                        when {
                            isChecked && screenType == TransactionType.INCOME -> R.color.kitatrack_primary_green
                            isChecked -> R.color.kitatrack_expense_red
                            else -> R.color.kitatrack_elevated_card_background
                        }
                    )
                )
                setOnClickListener {
                    selectedCategory = category
                    renderCategoryChips(view)
                }
            })
        }
    }

    private fun bindNumpad(view: View) {
        mapOf(
            R.id.key_0 to "0", R.id.key_1 to "1", R.id.key_2 to "2", R.id.key_3 to "3",
            R.id.key_4 to "4", R.id.key_5 to "5", R.id.key_6 to "6", R.id.key_7 to "7",
            R.id.key_8 to "8", R.id.key_9 to "9", R.id.key_decimal to "."
        ).forEach { (id, value) ->
            view.findViewById<Button>(id).setOnClickListener {
                appendAmount(value)
                updateAmountDisplay(view)
                if (screenType == TransactionType.INCOME) viewModel.previewAllocation(amountText)
            }
        }
        view.findViewById<Button>(R.id.key_backspace).setOnClickListener {
            amountText = amountText.dropLast(1).ifBlank { "0" }
            if (amountText == "0.") amountText = "0"
            updateAmountDisplay(view)
            if (screenType == TransactionType.INCOME) viewModel.previewAllocation(amountText)
        }
    }

    private fun appendAmount(value: String) {
        if (value == ".") {
            if (!amountText.contains(".")) amountText += "."
            return
        }
        val decimal = amountText.substringAfter(".", "")
        if (amountText.contains(".") && decimal.length >= 2) return
        amountText = when {
            amountText == "0" && value != "0" -> value
            amountText == "0" && value == "0" -> "0"
            else -> amountText + value
        }
    }

    private fun updateAmountDisplay(view: View) {
        val numeric = amountText.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
        view.findViewById<TextView>(R.id.amount_display).text = "₱${moneyFormat.format(numeric)}"
    }

    private fun updateDateAndAccount(view: View) {
        val today = Calendar.getInstance()
        val selected = Calendar.getInstance().apply { timeInMillis = selectedDate }
        view.findViewById<TextView>(R.id.date_context_value).text =
            if (today.get(Calendar.YEAR) == selected.get(Calendar.YEAR) && today.get(Calendar.DAY_OF_YEAR) == selected.get(Calendar.DAY_OF_YEAR)) "Today"
            else Formatters.date(selectedDate)
        view.findViewById<TextView>(R.id.account_context_value).text =
            selectedPiggyBank?.let { "Piggy Bank: ${it.name}" } ?: "Main Balance"
    }

    private fun showContextActions(view: View) {
        if (screenType == TransactionType.INCOME) {
            showDatePicker(view)
            return
        }
        val labels = buildList {
            add("Change date")
            add("Pay from Main Balance")
            if (piggyBanks.isNotEmpty()) add("Pay from Piggy Bank")
        }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setItems(labels) { _, which ->
                when (labels[which]) {
                    "Change date" -> showDatePicker(view)
                    "Pay from Main Balance" -> {
                        selectedPiggyBank = null
                        updateDateAndAccount(view)
                    }
                    "Pay from Piggy Bank" -> showPiggyBankPicker(view)
                }
            }
            .show()
    }

    private fun showPiggyBankPicker(view: View) {
        MaterialAlertDialogBuilder(requireContext())
            .setItems(piggyBanks.map { it.name }.toTypedArray()) { _, which ->
                selectedPiggyBank = piggyBanks[which]
                updateDateAndAccount(view)
            }
            .show()
    }

    private fun showDatePicker(view: View) {
        val calendar = Calendar.getInstance().apply { timeInMillis = selectedDate }
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                selectedDate = Calendar.getInstance().apply {
                    set(year, month, day, 12, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                updateDateAndAccount(view)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
