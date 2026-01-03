package com.krendstudio.cloudledger.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey val userUid: String,
    val lastLedgerId: String?
)
