package com.arin.ghostbank.data.net

import com.arin.ghostbank.data.Settings
import com.arin.ghostbank.data.db.ProxyDao
import com.arin.ghostbank.data.db.ProxyEntity
import com.arin.ghostbank.model.ProxyKind
import com.arin.ghostbank.model.StealthLevel
import java.net.InetSocketAddress
import java.net.Proxy

data class ExitRoute(
    val id: Long?,
    val proxy: Proxy?,
    val kind: ProxyKind?,
    val label: String,
    val country: String?
)

class NoExitConfigured : IllegalStateException("Ghost level requires a proxy exit and none is configured")

class ProxyEngine(private val dao: ProxyDao) {

    suspend fun pick(settings: Settings): ExitRoute? {
        if (settings.torMode) {
            return ExitRoute(
                id = null,
                proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 9050)),
                kind = ProxyKind.TOR,
                label = "Tor 127.0.0.1:9050",
                country = settings.lastExitCountry
            )
        }
        val pool = dao.healthy()
        if (pool.isEmpty()) {
            if (settings.stealthLevel == StealthLevel.GHOST) throw NoExitConfigured()
            return null
        }
        val preferIndia = pool.filter { it.country?.equals("IN", true) == true }
        val chosen = (if (preferIndia.isNotEmpty()) preferIndia else pool).random()
        dao.touch(chosen.id, System.currentTimeMillis())
        return ExitRoute(
            id = chosen.id,
            proxy = parse(chosen),
            kind = runCatching { ProxyKind.valueOf(chosen.kind) }.getOrDefault(ProxyKind.HTTP),
            label = chosen.url,
            country = chosen.country
        )
    }

    fun parse(entity: ProxyEntity): Proxy? = when (runCatching { ProxyKind.valueOf(entity.kind) }.getOrDefault(ProxyKind.HTTP)) {
        ProxyKind.SOCKS5 -> parseUrl(entity.url, Proxy.Type.SOCKS)
        ProxyKind.HTTP, ProxyKind.HTTPS -> parseUrl(entity.url, Proxy.Type.HTTP)
        ProxyKind.TOR -> Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 9050))
    }

    private fun parseUrl(url: String, type: Proxy.Type): Proxy? {
        return try {
            val cleaned = url.substringAfter("://")
            val host = cleaned.substringBefore(":").substringBefore("/")
            val port = cleaned.substringAfter(":", "8080").substringBefore("/").toIntOrNull() ?: 8080
            if (host.isBlank()) null else Proxy(type, InetSocketAddress(host, port))
        } catch (_: Exception) {
            null
        }
    }

    /** Parse "host:port" lines from clipboard / file, tolerate schemes and comments. */
    suspend fun importList(text: String): Int {
        var added = 0
        text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { line ->
            val kind = when {
                line.startsWith("socks5://", true) || line.startsWith("socks://", true) -> ProxyKind.SOCKS5
                line.startsWith("https://", true) -> ProxyKind.HTTPS
                else -> ProxyKind.HTTP
            }
            val url = if (line.contains("://")) line else "http://$line"
            val country = line.substringAfter("|", "").takeIf { it.length == 2 }?.uppercase()
            val clean = line.substringBefore("|")
            val finalUrl = if (clean.contains("://")) clean else "http://$clean"
            val id = dao.insert(ProxyEntity(url = finalUrl, kind = kind.name, country = country))
            if (id > 0) added++
            if (url.isNotEmpty()) Unit
        }
        return added
    }

    suspend fun setHealth(id: Long, healthy: Boolean) = dao.setHealth(id, healthy)
}
