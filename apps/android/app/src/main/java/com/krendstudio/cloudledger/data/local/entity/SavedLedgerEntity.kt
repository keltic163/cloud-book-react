package com.krendstudio.cloudledger.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "saved_ledgers",
    primaryKeys = ["userUid", "ledgerId"]
)
data class SavedLedgerEntity(
    val userUid: String,
    val ledgerId: String,
    val alias: String,
    val lastAccessedAt: Long
)
