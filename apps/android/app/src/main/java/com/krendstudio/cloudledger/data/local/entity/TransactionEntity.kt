package com.krendstudio.cloudledger.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val ledgerId: String,
    val amount: Double,
    val type: String,
    val category: String,
    val description: String,
    val rewards: Double,
    val date: String,
    val creatorUid: String,
    val targetUserUid: String?,
    val createdAt: Long,
    val updatedAt: Long?,
    val deleted: Boolean
)
