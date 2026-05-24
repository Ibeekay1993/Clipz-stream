package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryNeon,
    onPrimary = Color.Black,
    secondary = SecondaryNeon,
    onSecondary = Color.Black,
    tertiary = AccentNeon,
    onTertiary = Color.Black,
    background = DarkBg,
    onBackground = TextWhite,
    surface = SurfaceSlate,
    onSurface = TextWhite,
    surfaceVariant = ContainerGrey,
    onSurfaceVariant = TextWhite
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force premium dark mode for video editing layout
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
