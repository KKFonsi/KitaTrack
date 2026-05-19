package com.example.kitatrack.ui.addtransaction

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.kitatrack.KitaTrackApplication
import com.example.kitatrack.R
import com.example.kitatrack.data.local.entity.CategoryEntity
import com.example.kitatrack.util.Formatters
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Calendar
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine

class AddTransactionFragment : Fragment(R.layout.fragment_add_transaction) {
    private val app by lazy { requireActivity().application as KitaTrackApplication }
    private val viewModel by viewModels<AddTransactionViewModel> {
        AddTransactionViewModel.Factory(app.transactionRepository, app.categoryRepository, app.budgetRepository, app.piggyBankRepository, app.debtRepository, app.subscriptionRepository)
    }

    private var categories: List<CategoryEntity> = emptyList()
    private var expenseCategories: List<CategoryEntity> = emptyList()
    private var incomeSources: List<CategoryEntity> = emptyList()
    private var selectedDate: Long = System.currentTimeMillis()
    private var selectedCategory: CategoryEntity? = null
    private var pendingCategoryId: Long? = null
    private val transactionId by lazy { arguments?.getLong("transactionId", -1L) ?: -1L }
    private val initialType by lazy {
        arguments?.getString("initialType", "EXPENSE")
            ?.takeIf { it == "INCOME" || it == "EXPENSE" } ?: "EXPENSE"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val typeToggle = view.findViewById<MaterialButtonToggleGroup>(R.id.type_toggle)
        val incomeButton = view.findViewById<MaterialButton>(R.id.income_button)
        val expenseButton = view.findViewById<MaterialButton>(R.id.expense_button)
        val amountLayout = view.findViewById<TextInputLayout>(R.id.amount_layout)
        val amountInput = view.findViewById<TextInputEditText>(R.id.amount_input)
        val categoryLayout = view.findViewById<TextInputLayout>(R.id.category_layout)
        val categoryInput = view.findViewById<AutoCompleteTextView>(R.id.category_input)
        val descriptionLayout = view.findViewById<TextInputLayout>(R.id.description_layout)
        val descriptionInput = view.findViewById<TextInputEditText>(R.id.description_input)
        val dateLayout = view.findViewById<TextInputLayout>(R.id.date_layout)
        val dateInput = view.findViewById<TextInputEditText>(R.id.date_input)
        val paymentSourceLayout = view.findViewById<TextInputLayout>(R.id.payment_source_layout)
        val paymentSourceInput = view.findViewById<AutoCompleteTextView>(R.id.payment_source_input)
        val paymentPiggyLayout = view.findViewById<TextInputLayout>(R.id.payment_piggy_layout)
        val paymentPiggyInput = view.findViewById<AutoCompleteTextView>(R.id.payment_piggy_input)
        val allocationPreview = view.findViewById<android.widget.TextView>(R.id.allocation_preview)
        val notesInput = view.findViewById<TextInputEditText>(R.id.notes_input)
        val notesLayout = view.findViewById<TextInputLayout>(R.id.notes_layout)
        val saveButton = view.findViewById<MaterialButton>(R.id.save_button)

        val defaultType = if (initialType == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE
        typeToggle.check(if (defaultType == TransactionType.INCOME) R.id.income_button else R.id.expense_button)
        dateInput.setText(Formatters.date(selectedDate))
        updateSaveButton(saveButton, defaultType)
        updateFormForType(defaultType, categoryLayout, descriptionLayout, notesLayout, categoryInput, descriptionInput, notesInput)
        paymentSourceInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, listOf("Main Balance", "Piggy Bank")))
        paymentSourceInput.setText("Main Balance", false)
        paymentPiggyLayout.visibility = View.GONE
        var paymentPiggyBank: com.example.kitatrack.data.local.entity.PiggyBankEntity? = null

        typeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val type = if (checkedId == R.id.income_button) TransactionType.INCOME else TransactionType.EXPENSE
                updateSaveButton(
                    saveButton,
                    type
                )
                updateFormForType(type, categoryLayout, descriptionLayout, notesLayout, categoryInput, descriptionInput, notesInput)
                renderCategories(type, categoryInput)
                paymentSourceLayout.visibility = if (type == TransactionType.EXPENSE) View.VISIBLE else View.GONE
                paymentPiggyLayout.visibility = View.GONE
                allocationPreview.visibility = if (type == TransactionType.INCOME) View.VISIBLE else View.GONE
            }
        }
        paymentSourceInput.setOnItemClickListener { _, _, position, _ ->
            paymentPiggyLayout.visibility = if (position == 1) View.VISIBLE else View.GONE
            if (position == 0) paymentPiggyBank = null
        }

        dateInput.setOnClickListener {
            val calendar = Calendar.getInstance().apply { timeInMillis = selectedDate }
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    selectedDate = Calendar.getInstance().apply {
                        set(year, month, day, 12, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    dateInput.setText(Formatters.date(selectedDate))
                    dateLayout.error = null
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        categoryInput.setOnItemClickListener { _, _, position, _ ->
            selectedCategory = categories[position]
            categoryLayout.error = null
        }

        amountInput.setOnFocusChangeListener { _, _ -> amountLayout.error = null }
        descriptionInput.setOnFocusChangeListener { _, _ -> descriptionLayout.error = null }

        saveButton.setOnClickListener {
            val type = when (typeToggle.checkedButtonId) {
                R.id.income_button -> TransactionType.INCOME
                R.id.expense_button -> TransactionType.EXPENSE
                else -> null
            }
            viewModel.save(
                existingId = transactionId.takeIf { it > 0 },
                type = type,
                amountText = amountInput.text?.toString().orEmpty(),
                category = selectedCategory,
                description = descriptionInput.text?.toString().orEmpty(),
                occurredAt = selectedDate,
                note = notesInput.text?.toString()
                , piggyBankIdForExpense = paymentPiggyBank?.id
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    combine(viewModel.expenseCategories, viewModel.incomeSources) { expenses, sources ->
                        expenses to sources
                    }.collect { (expenses, sources) ->
                        expenseCategories = expenses
                        incomeSources = sources
                        renderCategories(currentType(typeToggle), categoryInput)
                        pendingCategoryId?.let { id ->
                            selectedCategory = categories.firstOrNull { it.id == id }
                            selectedCategory?.let {
                                categoryInput.setText(it.name, false)
                                pendingCategoryId = null
                            }
                        }
                    }
                }
                launch {
                    viewModel.piggyBanks().collect { banks ->
                        paymentPiggyInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, banks.map { it.name }))
                        paymentPiggyInput.setOnItemClickListener { _, _, pos, _ -> paymentPiggyBank = banks[pos] }
                        val totalPercent = banks.sumOf { it.selectedAllocationPercent }
                        allocationPreview.text = if (totalPercent > 0) "Debt Reserve is allocated first.\nPiggy Bank Allocation: $totalPercent% of income remaining after debt\nMain Balance: remaining income after reserves" else "Debt Reserve is allocated first. No automatic piggy bank allocation."
                    }
                }
                launch {
                    viewModel.saveResults.collect { result ->
                        when (result) {
                            is SaveTransactionResult.Success -> {
                                Snackbar.make(view, result.budgetWarning ?: "Transaction saved.", Snackbar.LENGTH_LONG).show()
                                findNavController().popBackStack()
                            }
                            is SaveTransactionResult.Error -> {
                                when {
                                    result.message.contains("Amount") -> amountLayout.error = result.message
                                    result.message.contains("category", ignoreCase = true) -> categoryLayout.error = result.message
                                    result.message.contains("Description") -> descriptionLayout.error = result.message
                                    result.message.contains("date", ignoreCase = true) -> dateLayout.error = result.message
                                    else -> Snackbar.make(view, result.message, Snackbar.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
                if (transactionId > 0) {
                    launch {
                        app.transactionRepository.getTransaction(transactionId).collect { tx ->
                            tx ?: return@collect
                            typeToggle.check(if (tx.type == "INCOME") R.id.income_button else R.id.expense_button)
                            amountInput.setText(tx.amount.toBigDecimal().movePointLeft(2).stripTrailingZeros().toPlainString())
                            descriptionInput.setText(tx.description)
                            notesInput.setText(tx.note)
                            selectedDate = tx.occurredAt
                            dateInput.setText(Formatters.date(selectedDate))
                            pendingCategoryId = tx.categoryId
                            selectedCategory = categories.firstOrNull { it.id == tx.categoryId }
                            selectedCategory?.let {
                                categoryInput.setText(it.name, false)
                                pendingCategoryId = null
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateSaveButton(button: MaterialButton, type: TransactionType) {
        button.text = if (type == TransactionType.INCOME) "Save Income" else "Save Expense"
        button.setBackgroundColor(
            requireContext().getColor(
                if (type == TransactionType.INCOME) R.color.kitatrack_primary_green else R.color.kitatrack_expense_red
            )
        )
    }

    private fun currentType(toggle: MaterialButtonToggleGroup): TransactionType =
        if (toggle.checkedButtonId == R.id.income_button) TransactionType.INCOME else TransactionType.EXPENSE

    private fun updateFormForType(
        type: TransactionType,
        categoryLayout: TextInputLayout,
        descriptionLayout: TextInputLayout,
        notesLayout: TextInputLayout,
        categoryInput: AutoCompleteTextView,
        descriptionInput: TextInputEditText,
        notesInput: TextInputEditText
    ) {
        val income = type == TransactionType.INCOME
        categoryLayout.hint = if (income) "Source of funds" else "Expense category"
        descriptionLayout.visibility = if (income) View.GONE else View.VISIBLE
        notesLayout.visibility = if (income) View.GONE else View.VISIBLE
        if (income) {
            descriptionInput.text?.clear()
            notesInput.text?.clear()
        }
        selectedCategory = null
        categoryInput.setText("", false)
    }

    private fun renderCategories(type: TransactionType, categoryInput: AutoCompleteTextView) {
        categories = if (type == TransactionType.INCOME) incomeSources else expenseCategories
        categoryInput.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                categories.map { it.name }
            )
        )
    }
}
