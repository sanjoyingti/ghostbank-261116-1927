=== ABOUT: Filters the raw request and response console by stage and status code ===
package com.arin.ghostbank.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.db.StageLogEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class ConsoleViewModel(private val container: AppContainer, private val runId: Long) : ViewModel() {

    private val _stage = MutableStateFlow("")
    val stage: StateFlow<String> = _stage.asStateFlow()

    private val _status = MutableStateFlow(0)
    val status: StateFlow<Int> = _status.asStateFlow()

    val logs: StateFlow<List<StageLogEntity>> = combine(_stage, _status) { stage, status -> stage to status }
        .flatMapLatest { (stage, status) -> container.runs.filtered(runId, stage, status) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setStage(value: String) { _stage.value = value }
    fun setStatus(value: Int) { _status.value = value }

    companion object {
        fun factory(container: AppContainer, runId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { ConsoleViewModel(container, runId) }
        }
    }
}
