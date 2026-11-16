package com.arin.ghostbank.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.db.HarvestLegEntity
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.HarvestStatus
import com.arin.ghostbank.model.Ids
import com.arin.ghostbank.model.RunMode
import com.arin.ghostbank.model.RunStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PreflightRow(
    val number: String,
    val ifsc: String,
    val problem: String?,
    val known: Boolean
) {
    val key: String get() = "$number|$ifsc"
    val usable: Boolean get() = problem == null && !known
}

data class HarvestState(
    val bankCode: String = "SBI",
    val pasteText: String = "",
    val singleNumber: String = "",
    val singleIfsc: String = "",
    val rows: List<PreflightRow> = emptyList(),
    val error: String? = null,
    val activeRunId: Long? = null,
    val running: Boolean = false,
    val pauseDialog: Boolean = false,
    val pauseReason: String? = null
)

class HarvestViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(HarvestState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.map { it.activeRunId }.distinctUntilChanged().flatMapLatest { id ->
                if (id == null) flowOf(null) else container.runs.byId(id)
            }.collect { run ->
                _state.update {
                    it.copy(
                        running = run?.status == RunStatus.RUNNING.name,
                        pauseDialog = run?.status == RunStatus.PAUSED.name,
                        pauseReason = if (run?.status == RunStatus.PAUSED.name) run.errorText else it.pauseReason
                    )
                }
            }
        }
    }

    fun setBank(code: String) = _state.update { it.copy(bankCode = code) }
    fun setPaste(text: String) = _state.update { it.copy(pasteText = text) }
    fun setSingleNumber(text: String) = _state.update { it.copy(singleNumber = text.filter { c -> c.isDigit() }.take(18)) }
    fun setSingleIfsc(text: String) = _state.update { it.copy(singleIfsc = text.uppercase().filter { c -> c.isLetterOrDigit() }.take(11)) }
    fun clearError() = _state.update { it.copy(error = null) }
    fun dismissPause() = _state.update { it.copy(pauseDialog = false) }

    fun addPasted() {
        val raw = _state.value.pasteText
        if (raw.isBlank()) {
            _state.update { it.copy(error = "Paste at least one line: account then IFSC") }
            return
        }
        viewModelScope.launch {
            val parsed = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
                val parts = line.split(",", " ", "\t", ";", "|").filter { it.isNotBlank() }
                val number = parts.getOrNull(0)?.filter { c -> c.isDigit() }.orEmpty()
                val ifsc = parts.getOrNull(1)?.uppercase()?.filter { c -> c.isLetterOrDigit() }.orEmpty()
                val problem = when {
                    parts.size < 2 -> "no IFSC on this line"
                    number.length < 9 -> "account number too short"
                    !Banks.isValidIfsc(ifsc) -> "IFSC shape is wrong"
                    else -> null
                }
                val hash = Ids.sha256("$number|$ifsc")
                val known = problem == null && container.registry.byHash(hash) != null
                PreflightRow(number = number, ifsc = ifsc, problem = problem, known = known)
            }
            _state.update { current ->
                val existing = current.rows.map { it.key }.toSet()
                val merged = current.rows + parsed.filter { !existing.contains(it.key) }
                current.copy(rows = merged, pasteText = "", error = null)
            }
        }
    }

    fun addSingle() {
        val number = _state.value.singleNumber
        val ifsc = _state.value.singleIfsc
        val problem = when {
            number.length < 9 -> "account number too short"
            !Banks.isValidIfsc(ifsc) -> "IFSC shape is wrong"
            else -> null
        }
        viewModelScope.launch {
            val hash = Ids.sha256("$number|$ifsc")
            val known = problem == null && container.registry.byHash(hash) != null
            val row = PreflightRow(number = number, ifsc = ifsc, problem = problem, known = known)
            _state.update { current ->
                if (current.rows.any { it.key == row.key }) current
                else current.copy(rows = current.rows + row, singleNumber = "", singleIfsc = "", error = null)
            }
        }
    }

    fun removeRow(key: String) = _state.update { current ->
        current.copy(rows = current.rows.filter { it.key != key })
    }

    fun clearRows() = _state.update { it.copy(rows = emptyList()) }

    fun start() {
        val usable = _state.value.rows.filter { it.usable }
        if (usable.isEmpty()) {
            _state.update { it.copy(error = "Nothing new in the queue — every account is either invalid or already harvested") }
            return
        }
        viewModelScope.launch {
            val settings = container.settings.flow.first()
            val claimed = mutableListOf<Pair<Long, Pair<String, String>>>()
            usable.forEach { row ->
                val id = container.registry.enqueue(row.number, row.ifsc, _state.value.bankCode)
                if (id != null) claimed += id to (row.number to row.ifsc)
            }
            if (claimed.isEmpty()) {
                _state.update { it.copy(error = "Every account in the queue is already in the registry") }
                return@launch
            }
            val runId = container.engine.createTransferRun(
                Banks.byCode(_state.value.bankCode) ?: Banks.all.first(),
                1L,
                RunMode.HARVEST,
                settings.stealthLevel
            )
            _state.update { it.copy(activeRunId = runId, running = true, error = null) }
            container.engine.performHarvestRun(runId, settings, _state.value.bankCode, claimed)
        }
    }

    fun resume() {
        val runId = _state.value.activeRunId ?: return
        viewModelScope.launch {
            val settings = container.settings.flow.first()
            container.engine.resume(runId)
            _state.update { it.copy(pauseDialog = false, running = true, error = null) }
            container.engine.resumeHarvest(runId, settings)
        }
    }

    fun cancel() {
        val runId = _state.value.activeRunId ?: return
        viewModelScope.launch {
            container.engine.cancel(runId)
            _state.update { it.copy(running = false) }
        }
    }

    fun retryLeg(leg: HarvestLegEntity) {
        viewModelScope.launch {
            val settings = container.settings.flow.first()
            container.registry.forceReset(leg.registryId)
            val bank = Banks.byCode(_state.value.bankCode) ?: Banks.all.first()
            val runId = container.engine.createTransferRun(bank, 1L, RunMode.HARVEST, settings.stealthLevel)
            _state.update { it.copy(activeRunId = runId, running = true) }
            container.engine.performHarvestRun(runId, settings, bank.code, listOf(leg.registryId to (leg.accountNumber to leg.ifsc)))
        }
    }

    fun export(legs: List<HarvestLegEntity>, totalPaise: Long) = viewModelScope.launch {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val rows = legs.map {
            "${it.ts},${it.accountNumber},${it.ifsc},${it.amountPaise},${it.status},${it.utr.orEmpty()},${(it.reason ?: "").replace(",", ";")}"
        } + "TOTAL,,,,$totalPaise,,"
        val uri = container.exporter.writeCsv(
            "ghostbank_harvest_$stamp.csv",
            "timestamp,account,ifsc,amount_paise,status,utr,reason",
            rows
        )
        container.exporter.share(uri, "GhostBank harvest")
    }

    fun summaryOf(legs: List<HarvestLegEntity>): String {
        val sent = legs.count { it.status != HarvestStatus.PENDING.name }
        val settled = legs.count { it.status == HarvestStatus.SETTLED.name }
        val floor = legs.count { it.status == HarvestStatus.FLOOR.name }
        val rejected = legs.count { it.status == HarvestStatus.REJECTED.name }
        return "sent $sent / settled $settled / floor $floor / rejected $rejected"
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { HarvestViewModel(container) }
        }
    }
}
