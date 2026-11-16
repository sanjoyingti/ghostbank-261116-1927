package com.arin.ghostbank.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightAccent = Color(0xFF0A7F8C)

private val DarkScheme = darkColorScheme(
    primary = Green,
    onPrimary = Ink,
    primaryContainer = GreenDim,
    onPrimaryContainer = TextPrimary,
    secondary = Cyan,
    onSecondary = Ink,
    tertiary = Amber,
    onTertiary = Ink,
    background = Ink,
    onBackground = TextPrimary,
    surface = Ink,
    onSurface = TextPrimary,
    surfaceVariant = InkCard,
    onSurfaceVariant = TextDim,
    outline = Hairline,
    outlineVariant = Hairline,
    error = Red,
    onError = Ink,
    surfaceContainer = InkElevated,
    surfaceContainerHigh = InkCard
)

private val LightScheme = lightColorScheme(
    primary = GreenDim,
    onPrimary = PaperLight,
    secondary = LightAccent,
    onSecondary = PaperLight,
    tertiary = Amber,
    onTertiary = InkOnPaper,
    background = PaperLight,
    onBackground = InkOnPaper,
    surface = PaperCard,
    onSurface = InkOnPaper,
    surfaceVariant = PaperCard,
    onSurfaceVariant = TextDim,
    outline = HairlineLight,
    error = Red,
    onError = PaperLight
)

@Composable
fun GhostBankTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = GhostTypography,
        content = content
    )
}

/** Semantic colours regardless of scheme. */
object GhostColors {
    val hidden = Green
    val info = Cyan
    val leaked = Amber
    val failed = Red
}
