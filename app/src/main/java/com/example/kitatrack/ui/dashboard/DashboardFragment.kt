package com.example.kitatrack.ui.dashboard

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.kitatrack.KitaTrackApplication
import com.example.kitatrack.R
import com.example.kitatrack.ui.common.TransactionAdapter
import com.example.kitatrack.ui.history.HistoryViewModel
import com.example.kitatrack.util.Formatters
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {
    private val app by lazy { requireActivity().application as KitaTrackApplication }
    private val viewModel by viewModels<DashboardViewModel> {
        DashboardViewModel.Factory(app.transactionRepository, app.budgetRepository, app.piggyBankRepository, app.debtRepository, app.subscriptionRepository)
    }
    private val balanceHelper by lazy { HistoryViewModel(app.transactionRepository) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = TransactionAdapter()
        view.findViewById<RecyclerView>(R.id.recent_transactions_list).apply { layoutManager = LinearLayoutManager(requireContext()); this.adapter = adapter }
        view.findViewById<MaterialButton>(R.id.add_income_button).setOnClickListener { navigateToAddTransaction("INCOME") }
        view.findViewById<MaterialButton>(R.id.add_expense_button).setOnClickListener { navigateToAddTransaction("EXPENSE") }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    view.findViewById<TextView>(R.id.main_balance_value).text = Formatters.peso(state.mainBalance)
                    view.findViewById<TextView>(R.id.month_income_value).text = Formatters.peso(state.monthlyIncome)
                    view.findViewById<TextView>(R.id.month_expenses_value).text = Formatters.peso(state.monthlyExpenses)
                    view.findViewById<TextView>(R.id.month_net_value).text = Formatters.peso(state.monthlyNet)
                    view.findViewById<TextView>(R.id.highest_expense_value).text = state.highestExpense?.let { Formatters.peso(it.transaction.amount) } ?: "?0.00"
                    view.findViewById<TextView>(R.id.top_category_value).text = state.topCategoryName ?: "—"
                    view.findViewById<TextView>(R.id.weekly_budget_value).text = "Weekly Budget Left: " + (state.weeklyBudget?.let { Formatters.peso(it.remainingAmount) } ?: "No active budget")
                    view.findViewById<TextView>(R.id.monthly_budget_value).text = "Monthly Budget Left: " + (state.monthlyBudget?.let { Formatters.peso(it.remainingAmount) } ?: "No active budget")
                    view.findViewById<TextView>(R.id.budget_warning_value).text = state.budgetWarning ?: "Create a budget to track spending."
                    view.findViewById<TextView>(R.id.piggy_bank_total_value).text = Formatters.peso(state.piggyBankTotal)
                    view.findViewById<TextView>(R.id.debt_reserve_value).text = Formatters.peso(state.debtReserve)
                    view.findViewById<TextView>(R.id.subscription_reserve_value).text = Formatters.peso(state.subscriptionReserve)
                    view.findViewById<TextView>(R.id.total_money_tracked_value).text = Formatters.peso(state.totalMoneyTracked)
                    view.findViewById<TextView>(R.id.debt_overview_value).text =
                        "I owe: ${Formatters.peso(state.totalDebtIOwe)} | Reserve: ${Formatters.peso(state.debtReserve)}\nOwed to me: ${Formatters.peso(state.totalOwedToMe)} | Overdue: ${state.overdueDebtCount}\nNearest due: ${state.nearestDebtName ?: "None"}"
                    view.findViewById<TextView>(R.id.piggy_overview_value).text =
                        if (state.piggyBanksNeedingAdjustment > 0) {
                            "${state.activePiggyBanks} active goals | ${state.piggyBanksNeedingAdjustment} need adjustment | ${Formatters.peso(state.missedPiggyContributionTotal)} planned savings missed"
                        } else {
                            "${state.activePiggyBanks} active goals | reserved savings"
                        }
                    view.findViewById<TextView>(R.id.subscription_overview_value).text =
                        "Reserve: ${Formatters.peso(state.subscriptionReserve)} | Upcoming: ${state.upcomingSubscriptionCount} | Overdue: ${state.overdueSubscriptionCount}\nNext due: ${state.nextSubscriptionName ?: "None"} | Monthly estimate: ${Formatters.peso(state.monthlySubscriptionEstimate)}"
                    view.findViewById<TextView>(R.id.recent_empty_state).visibility = if (state.recentTransactions.isEmpty()) View.VISIBLE else View.GONE
                    val balancesById = balanceHelper.withRunningBalances(state.allTransactions).associateBy { it.item.transaction.id }
                    adapter.submitList(state.recentTransactions.mapNotNull { balancesById[it.transaction.id] })
                }
            }
        }
    }
    private fun navigateToAddTransaction(initialType: String) {
        findNavController().navigate(R.id.action_dashboardFragment_to_addTransactionFragment, Bundle().apply { putString("initialType", initialType) })
    }
}
