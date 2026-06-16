package com.example.kitatrack.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.kitatrack.KitaTrackApplication
import com.example.kitatrack.MainActivity
import com.example.kitatrack.R
import com.example.kitatrack.data.repository.DebtRepository
import com.example.kitatrack.data.repository.SubscriptionRepository
import com.example.kitatrack.util.Formatters
import kotlinx.coroutines.flow.first

object KitaTrackWidgetUpdater {
    suspend fun updateAll(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, KitaTrackWidgetProvider::class.java))
        update(context, appWidgetManager, ids)
    }

    suspend fun update(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        val summary = runCatching { loadSummary(context.applicationContext as KitaTrackApplication) }
            .getOrElse { WidgetBalanceSummary(hasData = false, warningMessage = "Balance unavailable") }
        widgetIds.forEach { id ->
            manager.updateAppWidget(id, buildViews(context, summary))
        }
    }

    private suspend fun loadSummary(app: KitaTrackApplication): WidgetBalanceSummary {
        val totalIncome = app.transactionRepository.getTotalIncome().first()
        val totalExpenses = app.transactionRepository.getTotalExpenses().first()
        val piggyTotal = app.piggyBankRepository.getAllPiggyBanks().first()
            .filter { it.isActive && !it.isArchived }
            .sumOf { it.currentAmount }
        val debtReserve = app.debtRepository.getAllDebts().first()
            .filter { it.isActive && !it.isArchived && it.debtType == DebtRepository.TYPE_I_OWE }
            .sumOf { it.reservedAmount }
        val subscriptionReserve = app.subscriptionRepository.getAllSubscriptions().first()
            .filter {
                it.isActive &&
                    !it.isArchived &&
                    it.reserveEnabled &&
                    it.status !in setOf(
                        SubscriptionRepository.STATUS_CANCELLED,
                        SubscriptionRepository.STATUS_PAUSED,
                        SubscriptionRepository.STATUS_ARCHIVED
                    )
            }
            .sumOf { it.reservedAmount }
        return WidgetBalanceSummary(
            mainBalance = totalIncome - totalExpenses - debtReserve - piggyTotal - subscriptionReserve,
            debtReserve = debtReserve,
            piggyBankTotal = piggyTotal,
            subscriptionReserve = subscriptionReserve,
            hasData = true
        )
    }

    private fun buildViews(context: Context, summary: WidgetBalanceSummary): RemoteViews {
        return RemoteViews(context.packageName, R.layout.widget_kitatrack).apply {
            setTextViewText(R.id.widget_balance_value, if (summary.hasData) Formatters.peso(summary.mainBalance) else "Unavailable")
            setTextViewText(
                R.id.widget_reserve_summary,
                if (summary.hasData) {
                    "Debt ${Formatters.peso(summary.debtReserve)}\nPiggy ${Formatters.peso(summary.piggyBankTotal)}  |  Subs ${Formatters.peso(summary.subscriptionReserve)}"
                } else {
                    "Open app to refresh"
                }
            )
            setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
            setOnClickPendingIntent(R.id.widget_income_button, addTransactionIntent(context, "INCOME", 100))
            setOnClickPendingIntent(R.id.widget_expense_button, addTransactionIntent(context, "EXPENSE", 101))
        }
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(context, 99, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun addTransactionIntent(context: Context, type: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction("${MainActivity.ACTION_ADD_TRANSACTION}.$type")
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_ADD_TRANSACTION_TYPE, type)
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
