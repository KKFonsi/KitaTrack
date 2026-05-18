package com.example.kitatrack.ui.categories

import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.kitatrack.KitaTrackApplication
import com.example.kitatrack.R
import com.example.kitatrack.data.local.entity.CategoryEntity
import com.example.kitatrack.data.repository.CategoryRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine

class CategoriesFragment : Fragment(R.layout.fragment_categories) {
    private val app by lazy { requireActivity().application as KitaTrackApplication }
    private val viewModel by viewModels<CategoriesViewModel> { CategoriesViewModel.Factory(app.categoryRepository) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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
                adapter.submitList(
                    if (currentType(typeToggle) == CategoryRepository.TYPE_EXPENSE) viewModel.expenseCategories.value
                    else viewModel.incomeSources.value
                )
            }
        }
    }

    private fun showCategoryDialog(category: CategoryEntity?, type: String = category?.type ?: CategoryRepository.TYPE_EXPENSE) {
        val input = EditText(requireContext()).apply {
            hint = "Category name"
            setText(category?.name.orEmpty())
            setSingleLine()
        }
        AlertDialog.Builder(requireContext())
            .setTitle(if (category == null) "Add category" else "Rename category")
            .setView(input)
            .setPositiveButton(if (category == null) "Add" else "Save") { _, _ ->
                if (category == null) viewModel.add(input.text.toString(), type)
                else viewModel.rename(category, input.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun currentType(toggle: MaterialButtonToggleGroup): String =
        if (toggle.checkedButtonId == R.id.income_sources_button) CategoryRepository.TYPE_INCOME_SOURCE
        else CategoryRepository.TYPE_EXPENSE
}
