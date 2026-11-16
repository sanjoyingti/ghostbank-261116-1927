=== ABOUT: The bank list, grouped public, private and small finance, each with its cap and rail quirk ===
package com.arin.ghostbank.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.arin.ghostbank.data.db.AccountEntity
import com.arin.ghostbank.model.Bank
import com.arin.ghostbank.model.BankGroup
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.Ids
import com.arin.ghostbank.ui.components.Pill
import com.arin.ghostbank.ui.components.SectionTitle
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.paiseToRupeeText
import com.arin.ghostbank.ui.components.timeText
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Hairline
import com.arin.ghostbank.ui.theme.Ink
import com.arin.ghostbank.ui.theme.InkCard
import com.arin.ghostbank.ui.theme.TextDim
import com.arin.ghostbank.ui.theme.TextPrimary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class BanksViewModel(container: AppContainer) : ViewModel() {
    val destination: StateFlow<AccountEntity?> = container.accounts.destination()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { BanksViewModel(container) }
        }
    }
}

@Composable
fun BanksScreen(
    container: AppContainer,
    onBank: (String) -> Unit,
    onDestination: () -> Unit,
    onSettings: () -> Unit
) {
    val vm: BanksViewModel = viewModel(factory = BanksViewModel.factory(container))
    val destination by vm.destination.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(InkCard)
                    .border(1.dp, if (destination == null) Amber else Green, RoundedCornerShape(6.dp))
                    .padding(12.dp)
            ) {
                Text("DESTINATION", style = MaterialTheme.typography.labelSmall, color = TextDim)
                if (destination == null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "No account saved yet — every leg needs a fixed landing account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Amber
                    )
                    Spacer(Modifier.height(10.dp))
                    TerminalButton("ADD DESTINATION ACCOUNT", onDestination)
                } else {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${Ids.maskNumber(destination!!.number)} · ${destination!!.ifsc}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Green
                    )
                    Text(
                        "saved ${timeText(destination!!.createdAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDim
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TerminalButton("EDIT", onDestination, color = Cyan, modifier = Modifier.weight(1f))
                        TerminalButton("SETTINGS", onSettings, color = TextDim, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        item { SectionTitle("PICK A TARGET", "each row is one live bank portal") }

        BankGroup.values().forEach { group ->
            item {
                Text(
                    when (group) {
                        BankGroup.PUBLIC -> "// PUBLIC SECTOR"
                        BankGroup.PRIVATE -> "// PRIVATE"
                        BankGroup.SFB -> "// SMALL FINANCE BANKS"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(Banks.all.filter { it.group == group }) { bank ->
                BankRow(bank) { onBank(bank.code) }
            }
        }

        item {
            Text(
                "Caps are the retail per-transaction ceilings published for net banking. The rail is IMPS P2A only — real time, 24x7, no NEFT batches.",
                style = MaterialTheme.typography.bodySmall,
                color = TextDim,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
    }
}

@Composable
private fun BankRow(bank: Bank, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(InkCard)
            .border(1.dp, Hairline, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(bank.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Spacer(Modifier.width(8.dp))
                Pill(bank.code, Cyan)
                if (bank.impsSupported) {
                    Spacer(Modifier.width(6.dp))
                    Pill("IMPS", Green)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "cap ${paiseToRupeeText(bank.capPaise)} per txn",
                style = MaterialTheme.typography.bodySmall,
                color = Amber
            )
            Text(bank.quirk, style = MaterialTheme.typography.bodySmall, color = TextDim)
        }
        Text("›", style = MaterialTheme.typography.titleMedium, color = TextDim)
    }
}
