package com.arin.ghostbank.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.model.StealthLevel
import com.arin.ghostbank.ui.components.Panel
import com.arin.ghostbank.ui.components.Pill
import com.arin.ghostbank.ui.components.ScreenColumn
import com.arin.ghostbank.ui.components.TerminalButton
import com.arin.ghostbank.ui.components.TerminalField
import com.arin.ghostbank.ui.components.clickableNoRipple
import com.arin.ghostbank.ui.components.levelColor
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Hairline
import com.arin.ghostbank.ui.theme.InkCard
import com.arin.ghostbank.ui.theme.Red
import com.arin.ghostbank.ui.theme.TextDim
import com.arin.ghostbank.ui.vm.OnboardingViewModel

@Composable
fun OnboardingScreen(container: AppContainer, onDone: () -> Unit) {
    val vm: OnboardingViewModel = viewModel(factory = OnboardingViewModel.factory(container))
    val page by vm.page.collectAsStateWithLifecycle()
    val level by vm.level.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var number by remember { mutableStateOf("") }
    var ifsc by remember { mutableStateOf("") }
    var holder by remember { mutableStateOf("") }

    ScreenColumn {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("GHOSTBANK", style = MaterialTheme.typography.titleMedium, color = Green)
            Text("0${page + 1}/03", style = MaterialTheme.typography.labelSmall, color = TextDim)
        }

        when (page) {
            0 -> Panel(border = Amber) {
                Column {
                    Text("READ THIS FIRST", style = MaterialTheme.typography.titleSmall, color = Amber)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "GhostBank does not break into a bank. It talks to the live bank servers the way a browser does, runs the same steps a transfer really takes, and books only what a server confirms.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "The bank always knows your account number. The source bank always knows the account it debited. What GhostBank buys you is masking, unlinkability between the source and your account, and clean IP hygiene.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim
                    )
                }
            }
            1 -> {
                Text("PICK A STEALTH LEVEL", style = MaterialTheme.typography.titleSmall)
                StealthLevel.entries.forEach { option ->
                    val selected = option == level
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(5.dp))
                            .background(InkCard)
                            .border(1.dp, if (selected) levelColor(option) else Hairline, RoundedCornerShape(5.dp))
                            .clickableNoRipple { vm.setLevel(option) }
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Pill(option.name, levelColor(option))
                            Spacer(Modifier.width(8.dp))
                            Text(if (selected) "selected" else "tap to pick", style = MaterialTheme.typography.labelSmall, color = TextDim)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            when (option) {
                                StealthLevel.STANDARD -> "Browser fingerprint, random pacing. Proxy optional."
                                StealthLevel.DEEP -> "Adds proxy rotation, a fresh session per run, decoy browsing and velocity caps."
                                StealthLevel.GHOST -> "Everything below plus a mandatory exit. No exit means no traffic at all."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDim
                        )
                    }
                }
            }
            else -> {
                Text("WHERE SHOULD THE MONEY LAND", style = MaterialTheme.typography.titleSmall)
                Text("Fixed destination for every leg. Digits are masked once saved.", style = MaterialTheme.typography.bodySmall, color = TextDim)
                TerminalField("ACCOUNT NUMBER", number, { number = it.filter(Char::isDigit).take(18) }, numeric = true)
                TerminalField("IFSC", ifsc, { ifsc = it.uppercase().take(11) })
                TerminalField("ACCOUNT HOLDER", holder, { holder = it }, placeholder = "as printed on the passbook")
                if (error != null) Text(error!!, style = MaterialTheme.typography.bodySmall, color = Red)
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (page > 0) TerminalButton("BACK", { vm.go(page - 1) }, color = Cyan)
            if (page < 2) {
                TerminalButton("NEXT", { vm.go(page + 1) }, modifier = Modifier.fillMaxWidth())
            } else {
                TerminalButton(
                    "FINISH SETUP",
                    { vm.finish(number, ifsc, holder, onDone) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
        Text(
            "no analytics · no cloud · only the banks are called",
            style = MaterialTheme.typography.labelSmall,
            color = TextDim,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
