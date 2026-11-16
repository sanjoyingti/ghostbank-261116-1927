package com.arin.ghostbank.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.db.LedgerRowEntity
import com.arin.ghostbank.ui.components.BadgeChip
import com.arin.ghostbank.ui.components.EmptyState
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.Pill
import com.arin.ghostbank.ui.components.SectionTitle
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.badgeFor
import com.arin.ghostbank.ui.components.rupeesText
import com.arin.ghostbank.ui.components.tsText
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Ink
import com.arin.ghostbank.ui.theme.Red
import com.arin.ghostbank.ui.theme.TextDim
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch

@Composable
fun LedgerScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val rows by container.ledger.all().collectAsStateWithLifecycle(emptyList())
    val credited by container.ledger.creditedPaise().collectAsStateWithLifecycle(0L)
    var bankFilter by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("") }

    val filtered = remember(rows, bankFilter, statusFilter) {
        rows.filter { (bankFilter.isBlank() || it.bankCode == bankFilter) && (statusFilter.isBlank() || it.status == statusFilter) }
    }
    val paired = remember(filtered) { filtered.groupBy { it.runId }.values.toList() }

    Column(Modifier.fillMaxSize().background(Ink).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Panel {
            Column {
                Text("BOOKED MONEY", style = MaterialTheme.typography.labelSmall, color = Green)
                Text(rupeesText(credited ?: 0L), style = MaterialTheme.typography.titleMedium, color = Green)
                Text("integer paise in the store — never floats", style = MaterialTheme.typography.bodySmall, color = TextDim)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TerminalButton("ALL BANKS", { bankFilter = "" }, color = if (bankFilter.isBlank()) Green else TextDim)
            listOf("SBI", "HDFC", "ICICI", "AXIS").forEach { code ->
                TerminalButton(code, { bankFilter = code }, color = if (bankFilter == code) Green else TextDim)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TerminalButton("ALL STATES", { statusFilter = "" }, color = if (statusFilter.isBlank()) Green else TextDim)
            listOf("RECEIVED", "REVERSED", "FAILED", "SENT").forEach { st ->
                TerminalButton(st, { statusFilter = st }, color = if (statusFilter == st) Amber else TextDim)
            }
        }
        TerminalButton("EXPORT CSV TO SHARE SHEET", {
            scope.launch {
                val uri = container.exporter.writeCsv(
                    "ghostbank-ledger.csv",
                    "when,bank,direction,paise,utr,status,badge",
                    filtered.map { "${tsText(it.ts)},${it.bankCode},${it.direction},${it.amountPaise},${it.utr.orEmpty()},${it.status},${it.badge}" }
                )
                container.exporter.share(uri, "ghostbank ledger")
            }
        }, enabled = filtered.isNotEmpty(), color = Cyan)

        if (paired.isEmpty()) {
            EmptyState("Nothing booked yet.", "run a transfer or a harvest", null)
        }
        paired.forEach { pair ->
            val debit = pair.firstOrNull { it.direction == "DEBIT" }
            val credit = pair.firstOrNull { it.direction == "CREDIT" }
            Panel {
                Column {
                    Text("${debit?.bankCode ?: credit?.bankCode ?: "?"} · run #${pair.first().runId} · ${tsText(pair.first().ts)}", style = MaterialTheme.typography.labelSmall, color = TextDim)
                    Spacer(Modifier.height(6.dp))
                    PairRow("DEBIT", debit)
                    HorizontalDivider(color = TextDim.copy(alpha = 0.3f))
                    Spacer(Modifier.height(4.dp))
                    PairRow("CREDIT", credit)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PairRow(label: String, row: LedgerRowEntity?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (label == "CREDIT") Green else Red, modifier = Modifier.width(58.dp))
        if (row == null) {
            Text("—", style = MaterialTheme.typography.bodySmall, color = TextDim, textAlign = TextAlign.Start, modifier = Modifier.weight(1f))
        } else {
            Text(rupeesText(row.amountPaise) + "  " + row.counterparty.orEmpty(), style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color.Unspecified.takeIf { false } ?: androidx.compose.material3.MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            Text(row.utr ?: "", style = MaterialTheme.typography.labelSmall, color = TextDim)
            Spacer(Modifier.width(6.dp))
            Pill(row.status, if (row.status == "RECEIVED") Green else if (row.status == "SENT") Cyan else Amber)
            Spacer(Modifier.width(6.dp))
            BadgeChip(badgeFor(row.badge))
        }
    }
}
