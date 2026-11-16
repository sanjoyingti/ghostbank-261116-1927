package com.arin.ghostbank.model

data class RemitSource(val label: String, val detail: String, val profile: RemitterProfile?)

object RemitProfileHolder {

    fun optionsFor(bankCode: String): List<RemitSource> {
        val bank = Banks.byCode(bankCode)
        val own = RemitSource(
            label = "Bank's own P2A route",
            detail = "the bank forms the remitter itself — no saved profile leaves the device",
            profile = null
        )
        return listOf(own) + REMITTER_PROFILES.map {
            RemitSource(label = it.name, detail = Ids.maskNumber(it.accountNumber) + " · " + it.ifsc, profile = it)
        }
    }

    fun sourceAt(index: Int, bankCode: String): RemitSource {
        val list = optionsFor(bankCode)
        return list.getOrElse(index) { list.first() }
    }
}

fun RemitSource.toRemitter(): RemitterProfile =
    profile ?: RemitterProfile("PAYMENT", "", "")

fun paise(rupees: Int): Long = rupees * 100L

fun formatPaise(paise: Long): String {
    val sign = if (paise < 0) "-" else ""
    val abs = if (paise < 0) -paise else paise
    return "$sign₹${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
}
