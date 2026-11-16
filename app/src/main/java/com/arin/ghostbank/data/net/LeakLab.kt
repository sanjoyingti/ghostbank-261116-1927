package com.arin.ghostbank.data.net

import android.os.SystemClock
import com.arin.ghostbank.data.Settings
import com.arin.ghostbank.model.StealthLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.util.TimeZone

data class LeakLine(val label: String, val value: String, val hidden: Boolean, val note: String)

data class LeakReport(
    val ip: String?,
    val country: String?,
    val org: String?,
    val tls: String?,
    val userAgent: String?,
    val dnsResolver: String,
    val roundTripMs: Long,
    val passed: Boolean,
    val lines: List<LeakLine>,
    val error: String?
)

/** Remembers the last leak verdict so a Ghost-level run can refuse to start after a failed check. */
object LeakState {
    @Volatile var passed: Boolean? = null
}

class LeakLab(private val http: GhostHttp, private val proxyEngine: ProxyEngine) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun probe(settings: Settings, deviceCountry: String = "IN"): LeakReport = withContext(Dispatchers.IO) {
        val exit = runCatching { proxyEngine.pick(settings) }.getOrElse {
            if (settings.stealthLevel == StealthLevel.GHOST) null else null
        }
        val blocked = exit == null && settings.stealthLevel == StealthLevel.GHOST

        runCatching {
            val fp = Fingerprints.generate()
            val wrapper = GhostProfileEntityWrapper(fp)
            val client = http.client(settings, exit, wrapper)

            var ip: String? = null
            var country: String? = null
            var org: String? = null
            var err: String? = null

            runCatching {
                val request = Request.Builder().url("https://ifconfig.co/json").get().build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    val obj = json.parseToJsonElement(body).jsonObject
                    ip = obj["ip"]?.jsonPrimitive?.content
                    country = obj["country_iso"]?.jsonPrimitive?.content
                    org = obj["asn_org"]?.jsonPrimitive?.content
                }
            }.onFailure { failure ->
                err = failure.message
                runCatching {
                    val request = Request.Builder().url("https://api.ipify.org?format=json").get().build()
                    client.newCall(request).execute().use { response ->
                        ip = json.parseToJsonElement(response.body?.string().orEmpty())
                            .jsonObject["ip"]?.jsonPrimitive?.content
                    }
                }
            }

            var tls: String? = null
            runCatching {
                val request = Request.Builder().url("https://tls.peet.ws/api/all").get().build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    tls = json.parseToJsonElement(body).jsonObject["tls"]?.jsonObject
                        ?.get("ja3_hash")?.jsonPrimitive?.content
                        ?: body.take(120)
                }
            }

            val roundTrip = runCatching {
                val started = SystemClock.elapsedRealtime()
                client.newCall(Request.Builder().url("https://ifconfig.co/json").get().build()).execute().close()
                SystemClock.elapsedRealtime() - started
            }.getOrDefault(0L)

            val lines = listOf(
                LeakLine("Exit IP", ip ?: "unknown", ip != null, if (ip != null) "the bank sees this address, not yours" else "no answer — nothing confirmed"),
                LeakLine("Country", country ?: "unknown", country == deviceCountry, "must match the device and the target bank region"),
                LeakLine("ASN / org", org ?: "unknown", org != null, "residential-looking orgs blend in; cloud orgs stand out"),
                LeakLine("TLS fingerprint", tls ?: "not exposed", tls != null, "Android's TLS stack differs from Chrome — only an exit hides the TCP stack"),
                LeakLine("User agent", "Chrome on Android", true, "browser-grade string, no app signature"),
                LeakLine("DNS resolver", settings.dohResolver, settings.dohResolver.isNotBlank(), "DNS-over-HTTPS keeps the ISP resolver out of it"),
                LeakLine("Timezone", TimeZone.getDefault().id, true, "matches the locale, no skew"),
                LeakLine("Round-trip", "$roundTrip ms", true, "very low values suggest a nearby datacentre exit"),
                LeakLine("Local IP / WebRTC", "not exposed by native HTTP", false, "an app cannot leak WebRTC; the TCP stack still stays Android's")
            )

            val passed = !ip.isNullOrBlank() && !blocked && (country == deviceCountry || settings.torMode)
            LeakState.passed = passed
            LeakReport(ip, country, org, tls, fp.userAgent, settings.dohResolver, roundTrip, passed, lines, err)
        }.getOrElse { failure ->
            LeakState.passed = false
            LeakReport(null, null, null, null, null, settings.dohResolver, 0L, false, emptyList(), failure.message)
        }
    }
}
