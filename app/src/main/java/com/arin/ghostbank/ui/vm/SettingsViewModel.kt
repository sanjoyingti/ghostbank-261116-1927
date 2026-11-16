package com.arin.ghostbank.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
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
import java.net.InetSocketAddress
import java.net.Socket

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<Settings> = container.settings.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    /** Flips to true once the stored settings have actually been read from disk. */
    val booted: StateFlow<Boolean> = container.settings.flow
        .map { true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val proxies: StateFlow<List<ProxyEntity>> = container.db.proxies().all()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _torRunning = MutableStateFlow<Boolean?>(null)
    val torRunning = _torRunning.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    fun setLevel(level: StealthLevel) = launch { container.settings.setStealthLevel(level) }
    fun setTor(on: Boolean) = launch { container.settings.setTorMode(on) }
    fun setDoh(value: String) = launch { container.settings.setDoh(value) }
    fun setDiurnal(on: Boolean) = launch { container.settings.setDiurnal(on) }
    fun setRpm(value: Int) = launch { container.settings.setRpm(value) }
    fun setAph(value: Int) = launch { container.settings.setAph(value) }
    fun setBudget(value: Int) = launch { container.settings.setBudget(value) }
    fun setRetries(value: Int) = launch { container.settings.setRetries(value) }
    fun setBackoff(value: Long) = launch { container.settings.setBackoff(value) }
    fun setVa(on: Boolean) = launch { container.settings.setVaRotation(on) }
    fun setFragMin(value: Int) = launch { container.settings.setFragMin(value) }
    fun setFragMax(value: Int) = launch { container.settings.setFragMax(value) }
    fun setBackground(on: Boolean) = launch {
        container.settings.setBackground(on)
        val app = container.context as? com.arin.ghostbank.GhostBankApp
        withContext(Dispatchers.Main) {
            if (on) app?.scheduleBackground() else app?.cancelBackground()
        }
    }
    fun setDailyCap(value: Int) = launch { container.settings.setDailyCap(value) }
    fun setDestinationSaved(saved: Boolean) = launch { container.settings.setDestinationSaved(saved) }

    fun checkTor() {
        viewModelScope.launch {
            val running = withContext(Dispatchers.IO) {
                runCatching {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress("127.0.0.1", 9050), 1_200)
                        true
                    }
                }.getOrDefault(false)
            }
            _torRunning.value = running
            _message.value = if (running) "Orbot answers on 127.0.0.1:9050" else "Tor is not running — open Orbot first"
        }
    }

    fun addProxy(line: String) {
        if (line.isBlank()) return
        viewModelScope.launch {
            val added = container.proxyEngine.importList(line)
            _message.value = if (added > 0) "$added exit(s) added" else "That line was already in the pool"
        }
    }

    fun importProxies(text: String) {
        viewModelScope.launch {
            val added = container.proxyEngine.importList(text)
            _message.value = if (added > 0) "$added exit(s) imported" else "Nothing new in that list"
        }
    }

    fun deleteProxy(id: Long) = launch { container.db.proxies().delete(id) }

    fun wipeRegistry() = launch {
        container.registry.wipe()
        _message.value = "Dedupe registry wiped — every account can be harvested again"
    }

    fun wipeLedger() = launch {
        container.ledger.wipe()
        _message.value = "Ledger wiped"
    }

    fun exportAll(onReady: (android.net.Uri) -> Unit) {
        viewModelScope.launch {
            val ledger = container.ledger.allOnce()
            val registry = container.registry.unfinished()
            val rows = ledger.map { row ->
                listOf(
                    row.ts.toString(), row.bankCode, row.direction, row.amountPaise.toString(),
                    row.utr ?: "", row.status, row.badge, row.counterparty ?: ""
                ).joinToString(",")
            } + registry.map { entry ->
                listOf(
                    entry.lastAttempt.toString(), entry.bankCode, "REGISTRY", entry.amountPaise.toString(),
                    entry.utr ?: "", entry.status, entry.forced.toString(), entry.accountNumber
                ).joinToString(",")
            }
            val uri = container.exporter.writeCsv("ghostbank_all.csv", "ts,bank,kind,paise,utr,status,extra,ref", rows)
            onReady(uri)
        }
    }

    fun consumeMessage() { _message.value = null }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    }
}
