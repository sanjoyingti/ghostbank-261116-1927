=== ABOUT: Fixed the database records so the optional fields no longer have to be filled in everywhere, which is what broke the build ===
package com.arin.ghostbank.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val ifsc: String,
    val holderName: String? = null,
    val bankCode: String,
    val isDestination: Boolean,
    val createdAt: Long
)

@Entity(tableName = "registry", indices = [Index(value = ["hash"], unique = true)])
data class RegistryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hash: String,
    val accountNumber: String,
    val ifsc: String,
    val bankCode: String,
    val status: String,
    val utr: String? = null,
    val reason: String? = null,
    val amountPaise: Long = 0,
    val forced: Int = 0,
    val firstSeen: Long,
    val lastAttempt: Long
)

@Entity(tableName = "runs")
data class RunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bankCode: String,
    val mode: String,
    val amountPaise: Long,
    val idempotencyKey: String,
    val status: String,
    val badge: String,
    val stealthScore: Int = 0,
    val exitIp: String? = null,
    val exitCountry: String? = null,
    val stealthLevel: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val lastConfirmedStage: String? = null,
    val errorKind: String? = null,
    val errorText: String? = null,
    val summary: String? = null
)

@Entity(tableName = "stage_logs", indices = [Index("runId")])
data class StageLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: Long,
    val stage: String,
    val ts: Long,
    val httpStatus: Int? = null,
    val latencyMs: Long? = null,
    val badge: String,
    val requestHeaders: String? = null,
    val responseHeaders: String? = null,
    val rawBody: String? = null,
    val note: String? = null
)

@Entity(tableName = "harvest_legs", indices = [Index("runId")])
data class HarvestLegEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: Long,
    val registryId: Long,
    val accountNumber: String,
    val ifsc: String,
    val amountPaise: Long,
    val status: String,
    val utr: String? = null,
    val reason: String? = null,
    val ts: Long
)

@Entity(tableName = "ledger")
data class LedgerRowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: Long,
    val bankCode: String,
    val direction: String,
    val amountPaise: Long,
    val utr: String? = null,
    val status: String,
    val badge: String,
    val ts: Long,
    val counterparty: String? = null
)

@Entity(tableName = "proxies")
data class ProxyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val kind: String,
    val country: String? = null,
    val healthy: Boolean = true,
    val lastUsed: Long? = null
)

@Entity(tableName = "ghost_profiles")
data class GhostProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountKey: String,
    val userAgent: String,
    val chUa: String,
    val chMobile: String,
    val chPlatform: String,
    val headerOrder: String,
    val cipherOrder: String,
    val h2Settings: String,
    val burned: Boolean,
    val createdAt: Long
)

@Entity(tableName = "stealth_scores", indices = [Index("runId")])
data class StealthScoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: Long,
    val score: Int,
    val reasons: String,
    val ts: Long
)
