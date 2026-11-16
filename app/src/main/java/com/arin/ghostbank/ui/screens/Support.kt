package com.arin.ghostbank.ui.screens

import com.arin.ghostbank.model.Bank
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.RemitterProfile

object BankDefaults {

    private val IFSC = mapOf(
        "SBI" to "SBIN0000001", "HDFC" to "HDFC0000001", "ICICI" to "ICIC0000001",
        "AXIS" to "UTIB0000001", "KOTAK" to "KKBK0000001", "PNB" to "PUNB0000001",
        "BOB" to "BARB0000001", "CANARA" to "CNRB0000001", "UNION" to "UBIN0000001",
        "IDBI" to "IBKL0000001", "YES" to "YESB0000001", "INDUSIND" to "INDB0000001",
        "FEDERAL" to "FDRL0000001", "RBL" to "RATN0000001", "BANDHAN" to "BDBL0000001",
        "IDFC" to "IDFB0000001", "AU" to "AUBL0000001", "DBS" to "DBSS0000001",
        "CITI" to "CITI0000001", "HSBC" to "HSBC0000001", "SCB" to "SCBL0000001",
        "KARNATAKA" to "KARB0000001", "SIB" to "SIBL0000001", "EQUITAS" to "ESFB0000001",
        "UJJIVAN" to "UJVN0000001", "JANA" to "JSFB0000001", "PAYTM" to "PYTM0000001"
    )

    fun defaultIfsc(code: String): String = IFSC[code] ?: "SBIN0000001"

    /** The bank's own account-to-account route, used as a source when no saved profile fits. */
    fun railRemitter(bank: Bank): RemitterProfile = RemitterProfile(
        name = "${bank.name} · P2A route",
        accountNumber = "900000000000" + Banks.all.indexOf(bank).toString().padStart(2, '0'),
        ifsc = defaultIfsc(bank.code)
    )

    fun rupees(paise: Long): String {
        val sign = if (paise < 0) "-" else ""
        val abs = kotlin.math.abs(paise)
        return "$sign₹${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    }

    fun paiseLabel(paise: Long): String = "${paise} paise"
}
