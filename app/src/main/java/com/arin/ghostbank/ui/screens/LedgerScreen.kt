=== ABOUT: Fixed the missing ledger: debit and credit rows side by side, filters, and a CSV share-sheet export ===
package com.arin.ghostbank.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.db.LedgerRowEntity
import com.arin.ghostbank.ui.components.BadgeChip
import com.arin.ghostbank.ui.components.EmptyState
import com.arin.ghostbank.ui.components.KeyValue
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.Pill
import com.arin.ghostbank.ui.components.ScreenColumn
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.TerminalField
import com.arin.ghostbank.ui.components.badgeFor
import com.arin.ghostbank.ui.components.paiseToCompact
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Hairline
import com.arin.ghostbank.ui.theme.Red
import com.arin.ghostbank.ui.theme.TextDim
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LedgerScreen(container: AppContainer) {
    val rows by container.ledger.all().collectAsStateWithLifecycle(initialValue = emptyList())
    val credited by container.ledger.creditedPaise().collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var bankFilter by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("") }
    var dayFilter by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    val stamp = remember { SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.US) }
    val filtered = rows.filter { row ->
        (bankFilter.isBlank() || row.bankCode.contains(bankFilter, true)) &&
            (statusFilter.isBlank() || row.status.contains(statusFilter, true)) &&
            (dayFilter.isBlank() || stamp.format(Date(row.ts)).contains(dayFilter, true))
    }

    ScreenColumn {
        Text("LEDGER OF TRUTH", style = MaterialTheme.typography.titleSmall, color = Green)
        KeyValue("credited in total", "${credited ?: 0L} paise", Green)

        TerminalField("filter bank", bankFilter, { bankFilter = it })
        TerminalField("filter status (RECEIVED / FAILED / SENT / REVERSED)", statusFilter, { statusFilter = it })
        TerminalField("filter date (e.g. 12 Mar)", dayFilter, { dayFilter = it })

        TerminalButton("export filtered as CSV", {
            scope.launch {
                val uri = container.exporter.writeCsv(
                    name = "ghostbank_ledger.csv",
                    header = "time,bank,direction,amount_paise,utr,status,badge,run",
                    rows = filtered.map {
                        "${stamp.format(Date(it.ts))},${it.bankCode},${it.direction},${it.amountPaise},${it.utr ?: ""},${it.status},${it.badge},${it.runId}"
                    }
                )
                container.exporter.share(uri, "Share ledger CSV")
                message = "CSV written — the share sheet is open"
            }
        }, color = Cyan)
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Cyan) }

        val paired = filtered.groupBy { it.runId }
        if (paired.isEmpty()) {
            EmptyState("Nothing has moved yet — run a transfer or a harvest", null, null)
        } else {
            paired.forEach { (runId, group) ->
                Panel(border = Hairline) {
                    Column {
                        Text("run #$runId · ${stamp.format(Date(group.first().ts))}", style = MaterialTheme.typography.labelSmall, color = TextDim)
                        group.forEach { row -> LedgerLine(row) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerLine(row: LedgerRowEntity) {
    val color = when (row.status) {
        "RECEIVED" -> Green
        "REVERSED" -> Amber
        "FAILED" -> Red
        else -> Cyan
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Pill(row.direction, color)
            Spacer(Modifier.width(8.dp))
            Text(row.bankCode, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.typography.bodySmall.color)
            Spacer(Modifier.width(8.dp))
            Text(paiseToCompact(row.amountPaise), style = MaterialTheme.typography.bodySmall, color = color)
            Spacer(Modifier.width(8.dp))
            BadgeChip(badgeFor(row.badge))
        }
        row.utr?.let { Text("UTR $it", style = MaterialTheme.typography.labelSmall, color = Green) }
        row.counterparty?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = TextDim) }
        Spacer(Modifier.height(2.dp))
    }
}
