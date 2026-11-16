package com.arin.ghostbank.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.arin.ghostbank.data.net.PayeeCheck
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.StealthLevel
import com.arin.ghostbank.ui.components.BadgeChip
import com.arin.ghostbank.ui.components.EmptyState
import com.arin.ghostbank.ui.components.Panel
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OnboardingVm(private val container: AppContainer, private val onDone: () -> Unit) : ViewModel() {

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

    private val _checkMessage = MutableStateFlow<String?>(null)
    val checkMessage = _checkMessage.asStateFlow()

    private val _checkLive = MutableStateFlow<Boolean?>(null)
    val checkLive = _checkLive.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()

    fun next() {
        if (_page.value < 2) _page.value += 1
    }

    fun back() {
        if (_page.value > 0) _page.value -= 1
    }

    fun setLevel(value: StealthLevel) { _level.value = value }
    fun setNumber(value: String) { _number.value = value.filter { it.isDigit() }.take(18) }
    fun setIfsc(value: String) { _ifsc.value = value.uppercase().filter { it.isLetterOrDigit() }.take(11) }
    fun setHolder(value: String) { _holder.value = value.take(40) }

    fun save() {
        val number = _number.value.trim()
        val ifsc = _ifsc.value.trim().uppercase()
        if (number.length < 9) { _error.value = "Account number looks too short"; return }
        if (!Banks.isValidIfsc(ifsc)) { _error.value = "IFSC must be 4 letters, a 0, then 6 letters or digits"; return }
        val bank = Banks.byIfsc(ifsc)
        if (bank == null) { _error.value = "Valid shape, but that prefix is not one of the 27 supported banks"; return }
        _error.value = null
        _saving.value = true
        viewModelScope.launch {
            val settings = container.settings.flow.first()
            val check = PayeeCheck.run(container.http, settings, bank, number, ifsc)
            _checkLive.value = check.live
            _checkMessage.value = check.message
            container.accounts.saveDestination(number, ifsc, _holder.value.trim().ifBlank { null }, bank.code)
            container.settings.setDestinationSaved(true)
            container.settings.setOnboardingDone(true)
            _saving.value = false
            onDone()
        }
    }

    companion object {
        fun factory(container: AppContainer, onDone: () -> Unit): ViewModelProvider.Factory = viewModelFactory {
            initializer { OnboardingVm(container, onDone) }
        }
    }
}

@Composable
fun OnboardingScreen(container: AppContainer, onDone: () -> Unit) {
    val vm: OnboardingVm = viewModel(factory = OnboardingVm.factory(container, onDone))
    val page by vm.page.collectAsStateWithLifecycle()
    val level by vm.level.collectAsStateWithLifecycle()
    val number by vm.number.collectAsStateWithLifecycle()
    val ifsc by vm.ifsc.collectAsStateWithLifecycle()
    val holder by vm.holder.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val checkMessage by vm.checkMessage.collectAsStateWithLifecycle()
    val checkLive by vm.checkLive.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()

    ScreenColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("GHOSTBANK", style = MaterialTheme.typography.titleMedium, color = Green)
            Spacer(Modifier.weight(1f))
            Text("0${page + 1} / 03", style = MaterialTheme.typography.labelSmall, color = TextDim)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(3.dp)
                        .background(if (index <= page) Green else Hairline)
                )
            }
        }

        when (page) {
            0 -> {
                Panel(border = Green) {
                    Column {
                        Text("THE HONEST CARD", style = MaterialTheme.typography.titleSmall, color = Green)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "The bank always knows your account number — it is in their own ledger and nothing can change that. " +
                                "The source bank knows the debited account too.",
                            style = MaterialTheme.typography.bodyMedium, color = TextPrimary
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "What GhostBank actually does is make the trail unlinkable: rotating proxy exits, DNS over HTTPS, " +
                                "a burned-after-use browser identity per account, human-shaped timing, and virtual-account masking.",
                            style = MaterialTheme.typography.bodySmall, color = TextDim
                        )
                    }
                }
                Panel(border = Amber) {
                    Column {
                        Text("THE HARD LIMITS", style = MaterialTheme.typography.titleSmall, color = Amber)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "1. IMPS has a ₹1 floor, so a 1-paisa harvest upgrades to ₹1 and is tagged FLOOR.\n" +
                                "2. The rail only moves money if the source account is really debited — the app can instruct, never mint.\n" +
                                "3. Android's TCP stack is still Android's; only an exit can shadow it.",
                            style = MaterialTheme.typography.bodySmall, color = TextDim
                        )
                    }
                }
                TerminalButton("next: pick a stealth level", { vm.next() })
            }

            1 -> {
                Text("STEALTH LEVEL", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text("You can change this later, but Ghost is the honest maximum.", style = MaterialTheme.typography.bodySmall, color = TextDim)
                LevelCard(
                    level = StealthLevel.STANDARD,
                    selected = level == StealthLevel.STANDARD,
                    body = "Chrome fingerprint, random pacing. Proxy optional.",
                    onSelect = { vm.setLevel(StealthLevel.STANDARD) }
                )
                LevelCard(
                    level = StealthLevel.DEEP,
                    selected = level == StealthLevel.DEEP,
                    body = "Adds proxy rotation, a fresh session per run, decoy browsing and velocity caps.",
                    onSelect = { vm.setLevel(StealthLevel.DEEP) }
                )
                LevelCard(
                    level = StealthLevel.GHOST,
                    selected = level == StealthLevel.GHOST,
                    body = "Everything below, and a proxy exit becomes mandatory — no exit, no traffic.",
                    onSelect = { vm.setLevel(StealthLevel.GHOST) }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TerminalButton("back", { vm.back() }, color = TextDim, modifier = Modifier.weight(1f))
                    TerminalButton("next: your account", { vm.next() }, modifier = Modifier.weight(1f))
                }
            }

            else -> {
                Text("DESTINATION ACCOUNT", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text("This is the fixed landing point for every leg.", style = MaterialTheme.typography.bodySmall, color = TextDim)
                TerminalField("account number", number, vm::setNumber, placeholder = "e.g. 50100234987121", numeric = true)
                TerminalField(
                    "ifsc", ifsc, vm::setIfsc,
                    placeholder = "HDFC0000123",
                    error = BranchResolver.formatHint(ifsc)
                )
                BranchResolver.resolve(ifsc)?.let { info ->
                    Text("${info.bankName} · ${info.branchLabel}", style = MaterialTheme.typography.bodySmall, color = Cyan)
                }
                TerminalField("account holder (optional)", holder, vm::setHolder)
                if (error != null) Text(error!!, style = MaterialTheme.typography.bodySmall, color = Red)
                if (checkMessage != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BadgeChip(if (checkLive == true) com.arin.ghostbank.model.Badge.LIVE else com.arin.ghostbank.model.Badge.SIMULATED)
                        Spacer(Modifier.size(8.dp))
                        Text(checkMessage!!, style = MaterialTheme.typography.bodySmall, color = TextDim)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TerminalButton("back", { vm.back() }, color = TextDim, modifier = Modifier.weight(1f))
                    TerminalButton(
                        if (saving) "checking…" else "save & enter",
                        { vm.save() },
                        enabled = !saving,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (number.isBlank() && ifsc.isBlank()) {
                    EmptyState("Enter the account the money should land in.", null)
                }
            }
        }
    }
}

@Composable
private fun LevelCard(level: StealthLevel, selected: Boolean, body: String, onSelect: () -> Unit) {
    val tint = levelColor(level)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(InkCard)
            .border(1.dp, if (selected) tint else Hairline, RoundedCornerShape(6.dp))
            .clickable(onClick = onSelect)
            .padding(12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(level.name, style = MaterialTheme.typography.titleSmall, color = tint)
                Spacer(Modifier.weight(1f))
                Text(if (selected) "[ selected ]" else "[ tap ]", style = MaterialTheme.typography.labelSmall, color = if (selected) tint else TextDim)
            }
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = TextDim)
        }
    }
}
