package com.guesssong.app.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guesssong.app.game.AnswerMatcher
import com.guesssong.app.game.AnswerMode
import com.guesssong.app.game.GameRules
import com.guesssong.app.game.GuessTarget
import com.guesssong.app.net.ClientPhase
import com.guesssong.app.net.GuessStatus
import com.guesssong.app.net.OptionDto
import com.guesssong.app.ui.components.Equalizer
import com.guesssong.app.ui.components.FeedbackChip
import com.guesssong.app.ui.components.OptionShape
import com.guesssong.app.ui.components.appTextFieldColors
import com.guesssong.app.ui.theme.DisplayFont
import com.guesssong.app.ui.theme.GameColors
import com.guesssong.app.ui.theme.Palette
import kotlinx.coroutines.delay

@Composable
fun QuestionContent(phase: ClientPhase.Question, myScore: Int?, onAnswer: (Int) -> Unit, onGuess: (String) -> Unit) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(phase.round) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(TICK_MS)
        }
    }
    val remainingMs = (phase.durationMs - (now - phase.receivedAtMs)).coerceAtLeast(0)
    val timeUp = remainingMs == 0L
    Column(Modifier.fillMaxSize().imePadding().padding(top = 16.dp, bottom = 16.dp)) {
        RoundHeader(phase, myScore)
        when (phase.answerMode) {
            AnswerMode.CHOICES -> ChoicesAnswer(phase, remainingMs, timeUp, onAnswer)
            AnswerMode.TYPING -> TypingAnswer(phase, remainingMs, timeUp, onGuess)
        }
    }
}

@Composable
private fun RoundHeader(phase: ClientPhase.Question, myScore: Int?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Ronda ${phase.round}",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            " de ${phase.totalRounds}",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextMuted,
            modifier = Modifier.weight(1f),
        )
        if (myScore != null) {
            Text(
                "$myScore pts",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(CircleShape).background(Palette.Surface).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(phase.totalRounds) { i ->
            val color = when {
                i < phase.round - 1 -> Palette.Text
                i == phase.round - 1 -> Palette.Accent
                else -> Palette.SurfaceHigh
            }
            Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(color))
        }
    }
}

private fun questionTitle(target: GuessTarget) =
    if (target == GuessTarget.TITLE) "¿Qué canción\nestá sonando?" else "¿Quién canta\nesta canción?"

private fun secondsLeft(remainingMs: Long) = (remainingMs + 999) / 1000

@Composable
private fun ColumnScope.ChoicesAnswer(phase: ClientPhase.Question, remainingMs: Long, timeUp: Boolean, onAnswer: (Int) -> Unit) {
    val locked = phase.isDone || timeUp
    Spacer(Modifier.height(20.dp))
    CountdownRing(remainingMs, phase.durationMs, Modifier.align(Alignment.CenterHorizontally))
    Spacer(Modifier.height(14.dp))
    Text(
        questionTitle(phase.target),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(18.dp))
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        phase.options.withIndex().chunked(2).forEach { row ->
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (index, option) ->
                    OptionButton(
                        option = option,
                        index = index,
                        isSelected = phase.selectedIndex == index,
                        isDimmed = locked && phase.selectedIndex != index,
                        enabled = !locked,
                        onClick = { onAnswer(index) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    val status = when {
        phase.selectedIndex != null -> "¡Respuesta enviada!"
        timeUp -> "¡Se acabó el tiempo!"
        else -> "Entre más rápido, más puntos"
    }
    AnsweredLine(status, phase)
}

@Composable
private fun AnsweredLine(status: String, phase: ClientPhase.Question) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(status, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        if (phase.totalPlayers > 0) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (phase.totalPlayers <= MAX_DOTS) {
                    repeat(phase.totalPlayers) { i ->
                        Box(
                            Modifier
                                .padding(end = 4.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (i < phase.answered) Palette.Accent else Palette.SurfaceHigh),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    "${phase.answered} de ${phase.totalPlayers} ya respondieron",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                )
            }
        }
    }
}

@Composable
private fun CountdownRing(remainingMs: Long, durationMs: Long, modifier: Modifier = Modifier) {
    val fraction = remainingMs.toFloat() / durationMs.coerceAtLeast(1)
    val color = if (remainingMs < LOW_TIME_MS) Palette.Pink else Palette.Accent
    val seconds = secondsLeft(remainingMs)
    Box(
        modifier.size(120.dp).semantics { contentDescription = "Quedan $seconds segundos" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(Palette.Surface, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            drawArc(color, -90f, 360f * fraction, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$seconds", style = MaterialTheme.typography.displayMedium.copy(fontSize = 46.sp, lineHeight = 46.sp))
            Text("segundos", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
        }
    }
}

@Composable
private fun OptionButton(
    option: OptionDto,
    index: Int,
    isSelected: Boolean,
    isDimmed: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = GameColors.options[index % GameColors.options.size]
    val content = GameColors.onOptions[index % GameColors.onOptions.size]
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(22.dp),
        color = color,
        contentColor = content,
        border = if (isSelected) BorderStroke(4.dp, Palette.Text) else null,
        modifier = modifier.fillMaxHeight().alpha(if (isDimmed) DIMMED_ALPHA else 1f),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x29100E17)),
                    contentAlignment = Alignment.Center,
                ) {
                    OptionShape(index, content)
                }
                if (isSelected) {
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Filled.Check, contentDescription = "Tu respuesta", modifier = Modifier.size(26.dp))
                }
            }
            Text(
                option.label,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp, lineHeight = 23.sp),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ColumnScope.TypingAnswer(phase: ClientPhase.Question, remainingMs: Long, timeUp: Boolean, onGuess: (String) -> Unit) {
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
    val low = remainingMs < LOW_TIME_MS
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.weight(1f).height(8.dp).clip(CircleShape).background(Palette.Surface),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(remainingMs.toFloat() / phase.durationMs.coerceAtLeast(1))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(if (low) Palette.Pink else Palette.Accent),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "${secondsLeft(remainingMs)} s",
            fontFamily = DisplayFont,
            style = MaterialTheme.typography.headlineSmall,
            color = if (low) Palette.Pink else Palette.Accent,
        )
    }

    Column(
        Modifier.weight(1f).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Equalizer(bars = 9, height = 56.dp, barWidth = 9.dp, gap = 6.dp, animated = canType)
        Spacer(Modifier.height(18.dp))
        Text(questionTitle(phase.target), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        AttemptPips(phase.attemptsLeft)
    }

    TypingFeedback(phase, timeUp)
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(AnswerMatcher.MAX_GUESS_LENGTH) },
            enabled = canType,
            singleLine = true,
            placeholder = { Text(if (phase.target == GuessTarget.TITLE) "Nombre de la canción" else "Nombre del artista") },
            textStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
            shape = RoundedCornerShape(18.dp),
            colors = appTextFieldColors(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = submit,
            enabled = canSubmit,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Palette.Accent,
                contentColor = Palette.OnAccent,
                disabledContainerColor = Palette.SurfaceHigh,
                disabledContentColor = Palette.TextFaint,
            ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            modifier = Modifier.size(58.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Enviar respuesta")
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "Escribe como te salga: se perdonan tildes y errores",
        style = MaterialTheme.typography.bodySmall,
        color = Palette.TextMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AttemptPips(attemptsLeft: Int) {
    val total = GameRules.MAX_GUESS_ATTEMPTS
    val left = attemptsLeft.coerceIn(0, total)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Intentos", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
        Spacer(Modifier.width(8.dp))
        repeat(total) { i ->
            // Se gastan de izquierda a derecha: las primeras se apagan.
            val available = i >= total - left
            Box(
                Modifier
                    .padding(end = 6.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (available) Palette.Accent else Palette.Outline),
            )
        }
        Spacer(Modifier.width(2.dp))
        Text("$left de $total", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun TypingFeedback(phase: ClientPhase.Question, timeUp: Boolean) {
    when {
        phase.guessStatus == GuessStatus.CORRECT -> FeedbackChip(
            "¡Correcto! Espera a los demás…", Icons.Filled.Check, Palette.Accent, Palette.OnAccent,
        )
        phase.guessStatus == GuessStatus.OUT_OF_ATTEMPTS -> FeedbackChip(
            "Se acabaron tus intentos. Espera a los demás…", Icons.Filled.Close, PINK_TINT, Palette.PinkSoft,
        )
        phase.guessStatus == GuessStatus.CHECKING -> FeedbackChip(
            "Revisando…", Icons.Filled.HourglassBottom, Palette.Surface, Palette.TextMuted,
        )
        timeUp -> FeedbackChip("¡Se acabó el tiempo!", Icons.Filled.HourglassBottom, Palette.Surface, Palette.TextMuted)
        phase.guessStatus == GuessStatus.WRONG -> {
            val left = if (phase.attemptsLeft == 1) "Te queda 1 intento" else "Te quedan ${phase.attemptsLeft} intentos"
            FeedbackChip("“${phase.lastGuess}” no es. $left.", Icons.Filled.Close, PINK_TINT, Palette.PinkSoft)
        }
        else -> Unit
    }
}

private val PINK_TINT = Color(0x1FFF5C93)
private const val TICK_MS = 100L
private const val LOW_TIME_MS = 5_000L
private const val DIMMED_ALPHA = 0.35f
private const val MAX_DOTS = 12
