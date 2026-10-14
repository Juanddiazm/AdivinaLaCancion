package com.guesssong.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guesssong.app.game.GameConfig
import com.guesssong.app.model.Genre
import com.guesssong.app.net.ClientPhase
import com.guesssong.app.net.ClientState
import com.guesssong.app.net.ConnectionStatus
import com.guesssong.app.ui.HostInfo
import com.guesssong.app.ui.components.AppButton
import com.guesssong.app.ui.components.Equalizer
import com.guesssong.app.ui.components.SquareIconButton
import com.guesssong.app.ui.theme.Palette

@Composable
fun RoomScreen(
    state: ClientState?,
    isHost: Boolean,
    hostInfo: HostInfo?,
    genres: List<Genre>,
    onStartGame: (GameConfig) -> Unit,
    onBackToLobby: () -> Unit,
    onAnswer: (Int) -> Unit,
    onGuess: (String) -> Unit,
    onDismissNotice: () -> Unit,
    onLeave: () -> Unit,
) {
    var confirmLeave by remember { mutableStateOf(false) }
    val connection = state?.connection
    BackHandler {
        if (connection is ConnectionStatus.Closed) onLeave() else confirmLeave = true
    }
    // En plena ronda la barra superior estorba: el encabezado de la pregunta ya dice la ronda.
    val inRound = state?.phase is ClientPhase.Question
    Column(Modifier.fillMaxSize()) {
        if (!inRound) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        state?.roomName.orEmpty(),
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isHost) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "ERES EL HOST",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                            color = Palette.Accent,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Palette.SurfaceHigh)
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                }
                SquareIconButton(Icons.AutoMirrored.Filled.Logout, "Salir de la sala") { confirmLeave = true }
            }
        }
        state?.notice?.let { NoticeBanner(it, onDismissNotice) }
        Column(Modifier.weight(1f).padding(horizontal = 20.dp)) {
            when {
                state == null || connection == ConnectionStatus.Connecting -> CenteredMessage("Conectando…", loading = true)
                connection is ConnectionStatus.Closed -> ClosedContent(connection.reason, onLeave)
                else -> PhaseContent(
                    state, isHost, hostInfo, genres, onStartGame, onBackToLobby, onAnswer, onGuess, onLeave,
                )
            }
        }
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("¿Salir de la sala?", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(if (isHost) "Eres el host: la sala se cerrará para todos." else "Perderás tu lugar en la partida.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    onLeave()
                }) { Text("Salir", color = Palette.Pink) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Quedarme") } },
        )
    }
}

@Composable
private fun PhaseContent(
    state: ClientState,
    isHost: Boolean,
    hostInfo: HostInfo?,
    genres: List<Genre>,
    onStartGame: (GameConfig) -> Unit,
    onBackToLobby: () -> Unit,
    onAnswer: (Int) -> Unit,
    onGuess: (String) -> Unit,
    onLeave: () -> Unit,
) {
    when (val phase = state.phase) {
        ClientPhase.Lobby -> LobbyContent(state, isHost, hostInfo, genres, onStartGame)
        is ClientPhase.Preparing -> CenteredMessage(phase.message, loading = true)
        is ClientPhase.Question -> QuestionContent(phase, state.players.firstOrNull { it.id == state.myId }?.score, onAnswer, onGuess)
        is ClientPhase.Reveal -> RevealContent(phase, state)
        ClientPhase.Finished -> FinishedContent(state, isHost, onBackToLobby, onLeave)
    }
}

@Composable
private fun NoticeBanner(message: String, onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.SurfaceHigh)
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Cerrar aviso") }
    }
}

@Composable
fun CenteredMessage(message: String, loading: Boolean = false) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            Equalizer(bars = 7, height = 64.dp, barWidth = 12.dp, gap = 7.dp, highlight = { it == 3 })
            Spacer(Modifier.height(24.dp))
        }
        Text(message, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ClosedContent(reason: String, onLeave: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(reason, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        AppButton("Volver al inicio", onLeave, Modifier.width(260.dp))
    }
}
