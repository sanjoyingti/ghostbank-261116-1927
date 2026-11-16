package com.arin.ghostbank.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.model.Badge
import com.arin.ghostbank.model.REMITTER_PROFILES
import com.arin.ghostbank.model.RunStatus
import com.arin.ghostbank.ui.components.BackBar
import com.arin.ghostbank.ui.components.BadgeChip
import com.arin.ghostbank.ui.components.ConfirmDialog
import com.arin.ghostbank.ui.components.KeyValue
import com.arin.ghostbank.ui.components.Meter
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.Pill
import com.arin.ghostbank.ui.components.ScreenColumn
import com.arin.ghostbank.ui.components.SectionTitle
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.TerminalField
import com.arin.ghostbank.ui.components.clickableNoRipple
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Hairline
import com.arin.ghostbank.ui.theme.Red
import com.arin.ghostbank.ui.theme.TextDim
import com.arin.ghostbank.ui.vm.TransferViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val amount by vm.amount.collectAsStateWithLifecycle()
    val source by vm.source.collectAsStateWithLifecycle()
    val run by vm.run.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val destination by vm.destination.collectAsStateWithLifecycle()
    val runId by vm.runId.collectAsStateWithLifecycle()
    val expanded = remember { mutableStateMapOf<Long, Boolean>() }
    val confirm = remember { androidx.compose.runtime.mutableStateOf(false) }

    val bank = vm.bank
    val finished = run?.status == RunStatus.COMPLETED.name || run?.status == RunStatus.FAILED.name ||
        run?.status == RunStatus.CANCELLED.name

    Column {
        BackBar(bank?.name ?: "Unknown bank", "IMPS P2A · real-time · 24x7", onBack)
        ScreenColumn {
            if (bank == null) {
                Text("That bank code is not known.", style = MaterialTheme.typography.bodyMedium, color = Red)
                return@ScreenColumn
            }

            Panel(border = Hairline) {
                Column {
                    KeyValue("per-transaction cap", "₹${bank.capPaise / 100}")
                    KeyValue("rail", if (bank.impsSupported) "IMPS P2A (account + IFSC)" else "unsupported")
                    KeyValue("quirk", bank.quirk, Amber)
                    KeyValue("velocity", "${settings.maxRequestsPerMinute}/min · ${settings.maxAccountsPerHour}/hour")
                    KeyValue("entropy left", "${settings.entropyBudget - logs.size} requests")
                    Spacer(Modifier.height(6.dp))
                    Meter(
                        fraction = 1f - (logs.size.toFloat() / settings.entropyBudget.coerceAtLeast(1)),
                        color = if (logs.size < settings.entropyBudget) Green else Red
                    )
                }
            }

            if (destination == null) {
                Panel(border = Amber) {
                    Column {
                        Text("No destination saved.", style = MaterialTheme.typography.bodySmall, color = Amber)
                        Spacer(Modifier.height(8.dp))
                        TerminalButton("set destination", onDestination, color = Amber)
                    }
                }
            }

            TerminalField(
                label = "amount in rupees",
                value = amount,
                onChange = vm::setAmount,
                numeric = true,
                placeholder = "1 – ${bank.capPaise / 100}"
            )

            SectionTitle("Source", "saved remitter profile, or the bank's own P2A route")
            REMITTER_PROFILES.forEachIndexed { index, profile ->
                val selected = index == source
                Panel(
                    modifier = Modifier.clickableNoRipple { vm.setSource(index) },
                    border = if (selected) Green else Hairline
                ) {
                    Column {
                        Text(profile.name, style = MaterialTheme.typography.bodyMedium, color = if (selected) Green else MaterialTheme.colorScheme.onBackground)
                        Text("${profile.accountNumber} · ${profile.ifsc}", style = MaterialTheme.typography.labelSmall, color = TextDim)
                    }
                }
            }

            if (error != null) {
                Panel(border = Red) {
                    Text(error!!, style = MaterialTheme.typography.bodySmall, color = Red)
                }
            }

            if (!finished) {
                TerminalButton(
                    if (busy) "running…" else "RUN",
                    vm::run,
                    enabled = !busy && destination != null,
                    color = Green
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TerminalButton("retry with same key", vm::retry, modifier = Modifier.weight(1f), color = Cyan)
                    runId?.let { id ->
                        TerminalButton("console", { onConsole(id) }, modifier = Modifier.weight(1f), color = TextDim)
                    }
                }
            }
            if (!finished && runId != null) {
                TerminalButton("cancel run", { confirm.value = true }, color = Red)
            }
            if (confirm.value) {
                ConfirmDialog(
                    title = "Cancel this run?",
                    body = "The run stops at the last confirmed stage and stays in the ledger. Nothing is resent without you.",
                    confirm = "cancel run",
                    onConfirm = vm::cancel,
                    onDismiss = { confirm.value = false }
                )
            }

            SectionTitle("Pipeline", "a stage cannot advance without a server confirmation")
            vm.stages.forEachIndexed { index, stage ->
                val log = logs.lastOrNull { it.stage == stage.name }
                val status = log?.httpStatus
                val ok = status != null && status in 200..399
                val tickColor = when {
                    log == null -> TextDim
                    ok -> Green
                    else -> Amber
                }
                Panel(border = if (log == null) Hairline else tickColor.copy(alpha = 0.5f)) {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (ok) "✔" else if (log == null) "·" else "×", color = tickColor)
                                Spacer(Modifier.height(0.dp))
                                Text("  ${index + 1}. ${stage.name}", style = MaterialTheme.typography.titleSmall, color = tickColor)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                log?.let { BadgeChip(if (it.badge == Badge.LIVE.name) Badge.LIVE else Badge.SIMULATED) }
                                Spacer(Modifier.height(0.dp))
                                if (log?.httpStatus != null) {
                                    Text("  HTTP ${log!!.httpStatus}  ${log!!.latencyMs ?: 0}ms", style = MaterialTheme.typography.labelSmall, color = TextDim)
                                }
                            }
                        }
                        log?.note?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = Amber)
                        }
                        if (log != null && log!!.id > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (expanded[log!!.id] == true) "▾ raw" else "▸ raw",
                                style = MaterialTheme.typography.labelSmall,
                                color = Cyan,
                                modifier = Modifier.clickableNoRipple {
                                    expanded[log!!.id] = expanded[log!!.id] != true
                                }
                            )
                            if (expanded[log!!.id] == true) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    (log!!.requestHeaders ?: "") + "\n\n" + (log!!.responseHeaders ?: "") + "\n\n" + (log!!.rawBody ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextDim
                                )
                            }
                        }
                    }
                }
            }

            run?.let { current ->
                SectionTitle("Run", "idempotency key is reused on retry so nothing can double-debit")
                Panel(border = Hairline) {
                    Column {
                        KeyValue("status", current.status, if (current.status == RunStatus.FAILED.name) Red else Green)
                        KeyValue("idempotency key", current.idempotencyKey, Cyan)
                        KeyValue("badge", current.badge, if (current.badge == Badge.LIVE.name) Green else Amber)
                        KeyValue("last confirmed stage", current.lastConfirmedStage ?: "-", TextDim)
                        KeyValue("stealth score", "${current.stealthScore}/100", if (current.stealthScore >= 70) Green else Amber)
                        current.exitIp?.let { KeyValue("exit", "${it} (${current.exitCountry ?: "?"})", TextDim) }
                        current.summary?.let { KeyValue("summary", it, TextDim) }
                        current.errorText?.let { KeyValue("error", it, Red) }
                        if (current.endedAt != null) {
                            KeyValue(
                                "ended",
                                SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()).format(Date(current.endedAt!!)),
                                TextDim
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TerminalButton("trace audit", { onTrace(current.id) }, color = Cyan)
                            TerminalButton("raw console", { onConsole(current.id) }, color = TextDim)
                        }
                    }
                }
            }

            runId?.let { Pill("run #$it", TextDim) }
            Spacer(Modifier.height(24.dp))
        }
    }
}
