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
                put("databaseVersion", 7)
            })
            put("transactions", JSONArray(database.transactionDao().getAllForExport().map { it.transaction.toJson() }))
            put("categories", JSONArray(database.categoryDao().getAllForExport().map { it.toJson() }))
            put("budgets", JSONArray(database.budgetDao().getAllForExport().map { it.toJson() }))
            put("piggyBanks", JSONArray(database.piggyBankDao().getAllForExport().map { it.toJson() }))
            put("piggyBankTransactions", JSONArray(database.piggyBankTransactionDao().getAllForExport().map { it.toJson() }))
            put("piggyBankMissedContributions", JSONArray(database.piggyBankMissedContributionDao().getAllForExport().map { it.toJson() }))
            put("debts", JSONArray(database.debtDao().getAllForExport().map { it.toJson() }))
            put("subscriptions", JSONArray(database.subscriptionDao().getAllForExport().map { it.toJson() }))
            put("monthlySummaries", JSONArray(database.monthlySummaryDao().getAllForExport().map { it.toJson() }))
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
        val subscriptions = root.optJSONArray("subscriptions")?.toSubscriptionEntities().orEmpty()
        val monthlySummaries = root.optJSONArray("monthlySummaries")?.toMonthlySummaryEntities().orEmpty()
        val settings = root.optJSONObject("settings")?.takeIf { it.length() > 0 }?.toSettingsEntity()
        when (mode) {
            RestoreMode.REPLACE -> replaceAll(categories, transactions, budgets, piggyBanks, piggyBankTransactions, piggyBankMissedContributions, debts, subscriptions, monthlySummaries, settings)
            RestoreMode.MERGE_NEWEST_WINS -> mergeNewestWins(categories, transactions, budgets, piggyBanks, piggyBankTransactions, piggyBankMissedContributions, monthlySummaries, settings)
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
            database.debtDao().clearAll()
            database.subscriptionDao().clearAll()
            database.monthlySummaryDao().clearAll()
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
        subscriptions: List<SubscriptionEntity>,
        monthlySummaries: List<MonthlySummaryEntity>,
        settings: AppSettingsEntity?
    ) {
        database.withTransaction {
            database.transactionDao().clearAll()
            database.categoryDao().clearAll()
            database.budgetDao().clearAll()
            database.piggyBankDao().clearAll()
            database.piggyBankTransactionDao().clearAll()
            database.piggyBankMissedContributionDao().clearAll()
            database.debtDao().clearAll()
            database.subscriptionDao().clearAll()
            database.monthlySummaryDao().clearAll()
            database.appSettingsDao().clearAll()
            database.categoryDao().replaceAll(categories)
            database.transactionDao().insertAll(transactions)
            database.budgetDao().insertAll(budgets)
            database.piggyBankDao().insertAll(piggyBanks)
            database.piggyBankTransactionDao().insertAll(piggyBankTransactions)
            database.piggyBankMissedContributionDao().insertAll(piggyBankMissedContributions)
            database.debtDao().insertAll(debts)
            database.subscriptionDao().insertAll(subscriptions)
            database.monthlySummaryDao().insertAll(monthlySummaries)
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
        importedMonthlySummaries: List<MonthlySummaryEntity>,
        importedSettings: AppSettingsEntity?
    ) {
        database.withTransaction {
            val existingCategories = database.categoryDao().getAllForExport()
            val existingTransactions = database.transactionDao().getAllForExport().map { it.transaction }
            val existingBudgets = database.budgetDao().getAllForExport()
            val existingPiggies = database.piggyBankDao().getAllForExport()
            val existingSummaries = database.monthlySummaryDao().getAllForExport()
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
            val mergedSummaries = mergeByIdNewest(existingSummaries, importedMonthlySummaries, { it.monthKey }, { it.updatedAt })
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
            database.monthlySummaryDao().clearAll()
            database.appSettingsDao().clearAll()
            database.categoryDao().replaceAll(categoryMerge.categories)
            database.transactionDao().insertAll(mergedTransactions)
            database.budgetDao().insertAll(mergedBudgets)
            database.piggyBankDao().insertAll(mergedPiggies)
            database.piggyBankTransactionDao().insertAll(importedPiggyTransactions)
            database.piggyBankMissedContributionDao().insertAll(importedPiggyMissedContributions)
            database.monthlySummaryDao().insertAll(mergedSummaries)
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
private fun DebtEntity.toJson() = JSONObject().apply { put("id", id); put("name", name); put("totalAmount", totalAmount); put("remainingAmount", remainingAmount); put("dueDate", dueDate ?: JSONObject.NULL); put("isActive", isActive) }
private fun SubscriptionEntity.toJson() = JSONObject().apply { put("id", id); put("name", name); put("amount", amount); put("billingCycle", billingCycle); put("nextDueDate", nextDueDate ?: JSONObject.NULL); put("reserveEnabled", reserveEnabled); put("isActive", isActive) }
private fun MonthlySummaryEntity.toJson() = JSONObject().apply { put("monthKey", monthKey); put("totalIncome", totalIncome); put("totalExpenses", totalExpenses); put("mainBalance", mainBalance); put("debtReserve", debtReserve); put("piggyBankTotal", piggyBankTotal); put("subscriptionReserve", subscriptionReserve); put("totalMoneyTracked", totalMoneyTracked); put("updatedAt", updatedAt) }
private fun AppSettingsEntity.toJson() = JSONObject().apply { put("id", id); put("currencyCode", currencyCode); put("pinEnabled", pinEnabled); put("biometricEnabled", biometricEnabled); put("remindersEnabled", remindersEnabled); put("aiSummaryEnabled", aiSummaryEnabled) }

private fun JSONArray.toCategoryEntities() = List(length()) { i -> getJSONObject(i).run { CategoryEntity(requiredLong("id"), requiredString("name"), requiredString("type"), optNullableString("iconName"), optNullableString("colorHex"), optBoolean("isArchived"), optBoolean("isDefault"), requiredLong("createdAt"), requiredLong("updatedAt")) } }
private fun JSONArray.toTransactionEntities() = List(length()) { i -> getJSONObject(i).run { TransactionEntity(requiredLong("id"), requiredLong("amount"), requiredString("type"), optNullableLong("categoryId"), optString("description"), optNullableString("note"), requiredLong("occurredAt"), requiredLong("createdAt"), requiredLong("updatedAt")) } }
private fun JSONArray.toBudgetEntities() = List(length()) { i -> getJSONObject(i).run { BudgetEntity(requiredLong("id"), optNullableLong("categoryId"), optString("name"), requiredLong("amountLimit"), requiredString("budgetType"), optNullableLong("startDate"), optNullableLong("endDate"), optBoolean("isActive", true), requiredLong("createdAt"), requiredLong("updatedAt")) } }
private fun JSONArray.toPiggyBankEntities() = List(length()) { i -> getJSONObject(i).run { PiggyBankEntity(requiredLong("id"), optString("name"), requiredLong("targetAmount"), requiredLong("currentAmount"), requiredLong("weeklyIncomePrediction"), requiredLong("selectedAllocationPercent").toInt(), requiredLong("minAllocationPercent").toInt(), requiredLong("maxAllocationPercent").toInt(), optBoolean("isGoalPossible"), optNullableLong("targetDate"), requiredLong("createdAt"), requiredLong("updatedAt"), optBoolean("isActive", true), optBoolean("isArchived"), optNullableString("notes"), optBoolean("allowOverSaving"), optNullableLong("completedAt")) } }
private fun JSONArray.toPiggyBankTransactionEntities() = List(length()) { i -> getJSONObject(i).run { PiggyBankTransactionEntity(requiredLong("id"), requiredLong("piggyBankId"), requiredLong("amount"), requiredString("transactionType"), optNullableLong("sourceTransactionId"), requiredLong("date"), optNullableString("notes"), requiredLong("createdAt")) } }
private fun JSONArray.toPiggyBankMissedContributionEntities() = List(length()) { i -> getJSONObject(i).run { PiggyBankMissedContributionEntity(requiredLong("id"), requiredLong("piggyBankId"), requiredLong("expectedDate"), requiredLong("expectedAmount"), requiredLong("actualAmount"), requiredLong("missedAmount"), requiredLong("weeklyIncomePredictionAtTheTime"), requiredLong("selectedAllocationPercentAtTheTime").toInt(), requiredString("status"), optNullableString("adjustmentType"), optNullableString("notes"), optNullableLong("originalTargetDate"), optNullableLong("adjustedTargetDate"), optNullableLong("catchUpAmountPerWeek"), optNullableLong("affectedWeeksCount")?.toInt(), requiredLong("createdAt"), requiredLong("updatedAt")) } }
private fun JSONArray.toDebtEntities() = List(length()) { i -> getJSONObject(i).run { DebtEntity(requiredLong("id"), optString("name"), optLong("totalAmount"), optLong("remainingAmount"), optNullableLong("dueDate"), optBoolean("isActive", true)) } }
private fun JSONArray.toSubscriptionEntities() = List(length()) { i -> getJSONObject(i).run { SubscriptionEntity(requiredLong("id"), optString("name"), optLong("amount"), optString("billingCycle"), optNullableLong("nextDueDate"), optBoolean("reserveEnabled"), optBoolean("isActive", true)) } }
private fun JSONArray.toMonthlySummaryEntities() = List(length()) { i -> getJSONObject(i).run { MonthlySummaryEntity(requiredString("monthKey"), optLong("totalIncome"), optLong("totalExpenses"), optLong("mainBalance"), optLong("debtReserve"), optLong("piggyBankTotal"), optLong("subscriptionReserve"), optLong("totalMoneyTracked"), optLong("updatedAt")) } }
private fun JSONObject.toSettingsEntity() = AppSettingsEntity(optInt("id", 1), optString("currencyCode", "PHP"), optBoolean("pinEnabled"), optBoolean("biometricEnabled"), optBoolean("remindersEnabled"), optBoolean("aiSummaryEnabled"))
private fun JSONObject.optNullableString(name: String): String? = if (isNull(name)) null else optString(name)
private fun JSONObject.optNullableLong(name: String): Long? = if (isNull(name)) null else optLong(name)
