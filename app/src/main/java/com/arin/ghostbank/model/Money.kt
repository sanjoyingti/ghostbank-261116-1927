=== ABOUT: Keeps every amount in whole paise and formats them the Indian way ===
package com.arin.ghostbank.model

import java.text.NumberFormat
import java.util.Locale

object Money {

    /** The one paisa that a harvest takes, and the rail floor it may have to upgrade to. */
    const val HARVEST_PAISE = 1L
    const val FLOOR_PAISE = 100L

    private val inr: NumberFormat = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 0
        isGroupingUsed = true
    }

    fun format(paise: Long): String {
        val sign = if (paise < 0) "-" else ""
        val abs = kotlin.math.abs(paise)
        val rupees = abs / 100
        val fraction = (abs % 100).toInt()
        val base = inr.format(rupees)
        return if (fraction == 0) "$sign₹$base" else "$sign₹$base.${fraction.toString().padStart(2, '0')}"
    }

    fun paiseOnly(paise: Long): String = if (paise == 1L) "1 paisa" else "$paise paise"

    /** Plain rupee string for form posts — never a float. */
    fun railString(paise: Long): String {
        val rupees = paise / 100
        val fraction = (paise % 100).toInt()
        return if (fraction == 0) "$rupees" else "$rupees.${fraction.toString().padStart(2, '0')}"
    }

    /** Parses "12", "12.5", "12.50" into paise without ever touching a Double. */
    fun parseRupees(text: String): Long? {
        val clean = text.trim().replace(",", "").replace("₹", "")
        if (clean.isEmpty()) return null
        if (!Regex("^\\d{1,9}(\\.\\d{0,2})?$").matches(clean)) return null
        val whole = clean.substringBefore('.')
        val fractionPart = clean.substringAfter('.', "")
        val wholePaise = (whole.toLongOrNull() ?: return null) * 100
        if (fractionPart.isEmpty()) return wholePaise
        val fraction = fractionPart.padEnd(2, '0').toLongOrNull() ?: return 0L
        return wholePaise + fraction
    }
}
