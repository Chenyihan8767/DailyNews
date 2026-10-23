package com.dailynews.reader.ui.theme

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.core.view.WindowCompat
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dailynews.reader.data.Appearance
import com.dailynews.reader.data.ReaderSettings

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC4DAA5), onPrimary = Color(0xFF24351D), primaryContainer = Color(0xFF354A2B),
    onPrimaryContainer = Color(0xFFD9EDBC), secondary = Color(0xFFD7C9A5), onSecondary = Color(0xFF393020),
    secondaryContainer = Color(0xFF4B4230), onSecondaryContainer = Color(0xFFF3E4C1),
    tertiary = Color(0xFFE9C17B), onTertiary = Color(0xFF402D08),
    background = Color(0xFF141610), onBackground = Color(0xFFEAE9DE),
    surface = Color(0xFF141610), onSurface = Color(0xFFEAE9DE), surfaceVariant = Color(0xFF34372D),
    onSurfaceVariant = Color(0xFFC3C7B7), surfaceContainer = Color(0xFF20231B),
    surfaceContainerLow = Color(0xFF1B1E16), surfaceContainerHigh = Color(0xFF2A2E23),
    surfaceContainerHighest = Color(0xFF35392E), outline = Color(0xFF8E9483), outlineVariant = Color(0xFF444A3C),
)
private val LightColors = lightColorScheme(
    primary = Color(0xFF486333), onPrimary = Color.White, primaryContainer = Color(0xFFD8E9BF),
    onPrimaryContainer = Color(0xFF2F451D), secondary = Color(0xFF6B5E43), secondaryContainer = Color(0xFFF1E3C5),
    onSecondaryContainer = Color(0xFF4E412B), tertiary = Color(0xFF825B18),
    background = Color(0xFFF9F8F0), onBackground = Color(0xFF1B2118),
    surface = Color(0xFFF9F8F0), onSurface = Color(0xFF1B2118), surfaceVariant = Color(0xFFE3E7D8),
    onSurfaceVariant = Color(0xFF535B49), surfaceContainer = Color(0xFFEEF0E5),
    surfaceContainerLow = Color(0xFFF2F3E9), surfaceContainerHigh = Color(0xFFE6EADD),
    surfaceContainerHighest = Color(0xFFDEE4D2), outline = Color(0xFF777F6B), outlineVariant = Color(0xFFD1D7C6),
)
private val NewsTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 52.sp, lineHeight = 56.sp, letterSpacing = (-2).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-.8).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 27.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 17.sp, lineHeight = 27.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp),
)

@Composable fun DailyNewsTheme(settings: ReaderSettings, content: @Composable () -> Unit) {
    val dark = when (settings.appearance) { Appearance.DARK -> true; Appearance.LIGHT -> false; Appearance.SYSTEM -> isSystemInDarkTheme() }
    val colors = if (settings.dynamicColor && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    } else if (dark) DarkColors else LightColors
    val activity = LocalActivity.current
    SideEffect {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = colors, typography = NewsTypography, content = content)
}
