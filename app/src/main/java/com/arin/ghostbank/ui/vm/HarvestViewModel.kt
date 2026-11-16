package com.arin.ghostbank.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.Settings
import com.arin.ghostbank.data.db.HarvestLegEntity
import com.arin.ghostbank.data.db.RunEntity
import com.arin.ghostbank.data.net.LeakState
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.ErrorKind
import com.arin.ghostbank.model.GhostError
import com.arin.ghostbank.model.HarvestStatus
import com.arin.ghostbank.model.Ids
import com.arin.ghostbank.model.RunMode
import com.arin.ghostbank.model.RunStatus
import com.arin.ghostbank.model.StealthLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class HarvestViewModel(private val container: AppContainer) : ViewModel() {

    data class Candidate(
        val number: String,
        val ifsc: String,
        val problem: String?,
        val alreadyHarvested: Boolean,
        val existingStatus: String?
    )

    private val _bankCode = MutableStateFlow("SBI")
    val bankCode: StateFlow<String> = _bankCode.asStateFlow()

    private val _queue = MutableStateFlow<List<Candidate>>(emptyList())
    val queue: StateFlow<List<Candidate>> = _queue.asStateFlow()

    private val _runId = MutableStateFlow<Long?>(null)
    val runId: StateFlow<Long?> = _runId.asStateFlow()

    val legs: StateFlow<List<HarvestLegEntity>> = _runId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else container.runs.legs(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalPaise: StateFlow<Long> = _runId
        .flatMapLatest { id -> if (id == null) flowOf(0L) else container.db.legs().totalPaise(id).map { it ?: 0L } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val run: StateFlow<RunEntity?> = _runId
        .flatMapLatest { id -> if (id == null) flowOf(null) else container.runs.byId(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<Settings> = container.settings.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _error = MutableStateFlow<GhostError?>(null)
    val error: StateFlow<GhostError?> = _error.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _interrupted = MutableStateFlow<RunEntity?>(null)
    val interrupted: StateFlow<RunEntity?> = _interrupted.asStateFlow()

    private val _showCancel = MutableStateFlow(false)
    val showCancel: StateFlow<Boolean> = _showCancel.asStateFlow()

    init {
        viewModelScope.launch {
            val open = container.runs.unfinished().firstOrNull { it.mode == RunMode.HARVEST.name }
            _interrupted.value = open
            if (open != null) _runId.value = open.id
        }
    }

    private suspend fun validate(rawNumber: String, rawIfsc: String): Candidate {
        val number = rawNumber.trim()
        val ifsc = rawIfsc.trim().uppercase()
        val problem = when {
            !Regex("^\\d{9,18}$").matches(number) -> "account number must be 9–18 digits"
            !Banks.isValidIfsc(ifsc) -> "IFSC shape wrong (4 letters, 0, 6 alnum)"
            else -> null
        }
        val existing = if (problem == null) container.registry.byHash(Ids.sha256("$number|$ifsc")) else null
        return Candidate(
            number = number,
            ifsc = ifsc,
            problem = problem,
            alreadyHarvested = existing != null && existing.status != HarvestStatus.PENDING.name,
            existingStatus = existing?.status
        )
    }

    fun addLines(text: String, fallbackIfsc: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val added = mutableListOf<Candidate>()
            text.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
                val parts = line.split(",", "|", " ").map { it.trim() }.filter { it.isNotEmpty() }
                val number = parts.getOrNull(0).orEmpty()
                val ifsc = parts.getOrNull(1) ?: fallbackIfsc
                val candidate = validate(number, ifsc)
                if (_queue.value.none { it.number == candidate.number && it.ifsc == candidate.ifsc }) added += candidate
            }
            _queue.value = _queue.value + added
            _message.value = if (added.isEmpty()) "nothing new in that text" else "${added.size} line(s) added to the pre-flight list"
        }
    }

    fun removeAt(index: Int) {
        _queue.value = _queue.value.filterIndexed { i, _ -> i != index }
    }

    fun clearQueue() { _queue.value = emptyList() }

    fun setBank(code: String) { _bankCode.value = code }

    fun askCancel() { _showCancel.value = true }
    fun dismissCancel() { _showCancel.value = false }

    fun start() {
        if (_running.value) return
        val fresh = _queue.value.filter { it.problem == null && !it.alreadyHarvested }
        if (fresh.isEmpty()) {
            _error.value = GhostError(ErrorKind.CONFIG, "Nothing in the queue can be sent.", "Add valid account numbers that are not already in the registry")
            return
        }
        viewModelScope.launch {
            _running.value = true
            _error.value = null
            val current = settings.value
            if (container.accounts.current() == null) {
                _error.value = GhostError(ErrorKind.CONFIG, "No destination account saved.", "Save your account number and IFSC first")
                _running.value = false
                return@launch
            }
            if (current.stealthLevel == StealthLevel.GHOST && LeakState.passed != true) {
                val report = container.leakLab.probe(current)
                if (!report.passed) {
                    _error.value = GhostError(
                        ErrorKind.CONFIG,
                        "Ghost level is on and the leak lab has not passed, so nothing was sent.",
                        "Open the Shield tab, run the leak lab, then start the harvest",
                        report.error
                    )
                    _running.value = false
                    return@launch
                }
            }
            val bank = _bankCode.value
            val ids = mutableListOf<Pair<Long, Pair<String, String>>>()
            fresh.forEach { candidate ->
                val id = container.registry.enqueue(candidate.number, candidate.ifsc, bank)
                if (id != null) ids += id to (candidate.number to candidate.ifsc)
            }
            if (ids.isEmpty()) {
                _message.value = "every queued account is already in the registry"
                _running.value = false
                return@launch
            }
            val runId = container.engine.createTransferRun(
                Banks.byCode(bank)!!, 1L, RunMode.HARVEST, current.stealthLevel
            )
            _runId.value = runId
            _interrupted.value = null
            // Registry rows are stamped SENT before any request leaves the device.
            ids.forEach { (id, _) ->
                container.registry.outcome(id, HarvestStatus.SENT, null, null, 1L)
            }
            container.engine.performHarvestRun(runId, current, bank, ids)
            _queue.value = emptyList()
            _running.value = false
        }
    }

    fun resumeInterrupted() {
        val open = _interrupted.value ?: return
        viewModelScope.launch {
            _running.value = true
            _error.value = null
            container.engine.resume(open.id)
            val pending = container.registry.unfinished()
                .filter { it.status == HarvestStatus.PENDING.name }
                .map { it.id to (it.accountNumber to it.ifsc) }
            if (pending.isNotEmpty()) {
                container.engine.performHarvestRun(open.id, settings.value, open.bankCode, pending)
            } else {
                _message.value = "nothing left to resume for that run"
            }
            _interrupted.value = null
            _running.value = false
        }
    }

    fun retryRejected(leg: HarvestLegEntity, onNewRun: (Long) -> Unit) {
        viewModelScope.launch {
            val current = settings.value
            val bank = _bankCode.value
            val runId = container.engine.createTransferRun(
                Banks.byCode(bank) ?: return@launch, 1L, RunMode.HARVEST, current.stealthLevel
            )
            container.registry.forceReset(leg.registryId)
            val pair = leg.registryId to (leg.accountNumber to leg.ifsc)
            _runId.value = runId
            onNewRun(runId)
            _running.value = true
            container.engine.performHarvestRun(runId, current, bank, listOf(pair))
            _running.value = false
        }
    }

    fun cancel() {
        val id = _runId.value ?: return
        viewModelScope.launch {
            container.engine.cancel(id)
            _running.value = false
            _message.value = "Harvest cancelled — finished accounts stay in the registry"
        }
    }

    fun export(onUri: (android.net.Uri) -> Unit) {
        val id = _runId.value ?: return
        viewModelScope.launch {
            val rows = container.runs.legs(id)
            val collected = withContext(Dispatchers.IO) {
                var list: List<HarvestLegEntity> = emptyList()
                rows.collect { list = it; return@collect }
                list
            }
            if (collected.isEmpty()) {
                _message.value = "no legs to export yet"
                return@launch
            }
            val uri = withContext(Dispatchers.IO) {
                container.exporter.writeCsv(
                    "harvest_$id.csv",
                    "ts,account,ifsc,paise,status,utr,reason",
                    collected.map { "${it.ts},${it.accountNumber},${it.ifsc},${it.amountPaise},${it.status},${it.utr ?: ""},${it.reason ?: ""}" }
                )
            }
            onUri(uri)
        }
    }

    fun dismissError() { _error.value = null }
    fun dismissMessage() { _message.value = null }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { HarvestViewModel(container) }
        }
    }
}
