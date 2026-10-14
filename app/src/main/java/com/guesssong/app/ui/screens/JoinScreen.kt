package com.guesssong.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guesssong.app.net.DiscoveredRoom
import com.guesssong.app.ui.components.SectionLabel
import com.guesssong.app.ui.components.SquareIconButton
import com.guesssong.app.ui.components.avatarColor
import com.guesssong.app.ui.components.appTextFieldColors
import com.guesssong.app.ui.theme.DisplayFont
import com.guesssong.app.ui.theme.Palette

@Composable
fun JoinScreen(
    rooms: List<DiscoveredRoom>,
    onJoin: (DiscoveredRoom) -> Unit,
    onJoinManual: (String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var manualAddress by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SquareIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Volver", onBack)
            Spacer(Modifier.width(12.dp))
            Text("Unirme a una sala", style = MaterialTheme.typography.headlineSmall)
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Radar() }
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Buscando salas en tu Wi‑Fi…", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Aparecen solas cuando el host crea la sala",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextMuted,
                    )
                    Spacer(Modifier.height(18.dp))
                }
            }
            if (rooms.isEmpty()) {
                item {
                    Text(
                        "Aún no aparece ninguna sala. Verifica que el host ya la haya creado y que estén en la misma Wi‑Fi.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    )
                }
            } else {
                item { SectionLabel("Salas encontradas · ${rooms.size}") }
                items(rooms, key = { "${it.host}:${it.port}" }) { room -> RoomCard(room) { onJoin(room) } }
            }
        }

        Column(
            Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Palette.Surface)
                .padding(16.dp),
        ) {
            Text("¿No aparece? Escribe la IP que muestra el host", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = manualAddress,
                    onValueChange = { manualAddress = it.trim() },
                    placeholder = { Text("192.168.1.10", fontFamily = FontFamily.Monospace) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    shape = RoundedCornerShape(14.dp),
                    colors = appTextFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onJoinManual(manualAddress) }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onJoinManual(manualAddress) },
                    enabled = manualAddress.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Palette.Accent,
                        contentColor = Palette.OnAccent,
                        disabledContainerColor = Palette.SurfaceHigh,
                        disabledContentColor = Palette.TextFaint,
                    ),
                    modifier = Modifier.height(56.dp),
                ) {
                    Text("Entrar", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun Radar() {
    val transition = rememberInfiniteTransition(label = "radar")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PING_MS, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "ping",
    )
    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(190.dp).border(1.5.dp, Palette.SurfaceHigh, CircleShape))
        Box(Modifier.size(124.dp).border(1.5.dp, Palette.SurfaceHigh, CircleShape))
        Box(
            Modifier
                .size(190.dp)
                .graphicsLayer {
                    val scale = 0.4f + 0.6f * pulse
                    scaleX = scale
                    scaleY = scale
                    alpha = (1f - pulse) * PING_ALPHA
                }
                .clip(CircleShape)
                .background(Palette.Accent),
        )
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(Palette.Accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Wifi, contentDescription = null, tint = Palette.OnAccent, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun RoomCard(room: DiscoveredRoom, onClick: () -> Unit) {
    val owner = room.name.removePrefix("Sala de ").ifBlank { room.name }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Palette.Surface,
        contentColor = Palette.Text,
        border = BorderStroke(1.5.dp, Palette.Outline),
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(avatarColor(owner)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    owner.trim().firstOrNull()?.uppercase() ?: "?",
                    fontFamily = DisplayFont,
                    fontSize = 20.sp,
                    style = MaterialTheme.typography.titleLarge,
                    color = Palette.OnAccent,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(room.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    room.host,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = Palette.TextMuted,
                )
            }
            Box(
                Modifier.clip(CircleShape).background(Palette.Accent).padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text("Entrar", style = MaterialTheme.typography.labelLarge, color = Palette.OnAccent)
            }
        }
    }
}

private const val PING_MS = 2200
private const val PING_ALPHA = 0.25f
