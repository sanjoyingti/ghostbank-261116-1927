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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.BranchResolver
import com.arin.ghostbank.data.db.AccountEntity
import com.arin.ghostbank.model.Badge
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.Ids
import com.arin.ghostbank.ui.components.BackBar
import com.arin.ghostbank.ui.components.BadgeChip
import com.arin.ghostbank.ui.components.KeyValue
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.SectionTitle
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.TerminalField
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Ink
import com.arin.ghostbank.ui.theme.TextDim
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DestinationViewModel(private val container: AppContainer) : ViewModel() {

    var number by mutableStateOf("")
    var ifsc by mutableStateOf("")
    var holder by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var checkState by mutableStateOf<String?>(null)
    var checked by mutableStateOf<Badge?>(null)

    private val _saved = MutableStateFlow<AccountEntity?>(null)
    val saved: StateFlow<AccountEntity?> = _saved.asStateFlow()

    init {
        viewModelScope.launch {
            container.accounts.destination().collect { existing ->
                _saved.value = existing
                if (existing != null && number.isBlank()) {
                    number = existing.number
                    ifsc = existing.ifsc
                    holder = existing.holderName.orEmpty()
                }
            }
        }
    }

    private fun validate(): String? = when {
        number.filter { it.isDigit() }.length < 9 -> "Account number needs at least 9 digits"
        number.filter { it.isDigit() }.length > 18 -> "Account number is longer than 18 digits"
        !Banks.isValidIfsc(ifsc) -> "IFSC must be 4 letters, a 0, then 6 letters or digits"
        Banks.byIfsc(ifsc) == null -> "That IFSC is not one of the 27 supported banks"
        else -> null
    }

    /** Name check against the bank's own payee validation: a real request, marked LIVE only on a server answer. */
    fun runNameCheck() {
        val problem = validate()
        if (problem != null) { error = problem; return }
        error = null
        checkState = "checking payee with ${Banks.byIfsc(ifsc)?.name}…"
        viewModelScope.launch {
            val outcome = container.payeeCheck(ifsc, number)
            checked = outcome.first
            checkState = outcome.second
        }
    }

    fun save(onSaved: () -> Unit) {
        val problem = validate()
        if (problem != null) { error = problem; return }
        val bank = Banks.byIfsc(ifsc)!!
        error = null
        viewModelScope.launch {
            container.accounts.saveDestination(number.filter { it.isDigit() }, ifsc.trim().uppercase(), holder.ifBlank { null }, bank.code)
            container.settings.setDestinationSaved(true)
            onSaved()
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory { initializer { DestinationViewModel(container) } }
    }
}

@Composable
fun DestinationScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: DestinationViewModel = viewModel(factory = DestinationViewModel.factory(container))
    val saved by vm.saved.collectAsStateWithLifecycle()
    var confirmed by remember { mutableStateOf(false) }

    LaunchedEffect(saved) { if (saved != null) confirmed = false }

    Column(Modifier.fillMaxSize().background(Ink)) {
        BackBar("DESTINATION ACCOUNT", "the fixed landing point for every leg", onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (saved != null) {
                Panel(border = Green) {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("SAVED DESTINATION", style = MaterialTheme.typography.labelSmall, color = Green)
                            BadgeChip(Badge.SIMULATED)
                        }
                        Spacer(Modifier.height(8.dp))
                        KeyValue("masked", Ids.maskNumber(saved!!.number), Green)
                        KeyValue("ifsc", saved!!.ifsc)
                        KeyValue("bank", Banks.byCode(saved!!.bankCode)?.name ?: saved!!.bankCode)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "The destination bank always knows this number. GhostBank only hides it from everyone else.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Amber
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            SectionTitle("NEW DESTINATION", "One active destination at a time; saving replaces the old one.")
            TerminalField(
                label = "account number",
                value = vm.number,
                onChange = { vm.number = it.filter { c -> c.isDigit() } },
                placeholder = "50100234987121",
                numeric = true
            )
            Spacer(Modifier.height(10.dp))
            TerminalField(
                label = "IFSC",
                value = vm.ifsc,
                onChange = { vm.ifsc = it.uppercase().take(11) },
                placeholder = "HDFC0000123",
                error = vm.error ?: BranchResolver.formatHint(vm.ifsc)
            )
            val branch = BranchResolver.resolve(vm.ifsc)
            if (branch != null) {
                Spacer(Modifier.height(8.dp))
                Panel(border = Green) {
                    Column {
                        Text("BRANCH RESOLVED", style = MaterialTheme.typography.labelSmall, color = Green)
                        Text("${branch.bankName} — ${branch.branchLabel}", style = MaterialTheme.typography.bodySmall, color = TextDim)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            TerminalField(
                label = "holder name (optional)",
                value = vm.holder,
                onChange = { vm.holder = it.take(40) },
                placeholder = "as printed on the passbook"
            )

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TerminalButton("CHECK PAYEE", { vm.runNameCheck() }, color = Amber, modifier = Modifier.weight(1f))
                TerminalButton(
                    "SAVE",
                    { if (confirmed) vm.save { } else confirmed = true },
                    modifier = Modifier.weight(1f)
                )
            }

            if (!confirmed) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Save is armed in two taps so a typo cannot become the fixed destination.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim
                )
            }

            if (vm.checkState != null) {
                Spacer(Modifier.height(12.dp))
                Panel(border = if (vm.checked == Badge.LIVE) Green else Amber) {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("PAYEE NAME CHECK", style = MaterialTheme.typography.labelSmall, color = TextDim)
                            if (vm.checked != null) BadgeChip(vm.checked!!)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(vm.checkState!!, style = MaterialTheme.typography.bodySmall, color = TextDim)
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}
