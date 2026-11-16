package com.arin.ghostbank.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.ui.components.BackBar
import com.arin.ghostbank.ui.components.BadgeChip
import com.arin.ghostbank.ui.components.EmptyState
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.TerminalField
import com.arin.ghostbank.ui.components.badgeFor
import com.arin.ghostbank.ui.components.tsText
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Ink
import com.arin.ghostbank.ui.theme.TextDim
import com.arin.ghostbank.ui.theme.TextPrimary

@Composable
fun ConsoleScreen(container: AppContainer, runId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val logs by container.runs.logs(runId).collectAsStateWithLifecycle(emptyList())
    var stageFilter by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("") }

    val shown = remember(logs, stageFilter, statusFilter) {
        logs.filter {
            (stageFilter.isBlank() || it.stage.contains(stageFilter, true)) &&
                (statusFilter.isBlank() || it.httpStatus?.toString()?.startsWith(statusFilter) == true)
        }
    }

    Column(Modifier.fillMaxSize().background(Ink)) {
        BackBar("Raw console", "run #$runId — secrets masked", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TerminalField("STAGE FILTER", stageFilter, { stageFilter = it }, placeholder = "DEBIT_LEG")
            TerminalField("STATUS FILTER", statusFilter, { statusFilter = it.filter { c -> c.isDigit() }.take(3) }, placeholder = "403", numeric = true)
            if (shown.isEmpty()) {
                EmptyState("No rows for this filter.", "clear the filters", { stageFilter = ""; statusFilter = "" })
            }
            shown.forEach { log ->
                Panel {
                    Column {
                        Row(Modifier.fillMaxWidth()) {
                            Text("${log.stage} · ${tsText(log.ts)}", style = MaterialTheme.typography.labelSmall, color = TextPrimary, modifier = Modifier.weight(1f))
                            Text("${log.httpStatus ?: "-"} ${log.latencyMs ?: 0}ms", style = MaterialTheme.typography.labelSmall, color = TextDim)
                            Spacer(Modifier.height(2.dp))
                            BadgeChip(badgeFor(log.badge))
                        }
                        log.note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Cyan) }
                        log.requestHeaders?.let {
                            Text("REQUEST", style = MaterialTheme.typography.labelSmall, color = Cyan)
                            Text(it, style = MaterialTheme.typography.bodySmall, color = TextDim)
                        }
                        log.responseHeaders?.let {
                            Text("RESPONSE HEADERS", style = MaterialTheme.typography.labelSmall, color = Cyan)
                            Text(it, style = MaterialTheme.typography.bodySmall, color = TextDim)
                        }
                        log.rawBody?.let {
                            Text("BODY", style = MaterialTheme.typography.labelSmall, color = Cyan)
                            Text(it, style = MaterialTheme.typography.bodySmall, color = TextDim)
                        }
                        Spacer(Modifier.height(6.dp))
                        TerminalButton("COPY ROW", {
                            val text = buildString {
                                appendLine("${log.stage} ${log.httpStatus ?: "-"}")
                                log.requestHeaders?.let { appendLine(it) }
                                log.responseHeaders?.let { appendLine(it) }
                                log.rawBody?.let { appendLine(it) }
                            }
                            val manager = context.getSystemService(ClipboardManager::class.java)
                            manager?.setPrimaryClip(ClipData.newPlainText("ghostbank console", text))
                        }, color = Cyan)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
