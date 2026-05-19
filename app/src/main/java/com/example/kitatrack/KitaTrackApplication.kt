package com.example.kitatrack

import android.app.Application
import androidx.room.Room
import com.example.kitatrack.data.local.KitaTrackDatabase
import com.example.kitatrack.data.repository.CategoryRepository
import com.example.kitatrack.data.repository.BackupRepository
import com.example.kitatrack.data.repository.TransactionRepository
import com.example.kitatrack.data.repository.BudgetRepository
import com.example.kitatrack.data.repository.DebtRepository
import com.example.kitatrack.data.repository.PiggyBankRepository
import com.example.kitatrack.data.repository.SubscriptionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KitaTrackApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: KitaTrackDatabase by lazy {
        Room.databaseBuilder(
            this,
            KitaTrackDatabase::class.java,
            "kitatrack.db"
        )
            .addMigrations(KitaTrackDatabase.MIGRATION_1_2)
            .addMigrations(KitaTrackDatabase.MIGRATION_2_3)
            .addMigrations(KitaTrackDatabase.MIGRATION_3_4)
            .addMigrations(KitaTrackDatabase.MIGRATION_4_5)
            .addMigrations(KitaTrackDatabase.MIGRATION_5_6)
            .addMigrations(KitaTrackDatabase.MIGRATION_6_7)
            .addMigrations(KitaTrackDatabase.MIGRATION_7_8)
            .addMigrations(KitaTrackDatabase.MIGRATION_8_9)
            .build()
    }

    val transactionRepository by lazy { TransactionRepository(database.transactionDao()) }
    val categoryRepository by lazy { CategoryRepository(database.categoryDao()) }
    val budgetRepository by lazy { BudgetRepository(database.budgetDao(), database.transactionDao(), database.categoryDao()) }
    val debtRepository by lazy { DebtRepository(database.debtDao(), database.debtTransactionDao(), database.transactionDao(), database.categoryDao()) }
    val piggyBankRepository by lazy { PiggyBankRepository(database.piggyBankDao(), database.piggyBankTransactionDao(), database.piggyBankMissedContributionDao()) }
    val subscriptionRepository by lazy { SubscriptionRepository(database.subscriptionDao(), database.subscriptionTransactionDao(), database.transactionDao(), database.categoryDao()) }
    val backupRepository by lazy { BackupRepository(database) }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            categoryRepository.ensureDefaultCategories()
        }
    }
}
