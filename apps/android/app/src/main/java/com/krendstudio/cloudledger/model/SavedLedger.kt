package com.krendstudio.cloudledger.model

data class SavedLedger(
    val id: String,
    val alias: String,
    val lastAccessedAt: Long
)
