package de.beat2er.garage.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val GarageDarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = TextPrimary,
    secondary = BgCard,
    onSecondary = TextPrimary,
    background = BgDark,
    onBackground = TextPrimary,
    surface = BgCard,
    onSurface = TextPrimary,
    surfaceVariant = BgCardHover,
    onSurfaceVariant = TextDim,
    outline = Border,
    error = Accent,
    onError = TextPrimary
)

@Composable
fun GarageTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GarageDarkColorScheme,
        typography = GarageTypography,
        content = content
    )
}
