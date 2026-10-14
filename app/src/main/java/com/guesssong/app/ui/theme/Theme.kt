package com.guesssong.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.guesssong.app.R

/** Paleta "noche de karaoke": fondo casi negro, un acento lima y cuatro colores de juego. */
object Palette {
    val Background = Color(0xFF100E17)
    val Surface = Color(0xFF1C1927)
    val SurfaceHigh = Color(0xFF2A2638)
    val Outline = Color(0xFF3A3550)
    val Text = Color(0xFFF5F2EA)
    val TextMuted = Color(0xFFABA5BC)
    val TextFaint = Color(0xFF8A849C)
    val Accent = Color(0xFFD7FF3D)
    val OnAccent = Color(0xFF15130C)
    val Pink = Color(0xFFFF5C93)
    val PinkSoft = Color(0xFFFF8FB4)
    val Sky = Color(0xFF5AB0FF)
    val Amber = Color(0xFFFFB547)
    val Violet = Color(0xFF7B5CFF)
}

object GameColors {
    /** Colores de las 4 opciones; cada una lleva además una forma para no depender solo del color. */
    val options = listOf(Palette.Pink, Palette.Sky, Palette.Amber, Palette.Violet)
    val onOptions = listOf(Palette.OnAccent, Palette.OnAccent, Palette.OnAccent, Color.White)
    val avatars = listOf(Palette.Pink, Palette.Sky, Palette.Amber, Palette.Violet, Palette.Accent)
    val correct = Palette.Accent
    val wrong = Palette.Pink
}

/**
 * Títulos con carácter (Bricolage Grotesque) y texto corrido muy legible (Figtree), ambas con licencia OFL.
 * Son cortes estáticos sacados de las fuentes variables: así el peso no depende del soporte de ejes del teléfono.
 */
val DisplayFont = FontFamily(
    Font(R.font.bricolage_grotesque_600, FontWeight.SemiBold),
    Font(R.font.bricolage_grotesque_800, FontWeight.ExtraBold),
)

val BodyFont = FontFamily(
    Font(R.font.figtree_400, FontWeight.Normal),
    Font(R.font.figtree_500, FontWeight.Medium),
    Font(R.font.figtree_600, FontWeight.SemiBold),
    Font(R.font.figtree_700, FontWeight.Bold),
)

private val Colors = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Palette.OnAccent,
    primaryContainer = Palette.SurfaceHigh,
    onPrimaryContainer = Palette.Text,
    secondary = Palette.Pink,
    onSecondary = Palette.OnAccent,
    background = Palette.Background,
    onBackground = Palette.Text,
    surface = Palette.Background,
    onSurface = Palette.Text,
    surfaceVariant = Palette.Surface,
    onSurfaceVariant = Palette.TextMuted,
    surfaceContainerLowest = Palette.Background,
    surfaceContainerLow = Palette.Surface,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.Surface,
    surfaceContainerHighest = Palette.SurfaceHigh,
    outline = Palette.Outline,
    outlineVariant = Palette.SurfaceHigh,
    error = Palette.Pink,
)

private fun display(size: Int, lineHeight: Int) = TextStyle(
    fontFamily = DisplayFont,
    fontWeight = FontWeight.ExtraBold,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
)

private fun body(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = BodyFont,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
)

private val AppTypography = Typography(
    displayLarge = display(54, 52).copy(letterSpacing = (-1).sp),
    displayMedium = display(44, 44),
    displaySmall = display(36, 38),
    headlineLarge = display(32, 36),
    headlineMedium = display(28, 32),
    headlineSmall = display(24, 28),
    titleLarge = display(20, 24),
    titleMedium = body(17, 22, FontWeight.Bold),
    titleSmall = body(15, 20, FontWeight.Bold),
    bodyLarge = body(17, 24),
    bodyMedium = body(15, 21),
    bodySmall = body(13, 19),
    labelLarge = body(15, 20, FontWeight.Bold),
    labelMedium = body(13, 16, FontWeight.Bold),
    labelSmall = body(12, 16, FontWeight.Bold),
)

@Composable
fun GuessSongTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = AppTypography, content = content)
}
