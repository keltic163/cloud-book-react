package com.krendstudio.cloudledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.krendstudio.cloudledger.data.local.entity.LedgerMetaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerMetaDao {
    @Query("SELECT * FROM ledger_meta WHERE ledgerId = :ledgerId LIMIT 1")
    fun observe(ledgerId: String): Flow<LedgerMetaEntity?>

    @Query("SELECT * FROM ledger_meta WHERE ledgerId = :ledgerId LIMIT 1")
    suspend fun get(ledgerId: String): LedgerMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LedgerMetaEntity)

    @Query("DELETE FROM ledger_meta WHERE ledgerId = :ledgerId")
    suspend fun deleteByLedger(ledgerId: String)
}
