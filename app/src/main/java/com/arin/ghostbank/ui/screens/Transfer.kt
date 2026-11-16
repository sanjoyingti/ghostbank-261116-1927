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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.Settings
import com.arin.ghostbank.data.db.RunEntity
import com.arin.ghostbank.data.db.StageLogEntity
import com.arin.ghostbank.model.Badge
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.REMITTER_PROFILES
import com.arin.ghostbank.model.RemitterProfile
import com.arin.ghostbank.model.RemitterProfile as _Unused
import com.arin.ghostbank.model.RunMode
import com.arin.ghostbank.model.StageKind
import com.arin.ghostbank.ui.components.BadgeChip
import com.arin.ghostbank.ui.components.BackBar
import com.arin.ghostbank.ui.components.ConfirmDialog
import com.arin.ghostbank.ui.components.EmptyState
import com.arin.ghostbank.ui.components.KeyValue
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.Pill
import com.arin.ghostbank.ui.components.SectionTitle
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.TerminalField
import com.arin.ghostbank.ui.components.badgeFor
import com.arin.ghostbank.ui.components.paiseOf
import com.arin.ghostbank.ui.components.rupeesText
import com.arin.ghostbank.ui.components.runColor
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

@Composable
fun TransferScreen(
    container: AppContainer,
    bankCode: String,
    onBack: () -> Unit,
    onDestination: () -> Unit,
    onConsole: (Long) -> Unit,
    onTrace: (Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    val bank = remember(bankCode) { Banks.byCode(bankCode) }
    val settings by container.settings.flow.collectAsStateWithLifecycle(Settings())
    val destination by container.accounts.destination().collectAsStateWithLifecycle(null)

    var amountText by remember { mutableStateOf("") }
    var source by remember { mutableStateOf(REMITTER_PROFILES.first()) }
    var runId by remember { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }
    var askCancel by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<Long?>(null) }

    val runFlow: Flow<RunEntity?> = remember(runId) {
        runId?.let { container.runs.byId(it) } ?: flowOf(null)
    }
    val run by runFlow.collectAsStateWithLifecycle(null)
    val logsFlow: Flow<List<StageLogEntity>> = remember(runId) {
        runId?.let { container.runs.logs(it) } ?: flowOf(emptyList())
    }
    val logs by logsFlow.collectAsStateWithLifecycle(emptyList())

    val amountPaise = paiseOf(amountText)
    val amountError = when {
        amountText.isBlank() -> null
        amountPaise == null -> "not a number"
        amountPaise < 100L -> "₹1 minimum"
        bank != null && amountPaise > bank.capPaise -> "over this bank's cap"
        else -> null
    }

    Column(Modifier.fillMaxSize().background(Ink)) {
        BackBar(bank?.name ?: "Bank", "amount, source, then run the pipeline", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (bank == null) {
                EmptyState("That bank code is unknown.", "go back", onBack)
                return@Column
            }

            Panel {
                Column {
                    KeyValue("rail", "IMPS P2A (account + IFSC), real time 24x7", Cyan)
                    KeyValue("per-transaction cap", rupeesText(bank.capPaise), Amber)
                    KeyValue("quirk", bank.quirk, TextDim)
                    KeyValue("velocity", "${settings.maxRequestsPerMinute}/min · ${settings.maxAccountsPerHour}/h", TextDim)
                    KeyValue("entropy budget", "${settings.entropyBudget} requests per run", TextDim)
                }
            }

            if (destination == null) {
                Panel(border = Amber.copy(alpha = 0.5f)) {
                    Column {
                        Text("NO DESTINATION", style = MaterialTheme.typography.labelSmall, color = Amber)
                        Text("Save the account the money should land in first.", style = MaterialTheme.typography.bodySmall, color = TextDim)
                        Spacer(Modifier.height(8.dp))
                        TerminalButton("ADD DESTINATION", onDestination, color = Amber)
                    }
                }
            } else {
                KeyValue("lands in", destination!!.number.let { com.arin.ghostbank.model.Ids.maskNumber(it) } + " · " + destination!!.ifsc, Green)
            }

            TerminalField("AMOUNT IN RUPEES", amountText, { amountText = it.filter { c -> c.isDigit() || c == '.' }.take(12) }, placeholder = "1", error = amountError, numeric = true)
            TerminalField("SOURCE", "${source.name} — ${source.accountNumber}", { }, enabled = false)

            SectionTitle("source route", "a saved remitter profile, or the bank's own P2A route")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                REMITTER_PROFILES.forEach { profile ->
                    val selected = profile == source
                    Text(
                        profile.name.substringBefore(" –"),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) Green else TextDim,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (selected) Green.copy(alpha = 0.12f) else Ink)
                            .clickable { source = profile }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }

            val canRun = !busy && destination != null && amountError == null && amountPaise != null
            TerminalButton(if (busy) "RUNNING…" else "RUN PIPELINE", {
                val paise = amountPaise ?: return@TerminalButton
                scope.launch {
                    busy = true
                    val id = container.engine.createTransferRun(bank, paise, RunMode.TRANSFER, settings.stealthLevel)
                    runId = id
                    container.engine.performTransferRun(id, settings, source)
                    busy = false
                }
            }, enabled = canRun)

            runId?.let { id ->
                SectionTitle("pipeline", "a stage only ticks when the server confirmed it")
                StageKind.entries.forEach { kind ->
                    val rows = logs.filter { it.stage == kind.name }
                    val last = rows.lastOrNull()
                    val ticked = last != null && last.badge == Badge.LIVE.name
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (ticked) Green.copy(alpha = 0.07f) else Ink)
                            .padding(8.dp)
                            .clickable { expanded = if (expanded == last?.id) null else last?.id }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (ticked) "✓" else "·", style = MaterialTheme.typography.titleSmall, color = if (ticked) Green else TextDim)
                            Spacer(Modifier.width(8.dp))
                            Text(kind.name, style = MaterialTheme.typography.bodyMedium, color = if (ticked) TextPrimary else TextDim, modifier = Modifier.weight(1f))
                            last?.let {
                                Text("${it.httpStatus ?: "-"} · ${it.latencyMs ?: 0}ms", style = MaterialTheme.typography.labelSmall, color = TextDim)
                                Spacer(Modifier.width(6.dp))
                                BadgeChip(badgeFor(it.badge))
                            }
                        }
                        last?.note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Amber) }
                        if (expanded == last?.id && last != null) {
                            Spacer(Modifier.height(6.dp))
                            Text("REQUEST", style = MaterialTheme.typography.labelSmall, color = Cyan)
                            Text(last.requestHeaders.orEmpty(), style = MaterialTheme.typography.bodySmall, color = TextDim)
                            Text("RESPONSE", style = MaterialTheme.typography.labelSmall, color = Cyan)
                            Text(last.responseHeaders.orEmpty(), style = MaterialTheme.typography.bodySmall, color = TextDim)
                            Text(last.rawBody.orEmpty().take(900), style = MaterialTheme.typography.bodySmall, color = TextDim)
                        }
                    }
                }

                run?.let { r ->
                    Panel(border = runColor(r.status).copy(alpha = 0.5f)) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Pill(r.status, runColor(r.status))
                                Spacer(Modifier.width(8.dp))
                                BadgeChip(badgeFor(r.badge))
                                Spacer(Modifier.width(8.dp))
                                Text("#${r.id} · ${rupeesText(r.amountPaise)}", style = MaterialTheme.typography.labelSmall, color = TextDim)
                            }
                            Spacer(Modifier.height(6.dp))
                            KeyValue("idempotency key", r.idempotencyKey, Cyan)
                            KeyValue("last confirmed stage", r.lastConfirmedStage ?: "none", TextDim)
                            r.summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (r.status == "COMPLETED") Green else Amber) }
                            r.errorText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Red) }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TerminalButton("CONSOLE", { onConsole(r.id) }, color = Cyan)
                                TerminalButton("TRACE", { onTrace(r.id) }, color = Cyan)
                                TerminalButton("RETRY", {
                                    scope.launch {
                                        busy = true
                                        container.engine.retryTransfer(r.id, settings, source)
                                        busy = false
                                    }
                                }, enabled = !busy && r.status != "RUNNING", color = Amber)
                                if (r.status == "RUNNING" || r.status == "PAUSED") {
                                    TerminalButton("CANCEL", { askCancel = true }, color = Red)
                                }
                            }
                        }
                    }
                }
            }

            if (askCancel && runId != null) {
                ConfirmDialog(
                    title = "Cancel this run?",
                    body = "The run stops at its last confirmed stage. The ledger keeps the debit row and anything already booked stays booked.",
                    confirm = "cancel the run",
                    onConfirm = { scope.launch { container.engine.cancel(runId!!) } },
                    onDismiss = { askCancel = false }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
