package com.arin.ghostbank.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import com.arin.ghostbank.data.db.HarvestLegEntity
import com.arin.ghostbank.data.db.RegistryEntity
import com.arin.ghostbank.model.Bank
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.HarvestStatus
import com.arin.ghostbank.model.Ids
import com.arin.ghostbank.model.REMITTER_PROFILES
import com.arin.ghostbank.model.RunMode
import com.arin.ghostbank.ui.components.BadgeChip
import com.arin.ghostbank.ui.components.ConfirmDialog
import com.arin.ghostbank.ui.components.EmptyState
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.Pill
import com.arin.ghostbank.ui.components.SectionTitle
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.TerminalField
import com.arin.ghostbank.ui.components.clickableNoRipple
import com.arin.ghostbank.ui.components.statusColor
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HarvestViewModel(private val container: AppContainer) : ViewModel() {

    private val _bankCode = MutableStateFlow("SBI")
    val bankCode: StateFlow<String> = _bankCode.asStateFlow()

    private val _paste = MutableStateFlow("")
    val paste: StateFlow<String> = _paste.asStateFlow()

    private val _messages = MutableStateFlow<List<String>>(emptyList())
    val messages: StateFlow<List<String>> = _messages.asStateFlow()

    private val _runId = MutableStateFlow<Long?>(null)
    val runId: StateFlow<Long?> = _runId.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _note = MutableStateFlow<String?>(null)
    val note: StateFlow<String?> = _note.asStateFlow()

    val settings: StateFlow<Settings> =
        container.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    val queue: StateFlow<List<RegistryEntity>> = combine(container.registry.all(), _bankCode) { rows, bank ->
        rows.filter { it.bankCode == bank && it.status == HarvestStatus.PENDING.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val legs: StateFlow<List<HarvestLegEntity>> = _runId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else container.db.legs().byRun(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalPaise: StateFlow<Long> = _runId
        .flatMapLatest { id ->
            if (id == null) flowOf(0L) else container.db.legs().totalPaise(id).let { flow -> flow }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun setBank(code: String) { _bankCode.value = code }
    fun setPaste(value: String) { _paste.value = value }

    fun addSingle(number: String, ifsc: String) {
        if (number.isBlank() && ifsc.isBlank()) return
        addLines("${number.trim()} ${ifsc.trim()}".trim())
    }

    fun addLines(text: String) {
        val bank = Banks.byCode(_bankCode.value) ?: return
        viewModelScope.launch {
            val messages = mutableListOf<String>()
            text.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEachIndexed { index, line ->
                val parts = line.split(Regex("[\\s,;|]+")).filter { it.isNotBlank() }
                val number = parts.getOrNull(0)?.filter { it.isDigit() }.orEmpty()
                val ifsc = parts.getOrNull(1)?.uppercase() ?: BankDefaults.defaultIfsc(bank.code)
                when {
                    number.length < 9 || number.length > 18 -> messages += "line ${index + 1}: number must be 9–18 digits"
                    !Banks.isValidIfsc(ifsc) -> messages += "line ${index + 1}: IFSC $ifsc has the wrong shape"
                    Banks.byIfsc(ifsc) == null -> messages += "line ${index + 1}: $ifsc is not a supported bank"
                    else -> {
                        val id = container.registry.enqueue(number, ifsc, bank.code)
                        messages += if (id != null) "queued ${Ids.maskNumber(number)}" else "skipped ${Ids.maskNumber(number)} — already in the registry"
                    }
                }
            }
            _messages.value = messages
            _paste.value = ""
        }
    }

    fun start() {
        if (_busy.value) return
        val bank = Banks.byCode(_bankCode.value) ?: return
        val pending = queue.value
        if (pending.isEmpty()) { _note.value = "The pre-flight queue is empty."; return }
        _busy.value = true
        _note.value = null
        viewModelScope.launch {
            val settings = settings.value
            val runId = container.engine.createTransferRun(bank, 1, RunMode.HARVEST, settings.stealthLevel)
            _runId.value = runId
            container.engine.performHarvestRun(
                runId = runId,
                settings = settings,
                bankCode = bank.code,
                accountKeys = pending.map { it.id to (it.accountNumber to it.ifsc) }
            )
            _busy.value = false
        }
    }

    fun retryRejected() {
        _note.value = "Rejected rows are reset to PENDING by the retry button on each row."
    }

    fun retryRow(row: RegistryEntity) {
        viewModelScope.launch {
            container.registry.forceReset(row.id)
            _note.value = "${Ids.maskNumber(row.accountNumber)} is back in the queue."
        }
    }

    fun exportLegs(onShared: (String) -> Unit) {
        viewModelScope.launch {
            val rows = legs.value.map { leg ->
                listOf(leg.ts, leg.accountNumber, leg.ifsc, leg.amountPaise, leg.status, leg.utr.orEmpty(), leg.reason.orEmpty())
                    .joinToString(",")
            }
            if (rows.isEmpty()) { _note.value = "Nothing to export yet."; return@launch }
            val uri = container.exporter.writeCsv(
                "ghostbank_harvest_${System.currentTimeMillis()}.csv",
                "epoch_ms,account,ifsc,amount_paise,status,utr,reason",
                rows
            )
            container.exporter.share(uri, "Harvest legs CSV")
            onShared("exported ${rows.size} rows")
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory { initializer { HarvestViewModel(container) } }
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
    val bankCode by vm.bankCode.collectAsStateWithLifecycle()
    val paste by vm.paste.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val legs by vm.legs.collectAsStateWithLifecycle()
    val total by vm.totalPaise.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val note by vm.note.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val runId by vm.runId.collectAsStateWithLifecycle()
    val destination by container.accounts.destination().collectAsStateWithLifecycle(initialValue = null)

    var single by remember { mutableStateOf("") }
    var singleIfsc by remember { mutableStateOf("") }
    var confirmStart by remember { mutableStateOf(false) }

    val bank = Banks.byCode(bankCode)
    val settled = legs.count { it.status == HarvestStatus.SETTLED.name }
    val floored = legs.count { it.status == HarvestStatus.FLOOR.name }
    val rejected = legs.count { it.status == HarvestStatus.REJECTED.name }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("HARVEST MODE", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Text(
            "Amount is pinned to 1 paisa and hard-capped. One account, one paisa, ever — the registry is written before the request leaves.",
            style = MaterialTheme.typography.bodySmall,
            color = TextDim
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill("1 paisa fixed", Green)
            Spacer(Modifier.width(6.dp))
            if (settings.torMode) Pill("tor exit", Green) else Pill(settings.stealthLevel.name, Cyan)
            Spacer(Modifier.width(6.dp))
            Pill("max ${settings.maxAccountsPerHour}/h", Amber)
        }

        if (destination == null) {
            Spacer(Modifier.height(12.dp))
            Panel(border = Amber) {
                Column {
                    Text("NO DESTINATION YET", style = MaterialTheme.typography.titleSmall, color = Amber)
                    Text("Save the account every paisa lands in.", style = MaterialTheme.typography.bodySmall, color = TextDim)
                    Spacer(Modifier.height(10.dp))
                    TerminalButton("SET DESTINATION", onDestination, color = Amber, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("TARGET BANK", "The queue is kept per bank so the pre-flight list stays honest.")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Banks.all.forEach { candidate ->
                val selected = candidate.code == bankCode
                Panel(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .clickableNoRipple { vm.setBank(candidate.code) },
                    border = if (selected) Green else Hairline
                ) {
                    Text(candidate.code, style = MaterialTheme.typography.labelLarge, color = if (selected) Green else TextDim)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("BULK PASTE", "One account per line. Add the IFSC after a space, or the bank default is used.")
        TerminalField(
            label = "account numbers",
            value = paste,
            onChange = { vm.setPaste(it) },
            placeholder = "50100234987121 HDFC0000123\n00470155443322",
            singleLine = false
        )
        Spacer(Modifier.height(8.dp))
        TerminalButton("VALIDATE & QUEUE", { vm.addLines(paste) }, color = Cyan, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TerminalField(
                label = "single account",
                value = single,
                onChange = { single = it.filter { c -> c.isDigit() } },
                numeric = true
            )
            TerminalField(
                label = "ifsc",
                value = singleIfsc,
                onChange = { singleIfsc = it.uppercase().take(11) }
            )
        }
        Spacer(Modifier.height(8.dp))
        TerminalButton("ADD SINGLE", { vm.addSingle(single, singleIfsc); single = ""; singleIfsc = "" }, color = Cyan, modifier = Modifier.fillMaxWidth())

        if (messages.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Panel(border = Hairline) {
                Column {
                    Text("VALIDATION", style = MaterialTheme.typography.labelSmall, color = TextDim)
                    messages.forEach { line ->
                        val ok = line.startsWith("queued")
                        Text(line, style = MaterialTheme.typography.bodySmall, color = if (ok) Green else Red)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("PRE-FLIGHT", "${queue.size} accounts ready · nothing sent yet")
        if (queue.isEmpty()) {
            EmptyState("Nothing queued for ${bank?.name ?: bankCode}.", null)
        } else {
            queue.forEach { row ->
                Panel(border = Hairline, modifier = Modifier.padding(bottom = 6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Ids.maskNumber(row.accountNumber), style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Text(row.ifsc, style = MaterialTheme.typography.labelSmall, color = TextDim)
                        }
                        Pill(row.status, statusColor(HarvestStatus.PENDING))
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TerminalButton(
                if (busy) "HARVESTING…" else "RUN HARVEST",
                { confirmStart = true },
                enabled = !busy && queue.isNotEmpty() && destination != null,
                modifier = Modifier.weight(1f)
            )
            TerminalButton("LEDGER", onLedger, color = Cyan, modifier = Modifier.weight(1f))
        }

        if (note != null) {
            Spacer(Modifier.height(8.dp))
            Text(note!!, style = MaterialTheme.typography.bodySmall, color = Amber)
        }

        if (legs.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Panel(border = Hairline) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("RUN SUMMARY", style = MaterialTheme.typography.labelSmall, color = TextDim)
                        if (runId != null) TerminalButton("RAW", { onConsole(runId!!) }, color = Cyan)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill("sent ${legs.size}", Cyan)
                        Pill("settled $settled", Green)
                        Pill("floor $floored", Amber)
                        Pill("rejected $rejected", Red)
                    }
                    Spacer(Modifier.height(10.dp))
                    legs.forEach { leg ->
                        val status = runCatching { HarvestStatus.valueOf(leg.status) }.getOrDefault(HarvestStatus.PENDING)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                when (status) {
                                    HarvestStatus.SETTLED -> "✓"
                                    HarvestStatus.FLOOR -> "!"
                                    HarvestStatus.REJECTED -> "×"
                                    HarvestStatus.SENT -> "→"
                                    HarvestStatus.PENDING -> "·"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = statusColor(status),
                                modifier = Modifier.width(18.dp)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(Ids.maskNumber(leg.accountNumber), style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                Text(
                                    when (status) {
                                        HarvestStatus.SETTLED -> "utr ${leg.utr ?: "—"} · ${leg.amountPaise} paise"
                                        HarvestStatus.FLOOR -> "floor ₹1 · utr ${leg.utr ?: "—"}"
                                        HarvestStatus.REJECTED -> leg.reason ?: "rejected"
                                        else -> "${leg.amountPaise} paise"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (status == HarvestStatus.REJECTED) Red else TextDim
                                )
                            }
                            BadgeChip(Badge.SIMULATED.takeIf { status != HarvestStatus.SETTLED } ?: Badge.LIVE)
                            if (status == HarvestStatus.REJECTED) {
                                Spacer(Modifier.width(6.dp))
                                TerminalButton("RETRY", { onLedger() }, color = Amber)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("TOTAL", style = MaterialTheme.typography.labelSmall, color = TextDim)
                        Text("$total paise", style = MaterialTheme.typography.titleSmall, color = Green)
                    }
                    Spacer(Modifier.height(8.dp))
                    TerminalButton("EXPORT THIS RUN", { vm.exportLegs { _ -> } }, color = Cyan, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        Spacer(Modifier.height(30.dp))
    }

    if (confirmStart) {
        ConfirmDialog(
            title = "Send ${queue.size} legs?",
            body = "Each account is hashed into the registry before its request leaves, so a crash mid-run can never touch it twice.",
            confirm = "run",
            onConfirm = { vm.start() },
            onDismiss = { confirmStart = false }
        )
    }
}
