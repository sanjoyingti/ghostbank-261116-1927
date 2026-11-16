=== ABOUT: Fixed the missing bank picker: 27 banks grouped, each with its cap, IMPS flag and rail quirk, showing your saved destination ===
package com.arin.ghostbank.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.Settings
import com.arin.ghostbank.model.Bank
import com.arin.ghostbank.model.BankGroup
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.Ids
import com.arin.ghostbank.ui.components.KeyValue
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.Pill
import com.arin.ghostbank.ui.components.ScreenColumn
import com.arin.ghostbank.ui.components.SectionTitle
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.clickableNoRipple
import com.arin.ghostbank.ui.components.paiseToCompact
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Hairline
import com.arin.ghostbank.ui.theme.Red
import com.arin.ghostbank.ui.theme.TextDim

@Composable
fun BanksScreen(
    container: AppContainer,
    onBank: (String) -> Unit,
    onDestination: () -> Unit,
    onSettings: () -> Unit
) {
    val destination by container.accounts.destination().collectAsStateWithLifecycle(initialValue = null)
    val registryCount by container.registry.count().collectAsStateWithLifecycle(initialValue = 0)
    val settings by container.settings.flow.collectAsStateWithLifecycle(initialValue = Settings())

    ScreenColumn {
        val dest = destination
        Panel(border = if (dest == null) Amber else Green) {
            Column {
                Text("DESTINATION ACCOUNT", style = MaterialTheme.typography.labelSmall, color = if (dest == null) Amber else Green)
                Spacer(Modifier.height(6.dp))
                if (dest == null) {
                    Text("No account saved. Every leg needs a fixed destination before anything can run.", style = MaterialTheme.typography.bodySmall, color = TextDim)
                    Spacer(Modifier.height(8.dp))
                    TerminalButton("set destination", onDestination, color = Amber)
                } else {
                    Text("${Ids.maskNumber(dest.number)}  ·  ${dest.ifsc}", style = MaterialTheme.typography.titleSmall, color = Green)
                    Text(dest.holderName ?: "name not recorded", style = MaterialTheme.typography.bodySmall, color = TextDim)
                    Spacer(Modifier.height(8.dp))
                    TerminalButton("change destination", onDestination, color = TextDim)
                }
            }
        }

        KeyValue("stealth level", settings.stealthLevel.name, Green)
        KeyValue("dedupe registry", "$registryCount hashed accounts", Cyan)
        KeyValue("exit", if (settings.torMode) "Tor (127.0.0.1:9050)" else (settings.lastExitIp ?: "direct / none"), TextDim)

        SectionTitle("Targets", "27 banks. Tap one to open its transfer or harvest screen.")
        BankGroup.values().forEach { group ->
            Text(
                when (group) {
                    BankGroup.PUBLIC -> "// PUBLIC SECTOR"
                    BankGroup.PRIVATE -> "// PRIVATE"
                    BankGroup.SFB -> "// SMALL FINANCE BANKS"
                },
                style = MaterialTheme.typography.labelSmall,
                color = TextDim,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
            )
            Banks.all.filter { it.group == group }.forEach { bank -> BankRow(bank) { onBank(bank.code) } }
        }

        TerminalButton("settings", onSettings, color = TextDim)
    }
}

@Composable
private fun BankRow(bank: Bank, onClick: () -> Unit) {
    Panel(border = Hairline) {
        Column(Modifier.fillMaxWidth().clickableNoRipple(onClick)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(bank.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.typography.titleSmall.color)
                Spacer(Modifier.width(8.dp))
                Pill(bank.code, Cyan)
            }
            Spacer(Modifier.height(4.dp))
            Row {
                Text("cap ${paiseToCompact(bank.capPaise)}", style = MaterialTheme.typography.labelSmall, color = Amber)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (bank.impsSupported) "IMPS P2A 24x7" else "IMPS unavailable",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (bank.impsSupported) Green else Red
                )
                Spacer(Modifier.width(10.dp))
                Text(bank.country, style = MaterialTheme.typography.labelSmall, color = TextDim)
            }
            Spacer(Modifier.height(4.dp))
            Text(bank.quirk, style = MaterialTheme.typography.bodySmall, color = TextDim)
        }
    }
}
