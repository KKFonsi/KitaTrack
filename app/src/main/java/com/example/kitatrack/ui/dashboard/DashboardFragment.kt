package com.example.kitatrack.ui.dashboard

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.kitatrack.KitaTrackApplication
import com.example.kitatrack.R
import com.example.kitatrack.data.local.model.BudgetProgress
import com.example.kitatrack.util.Formatters
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {
    private val app by lazy { requireActivity().application as KitaTrackApplication }
    private val viewModel by viewModels<DashboardViewModel> {
        DashboardViewModel.Factory(app.transactionRepository, app.budgetRepository, app.piggyBankRepository, app.debtRepository, app.subscriptionRepository)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<MaterialButton>(R.id.add_income_button).setOnClickListener { navigateToAddTransaction("INCOME") }
        view.findViewById<MaterialButton>(R.id.add_expense_button).setOnClickListener { navigateToAddTransaction("EXPENSE") }
        view.findViewById<MaterialButton>(R.id.view_plans_button).setOnClickListener { findNavController().navigate(R.id.plansFragment) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    view.findViewById<TextView>(R.id.main_balance_value).text = Formatters.peso(state.mainBalance)
                    view.findViewById<TextView>(R.id.month_income_value).text = Formatters.peso(state.monthlyIncome)
                    view.findViewById<TextView>(R.id.month_expenses_value).text = Formatters.peso(state.monthlyExpenses)
                    view.findViewById<TextView>(R.id.month_net_value).apply {
                        text = Formatters.peso(state.monthlyNet)
                        setTextColor(ContextCompat.getColor(requireContext(), if (state.monthlyNet < 0) R.color.kitatrack_expense_red else R.color.kitatrack_primary_green))
                    }
                    view.findViewById<TextView>(R.id.reserved_money_summary).text =
                        "Debt ${Formatters.peso(state.debtReserve)}  |  Piggy ${Formatters.peso(state.piggyBankTotal)}  |  Subs ${Formatters.peso(state.subscriptionReserve)}"
                    view.findViewById<TextView>(R.id.smart_insight_value).text = smartInsight(state)
                    view.findViewById<TextView>(R.id.obligations_value).text = obligationsText(state)
                    renderBudgetPulse(view, state.weeklyBudget ?: state.monthlyBudget)
                    view.findViewById<TextView>(R.id.budget_hint_value).text = state.budgetWarning ?: "Budget usage counts spending only, not reserved money."
                    view.findViewById<TextView>(R.id.piggy_overview_value).text = when {
                        state.piggyBanksNeedingAdjustment > 0 -> "${state.piggyBanksNeedingAdjustment} goal needs adjustment. Missed planned savings: ${Formatters.peso(state.missedPiggyContributionTotal)}."
                        state.activePiggyBanks == 0 -> "No active goals yet. Create a piggy bank when you want to save for something specific."
                        else -> "${state.activePiggyBanks} active goal${if (state.activePiggyBanks == 1) "" else "s"} protected."
                    }
                }
            }
        }
    }

    private fun renderBudgetPulse(view: View, budget: BudgetProgress?) {
        val label = view.findViewById<TextView>(R.id.budget_pulse_value)
        val progress = view.findViewById<ProgressBar>(R.id.budget_pulse_progress)
        if (budget == null) {
            label.text = "No active budgets"
            progress.progress = 0
            return
        }
        label.text = "${Formatters.peso(budget.remainingAmount.coerceAtLeast(0))} left"
        progress.progress = budget.usagePercent.coerceIn(0, 100)
        progress.progressTintList = ContextCompat.getColorStateList(requireContext(), when {
            budget.isOverLimit -> R.color.kitatrack_expense_red
            budget.isNearLimit -> R.color.kitatrack_warning_yellow
            else -> R.color.kitatrack_primary_green
        })
    }

    private fun smartInsight(state: DashboardUiState): String = when {
        state.budgetWarning != null -> state.budgetWarning
        state.overdueDebtCount > 0 -> "You have ${state.overdueDebtCount} overdue debt item${if (state.overdueDebtCount == 1) "" else "s"}. Debt reserve stays protected."
        state.overdueSubscriptionCount > 0 -> "You have ${state.overdueSubscriptionCount} overdue subscription${if (state.overdueSubscriptionCount == 1) "" else "s"}."
        state.piggyBanksNeedingAdjustment > 0 -> "A savings goal may need a small adjustment. Missed contributions are planning gaps, not debt."
        state.topCategoryName != null -> "${state.topCategoryName} is your top spending category this month."
        else -> "No urgent money alerts today."
    }

    private fun obligationsText(state: DashboardUiState): String = when {
        state.overdueDebtCount > 0 -> "${state.overdueDebtCount} overdue debt item${if (state.overdueDebtCount == 1) "" else "s"}."
        state.nearestDebtName != null -> "Next debt: ${state.nearestDebtName}."
        state.overdueSubscriptionCount > 0 -> "${state.overdueSubscriptionCount} overdue subscription${if (state.overdueSubscriptionCount == 1) "" else "s"}."
        state.nextSubscriptionName != null && state.upcomingSubscriptionCount > 0 -> "${state.nextSubscriptionName} is coming up."
        else -> "No urgent obligations."
    }

    private fun navigateToAddTransaction(initialType: String) {
        findNavController().navigate(R.id.action_dashboardFragment_to_addTransactionFragment, Bundle().apply { putString("initialType", initialType) })
    }
}
