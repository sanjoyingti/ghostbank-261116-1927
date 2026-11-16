=== ABOUT: Filters the ledger by bank, status and date and exports it as CSV ===
package com.arin.ghostbank.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.db.LedgerRowEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LedgerViewModel(private val container: AppContainer) : ViewModel() {

    private val _bank = MutableStateFlow("")
    val bank: StateFlow<String> = _bank.asStateFlow()

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _window = MutableStateFlow(0)
    val window: StateFlow<Int> = _window.asStateFlow()

    val rows: StateFlow<List<LedgerRowEntity>> = combine(
        container.ledger.all(), _bank, _status, _window
    ) { all, bank, status, window ->
        val from = when (window) {
            1 -> System.currentTimeMillis() - 24 * 3_600_000L
            7 -> System.currentTimeMillis() - 7 * 24 * 3_600_000L
            else -> 0L
        }
        all.filter { row ->
            (bank.isBlank() || row.bankCode == bank) &&
                (status.isBlank() || row.status == status) &&
                row.ts >= from
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setBank(code: String) { _bank.value = code }
    fun setStatus(value: String) { _status.value = value }
    fun setWindow(days: Int) { _window.value = days }

    fun export() = viewModelScope.launch {
        val rows = rows.value.map {
            "${it.ts},${it.bankCode},${it.direction},${it.amountPaise},${it.utr ?: ""},${it.status},${it.badge},${it.counterparty ?: ""}"
        }
        val uri = container.exporter.writeCsv(
            "ghostbank_ledger.csv",
            "ts,bank,direction,paise,utr,status,badge,counterparty",
            rows
        )
        container.exporter.share(uri, "Export ledger")
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { LedgerViewModel(container) }
        }
    }
}
