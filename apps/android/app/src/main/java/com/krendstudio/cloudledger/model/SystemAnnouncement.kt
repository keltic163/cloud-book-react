package com.krendstudio.cloudledger.model

data class SystemAnnouncement(
    val text: String,
    val isEnabled: Boolean,
    val startAt: Long,
    val endAt: Long,
    val type: String?
)
