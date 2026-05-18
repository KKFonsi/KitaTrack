package com.example.kitatrack.ui.reports

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.kitatrack.KitaTrackApplication
import com.example.kitatrack.R
import com.example.kitatrack.data.local.model.DailyExpenseSummary
import com.example.kitatrack.data.local.model.MonthlyBalanceSummary
import com.example.kitatrack.data.local.model.NamedAmount
import com.example.kitatrack.ui.reports.chart.ChartEntry
import com.example.kitatrack.ui.reports.chart.ColumnChartView
import com.example.kitatrack.ui.reports.chart.HorizontalBarChartView
import com.example.kitatrack.ui.reports.chart.LineChartView
import com.example.kitatrack.util.Formatters
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class ReportsFragment : Fragment(R.layout.fragment_reports) {
    private val app by lazy { requireActivity().application as KitaTrackApplication }
    private val viewModel by viewModels<ReportsViewModel> { ReportsViewModel.Factory(app.transactionRepository) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<MaterialButton>(R.id.previous_month_button).setOnClickListener { viewModel.previousMonth() }
        view.findViewById<MaterialButton>(R.id.next_month_button).setOnClickListener { viewModel.nextMonth() }
        view.findViewById<MaterialButton>(R.id.current_month_button).setOnClickListener { viewModel.currentMonth() }
        view.findViewById<MaterialButton>(R.id.monthly_summary_button).setOnClickListener {
            findNavController().navigate(R.id.action_reportsFragment_to_monthlySummaryFragment)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    view.findViewById<TextView>(R.id.month_label).text = state.monthLabel
                    view.findViewById<TextView>(R.id.income_value).text = Formatters.peso(state.income)
                    view.findViewById<TextView>(R.id.expenses_value).text = Formatters.peso(state.expenses)
                    view.findViewById<TextView>(R.id.net_value).text = Formatters.peso(state.net)
                    renderIncomeVsExpense(view, state.income, state.expenses)
                    renderCategoryBars(view, state.expenseByCategory)
                    renderDailyBars(view, state.dailySpending)
                    renderTopCategories(view, state.topCategories, state.expenses)
                    renderMonthlyTrend(view, state.monthlyTrend)
                }
            }
        }
    }

    private fun renderIncomeVsExpense(view: View, income: Long, expenses: Long) {
        view.findViewById<ColumnChartView>(R.id.income_vs_expense_chart).submitData(
            listOf(
                ChartEntry("Income", income, Formatters.peso(income), R.color.kitatrack_primary_green),
                ChartEntry("Expenses", expenses, Formatters.peso(expenses), R.color.kitatrack_expense_red)
            )
        )
    }

    private fun renderCategoryBars(view: View, items: List<NamedAmount>) {
        val empty = view.findViewById<TextView>(R.id.expense_category_empty)
        empty.isVisible = items.isEmpty()
        val total = items.sumOf { it.totalAmount }.coerceAtLeast(1)
        view.findViewById<HorizontalBarChartView>(R.id.expense_category_chart).submitData(
            items.map {
                val percent = ((it.totalAmount * 100) / total).toInt()
                ChartEntry("${it.name ?: "Missing category"} • ${Formatters.peso(it.totalAmount)} • $percent%", it.totalAmount)
            }
        )
    }

    private fun renderDailyBars(view: View, items: List<DailyExpenseSummary>) {
        val empty = view.findViewById<TextView>(R.id.daily_spending_empty)
        empty.isVisible = items.isEmpty()
        view.findViewById<LineChartView>(R.id.daily_spending_chart).submitData(
            items.map { ChartEntry(it.dayOfMonth.toString(), it.totalAmount) }
        )
    }

    private fun renderTopCategories(view: View, items: List<NamedAmount>, monthlyExpenses: Long) {
        val empty = view.findViewById<TextView>(R.id.top_categories_empty)
        empty.isVisible = items.isEmpty()
        val total = monthlyExpenses.coerceAtLeast(1)
        view.findViewById<HorizontalBarChartView>(R.id.top_categories_chart).submitData(
            items.mapIndexed { index, item ->
                val percent = ((item.totalAmount * 100) / total).toInt()
                ChartEntry("${index + 1}. ${item.name ?: "Missing category"} • ${Formatters.peso(item.totalAmount)} • $percent%", item.totalAmount)
            }
        )
    }

    private fun renderMonthlyTrend(view: View, items: List<MonthlyBalanceSummary>) {
        view.findViewById<LineChartView>(R.id.monthly_trend_chart).submitData(
            items.map {
                val label = it.monthKey.substring(5)
                ChartEntry(label, it.netAmount)
            },
            negativeAware = true
        )
    }
}
