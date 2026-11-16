package com.arin.ghostbank.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.arin.ghostbank.ui.components.SectionTitle
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.TerminalField
import com.arin.ghostbank.ui.components.clickableNoRipple
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Red
import com.arin.ghostbank.ui.theme.TextDim
import com.arin.ghostbank.ui.vm.BanksViewModel

@Composable
fun BanksScreen(
    container: AppContainer,
    onBank: (String) -> Unit,
    onDestination: () -> Unit,
    onSettings: () -> Unit
) {
    val vm: BanksViewModel = viewModel(factory = BanksViewModel.factory(container))
    val destination by vm.destination.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()

    val filtered = Banks.all.filter {
        query.isBlank() || it.name.contains(query, true) || it.code.contains(query, true)
    }

    ScreenColumn {
        if (!online) {
            Panel(border = Amber) {
                Column {
                    Text("offline", style = MaterialTheme.typography.titleSmall, color = Amber)
                    Text(
                        "The bank list is cached. Runs you start now are queued and marked until the network returns.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim
                    )
                }
            }
        }

        Panel(border = if (destination == null) Amber else Green) {
            Column {
                Text("DESTINATION", style = MaterialTheme.typography.labelSmall, color = TextDim)
                if (destination == null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "No account saved. Every leg needs a fixed destination before it can move anything.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim
                    )
                    Spacer(Modifier.height(8.dp))
                    TerminalButton("set destination account", onDestination, color = Amber)
                } else {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${Ids.maskNumber(destination!!.number)} · ${destination!!.ifsc}",
                        style = MaterialTheme.typography.titleSmall,
                        color = Green
                    )
                    Spacer(Modifier.height(6.dp))
                    TerminalButton("edit destination", onDestination, color = Cyan)
                }
            }
        }

        TerminalField("search 27 banks", query, vm::setQuery, placeholder = "sbi, hdfc, kotak…")

        if (filtered.isEmpty()) {
            EmptyState("No bank matches \"$query\".", "clear the search") { vm.setQuery("") }
        }

        BankGroup.values().forEach { group ->
            val groupBanks = filtered.filter { it.group == group }
            if (groupBanks.isNotEmpty()) {
                SectionTitle(
                    when (group) {
                        BankGroup.PUBLIC -> "PUBLIC SECTOR"
                        BankGroup.PRIVATE -> "PRIVATE"
                        BankGroup.SFB -> "SMALL FINANCE BANKS"
                    },
                    "${groupBanks.size} bank(s)"
                )
                groupBanks.forEach { bank ->
                    BankRow(bank) { onBank(bank.code) }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        TerminalButton("settings and stealth dial", onSettings, color = Cyan)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BankRow(bank: Bank, onClick: () -> Unit) {
    Panel(modifier = Modifier.clickableNoRipple(onClick)) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(bank.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
                Text(bank.code, style = MaterialTheme.typography.labelSmall, color = TextDim)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill("cap ₹${bank.capPaise / 100}", Cyan)
                Pill(if (bank.impsSupported) "IMPS 24x7" else "no IMPS", if (bank.impsSupported) Green else Red)
                Pill("P2A", TextDim)
            }
            Spacer(Modifier.height(6.dp))
            Text(bank.quirk, style = MaterialTheme.typography.bodySmall, color = TextDim)
        }
    }
}
