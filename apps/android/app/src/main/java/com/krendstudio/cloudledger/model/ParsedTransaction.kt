package com.krendstudio.cloudledger.model

data class ParsedTransaction(
    val amount: Double,
    val type: TransactionType,
    val category: String,
    val description: String,
    val rewards: Double,
    val date: String?
)
