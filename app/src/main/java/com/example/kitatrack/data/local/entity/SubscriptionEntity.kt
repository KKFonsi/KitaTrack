package com.example.kitatrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val amount: Long = 0,
    val categoryId: Long? = null,
    val billingCycle: String = "MONTHLY",
    val customIntervalDays: Int? = null,
    val nextBillingDate: Long? = null,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val reserveEnabled: Boolean = false,
    val reservedAmount: Long = 0,
    val importance: String = "MEDIUM",
    val status: String = "ACTIVE",
    val notes: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val isActive: Boolean = true,
    val isArchived: Boolean = false,
    val lastPaidDate: Long? = null,
    val completedAt: Long? = null,
    val autoPay: Boolean = false,
    val paymentMethod: String? = null,
    val reminderEnabled: Boolean = false
)
