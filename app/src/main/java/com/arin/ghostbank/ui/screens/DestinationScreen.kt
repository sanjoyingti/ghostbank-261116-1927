package com.arin.ghostbank.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.ui.components.BadgeChip
import com.arin.ghostbank.ui.components.BackBar
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.ScreenColumn
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.TerminalField
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Hairline
import com.arin.ghostbank.ui.theme.TextDim
import com.arin.ghostbank.ui.theme.TextPrimary
import com.arin.ghostbank.model.Badge
import com.arin.ghostbank.ui.vm.DestinationViewModel

@Composable
fun DestinationScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: DestinationViewModel = viewModel(factory = DestinationViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    ScreenColumn {
        BackBar("destination account", "one active destination for every leg", onBack)

        Panel(border = Amber) {
            Column {
                Text("the honest part", style = MaterialTheme.typography.titleSmall, color = Amber)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Your bank always knows this account number. Masking hides it from the screen, not from your bank. What GhostBank removes is the link between the many source accounts and this one destination.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim
                )
            }
        }

        TerminalField(
            label = "ACCOUNT NUMBER",
            value = state.number,
            onChange = vm::setNumber,
            placeholder = "digits only",
            error = state.numberError,
            numeric = true
        )
        TerminalField(
            label = "IFSC",
            value = state.ifsc,
            onChange = vm::setIfsc,
            placeholder = "4 letters, 0, 6 characters",
            error = state.ifscError
        )
        TerminalField(
            label = "NAME (OPTIONAL)",
            value = state.holder,
            onChange = vm::setHolder,
            placeholder = "account holder"
        )

        val branch = state.branch
        if (branch != null) {
            Panel(border = Hairline) {
                Column {
                    Text("resolved", style = MaterialTheme.typography.labelSmall, color = Green)
                    Spacer(Modifier.height(4.dp))
                    Text(branch.bankName, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    Text(branch.branchLabel, style = MaterialTheme.typography.bodySmall, color = TextDim)
                }
            }
        }

        TerminalButton(
            if (state.checking) "checking…" else "run payee name-check",
            vm::checkPayee,
            enabled = !state.checking,
            color = com.arin.ghostbank.ui.theme.Cyan,
            modifier = Modifier.fillMaxWidth()
        )

        val checkText = state.checkText
        if (checkText != null) {
            Panel(border = if (state.checkLive) Green else Amber) {
                Column {
                    BadgeChip(if (state.checkLive) Badge.LIVE else Badge.SIMULATED)
                    Spacer(Modifier.height(6.dp))
                    Text(checkText, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                }
            }
        }

        TerminalButton("SAVE DESTINATION", vm::save, modifier = Modifier.fillMaxWidth())

        val masked = state.savedMasked
        if (masked != null) {
            Text("saved · " + masked, style = MaterialTheme.typography.bodySmall, color = Green)
        }

        Text(
            "Branch names come from the local IFSC table, never from a lookup service — nothing about this screen leaves the phone.",
            style = MaterialTheme.typography.labelSmall,
            color = TextDim
        )
    }
}
