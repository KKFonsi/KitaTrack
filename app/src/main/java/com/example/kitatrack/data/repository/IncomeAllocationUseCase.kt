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
        val piggyAllocated = piggyBankRepository.previewAllocationFromIncome(remainingAfterDebt)
        val remainingAfterPiggy = (remainingAfterDebt - piggyAllocated).coerceAtLeast(0)
        val subscriptionAllocated = subscriptionRepository.previewAllocationFromIncome(remainingAfterPiggy)
        val mainBalanceAmount = (incomeAmount - debtAllocated - piggyAllocated - subscriptionAllocated)
            .coerceAtLeast(0)

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

        val piggyAllocated = piggyBankRepository.allocateFromIncome(
            incomeTransactionId = incomeTransactionId,
            incomeAmount = remainingAfterDebt,
            date = incomeDate
        )
        val remainingAfterPiggy = (remainingAfterDebt - piggyAllocated).coerceAtLeast(0)

        val subscriptionAllocated = subscriptionRepository.allocateFromIncome(
            sourceTransactionId = incomeTransactionId,
            incomeAmount = remainingAfterPiggy,
            date = incomeDate
        )

        val mainBalanceAmount = (incomeAmount - debtAllocated - piggyAllocated - subscriptionAllocated)
            .coerceAtLeast(0)

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
            add("Your Main Balance will be low after allocations. Debt stays protected; consider reducing piggy bank or optional subscription reserves first.")
        }
    }
}
