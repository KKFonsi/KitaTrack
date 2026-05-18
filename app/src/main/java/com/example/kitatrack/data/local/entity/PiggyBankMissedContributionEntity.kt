package com.example.kitatrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "piggy_bank_missed_contributions")
data class PiggyBankMissedContributionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val piggyBankId: Long,
    val expectedDate: Long,
    val expectedAmount: Long,
    val actualAmount: Long,
    val missedAmount: Long,
    val weeklyIncomePredictionAtTheTime: Long,
    val selectedAllocationPercentAtTheTime: Int,
    val status: String,
    val adjustmentType: String? = null,
    val notes: String? = null,
    val originalTargetDate: Long? = null,
    val adjustedTargetDate: Long? = null,
    val catchUpAmountPerWeek: Long? = null,
    val affectedWeeksCount: Int? = null,
    val createdAt: Long,
    val updatedAt: Long
)
