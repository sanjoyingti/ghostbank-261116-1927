package com.arin.ghostbank.data.net

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/** Resolves A records over HTTPS JSON so the ISP's DNS never sees the bank host names. */
class DohDns(private val resolverUrl: String) : Dns {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            val addresses = runBlocking { resolve(hostname) }
            if (addresses.isEmpty()) Dns.SYSTEM.lookup(hostname) else addresses
        } catch (_: Exception) {
            Dns.SYSTEM.lookup(hostname)
        }
    }

    private fun resolve(hostname: String): List<InetAddress> {
        val url = "$resolverUrl?name=$hostname&type=A"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/dns-json")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: return emptyList()
            val answer = json.parseToJsonElement(body).jsonObject["Answer"]?.jsonArray ?: return emptyList()
            return answer.mapNotNull { element ->
                val obj = element.jsonObject
                if (obj["type"]?.jsonPrimitive?.content == "1") {
                    obj["data"]?.jsonPrimitive?.content?.let { InetAddress.getByName(it) }
                } else null
            }
        }
    }
}
