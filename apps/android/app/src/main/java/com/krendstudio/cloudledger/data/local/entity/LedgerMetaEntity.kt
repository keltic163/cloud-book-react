package com.krendstudio.cloudledger.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ledger_meta")
data class LedgerMetaEntity(
    @PrimaryKey val ledgerId: String,
    val expenseCategoriesJson: String,
    val incomeCategoriesJson: String,
    val membersJson: String,
    val updatedAt: Long
)
