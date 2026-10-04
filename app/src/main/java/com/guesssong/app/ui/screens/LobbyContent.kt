package com.guesssong.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.guesssong.app.game.GameConfig
import com.guesssong.app.model.Genre
import com.guesssong.app.model.MusicSource
import com.guesssong.app.net.ClientState
import com.guesssong.app.ui.HostInfo

@Composable
fun LobbyContent(
    state: ClientState,
    isHost: Boolean,
    hostInfo: HostInfo?,
    genres: List<Genre>,
    onStartGame: (GameConfig) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (isHost && hostInfo != null) HostAddressCard(hostInfo)
        PlayersCard(state)
        if (isHost) {
            HostSettings(genres, onStartGame)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Text("  Esperando a que el host empiece la partida…")
            }
        }
    }
}

@Composable
private fun HostAddressCard(hostInfo: HostInfo) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Los demás te encuentran en \"Unirme a una sala\".", style = MaterialTheme.typography.bodyMedium)
            if (hostInfo.addresses.isNotEmpty()) {
                Text(
                    "Si no aparece, que escriban: ${hostInfo.addresses.joinToString(" o ")}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                )
            } else {
                Text(
                    "No estás conectado a una red Wi-Fi. Conéctate o activa el hotspot.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayersCard(state: ClientState) {
    val players = state.players.filter { it.connected }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Jugadores (${players.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                players.forEach { player ->
                    FilterChip(
                        selected = player.id == state.myId,
                        onClick = {},
                        label = { Text(if (player.id == state.myId) "${player.name} (tú)" else player.name) },
                    )
                }
            }
        }
    }
}

private enum class SourceMode(val label: String) { Genre("Género"), Search("Buscar") }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HostSettings(genres: List<Genre>, onStartGame: (GameConfig) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(SourceMode.Genre) }
    var genreId by rememberSaveable { mutableLongStateOf(0L) }
    var query by rememberSaveable { mutableStateOf("") }
    var rounds by rememberSaveable { mutableIntStateOf(10) }
    var seconds by rememberSaveable { mutableIntStateOf(20) }

    val source: MusicSource? = when (mode) {
        SourceMode.Genre -> genres.firstOrNull { it.id == genreId }?.let { MusicSource.Chart(it.id, it.name) }
        SourceMode.Search -> query.trim().takeIf { it.length >= 2 }?.let { MusicSource.Search(it) }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Configura la partida", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SourceMode.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = mode == option,
                        onClick = { mode = option },
                        shape = SegmentedButtonDefaults.itemShape(index, SourceMode.entries.size),
                    ) { Text(option.label) }
                }
            }

            when (mode) {
                SourceMode.Genre -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    genres.forEach { genre ->
                        FilterChip(
                            selected = genre.id == genreId,
                            onClick = { genreId = genre.id },
                            label = { Text(if (genre.id == 0L) "Top global" else genre.name) },
                        )
                    }
                }
                SourceMode.Search -> OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(MAX_QUERY_LENGTH) },
                    label = { Text("Artista, playlist o estilo") },
                    placeholder = { Text("Ej: Shakira, rock en español, 80s") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            ChoiceRow("Rondas", GameConfig.ROUND_CHOICES, rounds, { "$it" }) { rounds = it }
            ChoiceRow("Tiempo por ronda", GameConfig.SECONDS_CHOICES, seconds, { "${it}s" }) { seconds = it }

            Button(
                onClick = { source?.let { onStartGame(GameConfig(it, rounds, seconds)) } },
                enabled = source != null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text("  ¡Empezar!", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(
    title: String,
    choices: List<Int>,
    selected: Int,
    label: (Int) -> String,
    onSelect: (Int) -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { value ->
                FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label(value)) })
            }
        }
    }
}

private const val MAX_QUERY_LENGTH = 60
