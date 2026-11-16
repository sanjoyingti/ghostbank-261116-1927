=== ABOUT: Fixed the missing dedupe registry: searchable hashed accounts with a force re-harvest button per row ===
package com.arin.ghostbank.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.model.HarvestStatus
import com.arin.ghostbank.ui.components.BackBar
import com.arin.ghostbank.ui.components.ConfirmDialog
import com.arin.ghostbank.ui.components.EmptyState
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.Pill
import com.arin.ghostbank.ui.components.ScreenColumn
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.TerminalField
import com.arin.ghostbank.ui.components.paiseToCompact
import com.arin.ghostbank.ui.components.statusColor
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Hairline
import com.arin.ghostbank.ui.theme.Red
import com.arin.ghostbank.ui.theme.TextDim
import kotlinx.coroutines.launch

@Composable
fun RegistryScreen(container: AppContainer, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val rows by remember(query) { container.registry.search(query) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val count by container.registry.count().collectAsStateWithLifecycle(initialValue = 0)
    val scope = rememberCoroutineScope()
    var confirmWipe by remember { mutableStateOf(false) }

    ScreenColumn {
        BackBar("Dedupe registry", "$count hashed accounts · SHA-256 of number + IFSC", onBack)
        Text(
            "A row is written before the request leaves, so a crash can never let the same account be paid twice. Force only if you really want a second paisa.",
            style = MaterialTheme.typography.bodySmall,
            color = TextDim
        )
        TerminalField("search", query, { query = it }, placeholder = "account number or IFSC")

        TerminalButton("wipe the whole registry", { confirmWipe = true }, color = Red)

        if (rows.isEmpty()) {
            EmptyState("No hashes match — paste accounts on the Harvest tab to fill it", null, null)
        } else {
            rows.forEach { entry ->
                val status = runCatching { HarvestStatus.valueOf(entry.status) }.getOrDefault(HarvestStatus.PENDING)
                Panel(border = statusColor(status)) {
                    Column {
                        Row {
                            Text(entry.accountNumber, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.typography.bodyMedium.color)
                            Spacer(Modifier.width(8.dp))
                            Pill(entry.status, statusColor(status))
                        }
                        Text("${entry.ifsc} · ${entry.bankCode}", style = MaterialTheme.typography.labelSmall, color = TextDim)
                        Text(entry.hash.take(24) + "…", style = MaterialTheme.typography.labelSmall, color = TextDim)
                        entry.utr?.let { Text("UTR $it", style = MaterialTheme.typography.labelSmall, color = Green) }
                        entry.reason?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Amber) }
                        Text("${paiseToCompact(entry.amountPaise)} · forced ${entry.forced}x", style = MaterialTheme.typography.labelSmall, color = TextDim)
                        Spacer(Modifier.height(4.dp))
                        TerminalButton("force re-harvest", {
                            scope.launch { container.registry.forceReset(entry.id) }
                        }, color = Cyan)
                    }
                }
            }
        }
    }

    if (confirmWipe) {
        ConfirmDialog(
            title = "Wipe the registry?",
            body = "Every hash is forgotten, so the same accounts become harvestable again. The ledger is untouched.",
            confirm = "wipe it",
            onConfirm = { scope.launch { container.registry.wipe() } },
            onDismiss = { confirmWipe = false }
        )
    }
}
