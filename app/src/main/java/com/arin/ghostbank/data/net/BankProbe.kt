package com.arin.ghostbank.data.net

import com.arin.ghostbank.data.Settings
import com.arin.ghostbank.model.Badge
import com.arin.ghostbank.model.Bank
import com.arin.ghostbank.model.StealthLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

class BankProbe(private val http: GhostHttp, private val proxyEngine: ProxyEngine) {

    suspend fun probe(settings: Settings, bank: Bank): Badge = withContext(Dispatchers.IO) {
        val exit = runCatching { proxyEngine.pick(settings) }.getOrNull()
        if (exit == null && settings.stealthLevel == StealthLevel.GHOST) return@withContext Badge.SIMULATED
        val fp = Fingerprints.generate(bank.code.hashCode())
        val client = http.client(settings, exit, GhostProfileEntityWrapper(fp))
        runCatching {
            client.newCall(Request.Builder().url("https://${bank.host}${bank.probePath}").get().build())
                .execute()
                .use { if (it.code in 200..399) Badge.LIVE else Badge.SIMULATED }
        }.getOrDefault(Badge.SIMULATED)
    }
}
