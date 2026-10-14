package com.guesssong.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guesssong.app.game.AnswerMode
import com.guesssong.app.game.GameConfig
import com.guesssong.app.game.GameRules
import com.guesssong.app.game.GuessTarget
import com.guesssong.app.model.Genre
import com.guesssong.app.model.MusicSource
import com.guesssong.app.net.ClientState
import com.guesssong.app.ui.HostInfo
import com.guesssong.app.ui.components.AppButton
import com.guesssong.app.ui.components.Avatar
import com.guesssong.app.ui.components.Equalizer
import com.guesssong.app.ui.components.PillSelector
import com.guesssong.app.ui.components.SectionLabel
import com.guesssong.app.ui.components.appTextFieldColors
import com.guesssong.app.ui.components.avatarColor
import com.guesssong.app.ui.theme.Palette

@Composable
fun LobbyContent(
    state: ClientState,
    isHost: Boolean,
    hostInfo: HostInfo?,
    genres: List<Genre>,
    onStartGame: (GameConfig) -> Unit,
) {
    if (isHost) HostLobby(state, hostInfo, genres, onStartGame) else GuestLobby(state)
}

private enum class SourceMode(val label: String) { Genre("Género"), Search("Buscar") }

@Composable
private fun HostLobby(state: ClientState, hostInfo: HostInfo?, genres: List<Genre>, onStartGame: (GameConfig) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(SourceMode.Genre) }
    var genreId by rememberSaveable { mutableLongStateOf(0L) }
    var query by rememberSaveable { mutableStateOf("") }
    var rounds by rememberSaveable { mutableIntStateOf(10) }
    var seconds by rememberSaveable { mutableIntStateOf(20) }
    var answerMode by rememberSaveable { mutableStateOf(AnswerMode.CHOICES) }
    var target by rememberSaveable { mutableStateOf(GuessTarget.TITLE) }
    val genre = genres.firstOrNull { it.id == genreId }
    val source: MusicSource? = when (mode) {
        SourceMode.Genre -> genre?.let { MusicSource.Chart(it.id, it.name) }
        SourceMode.Search -> query.trim().takeIf { it.length >= 2 }?.let { MusicSource.Search(it) }
    }
    val sourceLabel = when (mode) {
        SourceMode.Genre -> genre?.let(::genreLabel)
        SourceMode.Search -> query.trim().takeIf { it.length >= 2 }?.let { "“$it”" }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 18.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            hostInfo?.let { InviteCard(it) }
            PlayersStrip(state)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Palette.Surface)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingTitle("Música", Modifier.weight(1f))
                        PillSelector(
                            options = SourceMode.entries.map { it to it.label },
                            selected = mode,
                            onSelect = { mode = it },
                            fill = false,
                            height = 36.dp,
                            large = false,
                        )
                    }
                    when (mode) {
                        SourceMode.Genre -> GenreGrid(genres, genreId) { genreId = it }
                        SourceMode.Search -> OutlinedTextField(
                            value = query,
                            onValueChange = { query = it.take(MAX_QUERY_LENGTH) },
                            label = { Text("Artista, playlist o estilo") },
                            placeholder = { Text("Ej: Shakira, rock en español, 80s") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = appTextFieldColors(),
                            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingTitle("¿Qué adivinan?")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceCard("La canción", null, Icons.Filled.MusicNote, target == GuessTarget.TITLE, Modifier.weight(1f)) {
                            target = GuessTarget.TITLE
                        }
                        ChoiceCard("El artista", null, Icons.Filled.Mic, target == GuessTarget.ARTIST, Modifier.weight(1f)) {
                            target = GuessTarget.ARTIST
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingTitle("¿Cómo responden?")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceCard(
                            "Opciones",
                            "4 botones. El más rápido gana más.",
                            Icons.Filled.GridView,
                            answerMode == AnswerMode.CHOICES,
                            Modifier.weight(1f),
                        ) { answerMode = AnswerMode.CHOICES }
                        ChoiceCard(
                            "Escribiendo",
                            "${GameRules.MAX_GUESS_ATTEMPTS} intentos. Se perdonan errores.",
                            Icons.Filled.Keyboard,
                            answerMode == AnswerMode.TYPING,
                            Modifier.weight(1f),
                        ) { answerMode = AnswerMode.TYPING }
                    }
                    if (answerMode == AnswerMode.TYPING) {
                        Text(
                            "Para escribir conviene darles más tiempo: 30 s por ronda.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.TextMuted,
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingTitle("Rondas")
                    PillSelector(GameConfig.ROUND_CHOICES.map { it to "$it" }, rounds, { rounds = it }, Modifier.fillMaxWidth())
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingTitle("Tiempo por ronda")
                    PillSelector(GameConfig.SECONDS_CHOICES.map { it to "$it s" }, seconds, { seconds = it }, Modifier.fillMaxWidth())
                }
            }
        }

        HorizontalDivider(color = Palette.SurfaceHigh)
        Column(Modifier.padding(top = 12.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (sourceLabel == null) {
                    if (mode == SourceMode.Genre) "Elige un género para empezar" else "Escribe al menos 2 letras para buscar"
                } else {
                    listOf(
                        sourceLabel,
                        if (target == GuessTarget.TITLE) "Canción" else "Artista",
                        if (answerMode == AnswerMode.CHOICES) "Opciones" else "Escribiendo",
                        "$rounds rondas",
                        "$seconds s",
                    ).joinToString(" · ")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextMuted,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            AppButton(
                text = "¡Empezar!",
                icon = Icons.Filled.PlayArrow,
                enabled = source != null,
                onClick = { source?.let { onStartGame(GameConfig(it, rounds, seconds, answerMode, target)) } },
            )
        }
    }
}

@Composable
private fun InviteCard(hostInfo: HostInfo) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Palette.Surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (hostInfo.addresses.isNotEmpty()) {
            Text(
                "Tus amigos te ven en “Unirme a una sala”. Si no aparece, que escriban:",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextMuted,
            )
            hostInfo.addresses.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 26.sp),
                    color = Palette.Accent,
                )
            }
        } else {
            Text(
                "No estás conectado a una red Wi‑Fi. Conéctate o activa el hotspot para que te encuentren.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.PinkSoft,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayersStrip(state: ClientState) {
    val players = state.players.filter { it.connected }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("Jugadores · ${players.size}")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            players.forEach { player ->
                val isMe = player.id == state.myId
                Column(Modifier.width(68.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(player.name, avatarColor(player.id), 52.dp, highlighted = isMe)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (isMe) "${player.name} (tú)" else player.name,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun GenreGrid(genres: List<Genre>, selectedId: Long, onSelect: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        genres.chunked(GENRE_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { genre ->
                    val selected = genre.id == selectedId
                    Surface(
                        onClick = { onSelect(genre.id) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) Palette.Accent else Palette.SurfaceHigh,
                        contentColor = if (selected) Palette.OnAccent else Palette.Text,
                        border = if (selected) null else BorderStroke(1.5.dp, Palette.Outline),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                genreLabel(genre),
                                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                repeat(GENRE_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ChoiceCard(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Palette.SurfaceHigh else Palette.Surface,
        contentColor = if (selected) Palette.Text else Palette.TextMuted,
        border = BorderStroke(2.dp, if (selected) Palette.Accent else Palette.Outline),
        modifier = modifier.heightIn(min = 64.dp),
    ) {
        if (subtitle == null) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp))
            }
        } else {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            }
        }
    }
}

@Composable
private fun SettingTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier)
}

@Composable
private fun GuestLobby(state: ClientState) {
    val players = state.players.filter { it.connected }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 40.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Equalizer(bars = 7, height = 72.dp, barWidth = 12.dp, gap = 7.dp, highlight = { it == 3 })
        Spacer(Modifier.height(18.dp))
        Text("¡Ya estás dentro!", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            "Esperando a que el host elija la música y empiece la partida…",
            style = MaterialTheme.typography.bodyLarge,
            color = Palette.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 290.dp),
        )
        Spacer(Modifier.height(36.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.Surface).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel("Jugadores · ${players.size}")
            players.forEach { player ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(player.name, avatarColor(player.id), 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(player.name, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp), modifier = Modifier.weight(1f))
                    if (player.id == state.myId) {
                        Text("Tú", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .border(1.5.dp, Palette.Outline, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Palette.TextMuted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "La música suena en el teléfono del host. Tú solo miras tu pantalla y respondes.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextMuted,
            )
        }
    }
}

private fun genreLabel(genre: Genre) = if (genre.id == 0L) "Top global" else genre.name

private const val MAX_QUERY_LENGTH = 60
private const val GENRE_COLUMNS = 3
