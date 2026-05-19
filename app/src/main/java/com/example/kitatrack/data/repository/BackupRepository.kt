package com.example.kitatrack.data.repository

import androidx.room.withTransaction
import com.example.kitatrack.data.local.KitaTrackDatabase
import com.example.kitatrack.data.local.entity.*
import com.example.kitatrack.data.local.model.TransactionWithCategory
import com.example.kitatrack.util.Formatters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class BackupValidationResult(
    val isValid: Boolean,
    val message: String,
    val transactionCount: Int = 0,
    val categoryCount: Int = 0
)

enum class RestoreMode { REPLACE, MERGE_NEWEST_WINS }

class BackupRepository(private val database: KitaTrackDatabase) {
    suspend fun exportTransactionsToCsv(): Result<String> = runCatching {
        val transactions = database.transactionDao().getAllForExport()
        require(transactions.isNotEmpty()) { "No transactions available to export." }
        buildCsv(transactions)
    }

    suspend fun exportFullBackupToJson(): Result<String> = runCatching {
        JSONObject().apply {
            put("metadata", JSONObject().apply {
                put("appName", "KitaTrack")
                put("backupVersion", BACKUP_VERSION)
                put("createdAt", isoFormatter.format(Date()))
                put("databaseVersion", 11)
            })
            put("transactions", JSONArray(database.transactionDao().getAllForExport().map { it.transaction.toJson() }))
            put("categories", JSONArray(database.categoryDao().getAllForExport().map { it.toJson() }))
            put("budgets", JSONArray(database.budgetDao().getAllForExport().map { it.toJson() }))
            put("piggyBanks", JSONArray(database.piggyBankDao().getAllForExport().map { it.toJson() }))
            put("piggyBankTransactions", JSONArray(database.piggyBankTransactionDao().getAllForExport().map { it.toJson() }))
            put("piggyBankMissedContributions", JSONArray(database.piggyBankMissedContributionDao().getAllForExport().map { it.toJson() }))
            put("debts", JSONArray(database.debtDao().getAllForExport().map { it.toJson() }))
            put("debtTransactions", JSONArray(database.debtTransactionDao().getAllForExport().map { it.toJson() }))
            put("subscriptions", JSONArray(database.subscriptionDao().getAllForExport().map { it.toJson() }))
            put("subscriptionTransactions", JSONArray(database.subscriptionTransactionDao().getAllForExport().map { it.toJson() }))
            put("monthlySummaries", JSONArray(database.monthlySummaryDao().getAllForExport().map { it.toJson() }))
            put("monthlyAiSummaries", JSONArray(database.monthlyAiSummaryDao().getAllForExport().map { it.toJson() }))
            put("reminders", JSONArray(database.reminderDao().getAllForExport().map { it.toJson() }))
            put("settings", database.appSettingsDao().getForExport()?.toJson() ?: JSONObject())
        }.toString(2)
    }

    fun validateBackupJson(json: String): BackupValidationResult = runCatching {
        val root = JSONObject(json)
        val metadata = root.optJSONObject("metadata") ?: error("Invalid backup file.")
        if (metadata.optString("appName") != "KitaTrack") error("Invalid backup file.")
        if (metadata.optInt("backupVersion", -1) != BACKUP_VERSION) error("This backup version is not supported.")
        val categories = root.optJSONArray("categories") ?: error("Backup is missing required category data.")
        val transactions = root.optJSONArray("transactions") ?: error("Backup is missing required transaction data.")
        val categoryIds = mutableSetOf<Long>()
        repeat(categories.length()) { index ->
            val item = categories.getJSONObject(index)
            val id = item.requiredLong("id")
            val name = item.requiredString("name")
            val type = item.requiredString("type")
            if (name.isBlank() || type !in validCategoryTypes) error("Invalid category data.")
            categoryIds += id
        }
        repeat(transactions.length()) { index ->
            val item = transactions.getJSONObject(index)
            val type = item.requiredString("type")
            val amount = item.requiredLong("amount")
            item.requiredLong("occurredAt")
            item.requiredLong("createdAt")
            item.requiredLong("updatedAt")
            if (type !in validTransactionTypes) error("Invalid transaction type.")
            if (amount <= 0) error("Invalid transaction amount.")
            if (!item.isNull("categoryId") && item.requiredLong("categoryId") !in categoryIds) error("Backup references a missing category.")
        }
        root.optJSONArray("budgets")?.let { budgets ->
            repeat(budgets.length()) { index ->
                val item = budgets.getJSONObject(index)
                val type = item.requiredString("budgetType")
                val amount = item.requiredLong("amountLimit")
                if (type !in BudgetRepository.TYPES || amount <= 0) error("Invalid budget data.")
                if (type in BudgetRepository.CATEGORY_TYPES && (item.isNull("categoryId") || item.requiredLong("categoryId") !in categoryIds)) {
                    error("Budget references a missing category.")
                }
            }
        }
        val piggyIds = mutableSetOf<Long>()
        root.optJSONArray("piggyBanks")?.let { piggies ->
            repeat(piggies.length()) { index ->
                val item = piggies.getJSONObject(index)
                val id = item.requiredLong("id")
                if (item.requiredLong("targetAmount") <= 0 || item.requiredLong("currentAmount") < 0) error("Invalid piggy bank data.")
                if (item.requiredLong("selectedAllocationPercent") !in 0..100) error("Invalid piggy bank allocation.")
                piggyIds += id
            }
        }
        root.optJSONArray("piggyBankTransactions")?.let { entries ->
            repeat(entries.length()) { index ->
                val item = entries.getJSONObject(index)
                if (item.requiredLong("piggyBankId") !in piggyIds) error("Piggy bank transaction references a missing goal.")
            }
        }
        root.optJSONArray("piggyBankMissedContributions")?.let { entries ->
            repeat(entries.length()) { index ->
                val item = entries.getJSONObject(index)
                if (item.requiredLong("piggyBankId") !in piggyIds) error("Missed contribution references a missing goal.")
                if (item.requiredLong("expectedAmount") < 0 || item.requiredLong("actualAmount") < 0 || item.requiredLong("missedAmount") < 0) error("Invalid missed contribution data.")
                if (item.requiredString("status") !in validMissedStatuses) error("Invalid missed contribution status.")
                item.optNullableString("adjustmentType")?.let { if (it !in validAdjustmentTypes) error("Invalid missed contribution adjustment.") }
            }
        }
        val debtIds = mutableSetOf<Long>()
        root.optJSONArray("debts")?.let { debts ->
            repeat(debts.length()) { index ->
                val item = debts.getJSONObject(index)
                val id = item.requiredLong("id")
                val type = item.optString("debtType", DebtRepository.TYPE_I_OWE)
                val total = item.requiredLong("totalAmount")
                val paid = item.optLong("amountPaid", 0)
                val remaining = item.optLong("remainingAmount", (total - paid).coerceAtLeast(0))
                val reserved = item.optLong("reservedAmount", 0)
                val frequency = item.optString("paymentFrequency", DebtRepository.FREQ_ONE_TIME)
                val status = item.optString("status", DebtRepository.STATUS_ACTIVE)
                if (type !in DebtRepository.DEBT_TYPES) error("Invalid debt type.")
                if (frequency !in DebtRepository.FREQUENCIES) error("Invalid debt frequency.")
                if (status !in validDebtStatuses) error("Invalid debt status.")
                if (total <= 0 || paid < 0 || remaining < 0 || reserved < 0 || paid > total) error("Invalid debt data.")
                debtIds += id
            }
        }
        root.optJSONArray("debtTransactions")?.let { entries ->
            repeat(entries.length()) { index ->
                val item = entries.getJSONObject(index)
                if (item.requiredLong("debtId") !in debtIds) error("Debt transaction references a missing debt.")
                if (item.requiredLong("amount") <= 0) error("Invalid debt transaction amount.")
                if (item.requiredString("transactionType") !in validDebtTransactionTypes) error("Invalid debt transaction type.")
            }
        }
        val subscriptionIds = mutableSetOf<Long>()
        root.optJSONArray("subscriptions")?.let { subscriptions ->
            repeat(subscriptions.length()) { index ->
                val item = subscriptions.getJSONObject(index)
                val id = item.requiredLong("id")
                val amount = item.requiredLong("amount")
                val cycle = item.optString("billingCycle", SubscriptionRepository.CYCLE_MONTHLY)
                val reserved = item.optLong("reservedAmount", 0)
                val importance = item.optString("importance", SubscriptionRepository.IMPORTANCE_MEDIUM)
                val status = item.optString("status", SubscriptionRepository.STATUS_ACTIVE)
                if (amount <= 0 || reserved < 0) error("Invalid subscription data.")
                if (cycle !in SubscriptionRepository.BILLING_CYCLES) error("Invalid subscription billing cycle.")
                if (importance !in SubscriptionRepository.IMPORTANCE_LEVELS) error("Invalid subscription importance.")
                if (status !in validSubscriptionStatuses) error("Invalid subscription status.")
                subscriptionIds += id
            }
        }
        root.optJSONArray("subscriptionTransactions")?.let { entries ->
            repeat(entries.length()) { index ->
                val item = entries.getJSONObject(index)
                if (item.requiredLong("subscriptionId") !in subscriptionIds) error("Subscription transaction references a missing subscription.")
                if (item.requiredLong("amount") <= 0) error("Invalid subscription transaction amount.")
                if (item.requiredString("transactionType") !in validSubscriptionTransactionTypes) error("Invalid subscription transaction type.")
            }
        }
        BackupValidationResult(true, "Backup is valid.", transactions.length(), categories.length())
    }.getOrElse { BackupValidationResult(false, it.message ?: "Invalid backup file.") }

    suspend fun restoreFromBackup(json: String, mode: RestoreMode): Result<Unit> = runCatching {
        val validation = validateBackupJson(json)
        require(validation.isValid) { validation.message }
        val root = JSONObject(json)
        val categories = root.getJSONArray("categories").toCategoryEntities()
        val transactions = root.getJSONArray("transactions").toTransactionEntities()
        val budgets = root.optJSONArray("budgets")?.toBudgetEntities().orEmpty()
        val piggyBanks = root.optJSONArray("piggyBanks")?.toPiggyBankEntities().orEmpty()
        val piggyBankTransactions = root.optJSONArray("piggyBankTransactions")?.toPiggyBankTransactionEntities().orEmpty()
        val piggyBankMissedContributions = root.optJSONArray("piggyBankMissedContributions")?.toPiggyBankMissedContributionEntities().orEmpty()
        val debts = root.optJSONArray("debts")?.toDebtEntities().orEmpty()
        val debtTransactions = root.optJSONArray("debtTransactions")?.toDebtTransactionEntities().orEmpty()
        val subscriptions = root.optJSONArray("subscriptions")?.toSubscriptionEntities().orEmpty()
        val subscriptionTransactions = root.optJSONArray("subscriptionTransactions")?.toSubscriptionTransactionEntities().orEmpty()
        val monthlySummaries = root.optJSONArray("monthlySummaries")?.toMonthlySummaryEntities().orEmpty()
        val monthlyAiSummaries = root.optJSONArray("monthlyAiSummaries")?.toMonthlyAiSummaryEntities().orEmpty()
        val reminders = root.optJSONArray("reminders")?.toReminderEntities().orEmpty()
        val settings = root.optJSONObject("settings")?.takeIf { it.length() > 0 }?.toSettingsEntity()
        when (mode) {
            RestoreMode.REPLACE -> replaceAll(categories, transactions, budgets, piggyBanks, piggyBankTransactions, piggyBankMissedContributions, debts, debtTransactions, subscriptions, subscriptionTransactions, monthlySummaries, monthlyAiSummaries, reminders, settings)
            RestoreMode.MERGE_NEWEST_WINS -> mergeNewestWins(categories, transactions, budgets, piggyBanks, piggyBankTransactions, piggyBankMissedContributions, debts, debtTransactions, subscriptions, subscriptionTransactions, monthlySummaries, monthlyAiSummaries, reminders, settings)
        }
    }

    suspend fun resetAllData(): Result<Unit> = runCatching {
        database.withTransaction {
            database.transactionDao().clearAll()
            database.categoryDao().clearAll()
            database.budgetDao().clearAll()
            database.piggyBankDao().clearAll()
            database.piggyBankTransactionDao().clearAll()
            database.piggyBankMissedContributionDao().clearAll()
            database.debtTransactionDao().clearAll()
            database.debtDao().clearAll()
            database.subscriptionTransactionDao().clearAll()
            database.subscriptionDao().clearAll()
            database.monthlySummaryDao().clearAll()
            database.monthlyAiSummaryDao().clearAll()
            database.reminderDao().clearAll()
            database.appSettingsDao().clearAll()
        }
    }

    private suspend fun replaceAll(
        categories: List<CategoryEntity>,
        transactions: List<TransactionEntity>,
        budgets: List<BudgetEntity>,
        piggyBanks: List<PiggyBankEntity>,
        piggyBankTransactions: List<PiggyBankTransactionEntity>,
        piggyBankMissedContributions: List<PiggyBankMissedContributionEntity>,
        debts: List<DebtEntity>,
        debtTransactions: List<DebtTransactionEntity>,
        subscriptions: List<SubscriptionEntity>,
        subscriptionTransactions: List<SubscriptionTransactionEntity>,
        monthlySummaries: List<MonthlySummaryEntity>,
        monthlyAiSummaries: List<MonthlyAiSummaryEntity>,
        reminders: List<ReminderEntity>,
        settings: AppSettingsEntity?
    ) {
        database.withTransaction {
            database.transactionDao().clearAll()
            database.categoryDao().clearAll()
            database.budgetDao().clearAll()
            database.piggyBankDao().clearAll()
            database.piggyBankTransactionDao().clearAll()
            database.piggyBankMissedContributionDao().clearAll()
            database.debtTransactionDao().clearAll()
            database.debtDao().clearAll()
            database.subscriptionTransactionDao().clearAll()
            database.subscriptionDao().clearAll()
            database.monthlySummaryDao().clearAll()
            database.monthlyAiSummaryDao().clearAll()
            database.reminderDao().clearAll()
            database.appSettingsDao().clearAll()
            database.categoryDao().replaceAll(categories)
            database.transactionDao().insertAll(transactions)
            database.budgetDao().insertAll(budgets)
            database.piggyBankDao().insertAll(piggyBanks)
            database.piggyBankTransactionDao().insertAll(piggyBankTransactions)
            database.piggyBankMissedContributionDao().insertAll(piggyBankMissedContributions)
            database.debtDao().insertAll(debts)
            database.debtTransactionDao().insertAll(debtTransactions)
            database.subscriptionDao().insertAll(subscriptions)
            database.subscriptionTransactionDao().insertAll(subscriptionTransactions)
            database.monthlySummaryDao().insertAll(monthlySummaries)
            database.monthlyAiSummaryDao().insertAll(monthlyAiSummaries)
            database.reminderDao().insertAll(reminders)
            settings?.let { database.appSettingsDao().insert(it) }
        }
    }

    private suspend fun mergeNewestWins(
        importedCategories: List<CategoryEntity>,
        importedTransactions: List<TransactionEntity>,
        importedBudgets: List<BudgetEntity>,
        importedPiggyBanks: List<PiggyBankEntity>,
        importedPiggyTransactions: List<PiggyBankTransactionEntity>,
        importedPiggyMissedContributions: List<PiggyBankMissedContributionEntity>,
        importedDebts: List<DebtEntity>,
        importedDebtTransactions: List<DebtTransactionEntity>,
        importedSubscriptions: List<SubscriptionEntity>,
        importedSubscriptionTransactions: List<SubscriptionTransactionEntity>,
        importedMonthlySummaries: List<MonthlySummaryEntity>,
        importedMonthlyAiSummaries: List<MonthlyAiSummaryEntity>,
        importedReminders: List<ReminderEntity>,
        importedSettings: AppSettingsEntity?
    ) {
        database.withTransaction {
            val existingCategories = database.categoryDao().getAllForExport()
            val existingTransactions = database.transactionDao().getAllForExport().map { it.transaction }
            val existingBudgets = database.budgetDao().getAllForExport()
            val existingPiggies = database.piggyBankDao().getAllForExport()
            val existingDebts = database.debtDao().getAllForExport()
            val existingSubscriptions = database.subscriptionDao().getAllForExport()
            val existingSummaries = database.monthlySummaryDao().getAllForExport()
            val existingAiSummaries = database.monthlyAiSummaryDao().getAllForExport()
            val existingSettings = database.appSettingsDao().getForExport()

            val categoryMerge = mergeCategories(existingCategories, importedCategories)
            val mergedTransactions = mergeByIdNewest(
                existingTransactions,
                importedTransactions.map { tx -> tx.copy(categoryId = tx.categoryId?.let { categoryMerge.importedIdToMergedId[it] ?: it }) },
                { it.id },
                { it.updatedAt }
            )
            val mergedBudgets = mergeByIdNewest(
                existingBudgets,
                importedBudgets.map { budget -> budget.copy(categoryId = budget.categoryId?.let { categoryMerge.importedIdToMergedId[it] ?: it }) },
                { it.id },
                { it.updatedAt }
            )
            val mergedPiggies = mergeByIdNewest(existingPiggies, importedPiggyBanks, { it.id }, { it.updatedAt })
            val mergedDebts = mergeByIdNewest(existingDebts, importedDebts, { it.id }, { it.updatedAt })
            val mergedSubscriptions = mergeByIdNewest(existingSubscriptions, importedSubscriptions, { it.id }, { it.updatedAt })
            val mergedSummaries = mergeByIdNewest(existingSummaries, importedMonthlySummaries, { it.monthKey }, { it.updatedAt })
            val mergedAiSummaries = mergeByIdNewest(existingAiSummaries, importedMonthlyAiSummaries, { "${it.year}-${it.month}" }, { it.updatedAt })
            val mergedSettings = when {
                importedSettings == null -> existingSettings
                existingSettings == null -> importedSettings
                else -> importedSettings // settings do not yet track updatedAt; imported backup wins explicitly
            }

            database.transactionDao().clearAll()
            database.categoryDao().clearAll()
            database.budgetDao().clearAll()
            database.piggyBankDao().clearAll()
            database.piggyBankTransactionDao().clearAll()
            database.piggyBankMissedContributionDao().clearAll()
            database.debtTransactionDao().clearAll()
            database.debtDao().clearAll()
            database.subscriptionTransactionDao().clearAll()
            database.subscriptionDao().clearAll()
            database.monthlySummaryDao().clearAll()
            database.monthlyAiSummaryDao().clearAll()
            database.reminderDao().clearAll()
            database.appSettingsDao().clearAll()
            database.categoryDao().replaceAll(categoryMerge.categories)
            database.transactionDao().insertAll(mergedTransactions)
            database.budgetDao().insertAll(mergedBudgets)
            database.piggyBankDao().insertAll(mergedPiggies)
            database.piggyBankTransactionDao().insertAll(importedPiggyTransactions)
            database.piggyBankMissedContributionDao().insertAll(importedPiggyMissedContributions)
            database.debtDao().insertAll(mergedDebts)
            database.debtTransactionDao().insertAll(importedDebtTransactions)
            database.subscriptionDao().insertAll(mergedSubscriptions)
            database.subscriptionTransactionDao().insertAll(importedSubscriptionTransactions)
            database.monthlySummaryDao().insertAll(mergedSummaries)
            database.monthlyAiSummaryDao().insertAll(mergedAiSummaries)
            database.reminderDao().insertAll(importedReminders)
            mergedSettings?.let { database.appSettingsDao().insert(it) }
        }
    }

    private fun mergeCategories(
        existing: List<CategoryEntity>,
        imported: List<CategoryEntity>
    ): CategoryMergeResult {
        val byBusinessKey = linkedMapOf<String, CategoryEntity>()
        existing.forEach { byBusinessKey[it.businessKey()] = it }
        val importedIdToMergedId = mutableMapOf<Long, Long>()
        imported.forEach { importedCategory ->
            val key = importedCategory.businessKey()
            val current = byBusinessKey[key]
            val winner = when {
                current == null -> importedCategory
                importedCategory.updatedAt > current.updatedAt -> importedCategory.copy(id = current.id)
                else -> current
            }
            byBusinessKey[key] = winner
            importedIdToMergedId[importedCategory.id] = winner.id
        }
        return CategoryMergeResult(byBusinessKey.values.toList(), importedIdToMergedId)
    }

    private fun <T, K> mergeByIdNewest(
        existing: List<T>,
        imported: List<T>,
        keyOf: (T) -> K,
        updatedAtOf: (T) -> Long
    ): List<T> {
        val merged = existing.associateBy(keyOf).toMutableMap()
        imported.forEach { item ->
            val key = keyOf(item)
            val current = merged[key]
            if (current == null || updatedAtOf(item) > updatedAtOf(current)) {
                merged[key] = item
            }
        }
        return merged.values.toList()
    }

    private fun buildCsv(transactions: List<TransactionWithCategory>): String {
        var balance = 0L
        val rows = mutableListOf(
            listOf("ID", "Date", "Type", "Amount", "Category / Source", "Description", "Notes", "Balance After", "Created At", "Updated At")
        )
        transactions.forEach { item ->
            balance += if (item.transaction.type == "INCOME") item.transaction.amount else -item.transaction.amount
            rows += listOf(
                item.transaction.id.toString(),
                Formatters.date(item.transaction.occurredAt),
                item.transaction.type,
                "%.2f".format(Locale.US, item.transaction.amount / 100.0),
                item.categoryName.orEmpty(),
                if (item.transaction.type == "INCOME") "" else item.transaction.description,
                if (item.transaction.type == "INCOME") "" else item.transaction.note.orEmpty(),
                "%.2f".format(Locale.US, balance / 100.0),
                isoFormatter.format(Date(item.transaction.createdAt)),
                isoFormatter.format(Date(item.transaction.updatedAt))
            )
        }
        return rows.joinToString("\n") { row -> row.joinToString(",") { csvEscape(it) } }
    }

    private fun csvEscape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${value.replace("\"", "\"\"")}\"" else value

    companion object {
        private const val BACKUP_VERSION = 1
        private val validTransactionTypes = setOf("INCOME", "EXPENSE")
        private val validCategoryTypes = setOf("INCOME_SOURCE", "EXPENSE")
        private val validMissedStatuses = setOf("MISSED", "PARTIAL", "RESOLVED", "SKIPPED")
        private val validAdjustmentTypes = setOf("CATCH_UP_GRADUALLY", "EXTEND_DEADLINE", "SKIP_CONTRIBUTION", "MANUAL_RESOLUTION")
        private val validDebtStatuses = setOf("ACTIVE", "PARTIALLY_PAID", "PAID", "OVERDUE", "ARCHIVED")
        private val validDebtTransactionTypes = setOf(
            "RESERVE_ALLOCATION",
            "PAYMENT_MADE",
            "PAYMENT_RECEIVED",
            "MANUAL_RESERVE_ADD",
            "MANUAL_RESERVE_REMOVE",
            "ADJUSTMENT"
        )
        private val validSubscriptionStatuses = setOf("ACTIVE", "PAID_CURRENT", "UPCOMING", "OVERDUE", "PAUSED", "CANCELLED", "ARCHIVED")
        private val validSubscriptionTransactionTypes = setOf("RESERVE_ALLOCATION", "PAYMENT_MADE", "MANUAL_RESERVE_ADD", "MANUAL_RESERVE_REMOVE", "ADJUSTMENT")
        private val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    }
}

private data class CategoryMergeResult(
    val categories: List<CategoryEntity>,
    val importedIdToMergedId: Map<Long, Long>
)

private fun CategoryEntity.businessKey(): String = "${name.trim().lowercase()}|$type"

private fun JSONObject.requiredString(name: String): String =
    if (!has(name) || isNull(name)) error("Missing required field: $name") else getString(name)
private fun JSONObject.requiredLong(name: String): Long =
    if (!has(name) || isNull(name)) error("Missing required field: $name") else getLong(name)

private fun TransactionEntity.toJson() = JSONObject().apply {
    put("id", id); put("amount", amount); put("type", type); put("categoryId", categoryId ?: JSONObject.NULL)
    put("description", description); put("note", note ?: JSONObject.NULL); put("occurredAt", occurredAt)
    put("createdAt", createdAt); put("updatedAt", updatedAt)
}
private fun CategoryEntity.toJson() = JSONObject().apply {
    put("id", id); put("name", name); put("type", type); put("iconName", iconName ?: JSONObject.NULL)
    put("colorHex", colorHex ?: JSONObject.NULL); put("isArchived", isArchived); put("isDefault", isDefault)
    put("createdAt", createdAt); put("updatedAt", updatedAt)
}
private fun BudgetEntity.toJson() = JSONObject().apply { put("id", id); put("categoryId", categoryId ?: JSONObject.NULL); put("name", name); put("amountLimit", amountLimit); put("budgetType", budgetType); put("startDate", startDate ?: JSONObject.NULL); put("endDate", endDate ?: JSONObject.NULL); put("isActive", isActive); put("createdAt", createdAt); put("updatedAt", updatedAt) }
private fun PiggyBankEntity.toJson() = JSONObject().apply { put("id", id); put("name", name); put("targetAmount", targetAmount); put("currentAmount", currentAmount); put("weeklyIncomePrediction", weeklyIncomePrediction); put("selectedAllocationPercent", selectedAllocationPercent); put("minAllocationPercent", minAllocationPercent); put("maxAllocationPercent", maxAllocationPercent); put("isGoalPossible", isGoalPossible); put("targetDate", targetDate ?: JSONObject.NULL); put("createdAt", createdAt); put("updatedAt", updatedAt); put("isActive", isActive); put("isArchived", isArchived); put("notes", notes ?: JSONObject.NULL); put("allowOverSaving", allowOverSaving); put("completedAt", completedAt ?: JSONObject.NULL) }
private fun PiggyBankTransactionEntity.toJson() = JSONObject().apply { put("id", id); put("piggyBankId", piggyBankId); put("amount", amount); put("transactionType", transactionType); put("sourceTransactionId", sourceTransactionId ?: JSONObject.NULL); put("date", date); put("notes", notes ?: JSONObject.NULL); put("createdAt", createdAt) }
private fun PiggyBankMissedContributionEntity.toJson() = JSONObject().apply { put("id", id); put("piggyBankId", piggyBankId); put("expectedDate", expectedDate); put("expectedAmount", expectedAmount); put("actualAmount", actualAmount); put("missedAmount", missedAmount); put("weeklyIncomePredictionAtTheTime", weeklyIncomePredictionAtTheTime); put("selectedAllocationPercentAtTheTime", selectedAllocationPercentAtTheTime); put("status", status); put("adjustmentType", adjustmentType ?: JSONObject.NULL); put("notes", notes ?: JSONObject.NULL); put("originalTargetDate", originalTargetDate ?: JSONObject.NULL); put("adjustedTargetDate", adjustedTargetDate ?: JSONObject.NULL); put("catchUpAmountPerWeek", catchUpAmountPerWeek ?: JSONObject.NULL); put("affectedWeeksCount", affectedWeeksCount ?: JSONObject.NULL); put("createdAt", createdAt); put("updatedAt", updatedAt) }
private fun DebtEntity.toJson() = JSONObject().apply { put("id", id); put("name", name); put("personName", personName ?: JSONObject.NULL); put("debtType", debtType); put("totalAmount", totalAmount); put("amountPaid", amountPaid); put("remainingAmount", remainingAmount); put("reservedAmount", reservedAmount); put("dueDate", dueDate ?: JSONObject.NULL); put("nextDueDate", nextDueDate ?: JSONObject.NULL); put("startDate", startDate ?: JSONObject.NULL); put("endDate", endDate ?: JSONObject.NULL); put("paymentFrequency", paymentFrequency); put("customIntervalDays", customIntervalDays ?: JSONObject.NULL); put("installmentAmount", installmentAmount ?: JSONObject.NULL); put("isRecurring", isRecurring); put("status", status); put("notes", notes ?: JSONObject.NULL); put("createdAt", createdAt); put("updatedAt", updatedAt); put("isActive", isActive); put("isArchived", isArchived); put("completedAt", completedAt ?: JSONObject.NULL); put("priority", priority); put("autoReserveEnabled", autoReserveEnabled); put("reminderEnabled", reminderEnabled); put("reminderTimingDays", reminderTimingDays ?: JSONObject.NULL) }
private fun DebtTransactionEntity.toJson() = JSONObject().apply { put("id", id); put("debtId", debtId); put("amount", amount); put("transactionType", transactionType); put("sourceTransactionId", sourceTransactionId ?: JSONObject.NULL); put("date", date); put("notes", notes ?: JSONObject.NULL); put("createdAt", createdAt) }
private fun SubscriptionEntity.toJson() = JSONObject().apply { put("id", id); put("name", name); put("amount", amount); put("categoryId", categoryId ?: JSONObject.NULL); put("billingCycle", billingCycle); put("customIntervalDays", customIntervalDays ?: JSONObject.NULL); put("nextBillingDate", nextBillingDate ?: JSONObject.NULL); put("startDate", startDate ?: JSONObject.NULL); put("endDate", endDate ?: JSONObject.NULL); put("reserveEnabled", reserveEnabled); put("reservedAmount", reservedAmount); put("importance", importance); put("status", status); put("notes", notes ?: JSONObject.NULL); put("createdAt", createdAt); put("updatedAt", updatedAt); put("isActive", isActive); put("isArchived", isArchived); put("lastPaidDate", lastPaidDate ?: JSONObject.NULL); put("completedAt", completedAt ?: JSONObject.NULL); put("autoPay", autoPay); put("paymentMethod", paymentMethod ?: JSONObject.NULL); put("reminderEnabled", reminderEnabled) }
private fun SubscriptionTransactionEntity.toJson() = JSONObject().apply { put("id", id); put("subscriptionId", subscriptionId); put("amount", amount); put("transactionType", transactionType); put("sourceTransactionId", sourceTransactionId ?: JSONObject.NULL); put("date", date); put("notes", notes ?: JSONObject.NULL); put("createdAt", createdAt) }
private fun MonthlySummaryEntity.toJson() = JSONObject().apply { put("monthKey", monthKey); put("totalIncome", totalIncome); put("totalExpenses", totalExpenses); put("mainBalance", mainBalance); put("debtReserve", debtReserve); put("piggyBankTotal", piggyBankTotal); put("subscriptionReserve", subscriptionReserve); put("totalMoneyTracked", totalMoneyTracked); put("updatedAt", updatedAt) }
private fun MonthlyAiSummaryEntity.toJson() = JSONObject().apply { put("id", id); put("year", year); put("month", month); put("summaryText", summaryText); put("generatedAt", generatedAt); put("inputDataHash", inputDataHash ?: JSONObject.NULL); put("modelName", modelName ?: JSONObject.NULL); put("promptVersion", promptVersion); put("status", status); put("errorMessage", errorMessage ?: JSONObject.NULL); put("createdAt", createdAt); put("updatedAt", updatedAt) }
private fun ReminderEntity.toJson() = JSONObject().apply { put("id", id); put("reminderType", reminderType); put("linkedEntityId", linkedEntityId ?: JSONObject.NULL); put("linkedEntityType", linkedEntityType ?: JSONObject.NULL); put("title", title); put("message", message); put("scheduledAt", scheduledAt); put("triggerAt", triggerAt); put("reminderTiming", reminderTiming); put("customOffsetMinutes", customOffsetMinutes ?: JSONObject.NULL); put("isEnabled", isEnabled); put("status", status); put("createdAt", createdAt); put("updatedAt", updatedAt); put("sentAt", sentAt ?: JSONObject.NULL) }
private fun AppSettingsEntity.toJson() = JSONObject().apply { put("id", id); put("currencyCode", currencyCode); put("pinEnabled", pinEnabled); put("biometricEnabled", biometricEnabled); put("remindersEnabled", remindersEnabled); put("debtRemindersEnabled", debtRemindersEnabled); put("subscriptionRemindersEnabled", subscriptionRemindersEnabled); put("budgetAlertsEnabled", budgetAlertsEnabled); put("piggyBankRemindersEnabled", piggyBankRemindersEnabled); put("missedContributionRemindersEnabled", missedContributionRemindersEnabled); put("defaultReminderTiming", defaultReminderTiming); put("defaultCustomOffsetMinutes", defaultCustomOffsetMinutes ?: JSONObject.NULL); put("aiSummaryEnabled", aiSummaryEnabled) }

private fun JSONArray.toCategoryEntities() = List(length()) { i -> getJSONObject(i).run { CategoryEntity(requiredLong("id"), requiredString("name"), requiredString("type"), optNullableString("iconName"), optNullableString("colorHex"), optBoolean("isArchived"), optBoolean("isDefault"), requiredLong("createdAt"), requiredLong("updatedAt")) } }
private fun JSONArray.toTransactionEntities() = List(length()) { i -> getJSONObject(i).run { TransactionEntity(requiredLong("id"), requiredLong("amount"), requiredString("type"), optNullableLong("categoryId"), optString("description"), optNullableString("note"), requiredLong("occurredAt"), requiredLong("createdAt"), requiredLong("updatedAt")) } }
private fun JSONArray.toBudgetEntities() = List(length()) { i -> getJSONObject(i).run { BudgetEntity(requiredLong("id"), optNullableLong("categoryId"), optString("name"), requiredLong("amountLimit"), requiredString("budgetType"), optNullableLong("startDate"), optNullableLong("endDate"), optBoolean("isActive", true), requiredLong("createdAt"), requiredLong("updatedAt")) } }
private fun JSONArray.toPiggyBankEntities() = List(length()) { i -> getJSONObject(i).run { PiggyBankEntity(requiredLong("id"), optString("name"), requiredLong("targetAmount"), requiredLong("currentAmount"), requiredLong("weeklyIncomePrediction"), requiredLong("selectedAllocationPercent").toInt(), requiredLong("minAllocationPercent").toInt(), requiredLong("maxAllocationPercent").toInt(), optBoolean("isGoalPossible"), optNullableLong("targetDate"), requiredLong("createdAt"), requiredLong("updatedAt"), optBoolean("isActive", true), optBoolean("isArchived"), optNullableString("notes"), optBoolean("allowOverSaving"), optNullableLong("completedAt")) } }
private fun JSONArray.toPiggyBankTransactionEntities() = List(length()) { i -> getJSONObject(i).run { PiggyBankTransactionEntity(requiredLong("id"), requiredLong("piggyBankId"), requiredLong("amount"), requiredString("transactionType"), optNullableLong("sourceTransactionId"), requiredLong("date"), optNullableString("notes"), requiredLong("createdAt")) } }
private fun JSONArray.toPiggyBankMissedContributionEntities() = List(length()) { i -> getJSONObject(i).run { PiggyBankMissedContributionEntity(requiredLong("id"), requiredLong("piggyBankId"), requiredLong("expectedDate"), requiredLong("expectedAmount"), requiredLong("actualAmount"), requiredLong("missedAmount"), requiredLong("weeklyIncomePredictionAtTheTime"), requiredLong("selectedAllocationPercentAtTheTime").toInt(), requiredString("status"), optNullableString("adjustmentType"), optNullableString("notes"), optNullableLong("originalTargetDate"), optNullableLong("adjustedTargetDate"), optNullableLong("catchUpAmountPerWeek"), optNullableLong("affectedWeeksCount")?.toInt(), requiredLong("createdAt"), requiredLong("updatedAt")) } }
private fun JSONArray.toDebtEntities() = List(length()) { i -> getJSONObject(i).run { DebtEntity(requiredLong("id"), optString("name"), optNullableString("personName"), optString("debtType", "I_OWE"), optLong("totalAmount"), optLong("amountPaid"), optLong("remainingAmount"), optLong("reservedAmount"), optNullableLong("dueDate"), optNullableLong("nextDueDate"), optNullableLong("startDate"), optNullableLong("endDate"), optString("paymentFrequency", "ONE_TIME"), optNullableLong("customIntervalDays")?.toInt(), optNullableLong("installmentAmount"), optBoolean("isRecurring"), optString("status", "ACTIVE"), optNullableString("notes"), optLong("createdAt"), optLong("updatedAt"), optBoolean("isActive", true), optBoolean("isArchived"), optNullableLong("completedAt"), optInt("priority", 0), optBoolean("autoReserveEnabled", true), optNullableLong("reservePercent")?.toInt(), optNullableLong("fixedReserveAmount"), optNullableLong("lastPaymentDate"), optBoolean("reminderEnabled"), optNullableLong("reminderTimingDays")?.toInt()) } }
private fun JSONArray.toDebtTransactionEntities() = List(length()) { i -> getJSONObject(i).run { DebtTransactionEntity(requiredLong("id"), requiredLong("debtId"), requiredLong("amount"), requiredString("transactionType"), optNullableLong("sourceTransactionId"), requiredLong("date"), optNullableString("notes"), requiredLong("createdAt")) } }
private fun JSONArray.toSubscriptionEntities() = List(length()) { i -> getJSONObject(i).run { SubscriptionEntity(requiredLong("id"), optString("name"), optLong("amount"), optNullableLong("categoryId"), optString("billingCycle", "MONTHLY"), optNullableLong("customIntervalDays")?.toInt(), optNullableLong("nextBillingDate") ?: optNullableLong("nextDueDate"), optNullableLong("startDate"), optNullableLong("endDate"), optBoolean("reserveEnabled"), optLong("reservedAmount"), optString("importance", "MEDIUM"), optString("status", "ACTIVE"), optNullableString("notes"), optLong("createdAt"), optLong("updatedAt"), optBoolean("isActive", true), optBoolean("isArchived"), optNullableLong("lastPaidDate"), optNullableLong("completedAt"), optBoolean("autoPay"), optNullableString("paymentMethod"), optBoolean("reminderEnabled")) } }
private fun JSONArray.toSubscriptionTransactionEntities() = List(length()) { i -> getJSONObject(i).run { SubscriptionTransactionEntity(requiredLong("id"), requiredLong("subscriptionId"), requiredLong("amount"), requiredString("transactionType"), optNullableLong("sourceTransactionId"), requiredLong("date"), optNullableString("notes"), requiredLong("createdAt")) } }
private fun JSONArray.toMonthlySummaryEntities() = List(length()) { i -> getJSONObject(i).run { MonthlySummaryEntity(requiredString("monthKey"), optLong("totalIncome"), optLong("totalExpenses"), optLong("mainBalance"), optLong("debtReserve"), optLong("piggyBankTotal"), optLong("subscriptionReserve"), optLong("totalMoneyTracked"), optLong("updatedAt")) } }
private fun JSONArray.toMonthlyAiSummaryEntities() = List(length()) { i -> getJSONObject(i).run { MonthlyAiSummaryEntity(requiredLong("id"), requiredLong("year").toInt(), requiredLong("month").toInt(), requiredString("summaryText"), requiredLong("generatedAt"), optNullableString("inputDataHash"), optNullableString("modelName"), optString("promptVersion", MonthlyAiSummaryEntity.PROMPT_VERSION), optString("status", MonthlyAiSummaryEntity.STATUS_GENERATED), optNullableString("errorMessage"), requiredLong("createdAt"), requiredLong("updatedAt")) } }
private fun JSONArray.toReminderEntities() = List(length()) { i -> getJSONObject(i).run { ReminderEntity(requiredLong("id"), requiredString("reminderType"), optNullableLong("linkedEntityId"), optNullableString("linkedEntityType"), requiredString("title"), requiredString("message"), requiredLong("scheduledAt"), requiredLong("triggerAt"), requiredString("reminderTiming"), optNullableLong("customOffsetMinutes")?.toInt(), optBoolean("isEnabled", true), optString("status", ReminderEntity.STATUS_SCHEDULED), requiredLong("createdAt"), requiredLong("updatedAt"), optNullableLong("sentAt")) } }
private fun JSONObject.toSettingsEntity() = AppSettingsEntity(optInt("id", 1), optString("currencyCode", "PHP"), optBoolean("pinEnabled"), optBoolean("biometricEnabled"), optBoolean("remindersEnabled"), optBoolean("debtRemindersEnabled", true), optBoolean("subscriptionRemindersEnabled", true), optBoolean("budgetAlertsEnabled", true), optBoolean("piggyBankRemindersEnabled", true), optBoolean("missedContributionRemindersEnabled", true), optString("defaultReminderTiming", "ONE_DAY_BEFORE"), optNullableLong("defaultCustomOffsetMinutes")?.toInt(), optBoolean("aiSummaryEnabled"))
private fun JSONObject.optNullableString(name: String): String? = if (isNull(name)) null else optString(name)
private fun JSONObject.optNullableLong(name: String): Long? = if (isNull(name)) null else optLong(name)
