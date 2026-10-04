package com.guesssong.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.guesssong.app.net.ClientPhase
import com.guesssong.app.net.ClientState
import com.guesssong.app.net.PlayerDto
import com.guesssong.app.ui.theme.GameColors

@Composable
fun RevealContent(phase: ClientPhase.Reveal, state: ClientState) {
    val (headline, color) = when {
        !phase.didAnswer -> "No respondiste a tiempo" to MaterialTheme.colorScheme.onSurfaceVariant
        phase.myGain > 0 -> "¡Correcto! +${phase.myGain}" to GameColors.correct
        else -> "¡Fallaste!" to GameColors.wrong
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(headline, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = color)
        Cover(phase.coverUrl)
        Text(
            phase.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(phase.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        phase.myGuess?.let {
            Text("Escribiste: \u201C$it\u201D", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }
        Leaderboard(state.ranking, state.myId, limit = LEADERBOARD_PREVIEW)
        Text(
            if (phase.round < phase.totalRounds) "La siguiente ronda empieza en un momento…" else "Calculando el ganador…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Cover(url: String?) {
    val modifier = Modifier.size(170.dp).clip(RoundedCornerShape(16.dp))
    if (url == null) {
        Icon(
            Icons.Filled.MusicNote,
            contentDescription = null,
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(40.dp),
        )
    } else {
        AsyncImage(model = url, contentDescription = "Portada", contentScale = ContentScale.Crop, modifier = modifier)
    }
}

@Composable
fun FinishedContent(state: ClientState, isHost: Boolean, onBackToLobby: () -> Unit, onLeave: () -> Unit) {
    val ranking = state.ranking
    val topScore = ranking.firstOrNull()?.score ?: 0
    val winners = ranking.filter { it.score == topScore }
    val headline = when {
        ranking.isEmpty() -> "Fin de la partida"
        winners.size > 1 -> "¡Empate entre ${winners.joinToString(" y ") { it.name }}!"
        winners.first().id == state.myId -> "¡Ganaste!"
        else -> "¡Ganó ${winners.first().name}!"
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = GameColors.gold, modifier = Modifier.size(96.dp))
        Text(headline, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Leaderboard(ranking, state.myId, limit = Int.MAX_VALUE)
        if (isHost) {
            Button(onClick = onBackToLobby, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Jugar otra vez") }
        } else {
            Text("Esperando a que el host arme otra partida…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) { Text("Salir") }
    }
}

@Composable
fun Leaderboard(ranking: List<PlayerDto>, myId: String?, limit: Int) {
    Card(Modifier.fillMaxWidth()) {
        Column {
            ranking.take(limit).forEachIndexed { index, player ->
                LeaderboardRow(position = index + 1, player = player, isMe = player.id == myId)
            }
        }
    }
}

@Composable
private fun LeaderboardRow(position: Int, player: PlayerDto, isMe: Boolean) {
    val background = if (isMe) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Row(
        Modifier.fillMaxWidth().background(background).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            MEDALS.getOrNull(position - 1) ?: "$position.",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(36.dp),
        )
        Text(
            player.name + if (player.connected) "" else " (desconectado)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text("${player.score}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

private val MEDALS = listOf("🥇", "🥈", "🥉")
private const val LEADERBOARD_PREVIEW = 5
