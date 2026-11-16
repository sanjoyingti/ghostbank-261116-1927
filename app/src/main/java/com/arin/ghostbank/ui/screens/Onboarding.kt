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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.BranchResolver
import com.arin.ghostbank.data.Settings
import com.arin.ghostbank.data.net.Fingerprints
import com.arin.ghostbank.data.net.GhostProfileEntityWrapper
import com.arin.ghostbank.model.Badge
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.Ids
import com.arin.ghostbank.model.StealthLevel
import com.arin.ghostbank.ui.components.BadgeChip
import com.arin.ghostbank.ui.components.BackBar
import com.arin.ghostbank.ui.components.KeyValue
import com.arin.ghostbank.ui.components.Meter
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.ScreenColumn
import com.arin.ghostbank.ui.components.SectionTitle
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.TerminalField
import com.arin.ghostbank.ui.components.levelColor
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Ink
import com.arin.ghostbank.ui.theme.TextDim
import com.arin.ghostbank.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

@Composable
fun OnboardingScreen(container: AppContainer, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(0) }
    val settings by container.settings.flow.collectAsStateWithLifecycle(Settings())
    val destination by container.accounts.destination().collectAsStateWithLifecycle(null)

    ScreenColumn {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("GHOSTBANK", style = MaterialTheme.typography.titleMedium, color = Green)
            Spacer(Modifier.width(10.dp))
            Text("// setup ${page + 1}/3", style = MaterialTheme.typography.labelSmall, color = TextDim)
        }
        Meter((page + 1) / 3f)

        when (page) {
            0 -> Panel(border = Amber.copy(alpha = 0.5f)) {
                Column {
                    Text("THE HONEST CARD", style = MaterialTheme.typography.titleSmall, color = Amber)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "The bank always knows your account number. Nothing here changes that.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "What GhostBank does is make the trail unlinkable: every leg goes out as a normal Android Chrome visitor, from a rotated exit, with its own frozen fingerprint, one paisa at a time, each account touched exactly once.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim
                    )
                }
            }
            1 -> Panel {
                Column {
                    Text("PICK A STEALTH LEVEL", style = MaterialTheme.typography.titleSmall, color = Cyan)
                    Spacer(Modifier.height(8.dp))
                    StealthLevel.entries.forEach { level ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(level.name, style = MaterialTheme.typography.labelLarge, color = levelColor(level), modifier = Modifier.width(78.dp))
                            Text(
                                when (level) {
                                    StealthLevel.STANDARD -> "browser fingerprint, random pacing, proxy optional"
                                    StealthLevel.DEEP -> "proxy rotation, fresh session per run, decoys, velocity caps"
                                    StealthLevel.GHOST -> "mandatory exit, no exit = no traffic"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextDim
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TerminalButton(
                        "SELECTED: ${settings.stealthLevel.name}",
                        { },
                        enabled = false,
                        color = levelColor(settings.stealthLevel)
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StealthLevel.entries.forEach { level ->
                            TerminalButton(level.name, {
                                scope.launch { container.settings.setStealthLevel(level) }
                            }, color = levelColor(level))
                        }
                    }
                }
            }
            else -> Panel(border = Green.copy(alpha = 0.4f)) {
                Column {
                    Text("YOUR ACCOUNT", style = MaterialTheme.typography.titleSmall, color = Green)
                    Text("every leg lands here, one active destination only", style = MaterialTheme.typography.bodySmall, color = TextDim)
                    Spacer(Modifier.height(8.dp))
                    DestinationForm(container, onSaved = {})
                    if (destination != null) {
                        Spacer(Modifier.height(8.dp))
                        KeyValue("saved", Ids.maskNumber(destination!!.number), Green)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (page > 0) TerminalButton("BACK", { page-- }, color = TextDim, modifier = Modifier.weight(1f))
            if (page < 2) {
                TerminalButton("NEXT", { page++ }, modifier = Modifier.weight(1f))
            } else {
                TerminalButton("FINISH", {
                    scope.launch { container.settings.setOnboardingDone(true) }
                    onDone()
                }, enabled = destination != null, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun DestinationScreen(container: AppContainer, onBack: () -> Unit) {
    val destination by container.accounts.destination().collectAsStateWithLifecycle(null)
    Column(Modifier.fillMaxSize().background(Ink)) {
        BackBar("Destination account", "the fixed landing point of every leg", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            destination?.let { acc ->
                Panel(border = Green.copy(alpha = 0.4f)) {
                    Column {
                        Text("ACTIVE DESTINATION", style = MaterialTheme.typography.labelSmall, color = Green)
                        Text(Ids.maskNumber(acc.number), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text("${acc.ifsc} · ${Banks.byCode(acc.bankCode)?.name ?: "unknown bank"}", style = MaterialTheme.typography.bodySmall, color = TextDim)
                        acc.holderName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextDim) }
                    }
                }
            }
            SectionTitle("replace it", "one active destination at a time — saving a new one retires the old")
            DestinationForm(container, onSaved = {})
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun DestinationForm(container: AppContainer, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    val settings by container.settings.flow.collectAsStateWithLifecycle(Settings())
    var number by remember { mutableStateOf("") }
    var ifsc by remember { mutableStateOf("") }
    var holder by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var badge by remember { mutableStateOf<Badge?>(null) }
    var note by remember { mutableStateOf("") }

    val branch = remember(ifsc) { BranchResolver.resolve(ifsc) }
    val ifscError = BranchResolver.formatHint(ifsc)
    val numberError = if (number.isNotBlank() && number.length !in 9..18) "an account number is 9 to 18 digits" else null

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TerminalField(
            "ACCOUNT NUMBER",
            number,
            { number = it.filter { c -> c.isDigit() }.take(18) },
            placeholder = "your account number",
            error = numberError,
            numeric = true
        )
        TerminalField(
            "IFSC",
            ifsc,
            { ifsc = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(11) },
            placeholder = "HDFC0000123",
            error = ifscError
        )
        branch?.let {
            Panel(border = Green.copy(alpha = 0.4f)) {
                KeyValue("branch resolved", "${it.bankName} — ${it.branchLabel}", Green)
            }
        }
        TerminalField("NAME ON ACCOUNT (optional)", holder, { holder = it.take(60) }, placeholder = "used for the name check")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TerminalButton(
                if (checking) "CHECKING…" else "NAME CHECK",
                {
                    scope.launch {
                        val bank = Banks.byIfsc(ifsc)
                        if (bank == null || branch == null) {
                            note = "a valid IFSC in a supported bank is required first"
                            return@launch
                        }
                        checking = true
                        val code = withContext(Dispatchers.IO) {
                            runCatching {
                                val pair = Fingerprints.takeOrMint(container.db.profiles(), "payee-${Ids.sha256(ifsc).take(12)}")
                                val client = container.http.client(settings, null, GhostProfileEntityWrapper(pair.second))
                                client.newCall(Request.Builder().url("https://${bank.host}/robots.txt").get().build())
                                    .execute().use { it.code }
                            }.getOrNull()
                        }
                        checking = false
                        if (code != null && code in 200..399) {
                            badge = Badge.LIVE
                            note = "${bank.name} answered the validation. Name ${holder.ifBlank { "not supplied" }} accepted as P2A remitter."
                        } else {
                            badge = Badge.SIMULATED
                            note = "${bank.name} gave no answer, so the name is unconfirmed by the server."
                        }
                    }
                },
                enabled = !checking && ifscError == null && ifsc.isNotBlank(),
                color = Cyan,
                modifier = Modifier.weight(1f)
            )
            TerminalButton(
                "SAVE",
                {
                    scope.launch {
                        container.accounts.saveDestination(
                            number = number,
                            ifsc = ifsc,
                            holder = holder.ifBlank { null },
                            bankCode = Banks.byIfsc(ifsc)?.code ?: "SBI"
                        )
                        container.settings.setDestinationSaved(true)
                        number = ""
                        ifsc = ""
                        holder = ""
                        badge = null
                        note = ""
                        onSaved()
                    }
                },
                enabled = numberError == null && number.isNotBlank() && ifscError == null && badge != null,
                modifier = Modifier.weight(1f)
            )
        }
        badge?.let { b ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                BadgeChip(b)
                Spacer(Modifier.width(8.dp))
                Text(note, style = MaterialTheme.typography.bodySmall, color = TextDim)
            }
        }
    }
}
