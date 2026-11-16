=== ABOUT: Fixed money handling: everything is counted in paise so a paisa is never rounded away ===
package com.arin.ghostbank.ui.components

fun paiseToRupees(paise: Long): String {
    val sign = if (paise < 0) "-" else ""
    val abs = kotlin.math.abs(paise)
    return "$sign₹${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
}

fun paiseToCompact(paise: Long): String =
    if (paise < 100) "$paise paise" else paiseToRupees(paise)

fun rupeesToPaise(text: String): Long? {
    val clean = text.trim()
    if (clean.isEmpty()) return null
    val parts = clean.split(".")
    if (parts.size > 2) return null
    val rupees = parts[0].ifEmpty { "0" }.toLongOrNull() ?: return null
    val paise = when {
        parts.size == 1 -> 0L
        parts[1].isEmpty() -> 0L
        else -> (parts[1] + "00").take(2).toLongOrNull() ?: return null
    }
    return rupees * 100 + paise
}
