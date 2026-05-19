package com.example.kitatrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderType: String,
    val linkedEntityId: Long? = null,
    val linkedEntityType: String? = null,
    val title: String,
    val message: String,
    val scheduledAt: Long,
    val triggerAt: Long,
    val reminderTiming: String,
    val customOffsetMinutes: Int? = null,
    val isEnabled: Boolean = true,
    val status: String = STATUS_SCHEDULED,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val sentAt: Long? = null
) {
    companion object {
        const val TYPE_DEBT_DUE = "DEBT_DUE"
        const val TYPE_SUBSCRIPTION_DUE = "SUBSCRIPTION_DUE"
        const val TYPE_BUDGET_WARNING = "BUDGET_WARNING"
        const val TYPE_PIGGY_BANK_PROGRESS = "PIGGY_BANK_PROGRESS"
        const val TYPE_MISSED_CONTRIBUTION = "MISSED_CONTRIBUTION"

        const val ENTITY_DEBT = "DEBT"
        const val ENTITY_SUBSCRIPTION = "SUBSCRIPTION"
        const val ENTITY_BUDGET = "BUDGET"
        const val ENTITY_PIGGY_BANK = "PIGGY_BANK"
        const val ENTITY_MISSED_CONTRIBUTION = "MISSED_CONTRIBUTION"
        const val ENTITY_GENERAL = "GENERAL"

        const val TIMING_SAME_DAY = "SAME_DAY"
        const val TIMING_ONE_DAY_BEFORE = "ONE_DAY_BEFORE"
        const val TIMING_THREE_DAYS_BEFORE = "THREE_DAYS_BEFORE"
        const val TIMING_ONE_WEEK_BEFORE = "ONE_WEEK_BEFORE"
        const val TIMING_CUSTOM = "CUSTOM"

        const val STATUS_SCHEDULED = "SCHEDULED"
        const val STATUS_SENT = "SENT"
        const val STATUS_CANCELLED = "CANCELLED"
        const val STATUS_DISMISSED = "DISMISSED"
        const val STATUS_FAILED = "FAILED"
    }
}
