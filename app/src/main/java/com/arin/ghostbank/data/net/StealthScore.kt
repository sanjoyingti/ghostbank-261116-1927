=== ABOUT: Turns the run's stealth checks into a 0-100 score plus the plain reasons that moved it ===
package com.arin.ghostbank.data.net

import kotlin.math.max

object StealthScore {

    data class Checks(
        val fingerprintMatched: Boolean,
        val exitGeoMatched: Boolean,
        val dohActive: Boolean,
        val killSwitchArmed: Boolean,
        val profileFresh: Boolean,
        val jitterQuality: Int,
        val velocityUnderCap: Boolean,
        val wafClean: Boolean
    )

    fun compute(checks: Checks): Pair<Int, List<String>> {
        var score = 100
        val reasons = mutableListOf<String>()

        if (!checks.fingerprintMatched) {
            score -= 25
            reasons += "-25 fingerprint did not match the Chrome baseline"
        }
        if (!checks.exitGeoMatched) {
            score -= 15
            reasons += "-15 exit country did not match the device/bank region"
        }
        if (!checks.dohActive) {
            score -= 10
            reasons += "-10 DNS-over-HTTPS was not active"
        }
        if (!checks.killSwitchArmed) {
            score -= 15
            reasons += "-15 kill-switch was not armed (no exit configured)"
        }
        if (!checks.profileFresh) {
            score -= 10
            reasons += "-10 the ghost profile was reused"
        }
        if (checks.jitterQuality < 2) {
            val penalty = 8 * (2 - checks.jitterQuality)
            score -= penalty
            reasons += "-$penalty gaps between stages were ${if (checks.jitterQuality == 0) "mechanical" else "loosely spaced"}"
        }
        if (!checks.velocityUnderCap) {
            score -= 10
            reasons += "-10 velocity cap was exceeded"
        }
        if (!checks.wafClean) {
            score -= 7
            reasons += "-7 the WAF challenged a request"
        }
        if (reasons.isEmpty()) reasons += "all checks passed — nothing obvious gave the run away"

        return max(0, score.coerceAtMost(100)) to reasons
    }
}
