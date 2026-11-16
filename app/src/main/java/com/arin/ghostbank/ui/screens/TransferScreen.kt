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
import com.arin.ghostbank.data.db.StageLogEntity
import com.arin.ghostbank.model.Badge
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
import com.arin.ghostbank.ui.components.clickableNoRipple
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Hairline
import com.arin.ghostbank.ui.theme.InkCard
import com.arin.ghostbank.ui.theme.Red
import com.arin.ghostbank.ui.theme.TextDim
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
    val source by vm.source.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val run by vm.run.collectAsStateWithLifecycle()
    val stages by vm.stages.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val keysLeft by vm.keysLeft.collectAsStateWithLifecycle()
    val destination by vm.destination.collectAsStateWithLifecycle()
    var confirmCancel by remember { mutableStateOf(false) }
    var openStage by remember { mutableStateOf<Long?>(null) }

    if (confirmCancel) {
        ConfirmDialog(
            title = "Cancel this run?",
            body = "The run stops at its last confirmed stage and stays in the ledger. The idempotency key is kept, so a retry can never double-debit.",
            confirm = "cancel the run",
            onConfirm = vm::cancel,
            onDismiss = { confirmCancel = false }
        )
    }

    ScreenColumn {
        BackBar(bank?.name ?: "UNKNOWN BANK", bank?.host, onBack)

        if (bank == null) {
            Text("That bank code is not in the list.", style = MaterialTheme.typography.bodySmall, color = Red)
            return@ScreenColumn
        }

        if (destination == null) {
            Panel(border = Amber) {
                Column {
                    Text("No destination saved", style = MaterialTheme.typography.bodyMedium, color = Amber)
                    Spacer(Modifier.height(8.dp))
                    TerminalButton("SAVE DESTINATION", onDestination, color = Amber)
                }
            }
        }

        SectionTitle("MANUAL TRANSFER", bank.quirk)

        TerminalField(
            "AMOUNT (₹1 – ₹${bank.capPaise / 100})",
            amount,
            vm::onAmount,
            numeric = true,
            placeholder = "e.g. 250"
        )
        if (error != null) Text(error!!, style = MaterialTheme.typography.bodySmall, color = Red)

        Text("SOURCE", style = MaterialTheme.typography.labelSmall, color = TextDim)
        REMITTER_PROFILES.forEachIndexed { index, profile ->
            val selected = index == source
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(5.dp))
                    .background(InkCard)
                    .border(1.dp, if (selected) Green else Hairline, RoundedCornerShape(5.dp))
                    .clickableNoRipple { vm.onSource(index) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (selected) "[x]" else "[ ]", style = MaterialTheme.typography.bodySmall, color = if (selected) Green else TextDim)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(profile.name, style = MaterialTheme.typography.bodySmall)
                    Text("${profile.accountNumber} · ${profile.ifsc}", style = MaterialTheme.typography.labelSmall, color = TextDim)
                }
            }
        }

        Panel {
            Column {
                KeyValue("rail", "IMPS P2A, real time 24x7")
                KeyValue("velocity cap", "${settings.maxRequestsPerMinute} req/min")
                KeyValue("entropy budget", "$keysLeft requests left")
                Spacer(Modifier.height(6.dp))
                Meter(
                    fraction = keysLeft.toFloat() / settings.entropyBudget.coerceAtLeast(1),
                    color = if (keysLeft > settings.entropyBudget / 3) Green else Amber
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TerminalButton(
                "RUN PIPELINE",
                vm::run,
                enabled = run?.status != RunStatus.RUNNING.name,
                modifier = Modifier.fillMaxWidth()
            )
            if (run?.status == RunStatus.RUNNING.name) {
                TerminalButton("CANCEL", { confirmCancel = true }, color = Red)
            }
        }

        run?.let { current ->
            Panel(border = when (current.status) {
                RunStatus.COMPLETED.name -> Green
                RunStatus.FAILED.name -> Red
                RunStatus.PAUSED.name -> Amber
                else -> Hairline
            }) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("RUN #${current.id}", style = MaterialTheme.typography.titleSmall)
                        BadgeChip(if (current.badge == Badge.LIVE.name) Badge.LIVE else Badge.SIMULATED)
                    }
                    Spacer(Modifier.height(6.dp))
                    KeyValue("status", current.status)
                    KeyValue("idempotency key", current.idempotencyKey)
                    KeyValue("stealth score", current.stealthScore.toString())
                    current.lastConfirmedStage?.let { KeyValue("last confirmed stage", it, Cyan) }
                    current.summary?.let { KeyValue("summary", it) }
                    current.errorText?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Red)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (current.status) {
                            RunStatus.FAILED.name -> TerminalButton("RETRY (same key)", vm::retry, color = Amber)
                            RunStatus.PAUSED.name -> TerminalButton("RESUME ON FRESH EXIT", vm::resume, color = Amber)
                        }
                        TerminalButton("RAW CONSOLE", { onConsole(current.id) }, color = Cyan)
                        TerminalButton("TRACE AUDIT", { onTrace(current.id) }, color = Cyan)
                    }
                }
            }
        }

        SectionTitle("PIPELINE", "a stage can only advance on a server answer")

        StageKind.entries.forEach { kind ->
            val reached = stages.filter { it.stage == kind.name }
            val last = reached.lastOrNull()
            val done = reached.isNotEmpty() && last?.httpStatus != null
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (done) "[x]" else if (reached.isNotEmpty()) "[~]" else "[ ]",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (done) Green else if (reached.isNotEmpty()) Amber else TextDim
                )
                Spacer(Modifier.width(8.dp))
                Text(kind.name, style = MaterialTheme.typography.bodySmall, color = if (done) Green else TextDim)
                Spacer(Modifier.width(8.dp))
                last?.let {
                    Text("${it.httpStatus ?: "-"} · ${it.latencyMs ?: 0}ms", style = MaterialTheme.typography.labelSmall, color = TextDim)
                    Spacer(Modifier.width(6.dp))
                    BadgeChip(if (it.badge == Badge.LIVE.name) Badge.LIVE else Badge.SIMULATED)
                }
            }
            reached.forEach { log ->
                val open = openStage == log.id
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(InkCard)
                        .clickableNoRipple { openStage = if (open) null else log.id }
                        .padding(8.dp)
                ) {
                    Text(
                        (log.rawBody ?: log.note ?: "no body").take(if (open) 4000 else 120),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDim
                    )
                }
            }
        }

        if (stages.isEmpty()) {
            Text("No run yet. The pipeline will fill in as the bank answers.", style = MaterialTheme.typography.labelSmall, color = TextDim)
        }
    }
}

@Composable
private fun StageLine(log: StageLogEntity) {
    Text(log.stage, style = MaterialTheme.typography.labelSmall, color = TextDim)
}

@Composable
private fun SpacerPill() = Pill("·", TextDim)
