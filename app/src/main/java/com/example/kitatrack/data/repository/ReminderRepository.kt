package com.example.kitatrack.data.repository

import com.example.kitatrack.data.local.dao.ReminderDao
import com.example.kitatrack.data.local.entity.AppSettingsEntity
import com.example.kitatrack.data.local.entity.ReminderEntity
import com.example.kitatrack.reminders.ReminderScheduler
import com.example.kitatrack.util.Formatters
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ReminderRepository(
    private val reminderDao: ReminderDao,
    private val settingsRepository: AppSettingsRepository,
    private val debtRepository: DebtRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val budgetRepository: BudgetRepository,
    private val piggyBankRepository: PiggyBankRepository,
    private val scheduler: ReminderScheduler
) {
    fun getReminders() = reminderDao.getAll()

    suspend fun rescheduleAll() {
        val settings = settingsRepository.getOrCreateSettings()
        reminderDao.getScheduled().forEach { scheduler.cancel(it.id) }
        reminderDao.clearAll()
        if (!settings.remindersEnabled) return
        generate(settings).forEach {
            val id = reminderDao.insert(it)
            scheduler.schedule(it.copy(id = id))
        }
    }

    private suspend fun generate(settings: AppSettingsEntity): List<ReminderEntity> {
        val now = System.currentTimeMillis()
        val reminders = mutableListOf<ReminderEntity>()
        if (settings.debtRemindersEnabled) reminders += debtReminders(settings, now)
        if (settings.subscriptionRemindersEnabled) reminders += subscriptionReminders(settings, now)
        if (settings.budgetAlertsEnabled) reminders += budgetReminders(now)
        if (settings.piggyBankRemindersEnabled) reminders += piggyReminders(now)
        if (settings.missedContributionRemindersEnabled) reminders += missedContributionReminders(now)
        return reminders
            .filter { it.triggerAt >= now - TimeUnit.HOURS.toMillis(1) }
            .distinctBy { "${it.reminderType}:${it.linkedEntityType}:${it.linkedEntityId}:${it.triggerAt}" }
    }

    private suspend fun debtReminders(settings: AppSettingsEntity, now: Long): List<ReminderEntity> {
        return debtRepository.getAllDebts().first()
            .filter { it.debtType == DebtRepository.TYPE_I_OWE && it.isActive && !it.isArchived && it.remainingAmount > 0 && it.reminderEnabled }
            .mapNotNull { debt ->
                val due = debt.nextDueDate ?: debt.dueDate ?: return@mapNotNull null
                val trigger = triggerFor(due, settings)
                ReminderEntity(
                    reminderType = ReminderEntity.TYPE_DEBT_DUE,
                    linkedEntityId = debt.id,
                    linkedEntityType = ReminderEntity.ENTITY_DEBT,
                    title = "Debt reminder",
                    message = "${debt.name} payment of ${Formatters.peso(debt.installmentAmount ?: debt.remainingAmount)} is due ${relativeDueText(due, now)}.",
                    scheduledAt = now,
                    triggerAt = trigger,
                    reminderTiming = settings.defaultReminderTiming,
                    customOffsetMinutes = settings.defaultCustomOffsetMinutes
                )
            }
    }

    private suspend fun subscriptionReminders(settings: AppSettingsEntity, now: Long): List<ReminderEntity> {
        return subscriptionRepository.getAllSubscriptions().first()
            .filter { it.isActive && !it.isArchived && it.reminderEnabled && it.status !in setOf(SubscriptionRepository.STATUS_CANCELLED, SubscriptionRepository.STATUS_PAUSED, SubscriptionRepository.STATUS_ARCHIVED) }
            .mapNotNull { sub ->
                val due = sub.nextBillingDate ?: return@mapNotNull null
                ReminderEntity(
                    reminderType = ReminderEntity.TYPE_SUBSCRIPTION_DUE,
                    linkedEntityId = sub.id,
                    linkedEntityType = ReminderEntity.ENTITY_SUBSCRIPTION,
                    title = "Subscription reminder",
                    message = buildString {
                        append("${sub.name} renews ${relativeDueText(due, now)}.")
                        if (sub.reserveEnabled) append(" Reserved: ${Formatters.peso(sub.reservedAmount)}.")
                    },
                    scheduledAt = now,
                    triggerAt = triggerFor(due, settings),
                    reminderTiming = settings.defaultReminderTiming,
                    customOffsetMinutes = settings.defaultCustomOffsetMinutes
                )
            }
    }

    private suspend fun budgetReminders(now: Long): List<ReminderEntity> {
        return budgetRepository.getBudgetProgress().first()
            .filter { it.isActive && (it.isNearLimit || it.isOverLimit) }
            .map { budget ->
                val overBy = (-budget.remainingAmount).coerceAtLeast(0)
                ReminderEntity(
                    reminderType = ReminderEntity.TYPE_BUDGET_WARNING,
                    linkedEntityId = budget.budgetId,
                    linkedEntityType = ReminderEntity.ENTITY_BUDGET,
                    title = if (budget.isOverLimit) "Budget exceeded" else "Budget almost used",
                    message = if (budget.isOverLimit) "${budget.name} is over budget by ${Formatters.peso(overBy)}." else "You have used ${budget.usagePercent}% of ${budget.name}.",
                    scheduledAt = now,
                    triggerAt = now + TimeUnit.MINUTES.toMillis(1),
                    reminderTiming = ReminderEntity.TIMING_SAME_DAY
                )
            }
    }

    private suspend fun piggyReminders(now: Long): List<ReminderEntity> {
        return piggyBankRepository.getAllPiggyBanks().first()
            .filter { it.isActive && !it.isArchived && it.currentAmount < it.targetAmount }
            .filter { it.targetDate?.let { date -> date - now <= TimeUnit.DAYS.toMillis(7) } == true }
            .map {
                ReminderEntity(
                    reminderType = ReminderEntity.TYPE_PIGGY_BANK_PROGRESS,
                    linkedEntityId = it.id,
                    linkedEntityType = ReminderEntity.ENTITY_PIGGY_BANK,
                    title = "Piggy bank check-in",
                    message = "Check your ${it.name} goal progress this week.",
                    scheduledAt = now,
                    triggerAt = now + TimeUnit.MINUTES.toMillis(2),
                    reminderTiming = ReminderEntity.TIMING_SAME_DAY
                )
            }
    }

    private suspend fun missedContributionReminders(now: Long): List<ReminderEntity> {
        return piggyBankRepository.getUnresolvedMissedContributions().first().map {
            ReminderEntity(
                reminderType = ReminderEntity.TYPE_MISSED_CONTRIBUTION,
                linkedEntityId = it.id,
                linkedEntityType = ReminderEntity.ENTITY_MISSED_CONTRIBUTION,
                title = "Piggy bank adjustment",
                message = "You have an unresolved missed contribution. Review your plan when you’re ready.",
                scheduledAt = now,
                triggerAt = now + TimeUnit.MINUTES.toMillis(3),
                reminderTiming = ReminderEntity.TIMING_SAME_DAY
            )
        }
    }

    private fun triggerFor(eventTime: Long, settings: AppSettingsEntity): Long {
        val offset = when (settings.defaultReminderTiming) {
            ReminderEntity.TIMING_SAME_DAY -> 0L
            ReminderEntity.TIMING_THREE_DAYS_BEFORE -> TimeUnit.DAYS.toMillis(3)
            ReminderEntity.TIMING_ONE_WEEK_BEFORE -> TimeUnit.DAYS.toMillis(7)
            ReminderEntity.TIMING_CUSTOM -> TimeUnit.MINUTES.toMillis(settings.defaultCustomOffsetMinutes?.toLong() ?: 0L)
            else -> TimeUnit.DAYS.toMillis(1)
        }
        return (eventTime - offset).atNineAm()
    }

    private fun Long.atNineAm(): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = this@atNineAm }
        cal.set(Calendar.HOUR_OF_DAY, 9)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun relativeDueText(due: Long, now: Long): String {
        val days = TimeUnit.MILLISECONDS.toDays(due.atNineAm() - now.atNineAm())
        return when {
            days < 0 -> "overdue"
            days == 0L -> "today"
            days == 1L -> "tomorrow"
            else -> "in $days days"
        }
    }
}
