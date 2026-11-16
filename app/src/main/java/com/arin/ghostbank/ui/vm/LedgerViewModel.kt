package com.arin.ghostbank.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.db.LedgerRowEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LedgerViewModel(private val container: AppContainer) : ViewModel() {

    private val _bank = MutableStateFlow<String?>(null)
    val bank: StateFlow<String?> = _bank.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _weekOnly = MutableStateFlow(true)
    val weekOnly: StateFlow<Boolean> = _weekOnly.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val rows: StateFlow<List<LedgerRowEntity>> = combine(
        container.ledger.all(), _bank, _status, _weekOnly
    ) { all, bank, status, weekOnly ->
        val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        all.filter { row ->
            (bank == null || row.bankCode == bank) &&
                (status == null || row.status == status) &&
                (!weekOnly || row.ts >= cutoff)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val credited: StateFlow<Long> = container.ledger.creditedPaise()
        .let { flow ->
            kotlinx.coroutines.flow.map(flow) { it ?: 0L }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun setBank(code: String?) { _bank.value = code }
    fun setStatus(value: String?) { _status.value = value }
    fun setWeekOnly(value: Boolean) { _weekOnly.value = value }

    fun export(onUri: (android.net.Uri) -> Unit) {
        viewModelScope.launch {
            val current = rows.value
            if (current.isEmpty()) {
                _message.value = "no rows match the filter"
                return@launch
            }
            val uri = withContext(Dispatchers.IO) {
                container.exporter.writeCsv(
                    "ledger_export.csv",
                    "ts,run_id,bank,direction,paise,utr,status,badge,counterparty",
                    current.map { "${it.ts},${it.runId},${it.bankCode},${it.direction},${it.amountPaise},${it.utr ?: ""},${it.status},${it.badge},${it.counterparty ?: ""}" }
                )
            }
            onUri(uri)
        }
    }

    fun dismissMessage() { _message.value = null }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { LedgerViewModel(container) }
        }
    }
}
