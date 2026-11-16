package com.arin.ghostbank.ui.screens

import androidx.compose.foundation.background
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
import com.arin.ghostbank.data.Settings
import com.arin.ghostbank.data.db.HarvestLegEntity
import com.arin.ghostbank.data.db.RegistryEntity
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.HarvestStatus
import com.arin.ghostbank.model.RunMode
import com.arin.ghostbank.ui.components.BadgeChip
import com.arin.ghostbank.ui.components.ConfirmDialog
import com.arin.ghostbank.ui.components.EmptyState
import com.arin.ghostbank.ui.components.KeyValue
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.Pill
import com.arin.ghostbank.ui.components.SectionTitle
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.TerminalField
import com.arin.ghostbank.ui.components.badgeFor
import com.arin.ghostbank.ui.components.rupeesText
import com.arin.ghostbank.ui.components.statusColor
import com.arin.ghostbank.ui.components.tsText
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Ink
import com.arin.ghostbank.ui.theme.Red
import com.arin.ghostbank.ui.theme.TextDim
import com.arin.ghostbank.ui.theme.TextPrimary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private data class Line(val account: String, val ifsc: String, val error: String?)

@Composable
fun HarvestScreen(
    container: AppContainer,
    onDestination: () -> Unit,
    onLedger: () -> Unit,
    onConsole: (Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    val settings by container.settings.flow.collectAsStateWithLifecycle(Settings())
    val destination by container.accounts.destination().collectAsStateWithLifecycle(null)
    val registry by container.registry.all().collectAsStateWithLifecycle(emptyList())

    var paste by remember { mutableStateOf("") }
    var single by remember { mutableStateOf("") }
    var bankCode by remember { mutableStateOf("SBI") }
    var runId by remember { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var askCancel by remember { mutableStateOf(false) }

    val fallbackIfsc = destination?.ifsc.orEmpty()
    val lines = remember(paste, fallbackIfsc) {
        paste.lines().map { it.trim() }.filter { it.isNotBlank() }.map { raw ->
            val account = raw.substringBefore('|').filter { c -> c.isDigit() }
            val ifsc = raw.substringAfter('|', fallbackIfsc).trim().uppercase()
            val error = when {
                account.length !in 9..18 -> "account number must be 9–18 digits"
                !Banks.isValidIfsc(ifsc) -> "IFSC needs 4 letters, a 0, then 6 characters"
                else -> null
            }
            Line(account, ifsc, error)
        }
    }
    val queuePaths: Flow<List<HarvestLegEntity>> = remember(runId) {
        runId?.let { container.runs.legs(it) } ?: flowOf(emptyList())
    }
    val legs by queuePaths.collectAsStateWithLifecycle(emptyList())
    val totalFlow: Flow<Long?> = remember(runId) {
        runId?.let { container.db.legs().totalPaise(it) } ?: flowOf(0L)
    }
    val total by totalFlow.collectAsStateWithLifecycle(0L)

    val pending = registry.filter { it.status == HarvestStatus.PENDING.name }

    Column(Modifier.fillMaxSize().background(Ink).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel {
            Column {
                Text("HARVEST MODE", style = MaterialTheme.typography.titleSmall, color = Green)
                Text("amount is hard-fixed to ₹0.01 and can never drift. Sub-rupee rejections upgrade to the ₹1 rail floor and get tagged FLOOR.", style = MaterialTheme.typography.bodySmall, color = TextDim)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HarvestStatus.entries.forEach { Pill(it.name, statusColor(it)) }
                }
                Spacer(Modifier.height(6.dp))
                KeyValue("one account, one paisa, ever", "SHA-256 dedupe registry", Cyan)
                KeyValue("velocity", "${settings.maxAccountsPerHour} accounts/hour", TextDim)
                KeyValue("daily hard cap", settings.dailyHardCap.toString(), TextDim)
            }
        }

        if (destination == null) {
            Panel(border = Amber.copy(alpha = 0.5f)) {
                Column {
                    Text("NO DESTINATION SAVED", style = MaterialTheme.typography.labelSmall, color = Amber)
                    Spacer(Modifier.height(6.dp))
                    TerminalButton("ADD DESTINATION", onDestination, color = Amber)
                }
            }
        }

        TerminalField("BULK PASTE — one account per line, optional |IFSC", paste, { paste = it }, placeholder = "5010023498712|HDFC0000123", singleLine = false)
        lines.forEach { line ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(line.account.ifBlank { "?" }, style = MaterialTheme.typography.bodySmall, color = TextPrimary, modifier = Modifier.weight(1f))
                Text(line.error ?: "ok", style = MaterialTheme.typography.labelSmall, color = if (line.error == null) Green else Red)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TerminalButton("QUEUE ${lines.count { it.error == null }} VALID", {
                scope.launch {
                    var added = 0
                    lines.filter { it.error == null }.forEach { line ->
                        val bank = Banks.byIfsc(line.ifsc)?.code ?: bankCode
                        if (container.registry.enqueue(line.account, line.ifsc, bank) != null) added++
                    }
                    message = "$added queued, ${lines.size - added} already in the registry"
                    paste = ""
                }
            }, enabled = lines.any { it.error == null }, color = Green)
            TerminalButton("CLEAR PASTE", { paste = "" }, color = TextDim)
        }
        TerminalField("ADD ONE ACCOUNT", single, { single = it }, placeholder = "account number, uses the destination IFSC")
        TerminalButton("ADD SINGLY", {
            val account = single.filter { c -> c.isDigit() }
            if (account.length !in 9..18 || !Banks.isValidIfsc(fallbackIfsc)) {
                message = "account needs 9–18 digits and a valid destination IFSC"
            } else {
                scope.launch {
                    val added = container.registry.enqueue(account, fallbackIfsc, Banks.byIfsc(fallbackIfsc)?.code ?: bankCode)
                    message = if (added != null) "added to the pre-flight queue" else "that account is already in the registry"
                    single = ""
                }
            }
        }, enabled = destination != null, color = Cyan)

        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Cyan) }

        SectionTitle("pre-flight queue (${pending.size})", "nothing is sent until you run — the registry is written before each request leaves")
        if (pending.isEmpty()) {
            EmptyState("No accounts queued yet.", "paste a list above", null)
        } else {
            val validCodes = listOf("SBI", "HDFC", "ICICI", "AXIS", "KOTAK", "PNB", "BOB")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                validCodes.forEach { code ->
                    Text(
                        code,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (bankCode == code) Green else TextDim,
                        modifier = Modifier.padding(vertical = 4.dp).then(
                            androidx.compose.ui.Modifier
                        )
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                validCodes.take(4).forEach { code ->
                    TerminalButton(code, { bankCode = code }, color = if (bankCode == code) Green else TextDim)
                }
            }
            TerminalButton(if (busy) "RUNNING…" else "RUN HARVEST (${pending.size})", {
                scope.launch {
                    busy = true
                    val bank = Banks.byCode(bankCode) ?: Banks.all.first()
                    val id = container.engine.createTransferRun(bank, 1L, RunMode.HARVEST, settings.stealthLevel)
                    runId = id
                    container.engine.performHarvestRun(id, settings, bankCode, pending.map { it.id to (it.accountNumber to it.ifsc) })
                    busy = false
                }
            }, enabled = !busy, color = Green)
            if (busy) TerminalButton("STOP (confirm)", { askCancel = true }, color = Red)
        }

        if (askCancel && runId != null) {
            ConfirmDialog(
                "Cancel this harvest?",
                "The queue stops on the current account. Anything already settled stays settled; nothing is touched twice.",
                "cancel the run",
                { scope.launch { container.engine.cancel(runId!!) } },
                { askCancel = false }
            )
        }

        runId?.let { id ->
            SectionTitle("live run", "one UTR per settled account")
            Panel(border = Green.copy(alpha = 0.3f)) {
                Column {
                    KeyValue("sent", legs.size.toString(), TextDim)
                    KeyValue("settled", legs.count { it.status == HarvestStatus.SETTLED.name }.toString(), Green)
                    KeyValue("floor", legs.count { it.status == HarvestStatus.FLOOR.name }.toString(), Amber)
                    KeyValue("rejected", legs.count { it.status == HarvestStatus.REJECTED.name }.toString(), Red)
                    KeyValue("total paise", "${total ?: 0L} paise (${rupeesText(total ?: 0L)})", Green)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TerminalButton("CONSOLE", { onConsole(id) }, color = Cyan)
                        TerminalButton("LEDGER", onLedger, color = Cyan)
                        TerminalButton("EXPORT", {
                            scope.launch {
                                val uri = container.exporter.writeCsv(
                                    "harvest-$id.csv",
                                    "account,ifsc,paise,status,utr,reason,when",
                                    legs.map { "${it.accountNumber},${it.ifsc},${it.amountPaise},${it.status},${it.utr.orEmpty()},${it.reason.orEmpty()},${tsText(it.ts)}" }
                                )
                                container.exporter.share(uri, "harvest summary")
                            }
                        }, color = Amber)
                    }
                }
            }
            legs.forEach { leg ->
                val status = runCatching { HarvestStatus.valueOf(leg.status) }.getOrDefault(HarvestStatus.PENDING)
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${if (status == HarvestStatus.SETTLED) "✓" else "·"} ${leg.accountNumber}", style = MaterialTheme.typography.bodySmall, color = TextPrimary, modifier = Modifier.weight(1f))
                    Text(leg.utr ?: leg.reason.orEmpty(), style = MaterialTheme.typography.labelSmall, color = TextDim)
                    Spacer(Modifier.width(6.dp))
                    Pill("${status.name} ${leg.amountPaise}p", statusColor(status))
                }
            }
        }

        SectionTitle("registry", "accounts already claimed — re-runs skip them")
        if (registry.isEmpty()) {
            EmptyState("The registry is empty.", "queue an account to start", null)
        } else {
            registry.take(30).forEach { entry -> RegistryRow(container, entry) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RegistryRow(container: AppContainer, entry: RegistryEntity) {
    val scope = rememberCoroutineScope()
    val status = runCatching { HarvestStatus.valueOf(entry.status) }.getOrDefault(HarvestStatus.PENDING)
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(entry.accountNumber, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
            Text("${entry.ifsc} · ${tsText(entry.lastAttempt)}", style = MaterialTheme.typography.labelSmall, color = TextDim)
        }
        if (status == HarvestStatus.REJECTED) {
            TerminalButton("RETRY", { scope.launch { container.registry.forceReset(entry.id) } }, color = Amber)
            Spacer(Modifier.width(6.dp))
        }
        Pill(status.name, statusColor(status))
    }
}
