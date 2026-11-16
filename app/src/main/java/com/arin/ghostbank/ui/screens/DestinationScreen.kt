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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.ui.components.BackBar
import com.arin.ghostbank.ui.components.BadgeChip
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.ScreenColumn
import com.arin.ghostbank.ui.components.SectionTitle
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.TerminalField
import com.arin.ghostbank.model.Badge
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Hairline
import com.arin.ghostbank.ui.theme.Red
import com.arin.ghostbank.ui.theme.TextDim
import com.arin.ghostbank.ui.vm.DestinationViewModel

@Composable
fun DestinationScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: DestinationViewModel = viewModel(factory = DestinationViewModel.factory(container))
    val ui by vm.ui.collectAsStateWithLifecycle()

    Column {
        BackBar("Destination account", "the fixed end of every leg", onBack)
        ScreenColumn {
            Panel(border = Amber) {
                Text(
                    "The destination bank always knows your real account number. GhostBank masks the digits on the screen and rotates virtual accounts on the rail to keep the trail unlinkable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim
                )
            }

            if (ui.savedMasked != null) {
                Panel(border = Green) {
                    Column {
                        Text("SAVED DESTINATION", style = MaterialTheme.typography.labelSmall, color = TextDim)
                        Spacer(Modifier.height(4.dp))
                        Text("${ui.savedMasked} · ${ui.savedIfsc}", style = MaterialTheme.typography.titleSmall, color = Green)
                    }
                }
            }

            SectionTitle("Account number", "9–18 digits, digits only")
            TerminalField(
                label = "account number",
                value = ui.number,
                onChange = vm::setNumber,
                numeric = true,
                placeholder = "e.g. 50100234987121"
            )

            TerminalField(
                label = "IFSC",
                value = ui.ifsc,
                onChange = vm::setIfsc,
                error = ui.ifscError,
                placeholder = "HDFC0000123"
            )

            if (ui.bankName != null) {
                Panel(border = Hairline) {
                    Column {
                        Text(ui.bankName!!, style = MaterialTheme.typography.titleSmall, color = Cyan)
                        Text(ui.branchLabel ?: "branch code pending", style = MaterialTheme.typography.bodySmall, color = TextDim)
                    }
                }
            }

            TerminalField("holder name (optional)", ui.holder, vm::setHolder, placeholder = "as printed at the bank")

            TerminalButton(
                if (ui.checking) "asking the bank…" else "run payee name-check",
                vm::checkPayee,
                enabled = !ui.checking && ui.ifsc.length == 11,
                color = Cyan
            )

            if (ui.nameCheck != null) {
                Panel(border = if (ui.nameCheckLive) Green else Amber) {
                    Column {
                        BadgeChip(if (ui.nameCheckLive) Badge.LIVE else Badge.SIMULATED)
                        Spacer(Modifier.height(6.dp))
                        Text(ui.nameCheck!!, style = MaterialTheme.typography.bodySmall, color = TextDim)
                    }
                }
            }

            if (ui.message != null) {
                Panel(border = if (ui.message!!.startsWith("saved")) Green else Red) {
                    Text(ui.message!!, style = MaterialTheme.typography.bodySmall, color = TextDim)
                }
            }

            TerminalButton(
                "save as the only destination",
                vm::save,
                enabled = ui.number.length >= 9 && ui.ifsc.length == 11,
                color = Green
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
