=== ABOUT: Fixed the missing transfer screen: amount, source, one big run button, the seven stages with confirmations, timings and raw responses ===
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.Settings
import com.arin.ghostbank.data.db.RunEntity
import com.arin.ghostbank.data.db.StageLogEntity
import com.arin.ghostbank.model.Bank
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.REMITTER_PROFILES
import com.arin.ghostbank.model.RunMode
import com.arin.ghostbank.model.RunStatus
import com.arin.ghostbank.model.StageKind
import com.arin.ghostbank.ui.components.BackBar
import com.arin.ghostbank.ui.components.EmptyState
import com.arin.ghostbank.ui.components.KeyValue
import com.arin.ghostbank.ui.components.Meter
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.Pill
import com.arin.ghostbank.ui.components.ScreenColumn
import com.arin.ghostbank.ui.components.SectionTitle
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.TerminalField
import com.arin.ghostbank.ui.components.clickableNoRipple
import com.arin.ghostbank.ui.components.paiseToCompact
import com.arin.ghostbank.ui.components.rupeesToPaise
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Hairline
import com.arin.ghostbank.ui.theme.Red
import com.arin.ghostbank.ui.theme.TextDim
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TransferViewModel(private val container: AppContainer, private val bank: Bank) : ViewModel() {

    data class Ui(
        val amount: String = "1",
        val harvest: Boolean = true,
        val sourceIndex: Int = 0,
        val busy: Boolean = false,
        val problem: String? = null
    )

    private val _ui = MutableStateFlow(Ui())
    val state: StateFlow<Ui> = _ui
    private val runId = MutableStateFlow<Long?>(null)
    private var job: Job? = null

    val run: StateFlow<RunEntity?> = runId
        .flatMapLatest { id -> if (id == null) flowOf(null) else container.runs.byId(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val logs: StateFlow<List<StageLogEntity>> = runId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else container.runs.logs(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setAmount(value: String) {
        _ui.value = _ui.value.copy(amount = value.filter { it.isDigit() || it == '.' }.take(10), problem = null)
    }

    fun setHarvest(on: Boolean) = _ui.value = _ui.value.copy(harvest = on)
    fun setSource(index: Int) = _ui.value = _ui.value.copy(sourceIndex = index)

    private fun amountPaise(): Long? {
        val ui = _ui.value
        if (ui.harvest) return 1L
        val paise = rupeesToPaise(ui.amount) ?: return null
        if (paise < 100) return null
        if (paise > bank.capPaise) return null
        return paise
    }

    fun run() {
        val ui = _ui.value
        val paise = amountPaise()
        val problem = when {
            paise == null && ui.harvest -> "amount must be ₹1 or more"
            paise == null -> "amount must be between ₹1 and ${paiseToCompact(bank.capPaise)}"
            else -> null
        }
        if (problem != null) {
            _ui.value = ui.copy(problem = problem)
            return
        }
        val source = REMITTER_PROFILES[ui.sourceIndex.coerceIn(0, REMITTER_PROFILES.lastIndex)]
        _ui.value = ui.copy(busy = true, problem = null)
        job?.cancel()
        job = viewModelScope.launch {
            val settings = container.settings.flow.first()
            val id = container.engine.createTransferRun(
                bank = bank,
                amountPaise = paise!!,
                mode = if (ui.harvest) RunMode.HARVEST else RunMode.TRANSFER,
                level = settings.stealthLevel
            )
            runId.value = id
            container.engine.performTransferRun(id, settings, source)
            _ui.value = _ui.value.copy(busy = false)
        }
    }

    fun cancel() {
        val id = runId.value ?: return
        viewModelScope.launch { container.engine.cancel(id); _ui.value = _ui.value.copy(busy = false) }
    }

    fun retry() {
        val id = runId.value ?: return
        val source = REMITTER_PROFILES[_ui.value.sourceIndex.coerceIn(0, REMITTER_PROFILES.lastIndex)]
        _ui.value = _ui.value.copy(busy = true)
        job?.cancel()
        job = viewModelScope.launch {
            val settings = container.settings.flow.first()
            container.engine.retryTransfer(id, settings, source)
            _ui.value = _ui.value.copy(busy = false)
        }
    }

    companion object {
        fun factory(container: AppContainer, bank: Bank): ViewModelProvider.Factory = viewModelFactory {
            initializer { TransferViewModel(container, bank) }
        }
    }
}

@Composable
fun TransferScreen(
    container: AppContainer,
    bankCode: String,
    onBack: () -> Unit,
    onDestination: () -> Unit,
    onConsole: (Long) -> Unit,
    onTrace: (Long) -> Unit
) {
    val bank = Banks.byCode(bankCode)
    if (bank == null) {
        ScreenColumn {
            BackBar("Unknown bank", null, onBack)
            EmptyState("That bank code is not in the 27 — go back and pick one", "back", onBack)
        }
        return
    }
    val vm: TransferViewModel = viewModel(factory = TransferViewModel.factory(container, bank))
    val state by vm.state.collectAsStateWithLifecycle()
    val run by vm.run.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val destination by container.accounts.destination().collectAsStateWithLifecycle(initialValue = null)
    val settings by container.settings.flow.collectAsStateWithLifecycle(initialValue = Settings())
    var openStage by remember { mutableStateOf<String?>(null) }

    ScreenColumn {
        BackBar(bank.name, "${bank.group.name} · cap ${paiseToCompact(bank.capPaise)}", onBack)
        Panel(border = Amber) {
            Column {
                Text("RAIL QUIRK", style = MaterialTheme.typography.labelSmall, color = Amber)
                Text(bank.quirk, style = MaterialTheme.typography.bodySmall, color = TextDim)
                Spacer(Modifier.height(4.dp))
                Row {
                    Pill(if (bank.impsSupported) "IMPS P2A" else "NO IMPS", if (bank.impsSupported) Green else Red)
                    Spacer(Modifier.width(6.dp))
                    Pill("24x7 real time", Cyan)
                }
            }
        }

        if (destination == null) {
            Panel(border = Red) {
                Column {
                    Text("No destination saved — nothing can be credited", style = MaterialTheme.typography.bodySmall, color = Red)
                    Spacer(Modifier.height(6.dp))
                    TerminalButton("set destination", onDestination, color = Amber)
                }
            }
        }

        KeyValue("velocity cap", "${settings.maxRequestsPerMinute} req/min", Cyan)
        KeyValue("entropy budget", "${settings.entropyBudget} requests this run", Cyan)
        KeyValue("stealth", "${settings.stealthLevel.name}${if (settings.torMode) " · Tor" else ""}", Green)

        TerminalButton(
            if (state.harvest) "harvest mode: amount locked to ₹0.01" else "manual amount",
            { vm.setHarvest(!state.harvest) },
            color = if (state.harvest) Green else TextDim
        )
        if (!state.harvest) {
            TerminalField("AMOUNT (₹)", state.amount, vm::setAmount, numeric = true, error = state.problem)
        }

        SectionTitle("Source", "a saved remitter profile, or the bank's own P2A route")
        REMITTER_PROFILES.forEachIndexed { index, profile ->
            val selected = state.sourceIndex == index
            Panel(border = if (selected) Green else Hairline) {
                Column(Modifier.fillMaxWidth().clickableNoRipple { vm.setSource(index) }) {
                    Text(profile.name, style = MaterialTheme.typography.bodyMedium, color = if (selected) Green else MaterialTheme.typography.bodyMedium.color)
                    Text("${profile.accountNumber} · ${profile.ifsc}", style = MaterialTheme.typography.labelSmall, color = TextDim)
                }
            }
        }

        TerminalButton(
            if (state.busy) "running…" else if (state.harvest) "RUN HARVEST LEG" else "RUN TRANSFER",
            vm::run,
            enabled = !state.busy && destination != null
        )
        if (state.busy) TerminalButton("cancel run", vm::cancel, color = Red)

        run?.let { current ->
            KeyValue("idempotency key", current.idempotencyKey, Cyan)
            KeyValue("status", current.status, if (current.status == RunStatus.COMPLETED.name) Green else Amber)
            current.errorText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Red) }
            current.summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextDim) }
            Row {
                TerminalButton("raw console", { onConsole(current.id) }, color = Cyan)
                Spacer(Modifier.width(8.dp))
                TerminalButton("trace audit", { onTrace(current.id) }, color = TextDim)
            }
            if (current.status == RunStatus.FAILED.name || current.status == RunStatus.PAUSED.name) {
                TerminalButton("retry with the same key", vm::retry, color = Amber, enabled = !state.busy)
            }
        }

        SectionTitle("Pipeline", "a stage only ticks on a server confirmation")
        StageKind.values().forEach { stage ->
            val last = logs.lastOrNull { it.stage == stage.name }
            val statusColor = when {
                last == null -> TextDim
                last.httpStatus == null -> Amber
                last.httpStatus!! in 200..399 -> Green
                else -> Red
            }
            Panel(border = if (last == null) Hairline else statusColor) {
                Column(Modifier.fillMaxWidth().clickableNoRipple { openStage = if (openStage == stage.name) null else stage.name }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (last != null) "[x]" else "[ ]", style = MaterialTheme.typography.titleSmall, color = statusColor)
                        Spacer(Modifier.width(8.dp))
                        Text(stage.name, style = MaterialTheme.typography.titleSmall, color = if (last == null) TextDim else MaterialTheme.typography.titleSmall.color)
                        Spacer(Modifier.width(8.dp))
                        if (last != null) Pill("${last.latencyMs ?: 0}ms", statusColor)
                    }
                    Text(
                        last?.note ?: last?.let { "HTTP ${it.httpStatus ?: "-"} · ${it.badge}" } ?: "waiting for a confirmed response",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim
                    )
                    if (openStage == stage.name) {
                        Text(last?.requestHeaders ?: "no request recorded", style = MaterialTheme.typography.bodySmall, color = Cyan)
                        Text(last?.responseHeaders ?: "no response headers", style = MaterialTheme.typography.bodySmall, color = TextDim)
                        Text(last?.rawBody?.take(600) ?: "no body", style = MaterialTheme.typography.bodySmall, color = TextDim)
                    }
                }
            }
        }

        val confirmed = logs.count { it.httpStatus != null && it.httpStatus!! in 200..399 }
        Meter(confirmed / 7f, Green)
        Text("$confirmed of 7 stages confirmed · ${settings.entropyBudget} request budget", style = MaterialTheme.typography.labelSmall, color = TextDim)
    }
}
