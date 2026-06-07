package com.example.kitatrack.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class AllocationPriorityOrderTest {
    @Test
    fun enoughIncomeFundsEveryPriorityBeforeMainBalance() {
        val plan = AllocationPriorityOrder.plan(
            incomeAmount = 20_000,
            debtNeed = 3_000,
            highPrioritySubscriptionNeed = 4_000,
            piggyBankNeed = 5_000,
            lowerPrioritySubscriptionNeed = 2_000
        )

        assertEquals(3_000, plan.debt)
        assertEquals(4_000, plan.highPrioritySubscriptions)
        assertEquals(5_000, plan.piggyBank)
        assertEquals(2_000, plan.lowerPrioritySubscriptions)
        assertEquals(6_000, plan.mainBalance)
    }

    @Test
    fun limitedIncomeFundsDebtAndHighPrioritySubscriptionsBeforeSavings() {
        val plan = AllocationPriorityOrder.plan(
            incomeAmount = 10_000,
            debtNeed = 4_000,
            highPrioritySubscriptionNeed = 4_000,
            piggyBankNeed = 5_000,
            lowerPrioritySubscriptionNeed = 5_000
        )

        assertEquals(4_000, plan.debt)
        assertEquals(4_000, plan.highPrioritySubscriptions)
        assertEquals(2_000, plan.piggyBank)
        assertEquals(0, plan.lowerPrioritySubscriptions)
        assertEquals(0, plan.mainBalance)
    }

    @Test
    fun noReserveEnabledSubscriptionsFallsBackToDebtSavingsMainBalance() {
        val plan = AllocationPriorityOrder.plan(
            incomeAmount = 12_000,
            debtNeed = 3_000,
            highPrioritySubscriptionNeed = 0,
            piggyBankNeed = 4_000,
            lowerPrioritySubscriptionNeed = 0
        )

        assertEquals(3_000, plan.debt)
        assertEquals(0, plan.highPrioritySubscriptions)
        assertEquals(4_000, plan.piggyBank)
        assertEquals(0, plan.lowerPrioritySubscriptions)
        assertEquals(5_000, plan.mainBalance)
    }

    @Test
    fun mediumAndLowSubscriptionsFundAfterSavings() {
        val plan = AllocationPriorityOrder.plan(
            incomeAmount = 9_000,
            debtNeed = 2_000,
            highPrioritySubscriptionNeed = 0,
            piggyBankNeed = 4_000,
            lowerPrioritySubscriptionNeed = 5_000
        )

        assertEquals(2_000, plan.debt)
        assertEquals(0, plan.highPrioritySubscriptions)
        assertEquals(4_000, plan.piggyBank)
        assertEquals(3_000, plan.lowerPrioritySubscriptions)
        assertEquals(0, plan.mainBalance)
    }

    @Test
    fun reserveDisabledSubscriptionContributesNoSubscriptionNeed() {
        val plan = AllocationPriorityOrder.plan(
            incomeAmount = 8_000,
            debtNeed = 2_000,
            highPrioritySubscriptionNeed = 0,
            piggyBankNeed = 3_000,
            lowerPrioritySubscriptionNeed = 0
        )

        assertEquals(2_000, plan.debt)
        assertEquals(3_000, plan.piggyBank)
        assertEquals(0, plan.subscriptionTotal)
        assertEquals(3_000, plan.mainBalance)
    }
}
