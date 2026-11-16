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
import com.arin.ghostbank.ui.components.SectionTitle
import com.arin.ghostbank.ui.components.clickableNoRipple
import com.arin.ghostbank.ui.components.paiseToRupees
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Hairline
import com.arin.ghostbank.ui.theme.InkCard
import com.arin.ghostbank.ui.theme.Red
import com.arin.ghostbank.ui.theme.TextDim
import com.arin.ghostbank.ui.theme.TextPrimary
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

    ScreenColumn {
        val dest = destination
        if (dest == null) {
            Panel(border = Amber) {
                Column {
                    Text("no destination account", style = MaterialTheme.typography.titleSmall, color = Amber)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Every leg credits your account. Save it once and it is masked to the last four digits.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("SET DESTINATION →", style = MaterialTheme.typography.labelLarge, color = Amber,
                        modifier = Modifier.clickableNoRipple(onDestination))
                }
            }
        } else {
            Panel(border = Green) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("DESTINATION LOCKED", style = MaterialTheme.typography.labelSmall, color = Green)
                        Text(
                            Ids.maskNumber(dest.number) + " · " + dest.ifsc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Text(dest.holderName ?: "unnamed", style = MaterialTheme.typography.bodySmall, color = TextDim)
                    }
                    Text("EDIT", style = MaterialTheme.typography.labelLarge, color = Cyan,
                        modifier = Modifier.clickableNoRipple(onDestination))
                }
            }
        }

        SectionTitle("pick a target", "each bank shows its own per-transaction cap, IMPS support and rail quirk")

        BankGroup.values().forEach { group ->
            Text(
                when (group) {
                    BankGroup.PUBLIC -> "// public sector"
                    BankGroup.PRIVATE -> "// private"
                    BankGroup.SFB -> "// small finance"
                },
                style = MaterialTheme.typography.labelSmall,
                color = Cyan
            )
            Banks.all.filter { it.group == group }.forEach { bank ->
                BankRow(bank) { onBank(bank.code) }
            }
        }

        if (Banks.all.isEmpty()) {
            EmptyState("no banks loaded", "open settings", onSettings)
        }
        Text("caps are shown in rupees; all amounts are held internally in paise", style = MaterialTheme.typography.labelSmall, color = TextDim)
    }
}

@Composable
private fun BankRow(bank: Bank, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(InkCard)
            .border(1.dp, Hairline, RoundedCornerShape(6.dp))
            .clickableNoRipple(onClick)
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(bank.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.weight(1f))
            Pill(bank.code, Cyan)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("CAP " + paiseToRupees(bank.capPaise), Amber)
            Pill(if (bank.impsSupported) "IMPS 24x7" else "IMPS unavailable", if (bank.impsSupported) Green else Red)
        }
        Spacer(Modifier.height(6.dp))
        Text(bank.quirk, style = MaterialTheme.typography.bodySmall, color = TextDim)
    }
}
