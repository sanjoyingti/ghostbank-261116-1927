package com.arin.ghostbank.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import com.arin.ghostbank.model.Ids
import com.arin.ghostbank.ui.components.BackBar
import com.arin.ghostbank.ui.components.KeyValue
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.ScreenColumn
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.TerminalField
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Red
import com.arin.ghostbank.ui.theme.TextDim
import com.arin.ghostbank.ui.vm.DestinationViewModel

@Composable
fun DestinationScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: DestinationViewModel = viewModel(factory = DestinationViewModel.factory(container))
    val number by vm.number.collectAsStateWithLifecycle()
    val ifsc by vm.ifsc.collectAsStateWithLifecycle()
    val holder by vm.holder.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val branch by vm.branch.collectAsStateWithLifecycle()
    val hint by vm.hint.collectAsStateWithLifecycle()
    val existing by vm.existing.collectAsStateWithLifecycle()

    ScreenColumn(scrollable = true) {
        BackBar("DESTINATION ACCOUNT", "one active target for every leg", onBack)

        existing?.let { saved ->
            Panel(border = Green) {
                androidx.compose.foundation.layout.Column {
                    Text("ACTIVE DESTINATION", style = MaterialTheme.typography.labelSmall, color = Green)
                    Spacer(Modifier.height(8.dp))
                    KeyValue("account", Ids.maskNumber(saved.number))
                    KeyValue("ifsc", saved.ifsc)
                    KeyValue("holder", saved.holderName ?: "unverified")
                    KeyValue("bank code", saved.bankCode)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "The destination bank will always know this account number. Masking only keeps it out of sight on screen.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDim
                    )
                }
            }
        }

        Text("REPLACE IT", style = MaterialTheme.typography.titleSmall)
        TerminalField("BENEFICIARY ACCOUNT NUMBER", number, vm::onNumber, numeric = true, placeholder = "9 to 18 digits")
        TerminalField("IFSC", ifsc, vm::onIfsc, placeholder = "HDFC0000123")
        if (hint != null) Text(hint!!, style = MaterialTheme.typography.labelSmall, color = Amber)
        if (branch != null) {
            Text(
                "resolved: ${branch!!.bankName} · ${branch!!.branchLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = Cyan
            )
        }
        TerminalField("ACCOUNT HOLDER", holder, vm::onHolder, placeholder = "used for the payee name-check")
        if (error != null) Text(error!!, style = MaterialTheme.typography.bodySmall, color = Red)
        if (notice != null) Text(notice!!, style = MaterialTheme.typography.bodySmall, color = Green)

        TerminalButton("RUN PAYEE NAME-CHECK & SAVE", vm::save, modifier = Modifier.fillMaxSize().height(46.dp))
        Text(
            "Payee validation follows the bank's own IFSC rule: four letters, a zero, then six letters or digits. Nothing is stored until the shape passes.",
            style = MaterialTheme.typography.labelSmall,
            color = TextDim
        )
    }
}
