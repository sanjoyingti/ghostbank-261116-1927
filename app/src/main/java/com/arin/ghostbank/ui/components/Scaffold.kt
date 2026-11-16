package com.arin.ghostbank.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.arin.ghostbank.ui.theme.Green
import com.arin.ghostbank.ui.theme.Hairline
import com.arin.ghostbank.ui.theme.Ink
import com.arin.ghostbank.ui.theme.InkCard
import com.arin.ghostbank.ui.theme.Red
import com.arin.ghostbank.ui.theme.TextDim
import com.arin.ghostbank.ui.theme.TextPrimary

@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val base = modifier.fillMaxSize().background(Ink)
    Column(
        if (scrollable) base.verticalScroll(rememberScrollState()).padding(16.dp) else base.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
fun BackBar(title: String, subtitle: String? = null, onBack: () -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onBack)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextDim)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextDim)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
    }
}

@Composable
fun TerminalField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String? = null,
    error: String? = null,
    numeric: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true
) {
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label, style = MaterialTheme.typography.labelSmall, color = TextDim) },
            placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodySmall, color = TextDim) } },
            singleLine = singleLine,
            enabled = enabled,
            isError = error != null,
            textStyle = MaterialTheme.typography.bodyMedium,
            keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
            shape = RoundedCornerShape(5.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Green,
                unfocusedBorderColor = Hairline,
                focusedLabelColor = Green,
                unfocusedLabelColor = TextDim,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                errorBorderColor = Red,
                cursorColor = Green
            )
        )
        if (error != null) {
            Text(error, style = MaterialTheme.typography.labelSmall, color = Red, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
        }
    }
}

@Composable
fun Tag(text: String, color: com.arin.ghostbank.ui.theme.GhostPalette = com.arin.ghostbank.ui.theme.GhostPalette.NEUTRAL) {
    val tint = color.color
    Box(
        Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(InkCard)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = InkCard,
        title = { Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary) },
        text = { Text(body, style = MaterialTheme.typography.bodySmall, color = TextDim) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirm, color = Green, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("cancel", color = TextDim, style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}
