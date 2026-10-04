package com.guesssong.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guesssong.app.ui.screens.HomeScreen
import com.guesssong.app.ui.screens.JoinScreen
import com.guesssong.app.ui.screens.RoomScreen

@Composable
fun AppRoot(viewModel: SessionViewModel) {
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val nickname by viewModel.nickname.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
                Screen.Home -> HomeScreen(
                    nickname = nickname,
                    onNicknameChange = viewModel::setNickname,
                    onCreateRoom = viewModel::createRoom,
                    onJoinRoom = viewModel::openJoin,
                )
                Screen.Join -> {
                    val rooms by viewModel.rooms.collectAsStateWithLifecycle()
                    JoinScreen(
                        rooms = rooms,
                        onJoin = { viewModel.join(it.host, it.port) },
                        onJoinManual = viewModel::joinManual,
                        onBack = viewModel::backToHome,
                    )
                }
                Screen.Room -> {
                    val state by viewModel.clientState.collectAsStateWithLifecycle()
                    val hostInfo by viewModel.hostInfo.collectAsStateWithLifecycle()
                    val genres by viewModel.genres.collectAsStateWithLifecycle()
                    RoomScreen(
                        state = state,
                        isHost = viewModel.isHost,
                        hostInfo = hostInfo,
                        genres = genres,
                        onStartGame = viewModel::startGame,
                        onBackToLobby = viewModel::backToLobby,
                        onAnswer = viewModel::answer,
                        onDismissNotice = viewModel::dismissNotice,
                        onLeave = viewModel::backToHome,
                    )
                }
            }
        }
    }

    error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("OK") } },
            text = { Text(message) },
        )
    }
}
