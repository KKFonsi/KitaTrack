package com.example.kitatrack.ui.summary

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.kitatrack.KitaTrackApplication
import com.example.kitatrack.R
import com.example.kitatrack.util.Formatters
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class MonthlySummaryFragment : Fragment(R.layout.fragment_monthly_summary) {
    private val app by lazy { requireActivity().application as KitaTrackApplication }
    private val viewModel by viewModels<MonthlySummaryViewModel> { MonthlySummaryViewModel.Factory(app.transactionRepository, app.piggyBankRepository) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<MaterialButton>(R.id.summary_back_button).setOnClickListener { findNavController().popBackStack() }
        view.findViewById<MaterialButton>(R.id.previous_month_button).setOnClickListener { viewModel.previousMonth() }
        view.findViewById<MaterialButton>(R.id.next_month_button).setOnClickListener { viewModel.nextMonth() }
        view.findViewById<MaterialButton>(R.id.current_month_button).setOnClickListener { viewModel.currentMonth() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    view.findViewById<TextView>(R.id.month_label).text = state.monthLabel
                    view.findViewById<TextView>(R.id.summary_overview).text =
                        "Overview\n" +
                            "Income: ${Formatters.peso(state.totalIncome)}\n" +
                            "Expenses: ${Formatters.peso(state.totalExpenses)}\n" +
                            "Net: ${Formatters.peso(state.net)}\n" +
                            "Piggy bank allocations: ${Formatters.peso(state.piggyBankAllocations)}\n" +
                            "Main balance impact: ${Formatters.peso(state.mainBalanceImpact)}\n" +
                            "Highest expense: ${state.highestExpense?.let { Formatters.peso(it.transaction.amount) } ?: Formatters.peso(0)}\n" +
                            "Top category: ${state.topCategory?.name ?: "None yet"}\n" +
                            "Transactions: ${state.transactionCount} total | ${state.incomeCount} income | ${state.expenseCount} expense\n" +
                            "Average daily expense: ${Formatters.peso(state.averageDailyExpense)}"
                    view.findViewById<TextView>(R.id.income_breakdown).text =
                        "Income Sources\n" + (state.incomeSources.takeIf { it.isNotEmpty() }?.joinToString("\n") {
                            "${it.name ?: "Missing source"}: ${Formatters.peso(it.totalAmount)}"
                        } ?: "No income this month.")
                    view.findViewById<TextView>(R.id.expense_breakdown).text =
                        "Expense Categories\n" + (state.expenseCategories.takeIf { it.isNotEmpty() }?.joinToString("\n") {
                            "${it.name ?: "Missing category"}: ${Formatters.peso(it.totalAmount)}"
                        } ?: "No expenses this month.")
                    view.findViewById<TextView>(R.id.largest_expenses).text =
                        "Largest Expenses\n" + (state.largestExpenses.takeIf { it.isNotEmpty() }?.joinToString("\n") {
                            "${it.categoryName ?: "Missing category"} | ${Formatters.date(it.transaction.occurredAt)} | ${Formatters.peso(it.transaction.amount)}"
                        } ?: "No expenses this month.")
                }
            }
        }
    }
}
