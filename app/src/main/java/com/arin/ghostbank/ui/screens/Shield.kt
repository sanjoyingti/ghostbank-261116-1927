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
import com.arin.ghostbank.data.net.LeakLab
import com.arin.ghostbank.data.net.LeakReport
import com.arin.ghostbank.model.StealthLevel
import com.arin.ghostbank.ui.components.BackBar
import com.arin.ghostbank.ui.components.EmptyState
import com.arin.ghostbank.ui.components.KeyValue
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.Pill
import com.arin.ghostbank.ui.components.SectionTitle
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.levelColor
import com.arin.ghostbank.ui.components.runColor
import com.arin.ghostbank.ui.components.tsText
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Ink
import com.arin.ghostbank.ui.theme.Red
import com.arin.ghostbank.ui.theme.TextDim
import com.arin.ghostbank.ui.theme.TextPrimary
import kotlinx.coroutines.launch

@Composable
fun ShieldScreen(container: AppContainer, onRegistry: () -> Unit, onTrace: (Long) -> Unit, onConsole: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    val settings by container.settings.flow.collectAsStateWithLifecycle(Settings())
    val runs by container.runs.recent().collectAsStateWithLifecycle(emptyList())
    var report by remember { mutableStateOf<LeakReport?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Ink).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Panel {
            Column {
                Text("SHIELD", style = MaterialTheme.typography.titleSmall, color = Green)
                KeyValue("stealth level", settings.stealthLevel.name, levelColor(settings.stealthLevel))
                KeyValue("exit", if (settings.torMode) "Tor 127.0.0.1:9050" else (settings.lastExitIp ?: "direct (no exit)"), Cyan)
                KeyValue("kill-switch", if (settings.torMode || settings.stealthLevel != StealthLevel.STANDARD) "armed" else "not armed", Amber)
                KeyValue("DoH resolver", settings.dohResolver, TextDim)
                KeyValue("velocity", "${settings.maxRequestsPerMinute}/min · ${settings.maxAccountsPerHour}/h", TextDim)
                KeyValue("entropy budget", "${settings.entropyBudget} requests left per run", TextDim)
            }
        }

        TerminalButton(if (busy) "PROBING…" else "RUN LEAK LAB", {
            scope.launch {
                busy = true
                report = container.leakLab.probe(settings)
                busy = false
            }
        }, enabled = !busy, color = Cyan)

        report?.let { r ->
            Panel(border = if (r.passed) Green.copy(alpha = 0.5f) else Red.copy(alpha = 0.5f)) {
                Column {
                    Text(if (r.passed) "EXIT LOOKS CLEAN" else "LEAK DETECTED", style = MaterialTheme.typography.labelSmall, color = if (r.passed) Green else Red)
                    r.lines.forEach { line ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text("•", color = if (line.hidden) Green else Amber)
                            Spacer(Modifier.width(6.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${line.label}: ${line.value}", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                Text(line.note, style = MaterialTheme.typography.labelSmall, color = TextDim)
                            }
                            Pill(if (line.hidden) "HIDDEN" else "LEAKS", if (line.hidden) Green else Amber)
                        }
                    }
                    r.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Red) }
                    Spacer(Modifier.height(6.dp))
                    Text("Ghost level blocks a run when this fails.", style = MaterialTheme.typography.labelSmall, color = TextDim)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TerminalButton("DEDUPE REGISTRY", onRegistry, color = Cyan)
            TerminalButton("VERDICT ${LeakLab::class.simpleName?.take(6) ?: ""}", { }, enabled = false, color = TextDim)
        }

        SectionTitle("recent runs", "a stuck run stays visible at its last confirmed stage")
        if (runs.isEmpty()) {
            EmptyState("No runs yet.", "open a bank and run the pipeline", null)
        } else {
            runs.take(20).forEach { run ->
                Panel(border = runColor(run.status).copy(alpha = 0.35f)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Pill(run.status, runColor(run.status))
                            Spacer(Modifier.width(8.dp))
                            Text("#${run.id} ${run.bankCode} ${run.mode}", style = MaterialTheme.typography.bodySmall, color = TextPrimary, modifier = Modifier.weight(1f))
                            Text(run.stealthScore.toString(), style = MaterialTheme.typography.titleSmall, color = if (run.stealthScore >= 80) Green else Amber)
                        }
                        Text(tsText(run.startedAt) + " · last stage " + (run.lastConfirmedStage ?: "none"), style = MaterialTheme.typography.labelSmall, color = TextDim)
                        run.summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextDim) }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TerminalButton("CONSOLE", { onConsole(run.id) }, color = Cyan)
                            TerminalButton("TRACE", { onTrace(run.id) }, color = Amber)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun TraceAuditScreen(container: AppContainer, runId: Long, onBack: () -> Unit) {
    val run by container.runs.byId(runId).collectAsStateWithLifecycle(null)
    val ledger by container.ledger.byRun(runId).collectAsStateWithLifecycle(emptyList())
    val score by container.db.scores().byRun(runId).collectAsStateWithLifecycle(null)

    Column(Modifier.fillMaxSize().background(Ink)) {
        BackBar("Trace audit", "run #$runId — what actually leaked", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            run?.let { r ->
                Panel {
                    Column {
                        KeyValue("ip used", r.exitIp ?: "device address (no exit)", if (r.exitIp == null) Amber else Green)
                        KeyValue("exit country", r.exitCountry ?: "IN (device)", if (r.exitCountry == null || r.exitCountry == "IN") Green else Amber)
                        KeyValue("cookies persisted", "${container.http.cookiesHeld()} in session memory, none on disk", Green)
                        KeyValue("GhostBank requests to the bank", "1 probe and 1 step per stage", Green)
                        KeyValue("digit exposure", "last 4 only in the ledger; full number never shown again", Amber)
                        KeyValue("source bank sees", "the debited account of the source profile", Amber)
                        KeyValue("destination bank sees", "your account number — always", Amber)
                    }
                }
                Panel {
                    Column {
                        Text("UTR CHAIN", style = MaterialTheme.typography.labelSmall, color = Cyan)
                        if (ledger.isEmpty()) Text("no rows booked", style = MaterialTheme.typography.bodySmall, color = TextDim)
                        ledger.forEach { row ->
                            Text("${row.direction} ${row.utr ?: "-"} ${row.status}", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                        }
                    }
                }
                score?.let { s ->
                    Panel(border = Amber.copy(alpha = 0.4f)) {
                        Column {
                            Text("STEALTH SCORE ${s.score}", style = MaterialTheme.typography.titleSmall, color = if (s.score >= 80) Green else Amber)
                            Text(s.reasons, style = MaterialTheme.typography.bodySmall, color = TextDim)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Plain English: this run " + if (s.score >= 80) "hid the IP, kept the fingerprint browser-shaped and touched each account once — the bank still knows the destination number, and that is unavoidable." else "leaked more than it should — read the reasons above before the next run.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                        }
                    }
                }
            } ?: EmptyState("That run is gone.", "go back", onBack)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun RegistryScreen(container: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val rows by (if (query.isBlank()) container.registry.all() else container.registry.search(query)).collectAsStateWithLifecycle(emptyList())

    Column(Modifier.fillMaxSize().background(Ink)) {
        BackBar("Dedupe registry", "one account, one paisa, ever", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            com.arin.ghostbank.ui.components.TerminalField("SEARCH", query, { query = it }, placeholder = "account or IFSC")
            TerminalButton("WIPE REGISTRY", { scope.launch { container.registry.wipe() } }, color = Red)
            if (rows.isEmpty()) {
                EmptyState("Nothing matches.", "clear the search", { query = "" })
            } else {
                rows.forEach { entry ->
                    Panel {
                        Column {
                            Text("${entry.accountNumber} · ${entry.ifsc}", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                            Text("${entry.status} · ${entry.amountPaise} paise · ${tsText(entry.lastAttempt)}", style = MaterialTheme.typography.labelSmall, color = TextDim)
                            entry.utr?.let { Text("UTR $it", style = MaterialTheme.typography.labelSmall, color = Green) }
                            entry.reason?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Red) }
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TerminalButton("FORCE RE-HARVEST", { scope.launch { container.registry.forceReset(entry.id) } }, color = Amber)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
