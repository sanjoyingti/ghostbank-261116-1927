package com.arin.ghostbank.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val ifsc: String,
    val holderName: String?,
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
    val utr: String?,
    val reason: String?,
    val amountPaise: Long,
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
    val stealthScore: Int,
    val exitIp: String?,
    val exitCountry: String?,
    val stealthLevel: String,
    val startedAt: Long,
    val endedAt: Long?,
    val lastConfirmedStage: String?,
    val errorKind: String?,
    val errorText: String?,
    val summary: String?
)

@Entity(tableName = "stage_logs", indices = [Index("runId")])
data class StageLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: Long,
    val stage: String,
    val ts: Long,
    val httpStatus: Int?,
    val latencyMs: Long?,
    val badge: String,
    val requestHeaders: String?,
    val responseHeaders: String?,
    val rawBody: String?,
    val note: String?
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
    val utr: String?,
    val reason: String?,
    val ts: Long
)

@Entity(tableName = "ledger")
data class LedgerRowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: Long,
    val bankCode: String,
    val direction: String,
    val amountPaise: Long,
    val utr: String?,
    val status: String,
    val badge: String,
    val ts: Long,
    val counterparty: String?
)

@Entity(tableName = "proxies")
data class ProxyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val kind: String,
    val country: String?,
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
