=== ABOUT: The transfer screen: amount, source, a big RUN button and the live seven-stage pipeline with raw answers ===
package com.arin.ghostbank.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.model.Badge
import com.arin.ghostbank.model.Money
import com.arin.ghostbank.model.REMITTER_PROFILES
import com.arin.ghostbank.model.RunStatus
import com.arin.ghostbank.model.StageKind
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
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Hairline
import com.arin.ghostbank.ui.theme.InkCard
import com.arin.ghostbank.ui.theme.Red
import com.arin.ghostbank.ui.theme.TextDim
import com.arin.ghostbank.ui.theme.TextPrimary
import com.arin.ghostbank.ui.vm.TransferViewModel

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
    val bank = vm.bank
    val amount by vm.amount.collectAsStateWithLifecycle()
    val amountError by vm.amountError.collectAsStateWithLifecycle()
    val sourceIndex by vm.sourceIndex.collectAsStateWithLifecycle()
    val run by vm.run.collectAsStateWithLifecycle()
    val stages by vm.stages.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val destination by vm.destination.collectAsStateWithLifecycle()
    val blocked by vm.blocked.collectAsStateWithLifecycle()
    val level by vm.stealthLevel.collectAsStateWithLifecycle()
    var confirmCancel by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    Column {
        BackBar(bank?.name ?: bankCode, bank?.quirk, onBack)
        ScreenColumn {
            if (bank == null) {
                Text("unknown bank code", style = MaterialTheme.typography.bodyMedium, color = Red)
                return@ScreenColumn
            }

            if (destination == null) {
                Panel(border = Amber) {
                    Column {
                        Text("no destination yet", style = MaterialTheme.typography.titleSmall, color = Amber)
                        Spacer(Modifier.height(4.dp))
                        Text("Set your account + IFSC first — every leg credits it.", style = MaterialTheme.typography.bodySmall, color = TextDim)
                        Spacer(Modifier.height(8.dp))
                        TerminalButton("SET DESTINATION", onDestination)
                    }
                }
            }

            SectionTitle("amount", "₹1 up to the ${Money.format(bank.capPaise)} per-transaction cap")
            TerminalField("amount in ₹", amount, vm::setAmount, numeric = true, error = amountError, placeholder = "1")

            SectionTitle("source", "the remitter profile the debit is pulled from")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                REMITTER_PROFILES.forEachIndexed { index, profile ->
                    val selected = index == sourceIndex
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (selected) Green.copy(alpha = 0.1f) else InkCard)
                            .border(1.dp, if (selected) Green else Hairline, RoundedCornerShape(5.dp))
                            .clickable { vm.setSource(index) }
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(profile.name, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                            Text("${profile.accountNumber} • ${profile.ifsc}", style = MaterialTheme.typography.labelSmall, color = TextDim)
                        }
                    }
                }
            }

            blocked?.let {
                Panel(border = Red) { Text(it, style = MaterialTheme.typography.bodySmall, color = Red) }
            }

            TerminalButton(
                when {
                    busy -> "RUNNING…"
                    run?.status == RunStatus.QUEUED.name -> "SEND QUEUED RUN"
                    else -> "RUN TRANSFER"
                },
                vm::start,
                enabled = !busy && destination != null,
                color = Green,
                modifier = Modifier.fillMaxWidth()
            )

            run?.let { current ->
                Panel(border = when (current.status) {
                    RunStatus.COMPLETED.name -> Green
                    RunStatus.FAILED.name, RunStatus.CANCELLED.name -> Red
                    RunStatus.PAUSED.name, RunStatus.QUEUED.name -> Amber
                    else -> Cyan
                }) {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("run #${current.id}", style = MaterialTheme.typography.labelSmall, color = TextDim)
                            BadgeChip(if (current.badge == Badge.LIVE.name) Badge.LIVE else Badge.SIMULATED)
                        }
                        Spacer(Modifier.height(6.dp))
                        KeyValue("status", current.status, if (current.status == RunStatus.COMPLETED.name) Green else Amber)
                        KeyValue("idempotency key", current.idempotencyKey.take(16) + "…", TextDim)
                        KeyValue("amount", Money.format(current.amountPaise))
                        KeyValue("last confirmed", current.lastConfirmedStage ?: "none yet", Cyan)
                        current.errorText?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = Red)
                        }
                        current.summary?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = TextDim)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TerminalButton("RAW CONSOLE", { onConsole(current.id) }, color = Cyan)
                            TerminalButton("TRACE AUDIT", { onTrace(current.id) }, color = Cyan)
                            if (current.status == RunStatus.FAILED.name || current.status == RunStatus.PAUSED.name) {
                                TerminalButton("RETRY (same key)", vm::retry, color = Amber)
                            }
                            if (current.status == RunStatus.RUNNING.name) {
                                TerminalButton("CANCEL", { confirmCancel = true }, color = Red)
                            }
                        }
                    }
                }
            }

            SectionTitle("pipeline", "a stage cannot advance without a server confirmation")
            StageKind.values().forEach { kind ->
                val logs = stages.filter { it.stage == kind.name }
                val done = logs.isNotEmpty()
                val last = logs.lastOrNull()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(InkCard)
                        .padding(10.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "${if (done) "✓" else "·"} ${kind.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (done) Green else TextDim
                        )
                        last?.let {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text("${it.latencyMs ?: 0}ms ", style = MaterialTheme.typography.labelSmall, color = TextDim)
                                Pill("HTTP ${it.httpStatus ?: "—"}", if (it.httpStatus in 200..399) Green else Amber)
                            }
                        }
                    }
                    if (last != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            last.note ?: (last.rawBody?.take(160) ?: "confirmation recorded"),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextDim
                        )
                        if (last.rawBody != null) {
                            var open by remember(last.id) { mutableStateOf(false) }
                            Text(
                                if (open) "hide raw" else "raw response ▾",
                                style = MaterialTheme.typography.labelSmall,
                                color = Cyan,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clickable { open = !open }
                            )
                            AnimatedVisibility(open) {
                                Column {
                                    Text(
                                        last.rawBody.orEmpty().take(1200),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextDim,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f))
                                            .padding(6.dp)
                                    )
                                    Text(
                                        "copy",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Cyan,
                                        modifier = Modifier
                                            .padding(top = 3.dp)
                                            .clickable {
                                                clipboard.setText(AnnotatedString(last.rawBody.orEmpty()))
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text("entropy budget left", style = MaterialTheme.typography.labelSmall, color = TextDim)
            Spacer(Modifier.height(4.dp))
            Meter(fraction = (1f - (stages.size / 60f)).coerceIn(0f, 1f), color = if (level.name == "GHOST") Green else Cyan)

            if (confirmCancel) {
                ConfirmDialog(
                    title = "cancel this run?",
                    body = "The pipeline stops at its last confirmed stage. Nothing is resent — a retry reuses the same idempotency key.",
                    confirm = "cancel run",
                    onConfirm = vm::cancel,
                    onDismiss = { confirmCancel = false }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
