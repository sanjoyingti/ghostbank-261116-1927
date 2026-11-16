package com.arin.ghostbank.ui.vm

import androidx.lifecycle.ViewModel
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

    private val all = container.ledger.all()

    val bankFilter = MutableStateFlow<String?>(null)
    val statusFilter = MutableStateFlow<String?>(null)
    val daysFilter = MutableStateFlow(0)

    val rows: StateFlow<List<LedgerRowEntity>> =
        combine(all, bankFilter, statusFilter, daysFilter) { list, bank, status, days ->
            val cutoff = if (days == 0) 0L else System.currentTimeMillis() - days * 86_400_000L
            list.filter { row ->
                (bank == null || row.bankCode == bank) &&
                    (status == null || row.status == status) &&
                    (cutoff == 0L || row.ts >= cutoff)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val credited: StateFlow<Long> = container.ledger.creditedPaise()
        .let { flow -> kotlinx.coroutines.flow.map(flow) { it ?: 0L } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun setBank(code: String?) {
        bankFilter.value = code
    }

    fun setStatus(status: String?) {
        statusFilter.value = status
    }

    fun setDays(days: Int) {
        daysFilter.value = days
    }

    fun export() {
        viewModelScope.launch {
            val list = rows.value
            if (list.isEmpty()) {
                _message.value = "nothing to export with these filters"
                return@launch
            }
            val lines = list.map {
                "${it.ts},${it.bankCode},${it.direction},${it.amountPaise},${it.utr.orEmpty()},${it.status},${it.badge}"
            }
            val uri = container.exporter.writeCsv(
                "ghostbank-ledger.csv",
                "timestamp,bank,direction,paise,utr,status,badge",
                lines
            )
            container.exporter.share(uri, "GhostBank ledger")
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { LedgerViewModel(container) }
        }
    }
}
