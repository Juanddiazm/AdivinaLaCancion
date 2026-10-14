package com.guesssong.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.guesssong.app.net.ClientPhase
import com.guesssong.app.net.ClientState
import com.guesssong.app.net.PlayerDto
import com.guesssong.app.ui.components.AppButton
import com.guesssong.app.ui.components.Avatar
import com.guesssong.app.ui.components.ButtonKind
import com.guesssong.app.ui.components.SectionLabel
import com.guesssong.app.ui.components.avatarColor
import com.guesssong.app.ui.theme.DisplayFont
import com.guesssong.app.ui.theme.Palette

@Composable
fun RevealContent(phase: ClientPhase.Reveal, state: ClientState) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 16.dp),
    ) {
        ResultBanner(phase)
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(phase.coverUrl)
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel("Era")
                Text(phase.title, style = MaterialTheme.typography.headlineMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(phase.artist, style = MaterialTheme.typography.bodyLarge, color = Palette.TextMuted)
            }
        }
        phase.myGuess?.let {
            Spacer(Modifier.height(14.dp))
            Text("Escribiste: “$it”", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
        }
        Spacer(Modifier.height(26.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Tabla", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text("Ronda ${phase.round} de ${phase.totalRounds}", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
        }
        Spacer(Modifier.height(10.dp))
        Leaderboard(state.ranking, state.myId, limit = LEADERBOARD_PREVIEW)
        Spacer(Modifier.height(24.dp))
        Text(
            if (phase.round < phase.totalRounds) "La siguiente ronda empieza en un momento…" else "Calculando el ganador…",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            color = Palette.Text,
            trackColor = Palette.Surface,
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
        )
    }
}

@Composable
private fun ResultBanner(phase: ClientPhase.Reveal) {
    data class Look(val text: String, val icon: ImageVector, val bg: Color, val fg: Color)
    val look = when {
        !phase.didAnswer -> Look("No respondiste a tiempo", Icons.Filled.HourglassBottom, Palette.Surface, Palette.Text)
        phase.myGain > 0 -> Look("¡Correcto!", Icons.Filled.Check, Palette.Accent, Palette.OnAccent)
        else -> Look("¡Fallaste!", Icons.Filled.Close, Palette.Pink, Palette.OnAccent)
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(look.bg).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(look.fg.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(look.icon, contentDescription = null, tint = look.fg, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(look.text, style = MaterialTheme.typography.headlineSmall, color = look.fg, modifier = Modifier.weight(1f))
        if (phase.myGain > 0) {
            Text("+${phase.myGain}", style = MaterialTheme.typography.headlineMedium, color = look.fg)
        }
    }
}

@Composable
private fun Cover(url: String?) {
    val modifier = Modifier.size(116.dp).clip(RoundedCornerShape(18.dp))
    if (url == null) {
        Box(modifier.background(Palette.SurfaceHigh), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Palette.TextFaint, modifier = Modifier.size(44.dp))
        }
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
    val podium = ranking.take(PODIUM_SIZE)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SectionLabel("Fin de la partida")
        Spacer(Modifier.height(8.dp))
        Text(headline, style = MaterialTheme.typography.displayMedium.copy(fontSize = 40.sp), textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        if (podium.size >= 2) {
            Podium(podium, state.myId)
            Spacer(Modifier.height(14.dp))
            Leaderboard(ranking.drop(PODIUM_SIZE), state.myId, limit = Int.MAX_VALUE, startPosition = PODIUM_SIZE + 1)
        } else {
            Leaderboard(ranking, state.myId, limit = Int.MAX_VALUE)
        }
        Spacer(Modifier.height(24.dp))
        if (isHost) {
            AppButton("Jugar otra vez", onBackToLobby, icon = Icons.Filled.Replay)
            Spacer(Modifier.height(10.dp))
        } else {
            Text(
                "Esperando a que el host arme otra partida…",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
        }
        AppButton("Salir", onLeave, kind = ButtonKind.Secondary, icon = Icons.AutoMirrored.Filled.Logout, height = 52.dp)
    }
}

/** Podio con el 2.º a la izquierda, el 1.º al centro (más alto) y el 3.º a la derecha. */
@Composable
private fun Podium(top: List<PlayerDto>, myId: String?) {
    val slots = listOfNotNull(top.getOrNull(1)?.let { 2 to it }, 1 to top[0], top.getOrNull(2)?.let { 3 to it })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        slots.forEach { (position, player) ->
            val color = avatarColor(player.id)
            val style = when (position) {
                1 -> PodiumStyle(140.dp, 68.dp, color, Palette.OnAccent)
                2 -> PodiumStyle(96.dp, 56.dp, Palette.SurfaceHigh, Palette.Text)
                else -> PodiumStyle(68.dp, 52.dp, Palette.Surface, Palette.TextMuted)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(player.name, color, style.avatar, highlighted = position == 1)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (player.id == myId) "${player.name} (tú)" else player.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${player.score}", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(style.height)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
                        .background(style.block),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(
                        "$position",
                        fontFamily = DisplayFont,
                        style = MaterialTheme.typography.displaySmall,
                        color = style.number,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

private data class PodiumStyle(val height: Dp, val avatar: Dp, val block: Color, val number: Color)

@Composable
fun Leaderboard(ranking: List<PlayerDto>, myId: String?, limit: Int, startPosition: Int = 1) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ranking.take(limit).forEachIndexed { index, player ->
            LeaderboardRow(position = startPosition + index, player = player, isMe = player.id == myId)
        }
    }
}

@Composable
private fun LeaderboardRow(position: Int, player: PlayerDto, isMe: Boolean) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(if (isMe) Palette.SurfaceHigh else Palette.Surface)
            .border(2.dp, if (isMe) Palette.Accent else Color.Transparent, shape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$position",
            fontFamily = DisplayFont,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
            color = Palette.TextMuted,
            modifier = Modifier.width(28.dp),
        )
        Avatar(player.name, avatarColor(player.id), 34.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            buildString {
                append(player.name)
                if (isMe) append(" (tú)")
                if (!player.connected) append(" · desconectado")
            },
            style = if (isMe) MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp) else MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
            color = if (player.connected) Palette.Text else Palette.TextFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text("${player.score}", fontFamily = DisplayFont, style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
    }
}

private const val LEADERBOARD_PREVIEW = 5
private const val PODIUM_SIZE = 3
