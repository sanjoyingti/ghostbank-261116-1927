package com.arin.ghostbank.ui.screens

import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.model.StealthLevel
import com.arin.ghostbank.ui.components.ConfirmDialog
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.Pill
import com.arin.ghostbank.ui.components.ScreenColumn
import com.arin.ghostbank.ui.components.SectionTitle
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.TerminalField
import com.arin.ghostbank.ui.components.levelColor
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Hairline
import com.arin.ghostbank.ui.theme.InkCard
import com.arin.ghostbank.ui.theme.Red
import com.arin.ghostbank.ui.theme.TextDim
import com.arin.ghostbank.ui.theme.TextPrimary
import com.arin.ghostbank.ui.vm.SettingsViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun SettingsScreen(
    container: AppContainer,
    onReplayOnboarding: () -> Unit,
    onDestination: () -> Unit
) {
    val context = LocalContext.current
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val settings by vm.settings.collectAsStateWithLifecycle()
    val proxies by vm.proxies.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val torRunning by vm.torRunning.collectAsStateWithLifecycle()
    val proxyInput by vm.proxyInput.collectAsStateWithLifecycle()

    var confirmWipeRegistry by remember { mutableStateOf(false) }
    var confirmWipeLedger by remember { mutableStateOf(false) }

    if (confirmWipeRegistry) {
        ConfirmDialog(
            title = "Wipe the dedupe registry?",
            body = "Every account becomes harvestable again. The ledger is untouched.",
            confirm = "wipe registry",
            onConfirm = { vm.wipeRegistry() },
            onDismiss = { confirmWipeRegistry = false }
        )
    }
    if (confirmWipeLedger) {
        ConfirmDialog(
            title = "Wipe the ledger?",
            body = "All debit and credit rows are deleted. The registry stays so nothing is harvested twice.",
            confirm = "wipe ledger",
            onConfirm = { vm.wipeLedger() },
            onDismiss = { confirmWipeLedger = false }
        )
    }

    ScreenColumn {
        SectionTitle("STEALTH LEVEL", "How loud GhostBank is allowed to be")

        StealthLevel.entries.forEach { level ->
            val selected = settings.stealthLevel == level
            val color = levelColor(level)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (selected) InkCard else InkCard.copy(alpha = 0.4f))
                    .border(1.dp, if (selected) color else Hairline, RoundedCornerShape(5.dp))
                    .clickable { vm.setLevel(level) }
                    .padding(12.dp)
            ) {
                Text(level.name, style = MaterialTheme.typography.titleSmall, color = color)
                Text(
                    when (level) {
                        StealthLevel.STANDARD -> "browser fingerprint, random pacing, proxy optional"
                        StealthLevel.DEEP -> "proxy rotation, fresh session per run, decoy browsing, velocity caps"
                        StealthLevel.GHOST -> "everything below plus proxy mandatory — no exit, no traffic"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim
                )
            }
        }

        SectionTitle("TOR MODE", "SOCKS5 to Orbot on 127.0.0.1:9050")
        Panel {
            Column {
                SwitchRow("Route through Tor", settings.torMode) { vm.setTorMode(it) }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TerminalButton("detect Tor", { vm.checkTor() }, color = Cyan)
                    Spacer(Modifier.width(10.dp))
                    val state = torRunning
                    if (state != null) {
                        Pill(if (state) "RUNNING" else "NOT RUNNING", if (state) Green else Red)
                    }
                }
                if (torRunning == false) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Orbot is not listening on 9050. Start it, or install it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Amber
                    )
                    Spacer(Modifier.height(6.dp))
                    TerminalButton("open Orbot", { vm.openOrbot() }, color = Amber)
                }
            }
        }

        SectionTitle("PROXY EXITS", "host:port or socks5://host:port — append |IN to tag a country")
        Panel {
            Column {
                TerminalField(
                    label = "add an exit",
                    value = proxyInput,
                    onChange = vm::setProxyInput,
                    placeholder = "socks5://203.0.113.9:1080|IN"
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    TerminalButton("add", { vm.importProxies(proxyInput) })
                    Spacer(Modifier.width(8.dp))
                    TerminalButton("paste list", {
                        val clip = context.getSystemService(ClipboardManager::class.java)
                        val text = clip?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                        vm.importProxies(text.orEmpty())
                    }, color = Cyan)
                }
                if (proxies.isEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("No exits yet. Ghost level stays shut until you add one.", style = MaterialTheme.typography.bodySmall, color = Amber)
                }
                proxies.forEach { proxy ->
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.fillMaxWidth().background(InkCard, RoundedCornerShape(4.dp)).padding(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(proxy.url, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                            Pill(if (proxy.healthy) "UP" else "DOWN", if (proxy.healthy) Green else Red)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row {
                            TerminalButton("test", { vm.testProxy(proxy) }, color = Cyan)
                            Spacer(Modifier.width(8.dp))
                            TerminalButton("remove", { vm.deleteProxy(proxy.id) }, color = Red)
                        }
                    }
                }
            }
        }

        SectionTitle("DNS-OVER-HTTPS", "Keeps the ISP resolver out of the lookup path")
        TerminalField(
            label = "resolver url",
            value = settings.dohResolver,
            onChange = { vm.setDoh(it) },
            placeholder = "https://cloudflare-dns.com/dns-query"
        )

        SectionTitle("PACING")
        Panel {
            Column {
                SwitchRow("Diurnal window 09:00–23:00", settings.diurnalWindow) { vm.setDiurnal(it) }
                Spacer(Modifier.height(10.dp))
                Stepper("max requests / minute", settings.maxRequestsPerMinute, 1, 120) { vm.setRpm(it) }
                Stepper("max accounts / hour", settings.maxAccountsPerHour, 1, 600) { vm.setAph(it) }
                Stepper("entropy budget (requests per run)", settings.entropyBudget, 10, 2000) { vm.setBudget(it) }
                Stepper("retries per stage", settings.retries, 0, 8) { vm.setRetries(it) }
                Stepper("backoff base (ms)", settings.backoffMs.toInt(), 100, 30_000) { vm.setBackoff(it.toLong()) }
            }
        }

        SectionTitle("TRACE-SHIELD")
        Panel {
            Column {
                SwitchRow("Rotate virtual accounts", settings.vaRotation) { vm.setVaRotation(it) }
                Spacer(Modifier.height(10.dp))
                Stepper("fragment min legs", settings.fragMin, 1, 5) { vm.setFragMin(it) }
                Stepper("fragment max legs", settings.fragMax, 1, 5) { vm.setFragMax(it) }
            }
        }

        SectionTitle("BACKGROUND HARVEST", "WorkManager, randomised, respects the caps")
        Panel {
            Column {
                SwitchRow("Run in the background", settings.backgroundHarvest) { vm.setBackground(it) }
                Spacer(Modifier.height(10.dp))
                Stepper("hard daily cap", settings.dailyHardCap, 1, 500) { vm.setDailyCap(it) }
            }
        }

        SectionTitle("DATA")
        Row {
            TerminalButton("destination", onDestination, color = Cyan)
            Spacer(Modifier.width(8.dp))
            TerminalButton("replay onboarding", onReplayOnboarding)
        }
        Spacer(Modifier.height(8.dp))
        Row {
            TerminalButton("wipe registry", { confirmWipeRegistry = true }, color = Amber)
            Spacer(Modifier.width(8.dp))
            TerminalButton("wipe ledger", { confirmWipeLedger = true }, color = Red)
        }
        Spacer(Modifier.height(8.dp))
        TerminalButton("export everything (CSV)", { vm.exportAll(context) }, color = Cyan)

        if (message != null) {
            Spacer(Modifier.height(10.dp))
            Panel(border = Cyan.copy(alpha = 0.5f)) {
                Column {
                    Text(message!!, style = MaterialTheme.typography.bodySmall, color = Cyan)
                    Spacer(Modifier.height(6.dp))
                    TerminalButton("dismiss", { vm.clearMessage() }, color = TextDim)
                }
            }
        }

        SectionTitle("ABOUT / HONEST LIMITS")
        Panel {
            Column {
                Text(
                    "GhostBank moves money on the IMPS person-to-account rail and books what the server confirms. " +
                        "It is not a core-banking hack: authorisation for the debit leg still needs the remitter " +
                        "profile you saved, so treat every LIVE leg as a real instruction and every SIMULATED leg " +
                        "as an unconfirmed one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim
                )
                Spacer(Modifier.height(10.dp))
                Text("What is genuinely hidden", style = MaterialTheme.typography.titleSmall, color = Green)
                Text(
                    "Your IP (through an exit), the digits on the rail (virtual accounts), the fingerprint " +
                        "(frozen Chrome profile per account) and the DNS lookups (DoH through the tunnel).",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim
                )
                Spacer(Modifier.height(10.dp))
                Text("What physics keeps visible", style = MaterialTheme.typography.titleSmall, color = Amber)
                Text(
                    "The destination bank always knows your account number — it is in its own ledger. The source " +
                        "bank knows the debited account. The UTR chain is public to both banks. Android's TCP stack " +
                        "fingerprint is only shadowed by an exit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim
                )
                Spacer(Modifier.height(10.dp))
                Text("No analytics, no cloud, no third-party SDK. GhostBank talks only to the banks, the exit and the leak-check hosts.", style = MaterialTheme.typography.bodySmall, color = TextDim)
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Green, checkedTrackColor = Green.copy(alpha = 0.3f))
        )
    }
}

@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextDim)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.clip(RoundedCornerShape(4.dp)).background(InkCard)
                    .clickable { if (value > min) onChange(value - 1) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) { Text("-", style = MaterialTheme.typography.titleSmall, color = Green) }
            Text(
                value.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Box(
                Modifier.clip(RoundedCornerShape(4.dp)).background(InkCard)
                    .clickable { if (value < max) onChange(value + 1) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) { Text("+", style = MaterialTheme.typography.titleSmall, color = Green) }
        }
    }
}
