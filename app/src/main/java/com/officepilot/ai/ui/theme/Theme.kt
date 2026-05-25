package com.officepilot.ai.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

val Teal = Color(0xFF10B981)
val TealDark = Color(0xFF059669)
val Indigo = Color(0xFF4F46E5)
val Surface0 = Color(0xFFF8FAFC)
val Surface0Dark = Color(0xFF0F172A)

private val Light = lightColorScheme(
    primary = Teal, onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5), onPrimaryContainer = Color(0xFF064E3B),
    secondary = Indigo, secondaryContainer = Color(0xFFE0E7FF),
    background = Surface0, surface = Color.White,
    surfaceVariant = Color(0xFFF1F5F9), onSurfaceVariant = Color(0xFF64748B),
    onBackground = Color(0xFF1E293B), onSurface = Color(0xFF334155),
    error = Color(0xFFEF4444), outline = Color(0xFFCBD5E1)
)
private val Dark = darkColorScheme(
    primary = Color(0xFF34D399), onPrimary = Color.Black,
    primaryContainer = Color(0xFF064E3B), onPrimaryContainer = Color(0xFFD1FAE5),
    secondary = Color(0xFF818CF8), secondaryContainer = Color(0xFF312E81),
    background = Surface0Dark, surface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFF334155), onSurfaceVariant = Color(0xFFCBD5E1),
    onBackground = Color(0xFFF1F5F9), onSurface = Color(0xFFE2E8F0),
    error = Color(0xFFF87171), outline = Color(0xFF475569)
)

@Composable
fun OfficePilotTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) Dark else Light
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val w = (view.context as Activity).window
            w.statusBarColor = colors.background.toArgb()
            w.navigationBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = colors, content = content)
}
