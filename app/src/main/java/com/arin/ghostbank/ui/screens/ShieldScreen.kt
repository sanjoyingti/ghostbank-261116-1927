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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.Settings
import com.arin.ghostbank.data.db.GhostProfileEntity
import com.arin.ghostbank.data.db.ProxyEntity
import com.arin.ghostbank.data.db.RunEntity
import com.arin.ghostbank.data.db.StealthScoreEntity
import com.arin.ghostbank.data.net.LeakLine
import com.arin.ghostbank.data.net.LeakReport
import com.arin.ghostbank.model.StealthLevel
import com.arin.ghostbank.ui.components.EmptyState
import com.arin.ghostbank.ui.components.KeyValue
import com.arin.ghostbank.ui.components.Meter
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.Pill
import com.arin.ghostbank.ui.components.SectionTitle
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.levelColor
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Hairline
import com.arin.ghostbank.ui.theme.Ink
import com.arin.ghostbank.ui.theme.Red
import com.arin.ghostbank.ui.theme.TextDim
import androidx.compose.ui.Alignment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShieldViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<Settings> =
        container.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    val scores: StateFlow<List<StealthScoreEntity>> = container.db.scores().recent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val runs: StateFlow<List<RunEntity>> = container.runs.recent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val proxies: StateFlow<List<ProxyEntity>> = container.db.proxies().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val profiles: StateFlow<List<GhostProfileEntity>> = container.db.profiles().recent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _report = MutableStateFlow<LeakReport?>(null)
    val report: StateFlow<LeakReport?> = _report.asStateFlow()

    private val _probing = MutableStateFlow(false)
    val probing: StateFlow<Boolean> = _probing.asStateFlow()

    val latestScore: StateFlow<Int> = scores.let { flow ->
        container.db.scores().recent().let { _ -> flow }
    }.let { scores }.let { _ ->
        scores
    }.let { _ ->
        kotlinx.coroutines.flow.MutableStateFlow(0).also { state ->
            viewModelScope.launch {
                container.db.scores().recent().collect { rows -> state.value = rows.firstOrNull()?.score ?: 0 }
            }
        }
    }

    fun probe() {
        if (_probing.value) return
        _probing.value = true
        viewModelScope.launch {
            _report.value = container.leakLab.probe(settings.value)
            _probing.value = false
        }
    }

    fun clearReport() { _report.value = null }

    fun lineColor(hidden: Boolean) = if (hidden) Green else Amber

    fun leaks(report: LeakReport?): List<LeakLine> = report?.lines.orEmpty()

    fun blockedByGhost(): Boolean =
        settings.value.stealthLevel == StealthLevel.GHOST && proxies.value.none { it.healthy } && !settings.value.torMode

    companion object {
        fun factory(container: AppContainer) = viewModelFactory { initializer { ShieldViewModel(container) } }
    }
}

@Composable
fun ShieldScreen(
    container: AppContainer,
    onRegistry: () -> Unit,
    onTrace: (Long) -> Unit,
    onConsole: (Long) -> Unit
) {
    val vm: ShieldViewModel = viewModel(factory = ShieldViewModel.factory(container))
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scores by vm.scores.collectAsStateWithLifecycle()
    val runs by vm.runs.collectAsStateWithLifecycle()
    val report by vm.report.collectAsStateWithLifecycle()
    val probing by vm.probing.collectAsStateWithLifecycle()
    val proxies by vm.proxies.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()

    val score = scores.firstOrNull()?.score ?: 0

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("SHIELD", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Text("What the bank can still see, and what it cannot.", style = MaterialTheme.typography.bodySmall, color = TextDim)

        Spacer(Modifier.height(12.dp))
        Panel(border = Hairline) {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("STEALTH SCORE", style = MaterialTheme.typography.labelSmall, color = TextDim)
                    Pill(settings.stealthLevel.name, levelColor(settings.stealthLevel))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    score.toString().padStart(3, '0'),
                    style = MaterialTheme.typography.displaySmall,
                    color = when {
                        score >= 80 -> Green
                        score >= 50 -> Amber
                        score == 0 -> TextDim
                        else -> Red
                    }
                )
                Spacer(Modifier.height(6.dp))
                Meter(score / 100f, when {
                    score >= 80 -> Green
                    score >= 50 -> Amber
                    else -> Red
                })
                Spacer(Modifier.height(8.dp))
                val reasons = scores.firstOrNull()?.reasons
                if (reasons.isNullOrBlank()) {
                    Text("No scored run yet. The gauge fills after the first leg.", style = MaterialTheme.typography.bodySmall, color = TextDim)
                } else {
                    reasons.lines().forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall, color = if (line.startsWith("-")) Amber else Green)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionTitle("LEAK LAB", "Hits a real IP echo and a TLS fingerprint echo. Nothing is claimed without an answer.")
        Panel(border = if (report?.passed == true) Green else Amber) {
            Column {
                Text(
                    when {
                        settings.stealthLevel == StealthLevel.GHOST && proxies.none { it.healthy } && !settings.torMode ->
                            "Ghost level is on with no exit. No traffic will leave until one is configured."
                        report == null -> "Run the probe to see exactly what a bank sees."
                        report!!.passed -> "Exit is consistent with the device — a Ghost-level run may start."
                        else -> "The probe failed. Fix the exit before a Ghost-level run."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (report?.passed == true) Green else Amber
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TerminalButton(if (probing) "PROBING…" else "RUN PROBE", { vm.probe() }, enabled = !probing, color = Cyan, modifier = Modifier.weight(1f))
                    TerminalButton("CLEAR", { vm.clearReport() }, color = TextDim, modifier = Modifier.weight(1f))
                }
                report?.let { current ->
                    Spacer(Modifier.height(10.dp))
                    current.lines.forEach { line ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (line.hidden) "✓" else "!", style = MaterialTheme.typography.labelLarge, color = vm.lineColor(line.hidden))
                            Spacer(Modifier.padding(start = 6.dp))
                            Column(Modifier.weight(1f)) {
                                KeyValue(line.label, line.value, vm.lineColor(line.hidden))
                                Text(line.note, style = MaterialTheme.typography.labelSmall, color = TextDim)
                            }
                        }
                    }
                    current.error?.let { Text("error: $it", style = MaterialTheme.typography.labelSmall, color = Red) }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionTitle("EXITS", "${proxies.count { it.healthy }} healthy · tor ${if (settings.torMode) "on" else "off"}")
        Panel(border = Hairline) {
            Column {
                KeyValue("stealth level", settings.stealthLevel.name, levelColor(settings.stealthLevel))
                KeyValue("dns-over-https", settings.dohResolver, if (settings.dohResolver.isBlank()) Amber else Green)
                KeyValue("fresh ghost profiles", profiles.count { !it.burned }.toString())
                KeyValue("velocity cap", "${settings.maxRequestsPerMinute}/min · ${settings.maxAccountsPerHour}/h")
                if (proxies.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("No proxy list imported. Tor mode or the Standard level is the only way out.", style = MaterialTheme.typography.bodySmall, color = Amber)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionTitle("REGISTRY", "One account, one paisa, ever")
        TerminalButton("OPEN DEDUPE REGISTRY", onRegistry, color = Cyan, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(16.dp))
        SectionTitle("RUNS", "A stuck run stays visible with its last confirmed stage.")
        if (runs.isEmpty()) {
            EmptyState("No runs recorded yet.", null)
        } else {
            runs.take(12).forEach { row ->
                Panel(border = Hairline, modifier = Modifier.padding(bottom = 6.dp)) {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("run #${row.id} · ${row.bankCode} · ${row.mode}", style = MaterialTheme.typography.labelSmall, color = TextDim)
                            Text(row.status, style = MaterialTheme.typography.labelSmall, color = when (row.status) {
                                "COMPLETED" -> Green
                                "FAILED", "CANCELLED" -> Red
                                "PAUSED" -> Amber
                                else -> Cyan
                            })
                        }
                        Text(
                            "last confirmed: ${row.lastConfirmedStage ?: "—"} · score ${row.stealthScore}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDim
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TerminalButton("CONSOLE", { onConsole(row.id) }, color = Cyan, modifier = Modifier.weight(1f))
                            TerminalButton("TRACE AUDIT", { onTrace(row.id) }, color = Amber, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}
