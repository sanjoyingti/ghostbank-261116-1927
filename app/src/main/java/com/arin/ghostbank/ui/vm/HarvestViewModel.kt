=== ABOUT: Bulk account paste, per-line checks, the dedupe guard and the paise counter behind the harvest screen ===
package com.arin.ghostbank.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.db.HarvestLegEntity
import com.arin.ghostbank.data.db.RegistryEntity
import com.arin.ghostbank.data.net.LeakState
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.HarvestStatus
import com.arin.ghostbank.model.Ids
import com.arin.ghostbank.model.RunMode
import com.arin.ghostbank.model.StealthLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HarvestLine(
    val raw: String,
    val number: String,
    val ifsc: String,
    val error: String?,
    val alreadyHarvested: Boolean
) {
    val valid: Boolean get() = error == null
}

class HarvestViewModel(private val container: AppContainer) : ViewModel() {

    private val _bankCode = MutableStateFlow("SBI")
    val bankCode: StateFlow<String> = _bankCode.asStateFlow()

    private val _defaultIfsc = MutableStateFlow("")
    val defaultIfsc: StateFlow<String> = _defaultIfsc.asStateFlow()

    private val _paste = MutableStateFlow("")
    val paste: StateFlow<String> = _paste.asStateFlow()

    private val _lines = MutableStateFlow<List<HarvestLine>>(emptyList())
    val lines: StateFlow<List<HarvestLine>> = _lines.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _runId = MutableStateFlow<Long?>(null)
    val runId: StateFlow<Long?> = _runId.asStateFlow()

    val registry: StateFlow<List<RegistryEntity>> = container.registry.all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val legs: StateFlow<List<HarvestLegEntity>> = _runId.flatMapLatest { id ->
        if (id == null) kotlinx.coroutines.flow.flowOf(emptyList()) else container.runs.legs(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalPaise: StateFlow<Long> = _runId.flatMapLatest { id ->
        if (id == null) kotlinx.coroutines.flow.flowOf(0L) else container.db.legs().totalPaise(id).map { it ?: 0L }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun setBank(code: String) { _bankCode.value = code; rebuild() }
    fun setDefaultIfsc(value: String) { _defaultIfsc.value = value.uppercase().filter { it.isLetterOrDigit() }.take(11); rebuild() }
    fun setPaste(value: String) { _paste.value = value }

    fun parsePaste() = viewModelScope.launch {
        val harvested = container.registry.all().first().map { it.hash }.toSet()
        val rebuilt = _paste.value.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { raw ->
            build(raw, harvested)
        }
        _lines.value = rebuilt
        _error.value = if (rebuilt.isEmpty()) "Paste at least one account number, one per line." else null
    }

    private fun rebuild() {
        viewModelScope.launch {
            val harvested = container.registry.all().first().map { it.hash }.toSet()
            _lines.value = _lines.value.map { build(it.raw, harvested) }
        }
    }

    private fun build(raw: String, harvested: Set<String>): HarvestLine {
        val parts = raw.split(",", " ", "\t", ";").map { it.trim() }.filter { it.isNotEmpty() }
        val number = parts.firstOrNull().orEmpty().filter { it.isDigit() }
        val ifsc = (parts.getOrNull(1) ?: _defaultIfsc.value).uppercase().filter { it.isLetterOrDigit() }
        val error = when {
            number.length < 8 -> "account number too short"
            !Banks.isValidIfsc(ifsc) -> "IFSC must be 4 letters, a 0, then 6 letters or digits"
            Banks.byIfsc(ifsc) == null -> "IFSC not one of the 27 supported banks"
            else -> null
        }
        val already = harvested.contains(Ids.sha256("${number.trim()}|${ifsc.trim().uppercase()}"))
        return HarvestLine(raw, number, ifsc, error, already)
    }

    fun addSingle(number: String, ifsc: String) = viewModelScope.launch {
        val raw = "$number,$ifsc"
        val harvested = container.registry.all().first().map { it.hash }.toSet()
        _lines.value = _lines.value + build(raw, harvested)
    }

    fun remove(index: Int) {
        _lines.value = _lines.value.toMutableList().also { it.removeAt(index) }
    }

    fun forceInclude(index: Int) {
        _lines.value = _lines.value.toMutableList().also {
            val line = it[index]
            it[index] = line.copy(alreadyHarvested = false)
        }
    }

    fun clearQueue() { _lines.value = emptyList() }

    fun start() = viewModelScope.launch {
        if (_running.value) return@launch
        val bank = Banks.byCode(_bankCode.value) ?: run { _error.value = "Pick a bank."; return@launch }
        val destination = container.accounts.current()
        if (destination == null) { _error.value = "Save your destination account first."; return@launch }
        val targets = _lines.value.filter { it.valid && !it.alreadyHarvested }
        if (targets.isEmpty()) { _error.value = "Nothing to send — every account is invalid or already harvested."; return@launch }

        val settings = container.settings.flow.first()
        if (settings.stealthLevel == StealthLevel.GHOST && LeakState.passed == false) {
            _error.value = "Ghost level: the leak check must pass before any traffic leaves."
            return@launch
        }

        _error.value = null
        _running.value = true
        try {
            val id = container.engine.createTransferRun(bank, 1L, RunMode.HARVEST, settings.stealthLevel)
            _runId.value = id
            val keys = mutableListOf<Pair<Long, Pair<String, String>>>()
            targets.forEach { line ->
                // Written to the dedupe registry BEFORE the request leaves the device.
                val claimed = container.registry.claim(line.number, line.ifsc, bank.code, 1L, System.currentTimeMillis())
                keys += claimed.id to (line.number to line.ifsc)
            }
            container.engine.performHarvestRun(id, settings, bank.code, keys)
        } finally {
            _running.value = false
        }
    }

    fun cancel() = viewModelScope.launch {
        _runId.value?.let { container.engine.cancel(it) }
        _running.value = false
    }

    fun retryRejected() = viewModelScope.launch {
        val id = _runId.value ?: return@launch
        val settings = container.settings.flow.first()
        val rejected = legs.value.filter { it.status == HarvestStatus.REJECTED.name }
        if (rejected.isEmpty()) return@launch
        rejected.forEach { container.registry.forceReset(it.registryId) }
        val keys = rejected.map { leg ->
            container.registry.claim(leg.accountNumber, leg.ifsc, _bankCode.value, leg.amountPaise, System.currentTimeMillis()).id to
                (leg.accountNumber to leg.ifsc)
        }
        _running.value = true
        try {
            container.engine.performHarvestRun(id, settings, _bankCode.value, keys)
        } finally {
            _running.value = false
        }
    }

    fun exportSummary() = viewModelScope.launch {
        val rows = legs.value.map {
            "${it.ts},${it.accountNumber},${it.ifsc},${it.amountPaise},${it.status},${it.utr ?: ""},${it.reason ?: ""}"
        }
        val uri = container.exporter.writeCsv(
            "harvest_${_runId.value ?: 0}.csv",
            "ts,account,ifsc,paise,status,utr,reason",
            rows
        )
        container.exporter.share(uri, "Export harvest summary")
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { HarvestViewModel(container) }
        }
    }
}
