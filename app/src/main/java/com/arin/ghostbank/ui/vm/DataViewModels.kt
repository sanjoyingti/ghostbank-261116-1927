package com.arin.ghostbank.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.BranchInfo
import com.arin.ghostbank.data.BranchResolver
import com.arin.ghostbank.data.db.LedgerRowEntity
import com.arin.ghostbank.data.db.RegistryEntity
import com.arin.ghostbank.data.db.RunEntity
import com.arin.ghostbank.data.db.StealthScoreEntity
import com.arin.ghostbank.data.net.Fingerprints
import com.arin.ghostbank.data.net.GhostProfileEntityWrapper
import com.arin.ghostbank.data.net.LeakReport
import com.arin.ghostbank.model.Banks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BanksViewModel(container: AppContainer) : ViewModel() {
    val destination = container.accounts.destination()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { BanksViewModel(container) }
        }
    }
}

data class DestinationState(
    val number: String = "",
    val ifsc: String = "",
    val holder: String = "",
    val numberError: String? = null,
    val ifscError: String? = null,
    val branch: BranchInfo? = null,
    val checking: Boolean = false,
    val checkText: String? = null,
    val checkLive: Boolean = false,
    val saved: Boolean = false,
    val savedMasked: String? = null
)

class DestinationViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(DestinationState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.accounts.current()?.let { existing ->
                _state.update {
                    it.copy(number = existing.number, ifsc = existing.ifsc, holder = existing.holderName.orEmpty())
                }
            }
        }
    }

    fun setNumber(value: String) = _state.update {
        it.copy(number = value.filter { c -> c.isDigit() }.take(18), numberError = null, checkText = null)
    }

    fun setIfsc(value: String) = _state.update { current ->
        val clean = value.uppercase().filter { c -> c.isLetterOrDigit() }.take(11)
        current.copy(
            ifsc = clean,
            ifscError = BranchResolver.formatHint(clean),
            branch = BranchResolver.resolve(clean),
            checkText = null
        )
    }

    fun setHolder(value: String) = _state.update { it.copy(holder = value.take(40)) }

    fun checkPayee() {
        val current = _state.value
        val bank = Banks.byIfsc(current.ifsc) ?: run {
            _state.update { it.copy(ifscError = "Resolve the IFSC first") }
            return
        }
        if (current.number.length < 9) {
            _state.update { it.copy(numberError = "Account number looks too short") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(checking = true, checkText = null) }
            val settings = container.settings.flow.first()
            val exit = runCatching { container.proxyEngine.pick(settings) }.getOrNull()
            val wrapper = GhostProfileEntityWrapper(Fingerprints.generate())
            val client = container.http.client(settings, exit, wrapper)
            val url = "https://${bank.host}/imps/payee?account=${current.number}&ifsc=${current.ifsc}"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                        response.code to response.body?.string().orEmpty()
                    }
                }
            }
            result.onSuccess { (code, body) ->
                _state.update {
                    it.copy(
                        checking = false,
                        checkLive = code in 200..399,
                        checkText = if (code in 200..399) "bank answered HTTP $code — payee shape accepted (LIVE)"
                        else "bank answered HTTP $code — name check not confirmed (SIMULATED). ${body.take(80)}"
                    )
                }
            }.onFailure { failure ->
                _state.update {
                    it.copy(
                        checking = false,
                        checkLive = false,
                        checkText = "no server answer — name check simulated. ${failure.message.orEmpty()}"
                    )
                }
            }
        }
    }

    fun save() {
        val current = _state.value
        val bank = Banks.byIfsc(current.ifsc)
        val problem = when {
            current.number.length < 9 -> "Account number looks too short"
            !Banks.isValidIfsc(current.ifsc) -> BranchResolver.formatHint(current.ifsc) ?: "IFSC must be 4 letters, a 0, then 6 characters"
            bank == null -> "That IFSC is not one of the 27 supported banks"
            else -> null
        }
        if (problem != null) {
            _state.update { it.copy(numberError = problem) }
            return
        }
        viewModelScope.launch {
            container.accounts.saveDestination(current.number, current.ifsc, current.holder.ifBlank { null }, bank!!.code)
            container.settings.setDestinationSaved(true)
            val masked = "•".repeat((current.number.length - 4).coerceAtMost(8)) + current.number.takeLast(4)
            _state.update { it.copy(saved = true, savedMasked = masked) }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { DestinationViewModel(container) }
        }
    }
}

data class LedgerFilters(val bank: String = "ALL", val status: String = "ALL", val days: Int = 0)

class LedgerViewModel(private val container: AppContainer) : ViewModel() {

    private val _filters = MutableStateFlow(LedgerFilters())
    val filters = _filters.asStateFlow()

    val rows: StateFlow<List<LedgerRowEntity>> = combine(container.ledger.all(), _filters) { all, filter ->
        val cutOff = if (filter.days == 0) 0L else System.currentTimeMillis() - filter.days * 86_400_000L
        all.filter {
            (filter.bank == "ALL" || it.bankCode == filter.bank) &&
                (filter.status == "ALL" || it.status == filter.status) &&
                it.ts >= cutOff
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val credited = container.ledger.creditedPaise()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun setBank(bank: String) = _filters.update { it.copy(bank = bank) }
    fun setStatus(status: String) = _filters.update { it.copy(status = status) }
    fun setDays(days: Int) = _filters.update { it.copy(days = days) }

    fun export() = viewModelScope.launch {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val csv = rows.value.map {
            "${it.ts},${it.bankCode},${it.direction},${it.amountPaise},${it.utr.orEmpty()},${it.status},${it.badge},${it.counterparty.orEmpty()}"
        }
        val uri = container.exporter.writeCsv(
            "ghostbank_ledger_$stamp.csv",
            "timestamp,bank,direction,amount_paise,utr,status,badge,counterparty",
            csv
        )
        container.exporter.share(uri, "GhostBank ledger")
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { LedgerViewModel(container) }
        }
    }
}

class ShieldViewModel(private val container: AppContainer) : ViewModel() {

    private val _report = MutableStateFlow<LeakReport?>(null)
    val report = _report.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val _torRunning = MutableStateFlow<Boolean?>(null)
    val torRunning = _torRunning.asStateFlow()

    val latestRun: StateFlow<RunEntity?> = container.runs.recent()
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val latestScore: StateFlow<StealthScoreEntity?> = container.db.scores().recent()
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val proxyCount: StateFlow<Int> = container.db.proxies().all()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val registryCount: StateFlow<Int> = container.registry.count()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun probe() = viewModelScope.launch {
        _busy.value = true
        val settings = container.settings.flow.first()
        _report.value = container.leakLab.probe(settings)
        _busy.value = false
    }

    fun checkTor() = viewModelScope.launch {
        _torRunning.value = withContext(Dispatchers.IO) {
            runCatching { java.net.Socket("127.0.0.1", 9050).use { true } }.getOrDefault(false)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ShieldViewModel(container) }
        }
    }
}

class RegistryViewModel(private val container: AppContainer) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    val rows: StateFlow<List<RegistryEntity>> = _query.flatMapLatest { q ->
        if (q.isBlank()) container.registry.all() else container.registry.search(q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }

    fun force(id: Long) = viewModelScope.launch { container.registry.forceReset(id) }

    fun wipe() = viewModelScope.launch { container.registry.wipe() }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { RegistryViewModel(container) }
        }
    }
}
