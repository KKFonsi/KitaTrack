package com.example.kitatrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val currencyCode: String = "PHP",
    val pinEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val remindersEnabled: Boolean = false,
    val debtRemindersEnabled: Boolean = true,
    val subscriptionRemindersEnabled: Boolean = true,
    val budgetAlertsEnabled: Boolean = true,
    val piggyBankRemindersEnabled: Boolean = true,
    val missedContributionRemindersEnabled: Boolean = true,
    val defaultReminderTiming: String = "ONE_DAY_BEFORE",
    val defaultCustomOffsetMinutes: Int? = null,
    val aiSummaryEnabled: Boolean = false
)
