package com.krendstudio.cloudledger.util

object CsvUtils {
    fun encode(fields: List<String>): String {
        return fields.joinToString(",") { field ->
            val needsQuote = field.contains(",") || field.contains("\"") || field.contains("\n")
            if (!needsQuote) {
                field
            } else {
                "\"${field.replace("\"", "\"\"")}\""
            }
        }
    }

    fun parseLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i += 1
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
            i += 1
        }
        result.add(current.toString())
        return result
    }
}
