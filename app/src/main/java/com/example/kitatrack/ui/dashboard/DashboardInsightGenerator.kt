package com.example.kitatrack.ui.dashboard

import com.example.kitatrack.data.local.model.BudgetProgress
import com.example.kitatrack.util.Formatters

data class DashboardInsightInput(
    val mainBalance: Long,
    val monthlyIncome: Long,
    val monthlyExpenses: Long,
    val monthlyNet: Long,
    val totalReserved: Long,
    val totalMoneyTracked: Long,
    val budgets: List<BudgetProgress>,
    val debtReserve: Long,
    val upcomingDebtCount: Int,
    val upcomingDebtReserveShortfall: Long,
    val subscriptionReserve: Long,
    val upcomingSubscriptionCount: Int,
    val upcomingSubscriptionReserveShortfall: Long,
    val reserveDisabledUpcomingSubscriptionCount: Int,
    val activeSubscriptionCount: Int,
    val activePiggyBanks: Int,
    val piggyBanksNeedingAdjustment: Int,
    val missedPiggyContributionTotal: Long
)

object DashboardInsightGenerator {
    private const val LOW_SAFE_TO_SPEND_CENTAVOS = 5_000L
    private const val VERY_LOW_BUDGET_REMAINING_CENTAVOS = 5_000L

    fun generate(input: DashboardInsightInput): String {
        val activeBudgets = input.budgets.filter { it.isActive }
        val overLimitBudget = activeBudgets.firstOrNull { it.isOverLimit }
        val nearLimitBudget = activeBudgets.firstOrNull { it.isNearLimit }
        val pressureBudget = activeBudgets.firstOrNull {
            it.reserveImpactAmount > 0L && it.remainingAmount in 1L..VERY_LOW_BUDGET_REMAINING_CENTAVOS
        }
        val expensesToIncomeRatio = if (input.monthlyIncome > 0L) {
            input.monthlyExpenses.toDouble() / input.monthlyIncome.toDouble()
        } else {
            0.0
        }

        return when {
            input.mainBalance <= 0L ->
                "You currently have no safe-to-spend money. Avoid new expenses unless they are necessary."

            input.upcomingDebtReserveShortfall > 0L ->
                "A debt payment is coming soon. Make sure the reserve can cover the remaining ${Formatters.peso(input.upcomingDebtReserveShortfall)} before spending freely."

            input.upcomingSubscriptionReserveShortfall > 0L ->
                "A subscription payment is coming soon. Keep ${Formatters.peso(input.upcomingSubscriptionReserveShortfall)} available if reserve mode cannot cover it yet."

            overLimitBudget != null ->
                "${overLimitBudget.name} is over budget by ${Formatters.peso((-overLimitBudget.remainingAmount).coerceAtLeast(0))}. Review that category before spending more."

            nearLimitBudget != null ->
                "${nearLimitBudget.name} is close to its limit. Try to slow spending in that category."

            input.monthlyIncome > 0L && expensesToIncomeRatio >= 0.8 ->
                "Your expenses are using most of this month's income. Check budgets before adding more spending."

            pressureBudget != null ->
                "Debt and high-priority bills are protected first. Review savings or lower-priority subscriptions if spending feels tight."

            input.mainBalance <= LOW_SAFE_TO_SPEND_CENTAVOS ->
                "Your safe-to-spend balance is low. Consider reducing optional expenses until your next income."

            input.piggyBanksNeedingAdjustment > 0 || input.missedPiggyContributionTotal > 0L ->
                "A savings goal may need attention. Lower optional spending or adjust the allocation if needed."

            input.reserveDisabledUpcomingSubscriptionCount > 0 ->
                "A subscription without reserve is coming up. Make sure your safe-to-spend balance can cover it."

            input.debtReserve > 0L && input.upcomingDebtCount > 0 ->
                "Debt money is already being reserved. This helps avoid payment pressure near the due date."

            input.subscriptionReserve > 0L && input.upcomingSubscriptionCount > 0 ->
                "Your subscription reserves are covering upcoming bills. Try not to use this money for daily spending."

            input.activeSubscriptionCount >= 4 ->
                "You have multiple active subscriptions. Review whether each one is still necessary."

            input.monthlyIncome > 0L && expensesToIncomeRatio < 0.5 ->
                "You have spent less than half of this month's income so far. Consider strengthening savings or debt reserves."

            input.monthlyNet > 0L ->
                "You are currently earning more than you are spending this month."

            input.monthlyNet < 0L ->
                "Your expenses are higher than your income this month. Review flexible spending first."

            activeBudgets.isEmpty() ->
                "No budgets are active yet. Add one to help protect your safe-to-spend balance."

            reserveShare(input) >= 0.45 ->
                "A large part of your money is reserved. This is good for planning, but keep an eye on weekly spending."

            input.activePiggyBanks > 0 ->
                "Your savings goals are moving forward. Keep the allocation steady if your spending allows it."

            activeBudgets.isNotEmpty() ->
                "Your budgets look controlled so far. Keep tracking expenses consistently."

            input.mainBalance > LOW_SAFE_TO_SPEND_CENTAVOS ->
                "Your safe-to-spend balance is still healthy after reserves."

            else ->
                "Your income, reserves, and budget limits are balanced right now. Keep tracking new transactions."
        }
    }

    private fun reserveShare(input: DashboardInsightInput): Double {
        val available = (input.mainBalance + input.totalReserved).coerceAtLeast(1L)
        return input.totalReserved.toDouble() / available.toDouble()
    }
}
