package com.guesssong.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    primary = Color(0xFFC6A0FF),
    onPrimary = Color(0xFF2A0A5E),
    primaryContainer = Color(0xFF4B2A8C),
    onPrimaryContainer = Color(0xFFEBDDFF),
    secondary = Color(0xFF4DE8E0),
    onSecondary = Color(0xFF003734),
    background = Color(0xFF130B26),
    onBackground = Color(0xFFEDE6FA),
    surface = Color(0xFF1D1436),
    onSurface = Color(0xFFEDE6FA),
    surfaceVariant = Color(0xFF2B2149),
    onSurfaceVariant = Color(0xFFCBC0E3),
    error = Color(0xFFFF6B81),
)

object GameColors {
    val options = listOf(
        Color(0xFFE5395B),
        Color(0xFF2F80ED),
        Color(0xFFF2A516),
        Color(0xFF27AE60),
    )
    val correct = Color(0xFF2ECC71)
    val wrong = Color(0xFFFF5370)
    val gold = Color(0xFFFFD54F)
}

@Composable
fun GuessSongTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
