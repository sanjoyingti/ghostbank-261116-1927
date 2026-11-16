package com.arin.ghostbank.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.GhostBankApp
import com.arin.ghostbank.data.Settings
import com.arin.ghostbank.data.db.ProxyEntity
import com.arin.ghostbank.model.StealthLevel
import kotlinx.coroutines.flow.MutableStateFlow
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

    val proxies: StateFlow<List<ProxyEntity>> = container.db.proxies().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val message = MutableStateFlow<String?>(null)

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
    fun setDailyCap(value: Int) = viewModelScope.launch { container.settings.setDailyCap(value) }
    fun setOnboardingDone(done: Boolean) = viewModelScope.launch { container.settings.setOnboardingDone(done) }

    fun setFrag(min: Int, max: Int) = viewModelScope.launch {
        container.settings.setFragMin(min)
        container.settings.setFragMax(max.coerceAtLeast(min))
    }

    fun setBackground(on: Boolean) = viewModelScope.launch {
        container.settings.setBackground(on)
        val app = container.context as? GhostBankApp
        if (on) app?.scheduleBackground() else app?.cancelBackground()
    }

    fun importProxies(text: String) = viewModelScope.launch {
        val added = container.proxyEngine.importList(text)
        message.value = if (added > 0) "$added exit(s) imported" else "no usable exit found in that text"
    }

    fun deleteProxy(id: Long) = viewModelScope.launch { container.db.proxies().delete(id) }
    fun wipeProxies() = viewModelScope.launch { container.db.proxies().wipe() }
    fun wipeRegistry() = viewModelScope.launch {
        container.registry.wipe()
        message.value = "dedupe registry wiped — every account becomes harvestable again"
    }
    fun wipeLedger() = viewModelScope.launch {
        container.ledger.wipe()
        message.value = "ledger wiped"
    }

    fun exportAll() = viewModelScope.launch {
        val rows = container.ledger.allOnce().map {
            "${it.ts},${it.bankCode},${it.direction},${it.amountPaise},${it.utr.orEmpty()},${it.status},${it.badge}"
        }
        val uri = container.exporter.writeCsv(
            "ghostbank-export.csv",
            "timestamp,bank,direction,paise,utr,status,badge",
            rows
        )
        container.exporter.share(uri, "GhostBank export")
    }

    fun clearMessage() {
        message.value = null
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    }
}
