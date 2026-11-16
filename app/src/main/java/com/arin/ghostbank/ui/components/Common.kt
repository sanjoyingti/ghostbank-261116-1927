package com.arin.ghostbank.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arin.ghostbank.model.Badge
import com.arin.ghostbank.model.HarvestStatus
import com.arin.ghostbank.model.StealthLevel
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Hairline
import com.arin.ghostbank.ui.theme.Ink
import com.arin.ghostbank.ui.theme.InkCard
import com.arin.ghostbank.ui.theme.Red
import com.arin.ghostbank.ui.theme.TextDim

fun statusColor(status: HarvestStatus): Color = when (status) {
    HarvestStatus.SETTLED -> Green
    HarvestStatus.FLOOR -> Amber
    HarvestStatus.REJECTED -> Red
    HarvestStatus.SENT -> Cyan
    HarvestStatus.PENDING -> TextDim
}

fun levelColor(level: StealthLevel): Color = when (level) {
    StealthLevel.STANDARD -> Cyan
    StealthLevel.DEEP -> Amber
    StealthLevel.GHOST -> Green
}

fun badgeFor(name: String?): Badge = if (name == Badge.LIVE.name) Badge.LIVE else Badge.SIMULATED

@Composable
fun BadgeChip(badge: Badge, modifier: Modifier = Modifier) {
    val color = if (badge == Badge.LIVE) Green else Amber
    Text(
        text = badge.name,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    border: Color = Hairline,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(InkCard)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .padding(12.dp)
    ) { content() }
}

@Composable
fun SectionTitle(text: String, hint: String? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = TextDim)
    }
}

@Composable
fun EmptyState(title: String, action: String?, onAction: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("// nothing here", style = MaterialTheme.typography.bodySmall, color = TextDim)
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            TerminalButton(action, onAction)
        }
    }
}

@Composable
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.then(
        Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
    )
}

@Composable
fun TerminalButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    color: Color = Green,
    modifier: Modifier = Modifier
) {
    val tint = if (enabled) color else TextDim
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (enabled) color.copy(alpha = 0.12f) else InkCard)
            .border(1.dp, tint.copy(alpha = 0.6f), RoundedCornerShape(5.dp))
            .then(if (enabled) Modifier.clickableNoRipple(onClick) else Modifier)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = tint,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun KeyValue(key: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onBackground) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, style = MaterialTheme.typography.bodySmall, color = TextDim, modifier = Modifier.width(140.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = valueColor, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
    }
}

@Composable
fun Meter(fraction: Float, color: Color = Green, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Hairline)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .background(color)
        )
    }
}

@Composable
fun ScoreChip(score: Int) {
    val color = when {
        score >= 80 -> Green
        score >= 50 -> Amber
        else -> Red
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("STEALTH ", style = MaterialTheme.typography.labelSmall, color = TextDim)
        Text(
            score.toString().padStart(3, '0'),
            style = MaterialTheme.typography.titleSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun StickyHeader(level: StealthLevel, score: Int, subtitle: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Ink)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("GHOSTBANK", style = MaterialTheme.typography.titleMedium, color = Green)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextDim)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(level.name, levelColor(level))
                Spacer(Modifier.width(8.dp))
                ScoreChip(score)
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
    }
}
