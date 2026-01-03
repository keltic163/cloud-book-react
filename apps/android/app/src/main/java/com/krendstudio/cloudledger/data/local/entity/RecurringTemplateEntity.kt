package com.krendstudio.cloudledger.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recurring_templates")
data class RecurringTemplateEntity(
    @PrimaryKey val id: String,
    val ledgerId: String,
    val userId: String,
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
