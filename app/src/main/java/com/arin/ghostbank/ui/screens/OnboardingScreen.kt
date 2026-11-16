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
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.BranchResolver
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.StealthLevel
import com.arin.ghostbank.ui.components.ConfirmDialog
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.Pill
import com.arin.ghostbank.ui.components.SectionTitle
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.TerminalField
import com.arin.ghostbank.ui.components.clickableNoRipple
import com.arin.ghostbank.ui.components.levelColor
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Ink
import com.arin.ghostbank.ui.theme.Red
import com.arin.ghostbank.ui.theme.TextDim
import com.arin.ghostbank.ui.theme.TextPrimary
import kotlinx.coroutines.launch

class OnboardingViewModel(private val container: AppContainer) : ViewModel() {

    var number by mutableStateOf("")
    var ifsc by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var saved by mutableStateOf(false)

    fun save(onDone: () -> Unit) {
        val clean = ifsc.trim().uppercase()
        when {
            number.filter { it.isDigit() }.length < 9 -> { error = "Account number needs at least 9 digits"; return }
            !Banks.isValidIfsc(clean) -> { error = "IFSC must be 4 letters, a 0, then 6 letters or digits"; return }
            Banks.byIfsc(clean) == null -> { error = "That IFSC is not one of the 27 supported banks"; return }
        }
        val bank = Banks.byIfsc(clean)!!
        viewModelScope.launch {
            container.accounts.saveDestination(number.filter { it.isDigit() }, clean, null, bank.code)
            container.settings.setDestinationSaved(true)
            container.settings.setOnboardingDone(true)
            saved = true
            onDone()
        }
    }

    fun setLevel(level: StealthLevel) = viewModelScope.launch { container.settings.setStealthLevel(level) }

    fun finishWithoutAccount() = viewModelScope.launch { container.settings.setOnboardingDone(true) }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory { initializer { OnboardingViewModel(container) } }
    }
}

@Composable
fun OnboardingScreen(container: AppContainer, onDone: () -> Unit) {
    val vm: OnboardingViewModel = viewModel(factory = OnboardingViewModel.factory(container))
    var page by remember { mutableStateOf(0) }
    var confirmSkip by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("GHOSTBANK", style = MaterialTheme.typography.titleMedium, color = Green)
            Text("${page + 1}/3", style = MaterialTheme.typography.labelSmall, color = TextDim)
        }
        Spacer(Modifier.height(24.dp))

        when (page) {
            0 -> {
                Text("THE HONEST CARD", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                Spacer(Modifier.height(12.dp))
                Text(
                    "The bank always knows your account number. Nothing here changes that.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "What GhostBank does is make the trail unlinkable: each source account is touched once, " +
                        "behind a browser grade fingerprint and a rotated exit, so a burst of tiny credits never " +
                        "looks like one person with a script.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextDim
                )
                Spacer(Modifier.height(12.dp))
                Panel(border = Amber) {
                    Text(
                        "What it does NOT do: it does not steal. Legs are IMPS P2A calls against live bank hosts. " +
                            "Where a real bank API is unreachable, the leg is marked SIMULATED and never counted as live.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Amber
                    )
                }
            }
            1 -> {
                Text("PICK YOUR STEALTH LEVEL", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                Spacer(Modifier.height(12.dp))
                StealthLevel.entries.forEach { level ->
                    val desc = when (level) {
                        StealthLevel.STANDARD -> "Browser fingerprint, random pacing. Proxy optional."
                        StealthLevel.DEEP -> "Adds proxy rotation, ephemeral session per run, decoy browsing, velocity caps."
                        StealthLevel.GHOST -> "Everything in Deep plus a MANDATORY exit. No exit, no traffic."
                    }
                    Panel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clickableNoRipple { vm.setLevel(level) },
                        border = levelColor(level)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Pill(level.name, levelColor(level))
                                Spacer(Modifier.width(8.dp))
                                if (level == StealthLevel.DEEP) Text("recommended", style = MaterialTheme.typography.labelSmall, color = Cyan)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(desc, style = MaterialTheme.typography.bodySmall, color = TextDim)
                        }
                    }
                }
            }
            else -> {
                Text("YOUR DESTINATION ACCOUNT", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Every leg lands here. One active destination, editable later from Banks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim
                )
                Spacer(Modifier.height(12.dp))
                TerminalField(
                    label = "account number",
                    value = vm.number,
                    onChange = { vm.number = it.filter { c -> c.isDigit() } },
                    placeholder = "e.g. 50100234987121",
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
                val resolved = BranchResolver.resolve(vm.ifsc)
                if (resolved != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("resolved: ${resolved.bankName} — ${resolved.branchLabel}", style = MaterialTheme.typography.bodySmall, color = Green)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (page > 0) {
                TerminalButton("BACK", { page-- }, color = TextDim, modifier = Modifier.weight(1f))
            }
            if (page < 2) {
                TerminalButton("NEXT", { page++ }, modifier = Modifier.weight(1f))
            } else {
                TerminalButton("SAVE & ENTER", { vm.save(onDone) }, modifier = Modifier.weight(1f))
            }
        }
        if (page == 2) {
            Spacer(Modifier.height(10.dp))
            TerminalButton("skip for now", { confirmSkip = true }, color = Amber, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(30.dp))
    }

    if (confirmSkip) {
        ConfirmDialog(
            title = "Enter without a destination?",
            body = "You can still browse banks and the ledger, but no leg can be booked until an account is saved.",
            confirm = "enter anyway",
            onConfirm = { vm.finishWithoutAccount(); onDone() },
            onDismiss = { confirmSkip = false }
        )
    }
}
