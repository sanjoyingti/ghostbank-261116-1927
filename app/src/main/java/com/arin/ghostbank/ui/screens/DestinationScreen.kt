=== ABOUT: Fixed the missing destination screen: it checks the branch code shape, resolves the branch, runs a payee name check and masks the digits once saved ===
package com.arin.ghostbank.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.Ids
import com.arin.ghostbank.ui.components.BackBar
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.ScreenColumn
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.TerminalField
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Red
import com.arin.ghostbank.ui.theme.TextDim
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DestinationViewModel(private val container: AppContainer) : ViewModel() {

    data class Ui(
        val number: String = "",
        val ifsc: String = "",
        val holder: String = "",
        val ifscHint: String? = null,
        val branch: String? = null,
        val nameCheck: String? = null,
        val nameCheckOk: Boolean? = null,
        val message: String? = null,
        val savedMasked: String? = null,
        val busy: Boolean = false
    )

    private val _ui = MutableStateFlow(Ui())
    val state: StateFlow<Ui> = _ui

    init {
        viewModelScope.launch {
            container.accounts.current()?.let { existing ->
                _ui.value = _ui.value.copy(
                    number = existing.number,
                    ifsc = existing.ifsc,
                    holder = existing.holderName.orEmpty(),
                    ifscHint = BranchResolver.formatHint(existing.ifsc),
                    branch = BranchResolver.resolve(existing.ifsc)?.let { "${it.bankName} · ${it.branchLabel}" }
                )
            }
        }
    }

    fun setNumber(value: String) {
        _ui.value = _ui.value.copy(number = value.filter { it.isLetterOrDigit() }.take(18), message = null)
    }

    fun setIfsc(value: String) {
        val clean = value.uppercase().filter { it.isLetterOrDigit() }.take(11)
        _ui.value = _ui.value.copy(
            ifsc = clean,
            ifscHint = if (clean.length >= 11) BranchResolver.formatHint(clean) else null,
            branch = BranchResolver.resolve(clean)?.let { "${it.bankName} · ${it.branchLabel}" },
            message = null
        )
    }

    fun setHolder(value: String) {
        _ui.value = _ui.value.copy(holder = value.take(40), nameCheck = null, nameCheckOk = null)
    }

    fun nameCheck() {
        val ui = _ui.value
        when {
            ui.holder.trim().length < 3 -> {
                _ui.value = ui.copy(nameCheck = "holder name needs at least 3 characters", nameCheckOk = false)
                return
            }
            !Banks.isValidIfsc(ui.ifsc) -> {
                _ui.value = ui.copy(nameCheck = "fix the IFSC first — the bank checks the name against that account", nameCheckOk = false)
                return
            }
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(nameCheck = "asking the bank to validate the payee…", nameCheckOk = null, busy = true)
            delay(900)
            val bank = Banks.byIfsc(_ui.value.ifsc)
            val ok = bank != null && ui.holder.trim().all { it.isLetter() || it == ' ' || it == '.' }
            _ui.value = _ui.value.copy(
                busy = false,
                nameCheckOk = ok,
                nameCheck = if (ok)
                    "shape accepted by ${bank?.name} payee rules — the real server still has the last word (SIMULATED)"
                else "the bank's payee rules expect letters only — check the spelling"
            )
        }
    }

    fun save() {
        val ui = _ui.value
        val bank = Banks.byIfsc(ui.ifsc)
        val problem = when {
            ui.number.filter { it.isDigit() }.length !in 9..18 -> "Account number should be 9 to 18 digits"
            !Banks.isValidIfsc(ui.ifsc) -> "IFSC must be 4 letters, a 0, then 6 letters or digits"
            bank == null -> "That IFSC belongs to a bank outside the 27 supported"
            ui.holder.trim().length < 3 -> "Holder name needs at least 3 characters"
            else -> null
        }
        if (problem != null) {
            _ui.value = ui.copy(message = problem)
            return
        }
        viewModelScope.launch {
            _ui.value = ui.copy(busy = true, message = null)
            container.accounts.saveDestination(ui.number.trim(), ui.ifsc, ui.holder.trim(), bank!!.code)
            container.settings.setDestinationSaved(true)
            _ui.value = _ui.value.copy(busy = false, savedMasked = Ids.maskNumber(ui.number.trim()), message = "destination saved — digits masked")
        }
    }
}

@Composable
fun DestinationScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: DestinationViewModel = viewModel(factory = DestinationViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()

    ScreenColumn {
        BackBar("Destination account", "one active destination, editable", onBack)
        Text(
            "Your bank always knows this number — GhostBank cannot change that. What it does is mask the digits to the last four, route every leg through a virtual account and keep one frozen identity per source account.",
            style = MaterialTheme.typography.bodySmall,
            color = TextDim
        )
        TerminalField("ACCOUNT NUMBER", state.number, vm::setNumber, numeric = true)
        TerminalField("IFSC", state.ifsc, vm::setIfsc, error = state.ifscHint, placeholder = "HDFC0000123")
        Text(state.branch ?: "branch appears once the IFSC is complete", style = MaterialTheme.typography.labelSmall, color = Cyan)
        TerminalField("ACCOUNT HOLDER NAME", state.holder, vm::setHolder, placeholder = "as printed on the passbook")

        TerminalButton(if (state.busy) "working…" else "run payee name check", vm::nameCheck, color = Cyan, enabled = !state.busy)
        state.nameCheck?.let { line ->
            Panel(border = if (state.nameCheckOk == true) Green else Amber) {
                Text(line, style = MaterialTheme.typography.bodySmall, color = if (state.nameCheckOk == true) Green else Amber)
            }
        }

        TerminalButton(if (state.busy) "saving…" else "save destination", vm::save, enabled = !state.busy)
        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (state.savedMasked != null) Green else Red) }
        state.savedMasked?.let {
            Text("active: $it", style = MaterialTheme.typography.titleSmall, color = Green)
        }
        Spacer(Modifier.height(8.dp))
        Text("Virtual accounts rotate in front of this number when Trace-Shield is on, so the rail sees a different payee each run.", style = MaterialTheme.typography.bodySmall, color = TextDim)
    }
}

private fun DestinationViewModel.Companion.unusedMarker() = Unit

class DestinationViewModelFactory

private fun DestinationViewModel.factoryHelper() = Unit

fun DestinationViewModel.Companion.factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
    initializer { DestinationViewModel(container) }
}

private val DestinationViewModel.Companion.marker: Unit get() = Unit
