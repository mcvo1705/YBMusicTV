package com.ybmusic.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Palette ───────────────────────────────────────────────────────────────────
val Purple      = Color(0xFF6C63FF)
val PurpleDim   = Color(0xFF4A42CC)
val BgDeep      = Color(0xFF0F0F1A)
val BgSurface   = Color(0xFF1A1A2E)
val BgCard      = Color(0xFF1E1E35)
val BgVariant   = Color(0xFF252540)
val TextPrimary = Color(0xFFE8E8F0)
val TextMuted   = Color(0xFF9090B0)

// ── Color scheme ──────────────────────────────────────────────────────────────
private val Colors = darkColorScheme(
    primary          = Purple,
    onPrimary        = Color.White,
    primaryContainer = PurpleDim,
    secondary        = Purple,
    background       = BgDeep,
    surface          = BgSurface,
    surfaceVariant   = BgVariant,
    onBackground     = TextPrimary,
    onSurface        = TextPrimary,
    onSurfaceVariant = TextMuted,
    error            = Color(0xFFCF6679),
)

// ── Typography — font lớn hơn vì xem từ xa ~3m ───────────────────────────────
private val Typography = androidx.compose.material3.Typography(
    displayLarge   = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Bold),
    displayMedium  = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge     = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium),
    titleMedium    = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    titleSmall     = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge      = TextStyle(fontSize = 16.sp),
    bodyMedium     = TextStyle(fontSize = 14.sp, color = TextMuted),
    bodySmall      = TextStyle(fontSize = 12.sp, color = TextMuted),
    labelLarge     = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium    = TextStyle(fontSize = 12.sp),
)

// ── Theme ─────────────────────────────────────────────────────────────────────
@Composable
fun YBMusicTVTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography  = Typography,
        content     = content,
    )
}
