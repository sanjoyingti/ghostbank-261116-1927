package com.arin.ghostbank.data.net

import com.arin.ghostbank.data.db.GhostProfileDao
import com.arin.ghostbank.data.db.GhostProfileEntity
import java.security.SecureRandom

/**
 * A frozen, Chrome-valid identity. Header order is permuted only inside the range Chrome itself
 * keeps stable, so the request always looks like a browser and never like a script.
 */
data class GhostFingerprint(
    val userAgent: String,
    val chUa: String,
    val chMobile: String,
    val chPlatform: String,
    val headerOrder: List<String>,
    val cipherOrder: List<String>,
    val h2Settings: List<Pair<String, Int>>
)

object Fingerprints {

    private val MODELS = listOf(
        "Pixel 8", "Pixel 7a", "Pixel 6", "SM-S918B", "SM-A546B", "SM-G991B",
        "moto g84 5G", "CPH2451", "V2225", "Redmi Note 12", "OnePlus 11R"
    )

    private val ANDROIDS = listOf("Android 13", "Android 14", "Android 15")
    private val CHROME = listOf("131.0.0.0", "132.0.0.0", "133.0.0.0", "134.0.0.0", "135.0.0.0")

    /** Chrome's own order. Only the tail slice after "accept-language" is shuffled per profile. */
    private val BASE_ORDER = listOf(
        "sec-ch-ua",
        "sec-ch-ua-mobile",
        "sec-ch-ua-platform",
        "upgrade-insecure-requests",
        "user-agent",
        "accept",
        "sec-fetch-site",
        "sec-fetch-mode",
        "sec-fetch-user",
        "sec-fetch-dest",
        "accept-encoding",
        "accept-language"
    )

    private val CIPHERS = listOf(
        "TLS_AES_128_GCM_SHA256",
        "TLS_AES_256_GCM_SHA384",
        "TLS_CHACHA20_POLY1305_SHA256",
        "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
        "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
        "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
        "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",
        "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256"
    )

    fun generate(seed: Int = SecureRandom().nextInt()): GhostFingerprint {
        val random = java.util.Random(seed.toLong())
        val model = MODELS[random.nextInt(MODELS.size)]
        val android = ANDROIDS[random.nextInt(ANDROIDS.size)]
        val chrome = CHROME[random.nextInt(CHROME.size)]
        val major = chrome.substringBefore('.')

        val head = BASE_ORDER.take(5)
        val tail = BASE_ORDER.drop(5).shuffled(random)
        val order = listOf("host", "connection") + head + tail

        return GhostFingerprint(
            userAgent = "Mozilla/5.0 (Linux; $android; $model) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome Mobile Safari/537.36",
            chUa = "\"Chromium\";v=\"$major\", \"Not_A Brand\";v=\"24\", \"Google Chrome\";v=\"$major\"",
            chMobile = "?1",
            chPlatform = "\"Android\"",
            headerOrder = order,
            cipherOrder = CIPHERS.shuffled(random),
            h2Settings = listOf(
                "HEADER_TABLE_SIZE" to 65536,
                "MAX_CONCURRENT_STREAMS" to 1000,
                "INITIAL_WINDOW_SIZE" to 6291456,
                "MAX_HEADER_LIST_SIZE" to 262144
            )
        )
    }

    /** Takes a fresh profile from the pool; if all are burned, mints a new one. */
    suspend fun takeOrMint(dao: GhostProfileDao, accountKey: String): Pair<Long?, GhostFingerprint> {
        val stored = dao.takeFresh()
        if (stored != null) return stored.id to fromEntity(stored)
        val fp = generate(accountKey.hashCode())
        val id = dao.insert(
            GhostProfileEntity(
                accountKey = accountKey,
                userAgent = fp.userAgent,
                chUa = fp.chUa,
                chMobile = fp.chMobile,
                chPlatform = fp.chPlatform,
                headerOrder = fp.headerOrder.joinToString(","),
                cipherOrder = fp.cipherOrder.joinToString(","),
                h2Settings = fp.h2Settings.joinToString(",") { "${it.first}=${it.second}" },
                burned = false,
                createdAt = System.currentTimeMillis()
            )
        )
        return id to fp
    }

    fun fromEntity(entity: GhostProfileEntity): GhostFingerprint = GhostFingerprint(
        userAgent = entity.userAgent,
        chUa = entity.chUa,
        chMobile = entity.chMobile,
        chPlatform = entity.chPlatform,
        headerOrder = entity.headerOrder.split(","),
        cipherOrder = entity.cipherOrder.split(","),
        h2Settings = entity.h2Settings.split(",").mapNotNull {
            val parts = it.split("=")
            if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: 0) else null
        }
    )
}
