package com.arin.ghostbank.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import com.arin.ghostbank.data.BranchResolver
import com.arin.ghostbank.data.db.AccountEntity
import com.arin.ghostbank.data.net.PayeeCheck
import com.arin.ghostbank.model.Badge
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.Ids
import com.arin.ghostbank.ui.components.BackBar
import com.arin.ghostbank.ui.components.BadgeChip
import com.arin.ghostbank.ui.components.EmptyState
import com.arin.ghostbank.ui.components.KeyValue
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.ScreenColumn
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.TerminalField
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Red
import com.arin.ghostbank.ui.theme.TextDim
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DestinationVm(private val container: AppContainer) : ViewModel() {

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
    private val _check = MutableStateFlow<String?>(null)
    val check = _check.asStateFlow()
    private val _live = MutableStateFlow<Boolean?>(null)
    val live = _live.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    fun setNumber(value: String) { _number.value = value.filter { it.isDigit() }.take(18) }
    fun setIfsc(value: String) { _ifsc.value = value.uppercase().filter { it.isLetterOrDigit() }.take(11) }
    fun setHolder(value: String) { _holder.value = value.take(40) }

    fun save() {
        val number = _number.value.trim()
        val ifsc = _ifsc.value.trim().uppercase()
        if (number.length < 9) { _error.value = "Account number looks too short"; return }
        if (!Banks.isValidIfsc(ifsc)) { _error.value = "IFSC must be 4 letters, a 0, then 6 letters or digits"; return }
        val bank = Banks.byIfsc(ifsc)
        if (bank == null) { _error.value = "That IFSC prefix is not one of the 27 supported banks"; return }
        _error.value = null
        _busy.value = true
        viewModelScope.launch {
            val settings = container.settings.flow.first()
            val result = PayeeCheck.run(container.http, settings, bank, number, ifsc)
            _live.value = result.live
            _check.value = result.message
            container.accounts.saveDestination(number, ifsc, _holder.value.trim().ifBlank { null }, bank.code)
            container.settings.setDestinationSaved(true)
            _busy.value = false
            _number.value = ""
            _ifsc.value = ""
            _holder.value = ""
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { DestinationVm(container) }
        }
    }
}

@Composable
fun DestinationScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: DestinationVm = viewModel(factory = DestinationVm.factory(container))
    val saved by vm.saved.collectAsStateWithLifecycle()
    val number by vm.number.collectAsStateWithLifecycle()
    val ifsc by vm.ifsc.collectAsStateWithLifecycle()
    val holder by vm.holder.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val check by vm.check.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf(false) }

    Column {
        BackBar("destination account", "the one fixed landing point", onBack)
        ScreenColumn {
            val current = saved
            if (current != null && !editing) {
                Panel(border = Green) {
                    Column {
                        Text("SAVED", style = MaterialTheme.typography.titleSmall, color = Green)
                        Spacer(Modifier.height(8.dp))
                        KeyValue("account", Ids.maskNumber(current.number))
                        KeyValue("ifsc", current.ifsc)
                        KeyValue("holder", current.holderName ?: "not given")
                        val bank = Banks.byCode(current.bankCode)
                        KeyValue("bank", bank?.name ?: current.bankCode, Cyan)
                        BranchResolver.resolve(current.ifsc)?.let { KeyValue("branch", it.branchLabel) }
                        Spacer(Modifier.height(8.dp))
                        Text("Digits are masked to the last four after saving.", style = MaterialTheme.typography.bodySmall, color = TextDim)
                        Spacer(Modifier.height(10.dp))
                        TerminalButton("edit / replace", { editing = true }, color = Amber)
                    }
                }
            } else {
                TerminalField("account number", number, vm::setNumber, placeholder = "must land in your own account", numeric = true)
                TerminalField("ifsc", ifsc, vm::setIfsc, placeholder = "4 letters, 0, 6 alphanumerics", error = BranchResolver.formatHint(ifsc))
                BranchResolver.resolve(ifsc)?.let { info ->
                    Panel(border = Cyan) {
                        Column {
                            Text("RESOLVED BRANCH", style = MaterialTheme.typography.labelSmall, color = Cyan)
                            Text("${info.bankName} · ${info.branchLabel}", style = MaterialTheme.typography.bodyMedium, color = Cyan)
                        }
                    }
                }
                TerminalField("account holder (optional)", holder, vm::setHolder)
                if (error != null) Text(error!!, style = MaterialTheme.typography.bodySmall, color = Red)
                if (check != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BadgeChip(if (live == true) Badge.LIVE else Badge.SIMULATED)
                        Spacer(Modifier.size(8.dp))
                        Text(check!!, style = MaterialTheme.typography.bodySmall, color = TextDim)
                    }
                }
                Text(
                    "The name-check calls the bank's own payee endpoint. If the bank answers you get LIVE, otherwise SIMULATED — it never guesses.",
                    style = MaterialTheme.typography.bodySmall, color = TextDim
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (current != null) {
                        TerminalButton("cancel", { editing = false }, color = TextDim, modifier = Modifier.weight(1f))
                    }
                    TerminalButton(if (busy) "checking…" else "validate & save", { vm.save() }, enabled = !busy, modifier = Modifier.weight(1f))
                }
                if (number.isBlank() && ifsc.isBlank() && current == null) {
                    EmptyState("Type your account number and IFSC to begin.", null)
                }
            }
        }
    }
}
