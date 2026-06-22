package com.krendstudio.cloudledger.util

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

object DateUtils {
    fun parseLocalDate(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        val trimmed = value.trim()
        if (trimmed.length == 10 && trimmed[4] == '-' && trimmed[7] == '-') {
            return runCatching { LocalDate.parse(trimmed) }.getOrNull()
        }
        val taipeiZone = ZoneId.of("Asia/Taipei")
        runCatching { return Instant.parse(trimmed).atZone(taipeiZone).toLocalDate() }
        runCatching { return OffsetDateTime.parse(trimmed).atZoneSameInstant(taipeiZone).toLocalDate() }
        val normalized = trimmed.substringBefore('T').substringBefore(' ')
        return runCatching { LocalDate.parse(normalized) }.getOrNull()
    }
}
