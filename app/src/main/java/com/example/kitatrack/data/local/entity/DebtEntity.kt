package com.example.kitatrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val personName: String? = null,
    val debtType: String = "I_OWE",
    val totalAmount: Long = 0,
    val amountPaid: Long = 0,
    val remainingAmount: Long = 0,
    val reservedAmount: Long = 0,
    val dueDate: Long? = null,
    val nextDueDate: Long? = null,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val paymentFrequency: String = "ONE_TIME",
    val customIntervalDays: Int? = null,
    val installmentAmount: Long? = null,
    val isRecurring: Boolean = false,
    val status: String = "ACTIVE",
    val notes: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val isActive: Boolean = true,
    val isArchived: Boolean = false,
    val completedAt: Long? = null,
    val priority: Int = 0,
    val autoReserveEnabled: Boolean = true,
    val reservePercent: Int? = null,
    val fixedReserveAmount: Long? = null,
    val lastPaymentDate: Long? = null,
    val reminderEnabled: Boolean = false,
    val reminderTimingDays: Int? = null
)
