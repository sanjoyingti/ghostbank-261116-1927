package com.arin.ghostbank.data.repo

import com.arin.ghostbank.data.db.AccountDao
import com.arin.ghostbank.data.db.AccountEntity
import com.arin.ghostbank.data.db.HarvestLegDao
import com.arin.ghostbank.data.db.LedgerDao
import com.arin.ghostbank.data.db.LedgerRowEntity
import com.arin.ghostbank.data.db.RegistryDao
import com.arin.ghostbank.data.db.RegistryEntity
import com.arin.ghostbank.data.db.RunDao
import com.arin.ghostbank.data.db.RunEntity
import com.arin.ghostbank.data.db.StageLogDao
import com.arin.ghostbank.data.db.StageLogEntity
import com.arin.ghostbank.model.HarvestStatus
import com.arin.ghostbank.model.Ids
import kotlinx.coroutines.flow.Flow

class AccountRepository(private val dao: AccountDao) {
    fun destination(): Flow<AccountEntity?> = dao.destination()
    suspend fun current(): AccountEntity? = dao.destinationOnce()

    suspend fun saveDestination(number: String, ifsc: String, holder: String?, bankCode: String): Long {
        dao.clearDestination()
        return dao.insert(
            AccountEntity(
                number = number,
                ifsc = ifsc.uppercase(),
                holderName = holder,
                bankCode = bankCode,
                isDestination = true,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    fun byBank(bank: String): Flow<List<AccountEntity>> = dao.byBank(bank)
}

class RegistryRepository(private val dao: RegistryDao) {

    fun all(): Flow<List<RegistryEntity>> = dao.all()
    fun search(q: String): Flow<List<RegistryEntity>> = dao.search(q)
    suspend fun byHash(hash: String): RegistryEntity? = dao.byHash(hash)
    suspend fun unfinished(): List<RegistryEntity> = dao.unfinished()
    fun count(): Flow<Int> = dao.count()

    /** Writes the dedupe record BEFORE the request leaves the device. */
    suspend fun claim(accountNumber: String, ifsc: String, bankCode: String, amountPaise: Long, now: Long): RegistryEntity {
        val hash = Ids.sha256("${accountNumber.trim()}|${ifsc.trim().uppercase()}")
        val existing = dao.byHash(hash)
        if (existing != null) {
            dao.updateOutcome(existing.id, HarvestStatus.SENT.name, existing.utr, existing.reason, amountPaise, now)
            return existing.copy(status = HarvestStatus.SENT.name, amountPaise = amountPaise, lastAttempt = now)
        }
        val entity = RegistryEntity(
            hash = hash,
            accountNumber = accountNumber.trim(),
            ifsc = ifsc.trim().uppercase(),
            bankCode = bankCode,
            status = HarvestStatus.SENT.name,
            utr = null,
            reason = null,
            amountPaise = amountPaise,
            firstSeen = now,
            lastAttempt = now
        )
        val id = dao.upsert(entity)
        return entity.copy(id = if (id > 0) id else (dao.byHash(hash)?.id ?: 0))
    }

    /** Parks an account in the queue as PENDING without claiming it yet. Null if it is already known. */
    suspend fun enqueue(accountNumber: String, ifsc: String, bankCode: String): Long? {
        val hash = Ids.sha256("${accountNumber.trim()}|${ifsc.trim().uppercase()}")
        dao.byHash(hash)?.let { return null }
        val now = System.currentTimeMillis()
        val id = dao.insertIgnore(
            RegistryEntity(
                hash = hash,
                accountNumber = accountNumber.trim(),
                ifsc = ifsc.trim().uppercase(),
                bankCode = bankCode,
                status = HarvestStatus.PENDING.name,
                utr = null,
                reason = null,
                amountPaise = 0,
                firstSeen = now,
                lastAttempt = now
            )
        )
        return if (id > 0) id else dao.byHash(hash)?.id
    }

    suspend fun outcome(id: Long, status: HarvestStatus, utr: String?, reason: String?, amountPaise: Long) {
        dao.updateOutcome(id, status.name, utr, reason, amountPaise, System.currentTimeMillis())
    }

    suspend fun forceReset(id: Long) = dao.forceReset(id, System.currentTimeMillis())
    suspend fun wipe() = dao.wipe()
}

class RunRepository(
    private val runs: RunDao,
    private val stages: StageLogDao,
    private val legs: HarvestLegDao
) {
    fun recent(): Flow<List<RunEntity>> = runs.recent()
    fun byId(id: Long): Flow<RunEntity?> = runs.byId(id)
    suspend fun byIdOnce(id: Long): RunEntity? = runs.byIdOnce(id)
    fun logs(runId: Long): Flow<List<StageLogEntity>> = stages.byRun(runId)
    fun filtered(runId: Long, stage: String, status: Int): Flow<List<StageLogEntity>> = stages.filtered(runId, stage, status)
    fun legs(runId: Long): Flow<List<com.arin.ghostbank.data.db.HarvestLegEntity>> = legs.byRun(runId)
    suspend fun unfinished(): List<RunEntity> = runs.unfinished()
}

class LedgerRepository(private val dao: LedgerDao) {

    fun all(): Flow<List<LedgerRowEntity>> = dao.all()
    fun byBank(bank: String): Flow<List<LedgerRowEntity>> = dao.byBank(bank)
    fun byRun(runId: Long): Flow<List<LedgerRowEntity>> = dao.byRun(runId)
    fun creditedPaise(): Flow<Long?> = dao.creditedPaise()
    suspend fun insert(row: LedgerRowEntity): Long = dao.insert(row)
    suspend fun allOnce(): List<LedgerRowEntity> = dao.allOnce()
    suspend fun wipe() = dao.wipe()
    suspend fun markDebit(runId: Long, status: String) = dao.markDebit(runId, status)
}
