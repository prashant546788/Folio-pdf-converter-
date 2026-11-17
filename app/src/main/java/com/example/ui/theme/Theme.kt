package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = VermilionDarkPrimary,
    onPrimary = VermilionDarkOnPrimary,
    primaryContainer = VermilionDarkContainer,
    onPrimaryContainer = VermilionDarkOnContainer,
    secondary = SlateDarkSecondary,
    onSecondary = SlateDarkOnSecondary,
    secondaryContainer = SlateDarkContainer,
    onSecondaryContainer = SlateDarkOnContainer,
    tertiary = SageDarkTertiary,
    onTertiary = SageDarkOnTertiary,
    tertiaryContainer = SageDarkContainer,
    onTertiaryContainer = SageDarkOnContainer,
    background = StudioDarkBackground,
    onBackground = StudioDarkText,
    surface = StudioDarkSurface,
    onSurface = StudioDarkText,
    surfaceVariant = StudioDarkSurfaceContainer,
    onSurfaceVariant = StudioDarkTextSecondary,
    surfaceContainerLowest = StudioDarkBackground,
    surfaceContainerLow = StudioDarkSurfaceContainerLow,
    surfaceContainer = StudioDarkSurfaceContainer,
    surfaceContainerHigh = StudioDarkSurfaceContainerHigh,
    outline = StudioDarkOutline,
    outlineVariant = StudioDarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = VermilionPrimary,
    onPrimary = Color.White,
    primaryContainer = VermilionContainer,
    onPrimaryContainer = VermilionOnContainer,
    secondary = SlateSecondary,
    onSecondary = Color.White,
    secondaryContainer = SlateContainer,
    onSecondaryContainer = SlateOnContainer,
    tertiary = SageTertiary,
    onTertiary = Color.White,
    tertiaryContainer = SageContainer,
    onTertiaryContainer = SageOnContainer,
    background = IvoryBackground,
    onBackground = InkPrimary,
    surface = PaperSurface,
    onSurface = InkPrimary,
    surfaceVariant = PaperSurfaceContainer,
    onSurfaceVariant = InkSecondary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = PaperSurfaceContainerLow,
    surfaceContainer = PaperSurfaceContainer,
    surfaceContainerHigh = PaperSurfaceContainerHigh,
    outline = PaperOutline,
    outlineVariant = PaperOutlineVariant
)

val FolioShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FolioPdfTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FolioTypography,
        shapes = FolioShapes,
        content = content
    )
}
