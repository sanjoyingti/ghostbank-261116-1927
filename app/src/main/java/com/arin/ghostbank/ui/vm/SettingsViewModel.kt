=== ABOUT: Fixed the settings screen's brain file, which was cut off mid-line so the whole app could not compile ===
package com.arin.ghostbank.ui.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.GhostBankApp
import com.arin.ghostbank.data.Settings
import com.arin.ghostbank.data.db.AccountEntity
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

    /** Flips to true as soon as the stored settings have actually been read. */
    val booted: StateFlow<Boolean> = container.settings.flow
        .map { true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val destination: StateFlow<AccountEntity?> = container.accounts.destination()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val proxies: StateFlow<List<ProxyEntity>> = container.db.proxies().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val registryCount: StateFlow<Int> = container.registry.count()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _torRunning = MutableStateFlow<Boolean?>(null)
    val torRunning: StateFlow<Boolean?> = _torRunning.asStateFlow()

    fun clearMessage() { _message.value = null }

    fun setLevel(level: StealthLevel) = viewModelScope.launch { container.settings.setStealthLevel(level) }
    fun setTor(on: Boolean) = viewModelScope.launch { container.settings.setTorMode(on) }
    fun setDoh(resolver: String) = viewModelScope.launch { container.settings.setDoh(resolver.trim()) }
    fun setDiurnal(on: Boolean) = viewModelScope.launch { container.settings.setDiurnal(on) }
    fun setRpm(value: Int) = viewModelScope.launch { container.settings.setRpm(value) }
    fun setAph(value: Int) = viewModelScope.launch { container.settings.setAph(value) }
    fun setBudget(value: Int) = viewModelScope.launch { container.settings.setBudget(value) }
    fun setRetries(value: Int) = viewModelScope.launch { container.settings.setRetries(value) }
    fun setBackoff(value: Long) = viewModelScope.launch { container.settings.setBackoff(value) }
    fun setVa(on: Boolean) = viewModelScope.launch { container.settings.setVaRotation(on) }
    fun setFrag(min: Int, max: Int) = viewModelScope.launch {
        container.settings.setFragMin(min)
        container.settings.setFragMax(max.coerceAtLeast(min))
    }

    fun setBackground(context: Context, on: Boolean) = viewModelScope.launch {
        container.settings.setBackground(on)
        val app = context.applicationContext as? GhostBankApp
        if (on) app?.scheduleBackground() else app?.cancelBackground()
    }

    fun setDailyCap(value: Int) = viewModelScope.launch { container.settings.setDailyCap(value) }

    fun replayOnboarding() = viewModelScope.launch { container.settings.setOnboardingDone(false) }

    fun importProxies(text: String) = viewModelScope.launch {
        val added = container.proxyEngine.importList(text)
        _message.value = if (added == 0) "no usable proxy lines found" else "$added exit(s) imported"
    }

    fun deleteProxy(id: Long) = viewModelScope.launch { container.db.proxies().delete(id) }

    fun wipeProxies() = viewModelScope.launch {
        container.db.proxies().wipe()
        _message.value = "proxy list wiped"
    }

    fun wipeRegistry() = viewModelScope.launch {
        container.registry.wipe()
        _message.value = "dedupe registry wiped — every account can be harvested again"
    }

    fun wipeLedger() = viewModelScope.launch {
        container.ledger.wipe()
        container.db.stages().wipe()
        _message.value = "ledger and raw console wiped"
    }

    fun exportAll() = viewModelScope.launch {
        val ledger = container.ledger.allOnce()
        val rows = ledger.map { row ->
            listOf(
                row.runId.toString(), row.bankCode, row.direction, row.amountPaise.toString(),
                row.utr ?: "", row.status, row.badge, row.ts.toString(), row.counterparty ?: ""
            ).joinToString(",")
        }
        val uri = container.exporter.dump(
            "ghostbank_ledger_${System.currentTimeMillis()}.csv",
            buildString {
                appendLine("runId,bank,direction,amountPaise,utr,status,badge,ts,counterparty")
                rows.forEach { appendLine(it) }
            }
        )
        container.exporter.share(uri, "Export GhostBank ledger")
    }

    /** Actually tries to open a socket to Orbot's SOCKS port instead of guessing. */
    fun checkTor() = viewModelScope.launch {
        val running = withContext(Dispatchers.IO) {
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", 9050), 1_500)
                    true
                }
            }.getOrDefault(false)
        }
        _torRunning.value = running
        _message.value = if (running) "Tor is answering on 127.0.0.1:9050" else "Tor is not running — start Orbot first"
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    }
}
