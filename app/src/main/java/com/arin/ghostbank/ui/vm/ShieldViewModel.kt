=== ABOUT: Runs the leak lab, holds the newest stealth score and lists runs for the shield screen ===
package com.arin.ghostbank.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.db.RunEntity
import com.arin.ghostbank.data.db.StealthScoreEntity
import com.arin.ghostbank.data.net.LeakReport
import com.arin.ghostbank.model.StealthLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShieldViewModel(private val container: AppContainer) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _report = MutableStateFlow<LeakReport?>(null)
    val report: StateFlow<LeakReport?> = _report.asStateFlow()

    val runs: StateFlow<List<RunEntity>> = container.runs.recent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val scores: StateFlow<List<StealthScoreEntity>> = container.db.scores().recent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun probe() = viewModelScope.launch {
        if (_busy.value) return@launch
        _busy.value = true
        val settings = container.settings.flow.first()
        val report = container.leakLab.probe(settings)
        _report.value = report
        container.settings.setLastExit(report.ip, report.country)
        _busy.value = false
    }

    fun ghostBlocked(): Boolean {
        val report = _report.value ?: return false
        return !report.passed
    }

    fun levelOf(settings: StealthLevel): String = settings.name

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ShieldViewModel(container) }
        }
    }
}
