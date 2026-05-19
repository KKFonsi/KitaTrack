package com.example.kitatrack.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.kitatrack.KitaTrackApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val app = context.applicationContext as KitaTrackApplication
                app.reminderRepository.rescheduleAll()
            }
            pending.finish()
        }
    }
}
