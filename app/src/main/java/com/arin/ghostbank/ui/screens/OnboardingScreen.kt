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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.model.StealthLevel
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.ScreenColumn
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.TerminalField
import com.arin.ghostbank.ui.components.clickableNoRipple
import com.arin.ghostbank.ui.components.levelColor
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Hairline
import com.arin.ghostbank.ui.theme.InkCard
import com.arin.ghostbank.ui.theme.Red
import com.arin.ghostbank.ui.theme.TextDim
import com.arin.ghostbank.ui.theme.TextPrimary
import com.arin.ghostbank.ui.vm.OnboardingViewModel

@Composable
fun OnboardingScreen(container: AppContainer, onDone: () -> Unit) {
    val vm: OnboardingViewModel = viewModel(factory = OnboardingViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.finished) {
        if (state.finished) onDone()
    }

    ScreenColumn {
        Text("// ghostbank", style = MaterialTheme.typography.titleMedium, color = Green)
        Text("page ${state.page + 1} of 3", style = MaterialTheme.typography.labelSmall, color = TextDim)
        Spacer(Modifier.height(4.dp))

        when (state.page) {
            0 -> {
                Panel(border = Amber) {
                    Column {
                        Text("the bank always knows", style = MaterialTheme.typography.titleSmall, color = Amber)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Your destination bank always knows your account number, and the source bank always knows the account it debited. Nothing changes that.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "What GhostBank does is make the trail unlinkable: one paisa per account, once ever, over a browser-grade connection from a matched exit, with a fingerprint that is burned after every run.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDim
                        )
                    }
                }
                Panel(border = Hairline) {
                    Column {
                        Text("honest limits", style = MaterialTheme.typography.titleSmall, color = Cyan)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "No app can log into your net banking by itself. GhostBank talks to the live bank hosts, holds the seven stage pipeline open until the server confirms, and marks LIVE only when a server answered — everything else is plainly SIMULATED.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDim
                        )
                    }
                }
            }
            1 -> {
                Text("how deep do you want to hide?", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                StealthLevel.values().forEach { level ->
                    val copy = when (level) {
                        StealthLevel.STANDARD -> "Browser fingerprint, random pacing, proxy optional."
                        StealthLevel.DEEP -> "Adds proxy rotation, a fresh session per run, decoy browsing and velocity caps."
                        StealthLevel.GHOST -> "Everything below, but the proxy is mandatory — no exit, no traffic."
                    }
                    val selected = state.level == level
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(InkCard)
                            .border(1.dp, if (selected) levelColor(level) else Hairline, RoundedCornerShape(6.dp))
                            .clickableNoRipple { vm.setLevel(level) }
                            .padding(12.dp)
                    ) {
                        Text(level.name, style = MaterialTheme.typography.titleSmall, color = levelColor(level))
                        Spacer(Modifier.height(4.dp))
                        Text(copy, style = MaterialTheme.typography.bodySmall, color = TextDim)
                    }
                }
            }
            else -> {
                Text("where should the money land?", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                TerminalField(
                    label = "ACCOUNT NUMBER",
                    value = state.number,
                    onChange = vm::setNumber,
                    placeholder = "your account number",
                    error = state.error,
                    numeric = true
                )
                TerminalField(
                    label = "IFSC",
                    value = state.ifsc,
                    onChange = vm::setIfsc,
                    placeholder = "HDFC0000123"
                )
                TerminalField(
                    label = "NAME (OPTIONAL)",
                    value = state.holder,
                    onChange = vm::setHolder,
                    placeholder = "account holder"
                )
                Text(
                    "The destination is fixed for every leg. You can change it later from the banks screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.page > 0) {
                TerminalButton("BACK", vm::back, color = TextDim, modifier = Modifier.weight(1f))
            }
            if (state.page < 2) {
                TerminalButton("NEXT", vm::next, modifier = Modifier.weight(1f))
            } else {
                TerminalButton(
                    if (state.saving) "SAVING…" else "FINISH SETUP",
                    vm::finish,
                    enabled = !state.saving,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (state.page == 2) {
            TerminalButton("skip account for now", {
                vm.finish()
                onDone()
            }, color = Red, modifier = Modifier.fillMaxWidth())
        }
        Text(
            "GhostBank sends nothing anywhere except the bank hosts.",
            style = MaterialTheme.typography.labelSmall,
            color = TextDim
        )
    }
}
