@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.arin.ghostbank.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.Settings
import com.arin.ghostbank.data.db.AccountEntity
import com.arin.ghostbank.data.db.RunEntity
import com.arin.ghostbank.data.db.StageLogEntity
import com.arin.ghostbank.data.db.StealthScoreEntity
import com.arin.ghostbank.data.net.LeakState
import com.arin.ghostbank.model.Bank
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.REMITTER_PROFILES
import com.arin.ghostbank.model.RunMode
import com.arin.ghostbank.model.StageKind
import com.arin.ghostbank.model.StealthLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransferViewModel(private val container: AppContainer, bankCode: String) : ViewModel() {

    val bank: Bank? = Banks.byCode(bankCode)
    val stages: List<StageKind> = StageKind.values().toList()

    private val _amount = MutableStateFlow("")
    val amount = _amount.asStateFlow()

    private val _source = MutableStateFlow(0)
    val source = _source.asStateFlow()

    private val _runId = MutableStateFlow<Long?>(null)
    val runId = _runId.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    val settings: StateFlow<Settings> = container.settings.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    val destination: StateFlow<AccountEntity?> = container.accounts.destination()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val run: StateFlow<RunEntity?> = _runId
        .flatMapLatest { id -> if (id == null) flowOf(null) else container.runs.byId(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val logs: StateFlow<List<StageLogEntity>> = _runId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else container.runs.logs(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val score: StateFlow<Int> = _runId
        .flatMapLatest { id ->
            if (id == null) flowOf(100)
            else container.db.scores().byRun(id).let { flow ->
                kotlinx.coroutines.flow.map(flow) { entity: StealthScoreEntity? -> entity?.score ?: 100 }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 100)

    fun setAmount(value: String) {
        _amount.value = value.filter { it.isDigit() }.take(9)
        _error.value = null
    }

    fun setSource(index: Int) {
        _source.value = index.coerceIn(0, REMITTER_PROFILES.lastIndex)
    }

    fun validationMessage(): String? {
        val target = bank ?: return "unknown bank"
        val rupees = _amount.value.toLongOrNull() ?: return "enter an amount in rupees"
        if (rupees < 1) return "the minimum is ₹1"
        if (rupees * 100 > target.capPaise) return "this bank caps a single transaction at ₹${target.capPaise / 100}"
        if (!target.impsSupported) return "this bank does not take IMPS"
        return null
    }

    fun run() {
        val target = bank ?: return
        val problem = validationMessage()
        if (problem != null) {
            _error.value = problem
            return
        }
        if (destination.value == null) {
            _error.value = "save your destination account before running"
            return
        }
        val now = settings.value
        if (now.stealthLevel == StealthLevel.GHOST && LeakState.passed != true) {
            _error.value = "Ghost level blocks traffic until the Leak Lab passes — run the shield check first"
            return
        }
        val rupees = _amount.value.toLongOrNull() ?: return
        val chosen = REMITTER_PROFILES[_source.value.coerceIn(0, REMITTER_PROFILES.lastIndex)]
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val id = _runId.value
                    ?: container.engine.createTransferRun(target, rupees * 100, RunMode.TRANSFER, now.stealthLevel)
                        .also { _runId.value = it }
                container.engine.performTransferRun(id, now, chosen)
            } catch (failure: Exception) {
                _error.value = failure.message ?: "the run could not start"
            } finally {
                _busy.value = false
            }
        }
    }

    fun retry() {
        val id = _runId.value
        if (id == null) {
            run()
            return
        }
        val chosen = REMITTER_PROFILES[_source.value.coerceIn(0, REMITTER_PROFILES.lastIndex)]
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                container.engine.retryTransfer(id, settings.value, chosen)
            } catch (failure: Exception) {
                _error.value = failure.message ?: "retry failed"
            } finally {
                _busy.value = false
            }
        }
    }

    fun cancel() {
        val id = _runId.value ?: return
        viewModelScope.launch { container.engine.cancel(id) }
    }

    companion object {
        fun factory(container: AppContainer, bankCode: String) = viewModelFactory {
            initializer { TransferViewModel(container, bankCode) }
        }
    }
}
