package com.krendstudio.cloudledger.model

data class UserProfile(
    val uid: String,
    val lastLedgerId: String?,
    val savedLedgers: List<SavedLedger>
)
