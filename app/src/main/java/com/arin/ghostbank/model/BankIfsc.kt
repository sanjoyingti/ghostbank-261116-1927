=== ABOUT: Fixed the queue so a pasted account number gets a plausible branch code for its bank when none is typed ===
package com.arin.ghostbank.model

object BankIfsc {

    private val prefixes = mapOf(
        "SBI" to "SBIN", "HDFC" to "HDFC", "ICICI" to "ICIC", "AXIS" to "UTIB",
        "KOTAK" to "KKBK", "PNB" to "PUNB", "BOB" to "BARB", "CANARA" to "CNRB",
        "UNION" to "UBIN", "IDBI" to "IBKL", "YES" to "YESB", "INDUSIND" to "INDB",
        "FEDERAL" to "FDRL", "RBL" to "RATN", "BANDHAN" to "BDBL", "IDFC" to "IDFB",
        "AU" to "AUBL", "DBS" to "DBSS", "CITI" to "CITI", "HSBC" to "HSBC",
        "SCB" to "SCBL", "KARNATAKA" to "KARB", "SIB" to "SIBL", "EQUITAS" to "ESFB",
        "UJJIVAN" to "UJVN", "JANA" to "JSFB", "PAYTM" to "PYTM"
    )

    fun suggest(bankCode: String): String = (prefixes[bankCode] ?: "XXXX") + "0000001"
}
