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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.arin.ghostbank.data.db.RunEntity
import com.arin.ghostbank.data.db.StageLogEntity
import com.arin.ghostbank.model.Badge
import com.arin.ghostbank.model.Bank
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.REMITTER_PROFILES
import com.arin.ghostbank.model.RemitterProfile
import com.arin.ghostbank.model.RunMode
import com.arin.ghostbank.model.RunStatus
import com.arin.ghostbank.model.StageKind
import com.arin.ghostbank.ui.components.BackBar
import com.arin.ghostbank.ui.components.BadgeChip
import com.arin.ghostbank.ui.components.ConfirmDialog
import com.arin.ghostbank.ui.components.EmptyState
import com.arin.ghostbank.ui.components.KeyValue
import com.arin.ghostbank.ui.components.Meter
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.Pill
import com.arin.ghostbank.ui.components.SectionTitle
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.TerminalField
import com.arin.ghostbank.ui.components.clickableNoRipple
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Hairline
import com.arin.ghostbank.ui.theme.Ink
import com.arin.ghostbank.ui.theme.Red
import com.arin.ghostbank.ui.theme.TextDim
import com.arin.ghostbank.ui.theme.TextPrimary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransferViewModel(
    private val container: AppContainer,
    val bank: Bank
) : ViewModel() {

    val sources: List<RemitterProfile> = REMITTER_PROFILES + BankDefaults.railRemitter(bank)

    private val _amount = MutableStateFlow("")
    val amount: StateFlow<String> = _amount.asStateFlow()

    private val _sourceIndex = MutableStateFlow(0)
    val sourceIndex: StateFlow<Int> = _sourceIndex.asStateFlow()

    private val _runId = MutableStateFlow<Long?>(null)
    val runId: StateFlow<Long?> = _runId.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val settings: StateFlow<Settings> =
        container.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    val run: StateFlow<RunEntity?> = _runId
        .flatMapLatest { id -> if (id == null) flowOf(null) else container.db.runs().byId(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val stages: StateFlow<List<StageLogEntity>> = _runId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else container.db.stages().byRun(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setAmount(value: String) {
        _amount.value = value.filter { it.isDigit() || it == '.' }.take(12)
    }

    fun setSource(index: Int) { _sourceIndex.value = index }

    fun amountPaise(): Long {
        val text = _amount.value.trim()
        if (text.isEmpty()) return 0
        val rupees = text.substringBefore('.').toLongOrNull() ?: 0
        val fraction = text.substringAfter('.', "").padEnd(2, '0').take(2).toLongOrNull() ?: 0
        return rupees * 100 + fraction
    }

    fun start() {
        val paise = amountPaise()
        val problem = when {
            paise < 100 -> "The rail minimum for a manual transfer is ₹1"
            paise > bank.capPaise -> "Above this bank's per-transaction cap of ${BankDefaults.rupees(bank.capPaise)}"
            else -> null
        }
        if (problem != null) { _message.value = problem; return }
        if (_busy.value) return
        _busy.value = true
        _message.value = null
        viewModelScope.launch {
            val level = settings.value.stealthLevel
            val id = container.engine.createTransferRun(bank, paise, RunMode.TRANSFER, level)
            _runId.value = id
            container.engine.performTransferRun(id, settings.value, sources[_sourceIndex.value])
            _busy.value = false
        }
    }

    fun retry() {
        val id = _runId.value ?: return
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            container.engine.retryTransfer(id, settings.value, sources[_sourceIndex.value])
            _busy.value = false
        }
    }

    fun resume() {
        val id = _runId.value ?: return
        viewModelScope.launch {
            container.engine.resume(id)
            _busy.value = true
            container.engine.performTransferRun(id, settings.value, sources[_sourceIndex.value])
            _busy.value = false
        }
    }

    fun cancel() {
        val id = _runId.value ?: return
        viewModelScope.launch {
            container.engine.cancel(id)
            _busy.value = false
        }
    }

    companion object {
        fun factory(container: AppContainer, bank: Bank) = viewModelFactory {
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
        Column(Modifier.fillMaxSize().background(Ink).padding(16.dp)) {
            BackBar("UNKNOWN BANK", bankCode, onBack)
            EmptyState("That bank code is not in the list.", "go back", onBack)
        }
        return
    }

    val vm: TransferViewModel = viewModel(key = bank.code, factory = TransferViewModel.factory(container, bank))
    val amount by vm.amount.collectAsStateWithLifecycle()
    val sourceIndex by vm.sourceIndex.collectAsStateWithLifecycle()
    val run by vm.run.collectAsStateWithLifecycle()
    val stages by vm.stages.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val destination by container.accounts.destination().collectAsStateWithLifecycle(initialValue = null)

    var confirmCancel by remember { mutableStateOf(false) }
    var openRaw by remember { mutableStateOf<String?>(null) }

    val paise = vm.amountPaise()
    val used = stages.size
    val remaining = (settings.entropyBudget - used).coerceAtLeast(0)
    val running = run?.status == RunStatus.RUNNING.name && busy

    Column(Modifier.fillMaxSize().background(Ink)) {
        BackBar(bank.name, "${bank.group.name.lowercase()} · cap ${BankDefaults.rupees(bank.capPaise)} · IMPS P2A", onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Panel(border = Hairline) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("ENTROPY BUDGET", style = MaterialTheme.typography.labelSmall, color = TextDim)
                        Text("$remaining / ${settings.entropyBudget} requests left", style = MaterialTheme.typography.labelSmall, color = if (remaining > 20) Green else Amber)
                    }
                    Spacer(Modifier.height(6.dp))
                    Meter(
                        fraction = if (settings.entropyBudget == 0) 0f else remaining.toFloat() / settings.entropyBudget,
                        color = if (remaining > 20) Green else Amber
                    )
                }
            }

            if (destination == null) {
                Spacer(Modifier.height(12.dp))
                Panel(border = Amber) {
                    Column {
                        Text("NO DESTINATION YET", style = MaterialTheme.typography.titleSmall, color = Amber)
                        Text("Save your account number and IFSC before running.", style = MaterialTheme.typography.bodySmall, color = TextDim)
                        Spacer(Modifier.height(10.dp))
                        TerminalButton("SET DESTINATION", onDestination, color = Amber, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionTitle("AMOUNT", "₹1 up to ${BankDefaults.rupees(bank.capPaise)}. Integer paise underneath, never a float.")
            TerminalField(
                label = "amount in rupees",
                value = amount,
                onChange = { vm.setAmount(it) },
                placeholder = "250",
                numeric = true,
                error = message
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "= $paise paise · ${bank.quirk}",
                style = MaterialTheme.typography.labelSmall,
                color = TextDim
            )

            Spacer(Modifier.height(14.dp))
            SectionTitle("SOURCE", "A saved remitter profile, or the bank's own P2A route.")
            vm.sources.forEachIndexed { index, profile ->
                val selected = sourceIndex == index
                Panel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clickableNoRipple { vm.setSource(index) },
                    border = if (selected) Green else Hairline
                ) {
                    Column {
                        Text(profile.name, style = MaterialTheme.typography.bodyMedium, color = if (selected) Green else TextPrimary)
                        Text("${profile.accountNumber} · ${profile.ifsc}", style = MaterialTheme.typography.labelSmall, color = TextDim)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TerminalButton(
                    label = if (run == null) "RUN" else if (running) "RUNNING…" else "RUN AGAIN",
                    onClick = { vm.start() },
                    enabled = !busy && destination != null,
                    modifier = Modifier.weight(1f)
                )
                if (running) {
                    TerminalButton("CANCEL", { confirmCancel = true }, color = Red, modifier = Modifier.weight(1f))
                }
            }

            if (run != null) {
                Spacer(Modifier.height(16.dp))
                val row = run!!
                Panel(
                    border = when (row.status) {
                        RunStatus.COMPLETED.name -> Green
                        RunStatus.FAILED.name, RunStatus.CANCELLED.name -> Red
                        RunStatus.PAUSED.name -> Amber
                        else -> Cyan
                    }
                ) {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("RUN #${row.id} · ${row.status}", style = MaterialTheme.typography.labelSmall, color = TextDim)
                            BadgeChip(if (row.badge == Badge.LIVE.name) Badge.LIVE else Badge.SIMULATED)
                        }
                        Spacer(Modifier.height(6.dp))
                        KeyValue("idempotency key", row.idempotencyKey.take(16) + "…", Cyan)
                        KeyValue("amount", BankDefaults.rupees(row.amountPaise))
                        KeyValue("last confirmed stage", row.lastConfirmedStage ?: "—")
                        row.summary?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = if (row.status == RunStatus.COMPLETED.name) Green else Amber)
                        }
                        row.errorText?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = Red)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (row.status == RunStatus.FAILED.name || row.status == RunStatus.CANCELLED.name) {
                                TerminalButton("RETRY (same key)", { vm.retry() }, color = Amber, modifier = Modifier.weight(1f))
                            }
                            if (row.status == RunStatus.PAUSED.name) {
                                TerminalButton("RESUME", { vm.resume() }, color = Amber, modifier = Modifier.weight(1f))
                            }
                            TerminalButton("RAW", { onConsole(row.id) }, color = Cyan, modifier = Modifier.weight(1f))
                            TerminalButton("TRACE", { onTrace(row.id) }, color = Cyan, modifier = Modifier.weight(1f))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                SectionTitle("PIPELINE", "A stage only advances on a server confirmation.")
                StageKind.entries.forEach { kind ->
                    val log = stages.lastOrNull { it.stage == kind.name }
                    val done = log != null && log.httpStatus != null
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickableNoRipple { openRaw = if (openRaw == kind.name) null else kind.name },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (done) "✓" else "·",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (done) Green else TextDim,
                            modifier = Modifier.width(20.dp)
                        )
                        Text(
                            kind.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (done) TextPrimary else TextDim,
                            modifier = Modifier.weight(1f)
                        )
                        if (log != null) {
                            Text(
                                "${log.httpStatus ?: "—"} · ${log.latencyMs ?: 0}ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (log.httpStatus in 200..399) Green else Amber
                            )
                            Spacer(Modifier.width(6.dp))
                            BadgeChip(if (log.badge == Badge.LIVE.name) Badge.LIVE else Badge.SIMULATED)
                        }
                    }
                    if (openRaw == kind.name && log != null) {
                        Panel(border = Hairline) {
                            Column {
                                KeyValue("request", "tap RAW for full headers", TextDim)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    (log.rawBody ?: log.note ?: "no body").take(900),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextDim
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            } else {
                Spacer(Modifier.height(16.dp))
                EmptyState("No run yet for ${bank.name}.", null)
            }
            Spacer(Modifier.height(30.dp))
        }
    }

    if (confirmCancel) {
        ConfirmDialog(
            title = "Cancel this run?",
            body = "The leg stops where it is. A retry reuses the same idempotency key, so nothing can double-debit.",
            confirm = "cancel run",
            onConfirm = { vm.cancel() },
            onDismiss = { confirmCancel = false }
        )
    }
}
