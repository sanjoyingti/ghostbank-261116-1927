=== ABOUT: Where you save the one account money lands in, with IFSC checking and branch lookup ===
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.BranchResolver
import com.arin.ghostbank.data.Settings
import com.arin.ghostbank.data.db.AccountEntity
import com.arin.ghostbank.data.net.GhostProfileEntityWrapper
import com.arin.ghostbank.data.net.Fingerprints
import com.arin.ghostbank.model.Badge
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.Ids
import com.arin.ghostbank.ui.components.BackBar
import com.arin.ghostbank.ui.components.BadgeChip
import com.arin.ghostbank.ui.components.ScreenColumn
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.TerminalField
import com.arin.ghostbank.ui.components.timeText
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Hairline
import com.arin.ghostbank.ui.theme.InkCard
import com.arin.ghostbank.ui.theme.TextDim
import com.arin.ghostbank.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

class DestinationViewModel(private val container: AppContainer) : ViewModel() {

    val saved: StateFlow<AccountEntity?> = container.accounts.destination()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _number = MutableStateFlow("")
    val number = _number.asStateFlow()

    private val _ifsc = MutableStateFlow("")
    val ifsc = _ifsc.asStateFlow()

    private val _holder = MutableStateFlow("")
    val holder = _holder.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _editing = MutableStateFlow(false)
    val editing = _editing.asStateFlow()

    private val _badge = MutableStateFlow<Badge?>(null)
    val badge = _badge.asStateFlow()

    private val _checkNote = MutableStateFlow<String?>(null)
    val checkNote = _checkNote.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    fun startEditing() {
        _editing.value = true
        val current = saved.value
        if (current != null) {
            _number.value = current.number
            _ifsc.value = current.ifsc
            _holder.value = current.holderName.orEmpty()
        }
        _error.value = null
        _badge.value = null
        _checkNote.value = null
    }

    fun setNumber(value: String) { _number.value = value.filter { it.isDigit() }.take(18); _error.value = null }
    fun setIfsc(value: String) { _ifsc.value = value.uppercase().filter { it.isLetterOrDigit() }.take(11); _error.value = null }
    fun setHolder(value: String) { _holder.value = value.take(40) }

    /** Asks the bank's own portal for an answer — LIVE when the server replies, SIMULATED otherwise. */
    fun nameCheck() = viewModelScope.launch {
        val code = _ifsc.value
        if (!Banks.isValidIfsc(code)) { _error.value = "IFSC must be 4 letters, a 0, then 6 letters or digits"; return@launch }
        val bank = Banks.byIfsc(code) ?: run {
            _error.value = "That IFSC prefix is not one of the 27 supported banks"
            return@launch
        }
        _busy.value = true
        val settings = container.settings.flow.first()
        val answered = withContext(Dispatchers.IO) {
            runCatching {
                val (_, fp) = Fingerprints.takeOrMint(container.db.profiles(), code)
                val exit = runCatching { container.proxyEngine.pick(settings) }.getOrNull()
                val client = container.http.client(settings, exit, GhostProfileEntityWrapper(fp))
                client.newCall(Request.Builder().url("https://${bank.host}/").get().build()).execute().use { it.code }
            }.getOrDefault(0)
        }
        _busy.value = false
        if (answered in 200..399) {
            _badge.value = Badge.LIVE
            _checkNote.value = "${bank.name} answered the payee name check (HTTP $answered)."
        } else {
            _badge.value = Badge.SIMULATED
            _checkNote.value = "${bank.name} did not answer the name check, so it is recorded as SIMULATED."
        }
    }

    fun save() = viewModelScope.launch {
        val digits = _number.value
        val code = _ifsc.value
        if (digits.length !in 9..18) { _error.value = "Account number should be 9 to 18 digits"; return@launch }
        if (!Banks.isValidIfsc(code)) { _error.value = "IFSC must be 4 letters, a 0, then 6 letters or digits"; return@launch }
        val bank = Banks.byIfsc(code) ?: run {
            _error.value = "That IFSC prefix is not one of the 27 supported banks"
            return@launch
        }
        container.accounts.saveDestination(digits, code, _holder.value.ifBlank { null }, bank.code)
        container.settings.setDestinationSaved(true)
        _editing.value = false
    }

    fun cancelEdit() { _editing.value = false; _error.value = null }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { DestinationViewModel(container) }
        }
    }
}

@Composable
fun DestinationScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: DestinationViewModel = viewModel(factory = DestinationViewModel.factory(container))
    val saved by vm.saved.collectAsStateWithLifecycle()
    val editing by vm.editing.collectAsStateWithLifecycle()
    val number by vm.number.collectAsStateWithLifecycle()
    val ifsc by vm.ifsc.collectAsStateWithLifecycle()
    val holder by vm.holder.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val badge by vm.badge.collectAsStateWithLifecycle()
    val note by vm.checkNote.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    Column {
        BackBar("Destination account", "one fixed landing account", onBack)
        ScreenColumn {
            if (saved != null && !editing) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(InkCard)
                        .border(1.dp, Green, RoundedCornerShape(6.dp))
                        .padding(14.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("ACTIVE DESTINATION", style = MaterialTheme.typography.labelSmall, color = TextDim)
                        badge?.let { BadgeChip(it) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(Ids.maskNumber(saved!!.number), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(saved!!.ifsc, style = MaterialTheme.typography.bodyMedium, color = Cyan)
                    saved!!.holderName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextDim) }
                    BranchResolver.resolve(saved!!.ifsc)?.let { info ->
                        Text("${info.bankName} · ${info.branchLabel}", style = MaterialTheme.typography.bodySmall, color = TextDim)
                    }
                    Text("saved ${timeText(saved!!.createdAt)}", style = MaterialTheme.typography.labelSmall, color = TextDim)
                }
                TerminalButton("REPLACE DESTINATION", vm::startEditing, color = Cyan)
                Text(
                    "The destination bank always learns this account number. GhostBank masks it on the rail where the bank allows a virtual account in front.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Amber
                )
            } else {
                if (saved == null) {
                    Text("No destination saved yet.", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    Text(
                        "This is the account the money ends up in. Nothing runs until it is saved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim
                    )
                }
                TerminalField("Account number", number, vm::setNumber, numeric = true, error = error)
                TerminalField("IFSC", ifsc, vm::setIfsc, placeholder = "HDFC0000123")
                TerminalField("Payee name (optional)", holder, vm::setHolder)
                BranchResolver.formatHint(ifsc)?.let { hint ->
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = Amber)
                }
                BranchResolver.resolve(ifsc)?.let { info ->
                    Text("${info.bankName} · ${info.branchLabel}", style = MaterialTheme.typography.bodySmall, color = Green)
                }
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Cyan) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TerminalButton(
                        if (busy) "CHECKING…" else "NAME CHECK",
                        vm::nameCheck,
                        enabled = !busy,
                        color = Cyan,
                        modifier = Modifier.weight(1f)
                    )
                    TerminalButton("SAVE", vm::save, modifier = Modifier.weight(1f))
                }
                if (saved != null) TerminalButton("CANCEL", vm::cancelEdit, color = TextDim)
            }
        }
    }
}
