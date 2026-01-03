package com.krendstudio.cloudledger.model

enum class TransactionType {
    INCOME,
    EXPENSE
}

data class Transaction(
    val id: String,
    val amount: Double,
    val type: TransactionType,
    val category: String,
    val description: String,
    val rewards: Double,
    val date: String,
    val creatorUid: String,
    val targetUserUid: String?,
    val ledgerId: String,
    val createdAt: Long,
    val updatedAt: Long?,
    val deleted: Boolean
)
