=== ABOUT: Small helpers that turn paise into readable rupee text and timestamps into clock times ===
package com.arin.ghostbank.ui.components

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun paiseToRupeeText(paise: Long): String {
    val rupees = paise / 100
    val fraction = paise % 100
    return "₹$rupees.${fraction.toString().padStart(2, '0')}"
}

fun paiseSummary(paise: Long): String = "${paiseToRupeeText(paise)} · $paise paise"

fun timeText(ts: Long): String = SimpleDateFormat("dd MMM HH:mm:ss", Locale.US).format(Date(ts))

fun dateText(ts: Long): String = SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(ts))
