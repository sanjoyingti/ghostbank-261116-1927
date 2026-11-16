=== ABOUT: Fixed the missing three-page welcome: the honesty card, the stealth dial, and your account setup ===
package com.arin.ghostbank.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.BranchResolver
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.StealthLevel
import com.arin.ghostbank.ui.components.EmptyState
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.Pill
import com.arin.ghostbank.ui.components.ScreenColumn
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.TerminalField
import com.arin.ghostbank.ui.components.clickableNoRipple
import com.arin.ghostbank.ui.components.levelColor
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Hairline
import com.arin.ghostbank.ui.theme.Red
import com.arin.ghostbank.ui.theme.TextDim
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(container: AppContainer, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var level by rememberSaveable { mutableStateOf(StealthLevel.STANDARD.name) }
    var number by rememberSaveable { mutableStateOf("") }
    var ifsc by rememberSaveable { mutableStateOf("") }
    var holder by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val hint = BranchResolver.formatHint(ifsc)

    fun finish(storeDestination: Boolean) {
        if (busy) return
        if (storeDestination) {
            val bank = Banks.byIfsc(ifsc)
            when {
                number.filter { it.isDigit() }.length !in 9..18 -> { error = "Account number should be 9 to 18 digits"; return }
                !Banks.isValidIfsc(ifsc) || bank == null -> { error = "IFSC must be 4 letters, a 0, then 6 letters or digits, on a supported bank"; return }
                holder.trim().length < 3 -> { error = "Holder name needs at least 3 characters"; return }
            }
            busy = true
            scope.launch {
                container.accounts.saveDestination(number.trim(), ifsc, holder.trim(), bank!!.code)
                container.settings.setDestinationSaved(true)
                container.settings.setStealthLevel(StealthLevel.valueOf(level))
                container.settings.setOnboardingDone(true)
                busy = false
                onDone()
            }
        } else {
            scope.launch {
                container.settings.setStealthLevel(StealthLevel.valueOf(level))
                container.settings.setOnboardingDone(true)
                onDone()
            }
        }
    }

    ScreenColumn {
        Text("// onboarding ${page + 1} of 3", style = MaterialTheme.typography.labelSmall, color = TextDim)

        when (page) {
            0 -> Panel(border = Amber) {
                Column {
                    Text("THE HONEST CARD", style = MaterialTheme.typography.titleSmall, color = Amber)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Your bank always knows your own account number. Every paisa that lands carries the payee digits to your bank, and the source bank always knows the account it debited.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.typography.bodyMedium.color
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "What GhostBank actually changes: the destination digits are masked behind virtual accounts, each account is hit from its own burned fingerprint, and the traffic comes out of a rotating exit so the trail never lines up. That is masking and unlinkability — not magic.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextDim
                    )
                }
            }

            1 -> {
                Text("Pick your stealth level. You can move it later in Settings.", style = MaterialTheme.typography.bodyMedium, color = TextDim)
                StealthLevel.values().forEach { option ->
                    val selected = level == option.name
                    Panel(border = if (selected) levelColor(option) else Hairline) {
                        Column(Modifier.fillMaxWidth().clickableNoRipple { level = option.name }) {
                            Text(option.name, style = MaterialTheme.typography.titleSmall, color = levelColor(option), fontWeight = FontWeight.Bold)
                            Text(
                                when (option) {
                                    StealthLevel.STANDARD -> "Browser fingerprint, random pacing, proxy optional."
                                    StealthLevel.DEEP -> "Adds proxy rotation, a fresh session per run, decoy browsing and velocity caps."
                                    StealthLevel.GHOST -> "Everything below, and the proxy is mandatory — no exit means no traffic at all."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextDim,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            else -> {
                Text("Where should the money land? This is the fixed destination for every leg.", style = MaterialTheme.typography.bodyMedium, color = TextDim)
                TerminalField("ACCOUNT NUMBER", number, { number = it.filter { c -> c.isLetterOrDigit() }.take(18) }, numeric = true)
                TerminalField("IFSC", ifsc, { ifsc = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(11) }, error = hint)
                Text(
                    BranchResolver.resolve(ifsc)?.let { "${it.bankName} · ${it.branchLabel}" } ?: "branch resolves once the IFSC is valid",
                    style = MaterialTheme.typography.labelSmall,
                    color = Cyan
                )
                TerminalField("ACCOUNT HOLDER NAME", holder, { holder = it.take(40) })
                if (error != null) Text(error!!, style = MaterialTheme.typography.bodySmall, color = Red)
            }
        }

        Spacer(Modifier.height(4.dp))
        Column {
            if (page < 2) {
                TerminalButton(if (page == 0) "continue" else "next", { page += 1 })
                Spacer(Modifier.height(8.dp))
            }
            if (page == 2) {
                TerminalButton(if (busy) "saving…" else "save destination and enter", { finish(true) }, enabled = !busy)
                Spacer(Modifier.height(8.dp))
            }
            if (page > 0) {
                TerminalButton("back", { page -= 1 }, color = TextDim)
                Spacer(Modifier.height(8.dp))
            }
            TerminalButton("skip — set up later", { finish(page == 2) }, color = TextDim, enabled = !busy)
        }
        EmptyState("GhostBank only talks to the banks. No analytics, no cloud.", null, null)
        Pill("no app signature is ever sent", Green)
    }
}
