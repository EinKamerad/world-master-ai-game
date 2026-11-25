package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.example.data.preferences.AppThemeMode

private val DarkRpgColorScheme = darkColorScheme(
    primary = ArcaneAmber,
    onPrimary = OnArcaneAmber,
    primaryContainer = ArcaneAmberContainer,
    onPrimaryContainer = OnArcaneAmberContainer,
    secondary = MysticTeal,
    onSecondary = OnMysticTeal,
    secondaryContainer = MysticTealContainer,
    onSecondaryContainer = OnMysticTealContainer,
    tertiary = EldritchViolet,
    onTertiary = OnEldritchViolet,
    tertiaryContainer = EldritchVioletContainer,
    onTertiaryContainer = OnEldritchVioletContainer,
    error = CrimsonAlert,
    onError = OnCrimsonAlert,
    errorContainer = CrimsonAlertContainer,
    onErrorContainer = OnCrimsonAlertContainer,
    background = ObsidianBg,
    onBackground = IvoryText,
    surface = ObsidianSurface,
    onSurface = IvoryText,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = MutedSilverText,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceHigh,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    outline = ObsidianOutline
)

private val ColorErrorLight = androidx.compose.ui.graphics.Color(0xFFB3261E)
private val ColorErrorContainerLight = androidx.compose.ui.graphics.Color(0xFFF9DEDC)
private val ColorOnErrorContainerLight = androidx.compose.ui.graphics.Color(0xFF410E0B)

private val LightRpgColorScheme = lightColorScheme(
    primary = BronzeGold,
    onPrimary = OnBronzeGold,
    primaryContainer = BronzeGoldContainer,
    onPrimaryContainer = OnBronzeGoldContainer,
    secondary = DeepSage,
    onSecondary = OnDeepSage,
    secondaryContainer = DeepSageContainer,
    onSecondaryContainer = OnDeepSageContainer,
    tertiary = RoyalPlum,
    onTertiary = OnRoyalPlum,
    tertiaryContainer = RoyalPlumContainer,
    onTertiaryContainer = OnRoyalPlumContainer,
    error = ColorErrorLight,
    onError = OnBronzeGold,
    errorContainer = ColorErrorContainerLight,
    onErrorContainer = ColorOnErrorContainerLight,
    background = ParchmentBg,
    onBackground = InkText,
    surface = ParchmentSurface,
    onSurface = InkText,
    surfaceVariant = ParchmentSurfaceHigh,
    onSurfaceVariant = WarmSlateText,
    surfaceContainerLow = ParchmentSurface,
    surfaceContainer = ParchmentSurfaceHigh,
    surfaceContainerHigh = ParchmentSurfaceHighest,
    outline = ParchmentOutline
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun RpgMasterTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (useDark) DarkRpgColorScheme else LightRpgColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
