package com.krendstudio.cloudledger.model

data class RecurringTemplate(
    val id: String,
    val title: String,
    val amount: Double,
    val type: String,
    val category: String,
    val note: String?,
    val intervalMonths: Int,
    val executeDay: Int,
    val nextRunAt: Long,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val totalRuns: Int?,
    val remainingRuns: Int?
)
