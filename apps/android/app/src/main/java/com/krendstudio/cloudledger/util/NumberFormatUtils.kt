package com.krendstudio.cloudledger.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale

private val integerFormat = NumberFormat.getIntegerInstance(Locale.US)
private val decimalFormat = DecimalFormat("###,##0.##", DecimalFormatSymbols(Locale.US))
private val plainDecimalFormat = DecimalFormat("0.##", DecimalFormatSymbols(Locale.US))

fun formatNumber(value: Double): String {
    return if (value == value.toLong().toDouble()) {
        integerFormat.format(value.toLong())
    } else {
        decimalFormat.format(value)
    }
}

fun formatPlainNumber(value: Double): String {
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        plainDecimalFormat.format(value)
    }
}
