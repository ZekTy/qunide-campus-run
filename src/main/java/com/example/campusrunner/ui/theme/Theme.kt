package com.example.campusrunner.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CampusRunnerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(context)

        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)

        darkTheme -> routeVergeDarkColorScheme()
        else -> routeVergeLightColorScheme()
    }.withRouteVergeEssentials(darkTheme)

    val baseTypography = Typography()
    MaterialTheme(
        colorScheme = baseScheme,
        typography = baseTypography.copy(
            titleLarge = baseTypography.titleLarge.copy(fontWeight = FontWeight.Bold),
            titleMedium = baseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            labelLarge = baseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(12.dp),
            small = RoundedCornerShape(16.dp),
            medium = RoundedCornerShape(22.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(34.dp)
        ),
        content = content
    )
}

private fun routeVergeLightColorScheme() = lightColorScheme(
    primary = MiuixSkin.Primary,
    onPrimary = Color.White,
    primaryContainer = MiuixSkin.PrimarySoft,
    onPrimaryContainer = MiuixSkin.Primary,
    secondary = MiuixSkin.Success,
    background = MiuixSkin.Background,
    onBackground = MiuixSkin.Text,
    surface = MiuixSkin.Surface,
    onSurface = MiuixSkin.Text,
    surfaceVariant = MiuixSkin.SurfaceContainer,
    onSurfaceVariant = MiuixSkin.TextMuted,
    outline = MiuixSkin.Border,
    error = MiuixSkin.Danger
)

private fun routeVergeDarkColorScheme() = darkColorScheme(
    primary = Color(0xFF9DC3FF),
    onPrimary = Color(0xFF00325D),
    primaryContainer = Color(0xFF124B86),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFF77DD96),
    background = Color(0xFF101318),
    onBackground = Color(0xFFE1E4EA),
    surface = Color(0xFF161A21),
    onSurface = Color(0xFFE1E4EA),
    surfaceVariant = Color(0xFF303640),
    onSurfaceVariant = Color(0xFFC4CAD4),
    outline = Color(0xFF474F5C),
    error = Color(0xFFFFB4AB)
)

private fun ColorScheme.withRouteVergeEssentials(darkTheme: Boolean): ColorScheme {
    return if (darkTheme) {
        copy(error = error)
    } else {
        copy(
            onPrimary = Color.White,
            error = MiuixSkin.Danger
        )
    }
}

object MiuixSkin {
    const val PrimaryHex = "#3482FF"
    const val SuccessHex = "#36D167"

    val Primary = Color(0xFF3482FF)
    val PrimarySoft = Color(0xFFEAF2FF)
    val Success = Color(0xFF36D167)
    val Warning = Color(0xFFFFA726)
    val Danger = Color(0xFFFF4D4F)
    val Background = Color(0xFFF4F6FB)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceElevated = Color(0xFFFDFEFF)
    val SurfaceFloating = Color(0xF4FFFFFF)
    val SurfaceContainer = Color(0xFFF0F3F9)
    val Border = Color(0xFFDDE3EE)
    val Text = Color(0xFF111827)
    val TextMuted = Color(0xFF667085)
    val TextDisabled = Color(0xFF98A2B3)
    val DisabledContainer = Color(0xFFE8ECF3)

    val CardShape = RoundedCornerShape(28.dp)
    val FieldShape = RoundedCornerShape(18.dp)
    val ActionShape = RoundedCornerShape(22.dp)
    val PillShape = RoundedCornerShape(999.dp)
    val FloatingShape = RoundedCornerShape(30.dp)
}
