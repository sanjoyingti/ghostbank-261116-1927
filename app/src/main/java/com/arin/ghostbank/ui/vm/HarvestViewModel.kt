@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.arin.ghostbank.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.Settings
import com.arin.ghostbank.data.db.HarvestLegEntity
import com.arin.ghostbank.data.db.RunEntity
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.Ids
import com.arin.ghostbank.model.RunMode
import com.arin.ghostbank.model.RunStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PreflightRow(val number: String, val ifsc: String, val status: String, val note: String)

class HarvestViewModel(private val container: AppContainer) : ViewModel() {

    private val _rows = MutableStateFlow<List<PreflightRow>>(emptyList())
    val rows = _rows.asStateFlow()

    val bankCode = MutableStateFlow("SBI")

    private val _runId = MutableStateFlow<Long?>(null)
    val runId = _runId.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    val settings: StateFlow<Settings> = container.settings.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    val run: StateFlow<RunEntity?> = _runId
        .flatMapLatest { id -> if (id == null) flowOf(null) else container.runs.byId(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val legs: StateFlow<List<HarvestLegEntity>> = _runId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else container.runs.legs(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalPaise: StateFlow<Long> = _runId
        .flatMapLatest { id -> if (id == null) flowOf(0L) else container.db.legs().totalPaise(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val interrupted: StateFlow<RunEntity?> = container.runs.recent()
        .let { flow ->
            kotlinx.coroutines.flow.map(flow) { list ->
                list.firstOrNull { it.status == RunStatus.PAUSED.name || it.status == RunStatus.RUNNING.name }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setBank(code: String) {
        bankCode.value = code
    }

    fun ingest(text: String) {
        _error.value = null
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.Default) {
                text.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(60).map { line ->
                    val parts = line.split(' ', ',', '\t', ';', '|').filter { it.isNotBlank() }
                    val number = parts.getOrNull(0).orEmpty()
                    val ifsc = parts.getOrNull(1).orEmpty().uppercase()
                    when {
                        number.length < 9 || !number.all { it.isDigit() } ->
                            PreflightRow(number, ifsc, "INVALID", "account number must be 9–18 digits")
                        !Banks.isValidIfsc(ifsc) ->
                            PreflightRow(number, ifsc, "INVALID", "IFSC must be 4 letters, a 0, then 6 characters")
                        else -> PreflightRow(number, ifsc, "READY", "looks valid — will be checked against the registry")
                    }
                }
            }
            _rows.value = parsed.map { row ->
                if (row.status != "READY") row
                else {
                    val seen = container.registry.byHash(Ids.sha256("${row.number}|${row.ifsc}"))
                    if (seen != null) PreflightRow(row.number, row.ifsc, "DUPLICATE", "already ${seen.status.lowercase()} — one account, one paisa, ever")
                    else row
                }
            }
        }
    }

    fun addSingle(number: String, ifsc: String) {
        ingest("$number $ifsc")
    }

    fun run() {
        val ready = _rows.value.filter { it.status == "READY" }
        if (ready.isEmpty()) {
            _error.value = "nothing ready in the queue — paste account numbers, one per line"
            return
        }
        val target = Banks.byCode(bankCode.value)
        if (target == null) {
            _error.value = "pick the bank you are harvesting from"
            return
        }
        val now = settings.value
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val keys = ready.mapNotNull { row ->
                    val id = container.registry.enqueue(row.number, row.ifsc, target.code) ?: return@mapNotNull null
                    id to (row.number to row.ifsc)
                }
                if (keys.isEmpty()) {
                    _error.value = "every account was already in the registry"
                    return@launch
                }
                val id = container.engine.createTransferRun(target, 1, RunMode.HARVEST, now.stealthLevel)
                _runId.value = id
                container.engine.performHarvestRun(id, now, target.code, keys)
            } catch (failure: Exception) {
                _error.value = failure.message ?: "harvest could not start"
            } finally {
                _busy.value = false
            }
        }
    }

    fun resumeInterrupted() {
        val target = interrupted.value ?: return
        val now = settings.value
        _busy.value = true
        viewModelScope.launch {
            try {
                _runId.value = target.id
                container.engine.resumeHarvest(target.id, now)
            } catch (failure: Exception) {
                _error.value = failure.message ?: "resume failed"
            } finally {
                _busy.value = false
            }
        }
    }

    fun cancel() {
        val id = _runId.value ?: return
        viewModelScope.launch { container.engine.cancel(id) }
    }

    fun retryLeg(leg: HarvestLegEntity) {
        val bank = Banks.byCode(run.value?.bankCode ?: bankCode.value) ?: return
        val now = settings.value
        _busy.value = true
        viewModelScope.launch {
            try {
                container.registry.forceReset(leg.registryId)
                val id = container.engine.createTransferRun(bank, 1, RunMode.HARVEST, now.stealthLevel)
                _runId.value = id
                container.engine.performHarvestRun(
                    id, now, bank.code, listOf(leg.registryId to (leg.accountNumber to leg.ifsc))
                )
            } catch (failure: Exception) {
                _error.value = failure.message ?: "retry failed"
            } finally {
                _busy.value = false
            }
        }
    }

    fun exportCsv(onReady: (android.net.Uri) -> Unit) {
        val id = _runId.value ?: return
        viewModelScope.launch {
            val current = legs.value
            val rows = current.map { "${it.ts},${it.accountNumber},${it.ifsc},${it.amountPaise},${it.status},${it.utr.orEmpty()},${it.reason.orEmpty()}" }
            val uri = container.exporter.writeCsv(
                "ghostbank-harvest-$id.csv",
                "timestamp,account,ifsc,paise,status,utr,reason",
                rows
            )
            onReady(uri)
        }
    }

    fun clearError() {
        _error.value = null
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { HarvestViewModel(container) }
        }
    }
}
