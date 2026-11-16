package com.arin.ghostbank.data.net

import com.arin.ghostbank.data.Settings
import okhttp3.ConnectionPool
import okhttp3.ConnectionSpec
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.TlsVersion
import java.util.concurrent.TimeUnit

/** In-memory cookie jar so a run shares cookies like a browser tab and forgets them across runs. */
class EphemeralCookieJar : CookieJar {
    private val store = mutableListOf<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { incoming ->
            store.removeAll { it.name == incoming.name && it.domain == incoming.domain && it.path == incoming.path }
            store.add(incoming)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        store.filter { it.matches(url) && it.expiresAt > System.currentTimeMillis() }

    fun count(): Int = store.size
    fun clear() = store.clear()
}

class GhostHttp {

    private val cookieJar = EphemeralCookieJar()

    /** Called between runs so no cookie, socket or session survives into the next run. */
    fun newSession() {
        cookieJar.clear()
    }

    fun cookiesHeld(): Int = cookieJar.count()

    /** Interceptor that writes Chrome's headers in the profile's own order and nothing else. */
    private fun headerInterceptor(profile: GhostProfileEntityWrapper): Interceptor = Interceptor { chain ->
        val builder = chain.request().newBuilder()
        val values = mapOf(
            "sec-ch-ua" to profile.fp.chUa,
            "sec-ch-ua-mobile" to profile.fp.chMobile,
            "sec-ch-ua-platform" to profile.fp.chPlatform,
            "upgrade-insecure-requests" to "1",
            "user-agent" to profile.fp.userAgent,
            "accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            "sec-fetch-site" to profile.fetchSite,
            "sec-fetch-mode" to "navigate",
            "sec-fetch-user" to "?1",
            "sec-fetch-dest" to "document",
            "accept-encoding" to profile.acceptEncoding,
            "accept-language" to "en-IN,en-GB;q=0.9,en;q=0.8"
        )
        profile.fp.headerOrder.forEach { name ->
            values[name]?.let { builder.header(name, it) }
        }
        chain.proceed(builder.build())
    }

    private fun baseBuilder(settings: Settings, exit: ExitRoute?, profile: GhostProfileEntityWrapper): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder()
            .connectionPool(ConnectionPool(4, 30, TimeUnit.SECONDS))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(false)
            .cookieJar(cookieJar)
            .addInterceptor(headerInterceptor(profile))

        exit?.proxy?.let { builder.proxy(it) }
        if (exit != null) {
            builder.proxyAuthenticator { _, response ->
                val header = response.request.header("Proxy-Authorization")
                if (header != null) null else response.request.newBuilder()
                    .header("Proxy-Authorization", exit.label.basicAuth()).build()
            }
        }
        if (exit == null || exit.kind == com.arin.ghostbank.model.ProxyKind.HTTP || exit.kind == com.arin.ghostbank.model.ProxyKind.HTTPS) {
            builder.dns(DohDns(settings.dohResolver))
        }

        val cipherList = profile.fp.cipherOrder.mapNotNull {
            runCatching { okhttp3.CipherSuite.forJavaName(it) }.getOrNull()
        }
        runCatching {
            val spec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
                .apply { if (cipherList.isNotEmpty()) cipherSuites(*cipherList.toTypedArray()) }
                .build()
            builder.connectionSpecs(listOf(spec, ConnectionSpec.CLEARTEXT))
        }
        return builder
    }

    /** A client bound to one run/profile; close() releases the sockets so nothing lingers. */
    fun client(settings: Settings, exit: ExitRoute?, profile: GhostProfileEntityWrapper): OkHttpClient =
        baseBuilder(settings, exit, profile).build()
}

data class GhostProfileEntityWrapper(
    val fp: GhostFingerprint,
    val acceptEncoding: String = "gzip, deflate, br, zstd",
    val fetchSite: String = "none"
)

private fun String.basicAuth(): String {
    val creds = substringAfter("://", "").substringBefore("@")
    return if (creds.contains(":")) "Basic " + android.util.Base64.encodeToString(
        creds.toByteArray(), android.util.Base64.NO_WRAP
    ) else ""
}

fun Response.maskedHeaders(): String =
    headers.names().joinToString("\n") { name ->
        val value = header(name).orEmpty()
        "$name: " + if (name.equals("set-cookie", true)) maskCookie(value) else value
    }

private fun maskCookie(value: String): String {
    val parts = value.split(";")
    if (parts.isEmpty()) return value
    val first = parts.first().let {
        val nv = it.split("=", limit = 2)
        if (nv.size == 2) "${nv[0]}=${nv[1].take(6)}…" else it
    }
    return (listOf(first) + parts.drop(1)).joinToString(";")
}
