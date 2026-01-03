package com.krendstudio.cloudledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.krendstudio.cloudledger.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE ledgerId = :ledgerId ORDER BY date DESC")
    fun observeByLedger(ledgerId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE ledgerId = :ledgerId")
    suspend fun getByLedger(ledgerId: String): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TransactionEntity>)

    @Query("DELETE FROM transactions WHERE ledgerId = :ledgerId")
    suspend fun deleteByLedger(ledgerId: String)

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}
