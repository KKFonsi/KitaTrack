package com.example.kitatrack.ui.dashboard

import android.os.Bundle
import android.view.View
import android.widget.ImageView
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
import com.example.kitatrack.data.local.model.BudgetProgress
import com.example.kitatrack.util.CategoryIconMapper
import com.example.kitatrack.util.Formatters
import com.example.kitatrack.util.ThemePreferences
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {
    private val app by lazy { requireActivity().application as KitaTrackApplication }
    private val viewModel by viewModels<DashboardViewModel> {
        DashboardViewModel.Factory(app.transactionRepository, app.budgetRepository, app.piggyBankRepository, app.debtRepository, app.subscriptionRepository)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindThemeToggle(view)
        view.findViewById<MaterialButton>(R.id.add_income_button).setOnClickListener { navigateToAddTransaction("INCOME") }
        view.findViewById<MaterialButton>(R.id.add_expense_button).setOnClickListener { navigateToAddTransaction("EXPENSE") }
        view.findViewById<MaterialButton>(R.id.view_plans_button).setOnClickListener { navigateToPlansTab() }

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
                    view.findViewById<TextView>(R.id.smart_insight_value).text = state.dashboardInsight
                    renderReservedMoney(view, state)
                    renderObligations(view, state)
                    renderBudgetPulse(view, state.budgetPreviews)
                    renderPiggyPreview(view, state)
                }
            }
        }
    }

    private fun bindThemeToggle(view: View) {
        view.findViewById<MaterialButton>(R.id.theme_toggle_button).apply {
            updateThemeToggleIcon(this)
            setOnClickListener {
                ThemePreferences.setDarkMode(requireContext(), !ThemePreferences.isDarkModeActive(requireContext()))
            }
        }
    }

    private fun updateThemeToggleIcon(button: MaterialButton) {
        val isDark = ThemePreferences.isDarkModeActive(requireContext())
        button.setIconResource(if (isDark) R.drawable.ic_theme_sun else R.drawable.ic_theme_moon)
        button.contentDescription = if (isDark) "Switch to day mode" else "Switch to dark mode"
    }

    private fun renderReservedMoney(view: View, state: DashboardUiState) {
        val totalReserved = state.debtReserve + state.piggyBankTotal + state.subscriptionReserve
        view.findViewById<TextView>(R.id.reserved_total_value).text = "${Formatters.peso(totalReserved)} total"
        bindReserveCard(
            view.findViewById(R.id.debt_reserve_card),
            icon = R.drawable.ic_plans_debt,
            title = "Debt Reserve",
            subtitle = "Locked for payment",
            amount = state.debtReserve
        )
        bindReserveCard(
            view.findViewById(R.id.piggy_reserve_card),
            icon = R.drawable.ic_plans_piggy_bank,
            title = "Piggy Bank",
            subtitle = "Locked for savings goals",
            amount = state.piggyBankTotal
        )
        bindReserveCard(
            view.findViewById(R.id.subscription_reserve_card),
            icon = R.drawable.ic_plans_subscription,
            title = "Subscriptions",
            subtitle = "Reserved bills only",
            amount = state.subscriptionReserve
        )
    }

    private fun bindReserveCard(card: View, icon: Int, title: String, subtitle: String, amount: Long) {
        card.findViewById<ImageView>(R.id.reserve_icon).setImageResource(icon)
        card.findViewById<TextView>(R.id.reserve_title).text = title
        card.findViewById<TextView>(R.id.reserve_subtitle).text = subtitle
        card.findViewById<TextView>(R.id.reserve_amount).text = Formatters.peso(amount)
    }

    private fun renderObligations(view: View, state: DashboardUiState) {
        val text = obligationsText(state)
        view.findViewById<View>(R.id.obligations_card).isVisible = text != null
        view.findViewById<TextView>(R.id.obligations_value).text = text.orEmpty()
    }

    private fun renderBudgetPulse(view: View, budgets: List<BudgetProgress>) {
        val cards = listOf<View>(
            view.findViewById(R.id.budget_first_card),
            view.findViewById(R.id.budget_second_card),
            view.findViewById(R.id.budget_third_card)
        )
        cards.forEachIndexed { index, card ->
            val budget = budgets.getOrNull(index)
            card.isVisible = budget != null
            if (budget != null) bindBudgetCard(card, budget)
        }
        view.findViewById<View>(R.id.budget_empty_card).isVisible = budgets.isEmpty()
        view.findViewById<TextView>(R.id.budget_empty_value).text =
            "No active budgets. Budgets measure spending only; reserved money stays separate."
    }

    private fun bindBudgetCard(card: View, budget: BudgetProgress) {
        card.findViewById<ImageView>(R.id.budget_card_icon)
            .setImageResource(budget.categoryName?.let { CategoryIconMapper.expenseIconFor(it) } ?: R.drawable.ic_plans_budget)
        card.findViewById<TextView>(R.id.budget_card_title).text = budget.name
        card.findViewById<TextView>(R.id.budget_card_subtitle).text = budgetSubtitle(budget)
        card.findViewById<TextView>(R.id.budget_card_percent).text = "${budget.usagePercent.coerceIn(0, 999)}% used"
        card.findViewById<TextView>(R.id.budget_card_remaining).text =
            "${Formatters.peso(budget.remainingAmount.coerceAtLeast(0))} left in ${budget.periodLabel.lowercase()} spending."
        val progress = card.findViewById<ProgressBar>(R.id.budget_card_progress)
        progress.progress = budget.usagePercent.coerceIn(0, 100)
        progress.progressTintList = ContextCompat.getColorStateList(requireContext(), when {
            budget.isOverLimit -> R.color.kitatrack_expense_red
            budget.isNearLimit -> R.color.kitatrack_warning_yellow
            else -> R.color.kitatrack_primary_green
        })
    }

    private fun budgetSubtitle(budget: BudgetProgress): String =
        budget.categoryName
            ?.takeUnless { it.equals(budget.name, ignoreCase = true) }
            ?.let { "$it - ${budget.periodLabel}" }
            ?: budget.periodLabel

    private fun renderPiggyPreview(view: View, state: DashboardUiState) {
        view.findViewById<TextView>(R.id.piggy_goal_name).text = state.piggyGoalName ?: "No active goal yet"
        view.findViewById<TextView>(R.id.piggy_goal_percent).text =
            if (state.piggyGoalName == null) "" else "${state.piggyGoalPercent}%"
        view.findViewById<TextView>(R.id.piggy_overview_value).text = when {
            state.piggyGoalName == null -> "Create a piggy bank from Plans when you want to reserve money for a goal."
            state.piggyGoalTargetAmount > 0 -> "${Formatters.peso(state.piggyGoalCurrentAmount)} of ${Formatters.peso(state.piggyGoalTargetAmount)} saved"
            else -> "${Formatters.peso(state.piggyGoalCurrentAmount)} reserved"
        }
        view.findViewById<ProgressBar>(R.id.piggy_goal_progress).progress = state.piggyGoalPercent
    }

    private fun obligationsText(state: DashboardUiState): String? = when {
        state.overdueDebtCount > 0 -> "${state.overdueDebtCount} overdue debt item${if (state.overdueDebtCount == 1) "" else "s"}."
        state.overdueSubscriptionCount > 0 -> "${state.overdueSubscriptionCount} overdue subscription${if (state.overdueSubscriptionCount == 1) "" else "s"}."
        state.nextSubscriptionName != null && state.upcomingSubscriptionCount > 0 -> "${state.nextSubscriptionName} is coming up."
        else -> null
    }

    private fun navigateToAddTransaction(initialType: String) {
        findNavController().navigate(R.id.action_dashboardFragment_to_addTransactionFragment, Bundle().apply { putString("initialType", initialType) })
    }

    private fun navigateToPlansTab() {
        requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId = R.id.plansFragment
    }
}
