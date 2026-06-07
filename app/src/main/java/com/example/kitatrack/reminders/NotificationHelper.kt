package com.example.kitatrack.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.kitatrack.MainActivity
import com.example.kitatrack.R
import com.example.kitatrack.data.local.entity.ReminderEntity

object NotificationHelper {
    const val CHANNEL_DEBT = "kitatrack_debt_reminders"
    const val CHANNEL_SUBSCRIPTION = "kitatrack_subscription_reminders"
    const val CHANNEL_BUDGET = "kitatrack_budget_alerts"
    const val CHANNEL_PIGGY = "kitatrack_piggy_bank_reminders"
    const val CHANNEL_GENERAL = "kitatrack_general_reminders"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_DEBT, "Debt Reminders", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_SUBSCRIPTION, "Subscription Reminders", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_BUDGET, "Budget Alerts", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_PIGGY, "Piggy Bank Reminders", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_GENERAL, "KitaTrack Reminders", NotificationManager.IMPORTANCE_DEFAULT)
            )
        )
    }

    fun canNotify(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun show(context: Context, reminder: ReminderEntity) {
        createChannels(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val contentIntent = PendingIntent.getActivity(
            context,
            reminder.id.toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelFor(reminder.reminderType))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(reminder.title)
            .setContentText(reminder.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.message))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(reminder.id.toInt(), notification)
    }

    private fun channelFor(type: String): String = when (type) {
        ReminderEntity.TYPE_DEBT_DUE -> CHANNEL_DEBT
        ReminderEntity.TYPE_SUBSCRIPTION_DUE -> CHANNEL_SUBSCRIPTION
        ReminderEntity.TYPE_BUDGET_WARNING -> CHANNEL_BUDGET
        ReminderEntity.TYPE_PIGGY_BANK_PROGRESS, ReminderEntity.TYPE_MISSED_CONTRIBUTION -> CHANNEL_PIGGY
        else -> CHANNEL_GENERAL
    }
}
