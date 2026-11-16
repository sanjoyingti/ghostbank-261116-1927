package com.arin.ghostbank.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arin.ghostbank.ui.theme.Amber
import com.arin.ghostbank.ui.theme.Cyan
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Red
import com.arin.ghostbank.ui.theme.TextDim
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private val inr = NumberFormat.getNumberInstance(Locale("en", "IN"))
private val clock = SimpleDateFormat("dd MMM HH:mm:ss", Locale.US)

fun rupeesText(paise: Long): String {
    val sign = if (paise < 0) "-" else ""
    val abs = abs(paise)
    return sign + "₹" + inr.format(abs / 100) + "." + (abs % 100).toString().padStart(2, '0')
}

fun tsText(ms: Long): String = clock.format(Date(ms))

fun paiseOf(text: String): Long? = text.trim().toDoubleOrNull()?.let { (it * 100).roundToLong() }

fun runColor(status: String): androidx.compose.ui.graphics.Color = when (status) {
    "RUNNING" -> Cyan
    "COMPLETED" -> Green
    "PAUSED" -> Amber
    "FAILED", "CANCELLED" -> Red
    else -> TextDim
}

@Composable
fun Stepper(label: String, value: Int, hint: String?, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
            if (hint != null) Text(hint, style = MaterialTheme.typography.labelSmall, color = TextDim)
        }
        TerminalButton("–", { onChange(value - 1) }, color = TextDim)
        Spacer(Modifier.width(8.dp))
        Text(value.toString(), style = MaterialTheme.typography.titleSmall, color = Green)
        Spacer(Modifier.width(8.dp))
        TerminalButton("+", { onChange(value + 1) }, color = Green)
    }
}

@Composable
fun LongStepper(label: String, value: Long, hint: String?, step: Long, onChange: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
            if (hint != null) Text(hint, style = MaterialTheme.typography.labelSmall, color = TextDim)
        }
        TerminalButton("–", { onChange(value - step) }, color = TextDim)
        Spacer(Modifier.width(8.dp))
        Text(value.toString(), style = MaterialTheme.typography.titleSmall, color = Green)
        Spacer(Modifier.width(8.dp))
        TerminalButton("+", { onChange(value + step) }, color = Green)
    }
}
