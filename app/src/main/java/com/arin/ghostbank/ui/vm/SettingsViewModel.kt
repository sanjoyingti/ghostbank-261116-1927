package com.arin.ghostbank.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.Settings
import com.arin.ghostbank.model.StealthLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<Settings> = container.settings.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _booted = MutableStateFlow(false)
    val booted: StateFlow<Boolean> = _booted.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            container.settings.flow.first()
            _booted.value = true
        }
    }

    fun setLevel(level: StealthLevel) = viewModelScope.launch { container.settings.setStealthLevel(level) }
    fun setTor(on: Boolean) = viewModelScope.launch { container.settings.setTorMode(on) }
    fun setDoh(value: String) = viewModelScope.launch { container.settings.setDoh(value.ifBlank { "https://cloudflare-dns.com/dns-query" }) }
    fun setDiurnal(on: Boolean) = viewModelScope.launch { container.settings.setDiurnal(on) }
    fun setRpm(value: String) = value.toIntOrNull()?.let { v -> viewModelScope.launch { container.settings.setRpm(v) } }
    fun setAph(value: String) = value.toIntOrNull()?.let { v -> viewModelScope.launch { container.settings.setAph(v) } }
    fun setBudget(value: String) = value.toIntOrNull()?.let { v -> viewModelScope.launch { container.settings.setBudget(v) } }
    fun setRetries(value: String) = value.toIntOrNull()?.let { v -> viewModelScope.launch { container.settings.setRetries(v) } }
    fun setBackoff(value: String) = value.toLongOrNull()?.let { v -> viewModelScope.launch { container.settings.setBackoff(v) } }
    fun setDailyCap(value: String) = value.toIntOrNull()?.let { v -> viewModelScope.launch { container.settings.setDailyCap(v) } }
    fun setVa(on: Boolean) = viewModelScope.launch { container.settings.setVaRotation(on) }
    fun setFragMin(value: String) = value.toIntOrNull()?.let { v -> viewModelScope.launch { container.settings.setFragMin(v) } }
    fun setFragMax(value: String) = value.toIntOrNull()?.let { v -> viewModelScope.launch { container.settings.setFragMax(v) } }

    fun setBackground(on: Boolean) {
        viewModelScope.launch {
            container.settings.setBackground(on)
            val app = container.context as? com.arin.ghostbank.GhostBankApp
            if (on) app?.scheduleBackground() else app?.cancelBackground()
        }
    }

    fun replayOnboarding() = viewModelScope.launch { container.settings.setOnboardingDone(false) }

    fun importProxies(text: String) {
        viewModelScope.launch {
            val added = container.proxyEngine.importList(text)
            _message.value = if (added > 0) "$added exit(s) added" else "nothing usable in that text"
        }
    }

    fun deleteProxy(id: Long) = viewModelScope.launch { container.db.proxies().delete(id) }

    fun wipeRegistry() = viewModelScope.launch {
        container.registry.wipe()
        _message.value = "registry wiped — every account can be harvested again"
    }

    fun wipeLedger() = viewModelScope.launch {
        container.ledger.wipe()
        container.db.stages().wipe()
        _message.value = "ledger and stage logs wiped"
    }

    fun exportAll() = viewModelScope.launch {
        val rows = container.ledger.allOnce()
        val csv = rows.map {
            "${it.ts},${it.bankCode},${it.direction},${it.amountPaise},${it.utr.orEmpty()},${it.status},${it.badge}"
        }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val uri = container.exporter.writeCsv(
            "ghostbank_export_$stamp.csv",
            "timestamp,bank,direction,amount_paise,utr,status,badge",
            csv
        )
        container.exporter.share(uri, "GhostBank export")
    }

    fun clearMessage() {
        _message.value = null
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    }
}
