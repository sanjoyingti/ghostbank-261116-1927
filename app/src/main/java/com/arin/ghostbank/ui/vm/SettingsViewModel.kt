package com.arin.ghostbank.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.GhostBankApp
import com.arin.ghostbank.data.Settings
import com.arin.ghostbank.data.db.ProxyEntity
import com.arin.ghostbank.model.StealthLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<Settings> = container.settings.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    val booted: StateFlow<Boolean> = container.settings.flow
        .map { true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val proxies: StateFlow<List<ProxyEntity>> = container.db.proxies().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun dismissMessage() { _message.value = null }

    fun setLevel(level: StealthLevel) = viewModelScope.launch {
        container.settings.setStealthLevel(level)
        if (level == StealthLevel.GHOST && !settings.value.torMode && proxies.value.isEmpty()) {
            _message.value = "Ghost level needs an exit — add a proxy or switch Tor mode on, otherwise no traffic will leave."
        }
    }

    fun setTorMode(on: Boolean) = viewModelScope.launch {
        container.settings.setTorMode(on)
        if (on) _message.value = "Tor mode: GhostBank will dial SOCKS5 127.0.0.1:9050 (Orbot)."
    }

    fun setDoh(resolver: String) = viewModelScope.launch { container.settings.setDoh(resolver.ifBlank { "https://cloudflare-dns.com/dns-query" }) }
    fun setDiurnal(on: Boolean) = viewModelScope.launch { container.settings.setDiurnal(on) }
    fun setRpm(value: Int) = viewModelScope.launch { container.settings.setRpm(value) }
    fun setAph(value: Int) = viewModelScope.launch { container.settings.setAph(value) }
    fun setBudget(value: Int) = viewModelScope.launch { container.settings.setBudget(value) }
    fun setRetries(value: Int) = viewModelScope.launch { container.settings.setRetries(value) }
    fun setBackoff(value: Long) = viewModelScope.launch { container.settings.setBackoff(value) }
    fun setVaRotation(on: Boolean) = viewModelScope.launch { container.settings.setVaRotation(on) }
    fun setFragMin(value: Int) = viewModelScope.launch { container.settings.setFragMin(value) }
    fun setFragMax(value: Int) = viewModelScope.launch { container.settings.setFragMax(value) }
    fun setDailyCap(value: Int) = viewModelScope.launch { container.settings.setDailyCap(value) }

    fun setBackground(on: Boolean) = viewModelScope.launch {
        container.settings.setBackground(on)
        val app = container.context as? GhostBankApp
        if (on) app?.scheduleBackground() else app?.cancelBackground()
    }

    fun addProxies(text: String) = viewModelScope.launch {
        val added = container.proxyEngine.importList(text)
        _message.value = if (added > 0) "$added exit(s) added" else "Nothing recognisable in that text"
    }

    fun addSingleProxy(line: String) = addProxies(line)

    fun deleteProxy(id: Long) = viewModelScope.launch { container.db.proxies().delete(id) }

    fun wipeProxies() = viewModelScope.launch {
        container.db.proxies().wipe()
        _message.value = "Exit list wiped"
    }

    fun wipeRegistry() = viewModelScope.launch {
        container.registry.wipe()
        _message.value = "Registry wiped — every account can be harvested again"
    }

    fun wipeLedger() = viewModelScope.launch {
        container.ledger.wipe()
        container.db.stages().wipe()
        _message.value = "Ledger and stage logs wiped"
    }

    fun exportAll() = viewModelScope.launch {
        val rows = container.ledger.allOnce()
        if (rows.isEmpty()) {
            _message.value = "Nothing in the ledger to export"
            return@launch
        }
        val uri = withContext(Dispatchers.IO) {
            container.exporter.writeCsv(
                "ghostbank_all.csv",
                "date,run_id,bank,direction,paise,utr,status,badge,counterparty",
                rows.map { "${it.ts},${it.runId},${it.bankCode},${it.direction},${it.amountPaise},${it.utr ?: ""},${it.status},${it.badge},${it.counterparty ?: ""}" }
            )
        }
        container.exporter.share(uri, "Export everything")
    }

    fun replayOnboarding() = viewModelScope.launch { container.settings.setOnboardingDone(false) }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    }
}
