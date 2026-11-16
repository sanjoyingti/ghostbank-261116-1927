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
import com.arin.ghostbank.ui.components.BadgeChip
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.ScreenColumn
import com.arin.ghostbank.ui.components.SectionTitle
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.badgeFor
import com.arin.ghostbank.ui.components.clickableNoRipple
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Hairline
import com.arin.ghostbank.ui.theme.InkCard
import com.arin.ghostbank.ui.theme.TextDim
import com.arin.ghostbank.ui.theme.TextPrimary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class BanksVm(container: AppContainer) : ViewModel() {

    val destination: StateFlow<AccountEntity?> = container.accounts.destination()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val lastBadges: StateFlow<Map<String, String>> = container.runs.recent()
        .map { runs -> runs.groupBy { it.bankCode }.mapValues { it.value.first().badge } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { BanksVm(container) }
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
    val vm: BanksVm = viewModel(factory = BanksVm.factory(container))
    val destination by vm.destination.collectAsStateWithLifecycle()
    val badges by vm.lastBadges.collectAsStateWithLifecycle()

    ScreenColumn(scrollable = false) {
        val dest = destination
        if (dest == null) {
            Panel(border = Amber) {
                Column {
                    Text("NO DESTINATION YET", style = MaterialTheme.typography.titleSmall, color = Amber)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Money needs somewhere to land. Save your account number and IFSC once — every leg then targets it.",
                        style = MaterialTheme.typography.bodySmall, color = TextDim
                    )
                    Spacer(Modifier.height(10.dp))
                    TerminalButton("set destination account", onDestination, color = Amber)
                }
            }
        } else {
            Panel(border = Green) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("DESTINATION", style = MaterialTheme.typography.labelSmall, color = Green)
                        Spacer(Modifier.weight(1f))
                        Text(dest.bankCode, style = MaterialTheme.typography.labelSmall, color = TextDim)
                    }
                    Text(Ids.maskNumber(dest.number), style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text("${dest.ifsc} · ${dest.holderName ?: "no holder name"}", style = MaterialTheme.typography.bodySmall, color = TextDim)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TerminalButton("edit", onDestination, color = TextDim, modifier = Modifier.weight(1f))
                        TerminalButton("settings", onSettings, color = TextDim, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Text(
            "27 BANKS · CAPS AND RAIL NOTES CACHED ON DEVICE",
            style = MaterialTheme.typography.labelSmall, color = TextDim
        )

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BankGroup.entries.forEach { group ->
                item {
                    SectionTitle(
                        when (group) {
                            BankGroup.PUBLIC -> "PUBLIC SECTOR"
                            BankGroup.PRIVATE -> "PRIVATE"
                            BankGroup.SFB -> "SMALL FINANCE BANKS"
                        }
                    )
                }
                items(Banks.all.filter { it.group == group }, key = { it.code }) { bank ->
                    BankRow(bank = bank, badge = badges[bank.code], onClick = { onBank(bank.code) })
                }
            }
            item {
                Text(
                    "A bank marked SIMULATED has not answered a run yet — the badge flips to LIVE only on a real server confirmation.",
                    style = MaterialTheme.typography.bodySmall, color = TextDim,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun BankRow(bank: Bank, badge: String?, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(InkCard)
            .border(1.dp, Hairline, RoundedCornerShape(6.dp))
            .clickableNoRipple(onClick)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(bank.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.weight(1f))
            BadgeChip(badgeFor(badge))
            Spacer(Modifier.width(6.dp))
            Text(bank.code, style = MaterialTheme.typography.labelSmall, color = TextDim)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("cap ₹${"%,d".format(bank.capPaise / 100)}", style = MaterialTheme.typography.bodySmall, color = TextDim)
            Spacer(Modifier.width(10.dp))
            Text(if (bank.impsSupported) "IMPS ✓" else "IMPS ✗", style = MaterialTheme.typography.bodySmall, color = if (bank.impsSupported) Green else Amber)
        }
        Spacer(Modifier.height(4.dp))
        Text(bank.quirk, style = MaterialTheme.typography.bodySmall, color = TextDim)
    }
}
