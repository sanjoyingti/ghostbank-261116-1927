package com.arin.ghostbank.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.Settings
import com.arin.ghostbank.data.net.BankProbe
import com.arin.ghostbank.model.Badge
import com.arin.ghostbank.model.Bank
import com.arin.ghostbank.model.Banks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BanksViewModel(private val container: AppContainer) : ViewModel() {

    private val probe = BankProbe(container.http, container.proxyEngine)

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _badges = MutableStateFlow<Map<String, Badge>>(emptyMap())
    val badges = _badges.asStateFlow()

    private val _probing = MutableStateFlow<Set<String>>(emptySet())
    val probing = _probing.asStateFlow()

    val settings: StateFlow<Settings> = container.settings.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    val destination = container.accounts.destination()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val banks: StateFlow<List<Bank>> = combine(_query, _badges) { query, badges ->
        val filtered = Banks.all.filter {
            query.isBlank() || it.name.contains(query, true) || it.code.contains(query, true)
        }
        filtered.sortedWith(compareBy({ it.group.ordinal }, { if (badges[it.code] == Badge.LIVE) 0 else 1 }, { it.name }))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Banks.all)

    fun setQuery(value: String) {
        _query.value = value
    }

    fun probeBank(code: String) = viewModelScope.launch {
        val bank = Banks.byCode(code) ?: return@launch
        if (_probing.value.contains(code)) return@launch
        _probing.value = _probing.value + code
        val settingsNow = container.settings.flow.first()
        val badge = probe.probe(settingsNow, bank)
        _badges.value = _badges.value + (code to badge)
        _probing.value = _probing.value - code
    }

    fun probeAll() = viewModelScope.launch {
        Banks.all.forEach { bank ->
            if (!_badges.value.containsKey(bank.code)) probeBank(bank.code)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { BanksViewModel(container) }
        }
    }
}
