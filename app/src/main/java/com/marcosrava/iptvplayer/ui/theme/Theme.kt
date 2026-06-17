package com.marcosrava.iptvplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = IPTVPrimary,
    secondary = IPTVSecondary,
    background = IPTVBackground,
    surface = IPTVSurface,
    surfaceVariant = IPTVSurfaceVariant,
    onPrimary = IPTVOnPrimary,
    onBackground = IPTVOnBackground,
    onSurface = IPTVOnSurface,
    error = IPTVLive
)

@Composable
fun IPTVPlayerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
