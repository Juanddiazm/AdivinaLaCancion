package com.guesssong.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guesssong.app.ui.theme.DisplayFont
import com.guesssong.app.ui.theme.GameColors
import com.guesssong.app.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

enum class ButtonKind { Primary, Secondary }

/** Botón grande y redondeado; el primario es lima, el secundario es una superficie con borde. */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 60.dp,
) {
    val primary = kind == ButtonKind.Primary
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) Palette.Accent else Color.Transparent,
            contentColor = if (primary) Palette.OnAccent else Palette.Text,
            disabledContainerColor = if (primary) Palette.SurfaceHigh else Color.Transparent,
            disabledContentColor = Palette.TextFaint,
        ),
        border = if (primary) null else BorderStroke(1.5.dp, Palette.Outline),
        modifier = modifier.fillMaxWidth().height(height),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text,
            fontFamily = if (primary) DisplayFont else null,
            style = if (primary) MaterialTheme.typography.headlineSmall.copy(fontSize = 21.sp) else MaterialTheme.typography.titleMedium,
        )
    }
}

/** Botón cuadrado de 48 dp para acciones de la barra superior (volver, salir). */
@Composable
fun SquareIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Palette.Surface,
        contentColor = Palette.Text,
        modifier = Modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(22.dp))
        }
    }
}

/** Etiqueta pequeña en mayúsculas que encabeza una sección. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.6.sp),
        color = Palette.TextMuted,
        modifier = modifier,
    )
}

/** Mismo color para el mismo jugador en todos los teléfonos (el id es igual en todos). */
fun avatarColor(key: String): Color = GameColors.avatars[abs(key.hashCode()) % GameColors.avatars.size]

/** Círculo con la inicial; [highlighted] le pone un anillo por dentro del mismo tamaño total. */
@Composable
fun Avatar(name: String, color: Color, size: Dp, highlighted: Boolean = false, modifier: Modifier = Modifier) {
    val ring = if (highlighted) size * RING_FRACTION else 0.dp
    Box(
        modifier
            .size(size)
            .then(if (highlighted) Modifier.border(2.dp, color, CircleShape) else Modifier)
            .padding(ring)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            fontFamily = DisplayFont,
            fontWeight = FontWeight.ExtraBold,
            fontSize = ((size - ring * 2).value * 0.42f).sp,
            color = Palette.OnAccent,
        )
    }
}

private const val RING_FRACTION = 0.1f

/** Barras de ecualizador que suben y bajan; [highlight] marca qué barras van en color de acento. */
@Composable
fun Equalizer(
    bars: Int,
    height: Dp,
    modifier: Modifier = Modifier,
    barWidth: Dp = 10.dp,
    gap: Dp = 6.dp,
    animated: Boolean = true,
    highlight: (Int) -> Boolean = { false },
) {
    val transition = rememberInfiniteTransition(label = "eq")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(EQ_PERIOD_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "eqPhase",
    )
    val width = barWidth * bars + gap * (bars - 1)
    Canvas(modifier.width(width).height(height)) {
        val w = barWidth.toPx()
        val step = w + gap.toPx()
        repeat(bars) { i ->
            val offset = (i * 0.37f) % 1f
            val level = if (animated) {
                0.3f + 0.7f * (0.5f + 0.5f * sin(2 * PI * (phase + offset)).toFloat())
            } else {
                STATIC_LEVELS[i % STATIC_LEVELS.size]
            }
            val h = size.height * level
            drawRoundRect(
                color = if (highlight(i)) Palette.Accent else Palette.Outline,
                topLeft = Offset(i * step, size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2, w / 2),
            )
        }
    }
}

/** Forma de cada opción (triángulo, rombo, círculo, cuadrado), como en los juegos de concurso. */
@Composable
fun OptionShape(index: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(18.dp)) {
        val s = size.minDimension
        when (index % 4) {
            0 -> drawPath(Path().apply { moveTo(s / 2, 0f); lineTo(s, s * 0.9f); lineTo(0f, s * 0.9f); close() }, color)
            1 -> drawPath(Path().apply { moveTo(s / 2, 0f); lineTo(s, s / 2); lineTo(s / 2, s); lineTo(0f, s / 2); close() }, color)
            2 -> drawCircle(color, radius = s / 2 * 0.95f)
            else -> drawRoundRect(color, topLeft = Offset(s * 0.06f, s * 0.06f), size = Size(s * 0.88f, s * 0.88f), cornerRadius = CornerRadius(s * 0.1f))
        }
    }
}

/**
 * Selector de pastillas sobre un carril oscuro: la opción elegida queda en blanco.
 * Sirve para rondas, tiempo y para Género/Buscar.
 */
@Composable
fun <T> PillSelector(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = true,
    height: Dp = 44.dp,
    large: Boolean = true,
) {
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(Palette.Background).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            PillItem(label, value == selected, { onSelect(value) }, if (fill) Modifier.weight(1f) else Modifier, height, large)
        }
    }
}

@Composable
private fun RowScope.PillItem(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier, height: Dp, large: Boolean) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Palette.Text else Color.Transparent,
        contentColor = if (selected) Palette.OnAccent else Palette.TextMuted,
        modifier = modifier.height(height),
    ) {
        Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                fontFamily = if (large) DisplayFont else null,
                style = if (large) MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp) else MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun appTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Palette.Surface,
    unfocusedContainerColor = Palette.Surface,
    disabledContainerColor = Palette.Surface,
    focusedBorderColor = Palette.Accent,
    unfocusedBorderColor = Palette.Outline,
    disabledBorderColor = Palette.SurfaceHigh,
    cursorColor = Palette.Accent,
    focusedTextColor = Palette.Text,
    unfocusedTextColor = Palette.Text,
    focusedPlaceholderColor = Palette.TextFaint,
    unfocusedPlaceholderColor = Palette.TextFaint,
    focusedLabelColor = Palette.Accent,
    unfocusedLabelColor = Palette.TextMuted,
)

/** Banda de aviso (correcto, error o neutro) con ícono a la izquierda. */
@Composable
fun FeedbackChip(text: String, icon: ImageVector, background: Color, content: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(background).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

private const val EQ_PERIOD_MS = 1100
private val STATIC_LEVELS = listOf(0.32f, 0.6f, 0.93f, 0.54f, 0.79f, 1f, 0.46f, 0.71f, 0.39f, 0.86f, 0.64f, 0.96f)
