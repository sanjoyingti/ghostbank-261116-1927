package com.arin.ghostbank.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Query("SELECT * FROM accounts WHERE isDestination = 1 ORDER BY createdAt DESC LIMIT 1")
    fun destination(): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE isDestination = 1 ORDER BY createdAt DESC LIMIT 1")
    suspend fun destinationOnce(): AccountEntity?

    @Query("SELECT * FROM accounts WHERE bankCode = :bank ORDER BY createdAt DESC")
    fun byBank(bank: String): Flow<List<AccountEntity>>

    @Query("UPDATE accounts SET isDestination = 0")
    suspend fun clearDestination()

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface RegistryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: RegistryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entry: RegistryEntity): Long

    @Query("SELECT * FROM registry WHERE hash = :hash LIMIT 1")
    suspend fun byHash(hash: String): RegistryEntity?

    @Query("SELECT * FROM registry ORDER BY lastAttempt DESC")
    fun all(): Flow<List<RegistryEntity>>

    @Query("SELECT * FROM registry WHERE accountNumber LIKE '%' || :q || '%' OR ifsc LIKE '%' || :q || '%' ORDER BY lastAttempt DESC")
    fun search(q: String): Flow<List<RegistryEntity>>

    @Query("UPDATE registry SET status = :status, utr = :utr, reason = :reason, amountPaise = :amount, lastAttempt = :ts WHERE id = :id")
    suspend fun updateOutcome(id: Long, status: String, utr: String?, reason: String?, amount: Long, ts: Long)

    @Query("UPDATE registry SET status = 'PENDING', forced = forced + 1, lastAttempt = :ts WHERE id = :id")
    suspend fun forceReset(id: Long, ts: Long)

    @Query("SELECT COUNT(*) FROM registry")
    fun count(): Flow<Int>

    @Query("DELETE FROM registry")
    suspend fun wipe()

    @Query("SELECT * FROM registry WHERE status IN ('PENDING','SENT')")
    suspend fun unfinished(): List<RegistryEntity>
}

@Dao
interface RunDao {
    @Insert
    suspend fun insert(run: RunEntity): Long

    @Update
    suspend fun update(run: RunEntity)

    @Query("SELECT * FROM runs WHERE id = :id")
    fun byId(id: Long): Flow<RunEntity?>

    @Query("SELECT * FROM runs WHERE id = :id")
    suspend fun byIdOnce(id: Long): RunEntity?

    @Query("SELECT * FROM runs ORDER BY startedAt DESC LIMIT 100")
    fun recent(): Flow<List<RunEntity>>

    @Query("SELECT * FROM runs WHERE status = 'RUNNING' OR status = 'PAUSED' ORDER BY startedAt DESC")
    suspend fun unfinished(): List<RunEntity>
}

@Dao
interface StageLogDao {
    @Insert
    suspend fun insert(log: StageLogEntity): Long

    @Query("SELECT * FROM stage_logs WHERE runId = :runId ORDER BY ts ASC")
    fun byRun(runId: Long): Flow<List<StageLogEntity>>

    @Query("SELECT * FROM stage_logs WHERE runId = :runId AND (stage LIKE '%' || :stage || '%') AND (:status = 0 OR httpStatus = :status) ORDER BY ts ASC")
    fun filtered(runId: Long, stage: String, status: Int): Flow<List<StageLogEntity>>

    @Query("DELETE FROM stage_logs")
    suspend fun wipe()
}

@Dao
interface HarvestLegDao {
    @Insert
    suspend fun insert(leg: HarvestLegEntity): Long

    @Query("SELECT * FROM harvest_legs WHERE runId = :runId ORDER BY ts ASC")
    fun byRun(runId: Long): Flow<List<HarvestLegEntity>>

    @Query("SELECT SUM(amountPaise) FROM harvest_legs WHERE runId = :runId AND status IN ('SETTLED','FLOOR')")
    fun totalPaise(runId: Long): Flow<Long?>

    @Query("UPDATE harvest_legs SET status = :status, utr = :utr, reason = :reason WHERE id = :id")
    suspend fun update(id: Long, status: String, utr: String?, reason: String?)
}

@Dao
interface LedgerDao {
    @Insert
    suspend fun insert(row: LedgerRowEntity): Long

    @Query("SELECT * FROM ledger ORDER BY ts DESC")
    fun all(): Flow<List<LedgerRowEntity>>

    @Query("SELECT * FROM ledger WHERE bankCode = :bank ORDER BY ts DESC")
    fun byBank(bank: String): Flow<List<LedgerRowEntity>>

    @Query("SELECT * FROM ledger WHERE runId = :runId ORDER BY direction ASC")
    fun byRun(runId: Long): Flow<List<LedgerRowEntity>>

    @Query("UPDATE ledger SET status = :status WHERE runId = :runId AND direction = 'DEBIT'")
    suspend fun markDebit(runId: Long, status: String)

    @Query("SELECT * FROM ledger ORDER BY ts DESC")
    suspend fun allOnce(): List<LedgerRowEntity>

    @Query("SELECT SUM(amountPaise) FROM ledger WHERE direction = 'CREDIT' AND status = 'RECEIVED'")
    fun creditedPaise(): Flow<Long?>

    @Query("DELETE FROM ledger")
    suspend fun wipe()
}

@Dao
interface ProxyDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(proxy: ProxyEntity): Long

    @Query("SELECT * FROM proxies ORDER BY id ASC")
    fun all(): Flow<List<ProxyEntity>>

    @Query("SELECT * FROM proxies WHERE healthy = 1")
    suspend fun healthy(): List<ProxyEntity>

    @Query("UPDATE proxies SET lastUsed = :ts WHERE id = :id")
    suspend fun touch(id: Long, ts: Long)

    @Query("UPDATE proxies SET healthy = :healthy WHERE id = :id")
    suspend fun setHealth(id: Long, healthy: Boolean)

    @Query("DELETE FROM proxies WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM proxies")
    suspend fun wipe()
}

@Dao
interface GhostProfileDao {
    @Insert
    suspend fun insert(profile: GhostProfileEntity): Long

    @Query("SELECT * FROM ghost_profiles WHERE burned = 0 ORDER BY createdAt ASC LIMIT 1")
    suspend fun takeFresh(): GhostProfileEntity?

    @Query("UPDATE ghost_profiles SET burned = 1 WHERE id = :id")
    suspend fun burn(id: Long)

    @Query("SELECT COUNT(*) FROM ghost_profiles WHERE burned = 0")
    suspend fun freshCount(): Int

    @Query("SELECT * FROM ghost_profiles ORDER BY createdAt DESC LIMIT 50")
    fun recent(): Flow<List<GhostProfileEntity>>
}

@Dao
interface StealthScoreDao {
    @Insert
    suspend fun insert(score: StealthScoreEntity): Long

    @Query("SELECT * FROM stealth_scores WHERE runId = :runId ORDER BY ts DESC LIMIT 1")
    fun byRun(runId: Long): Flow<StealthScoreEntity?>

    @Query("SELECT * FROM stealth_scores ORDER BY ts DESC LIMIT 50")
    fun recent(): Flow<List<StealthScoreEntity>>
}
