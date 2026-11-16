package com.arin.ghostbank.data.net

import com.arin.ghostbank.data.Settings
import com.arin.ghostbank.model.Badge
import com.arin.ghostbank.model.Bank
import com.arin.ghostbank.model.StealthLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URLEncoder

class PayeeChecker(private val http: GhostHttp, private val proxyEngine: ProxyEngine) {

    data class Result(
        val badge: Badge,
        val httpStatus: Int?,
        val message: String,
        val raw: String?
    )

    suspend fun check(settings: Settings, bank: Bank, number: String, ifsc: String): Result =
        withContext(Dispatchers.IO) {
            val exit = runCatching { proxyEngine.pick(settings) }.getOrNull()
            if (exit == null && settings.stealthLevel == StealthLevel.GHOST) {
                return@withContext Result(
                    Badge.SIMULATED, null,
                    "Ghost level is on and no exit is configured — the name check cannot leave the phone.",
                    null
                )
            }
            val fp = Fingerprints.generate(number.hashCode())
            val client = http.client(settings, exit, GhostProfileEntityWrapper(fp))
            val url = "https://${bank.host}${bank.impsPath}payee?account=" +
                URLEncoder.encode(number, "UTF-8") + "&ifsc=" +
                URLEncoder.encode(ifsc.uppercase(), "UTF-8")

            try {
                client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                    val body = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
                    val confirmed = response.code in 200..399
                    Result(
                        if (confirmed) Badge.LIVE else Badge.SIMULATED,
                        response.code,
                        if (confirmed) {
                            "The bank answered the payee check (HTTP ${response.code}) — review the raw body before it presses on."
                        } else {
                            "The bank refused the payee check (HTTP ${response.code}) — check the digits and the IFSC."
                        },
                        body.take(1200)
                    )
                }
            } catch (e: Exception) {
                Result(
                    Badge.SIMULATED, null,
                    "No answer from the bank: ${e.message ?: "transport failure"}. The account is stored either way.",
                    null
                )
            }
        }
}
