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
import com.arin.ghostbank.data.net.ProxyEngine
import com.arin.ghostbank.model.Bank
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.REMITTER_PROFILES
import com.arin.ghostbank.model.RunMode
import com.arin.ghostbank.model.RunStatus
import com.arin.ghostbank.model.StealthLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

@OptIn(ExperimentalCoroutinesApi::class)
class TransferViewModel(private val container: AppContainer, bankCode: String) : ViewModel() {

    val bank: Bank? = Banks.byCode(bankCode)

    val destination = container.accounts.destination()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<Settings> = container.settings.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    private val _mode = MutableStateFlow(RunMode.HARVEST)
    val mode = _mode.asStateFlow()

    private val _amountText = MutableStateFlow("")
    val amountText = _amountText.asStateFlow()

    private val _sourceIndex = MutableStateFlow(0)
    val sourceIndex = _sourceIndex.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val runId = MutableStateFlow<Long?>(null)

    val run: StateFlow<RunEntity?> = runId
        .flatMapLatest { id -> if (id == null) flowOf(null) else container.runs.byId(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val stages: StateFlow<List<StageLogEntity>> = runId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else container.runs.logs(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val score: StateFlow<Int> = runId
        .flatMapLatest { id -> if (id == null) flowOf(100) else container.db.scores().byRun(id) }
        .let { flow -> flow }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 100)

    private var job: Job? = null

    fun setMode(value: RunMode) {
        _mode.value = value
        _error.value = null
    }

    fun setAmount(value: String) {
        _amountText.value = value.filter { it.isDigit() || it == '.' }.take(12)
        _error.value = null
    }

    fun setSource(index: Int) {
        _sourceIndex.value = index
    }

    fun amountPaise(): Long {
        if (_mode.value == RunMode.HARVEST) return 1L
        val parsed = _amountText.value.trim().toDoubleOrNull() ?: return 0L
        return (parsed * 100).roundToLong()
    }

    fun start() = viewModelScope.launch {
        val bankNow = bank ?: return@launch
        val destinationNow = container.accounts.current()
        if (destinationNow == null) {
            _error.value = "save your destination account first"
            return@launch
        }
        val paise = amountPaise()
        if (paise < 100) {
            _error.value = "the rail floor is ₹1 — enter at least 1 rupee"
            return@launch
        }
        if (paise > bankNow.capPaise) {
            _error.value = "${bankNow.name} caps a single transaction at ₹${bankNow.capPaise / 100}"
            return@launch
        }
        val settingsNow = container.settings.flow.first()
        if (settingsNow.stealthLevel == StealthLevel.GHOST) {
            val exitOk = runCatching { container.proxyEngine.pick(settingsNow) }.getOrNull() != null
            if (!exitOk) {
                _error.value = "Ghost level is on but no exit is configured — add a proxy or enable Tor in Settings"
                return@launch
            }
            if (LeakState.passed == false) {
                _error.value = "the last leak lab check failed — run it again from Shield before sending at Ghost level"
                return@launch
            }
        }
        val source = REMITTER_PROFILES.getOrNull(_sourceIndex.value) ?: REMITTER_PROFILES.first()
        val id = container.engine.createTransferRun(bankNow, paise, _mode.value, settingsNow.stealthLevel)
        runId.value = id
        _busy.value = true
        job = viewModelScope.launch {
            runCatching { container.engine.performTransferRun(id, settingsNow, source) }
            _busy.value = false
        }
    }

    fun retry() = viewModelScope.launch {
        val id = runId.value ?: return@launch
        val current = container.runs.byIdOnce(id) ?: return@launch
        if (current.status == RunStatus.RUNNING.name) return@launch
        val source = REMITTER_PROFILES.getOrNull(_sourceIndex.value) ?: REMITTER_PROFILES.first()
        val settingsNow = container.settings.flow.first()
        container.engine.resume(id)
        _busy.value = true
        job = viewModelScope.launch {
            runCatching { container.engine.retryTransfer(id, settingsNow, source) }
            _busy.value = false
        }
    }

    fun cancel() = viewModelScope.launch {
        val id = runId.value ?: return@launch
        container.engine.cancel(id)
        job?.cancel()
        _busy.value = false
    }

    companion object {
        fun factory(container: AppContainer, bankCode: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { TransferViewModel(container, bankCode) }
        }
    }
}
