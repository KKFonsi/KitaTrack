package com.example.kitatrack.widget

data class WidgetBalanceSummary(
    val mainBalance: Long = 0,
    val debtReserve: Long = 0,
    val piggyBankTotal: Long = 0,
    val subscriptionReserve: Long = 0,
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    val hasData: Boolean = true,
    val warningMessage: String? = null
)
