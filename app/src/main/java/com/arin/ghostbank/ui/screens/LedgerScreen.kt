package com.arin.ghostbank.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.db.LedgerRowEntity
import com.arin.ghostbank.ui.components.BadgeChip
import com.arin.ghostbank.ui.components.EmptyState
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.Pill
import com.arin.ghostbank.ui.components.SectionTitle
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.TerminalField
import com.arin.ghostbank.ui.components.clickableNoRipple
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Hairline
import com.arin.ghostbank.ui.theme.Ink
import com.arin.ghostbank.ui.theme.Red
import com.arin.ghostbank.ui.theme.TextDim
import com.arin.ghostbank.ui.theme.TextPrimary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LedgerViewModel(private val container: AppContainer) : ViewModel() {

    private val _bank = MutableStateFlow<String?>(null)
    val bank: StateFlow<String?> = _bank.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _date = MutableStateFlow("")
    val date: StateFlow<String> = _date.asStateFlow()

    private val _note = MutableStateFlow<String?>(null)
    val note: StateFlow<String?> = _note.asStateFlow()

    private val _all = container.ledger.all()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptyList())

    val rows: StateFlow<List<LedgerRowEntity>> = combine(_all, _bank, _status, _date) { list, bankCode, statusName, dateText ->
        list.filter { row ->
            (bankCode == null || row.bankCode == bankCode) &&
                (statusName == null || row.status == statusName) &&
                (dateText.isBlank() || dayOf(row.ts) == dateText)
        }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptyList())

    val credited: StateFlow<Long> = _all.map { list -> list.filter { it.direction == "CREDIT" && it.status == "RECEIVED" }.sumOf { it.amountPaise } }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), 0L)

    val banksPresent: StateFlow<List<String>> = _all.map { list -> list.map { it.bankCode }.distinct().sorted() }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setBank(value: String?) { _bank.value = value }
    fun setStatus(value: String?) { _status.value = value }
    fun setDate(value: String) { _date.value = value }

    fun export(onShared: (String) -> Unit) {
        viewModelScope.launch {
            val current = rows.value
            if (current.isEmpty()) { _note.value = "Nothing matches the filters."; return@launch }
            val lines = current.map { row ->
                listOf(
                    row.ts, row.bankCode, row.direction, row.amountPaise, row.utr.orEmpty(),
                    row.status, row.badge, row.counterparty.orEmpty()
                ).joinToString(",")
            }
            val uri = container.exporter.writeCsv(
                "ghostbank_ledger_${System.currentTimeMillis()}.csv",
                "epoch_ms,bank,direction,amount_paise,utr,status,badge,counterparty",
                lines
            )
            container.exporter.share(uri, "Ledger CSV")
            onShared("exported ${lines.size} rows to a share sheet")
        }
    }

    private fun dayOf(ts: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ts))

    companion object {
        fun factory(container: AppContainer) = viewModelFactory { initializer { LedgerViewModel(container) } }
    }
}

@Composable
fun LedgerScreen(container: AppContainer) {
    val vm: LedgerViewModel = viewModel(factory = LedgerViewModel.factory(container))
    val rows by vm.rows.collectAsStateWithLifecycle()
    val credited by vm.credited.collectAsStateWithLifecycle()
    val banksPresent by vm.banksPresent.collectAsStateWithLifecycle()
    val bankFilter by vm.bank.collectAsStateWithLifecycle()
    val statusFilter by vm.status.collectAsStateWithLifecycle()
    val dateFilter by vm.date.collectAsStateWithLifecycle()
    val note by vm.note.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("LEDGER OF TRUTH", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Text("Debit and credit side by side. Amounts in integer paise.", style = MaterialTheme.typography.bodySmall, color = TextDim)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Pill("credited", Green)
            Spacer(Modifier.width(6.dp))
            Text("$credited paise", style = MaterialTheme.typography.titleSmall, color = Green)
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            FilterChip("all banks", bankFilter == null) { vm.setBank(null) }
            banksPresent.forEach { code -> FilterChip(code, bankFilter == code) { vm.setBank(code) } }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            FilterChip("all", statusFilter == null) { vm.setStatus(null) }
            listOf("RECEIVED", "SENT", "REVERSED", "FAILED").forEach { name ->
                FilterChip(name, statusFilter == name) { vm.setStatus(name) }
            }
        }
        Spacer(Modifier.height(8.dp))
        TerminalField("date (yyyy-MM-dd)", dateFilter, { vm.setDate(it) }, placeholder = "2025-01-31")
        Spacer(Modifier.height(8.dp))
        TerminalButton("EXPORT CSV", { vm.export { } }, color = Cyan, modifier = Modifier.fillMaxWidth())
        note?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.labelSmall, color = Amber)
        }

        Spacer(Modifier.height(14.dp))
        if (rows.isEmpty()) {
            EmptyState(
                title = if (bankFilter == null && statusFilter == null && dateFilter.isBlank())
                    "The ledger is empty. Run a harvest and every paisa shows here."
                else "No rows match these filters.",
                action = null
            )
        } else {
            SectionTitle("${rows.size} ROWS")
            val grouped = rows.groupBy { it.runId }
            grouped.forEach { (runId, group) ->
                Panel(border = Hairline, modifier = Modifier.padding(bottom = 8.dp)) {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("run #$runId", style = MaterialTheme.typography.labelSmall, color = TextDim)
                            Text(
                                SimpleDateFormat("dd MMM HH:mm", Locale.US).format(Date(group.first().ts)),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextDim
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        group.sortedBy { it.direction }.forEach { row ->
                            val isDebit = row.direction == "DEBIT"
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(
                                    if (isDebit) "DEBIT " else "CREDIT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isDebit) Amber else Green,
                                    modifier = Modifier.width(66.dp)
                                )
                                Column(Modifier.weight(1f)) {
                                    Text("${row.amountPaise} paise", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                    Text(
                                        row.utr?.let { "utr $it" } ?: "(no utr — not confirmed)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextDim
                                    )
                                }
                                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                    Text(row.bankCode, style = MaterialTheme.typography.labelSmall, color = TextDim)
                                    Text(
                                        row.status,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when (row.status) {
                                            "RECEIVED" -> Green
                                            "FAILED", "REVERSED" -> Red
                                            else -> Cyan
                                        }
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                BadgeChip(if (row.badge == "LIVE") com.arin.ghostbank.model.Badge.LIVE else com.arin.ghostbank.model.Badge.SIMULATED)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Panel(
        modifier = Modifier.padding(end = 6.dp).clickableNoRipple(onClick),
        border = if (selected) Green else Hairline
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) Green else TextDim)
    }
}
