package com.example.kitatrack.ui.quickadd

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.kitatrack.KitaTrackApplication
import com.example.kitatrack.R
import com.example.kitatrack.data.local.entity.CategoryEntity
import com.example.kitatrack.util.Formatters
import com.example.kitatrack.widget.KitaTrackWidgetUpdater
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuickAddActivity : AppCompatActivity() {
    private val app by lazy { application as KitaTrackApplication }
    private val viewModel by viewModels<QuickAddViewModel> {
        QuickAddViewModel.Factory(app.transactionRepository, app.categoryRepository, app.incomeAllocationUseCase)
    }
    private val type by lazy { intent.getStringExtra(EXTRA_TYPE)?.takeIf { it == TYPE_INCOME || it == TYPE_EXPENSE } ?: TYPE_EXPENSE }
    private var categories: List<CategoryEntity> = emptyList()
    private var selectedCategory: CategoryEntity? = null
    private var selectedDate: Long = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quick_add)

        val income = type == TYPE_INCOME
        val title = findViewById<TextView>(R.id.quick_add_title)
        val subtitle = findViewById<TextView>(R.id.quick_add_subtitle)
        val amountInput = findViewById<TextInputEditText>(R.id.quick_amount_input)
        val amountLayout = findViewById<TextInputLayout>(R.id.quick_amount_layout)
        val categoryLayout = findViewById<TextInputLayout>(R.id.quick_category_layout)
        val categoryInput = findViewById<AutoCompleteTextView>(R.id.quick_category_input)
        val descriptionLayout = findViewById<TextInputLayout>(R.id.quick_description_layout)
        val descriptionInput = findViewById<TextInputEditText>(R.id.quick_description_input)
        val dateInput = findViewById<TextInputEditText>(R.id.quick_date_input)
        val notesLayout = findViewById<TextInputLayout>(R.id.quick_notes_layout)
        val notesInput = findViewById<TextInputEditText>(R.id.quick_notes_input)
        val allocationCard = findViewById<MaterialCardView>(R.id.quick_allocation_card)
        val allocationPreview = findViewById<TextView>(R.id.quick_allocation_preview)
        val saveButton = findViewById<MaterialButton>(R.id.quick_save_button)

        title.text = if (income) "Quick Add Income" else "Quick Add Expense"
        subtitle.text = if (income) {
            "Income will follow Debt > Piggy Bank > Subscription > Main Balance allocation."
        } else {
            "Expense will reduce Main Balance."
        }
        categoryLayout.hint = if (income) "Source of funds" else "Expense category"
        descriptionLayout.visibility = if (income) View.GONE else View.VISIBLE
        notesLayout.visibility = if (income) View.GONE else View.VISIBLE
        allocationCard.visibility = if (income) View.VISIBLE else View.GONE
        saveButton.text = if (income) "Save Income" else "Save Expense"
        saveButton.setBackgroundColor(getColor(if (income) R.color.kitatrack_primary_green else R.color.kitatrack_expense_red))
        dateInput.setText(Formatters.date(selectedDate))

        categoryInput.threshold = 0
        categoryInput.keyListener = null
        categoryInput.setOnClickListener { categoryInput.post { categoryInput.showDropDown() } }
        categoryInput.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) categoryInput.post { categoryInput.showDropDown() } }
        categoryInput.setOnItemClickListener { _, _, position, _ ->
            selectedCategory = categories.getOrNull(position)
            categoryLayout.error = null
        }

        amountInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (income) viewModel.previewIncome(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        dateInput.setOnClickListener {
            val calendar = Calendar.getInstance().apply { timeInMillis = selectedDate }
            DatePickerDialog(this, { _, year, month, day ->
                selectedDate = Calendar.getInstance().apply {
                    set(year, month, day, 12, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                dateInput.setText(Formatters.date(selectedDate))
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        saveButton.setOnClickListener {
            viewModel.save(
                type = type,
                amountText = amountInput.text?.toString().orEmpty(),
                category = selectedCategory,
                description = descriptionInput.text?.toString().orEmpty(),
                date = selectedDate,
                notes = notesInput.text?.toString()
            )
        }

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    (if (income) viewModel.incomeSources else viewModel.expenseCategories).collect {
                        categories = it
                        categoryInput.setAdapter(ArrayAdapter(this@QuickAddActivity, android.R.layout.simple_dropdown_item_1line, it.map { category -> category.name }))
                    }
                }
                launch {
                    viewModel.preview.collect { allocationPreview.text = it }
                }
                launch {
                    viewModel.isSaving.collect { saving ->
                        saveButton.isEnabled = !saving
                        saveButton.text = when {
                            saving -> "Saving..."
                            income -> "Save Income"
                            else -> "Save Expense"
                        }
                    }
                }
                launch {
                    viewModel.result.collect { result ->
                        when (result) {
                            is QuickAddResult.Error -> showError(result.message, amountLayout, categoryLayout, descriptionLayout)
                            is QuickAddResult.Success -> {
                                Snackbar.make(saveButton, result.message, Snackbar.LENGTH_SHORT).show()
                                withContext(Dispatchers.IO) {
                                    app.reminderRepository.rescheduleAll()
                                    KitaTrackWidgetUpdater.updateAll(applicationContext)
                                }
                                finish()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showError(message: String, amountLayout: TextInputLayout, categoryLayout: TextInputLayout, descriptionLayout: TextInputLayout) {
        amountLayout.error = null
        categoryLayout.error = null
        descriptionLayout.error = null
        when {
            message.contains("amount", ignoreCase = true) -> amountLayout.error = message
            message.contains("source", ignoreCase = true) || message.contains("category", ignoreCase = true) -> categoryLayout.error = message
            message.contains("Description", ignoreCase = true) -> descriptionLayout.error = message
            else -> Snackbar.make(amountLayout, message, Snackbar.LENGTH_LONG).show()
        }
    }

    companion object {
        const val EXTRA_TYPE = "extra_type"
        const val TYPE_INCOME = "INCOME"
        const val TYPE_EXPENSE = "EXPENSE"
    }
}
