package com.guesssong.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.guesssong.app.game.GameConfig
import com.guesssong.app.model.Genre
import com.guesssong.app.net.ClientPhase
import com.guesssong.app.net.ClientState
import com.guesssong.app.net.ConnectionStatus
import com.guesssong.app.ui.HostInfo

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

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                state?.roomName.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { confirmLeave = true }) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Salir de la sala")
            }
        }
        state?.notice?.let { NoticeBanner(it, onDismissNotice) }
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
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
            title = { Text("¿Salir de la sala?") },
            text = {
                Text(if (isHost) "Eres el host: la sala se cerrará para todos." else "Perderás tu lugar en la partida.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    onLeave()
                }) { Text("Salir") }
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
        is ClientPhase.Question -> QuestionContent(phase, onAnswer, onGuess)
        is ClientPhase.Reveal -> RevealContent(phase, state)
        ClientPhase.Finished -> FinishedContent(state, isHost, onBackToLobby, onLeave)
    }
}

@Composable
private fun NoticeBanner(message: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Cerrar aviso") }
        }
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
            CircularProgressIndicator()
            Spacer(Modifier.height(20.dp))
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
        Text(reason, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onLeave) { Text("Volver al inicio") }
    }
}
