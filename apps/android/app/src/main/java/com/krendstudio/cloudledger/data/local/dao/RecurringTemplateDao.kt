package com.krendstudio.cloudledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.krendstudio.cloudledger.data.local.entity.RecurringTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringTemplateDao {
    @Query("SELECT * FROM recurring_templates WHERE ledgerId = :ledgerId AND userId = :userId")
    fun observeByLedgerAndUser(ledgerId: String, userId: String): Flow<List<RecurringTemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<RecurringTemplateEntity>)

    @Query("DELETE FROM recurring_templates WHERE ledgerId = :ledgerId AND userId = :userId")
    suspend fun deleteByLedgerAndUser(ledgerId: String, userId: String)

    @Query("DELETE FROM recurring_templates WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}
