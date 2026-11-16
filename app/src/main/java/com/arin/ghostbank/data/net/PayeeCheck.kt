package com.arin.ghostbank.data.net

import com.arin.ghostbank.data.Settings
import com.arin.ghostbank.model.Bank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URLEncoder

data class PayeeCheckResult(val live: Boolean, val message: String, val raw: String?)

/**
 * One GET to the target bank's payee endpoint. If the server answers we say LIVE and quote it,
 * if it does not we say SIMULATED and explain instead of pretending.
 */
object PayeeCheck {

    suspend fun run(
        http: GhostHttp,
        settings: Settings,
        bank: Bank,
        accountNumber: String,
        ifsc: String
    ): PayeeCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val wrapper = GhostProfileEntityWrapper(Fingerprints.generate())
            val client = http.client(settings, null, wrapper)
            val url = "https://${bank.host}${bank.impsPath}payee?account=${enc(accountNumber)}&ifsc=${enc(ifsc)}"
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.code in 200..399) {
                    PayeeCheckResult(
                        live = true,
                        message = "The bank's own payee endpoint confirmed the beneficiary (HTTP ${response.code}).",
                        raw = body.take(600)
                    )
                } else {
                    PayeeCheckResult(
                        live = false,
                        message = "Bank answered HTTP ${response.code} — the beneficiary may be wrong, or the endpoint needs a login.",
                        raw = body.take(600)
                    )
                }
            }
        }.getOrElse { failure ->
            PayeeCheckResult(
                live = false,
                message = "No answer from the bank (${failure.message ?: "network"}). Saved as SIMULATED — re-check when you are online.",
                raw = null
            )
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
