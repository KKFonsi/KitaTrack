package com.example.kitatrack.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.kitatrack.KitaTrackApplication
import com.example.kitatrack.data.local.entity.ReminderEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (reminderId <= 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val app = context.applicationContext as KitaTrackApplication
                val reminder = app.database.reminderDao().getById(reminderId) ?: return@runCatching
                if (reminder.isEnabled && reminder.status == ReminderEntity.STATUS_SCHEDULED) {
                    NotificationHelper.show(context, reminder)
                    app.database.reminderDao().update(
                        reminder.copy(
                            status = ReminderEntity.STATUS_SENT,
                            sentAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
            pending.finish()
        }
    }

    companion object {
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
    }
}
