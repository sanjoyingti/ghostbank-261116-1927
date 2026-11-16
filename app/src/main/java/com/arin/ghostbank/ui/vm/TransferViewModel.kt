package com.arin.ghostbank.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.Ids
import com.arin.ghostbank.model.REMITTER_PROFILES
import com.arin.ghostbank.model.RunMode
import com.arin.ghostbank.model.RunStatus
import com.arin.ghostbank.model.StealthLevel
import com.arin.ghostbank.ui.components.parseRupeesToPaise
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TransferState(
    val amountText: String = "",
    val sourceIndex: Int = 0,
    val error: String? = null,
    val activeRunId: Long? = null,
    val running: Boolean = false,
    val expanded: Set<Long> = emptySet(),
    val destinationMissing: Boolean = false,
    val destinationLabel: String? = null,
    val queuedOffline: Boolean = false
)

class TransferViewModel(private val container: AppContainer, val bankCode: String) : ViewModel() {

    private val _state = MutableStateFlow(TransferState())
    val state = _state.asStateFlow()

    val bank = Banks.byCode(bankCode)

    init {
        viewModelScope.launch {
            container.accounts.destination().collect { destination ->
                _state.update {
                    it.copy(
                        destinationMissing = destination == null,
                        destinationLabel = destination?.let { d -> "to " + Ids.maskNumber(d.number) + " · " + d.ifsc }
                    )
                }
            }
        }
        viewModelScope.launch {
            _state.map { it.activeRunId }.distinctUntilChanged().flatMapLatest { id ->
                if (id == null) flowOf(null) else container.runs.byId(id)
            }.collect { run ->
                _state.update {
                    it.copy(
                        running = run?.status == RunStatus.RUNNING.name,
                        queuedOffline = run?.badge == "SIMULATED" && run?.status == RunStatus.QUEUED.name
                    )
                }
            }
        }
    }

    fun setAmount(value: String) = _state.update {
        it.copy(amountText = value.filter { c -> c.isDigit() || c == '.' }.take(12), error = null)
    }

    fun setSource(index: Int) = _state.update { it.copy(sourceIndex = index) }
    fun toggleExpanded(id: Long) = _state.update {
        it.copy(expanded = if (it.expanded.contains(id)) it.expanded - id else it.expanded + id)
    }

    fun fillHarvestFloor() = _state.update { it.copy(amountText = "0.01") }

    private fun validate(amountPaise: Long, capPaise: Long): String? = when {
        amountPaise < 100 -> "Minimum on this rail is ₹1 (₹0.01 gets floored to ₹1)"
        amountPaise > capPaise -> "Above this bank's per-transaction cap"
        else -> null
    }

    fun start() {
        val bankValue = bank ?: run {
            _state.update { it.copy(error = "Unknown bank") }
            return
        }
        val paise = parseRupeesToPaise(_state.value.amountText)
        if (paise == null) {
            _state.update { it.copy(error = "Enter an amount first") }
            return
        }
        val problem = validate(paise, bankValue.capPaise)
        if (problem != null) {
            _state.update { it.copy(error = problem) }
            return
        }
        if (_state.value.destinationMissing) {
            _state.update { it.copy(error = "Save your destination account first") }
            return
        }
        viewModelScope.launch {
            val settings = container.settings.flow.first()
            if (settings.stealthLevel == StealthLevel.GHOST) {
                val exits = container.db.proxies().healthy()
                if (exits.isEmpty() && !settings.torMode) {
                    _state.update { it.copy(error = "Ghost level is on but no exit is configured — add a proxy or switch Tor on") }
                    return@launch
                }
            }
            val runId = container.engine.createTransferRun(bankValue, paise, RunMode.TRANSFER, settings.stealthLevel)
            _state.update { it.copy(activeRunId = runId, running = true, error = null) }
            container.engine.performTransferRun(runId, settings, REMITTER_PROFILES[_state.value.sourceIndex])
        }
    }

    fun retry() {
        val runId = _state.value.activeRunId ?: return start()
        viewModelScope.launch {
            val settings = container.settings.flow.first()
            _state.update { it.copy(running = true, error = null) }
            container.engine.retryTransfer(runId, settings, REMITTER_PROFILES[_state.value.sourceIndex])
        }
    }

    fun cancel() {
        val runId = _state.value.activeRunId ?: return
        viewModelScope.launch {
            container.engine.cancel(runId)
            _state.update { it.copy(running = false) }
        }
    }

    companion object {
        fun factory(container: AppContainer, bankCode: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { TransferViewModel(container, bankCode) }
        }
    }
}
