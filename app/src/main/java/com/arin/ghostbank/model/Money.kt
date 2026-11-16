package com.arin.ghostbank.model

object Money {

    /** "1.25" -> 125, "40" -> 4000. Returns null when the text is not a clean rupee amount. */
    fun parseRupees(text: String): Long? {
        val clean = text.trim().removePrefix("₹").replace(",", "")
        if (clean.isEmpty()) return null
        if (!Regex("^\\d{1,9}(\\.\\d{1,2})?$").matches(clean)) return null
        val parts = clean.split(".")
        val rupees = parts[0].toLongOrNull() ?: return null
        val paise = if (parts.size == 2) parts[1].padEnd(2, '0').toLongOrNull() ?: 0L else 0L
        return rupees * 100 + paise
    }

    fun format(paise: Long): String = "₹" + (paise / 100) + "." + (paise % 100).toString().padStart(2, '0')

    fun paiseLabel(paise: Long): String =
        if (paise == 1L) "1 paisa" else "$paise paise"

    fun short(paise: Long): String =
        when {
            paise >= 10_000_000L -> "₹" + (paise / 10_000_000L) + "L"
            paise >= 100_000L -> "₹" + (paise / 100_000L) + "T"
            paise >= 1_000L -> "₹" + (paise / 1_000L) + "k"
            else -> format(paise)
        }
}
