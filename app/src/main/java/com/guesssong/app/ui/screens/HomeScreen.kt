package com.guesssong.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guesssong.app.ui.components.Equalizer
import com.guesssong.app.ui.components.SectionLabel
import com.guesssong.app.ui.components.appTextFieldColors
import com.guesssong.app.ui.theme.Palette

@Composable
fun HomeScreen(
    nickname: String,
    onNicknameChange: (String) -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
) {
    val canPlay = nickname.isNotBlank()
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VinylMark()
                    Spacer(Modifier.width(10.dp))
                    SectionLabel("Juego de fiesta")
                }
                Spacer(Modifier.height(40.dp))
                Text(
                    buildAnnotatedString {
                        append("Adivina\nla ")
                        withStyle(SpanStyle(color = Palette.Accent)) { append("Canción") }
                    },
                    style = MaterialTheme.typography.displayLarge,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Suena un pedacito. El más rápido en adivinar se lleva más puntos.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.TextMuted,
                    modifier = Modifier.fillMaxWidth(0.85f),
                )
                Spacer(Modifier.height(28.dp))
                Equalizer(bars = 20, height = 56.dp, highlight = { it % 5 == 2 })
            }

            Column(Modifier.padding(top = 32.dp)) {
                Text("Tu nombre", style = MaterialTheme.typography.labelLarge, color = Palette.TextMuted)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = nickname,
                    onValueChange = onNicknameChange,
                    placeholder = { Text("¿Cómo te llamamos?") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    shape = RoundedCornerShape(16.dp),
                    colors = appTextFieldColors(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                ActionCard(
                    title = "Crear sala",
                    subtitle = "Tu teléfono pone la música",
                    icon = Icons.Filled.Speaker,
                    primary = true,
                    enabled = canPlay,
                    onClick = onCreateRoom,
                )
                Spacer(Modifier.height(12.dp))
                ActionCard(
                    title = "Unirme a una sala",
                    subtitle = "Entra a la sala de un amigo",
                    icon = Icons.Filled.Groups,
                    primary = false,
                    enabled = canPlay,
                    onClick = onJoinRoom,
                )
                Spacer(Modifier.height(18.dp))
                Row {
                    Icon(Icons.Filled.Wifi, contentDescription = null, tint = Palette.TextMuted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (canPlay) {
                            "Todos en la misma Wi‑Fi o en el hotspot del host. Conecta el host a un parlante para más ambiente."
                        } else {
                            "Escribe tu nombre para crear una sala o unirte a una."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun VinylMark() {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(Palette.Surface),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Palette.Accent), contentAlignment = Alignment.Center) {
            Box(Modifier.size(3.dp).clip(CircleShape).background(Palette.Background))
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val content = if (primary) Palette.OnAccent else Palette.Text
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        color = if (primary) Palette.Accent else Palette.Surface,
        contentColor = content,
        border = if (primary) null else BorderStroke(1.5.dp, Palette.Outline),
        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp).alpha(if (enabled) 1f else DISABLED_ALPHA),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (primary) Color(0x1F15130C) else Palette.SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (primary) content.copy(alpha = 0.75f) else Palette.TextMuted,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

private const val DISABLED_ALPHA = 0.4f
