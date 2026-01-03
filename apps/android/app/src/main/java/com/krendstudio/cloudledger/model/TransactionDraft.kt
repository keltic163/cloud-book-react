package com.krendstudio.cloudledger.model

data class TransactionDraft(
    val amount: Double,
    val type: TransactionType,
    val category: String,
    val description: String,
    val rewards: Double,
    val date: String,
    val targetUserUid: String?
)
