=== ABOUT: Fixed the missing harvest screen: bulk paste with per-line checks, the pre-flight queue, live paise counter, hard stop and per-row retry ===
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
import com.arin.ghostbank.data.db.HarvestLegEntity
import com.arin.ghostbank.data.db.RegistryEntity
import com.arin.ghostbank.model.BankIfsc
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.HarvestStatus
import com.arin.ghostbank.model.RunMode
import com.arin.ghostbank.ui.components.ConfirmDialog
import com.arin.ghostbank.ui.components.EmptyState
import com.arin.ghostbank.ui.components.KeyValue
import com.arin.ghostbank.ui.components.Meter
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.Pill
import com.arin.ghostbank.ui.components.ScreenColumn
import com.arin.ghostbank.ui.components.SectionTitle
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.TerminalField
import com.arin.ghostbank.ui.components.paiseToCompact
import com.arin.ghostbank.ui.components.statusColor
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Hairline
import com.arin.ghostbank.ui.theme.Red
import com.arin.ghostbank.ui.theme.TextDim
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HarvestViewModel(private val container: AppContainer) : ViewModel() {

    data class Ui(
        val bankCode: String = "SBI",
        val paste: String = "",
        val busy: Boolean = false,
        val message: String? = null
    )

    private val _ui = MutableStateFlow(Ui())
    val state: StateFlow<Ui> = _ui
    private val runId = MutableStateFlow<Long?>(null)

    val pending: StateFlow<List<RegistryEntity>> = container.registry.all()
        .map { rows -> rows.filter { it.status == HarvestStatus.PENDING.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val legs: StateFlow<List<HarvestLegEntity>> = runId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else container.runs.legs(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalPaise: StateFlow<Long> = runId
        .flatMapLatest { id -> if (id == null) flowOf(0L) else container.db.legs().totalPaise(id) }
        .map { it ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun setBank(code: String) = _ui.value = _ui.value.copy(bankCode = code)
    fun setPaste(value: String) = _ui.value = _ui.value.copy(paste = value)

    fun parse() {
        val snapshot = _ui.value
        if (snapshot.busy) return
        viewModelScope.launch {
            var added = 0
            var skipped = 0
            snapshot.paste.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { raw ->
                val number = raw.substringBefore(',').filter { it.isDigit() }
                val ifsc = raw.substringAfter(',', "").trim().uppercase().ifBlank { BankIfsc.suggest(snapshot.bankCode) }
                val valid = number.length in 9..18 && Banks.isValidIfsc(ifsc)
                if (valid) {
                    val id = container.registry.enqueue(number, ifsc, snapshot.bankCode)
                    if (id != null) added++ else skipped++
                } else skipped++
            }
            _ui.value = _ui.value.copy(
                paste = "",
                message = "queued $added · skipped $skipped (invalid shape or already hashed)"
            )
        }
    }

    fun addSingle(number: String, ifsc: String) {
        viewModelScope.launch {
            val clean = number.filter { it.isDigit() }
            val code = ifsc.uppercase().ifBlank { BankIfsc.suggest(_ui.value.bankCode) }
            if (clean.length !in 9..18 || !Banks.isValidIfsc(code)) {
                _ui.value = _ui.value.copy(message = "that line does not look like an account and IFSC")
                return@launch
            }
            val id = container.registry.enqueue(clean, code, _ui.value.bankCode)
            _ui.value = _ui.value.copy(message = if (id != null) "queued 1 account" else "already in the registry")
        }
    }

    fun run() {
        val snapshot = _ui.value
        if (snapshot.busy) return
        viewModelScope.launch {
            val queue = pending.value
            if (queue.isEmpty()) {
                _ui.value = snapshot.copy(message = "nothing in the pre-flight queue")
                return@launch
            }
            _ui.value = snapshot.copy(busy = true, message = null)
            val settings = container.settings.flow.first()
            val id = container.engine.createTransferRun(
                bank = Banks.byCode(snapshot.bankCode) ?: Banks.all.first(),
                amountPaise = 1,
                mode = RunMode.HARVEST,
                level = settings.stealthLevel
            )
            runId.value = id
            container.engine.performHarvestRun(
                runId = id,
                settings = settings,
                bankCode = snapshot.bankCode,
                accountKeys = queue.map { it.id to (it.accountNumber to it.ifsc) }
            )
            _ui.value = _ui.value.copy(busy = false)
        }
    }

    fun cancel() {
        val id = runId.value ?: return
        viewModelScope.launch { container.engine.cancel(id); _ui.value = _ui.value.copy(busy = false) }
    }

    fun retry(entry: RegistryEntity) {
        viewModelScope.launch {
            container.registry.forceReset(entry.id)
            _ui.value = _ui.value.copy(message = "re-queued ${entry.accountNumber} — same hash, one paisa still")
        }
    }

    fun resume() {
        val id = runId.value ?: return
        viewModelScope.launch {
            val settings = container.settings.flow.first()
            container.engine.resume(id)
            _ui.value = _ui.value.copy(busy = true)
            container.engine.resumeHarvest(id, settings)
            _ui.value = _ui.value.copy(busy = false)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { HarvestViewModel(container) }
        }
    }
}

@Composable
fun HarvestScreen(
    container: AppContainer,
    onDestination: () -> Unit,
    onLedger: () -> Unit,
    onConsole: (Long) -> Unit
) {
    val vm: HarvestViewModel = viewModel(factory = HarvestViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val legs by vm.legs.collectAsStateWithLifecycle()
    val total by vm.totalPaise.collectAsStateWithLifecycle()
    val destination by container.accounts.destination().collectAsStateWithLifecycle(initialValue = null)
    var confirmCancel by remember { mutableStateOf(false) }
    var single by remember { mutableStateOf("") }

    ScreenColumn {
        Text("HARVEST", style = MaterialTheme.typography.titleSmall, color = Green)
        Text("Amount is hard-locked to ₹0.01 and cannot drift. One account, one paisa, ever.", style = MaterialTheme.typography.bodySmall, color = TextDim)

        if (destination == null) {
            Panel(border = Amber) {
                Column {
                    Text("No destination saved yet", style = MaterialTheme.typography.bodySmall, color = Amber)
                    Spacer(Modifier.height(6.dp))
                    TerminalButton("set destination", onDestination, color = Amber)
                }
            }
        }

        SectionTitle("Target bank", "the queue is sent to one bank at a time")
        Row {
            listOf("SBI", "HDFC", "ICICI", "AXIS").forEach { code ->
                val selected = state.bankCode == code
                Panel(border = if (selected) Green else Hairline) {
                    Text(
                        code,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) Green else TextDim,
                        modifier = Modifier.padding(2.dp)
                    )
                    TerminalButton(if (selected) "picked" else "pick", { vm.setBank(code) }, color = if (selected) Green else TextDim)
                }
                Spacer(Modifier.width(6.dp))
            }
        }

        TerminalField("BULK PASTE — one account per line, or account,IFSC", state.paste, vm::setPaste, singleLine = false)
        TerminalButton("validate and queue", vm::parse, color = Cyan)

        Row {
            Spacer(Modifier)
        }
        TerminalField("single account number", single, { single = it }, numeric = true)
        TerminalButton("add this one", { vm.addSingle(single, ""); single = "" }, color = Cyan)

        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (it.contains("queued")) Green else Amber) }

        SectionTitle("Pre-flight queue", "${pending.size} accounts waiting — nothing has been sent")
        if (pending.isEmpty()) {
            EmptyState("Paste account numbers above, then queue them", null, null)
        } else {
            pending.take(60).forEach { entry ->
                Panel(border = Hairline) {
                    Column {
                        Row {
                            Text(entry.accountNumber, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.typography.bodyMedium.color)
                            Spacer(Modifier.width(8.dp))
                            Pill(entry.status, statusColor(HarvestStatus.PENDING))
                        }
                        Text("${entry.ifsc} · ${entry.bankCode}", style = MaterialTheme.typography.labelSmall, color = TextDim)
                    }
                }
            }
        }

        TerminalButton(
            if (state.busy) "running — hard stop armed…" else "RUN QUEUE",
            vm::run,
            enabled = !state.busy && pending.isNotEmpty() && destination != null
        )
        if (state.busy) TerminalButton("cancel run", { confirmCancel = true }, color = Red)
        TerminalButton("resume interrupted run", vm::resume, color = Amber, enabled = !state.busy)

        SectionTitle("This run", "sent / settled / floor / rejected")
        val sent = legs.size
        val settled = legs.count { it.status == HarvestStatus.SETTLED.name }
        val floor = legs.count { it.status == HarvestStatus.FLOOR.name }
        val rejected = legs.count { it.status == HarvestStatus.REJECTED.name }
        KeyValue("sent", sent.toString(), Cyan)
        KeyValue("settled", settled.toString(), Green)
        KeyValue("floor ₹1", floor.toString(), Amber)
        KeyValue("rejected", rejected.toString(), Red)

        legs.forEach { leg ->
            Panel(border = statusColor(runCatching { HarvestStatus.valueOf(leg.status) }.getOrDefault(HarvestStatus.PENDING))) {
                Column {
                    Row {
                        Text(leg.accountNumber, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.typography.bodyMedium.color)
                        Spacer(Modifier.width(8.dp))
                        Pill(leg.status, statusColor(runCatching { HarvestStatus.valueOf(leg.status) }.getOrDefault(HarvestStatus.PENDING)))
                    }
                    leg.utr?.let { Text("UTR $it", style = MaterialTheme.typography.labelSmall, color = Green) }
                    leg.reason?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = TextDim) }
                    Text("${paiseToCompact(leg.amountPaise)} paise", style = MaterialTheme.typography.labelSmall, color = TextDim)
                    if (leg.status == HarvestStatus.REJECTED.name) {
                        val entry = pending.firstOrNull { it.id == leg.registryId }
                        if (entry != null) TerminalButton("retry this account", { vm.retry(entry) }, color = Amber)
                    }
                }
            }
        }

        Meter((total / 100f).coerceIn(0f, 1f), Green)
        Text("TOTAL $total paise — integer paise, never rounded", style = MaterialTheme.typography.titleSmall, color = Green)

        Row {
            TerminalButton("export run", {
                onLedger()
            }, color = Cyan)
            Spacer(Modifier.width(8.dp))
            val lastRun = legs.firstOrNull()?.runId
            if (lastRun != null) TerminalButton("raw console", { onConsole(lastRun) }, color = TextDim)
        }
    }

    if (confirmCancel) {
        ConfirmDialog(
            title = "Stop this harvest?",
            body = "The current leg is abandoned, not resent. Anything already claimed stays claimed in the registry, so nothing is touched twice.",
            confirm = "stop it",
            onConfirm = vm::cancel,
            onDismiss = { confirmCancel = false }
        )
    }
}
