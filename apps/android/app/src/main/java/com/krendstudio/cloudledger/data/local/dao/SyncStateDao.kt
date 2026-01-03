package com.krendstudio.cloudledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.krendstudio.cloudledger.data.local.entity.SyncStateEntity

@Dao
interface SyncStateDao {
    @Query("SELECT value FROM sync_state WHERE `key` = :key LIMIT 1")
    suspend fun getValue(key: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SyncStateEntity)
}
