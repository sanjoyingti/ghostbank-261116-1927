=== ABOUT: Human-like timing: poisson-distributed gaps and a speed limiter so requests never burst like a robot ===
package com.arin.ghostbank.data.net

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom
import kotlin.math.exp

object Jitter {

    private val random = SecureRandom()

    /** Knuth's poisson sampler, in milliseconds. */
    fun poisson(meanMs: Double): Long {
        if (meanMs <= 0.0) return 0L
        val limit = exp(-meanMs)
        var k = 0
        var p = 1.0
        do {
            k++
            p *= random.nextDouble()
        } while (p > limit && k < 500)
        return k.toLong()
    }

    suspend fun humanGap(baseMs: Long, spread: Long = 900L) {
        val extra = poisson(1200.0)
        delay(baseMs + extra + random.nextInt(spread.toInt().coerceAtLeast(1)).toLong())
    }

    fun percentile(min: Int, max: Int): Int = if (max <= min) min else min + random.nextInt(max - min + 1)
}

/** Token gate enforcing max requests per minute for the whole app. */
class VelocityGate {

    private val mutex = Mutex()
    private var lastSent = 0L

    suspend fun acquire(requestsPerMinute: Int) {
        val gap = (60_000L / requestsPerMinute.coerceAtLeast(1))
        mutex.withLock {
            val now = System.currentTimeMillis()
            val wait = (lastSent + gap) - now
            if (wait > 0) delay(wait)
            lastSent = System.currentTimeMillis()
        }
    }
}
