package com.guesssong.app.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.guesssong.app.net.ClientPhase
import com.guesssong.app.net.OptionDto
import com.guesssong.app.ui.theme.GameColors
import kotlinx.coroutines.delay

@Composable
fun QuestionContent(phase: ClientPhase.Question, onAnswer: (Int) -> Unit) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(phase.round) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(TICK_MS)
        }
    }
    val remainingMs = (phase.durationMs - (now - phase.receivedAtMs)).coerceAtLeast(0)
    val timeUp = remainingMs == 0L
    val locked = phase.selectedIndex != null || timeUp

    Column(Modifier.fillMaxSize().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Ronda ${phase.round} de ${phase.totalRounds}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${(remainingMs + 999) / 1000}s",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = if (remainingMs < LOW_TIME_MS) GameColors.wrong else MaterialTheme.colorScheme.secondary,
            )
        }
        LinearProgressIndicator(
            progress = { remainingMs.toFloat() / phase.durationMs.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("  ¿Qué canción está sonando?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        phase.options.forEachIndexed { index, option ->
            OptionButton(
                option = option,
                color = GameColors.options[index % GameColors.options.size],
                isSelected = phase.selectedIndex == index,
                isDimmed = locked && phase.selectedIndex != index,
                enabled = !locked,
                onClick = { onAnswer(index) },
                modifier = Modifier.weight(1f),
            )
        }

        val status = when {
            phase.selectedIndex != null -> "¡Respuesta enviada! Espera a los demás…"
            timeUp -> "¡Se acabó el tiempo!"
            else -> "Toca la respuesta: entre más rápido, más puntos"
        }
        Text(status, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
        if (phase.totalPlayers > 0) {
            Text(
                "Respondieron ${phase.answered} de ${phase.totalPlayers}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun OptionButton(
    option: OptionDto,
    color: Color,
    isSelected: Boolean,
    isDimmed: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = color,
            contentColor = Color.White,
            disabledContainerColor = color,
            disabledContentColor = Color.White,
        ),
        border = if (isSelected) BorderStroke(4.dp, Color.White) else null,
        modifier = modifier.fillMaxWidth().alpha(if (isDimmed) DIMMED_ALPHA else 1f),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                option.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(option.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private const val TICK_MS = 100L
private const val LOW_TIME_MS = 5_000L
private const val DIMMED_ALPHA = 0.35f
