package com.krendstudio.cloudledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.krendstudio.cloudledger.data.local.entity.SavedLedgerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedLedgerDao {
    @Query("SELECT * FROM saved_ledgers WHERE userUid = :userUid ORDER BY lastAccessedAt DESC")
    fun observeSavedLedgers(userUid: String): Flow<List<SavedLedgerEntity>>

    @Query("SELECT * FROM saved_ledgers WHERE userUid = :userUid ORDER BY lastAccessedAt DESC")
    suspend fun getSavedLedgers(userUid: String): List<SavedLedgerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SavedLedgerEntity>)

    @Query("DELETE FROM saved_ledgers WHERE userUid = :userUid")
    suspend fun clearForUser(userUid: String)
}
