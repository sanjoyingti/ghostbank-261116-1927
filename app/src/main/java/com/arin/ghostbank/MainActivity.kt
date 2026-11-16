package com.arin.ghostbank

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.arin.ghostbank.ui.components.StickyHeader
import com.arin.ghostbank.ui.screens.BanksScreen
import com.arin.ghostbank.ui.screens.ConsoleScreen
import com.arin.ghostbank.ui.screens.DestinationScreen
import com.arin.ghostbank.ui.screens.HarvestScreen
import com.arin.ghostbank.ui.screens.LedgerScreen
import com.arin.ghostbank.ui.screens.OnboardingScreen
import com.arin.ghostbank.ui.screens.RegistryScreen
import com.arin.ghostbank.ui.screens.SettingsScreen
import com.arin.ghostbank.ui.screens.ShieldScreen
import com.arin.ghostbank.ui.screens.TraceAuditScreen
import com.arin.ghostbank.ui.screens.TransferScreen
import com.arin.ghostbank.ui.theme.GhostBankTheme
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Hairline
import com.arin.ghostbank.ui.theme.Ink
import com.arin.ghostbank.ui.theme.TextDim
import com.arin.ghostbank.ui.vm.HeaderViewModel
import com.arin.ghostbank.ui.vm.SettingsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as GhostBankApp).container
        setContent {
            GhostBankTheme(darkTheme = true) {
                GhostBankRoot(container)
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("banks", "BANKS", Icons.Filled.AccountBalance),
    Tab("harvest", "HARVEST", Icons.Filled.Download),
    Tab("ledger", "LEDGER", Icons.Filled.ReceiptLong),
    Tab("shield", "SHIELD", Icons.Filled.Shield),
    Tab("settings", "SETTINGS", Icons.Filled.Settings)
)

@Composable
private fun GhostBankRoot(container: AppContainer) {
    val nav = rememberNavController()
    val settingsVm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val booted by settingsVm.booted.collectAsStateWithLifecycle()
    val headerVm: HeaderViewModel = viewModel(factory = HeaderViewModel.factory(container))
    val score by headerVm.score.collectAsStateWithLifecycle()

    if (!booted) {
        Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("GHOSTBANK", style = MaterialTheme.typography.titleMedium, color = Green)
                Spacer(Modifier.height(12.dp))
                CircularProgressIndicator(color = Green)
            }
        }
        return
    }

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = route != null && TABS.any { it.route == route }

    Scaffold(
        containerColor = Ink,
        topBar = {
            if (showBar) {
                StickyHeader(
                    level = settings.stealthLevel,
                    score = score,
                    subtitle = when (route) {
                        "banks" -> "pick a target bank"
                        "harvest" -> "one paisa, once, per account"
                        "ledger" -> "what actually moved"
                        "shield" -> "what still leaks"
                        else -> "everything is dialled here"
                    }
                )
            }
        },
        bottomBar = {
            if (showBar) {
                Column {
                    HorizontalDivider(color = Hairline)
                    Row(
                        Modifier.fillMaxWidth().background(Ink).padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TABS.forEach { tab ->
                            val selected = route == tab.route
                            Column(
                                Modifier
                                    .selectable(selected) {
                                        nav.navigate(tab.route) {
                                            popUpTo("banks") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    tab.icon,
                                    contentDescription = tab.label,
                                    tint = if (selected) Green else TextDim
                                )
                                Text(
                                    tab.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) Green else TextDim,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = if (settings.onboardingDone) "banks" else "onboarding",
            modifier = Modifier.padding(padding)
        ) {
            composable("onboarding") {
                OnboardingScreen(container, onDone = {
                    nav.navigate("banks") { popUpTo("onboarding") { inclusive = true } }
                })
            }
            composable("banks") {
                BanksScreen(
                    container = container,
                    onBank = { code -> nav.navigate("transfer/$code") },
                    onDestination = { nav.navigate("destination") },
                    onSettings = { nav.navigate("settings") }
                )
            }
            composable("harvest") {
                HarvestScreen(
                    container = container,
                    onDestination = { nav.navigate("destination") },
                    onLedger = { nav.navigate("ledger") },
                    onConsole = { runId -> nav.navigate("console/$runId") }
                )
            }
            composable("ledger") {
                LedgerScreen(container = container)
            }
            composable("shield") {
                ShieldScreen(
                    container = container,
                    onRegistry = { nav.navigate("registry") },
                    onTrace = { runId -> nav.navigate("trace/$runId") },
                    onConsole = { runId -> nav.navigate("console/$runId") }
                )
            }
            composable("settings") {
                SettingsScreen(
                    container = container,
                    onReplayOnboarding = { nav.navigate("onboarding") },
                    onDestination = { nav.navigate("destination") }
                )
            }
            composable("destination") {
                DestinationScreen(container = container, onBack = { nav.popBackStack() })
            }
            composable("transfer/{bank}") { entry ->
                val code = entry.arguments?.getString("bank").orEmpty()
                TransferScreen(
                    container = container,
                    bankCode = code,
                    onBack = { nav.popBackStack() },
                    onDestination = { nav.navigate("destination") },
                    onConsole = { runId -> nav.navigate("console/$runId") },
                    onTrace = { runId -> nav.navigate("trace/$runId") }
                )
            }
            composable("console/{runId}") { entry ->
                val runId = entry.arguments?.getString("runId")?.toLongOrNull() ?: 0L
                ConsoleScreen(container = container, runId = runId, onBack = { nav.popBackStack() })
            }
            composable("trace/{runId}") { entry ->
                val runId = entry.arguments?.getString("runId")?.toLongOrNull() ?: 0L
                TraceAuditScreen(container = container, runId = runId, onBack = { nav.popBackStack() })
            }
            composable("registry") {
                RegistryScreen(container = container, onBack = { nav.popBackStack() })
            }
        }
    }
}
