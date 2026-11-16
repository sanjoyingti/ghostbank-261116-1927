package com.arin.ghostbank.ui.components

import com.arin.ghostbank.model.HarvestStatus

fun rupees(paise: Long?): String {
    if (paise == null) return "—"
    val whole = paise / 100
    val fraction = paise % 100
    val grouped = whole.toString().reversed().chunked(3).joinToString(",").reversed()
    return if (fraction == 0L) "₹$grouped" else "₹$grouped.${fraction.toString().padStart(2, '0')}"
}

fun paiseLabel(paise: Long?): String = "${paise ?: 0L} paise"

fun harvestStatusOf(value: String?): HarvestStatus =
    runCatching { HarvestStatus.valueOf(value.orEmpty()) }.getOrDefault(HarvestStatus.PENDING)
