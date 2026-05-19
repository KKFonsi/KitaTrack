package com.example.kitatrack.data.local.model

data class AllocationResult(
    val originalIncomeAmount: Long,
    val debtAllocatedTotal: Long,
    val piggyBankAllocatedTotal: Long,
    val subscriptionAllocatedTotal: Long,
    val mainBalanceAmount: Long,
    val warnings: List<String> = emptyList()
) {
    val totalReservedAmount: Long
        get() = debtAllocatedTotal + piggyBankAllocatedTotal + subscriptionAllocatedTotal

    val hasReservedMoney: Boolean
        get() = totalReservedAmount > 0
}
