package com.arin.ghostbank.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.db.LedgerRowEntity
import com.arin.ghostbank.data.db.RunEntity
import com.arin.ghostbank.data.db.StageLogEntity
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

@OptIn(ExperimentalCoroutinesApi::class)
class RunViewModel(private val container: AppContainer, private val runId: Long) : ViewModel() {

    private val _stage = MutableStateFlow("")
    val stageFilter: StateFlow<String> = _stage.asStateFlow()

    private val _status = MutableStateFlow(0)
    val statusFilter: StateFlow<Int> = _status.asStateFlow()

    val run: StateFlow<RunEntity?> = container.db.runs().byId(runId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val logs: StateFlow<List<StageLogEntity>> = _stage
        .flatMapLatest { s -> container.db.stages().filtered(runId, s, _status.value) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val score: StateFlow<Int> = run.map { it?.stealthScore ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val ledgerRows: StateFlow<List<LedgerRowEntity>> = container.db.ledger().byRun(runId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setStage(value: String) {
        _stage.value = value
        refresh()
    }

    fun setStatus(value: Int) {
        _status.value = value
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            val list = container.db.stages().filtered(runId, _stage.value, _status.value)
            // the flatMap above re-collects; nothing else needed
            list.hashCode()
        }
    }

    companion object {
        fun factory(container: AppContainer, runId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { RunViewModel(container, runId) }
        }
    }
}
