=== ABOUT: The three welcome pages: why it is honest, how hidden you want to be, and your account details ===
package com.arin.ghostbank.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.StealthLevel
import com.arin.ghostbank.ui.components.ScreenColumn
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.TerminalField
import com.arin.ghostbank.ui.components.levelColor
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Hairline
import com.arin.ghostbank.ui.theme.InkCard
import com.arin.ghostbank.ui.theme.Red
import com.arin.ghostbank.ui.theme.TextDim
import com.arin.ghostbank.ui.theme.TextPrimary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnboardingViewModel(private val container: AppContainer) : ViewModel() {

    private val _page = MutableStateFlow(0)
    val page = _page.asStateFlow()

    private val _level = MutableStateFlow(StealthLevel.STANDARD)
    val level = _level.asStateFlow()

    private val _number = MutableStateFlow("")
    val number = _number.asStateFlow()

    private val _ifsc = MutableStateFlow("")
    val ifsc = _ifsc.asStateFlow()

    private val _holder = MutableStateFlow("")
    val holder = _holder.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun setLevel(level: StealthLevel) { _level.value = level }
    fun setNumber(value: String) { _number.value = value.filter { it.isDigit() }.take(18); _error.value = null }
    fun setIfsc(value: String) { _ifsc.value = value.uppercase().filter { it.isLetterOrDigit() }.take(11); _error.value = null }
    fun setHolder(value: String) { _holder.value = value.take(40) }

    fun next() {
        if (_page.value == 1) viewModelScope.launch { container.settings.setStealthLevel(_level.value) }
        if (_page.value < 2) _page.value += 1
    }

    fun back() { if (_page.value > 0) _page.value -= 1 }

    fun finish(onDone: () -> Unit) = viewModelScope.launch {
        val digits = _number.value
        val code = _ifsc.value
        if (digits.length !in 9..18) { _error.value = "Account number should be 9 to 18 digits"; return@launch }
        if (!Banks.isValidIfsc(code)) { _error.value = "IFSC must be 4 letters, a 0, then 6 letters or digits"; return@launch }
        val bank = Banks.byIfsc(code)
        if (bank == null) { _error.value = "That IFSC prefix is not one of the 27 supported banks"; return@launch }
        container.accounts.saveDestination(digits, code, _holder.value.ifBlank { null }, bank.code)
        container.settings.setDestinationSaved(true)
        container.settings.setStealthLevel(_level.value)
        container.settings.setOnboardingDone(true)
        onDone()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { OnboardingViewModel(container) }
        }
    }
}

@Composable
fun OnboardingScreen(container: AppContainer, onDone: () -> Unit) {
    val vm: OnboardingViewModel = viewModel(factory = OnboardingViewModel.factory(container))
    val page by vm.page.collectAsStateWithLifecycle()
    val level by vm.level.collectAsStateWithLifecycle()
    val number by vm.number.collectAsStateWithLifecycle()
    val ifsc by vm.ifsc.collectAsStateWithLifecycle()
    val holder by vm.holder.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    ScreenColumn {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("SETUP ${page + 1}/3", style = MaterialTheme.typography.labelSmall, color = TextDim)
            Text("GHOSTBANK", style = MaterialTheme.typography.labelSmall, color = Green)
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(Hairline)) {
            Box(Modifier.fillMaxWidth((page + 1) / 3f).height(2.dp).background(Green))
        }

        when (page) {
            0 -> {
                Text("THE HONEST CARD", style = MaterialTheme.typography.titleMedium, color = Green)
                Spacer(Modifier.height(8.dp))
                Card {
                    Text(
                        "The bank always knows your account number. It has to — that is where the money lands.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "What GhostBank changes is the trail around it: the IP, the browser fingerprint, the virtual account the credit lands on first, and the fact that each source account only ever sends one paisa once.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "27 banks · IMPS P2A · integer paise · no analytics, no cloud.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Cyan
                    )
                }
            }
            1 -> {
                Text("HOW HIDDEN?", style = MaterialTheme.typography.titleMedium, color = Green)
                Text("You can change this later in Settings.", style = MaterialTheme.typography.bodySmall, color = TextDim)
                StealthLevel.values().forEach { option ->
                    val color = levelColor(option)
                    val selected = option == level
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(InkCard)
                            .border(1.dp, if (selected) color else Hairline, RoundedCornerShape(6.dp))
                            .padding(12.dp)
                            .clickable { vm.setLevel(option) }
                    ) {
                        Text(option.name, style = MaterialTheme.typography.titleSmall, color = color)
                        Text(
                            when (option) {
                                StealthLevel.STANDARD -> "Browser fingerprint and random pacing. Proxy optional."
                                StealthLevel.DEEP -> "Adds proxy rotation, a fresh session per run, decoy browsing and velocity caps."
                                StealthLevel.GHOST -> "Everything above and a proxy is mandatory — no exit, no traffic."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDim
                        )
                    }
                }
            }
            else -> {
                Text("WHERE SHOULD IT LAND?", style = MaterialTheme.typography.titleMedium, color = Green)
                Text("One fixed destination. Digits are masked after saving.", style = MaterialTheme.typography.bodySmall, color = TextDim)
                TerminalField("Account number", number, vm::setNumber, numeric = true, error = error)
                TerminalField("IFSC", ifsc, vm::setIfsc, placeholder = "e.g. HDFC0000123")
                TerminalField("Payee name (optional)", holder, vm::setHolder)
                BranchResolver.formatHint(ifsc)?.let { hint ->
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = Amber)
                }
                BranchResolver.resolve(ifsc)?.let { info ->
                    Text("${info.bankName} · ${info.branchLabel}", style = MaterialTheme.typography.bodySmall, color = Green)
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (page > 0) {
                TerminalButton("BACK", vm::back, color = TextDim, modifier = Modifier.weight(1f))
            }
            TerminalButton(
                if (page < 2) "NEXT" else "SAVE AND START",
                { if (page < 2) vm.next() else vm.finish(onDone) },
                modifier = Modifier.weight(2f)
            )
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(InkCard)
            .border(1.dp, Hairline, RoundedCornerShape(6.dp))
            .padding(14.dp)
    ) { content() }
}

@Composable
private fun Modifier.clickable(onClick: () -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable(onClick = onClick))

@Composable
private fun Unused() {
    Box(Modifier.fillMaxSize()) { Text("", color = Red, style = MaterialTheme.typography.labelSmall) }
    Spacer(Modifier.height(0.dp))
    Alignment.Center
}
