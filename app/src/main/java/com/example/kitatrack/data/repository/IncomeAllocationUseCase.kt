package com.example.kitatrack.data.repository

import com.example.kitatrack.data.local.model.AllocationResult

class IncomeAllocationUseCase(
    private val debtRepository: DebtRepository,
    private val piggyBankRepository: PiggyBankRepository,
    private val subscriptionRepository: SubscriptionRepository
) {
    suspend fun previewIncome(incomeAmount: Long): AllocationResult {
        val debtAllocated = debtRepository.previewAllocationFromIncome(incomeAmount)
        val remainingAfterDebt = (incomeAmount - debtAllocated).coerceAtLeast(0)
        val highPrioritySubscriptionAllocated = subscriptionRepository.previewAllocationFromIncome(
            remainingAfterDebt,
            SubscriptionRepository.HIGH_PRIORITY_IMPORTANCE_LEVELS
        )
        val remainingAfterHighPrioritySubscriptions = (remainingAfterDebt - highPrioritySubscriptionAllocated).coerceAtLeast(0)
        val piggyAllocated = piggyBankRepository.previewAllocationFromIncome(remainingAfterHighPrioritySubscriptions)
        val remainingAfterPiggy = (remainingAfterHighPrioritySubscriptions - piggyAllocated).coerceAtLeast(0)
        val lowerPrioritySubscriptionAllocated = subscriptionRepository.previewAllocationFromIncome(
            remainingAfterPiggy,
            SubscriptionRepository.LOWER_PRIORITY_IMPORTANCE_LEVELS
        )
        val subscriptionAllocated = highPrioritySubscriptionAllocated + lowerPrioritySubscriptionAllocated
        val mainBalanceAmount = AllocationPriorityOrder.remainingMainBalance(
            incomeAmount,
            debtAllocated,
            highPrioritySubscriptionAllocated,
            piggyAllocated,
            lowerPrioritySubscriptionAllocated
        )

        return AllocationResult(
            originalIncomeAmount = incomeAmount,
            debtAllocatedTotal = debtAllocated,
            piggyBankAllocatedTotal = piggyAllocated,
            subscriptionAllocatedTotal = subscriptionAllocated,
            mainBalanceAmount = mainBalanceAmount,
            warnings = allocationWarnings(incomeAmount, debtAllocated, piggyAllocated, subscriptionAllocated, mainBalanceAmount)
        )
    }

    suspend fun allocateIncome(
        incomeTransactionId: Long,
        incomeAmount: Long,
        incomeDate: Long
    ): AllocationResult {
        val debtAllocated = debtRepository.allocateFromIncome(
            incomeTransactionId = incomeTransactionId,
            incomeAmount = incomeAmount,
            date = incomeDate
        )
        val remainingAfterDebt = (incomeAmount - debtAllocated).coerceAtLeast(0)

        val highPrioritySubscriptionAllocated = subscriptionRepository.allocateFromIncome(
            sourceTransactionId = incomeTransactionId,
            incomeAmount = remainingAfterDebt,
            date = incomeDate,
            importanceLevels = SubscriptionRepository.HIGH_PRIORITY_IMPORTANCE_LEVELS
        )
        val remainingAfterHighPrioritySubscriptions = (remainingAfterDebt - highPrioritySubscriptionAllocated).coerceAtLeast(0)

        val piggyAllocated = piggyBankRepository.allocateFromIncome(
            incomeTransactionId = incomeTransactionId,
            incomeAmount = remainingAfterHighPrioritySubscriptions,
            date = incomeDate
        )
        val remainingAfterPiggy = (remainingAfterHighPrioritySubscriptions - piggyAllocated).coerceAtLeast(0)

        val lowerPrioritySubscriptionAllocated = subscriptionRepository.allocateFromIncome(
            sourceTransactionId = incomeTransactionId,
            incomeAmount = remainingAfterPiggy,
            date = incomeDate,
            importanceLevels = SubscriptionRepository.LOWER_PRIORITY_IMPORTANCE_LEVELS
        )
        val subscriptionAllocated = highPrioritySubscriptionAllocated + lowerPrioritySubscriptionAllocated
        val mainBalanceAmount = AllocationPriorityOrder.remainingMainBalance(
            incomeAmount,
            debtAllocated,
            highPrioritySubscriptionAllocated,
            piggyAllocated,
            lowerPrioritySubscriptionAllocated
        )

        return AllocationResult(
            originalIncomeAmount = incomeAmount,
            debtAllocatedTotal = debtAllocated,
            piggyBankAllocatedTotal = piggyAllocated,
            subscriptionAllocatedTotal = subscriptionAllocated,
            mainBalanceAmount = mainBalanceAmount,
            warnings = allocationWarnings(incomeAmount, debtAllocated, piggyAllocated, subscriptionAllocated, mainBalanceAmount)
        )
    }

    private fun allocationWarnings(
        incomeAmount: Long,
        debtAllocated: Long,
        piggyAllocated: Long,
        subscriptionAllocated: Long,
        mainBalanceAmount: Long
    ): List<String> = buildList {
        val reserved = debtAllocated + piggyAllocated + subscriptionAllocated
        if (reserved > incomeAmount) {
            add("Allocation was capped to avoid exceeding the income amount.")
        }
        if (mainBalanceAmount == 0L && incomeAmount > 0 && reserved > 0) {
            add("All of this income is reserved, so nothing goes to Main Balance.")
        } else if (incomeAmount > 0 && mainBalanceAmount < incomeAmount / 5 && reserved > 0) {
            add("Debt and high-priority bills are protected first. Review savings or lower-priority subscriptions if spending feels tight.")
        }
    }
}

data class AllocationPriorityPlan(
    val debt: Long,
    val highPrioritySubscriptions: Long,
    val piggyBank: Long,
    val lowerPrioritySubscriptions: Long,
    val mainBalance: Long
) {
    val subscriptionTotal: Long
        get() = highPrioritySubscriptions + lowerPrioritySubscriptions
}

object AllocationPriorityOrder {
    fun plan(
        incomeAmount: Long,
        debtNeed: Long,
        highPrioritySubscriptionNeed: Long,
        piggyBankNeed: Long,
        lowerPrioritySubscriptionNeed: Long
    ): AllocationPriorityPlan {
        var remaining = incomeAmount.coerceAtLeast(0)
        val debt = take(remaining, debtNeed)
        remaining -= debt
        val highPrioritySubscriptions = take(remaining, highPrioritySubscriptionNeed)
        remaining -= highPrioritySubscriptions
        val piggyBank = take(remaining, piggyBankNeed)
        remaining -= piggyBank
        val lowerPrioritySubscriptions = take(remaining, lowerPrioritySubscriptionNeed)
        remaining -= lowerPrioritySubscriptions
        return AllocationPriorityPlan(
            debt = debt,
            highPrioritySubscriptions = highPrioritySubscriptions,
            piggyBank = piggyBank,
            lowerPrioritySubscriptions = lowerPrioritySubscriptions,
            mainBalance = remaining
        )
    }

    fun remainingMainBalance(
        incomeAmount: Long,
        debtAllocated: Long,
        highPrioritySubscriptionAllocated: Long,
        piggyBankAllocated: Long,
        lowerPrioritySubscriptionAllocated: Long
    ): Long = (incomeAmount - debtAllocated - highPrioritySubscriptionAllocated - piggyBankAllocated - lowerPrioritySubscriptionAllocated)
        .coerceAtLeast(0)

    private fun take(remaining: Long, need: Long): Long = minOf(remaining, need.coerceAtLeast(0))
}
