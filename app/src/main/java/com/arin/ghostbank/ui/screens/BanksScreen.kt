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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.db.AccountEntity
import com.arin.ghostbank.model.Badge
import com.arin.ghostbank.model.Bank
import com.arin.ghostbank.model.BankGroup
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.Ids
import com.arin.ghostbank.ui.components.BadgeChip
import com.arin.ghostbank.ui.components.EmptyState
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.Pill
import com.arin.ghostbank.ui.components.SectionTitle
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.clickableNoRipple
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Hairline
import com.arin.ghostbank.ui.theme.Ink
import com.arin.ghostbank.ui.theme.TextDim
import com.arin.ghostbank.ui.theme.TextPrimary

@Composable
fun BanksScreen(
    container: AppContainer,
    onBank: (String) -> Unit,
    onDestination: () -> Unit,
    onSettings: () -> Unit
) {
    val destination by container.accounts.destination()
        .collectAsStateWithLifecycle(initialValue = null as AccountEntity?)

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            if (destination == null) {
                Panel(border = Amber) {
                    Column {
                        Text("NO DESTINATION ACCOUNT", style = MaterialTheme.typography.titleSmall, color = Amber)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Every leg needs somewhere to land. Save your account number and IFSC first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDim
                        )
                        Spacer(Modifier.height(10.dp))
                        TerminalButton("SET DESTINATION", onDestination, color = Amber, modifier = Modifier.fillMaxWidth())
                    }
                }
            } else {
                Panel(border = Green) {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("DESTINATION LOCKED", style = MaterialTheme.typography.labelSmall, color = Green)
                            Text("one active", style = MaterialTheme.typography.labelSmall, color = TextDim)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            Ids.maskNumber(destination!!.number) + " · " + destination!!.ifsc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        TerminalButton("edit destination", onDestination, color = TextDim, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        item { SectionTitle("TARGET BANKS", "27 institutions. Pick one to open its transfer screen.") }

        BankGroup.entries.forEach { group ->
            item {
                Text(
                    when (group) {
                        BankGroup.PUBLIC -> "// public sector"
                        BankGroup.PRIVATE -> "// private"
                        BankGroup.SFB -> "// small finance banks"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Cyan,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                )
            }
            items(Banks.all.filter { it.group == group }, key = { it.code }) { bank ->
                BankRow(bank = bank, onClick = { onBank(bank.code) })
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            EmptyState(
                title = "Nothing to run yet? Set a destination, then pick a bank.",
                action = "open settings",
                onAction = onSettings
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BankRow(bank: Bank, onClick: () -> Unit) {
    Panel(border = Hairline, modifier = Modifier.clickableNoRipple(onClick)) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(bank.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text(bank.host, style = MaterialTheme.typography.labelSmall, color = TextDim)
                }
                BadgeChip(Badge.SIMULATED)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("cap " + BankDefaults.rupees(bank.capPaise), Amber)
                Spacer(Modifier.width(6.dp))
                Pill(if (bank.impsSupported) "IMPS P2A" else "no IMPS", if (bank.impsSupported) Green else Amber)
                Spacer(Modifier.width(6.dp))
                Pill(bank.country, Cyan)
            }
            Spacer(Modifier.height(6.dp))
            Text(bank.quirk, style = MaterialTheme.typography.bodySmall, color = TextDim)
        }
    }
}
