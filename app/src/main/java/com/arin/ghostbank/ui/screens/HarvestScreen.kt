package com.arin.ghostbank.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.HarvestStatus
import com.arin.ghostbank.model.RunStatus
import com.arin.ghostbank.ui.components.ConfirmDialog
import com.arin.ghostbank.ui.components.EmptyState
import com.arin.ghostbank.ui.components.KeyValue
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.Pill
import com.arin.ghostbank.ui.components.ScreenColumn
import com.arin.ghostbank.ui.components.SectionTitle
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.TerminalField
import com.arin.ghostbank.ui.components.statusColor
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Hairline
import com.arin.ghostbank.ui.theme.InkCard
import com.arin.ghostbank.ui.theme.Red
import com.arin.ghostbank.ui.theme.TextDim
import com.arin.ghostbank.ui.vm.HarvestViewModel

@Composable
fun HarvestScreen(
    container: AppContainer,
    onDestination: () -> Unit,
    onLedger: () -> Unit,
    onConsole: (Long) -> Unit
) {
    val vm: HarvestViewModel = viewModel(factory = HarvestViewModel.factory(container))
    val preflight by vm.preflight.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val legs by vm.legs.collectAsStateWithLifecycle()
    val total by vm.totalPaise.collectAsStateWithLifecycle()
    val run by vm.run.collectAsStateWithLifecycle()
    val registry by vm.registry.collectAsStateWithLifecycle()
    val bankCode by vm.bankCode.collectAsStateWithLifecycle()
    var bulk by remember { mutableStateOf("") }
    var singleNumber by remember { mutableStateOf("") }
    var singleIfsc by remember { mutableStateOf("") }
    var confirmStop by remember { mutableStateOf(false) }

    if (confirmStop) {
        ConfirmDialog(
            title = "Stop the harvest?",
            body = "The queue stops permanently at the current account. Everything already claimed stays claimed — no account is ever touched twice.",
            confirm = "stop it",
            onConfirm = vm::cancel,
            onDismiss = { confirmStop = false }
        )
    }

    val settled = legs.count { it.status == HarvestStatus.SETTLED.name }
    val floored = legs.count { it.status == HarvestStatus.FLOOR.name }
    val rejected = legs.count { it.status == HarvestStatus.REJECTED.name }

    ScreenColumn {
        Panel(border = Green) {
            Column {
                Text("PAISE COUNTER", style = MaterialTheme.typography.labelSmall, color = TextDim)
                Text("$total paise", style = MaterialTheme.typography.headlineSmall, color = Green)
                Text(
                    "kept in integer paise, so 25 accounts are exactly 25 paise",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box3("sent", legs.size.toString())
            Box3("settled", settled.toString(), Green)
            Box3("floor", floored.toString(), Amber)
            Box3("rejected", rejected.toString(), Red)
        }

        Text("TARGET BANK", style = MaterialTheme.typography.labelSmall, color = TextDim)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("SBI", "HDFC", "ICICI", "AXIS").forEach { code ->
                val selected = code == bankCode
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (selected) Green.copy(alpha = 0.15f) else InkCard)
                        .border(1.dp, if (selected) Green else Hairline, RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(code, style = MaterialTheme.typography.labelSmall, color = if (selected) Green else TextDim)
                }
            }
        }

        SectionTitle("BULK PASTE", "one account per line: number, IFSC")
        TerminalField("PASTE", bulk, { bulk = it }, singleLine = false, placeholder = "50100234987121, HDFC0000123")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TerminalButton("VALIDATE", { vm.validate(bulk) }, color = Cyan)
            TerminalButton("QUEUE VALID LINES", vm::enqueueValid, enabled = preflight.any { it.ok })
        }

        if (preflight.isNotEmpty()) {
            Text("PRE-FLIGHT", style = MaterialTheme.typography.labelSmall, color = TextDim)
            preflight.forEach { line ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (line.ok) "[x]" else "[!]", color = if (line.ok) Green else Red, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(line.line, style = MaterialTheme.typography.labelSmall)
                        Text(line.reason ?: "ready", style = MaterialTheme.typography.labelSmall, color = if (line.ok) Green else Red)
                    }
                }
            }
        }

        SectionTitle("ADD ONE", null)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TerminalField("NUMBER", singleNumber, { singleNumber = it.filter(Char::isDigit).take(18) }, numeric = true)
            TerminalField("IFSC", singleIfsc, { singleIfsc = it.uppercase().take(11) })
        }
        TerminalButton("ADD TO QUEUE", { vm.addSingle(singleNumber, singleIfsc); singleNumber = ""; singleIfsc = "" }, color = Cyan)

        if (message != null) Text(message!!, style = MaterialTheme.typography.bodySmall, color = Amber)

        SectionTitle("PRE-FLIGHT LIST", "${pending.size} queued")
        if (pending.isEmpty()) {
            EmptyState("Nothing queued yet. Paste accounts above and validate them.", null)
        } else {
            pending.forEach { entry ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(entry.accountNumber, style = MaterialTheme.typography.bodySmall)
                        Text("${entry.ifsc} · ${entry.bankCode}", style = MaterialTheme.typography.labelSmall, color = TextDim)
                    }
                    Pill(HarvestStatus.PENDING.name, statusColor(HarvestStatus.PENDING))
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TerminalButton(
                "RUN HARVEST (₹0.01 FIXED)",
                vm::start,
                enabled = pending.isNotEmpty() && run?.status != RunStatus.RUNNING.name,
                modifier = Modifier.fillMaxWidth()
            )
            if (run?.status == RunStatus.RUNNING.name) TerminalButton("STOP", { confirmStop = true }, color = Red)
        }
        TerminalButton("RESUME INTERRUPTED RUN", vm::resumeInterrupted, color = Cyan)

        run?.let { current ->
            Panel {
                Column {
                    KeyValue("run", "#${current.id} · ${current.status}")
                    KeyValue("idempotency key", current.idempotencyKey)
                    current.summary?.let { KeyValue("summary", it) }
                    current.lastConfirmedStage?.let { KeyValue("last confirmed", it, Cyan) }
                    current.errorText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Red) }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TerminalButton("RAW CONSOLE", { onConsole(current.id) }, color = Cyan)
                        TerminalButton("LEDGER", onLedger, color = Cyan)
                        TerminalButton("EXPORT RUN", { vm.export() }, color = Amber)
                    }
                }
            }

            if (legs.isEmpty()) {
                Text("No accounts sent yet in this run.", style = MaterialTheme.typography.labelSmall, color = TextDim)
            } else {
                legs.forEach { leg ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(statusColor(runStatus(leg.status)).let { "■" }, color = statusColor(runStatus(leg.status)))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.fillMaxWidth(0.7f)) {
                            Text(leg.accountNumber, style = MaterialTheme.typography.bodySmall)
                            Text(
                                leg.utr?.let { "UTR $it" } ?: leg.reason ?: "in flight",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextDim
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Pill(leg.status, statusColor(runStatus(leg.status)))
                    }
                }
            }
        }

        if (run == null && pending.isEmpty()) {
            EmptyState("One account, one paisa, ever. Start by pasting account numbers.", null)
        }

        Panel {
            Column {
                KeyValue("registry entries", registry.size.toString())
                KeyValue("bank cap", Banks.byCode(bankCode)?.let { "₹${it.capPaise / 100}" } ?: "-")
                KeyValue("amount", "₹0.01 hard fixed · ₹1 floor")
            }
        }
    }
}

private fun runStatus(status: String): HarvestStatus =
    runCatching { HarvestStatus.valueOf(status) }.getOrDefault(HarvestStatus.PENDING)

@Composable
private fun androidx.compose.foundation.layout.RowScope.Box3(label: String, value: String, color: androidx.compose.ui.graphics.Color = TextDim) {
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(InkCard)
            .padding(8.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleSmall, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextDim)
    }
}
