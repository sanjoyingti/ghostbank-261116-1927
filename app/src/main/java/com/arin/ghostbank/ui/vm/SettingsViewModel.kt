package com.arin.ghostbank.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.Settings
import com.arin.ghostbank.model.Badge
import com.arin.ghostbank.model.StealthLevel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<Settings> =
        container.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    val booted: StateFlow<Boolean> = container.settings.flow
        .map { true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setLevel(level: StealthLevel) = viewModelScope.launch { container.settings.setStealthLevel(level) }
    fun setTor(on: Boolean) = viewModelScope.launch { container.settings.setTorMode(on) }
    fun setDoh(value: String) = viewModelScope.launch { container.settings.setDoh(value) }
    fun setDiurnal(on: Boolean) = viewModelScope.launch { container.settings.setDiurnal(on) }
    fun setRpm(value: Int) = viewModelScope.launch { container.settings.setRpm(value) }
    fun setAph(value: Int) = viewModelScope.launch { container.settings.setAph(value) }
    fun setBudget(value: Int) = viewModelScope.launch { container.settings.setBudget(value) }
    fun setRetries(value: Int) = viewModelScope.launch { container.settings.setRetries(value) }
    fun setBackoff(value: Long) = viewModelScope.launch { container.settings.setBackoff(value) }
    fun setVa(on: Boolean) = viewModelScope.launch { container.settings.setVaRotation(on) }
    fun setFragMin(value: Int) = viewModelScope.launch { container.settings.setFragMin(value) }
    fun setFragMax(value: Int) = viewModelScope.launch { container.settings.setFragMax(value) }
    fun setOnboardingDone(done: Boolean) = viewModelScope.launch { container.settings.setOnboardingDone(done) }
    fun setBackground(on: Boolean) = viewModelScope.launch { container.settings.setBackground(on) }
    fun setDailyCap(value: Int) = viewModelScope.launch { container.settings.setDailyCap(value) }

    fun importProxies(text: String, onResult: (Int) -> Unit) = viewModelScope.launch {
        onResult(container.proxyEngine.importList(text))
    }

    fun wipeRegistry() = viewModelScope.launch { container.registry.wipe() }
    fun wipeLedger() = viewModelScope.launch { container.ledger.wipe() }

    fun exportAll(onDone: (String) -> Unit) = viewModelScope.launch {
        val rows = container.ledger.allOnce().map { row ->
            listOf(
                row.ts.toString(), row.bankCode, row.direction, row.amountPaise.toString(),
                row.utr.orEmpty(), row.status, row.badge, row.counterparty.orEmpty()
            ).joinToString(",")
        }
        val uri = container.exporter.writeCsv(
            "ghostbank_all_${System.currentTimeMillis()}.csv",
            "epoch_ms,bank,direction,amount_paise,utr,status,badge,counterparty",
            rows
        )
        container.exporter.share(uri, "GhostBank export")
        onDone("exported ${rows.size} ledger rows")
    }

    /** True when the level needs an exit before any traffic may leave. */
    fun trafficBlocked(settings: Settings, healthyExits: Int): Boolean =
        settings.stealthLevel == StealthLevel.GHOST && !settings.torMode && healthyExits == 0

    fun badgeOf(name: String?): Badge = if (name == Badge.LIVE.name) Badge.LIVE else Badge.SIMULATED

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    }
}
