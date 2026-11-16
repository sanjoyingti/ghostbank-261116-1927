package com.arin.ghostbank.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.Settings
import com.arin.ghostbank.model.Badge
import com.arin.ghostbank.model.Bank
import com.arin.ghostbank.model.BankGroup
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.Ids
import com.arin.ghostbank.ui.components.BadgeChip
import com.arin.ghostbank.ui.components.Meter
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.Pill
import com.arin.ghostbank.ui.components.SectionTitle
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.levelColor
import com.arin.ghostbank.ui.components.rupeesText
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Hairline
import com.arin.ghostbank.ui.theme.Ink
import com.arin.ghostbank.ui.theme.InkCard
import com.arin.ghostbank.ui.theme.TextDim
import com.arin.ghostbank.ui.theme.TextPrimary

@Composable
fun BanksScreen(
    container: AppContainer,
    onBank: (String) -> Unit,
    onDestination: () -> Unit,
    onSettings: () -> Unit
) {
    val destination by container.accounts.destination().collectAsStateWithLifecycle(null)
    val settings by container.settings.flow.collectAsStateWithLifecycle(Settings())

    Column(
        Modifier.fillMaxSize().background(Ink).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Panel(border = if (destination == null) Amber.copy(alpha = 0.5f) else Green.copy(alpha = 0.35f)) {
            Column {
                if (destination == null) {
                    Text("NO DESTINATION SAVED", style = MaterialTheme.typography.labelSmall, color = Amber)
                    Text(
                        "Every leg needs a landing account. Add yours once and it stays the fixed destination.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim
                    )
                    Spacer(Modifier.height(8.dp))
                    TerminalButton("ADD DESTINATION", onDestination, color = Amber)
                } else {
                    Text("DESTINATION", style = MaterialTheme.typography.labelSmall, color = Green)
                    Text(Ids.maskNumber(destination!!.number), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(destination!!.ifsc, style = MaterialTheme.typography.bodySmall, color = TextDim)
                    Spacer(Modifier.height(6.dp))
                    TerminalButton("CHANGE", onDestination, color = TextDim)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Pill("LEVEL ${settings.stealthLevel.name}", levelColor(settings.stealthLevel))
            Pill(if (settings.torMode) "TOR ON" else "TOR OFF", Cyan)
            Box(Modifier.weight(1f))
            Pill("${settings.maxAccountsPerHour}/h cap", TextDim)
        }

        SectionTitle("27 banks on the rail", "tap a bank to open its transfer screen — caps and quirks are shown per row")

        BankGroup.entries.forEach { group ->
            Text(
                when (group) {
                    BankGroup.PUBLIC -> "PUBLIC SECTOR"
                    BankGroup.PRIVATE -> "PRIVATE"
                    BankGroup.SFB -> "SMALL FINANCE BANKS"
                },
                style = MaterialTheme.typography.labelSmall,
                color = Cyan,
                modifier = Modifier.padding(top = 8.dp)
            )
            Banks.all.filter { it.group == group }.forEach { bank ->
                BankRow(bank, enabled = destination != null) { onBank(bank.code) }
            }
        }

        Spacer(Modifier.height(10.dp))
        TerminalButton("SETTINGS", onSettings, color = TextDim)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BankRow(bank: Bank, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(InkCard)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(bank.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
            Text(bank.code, style = MaterialTheme.typography.labelSmall, color = TextDim)
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Pill("cap ${rupeesText(bank.capPaise)}", Amber)
            if (bank.impsSupported) BadgeChip(Badge.SIMULATED) else Pill("no IMPS", TextDim)
            Text("IMPS P2A", style = MaterialTheme.typography.labelSmall, color = TextDim)
        }
        Spacer(Modifier.height(4.dp))
        Text(bank.quirk, style = MaterialTheme.typography.bodySmall, color = TextDim)
        Spacer(Modifier.height(6.dp))
        Meter(0.35f, Hairline)
    }
}
