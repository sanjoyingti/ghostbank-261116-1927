package com.arin.ghostbank.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.Settings
import com.arin.ghostbank.data.db.RunEntity
import com.arin.ghostbank.data.db.StealthScoreEntity
import com.arin.ghostbank.data.net.LeakReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShieldViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<Settings> = container.settings.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    val runs: StateFlow<List<RunEntity>> = container.runs.recent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val scores: StateFlow<List<StealthScoreEntity>> = container.db.scores().recent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _report = MutableStateFlow<LeakReport?>(null)
    val report = _report.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    fun probe() {
        viewModelScope.launch {
            _busy.value = true
            _report.value = runCatching { container.leakLab.probe(settings.value) }.getOrNull()
            _busy.value = false
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { ShieldViewModel(container) }
        }
    }
}
