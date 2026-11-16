package com.arin.ghostbank.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.Settings
import com.arin.ghostbank.data.db.RunEntity
import com.arin.ghostbank.data.db.StageLogEntity
import com.arin.ghostbank.data.net.LeakState
import com.arin.ghostbank.model.Bank
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.ErrorKind
import com.arin.ghostbank.model.GhostError
import com.arin.ghostbank.model.Money
import com.arin.ghostbank.model.REMITTER_PROFILES
import com.arin.ghostbank.model.RemitterProfile
import com.arin.ghostbank.model.RunMode
import com.arin.ghostbank.model.StealthLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TransferViewModel(private val container: AppContainer, val bankCode: String) : ViewModel() {

    val bank: Bank? = Banks.byCode(bankCode)

    private val _amount = MutableStateFlow("")
    val amount: StateFlow<String> = _amount.asStateFlow()

    private val _sourceIndex = MutableStateFlow(0)
    val sourceIndex: StateFlow<Int> = _sourceIndex.asStateFlow()

    private val _runId = MutableStateFlow<Long?>(null)
    val runId: StateFlow<Long?> = _runId.asStateFlow()

    val logs: StateFlow<List<StageLogEntity>> = _runId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else container.runs.logs(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val run: StateFlow<RunEntity?> = _runId
        .flatMapLatest { id -> if (id == null) flowOf(null) else container.runs.byId(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<Settings> = container.settings.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _error = MutableStateFlow<GhostError?>(null)
    val error: StateFlow<GhostError?> = _error.asStateFlow()

    private val _showConfirm = MutableStateFlow(false)
    val showConfirm: StateFlow<Boolean> = _showConfirm.asStateFlow()

    private val _missingDestination = MutableStateFlow(false)
    val missingDestination: StateFlow<Boolean> = _missingDestination.asStateFlow()

    private val _budgetLeft = MutableStateFlow(0)
    val budgetLeft: StateFlow<Int> = _budgetLeft.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setAmount(value: String) {
        val filtered = value.filter { it.isDigit() || it == '.' }.take(12)
        _amount.value = filtered
        _error.value = null
    }

    fun setSource(index: Int) { _sourceIndex.value = index }

    fun ask() {
        val b = bank ?: return
        val paise = Money.parseRupees(_amount.value)
        if (paise == null) {
            _error.value = GhostError(ErrorKind.CONFIG, "That amount is not a number.", "Type rupees, for example 250 or 250.50")
            return
        }
        if (paise < 100) {
            _error.value = GhostError(ErrorKind.RAIL_FLOOR, "IMPS needs at least ₹1.", "Raise the amount to ₹1 or more")
            return
        }
        if (paise > b.capPaise) {
            _error.value = GhostError(ErrorKind.CONFIG, "${b.name} caps a single transfer at ${Money.format(b.capPaise)}.", "Lower the amount")
            return
        }
        _showConfirm.value = true
    }

    fun dismissConfirm() { _showConfirm.value = false }

    fun sources(): List<RemitterProfile> {
        val b = bank ?: return REMITTER_PROFILES
        return REMITTER_PROFILES + RemitterProfile("${b.code} own P2A route", "ONUS-${b.code}", "ONUS0000001")
    }

    private fun selectedSource(): RemitterProfile = sources().getOrElse(_sourceIndex.value) { REMITTER_PROFILES.first() }

    fun run() {
        val b = bank ?: return
        val paise = Money.parseRupees(_amount.value) ?: return
        if (_running.value) return
        viewModelScope.launch {
            _running.value = true
            _error.value = null
            _missingDestination.value = false
            val current = settings.value
            _budgetLeft.value = current.entropyBudget
            if (container.accounts.current() == null) {
                _missingDestination.value = true
                _running.value = false
                return@launch
            }
            if (current.stealthLevel == StealthLevel.GHOST && LeakState.passed != true) {
                val report = container.leakLab.probe(current)
                if (!report.passed) {
                    _error.value = GhostError(
                        ErrorKind.CONFIG,
                        "Ghost level is on and the leak lab has not passed, so nothing was sent.",
                        "Run the leak lab from the Shield tab, fix the exit, then run again",
                        report.error
                    )
                    _running.value = false
                    return@launch
                }
            }
            val id = container.engine.createTransferRun(b, paise, RunMode.TRANSFER, current.stealthLevel)
            _runId.value = id
            container.engine.performTransferRun(id, current, selectedSource())
            _running.value = false
        }
    }

    fun retry() {
        val id = _runId.value ?: return
        if (_running.value) return
        viewModelScope.launch {
            _running.value = true
            _error.value = null
            container.engine.retryTransfer(id, settings.value, selectedSource())
            _running.value = false
        }
    }

    fun cancel() {
        val id = _runId.value ?: return
        viewModelScope.launch {
            container.engine.cancel(id)
            _running.value = false
            _message.value = "Run cancelled"
        }
    }

    fun dismissError() { _error.value = null }
    fun dismissMessage() { _message.value = null }

    companion object {
        fun factory(container: AppContainer, bankCode: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { TransferViewModel(container, bankCode) }
        }
    }
}
