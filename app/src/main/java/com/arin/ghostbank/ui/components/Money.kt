package com.arin.ghostbank.ui.components

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun groupDigits(value: Long): String {
    val text = value.toString()
    val builder = StringBuilder()
    text.reversed().forEachIndexed { index, char ->
        if (index > 0 && index % 3 == 0) builder.append(',')
        builder.append(char)
    }
    return builder.reverse().toString()
}

fun paiseToRupees(paise: Long): String {
    val negative = paise < 0
    val abs = kotlin.math.abs(paise)
    val rupees = abs / 100
    val fraction = abs % 100
    return (if (negative) "-" else "") + "₹" + groupDigits(rupees) + "." + fraction.toString().padStart(2, '0')
}

fun parseRupeesToPaise(text: String): Long? {
    val clean = text.trim().removePrefix("₹").replace(",", "")
    if (clean.isEmpty()) return null
    val parts = clean.split(".")
    if (parts.size > 2) return null
    val rupees = parts[0].toLongOrNull() ?: return null
    val fraction = when {
        parts.size == 1 -> 0L
        parts[1].isEmpty() -> 0L
        parts[1].length == 1 -> (parts[1].toLongOrNull() ?: return null) * 10
        else -> parts[1].take(2).toLongOrNull() ?: return null
    }
    return rupees * 100 + fraction
}

fun stamp(ts: Long): String = SimpleDateFormat("dd MMM HH:mm:ss", Locale.US).format(Date(ts))
