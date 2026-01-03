package com.krendstudio.cloudledger.util

import java.time.LocalDate

object DateUtils {
    fun parseLocalDate(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        val normalized = value.substringBefore('T').substringBefore(' ')
        return runCatching { LocalDate.parse(normalized) }.getOrNull()
    }
}
