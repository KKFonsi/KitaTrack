package com.example.kitatrack.ui.categories

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.kitatrack.KitaTrackApplication
import com.example.kitatrack.R
import com.example.kitatrack.data.local.entity.CategoryEntity
import com.example.kitatrack.data.repository.CategoryRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine

class CategoriesFragment : Fragment(R.layout.fragment_categories) {
    private val app by lazy { requireActivity().application as KitaTrackApplication }
    private val viewModel by viewModels<CategoriesViewModel> { CategoriesViewModel.Factory(app.categoryRepository) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<MaterialButton>(R.id.categories_back_button).setOnClickListener {
            findNavController().popBackStack()
        }
        val adapter = CategoryAdapter(
            onEdit = { showCategoryDialog(it) },
            onDelete = { category ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete category?")
                    .setMessage("Unused custom categories can be deleted.")
                    .setPositiveButton("Delete") { _, _ -> viewModel.delete(category) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        view.findViewById<RecyclerView>(R.id.categories_list).apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }
        val typeToggle = view.findViewById<MaterialButtonToggleGroup>(R.id.category_type_toggle)
        typeToggle.check(R.id.expense_categories_button)
        updateSegmentStyle(view, typeToggle.checkedButtonId)
        view.findViewById<MaterialButton>(R.id.add_category_button).setOnClickListener {
            showCategoryDialog(null, currentType(typeToggle))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    combine(viewModel.expenseCategories, viewModel.incomeSources) { expenses, income ->
                        expenses to income
                    }.collect { (expenses, income) ->
                        adapter.submitList(if (currentType(typeToggle) == CategoryRepository.TYPE_EXPENSE) expenses else income)
                    }
                }
                launch { viewModel.messages.collect { Snackbar.make(view, it, Snackbar.LENGTH_SHORT).show() } }
            }
        }
        typeToggle.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) {
                updateSegmentStyle(view, typeToggle.checkedButtonId)
                adapter.submitList(
                    if (currentType(typeToggle) == CategoryRepository.TYPE_EXPENSE) viewModel.expenseCategories.value
                    else viewModel.incomeSources.value
                )
            }
        }
    }

    private fun showCategoryDialog(category: CategoryEntity?, type: String = category?.type ?: CategoryRepository.TYPE_EXPENSE) {
        val dialogView = layoutInflater.inflate(R.layout.bottom_sheet_category, null, false)
        val input = dialogView.findViewById<TextInputEditText>(R.id.category_name_input)
        val expenseButton = dialogView.findViewById<MaterialButton>(R.id.category_expense_type_button)
        val incomeButton = dialogView.findViewById<MaterialButton>(R.id.category_income_type_button)
        val saveButton = dialogView.findViewById<MaterialButton>(R.id.category_save_button)
        val cancelButton = dialogView.findViewById<MaterialButton>(R.id.category_cancel_button)
        var selectedType = type
        input.setText(category?.name.orEmpty())
        dialogView.findViewById<android.widget.TextView>(R.id.category_sheet_title).text =
            if (category == null) "New Category" else "Rename Category"
        dialogView.findViewById<android.widget.TextView>(R.id.category_sheet_subtitle).text =
            if (category == null) "Add a custom category" else "Update this custom category"
        saveButton.text = if (category == null) "Add Category" else "Save Category"

        fun refreshTypeButtons() {
            styleCategoryTypeButton(expenseButton, selectedType == CategoryRepository.TYPE_EXPENSE)
            styleCategoryTypeButton(incomeButton, selectedType == CategoryRepository.TYPE_INCOME_SOURCE)
        }
        refreshTypeButtons()
        expenseButton.setOnClickListener {
            selectedType = CategoryRepository.TYPE_EXPENSE
            refreshTypeButtons()
        }
        incomeButton.setOnClickListener {
            selectedType = CategoryRepository.TYPE_INCOME_SOURCE
            refreshTypeButtons()
        }

        val sheet = BottomSheetDialog(requireContext())
        sheet.setContentView(dialogView)
        sheet.setOnShowListener {
            sheet.window?.setDimAmount(0.42f)
            sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
        }
        cancelButton.setOnClickListener { sheet.dismiss() }
        saveButton.setOnClickListener {
            if (category == null) viewModel.add(input.text.toString(), selectedType)
            else viewModel.rename(category, input.text.toString())
            sheet.dismiss()
        }
        sheet.show()
    }

    private fun styleCategoryTypeButton(button: MaterialButton, selected: Boolean) {
        button.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), if (selected) R.color.kitatrack_primary_green else R.color.kitatrack_secondary_background)
        )
        button.setTextColor(
            ContextCompat.getColor(requireContext(), if (selected) R.color.white else R.color.kitatrack_secondary_text)
        )
        button.strokeColor = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), if (selected) R.color.kitatrack_primary_green else R.color.kitatrack_subtle_border)
        )
        button.strokeWidth = resources.getDimensionPixelSize(
            if (selected) R.dimen.kt_chip_selected_stroke else R.dimen.kt_chip_stroke
        )
        button.isAllCaps = false
        button.stateListAnimator = null
    }

    private fun currentType(toggle: MaterialButtonToggleGroup): String =
        if (toggle.checkedButtonId == R.id.income_sources_button) CategoryRepository.TYPE_INCOME_SOURCE
        else CategoryRepository.TYPE_EXPENSE

    private fun updateSegmentStyle(view: View, selectedId: Int) {
        val selectedBackground = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.kitatrack_segment_selected_background))
        val unselectedBackground = ColorStateList.valueOf(Color.TRANSPARENT)
        val selectedText = ContextCompat.getColor(requireContext(), R.color.kitatrack_primary_green)
        val unselectedText = ContextCompat.getColor(requireContext(), R.color.kitatrack_secondary_text)
        val border = ColorStateList.valueOf(Color.TRANSPARENT)

        listOf(
            view.findViewById<MaterialButton>(R.id.expense_categories_button),
            view.findViewById<MaterialButton>(R.id.income_sources_button)
        ).forEach { button ->
            val selected = button.id == selectedId
            button.backgroundTintList = if (selected) selectedBackground else unselectedBackground
            button.setTextColor(if (selected) selectedText else unselectedText)
            button.strokeColor = border
            button.strokeWidth = 0
            button.elevation = if (selected) 2f else 0f
            button.stateListAnimator = null
        }
    }
}
