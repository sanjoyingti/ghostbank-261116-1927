package com.arin.ghostbank.model

import java.security.MessageDigest
import java.util.UUID

enum class StealthLevel { STANDARD, DEEP, GHOST }

enum class Badge { LIVE, SIMULATED }

enum class StageKind {
    INITIATED, FINGERPRINT, RESOLVE_PAYEE, DEBIT_LEG, CREDIT_LEG, SETTLE, BOOKED
}

enum class RunMode { TRANSFER, HARVEST }

enum class RunStatus { RUNNING, COMPLETED, FAILED, PAUSED, CANCELLED, QUEUED }

enum class HarvestStatus { PENDING, SENT, SETTLED, FLOOR, REJECTED }

enum class ErrorKind {
    NETWORK, BLOCKED, INVALID_PAYEE, INSUFFICIENT_FUNDS, RAIL_FLOOR, TIMEOUT,
    ANOMALY, KILL_SWITCH, CONFIG, UNKNOWN
}

enum class ProxyKind { HTTP, HTTPS, SOCKS5, TOR }

data class GhostError(
    val kind: ErrorKind,
    val plain: String,
    val nextAction: String,
    val rawBody: String? = null
)

data class Bank(
    val code: String,
    val name: String,
    val group: BankGroup,
    val host: String,
    val probePath: String = "/",
    val impsPath: String = "/",
    val capPaise: Long,
    val impsSupported: Boolean,
    val quirk: String,
    val country: String = "IN"
)

enum class BankGroup { PUBLIC, PRIVATE, SFB }

/** The 27 banks, grouped, each with its own transaction cap, IMPS flag and rail quirk. */
object Banks {

    val all: List<Bank> = listOf(
        Bank("SBI", "State Bank of India", BankGroup.PUBLIC, "www.onlinesbi.sbi", capPaise = 500_000_00, impsSupported = true,
            quirk = "Retail IMPS cap ₹5,00,000/day; payee freeze 30 min on new beneficiary."),
        Bank("HDFC", "HDFC Bank", BankGroup.PRIVATE, "netbanking.hdfcbank.com", capPaise = 500_000_00, impsSupported = true,
            quirk = "New payee cooling-off 30 min before IMPS unlocks."),
        Bank("ICICI", "ICICI Bank", BankGroup.PRIVATE, "www.icicibank.com", capPaise = 500_000_00, impsSupported = true,
            quirk = "IMPS blocked for 30 min on freshly added payee."),
        Bank("AXIS", "Axis Bank", BankGroup.PRIVATE, "www.axisbank.com", capPaise = 500_000_00, impsSupported = true,
            quirk = "Per-txn IMPS ceiling ₹5,00,000; aggressive WAF on portal."),
        Bank("KOTAK", "Kotak Mahindra Bank", BankGroup.PRIVATE, "www.kotak.com", capPaise = 500_000_00, impsSupported = true,
            quirk = "IMPS available 24x7 but bot-checks on repeated GETs."),
        Bank("PNB", "Punjab National Bank", BankGroup.PUBLIC, "netbankingpnb.pnb.bank.in", capPaise = 500_000_00, impsSupported = true,
            quirk = "Legacy portal, slow TLS; retries often needed."),
        Bank("BOB", "Bank of Baroda", BankGroup.PUBLIC, "www.bobibanking.com", capPaise = 500_000_00, impsSupported = true,
            quirk = "IMPS capped ₹5,00,000; batch windows on older portals."),
        Bank("CANARA", "Canara Bank", BankGroup.PUBLIC, "netbanking.canarabank.in", capPaise = 500_000_00, impsSupported = true,
            quirk = "Strict referer checks on form posts."),
        Bank("UNION", "Union Bank of India", BankGroup.PUBLIC, "www.unionbankofindia.co.in", capPaise = 500_000_00, impsSupported = true,
            quirk = "Occasional 503 on burst; backoff mandatory."),
        Bank("IDBI", "IDBI Bank", BankGroup.PUBLIC, "www.idbibank.in", capPaise = 500_000_00, impsSupported = true,
            quirk = "IMPS allowed; portal prefers HTTP/1.1 over HTTP/2."),
        Bank("YES", "Yes Bank", BankGroup.PRIVATE, "www.yesbank.in", capPaise = 500_000_00, impsSupported = true,
            quirk = "Per-txn cap may be lower per account profile."),
        Bank("INDUSIND", "IndusInd Bank", BankGroup.PRIVATE, "www.indusind.com", capPaise = 400_000_00, impsSupported = true,
            quirk = "Portal WAF flags rapid sequential hits (403)."),
        Bank("FEDERAL", "Federal Bank", BankGroup.PRIVATE, "www.federalbank.co.in", capPaise = 500_000_00, impsSupported = true,
            quirk = "IMPS allowed 24x7; branch lookups sometimes stale."),
        Bank("RBL", "RBL Bank", BankGroup.PRIVATE, "www.rblbank.com", capPaise = 300_000_00, impsSupported = true,
            quirk = "Lower per-txn ceiling on retail net banking."),
        Bank("BANDHAN", "Bandhan Bank", BankGroup.PRIVATE, "www.bandhanbank.com", capPaise = 200_000_00, impsSupported = true,
            quirk = "Conservative IMPS ceiling for new accounts."),
        Bank("IDFC", "IDFC First Bank", BankGroup.PRIVATE, "www.idfcfirstbank.com", capPaise = 500_000_00, impsSupported = true,
            quirk = "Modern stack, HTTP/2 clean; fast responses."),
        Bank("AU", "AU Small Finance Bank", BankGroup.SFB, "www.aubank.in", capPaise = 200_000_00, impsSupported = true,
            quirk = "SFB ceiling lower; payee name match is strict."),
        Bank("DBS", "DBS Bank India", BankGroup.PRIVATE, "www.dbs.com", capPaise = 500_000_00, impsSupported = true,
            quirk = "Regional endpoint stalls outside IST hours."),
        Bank("CITI", "Citibank India", BankGroup.PRIVATE, "www.citibank.co.in", capPaise = 500_000_00, impsSupported = true,
            quirk = "Legacy portal, cookie-heavy; ephemeral session may fail."),
        Bank("HSBC", "HSBC India", BankGroup.PRIVATE, "www.hsbc.co.in", capPaise = 500_000_00, impsSupported = true,
            quirk = "Strict TLS fingerprint matching, keep Chrome-like ciphers."),
        Bank("SCB", "Standard Chartered", BankGroup.PRIVATE, "www.sc.com", capPaise = 500_000_00, impsSupported = true,
            quirk = "Geo-sensitive; non-Indian exit triggers step-up."),
        Bank("KARNATAKA", "Karnataka Bank", BankGroup.PUBLIC, "www.karnatakabank.com", capPaise = 200_000_00, impsSupported = true,
            quirk = "Smaller portal, occasional 502 on peak."),
        Bank("SIB", "South Indian Bank", BankGroup.PUBLIC, "www.southindianbank.com", capPaise = 200_000_00, impsSupported = true,
            quirk = "Low per-txn ceiling; retries need fresh exit."),
        Bank("EQUITAS", "Equitas SFB", BankGroup.SFB, "www.equitasbank.com", capPaise = 100_000_00, impsSupported = true,
            quirk = "SFB cap ₹1,00,000; sub-rupee rejects on some legs."),
        Bank("UJJIVAN", "Ujjivan SFB", BankGroup.SFB, "www.ujjivansfb.in", capPaise = 100_000_00, impsSupported = true,
            quirk = "Sub-rupee amounts rejected → FLOOR upgrade likely."),
        Bank("JANA", "Jana Small Finance Bank", BankGroup.SFB, "www.janabank.com", capPaise = 100_000_00, impsSupported = true,
            quirk = "SFB ceiling; portal blocks non-mobile UAs."),
        Bank("PAYTM", "Paytm Payments Bank", BankGroup.SFB, "www.paytmbank.com", capPaise = 100_000_00, impsSupported = true,
            quirk = "Sub-rupee often rejected; expects UPI-style forms.")
    )

    fun byCode(code: String): Bank? = all.firstOrNull { it.code == code }

    fun byIfsc(ifsc: String): Bank? {
        val prefix = ifsc.take(4).uppercase()
        return all.firstOrNull { b -> ifscPrefix(b.code).contains(prefix) }
    }

    private fun ifscPrefix(code: String): List<String> = when (code) {
        "SBI" -> listOf("SBIN")
        "HDFC" -> listOf("HDFC")
        "ICICI" -> listOf("ICIC")
        "AXIS" -> listOf("UTIB")
        "KOTAK" -> listOf("KKBK")
        "PNB" -> listOf("PUNB")
        "BOB" -> listOf("BARB")
        "CANARA" -> listOf("CNRB")
        "UNION" -> listOf("UBIN")
        "IDBI" -> listOf("IBKL")
        "YES" -> listOf("YESB")
        "INDUSIND" -> listOf("INDB")
        "FEDERAL" -> listOf("FDRL")
        "RBL" -> listOf("RATN")
        "BANDHAN" -> listOf("BDBL")
        "IDFC" -> listOf("IDFB")
        "AU" -> listOf("AUBL")
        "DBS" -> listOf("DBSS")
        "CITI" -> listOf("CITI")
        "HSBC" -> listOf("HSBC")
        "SCB" -> listOf("SCBL")
        "KARNATAKA" -> listOf("KARB")
        "SIB" -> listOf("SIBL")
        "EQUITAS" -> listOf("ESFB")
        "UJJIVAN" -> listOf("UJVN")
        "JANA" -> listOf("JSFB")
        "PAYTM" -> listOf("PYTM")
        else -> emptyList()
    }

    fun isValidIfsc(ifsc: String): Boolean =
        Regex("^[A-Z]{4}0[A-Z0-9]{6}$").matches(ifsc.uppercase())
}

object Ids {
    fun idempotencyKey(): String = UUID.randomUUID().toString().replace("-", "").take(32)

    fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun utr(): String = (1..12).joinToString("") { (('0'..'9').random()).toString() }

    fun maskNumber(number: String): String =
        if (number.length <= 4) number else "•".repeat((number.length - 4).coerceAtMost(8)) + number.takeLast(4)
}

/** Destinations you can move money to and the fake-but-stable remitter profiles */
data class RemitterProfile(val name: String, val accountNumber: String, val ifsc: String)

val REMITTER_PROFILES = listOf(
    RemitterProfile("Salary pool – HDFC", "50100234987121", "HDFC0000123"),
    RemitterProfile("Settlement – ICICI", "00470155443322", "ICIC0000047"),
    RemitterProfile("Ops float – Axis", "918020049912772", "UTIB0000918")
)

/** Virtual accounts used by Trace-Shield to mask the destination digits on the rail */
val VIRTUAL_ACCOUNTS = listOf(
    "99001122334401", "99001122334402", "99001122334403"
)
