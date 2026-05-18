package com.example.kitatrack.data.local.model

data class NamedAmount(
    val name: String?,
    val totalAmount: Long
)

data class DailyExpenseSummary(
    val dayOfMonth: Int,
    val totalAmount: Long
)

data class MonthlyBalanceSummary(
    val monthKey: String,
    val totalIncome: Long,
    val totalExpenses: Long
) {
    val netAmount: Long get() = totalIncome - totalExpenses
}
