package com.example.kitatrack.ui.dashboard

import com.example.kitatrack.data.local.model.BudgetProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardInsightGeneratorTest {
    @Test
    fun noSafeToSpendWinsOverOtherWarnings() {
        val insight = DashboardInsightGenerator.generate(
            input(
                mainBalance = 0,
                upcomingDebtReserveShortfall = 10_000,
                budgets = listOf(budget(isOverLimit = true, remainingAmount = -5_000))
            )
        )

        assertEquals(
            "You currently have no safe-to-spend money. Avoid new expenses unless they are necessary.",
            insight
        )
    }

    @Test
    fun upcomingDebtShortfallWinsBeforeBudgetWarning() {
        val insight = DashboardInsightGenerator.generate(
            input(
                mainBalance = 50_000,
                upcomingDebtReserveShortfall = 12_500,
                budgets = listOf(budget(isNearLimit = true, usagePercent = 85))
            )
        )

        assertTrue(insight.startsWith("A debt payment is coming soon."))
    }

    @Test
    fun overLimitBudgetBeatsHighExpenseRatio() {
        val insight = DashboardInsightGenerator.generate(
            input(
                monthlyIncome = 100_000,
                monthlyExpenses = 90_000,
                budgets = listOf(budget(name = "Food", isOverLimit = true, remainingAmount = -7_500))
            )
        )

        assertEquals(
            "Food is over budget by ₱75.00. Review that category before spending more.",
            insight
        )
    }

    @Test
    fun noActiveBudgetsGetsGuidance() {
        val insight = DashboardInsightGenerator.generate(
            input(monthlyIncome = 0, monthlyExpenses = 0, monthlyNet = 0)
        )

        assertEquals(
            "No budgets are active yet. Add one to help protect your safe-to-spend balance.",
            insight
        )
    }

    private fun input(
        mainBalance: Long = 20_000,
        monthlyIncome: Long = 100_000,
        monthlyExpenses: Long = 30_000,
        monthlyNet: Long = monthlyIncome - monthlyExpenses,
        totalReserved: Long = 0,
        totalMoneyTracked: Long = mainBalance + totalReserved,
        budgets: List<BudgetProgress> = emptyList(),
        debtReserve: Long = 0,
        upcomingDebtCount: Int = 0,
        upcomingDebtReserveShortfall: Long = 0,
        subscriptionReserve: Long = 0,
        upcomingSubscriptionCount: Int = 0,
        upcomingSubscriptionReserveShortfall: Long = 0,
        reserveDisabledUpcomingSubscriptionCount: Int = 0,
        activeSubscriptionCount: Int = 0,
        activePiggyBanks: Int = 0,
        piggyBanksNeedingAdjustment: Int = 0,
        missedPiggyContributionTotal: Long = 0
    ) = DashboardInsightInput(
        mainBalance = mainBalance,
        monthlyIncome = monthlyIncome,
        monthlyExpenses = monthlyExpenses,
        monthlyNet = monthlyNet,
        totalReserved = totalReserved,
        totalMoneyTracked = totalMoneyTracked,
        budgets = budgets,
        debtReserve = debtReserve,
        upcomingDebtCount = upcomingDebtCount,
        upcomingDebtReserveShortfall = upcomingDebtReserveShortfall,
        subscriptionReserve = subscriptionReserve,
        upcomingSubscriptionCount = upcomingSubscriptionCount,
        upcomingSubscriptionReserveShortfall = upcomingSubscriptionReserveShortfall,
        reserveDisabledUpcomingSubscriptionCount = reserveDisabledUpcomingSubscriptionCount,
        activeSubscriptionCount = activeSubscriptionCount,
        activePiggyBanks = activePiggyBanks,
        piggyBanksNeedingAdjustment = piggyBanksNeedingAdjustment,
        missedPiggyContributionTotal = missedPiggyContributionTotal
    )

    private fun budget(
        name: String = "Budget",
        remainingAmount: Long = 10_000,
        usagePercent: Int = 25,
        isNearLimit: Boolean = false,
        isOverLimit: Boolean = false
    ) = BudgetProgress(
        budgetId = 1,
        name = name,
        budgetType = "MONTHLY_OVERALL",
        limitAmount = 100_000,
        usedAmount = 100_000 - remainingAmount,
        remainingAmount = remainingAmount,
        usagePercent = usagePercent,
        isNearLimit = isNearLimit,
        isOverLimit = isOverLimit,
        categoryName = null,
        periodLabel = "This Month",
        isActive = true
    )
}
