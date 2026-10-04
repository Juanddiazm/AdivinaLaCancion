package com.guesssong.app.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.guesssong.app.game.AnswerMatcher
import com.guesssong.app.game.AnswerMode
import com.guesssong.app.game.GuessTarget
import com.guesssong.app.net.ClientPhase
import com.guesssong.app.net.GuessStatus
import com.guesssong.app.net.OptionDto
import com.guesssong.app.ui.theme.GameColors
import kotlinx.coroutines.delay

@Composable
fun QuestionContent(phase: ClientPhase.Question, onAnswer: (Int) -> Unit, onGuess: (String) -> Unit) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(phase.round) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(TICK_MS)
        }
    }
    val remainingMs = (phase.durationMs - (now - phase.receivedAtMs)).coerceAtLeast(0)
    val timeUp = remainingMs == 0L

    Column(
        Modifier.fillMaxSize().imePadding().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Countdown(phase, remainingMs)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                if (phase.target == GuessTarget.TITLE) "  ¿Qué canción está sonando?" else "  ¿Quién canta esta canción?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        when (phase.answerMode) {
            AnswerMode.CHOICES -> ChoicesAnswer(phase, timeUp, onAnswer)
            AnswerMode.TYPING -> TypingAnswer(phase, timeUp, onGuess)
        }

        if (phase.totalPlayers > 0) {
            Text(
                "Listos: ${phase.answered} de ${phase.totalPlayers}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun Countdown(phase: ClientPhase.Question, remainingMs: Long) {
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
}

@Composable
private fun ColumnScope.ChoicesAnswer(phase: ClientPhase.Question, timeUp: Boolean, onAnswer: (Int) -> Unit) {
    val locked = phase.isDone || timeUp
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
}

@Composable
private fun ColumnScope.TypingAnswer(phase: ClientPhase.Question, timeUp: Boolean, onGuess: (String) -> Unit) {
    var text by remember(phase.round) { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    // El campo sigue habilitado mientras se revisa un intento para no perder el foco ni el teclado.
    val canType = !phase.isDone && !timeUp
    val canSubmit = canType && phase.guessStatus != GuessStatus.CHECKING && text.isNotBlank()
    val submit = {
        if (canSubmit) {
            onGuess(text)
            text = ""
        }
    }
    LaunchedEffect(phase.round, phase.guessStatus) {
        if (!phase.isDone) runCatching { focusRequester.requestFocus() }
    }

    Spacer(Modifier.weight(1f))
    Icon(
        Icons.Filled.GraphicEq,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.size(96.dp).align(Alignment.CenterHorizontally),
    )
    Spacer(Modifier.weight(1f))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(AnswerMatcher.MAX_GUESS_LENGTH) },
            enabled = canType,
            singleLine = true,
            label = { Text(if (phase.target == GuessTarget.TITLE) "Nombre de la canción" else "Nombre del artista") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
        )
        Spacer(Modifier.size(8.dp))
        Button(onClick = submit, enabled = canSubmit, modifier = Modifier.height(56.dp)) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
        }
    }
    val (status, color) = typingStatus(phase, timeUp)
    Text(
        status,
        style = MaterialTheme.typography.titleMedium,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun typingStatus(phase: ClientPhase.Question, timeUp: Boolean): Pair<String, Color> {
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    val status = phase.guessStatus
    return when {
        status == GuessStatus.CORRECT -> "¡Correcto! Espera a los demás…" to GameColors.correct
        status == GuessStatus.OUT_OF_ATTEMPTS -> "Se acabaron tus intentos. Espera a los demás…" to GameColors.wrong
        status == GuessStatus.CHECKING -> "Revisando…" to neutral
        timeUp -> "¡Se acabó el tiempo!" to neutral
        status == GuessStatus.WRONG -> {
            val left = if (phase.attemptsLeft == 1) "Te queda 1 intento" else "Te quedan ${phase.attemptsLeft} intentos"
            "“${phase.lastGuess}” no es. $left" to GameColors.wrong
        }
        else -> "Escribe como te salga: se perdonan errores de ortografía" to neutral
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
                option.label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val TICK_MS = 100L
private const val LOW_TIME_MS = 5_000L
private const val DIMMED_ALPHA = 0.35f
