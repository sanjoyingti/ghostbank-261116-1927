=== ABOUT: The monospaced terminal typeface used everywhere in the app ===
package com.arin.ghostbank.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.arin.ghostbank.R

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold)
)

val GhostTypography = Typography(
    displaySmall = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = 1.sp),
    headlineSmall = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 0.5.sp),
    titleMedium = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.4.sp),
    titleSmall = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.6.sp),
    bodyLarge = TextStyle(fontFamily = JetBrainsMono, fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontFamily = JetBrainsMono, fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, lineHeight = 15.sp),
    labelLarge = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.8.sp),
    labelSmall = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, letterSpacing = 1.sp)
)
