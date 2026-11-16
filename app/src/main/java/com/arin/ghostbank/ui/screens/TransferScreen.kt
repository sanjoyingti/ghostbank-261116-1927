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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.arin.ghostbank.data.db.StageLogEntity
import com.arin.ghostbank.model.Badge
import com.arin.ghostbank.model.REMITTER_PROFILES
import com.arin.ghostbank.model.RunStatus
import com.arin.ghostbank.model.StageKind
import com.arin.ghostbank.ui.components.BadgeChip
import com.arin.ghostbank.ui.components.BackBar
import com.arin.ghostbank.ui.components.ConfirmDialog
import com.arin.ghostbank.ui.components.EmptyState
import com.arin.ghostbank.ui.components.KeyValue
import com.arin.ghostbank.ui.components.Meter
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.Pill
import com.arin.ghostbank.ui.components.ScoreChip
import com.arin.ghostbank.ui.components.ScreenColumn
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.TerminalField
import com.arin.ghostbank.ui.components.clickableNoRipple
import com.arin.ghostbank.ui.components.paiseToRupees
import com.arin.ghostbank.ui.components.rememberKey
import com.arin.ghostbank.ui.components.stamp
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Hairline
import com.arin.ghostbank.ui.theme.InkCard
import com.arin.ghostbank.ui.theme.Red
import com.arin.ghostbank.ui.theme.TextDim
import com.arin.ghostbank.ui.theme.TextPrimary
import com.arin.ghostbank.ui.vm.TransferViewModel
import kotlinx.coroutines.flow.flowOf

@Composable
fun TransferScreen(
    container: AppContainer,
    bankCode: String,
    onBack: () -> Unit,
    onDestination: () -> Unit,
    onConsole: (Long) -> Unit,
    onTrace: (Long) -> Unit
) {
    val vm: TransferViewModel = viewModel(factory = TransferViewModel.factory(container, bankCode))
    val state by vm.state.collectAsStateWithLifecycle()
    val bank = vm.bank

    var confirmCancel by remember { mutableStateOf(false) }
    var confirmRun by remember { mutableStateOf(false) }

    ScreenColumn {
        BackBar(bank?.name ?: bankCode, bank?.quirk ?: "unknown bank", onBack)

        if (bank == null) {
            EmptyState("no such bank", "back", onBack)
            return@ScreenColumn
        }

        if (state.destinationMissing) {
            Panel(border = Amber) {
                Column {
                    Text("no destination saved", style = MaterialTheme.typography.titleSmall, color = Amber)
                    Text("Set the account the money should land in first.", style = MaterialTheme.typography.bodySmall, color = TextDim)
                    Spacer(Modifier.height(8.dp))
                    Text("SET DESTINATION →", style = MaterialTheme.typography.labelLarge, color = Amber,
                        modifier = Modifier.clickableNoRipple(onDestination))
                }
            }
        } else {
            Text(state.destinationLabel.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Green)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("CAP " + paiseToRupees(bank.capPaise), Amber)
            Pill("IMPS P2A 24x7", Green)
        }

        TerminalField(
            label = "AMOUNT (₹)",
            value = state.amountText,
            onChange = vm::setAmount,
            placeholder = "1 to " + paiseToRupees(bank.capPaise),
            error = state.error,
            numeric = true,
            enabled = !state.running
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TerminalButton("harvest ₹0.01", vm::fillHarvestFloor, enabled = !state.running, color = Cyan, modifier = Modifier.weight(1f))
            TerminalButton("max", {
                vm.setAmount((bank.capPaise / 100).toString())
            }, enabled = !state.running, color = TextDim, modifier = Modifier.weight(1f))
        }

        Text("source", style = MaterialTheme.typography.labelSmall, color = TextDim)
        REMITTER_PROFILES.forEachIndexed { index, profile ->
            val selected = state.sourceIndex == index
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(5.dp))
                    .background(InkCard)
                    .border(1.dp, if (selected) Green else Hairline, RoundedCornerShape(5.dp))
                    .clickableNoRipple { vm.setSource(index) }
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(profile.name, style = MaterialTheme.typography.bodySmall, color = if (selected) TextPrimary else TextDim)
                if (selected) Text("ARMED", style = MaterialTheme.typography.labelSmall, color = Green)
            }
        }

        if (state.running) {
            TerminalButton("RUN IN FLIGHT", {}, enabled = false, modifier = Modifier.fillMaxWidth())
        } else {
            TerminalButton(if (state.activeRunId == null) "RUN" else "RUN AGAIN", { confirmRun = true }, modifier = Modifier.fillMaxWidth())
        }

        val runId = state.activeRunId
        if (runId != null) {
            RunPanel(
                container = container,
                runId = runId,
                running = state.running,
                expanded = state.expanded,
                onToggle = vm::toggleExpanded,
                onCancel = { confirmCancel = true },
                onRetry = vm::retry,
                onConsole = { onConsole(runId) },
                onTrace = { onTrace(runId) }
            )
        } else {
            EmptyState("nothing sent yet", null)
        }

        Text(
            "A stage never advances without a server confirmation. The idempotency key is fixed per run, so a retry can never double-debit.",
            style = MaterialTheme.typography.labelSmall,
            color = TextDim
        )
    }

    if (confirmRun) {
        ConfirmDialog(
            title = "send the run?",
            body = "The seven stages will fire against ${bank.name}. Velocity caps and the entropy budget apply.",
            confirm = "run",
            onConfirm = vm::start,
            onDismiss = { confirmRun = false }
        )
    }
    if (confirmCancel) {
        ConfirmDialog(
            title = "cancel the run?",
            body = "The run stops at its last confirmed stage and stays in the ledger. Nothing is silently dropped.",
            confirm = "cancel run",
            onConfirm = vm::cancel,
            onDismiss = { confirmCancel = false }
        )
    }
}

@Composable
private fun RunPanel(
    container: AppContainer,
    runId: Long,
    running: Boolean,
    expanded: Set<Long>,
    onToggle: (Long) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onConsole: () -> Unit,
    onTrace: () -> Unit
) {
    val runFlow = remember(runId) { container.runs.byId(runId) }
    val run by runFlow.collectAsState(initial = null)
    val logsFlow = remember(runId) { container.runs.logs(runId) }
    val logs by logsFlow.collectAsState(initial = emptyList<StageLogEntity>())
    val scoreFlow = remember(runId) { container.db.scores().byRun(runId) }
    val score by scoreFlow.collectAsState(initial = null)

    val status = run?.status ?: RunStatus.RUNNING.name
    val idempotency = run?.idempotencyKey.orEmpty()

    Panel(border = when (status) {
        RunStatus.COMPLETED.name -> Green
        RunStatus.FAILED.name, RunStatus.CANCELLED.name -> Red
        RunStatus.PAUSED.name -> Amber
        else -> Hairline
    }) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("run #$runId", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                PILL_STATUS(status)
            }
            Spacer(Modifier.height(6.dp))
            KeyValue("idempotency", idempotency.take(18) + "…")
            KeyValue("amount", paiseToRupees(run?.amountPaise ?: 0L))
            KeyValue("exit", (run?.exitIp ?: "unknown") + " / " + (run?.exitCountry ?: "??"))
            KeyValue("last confirmed", run?.lastConfirmedStage ?: "none")
            Spacer(Modifier.height(8.dp))

            StageKind.values().forEach { kind ->
                val latest = logs.lastOrNull { it.stage == kind.name }
                val colour = when {
                    latest == null -> TextDim
                    latest.httpStatus != null && latest.httpStatus!! in 200..399 -> Green
                    else -> Red
                }
                val mark = when {
                    latest == null -> "·"
                    latest.httpStatus != null && latest.httpStatus!! in 200..399 -> "✓"
                    else -> "✕"
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickableNoRipple { latest?.let { onToggle(it.id) } }
                        .padding(vertical = 4.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(mark + " ", style = MaterialTheme.typography.bodyMedium, color = colour)
                            Text(kind.name, style = MaterialTheme.typography.bodySmall, color = colour)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (latest?.latencyMs != null) {
                                Text(latest!!.latencyMs.toString() + "ms ", style = MaterialTheme.typography.labelSmall, color = TextDim)
                            }
                            if (latest?.httpStatus != null) {
                                Text("HTTP " + latest!!.httpStatus, style = MaterialTheme.typography.labelSmall, color = colour)
                            }
                            if (latest != null) {
                                Spacer(Modifier.height(2.dp))
                                Text(if (latest!!.badge == Badge.LIVE.name) " LIVE" else " SIMULATED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (latest!!.badge == Badge.LIVE.name) Green else Amber)
                            }
                        }
                    }
                    val entry = latest
                    if (entry != null && expanded.contains(entry.id)) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(com.arin.ghostbank.ui.theme.Ink)
                                .padding(8.dp)
                        ) {
                            Text(entry.note ?: entry.stage, style = MaterialTheme.typography.labelSmall, color = TextDim)
                            if (entry.requestHeaders != null) {
                                Text("→ request", style = MaterialTheme.typography.labelSmall, color = Cyan)
                                Text(entry.requestHeaders, style = MaterialTheme.typography.bodySmall, color = TextDim)
                            }
                            if (entry.responseHeaders != null) {
                                Text("← response headers", style = MaterialTheme.typography.labelSmall, color = Cyan)
                                Text(entry.responseHeaders, style = MaterialTheme.typography.bodySmall, color = TextDim)
                            }
                            if (entry.rawBody != null) {
                                Text("raw body", style = MaterialTheme.typography.labelSmall, color = Cyan)
                                Text(entry.rawBody.take(1200), style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            val effectiveScore = score?.score ?: run?.stealthScore ?: 0
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                ScoreChip(effectiveScore)
                Text("score gauge", style = MaterialTheme.typography.labelSmall, color = TextDim)
            }
            Spacer(Modifier.height(4.dp))
            Meter(effectiveScore / 100f, if (effectiveScore >= 80) Green else if (effectiveScore >= 50) Amber else Red)
            score?.reasons?.split("\n")?.filter { it.isNotBlank() }?.forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = TextDim)
            }

            Spacer(Modifier.height(8.dp))
            if (run?.errorText != null) {
                Panel(border = Red) {
                    Column {
                        Text(run!!.errorKind ?: "ERROR", style = MaterialTheme.typography.labelSmall, color = Red)
                        Text(run!!.errorText!!, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (run?.summary != null) {
                Text(run!!.summary!!, style = MaterialTheme.typography.bodySmall, color = TextDim)
                Spacer(Modifier.height(8.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (running) {
                    TerminalButton("CANCEL", onCancel, color = Red, modifier = Modifier.weight(1f))
                } else {
                    TerminalButton("RETRY (same key)", onRetry, color = Amber, modifier = Modifier.weight(1f))
                }
                TerminalButton("RAW", onConsole, color = Cyan, modifier = Modifier.weight(1f))
                TerminalButton("TRACE", onTrace, color = TextDim, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            Text("started " + stamp(run?.startedAt ?: 0L), style = MaterialTheme.typography.labelSmall, color = TextDim)
        }
    }
}

@Composable
private fun PILL_STATUS(status: String) {
    val colour = when (status) {
        RunStatus.COMPLETED.name -> Green
        RunStatus.FAILED.name, RunStatus.CANCELLED.name -> Red
        RunStatus.PAUSED.name -> Amber
        else -> Cyan
    }
    Pill(status, colour)
}
