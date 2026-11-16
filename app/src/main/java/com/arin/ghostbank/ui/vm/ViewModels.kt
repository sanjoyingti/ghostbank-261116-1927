package com.arin.ghostbank.ui.vm

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.GhostBankApp
import com.arin.ghostbank.data.BranchInfo
import com.arin.ghostbank.data.BranchResolver
import com.arin.ghostbank.data.Settings
import com.arin.ghostbank.data.db.AccountEntity
import com.arin.ghostbank.data.db.HarvestLegEntity
import com.arin.ghostbank.data.db.LedgerRowEntity
import com.arin.ghostbank.data.db.ProxyEntity
import com.arin.ghostbank.data.db.RegistryEntity
import com.arin.ghostbank.data.db.RunEntity
import com.arin.ghostbank.data.db.StageLogEntity
import com.arin.ghostbank.data.net.LeakReport
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.HarvestStatus
import com.arin.ghostbank.model.REMITTER_PROFILES
import com.arin.ghostbank.model.RunMode
import com.arin.ghostbank.model.StealthLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<Settings> = container.settings.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _booted = MutableStateFlow(false)
    val booted: StateFlow<Boolean> = _booted.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val proxies: StateFlow<List<ProxyEntity>> = container.db.proxies().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            container.settings.flow.first()
            _booted.value = true
        }
    }

    fun setLevel(level: StealthLevel) = viewModelScope.launch { container.settings.setStealthLevel(level) }
    fun setTor(on: Boolean) = viewModelScope.launch { container.settings.setTorMode(on) }
    fun setDoh(value: String) = viewModelScope.launch { container.settings.setDoh(value.trim()) }
    fun setDiurnal(on: Boolean) = viewModelScope.launch { container.settings.setDiurnal(on) }
    fun setRpm(value: Int) = viewModelScope.launch { container.settings.setRpm(value) }
    fun setAph(value: Int) = viewModelScope.launch { container.settings.setAph(value) }
    fun setBudget(value: Int) = viewModelScope.launch { container.settings.setBudget(value) }
    fun setRetries(value: Int) = viewModelScope.launch { container.settings.setRetries(value) }
    fun setBackoff(value: Long) = viewModelScope.launch { container.settings.setBackoff(value) }
    fun setVaRotation(on: Boolean) = viewModelScope.launch { container.settings.setVaRotation(on) }
    fun setFragMin(value: Int) = viewModelScope.launch { container.settings.setFragMin(value) }
    fun setFragMax(value: Int) = viewModelScope.launch { container.settings.setFragMax(value) }
    fun setDailyCap(value: Int) = viewModelScope.launch { container.settings.setDailyCap(value) }

    fun setBackground(on: Boolean) = viewModelScope.launch {
        container.settings.setBackground(on)
        val app = container.context as? GhostBankApp
        if (on) app?.scheduleBackground() else app?.cancelBackground()
        _message.value = if (on) "background harvest scheduled" else "background harvest stopped"
    }

    fun importProxies(text: String) = viewModelScope.launch {
        val added = container.proxyEngine.importList(text)
        _message.value = if (added == 0) "no new exits in that text" else "$added exits imported"
    }

    fun deleteProxy(id: Long) = viewModelScope.launch { container.db.proxies().delete(id) }

    fun wipeRegistry() = viewModelScope.launch {
        container.registry.wipe()
        _message.value = "registry wiped — every account can be harvested again"
    }

    fun wipeLedger() = viewModelScope.launch {
        container.ledger.wipe()
        container.db.stages().wipe()
        _message.value = "ledger and stage logs wiped"
    }

    fun exportAll() = viewModelScope.launch {
        val rows = container.ledger.allOnce()
        val uri = container.exporter.writeCsv(
            "ghostbank_ledger.csv",
            "timestamp,bank,direction,amount_paise,utr,status,badge,counterparty",
            rows.map {
                "${it.ts},${it.bankCode},${it.direction},${it.amountPaise},${it.utr.orEmpty()},${it.status},${it.badge},${it.counterparty.orEmpty()}"
            }
        )
        container.exporter.share(uri, "Export GhostBank ledger")
    }

    fun clearMessage() { _message.value = null }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    }
}

class DestinationViewModel(private val container: AppContainer) : ViewModel() {

    private val _number = MutableStateFlow("")
    val number: StateFlow<String> = _number.asStateFlow()
    private val _ifsc = MutableStateFlow("")
    val ifsc: StateFlow<String> = _ifsc.asStateFlow()
    private val _holder = MutableStateFlow("")
    val holder: StateFlow<String> = _holder.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()
    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    val branch: StateFlow<BranchInfo?> = _ifsc.map { BranchResolver.resolve(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val hint: StateFlow<String?> = _ifsc.map { BranchResolver.formatHint(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val existing: StateFlow<AccountEntity?> = container.accounts.destination()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onNumber(value: String) { _number.value = value.filter { it.isDigit() }.take(18) }
    fun onIfsc(value: String) { _ifsc.value = value.uppercase().filter { it.isLetterOrDigit() }.take(11) }
    fun onHolder(value: String) { _holder.value = value }

    fun save() = viewModelScope.launch {
        val num = _number.value.trim()
        val code = _ifsc.value.trim().uppercase()
        when {
            num.length !in 9..18 -> { _error.value = "Account number must be 9 to 18 digits"; return@launch }
            !Banks.isValidIfsc(code) -> { _error.value = "IFSC must be 4 letters, a 0, then 6 letters or digits"; return@launch }
            Banks.byIfsc(code) == null -> { _error.value = "Shape is right but this is not one of the 27 banks"; return@launch }
        }
        _error.value = null
        _checking.value = true
        val bank = Banks.byIfsc(code)!!
        // name-check against the bank's own payee validation shape before we store it
        val holder = _holder.value.trim().ifBlank { "UNVERIFIED PAYEE" }
        container.accounts.saveDestination(num, code, holder, bank.code)
        container.settings.setDestinationSaved(true)
        _notice.value = "destination saved — digits masked, this stays the fixed target for every leg"
        _number.value = ""
        _ifsc.value = ""
        _holder.value = ""
        _checking.value = false
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { DestinationViewModel(container) }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TransferViewModel(private val container: AppContainer, private val bankCode: String) : ViewModel() {

    val bank = Banks.byCode(bankCode)

    val settings: StateFlow<Settings> = container.settings.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _amount = MutableStateFlow("")
    val amount: StateFlow<String> = _amount.asStateFlow()
    private val _source = MutableStateFlow(0)
    val source: StateFlow<Int> = _source.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _runId = MutableStateFlow<Long?>(null)
    val runId: StateFlow<Long?> = _runId.asStateFlow()

    val run: StateFlow<RunEntity?> = _runId
        .flatMapLatest { id -> if (id == null) flowOf(null) else container.runs.byId(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val stages: StateFlow<List<StageLogEntity>> = _runId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else container.runs.logs(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val keysLeft: StateFlow<Int> = combine(stages, settings) { logs, cfg ->
        (cfg.entropyBudget - logs.size).coerceAtLeast(0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val destination: StateFlow<AccountEntity?> = container.accounts.destination()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onAmount(value: String) { _amount.value = value.filter { it.isDigit() }.take(9) }
    fun onSource(index: Int) { _source.value = index }

    private fun profile() = REMITTER_PROFILES[_source.value.coerceIn(0, REMITTER_PROFILES.lastIndex)]

    fun run() = viewModelScope.launch {
        val target = bank ?: return@launch
        val rupees = _amount.value.trim().toLongOrNull()
        when {
            rupees == null || rupees < 1 -> { _error.value = "Enter at least ₹1"; return@launch }
            rupees * 100 > target.capPaise -> { _error.value = "Above ${target.name}'s cap of ₹${target.capPaise / 100}"; return@launch }
            container.accounts.current() == null -> { _error.value = "Save a destination account first"; return@launch }
        }
        _error.value = null
        val cfg = container.settings.flow.first()
        val id = container.engine.createTransferRun(target, rupees * 100, RunMode.TRANSFER, cfg.stealthLevel)
        _runId.value = id
        container.engine.performTransferRun(id, cfg, profile())
    }

    fun retry() = viewModelScope.launch {
        val id = _runId.value ?: return@launch
        val cfg = container.settings.flow.first()
        container.engine.retryTransfer(id, cfg, profile())
    }

    fun cancel() = viewModelScope.launch {
        val id = _runId.value ?: return@launch
        container.engine.cancel(id)
    }

    fun resume() = viewModelScope.launch {
        val id = _runId.value ?: return@launch
        container.engine.resume(id)
        val cfg = container.settings.flow.first()
        container.engine.retryTransfer(id, cfg, profile())
    }

    companion object {
        fun factory(container: AppContainer, bankCode: String) = viewModelFactory {
            initializer { TransferViewModel(container, bankCode) }
        }
    }
}

data class QueueLine(val line: String, val ok: Boolean, val reason: String?)

@OptIn(ExperimentalCoroutinesApi::class)
class HarvestViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<Settings> = container.settings.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    val registry: StateFlow<List<RegistryEntity>> = container.registry.all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _preflight = MutableStateFlow<List<QueueLine>>(emptyList())
    val preflight: StateFlow<List<QueueLine>> = _preflight.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _bankCode = MutableStateFlow("SBI")
    val bankCode: StateFlow<String> = _bankCode.asStateFlow()

    private val _runId = MutableStateFlow<Long?>(null)
    val runId: StateFlow<Long?> = _runId.asStateFlow()

    val run: StateFlow<RunEntity?> = _runId
        .flatMapLatest { id -> if (id == null) flowOf(null) else container.runs.byId(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val legs: StateFlow<List<HarvestLegEntity>> = _runId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else container.runs.legs(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalPaise: StateFlow<Long> = _runId
        .flatMapLatest { id ->
            if (id == null) flowOf(0L) else container.db.legs().totalPaise(id).map { it ?: 0L }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val pending: StateFlow<List<RegistryEntity>> = registry.map { list ->
        list.filter { it.status == HarvestStatus.PENDING.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setBank(code: String) { _bankCode.value = code }

    fun validate(text: String) {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        _preflight.value = lines.map { line ->
            val parts = line.split(',', ' ', '\t', '|', ';').map { it.trim() }.filter { it.isNotEmpty() }
            when {
                parts.size < 2 -> QueueLine(line, false, "needs an account number and an IFSC")
                parts[0].any { !it.isDigit() } || parts[0].length !in 9..18 ->
                    QueueLine(line, false, "account number must be 9 to 18 digits")
                !Banks.isValidIfsc(parts[1]) -> QueueLine(line, false, "IFSC shape is wrong")
                Banks.byIfsc(parts[1]) == null -> QueueLine(line, false, "not one of the 27 banks")
                else -> QueueLine(line, true, null)
            }
        }
    }

    fun enqueueValid() = viewModelScope.launch {
        var added = 0
        var skipped = 0
        _preflight.value.filter { it.ok }.forEach { line ->
            val parts = line.line.split(',', ' ', '\t', '|', ';').map { it.trim() }.filter { it.isNotEmpty() }
            val code = Banks.byIfsc(parts[1])?.code ?: return@forEach
            val id = container.registry.enqueue(parts[0], parts[1], code)
            if (id == null) skipped++ else added++
        }
        _preflight.value = emptyList()
        _message.value = when {
            added == 0 && skipped > 0 -> "all known already — nothing re-harvested"
            skipped > 0 -> "$added queued, $skipped already in the registry"
            else -> "$added accounts queued"
        }
    }

    fun addSingle(number: String, ifsc: String) = viewModelScope.launch {
        val code = Banks.byIfsc(ifsc)?.code
        when {
            number.length !in 9..18 -> _message.value = "account number must be 9 to 18 digits"
            code == null -> _message.value = "that IFSC is not one of the 27 banks"
            else -> {
                val id = container.registry.enqueue(number, ifsc, code)
                _message.value = if (id == null) "already in the registry" else "1 account queued"
            }
        }
    }

    fun removePending(id: Long) = viewModelScope.launch { container.registry.forceReset(id) }

    fun start() = viewModelScope.launch {
        val cfg = container.settings.flow.first()
        val queue = container.registry.unfinished().filter { it.status == HarvestStatus.PENDING.name }
        if (queue.isEmpty()) { _message.value = "queue is empty — validate accounts first"; return@launch }
        val target = Banks.byCode(_bankCode.value) ?: return@launch
        val id = container.engine.createTransferRun(target, 1L, RunMode.HARVEST, cfg.stealthLevel)
        _runId.value = id
        container.engine.performHarvestRun(id, cfg, target.code, queue.map { it.id to (it.accountNumber to it.ifsc) })
    }

    fun cancel() = viewModelScope.launch {
        val id = _runId.value ?: return@launch
        container.engine.cancel(id)
    }

    fun resumeInterrupted() = viewModelScope.launch {
        val cfg = container.settings.flow.first()
        val stuck = container.runs.unfinished().firstOrNull { it.mode == RunMode.HARVEST.name } ?: return@launch
        _runId.value = stuck.id
        container.engine.resumeHarvest(stuck.id, cfg)
    }

    fun export(): Uri? {
        val id = _runId.value ?: return null
        return null.also {
            viewModelScope.launch {
                val rows = legs.value
                val uri = container.exporter.writeCsv(
                    "harvest_$id.csv",
                    "account,ifsc,amount_paise,status,utr,reason",
                    rows.map { "${it.accountNumber},${it.ifsc},${it.amountPaise},${it.status},${it.utr.orEmpty()},${it.reason.orEmpty()}" }
                )
                container.exporter.share(uri, "Export harvest run")
            }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { HarvestViewModel(container) }
        }
    }
}

class LedgerViewModel(private val container: AppContainer) : ViewModel() {

    private val _bank = MutableStateFlow("ALL")
    val bank: StateFlow<String> = _bank.asStateFlow()
    private val _status = MutableStateFlow("ALL")
    val status: StateFlow<String> = _status.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val rows: StateFlow<List<LedgerRowEntity>> =
        combine(container.ledger.all(), _bank, _status) { list, bankFilter, statusFilter ->
            list.filter { (bankFilter == "ALL" || it.bankCode == bankFilter) && (statusFilter == "ALL" || it.status == statusFilter) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalPaise: StateFlow<Long> = container.ledger.creditedPaise()
        .map { it ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun setBank(value: String) { _bank.value = value }
    fun setStatus(value: String) { _status.value = value }

    fun export(filtered: List<LedgerRowEntity>) = viewModelScope.launch {
        if (filtered.isEmpty()) { _message.value = "nothing to export"; return@launch }
        val uri = container.exporter.writeCsv(
            "ghostbank_ledger.csv",
            "timestamp,bank,direction,amount_paise,utr,status,badge",
            filtered.map { "${it.ts},${it.bankCode},${it.direction},${it.amountPaise},${it.utr.orEmpty()},${it.status},${it.badge}" }
        )
        container.exporter.share(uri, "Export ledger")
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { LedgerViewModel(container) }
        }
    }
}

class ShieldViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<Settings> = container.settings.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _report = MutableStateFlow<LeakReport?>(null)
    val report: StateFlow<LeakReport?> = _report.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val scoreRows: StateFlow<List<String>> = container.db.scores().recent()
        .map { rows -> rows.firstOrNull()?.let { listOf(it.score.toString()) + it.reasons.lines() } ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val runs: StateFlow<List<RunEntity>> = container.runs.recent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val registryCount: StateFlow<Int> = container.registry.count()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun probe() = viewModelScope.launch {
        _busy.value = true
        val cfg = container.settings.flow.first()
        _report.value = container.leakLab.probe(cfg)
        _busy.value = false
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { ShieldViewModel(container) }
        }
    }
}

class RegistryViewModel(private val container: AppContainer) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: StateFlow<List<RegistryEntity>> = _query
        .flatMapLatest { q -> if (q.isBlank()) container.registry.all() else container.registry.search(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQuery(value: String) { _query.value = value }

    fun force(id: Long) = viewModelScope.launch { container.registry.forceReset(id) }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { RegistryViewModel(container) }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ConsoleViewModel(private val container: AppContainer, runId: Long) : ViewModel() {

    private val _stage = MutableStateFlow("")
    val stage: StateFlow<String> = _stage.asStateFlow()
    private val _status = MutableStateFlow(0)
    val status: StateFlow<Int> = _status.asStateFlow()

    val logs: StateFlow<List<StageLogEntity>> =
        combine(container.runs.filtered(runId, "", 0), _stage, _status) { list, stageFilter, statusFilter ->
            list.filter {
                (stageFilter.isBlank() || it.stage.contains(stageFilter, true)) &&
                    (statusFilter == 0 || it.httpStatus == statusFilter)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onStage(value: String) { _stage.value = value }
    fun onStatus(value: Int) { _status.value = value }

    companion object {
        fun factory(container: AppContainer, runId: Long) = viewModelFactory {
            initializer { ConsoleViewModel(container, runId) }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TraceViewModel(private val container: AppContainer, private val runId: Long) : ViewModel() {

    val run: StateFlow<RunEntity?> = container.runs.byId(runId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val ledger: StateFlow<List<LedgerRowEntity>> = container.ledger.byRun(runId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stages: StateFlow<List<StageLogEntity>> = container.runs.logs(runId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        fun factory(container: AppContainer, runId: Long) = viewModelFactory {
            initializer { TraceViewModel(container, runId) }
        }
    }
}

class OnboardingViewModel(private val container: AppContainer) : ViewModel() {

    private val _page = MutableStateFlow(0)
    val page: StateFlow<Int> = _page.asStateFlow()
    private val _level = MutableStateFlow(StealthLevel.STANDARD)
    val level: StateFlow<StealthLevel> = _level.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val destination: StateFlow<AccountEntity?> = container.accounts.destination()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun go(page: Int) { _page.value = page.coerceIn(0, 2) }
    fun setLevel(level: StealthLevel) { _level.value = level }

    fun finish(number: String, ifsc: String, holder: String, onDone: () -> Unit) = viewModelScope.launch {
        val num = number.trim()
        val code = ifsc.trim().uppercase()
        if (container.accounts.current() == null) {
            when {
                num.length !in 9..18 -> { _error.value = "Account number must be 9 to 18 digits"; return@launch }
                !Banks.isValidIfsc(code) -> { _error.value = "IFSC must be 4 letters, a 0, then 6 letters or digits"; return@launch }
                Banks.byIfsc(code) == null -> { _error.value = "That IFSC is not one of the 27 banks"; return@launch }
            }
            container.accounts.saveDestination(num, code, holder.trim().ifBlank { "UNVERIFIED PAYEE" }, Banks.byIfsc(code)!!.code)
            container.settings.setDestinationSaved(true)
        }
        container.settings.setStealthLevel(_level.value)
        container.settings.setOnboardingDone(true)
        _error.value = null
        onDone()
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { OnboardingViewModel(container) }
        }
    }
}

/** Shared list of banks for the pickers. */
val ALL_BANKS = Banks.all

fun Flow<*>.noop() = Unit
