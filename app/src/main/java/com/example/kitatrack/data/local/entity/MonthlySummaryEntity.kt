package com.example.kitatrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monthly_summaries")
data class MonthlySummaryEntity(
    @PrimaryKey val monthKey: String = "",
    val totalIncome: Long = 0,
    val totalExpenses: Long = 0,
    val mainBalance: Long = 0,
    val debtReserve: Long = 0,
    val piggyBankTotal: Long = 0,
    val subscriptionReserve: Long = 0,
    val totalMoneyTracked: Long = 0,
    val updatedAt: Long = 0
)
