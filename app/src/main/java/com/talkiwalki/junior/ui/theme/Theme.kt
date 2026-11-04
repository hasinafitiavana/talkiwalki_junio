package com.talkiwalki.junior.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Emerald,
    onPrimary = Night,
    primaryContainer = EmeraldDark,
    onPrimaryContainer = OnNight,
    secondary = Emerald,
    background = Night,
    onBackground = OnNight,
    surface = SurfaceNight,
    onSurface = OnNight,
    surfaceVariant = SurfaceVariantNight,
    onSurfaceVariant = Color(0xFFC0D1C9),
    error = ErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = LightEmerald,
    secondary = LightEmerald
)

@Composable
fun TalkiWalkiJuniorTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
