package app.focuslite.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Black ink on soft grey cards. Light and dark are designed together; screens use scheme roles only.
// Every text pair is at least 4.5:1 and every chart mark at least 3:1 (measured, see docs/UI-SPEC.md).
private val LightColors = lightColorScheme(
    primary = Color(0xFF111114),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4E4E9),
    onPrimaryContainer = Color(0xFF111114),
    secondary = Color(0xFF55555C),
    tertiary = Color(0xFF176B3A), // "on" / "within goal"; 5.6:1 on cards
    background = Color(0xFFF6F6F8),
    onBackground = Color(0xFF111114),
    surface = Color(0xFFF6F6F8),
    onSurface = Color(0xFF111114),
    surfaceVariant = Color(0xFFECECF0), // cards
    onSurfaceVariant = Color(0xFF66666E),
    outlineVariant = Color(0xFFD9D9DF),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF2F2F5),
    onPrimary = Color(0xFF111114),
    primaryContainer = Color(0xFF2A2A30),
    onPrimaryContainer = Color(0xFFF2F2F5),
    secondary = Color(0xFFBEBEC6),
    tertiary = Color(0xFF6FD39A),
    background = Color(0xFF0E0E10),
    onBackground = Color(0xFFF2F2F5),
    surface = Color(0xFF0E0E10),
    onSurface = Color(0xFFF2F2F5),
    surfaceVariant = Color(0xFF1C1C21),
    onSurfaceVariant = Color(0xFFA8A8B0),
    outlineVariant = Color(0xFF34343B),
    error = Color(0xFFF2B8B5),
)

/** Colours the Material scheme has no slot for. */
data class ExtraColors(
    /** Unselected chart marks: 3:1 or better against the card they sit on. */
    val chartMuted: Color,
    /** A 1dp edge around cards and icons: depth without shadows. */
    val hairline: Color,
)

private val LightExtra = ExtraColors(chartMuted = Color(0xFF86868E), hairline = Color(0x0F000000))
private val DarkExtra = ExtraColors(chartMuted = Color(0xFF7A7A84), hairline = Color(0x14FFFFFF))

val LocalExtraColors = staticCompositionLocalOf { LightExtra }

val MaterialTheme.extra: ExtraColors
    @Composable @ReadOnlyComposable get() = LocalExtraColors.current

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

/**
 * Material's type scale with two changes: digits are always tabular (every number that changes, such as
 * timers, totals and the counter, keeps its width and does not jitter), and nothing is smaller than 12sp.
 */
private fun appTypography(): Typography {
    val base = Typography()
    fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum")
    return base.copy(
        displayLarge = base.displayLarge.tabular(), displayMedium = base.displayMedium.tabular(),
        displaySmall = base.displaySmall.tabular(), headlineLarge = base.headlineLarge.tabular(),
        headlineMedium = base.headlineMedium.tabular(), headlineSmall = base.headlineSmall.tabular(),
        titleLarge = base.titleLarge.tabular(), titleMedium = base.titleMedium.tabular(),
        titleSmall = base.titleSmall.tabular(),
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp).tabular(),
        bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp).tabular(),
        bodySmall = base.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp).tabular(),
        labelLarge = base.labelLarge.tabular(), labelMedium = base.labelMedium.tabular(),
        labelSmall = base.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp).tabular(),
    )
}

@Composable
fun FocusLiteTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalExtraColors provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            shapes = AppShapes,
            typography = appTypography(),
            content = content,
        )
    }
}
