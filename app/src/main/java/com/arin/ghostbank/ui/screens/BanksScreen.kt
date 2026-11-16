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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.model.Bank
import com.arin.ghostbank.model.BankGroup
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.Ids
import com.arin.ghostbank.ui.components.EmptyState
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.Pill
import com.arin.ghostbank.ui.components.ScreenColumn
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.TerminalField
import com.arin.ghostbank.ui.components.clickableNoRipple
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Hairline
import com.arin.ghostbank.ui.theme.InkCard
import com.arin.ghostbank.ui.theme.TextDim
import com.arin.ghostbank.ui.vm.DestinationViewModel

@Composable
fun BanksScreen(
    container: AppContainer,
    onBank: (String) -> Unit,
    onDestination: () -> Unit,
    onSettings: () -> Unit
) {
    val destVm: DestinationViewModel = viewModel(factory = DestinationViewModel.factory(container))
    val destination by destVm.existing.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    ScreenColumn {
        if (destination == null) {
            Panel(border = Amber) {
                Column {
                    Text("NO DESTINATION SET", style = MaterialTheme.typography.titleSmall, color = Amber)
                    Spacer(Modifier.height(6.dp))
                    Text("Every leg needs one fixed account to credit. Set it once.", style = MaterialTheme.typography.bodySmall, color = TextDim)
                    Spacer(Modifier.height(10.dp))
                    TerminalButton("SAVE DESTINATION", onDestination, color = Amber)
                }
            }
        } else {
            Panel(border = Green) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("DESTINATION · MASKED", style = MaterialTheme.typography.labelSmall, color = Green)
                        Text(Ids.maskNumber(destination!!.number), style = MaterialTheme.typography.bodyMedium)
                        Text(destination!!.ifsc, style = MaterialTheme.typography.labelSmall, color = TextDim)
                    }
                    Pill("EDIT", Cyan, Modifier.clickableNoRipple(onDestination))
                }
            }
        }

        TerminalField("FILTER BANKS", query, { query = it }, placeholder = "SBI, HDFC, SFB…")

        BankGroup.entries.forEach { group ->
            val banks = Banks.all.filter {
                it.group == group && (query.isBlank() || it.name.contains(query, true) || it.code.contains(query, true))
            }
            if (banks.isNotEmpty()) {
                Text(
                    when (group) {
                        BankGroup.PUBLIC -> "PUBLIC SECTOR"
                        BankGroup.PRIVATE -> "PRIVATE"
                        BankGroup.SFB -> "SMALL FINANCE BANKS"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim
                )
                banks.forEach { bank -> BankRow(bank) { onBank(bank.code) } }
            }
        }

        if (Banks.all.none { query.isBlank() || it.name.contains(query, true) || it.code.contains(query, true) }) {
            EmptyState("No bank matches \"$query\".", "clear the filter") { query = "" }
        }

        Text(
            "Caps are per-transaction ceilings quoted for retail net banking. They are shown for guidance; the bank's answer always wins.",
            style = MaterialTheme.typography.labelSmall,
            color = TextDim
        )
    }
}

@Composable
private fun BankRow(bank: Bank, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(InkCard)
            .border(1.dp, Hairline, RoundedCornerShape(5.dp))
            .clickableNoRipple(onClick)
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(bank.name, style = MaterialTheme.typography.bodyMedium)
            Text("₹${bank.capPaise / 100}", style = MaterialTheme.typography.bodySmall, color = Amber)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(bank.code, Cyan)
            Spacer(Modifier.padding(4.dp))
            Pill(if (bank.impsSupported) "IMPS P2A" else "NO IMPS", if (bank.impsSupported) Green else Amber)
        }
        Spacer(Modifier.height(6.dp))
        Text(bank.quirk, style = MaterialTheme.typography.labelSmall, color = TextDim)
    }
}
