package com.krendstudio.cloudledger.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.krendstudio.cloudledger.data.local.dao.LedgerMetaDao
import com.krendstudio.cloudledger.data.local.dao.RecurringTemplateDao
import com.krendstudio.cloudledger.data.local.dao.SavedLedgerDao
import com.krendstudio.cloudledger.data.local.dao.SyncStateDao
import com.krendstudio.cloudledger.data.local.dao.TransactionDao
import com.krendstudio.cloudledger.data.local.dao.UserProfileDao
import com.krendstudio.cloudledger.data.local.entity.LedgerMetaEntity
import com.krendstudio.cloudledger.data.local.entity.RecurringTemplateEntity
import com.krendstudio.cloudledger.data.local.entity.SavedLedgerEntity
import com.krendstudio.cloudledger.data.local.entity.SyncStateEntity
import com.krendstudio.cloudledger.data.local.entity.TransactionEntity
import com.krendstudio.cloudledger.data.local.entity.UserProfileEntity

@Database(
    entities = [
        SavedLedgerEntity::class,
        UserProfileEntity::class,
        TransactionEntity::class,
        LedgerMetaEntity::class,
        RecurringTemplateEntity::class,
        SyncStateEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedLedgerDao(): SavedLedgerDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun transactionDao(): TransactionDao
    abstract fun ledgerMetaDao(): LedgerMetaDao
    abstract fun recurringTemplateDao(): RecurringTemplateDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cloud_ledger.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
        }
    }
}
