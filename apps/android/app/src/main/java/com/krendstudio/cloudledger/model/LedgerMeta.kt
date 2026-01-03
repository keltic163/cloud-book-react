package com.krendstudio.cloudledger.model

data class LedgerMeta(
    val expenseCategories: List<String>,
    val incomeCategories: List<String>,
    val members: List<LedgerMember>
)
