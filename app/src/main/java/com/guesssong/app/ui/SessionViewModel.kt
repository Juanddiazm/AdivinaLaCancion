package com.guesssong.app.ui

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.guesssong.app.audio.MediaAudioPlayer
import com.guesssong.app.game.GameConfig
import com.guesssong.app.game.GameRules
import com.guesssong.app.model.Genre
import com.guesssong.app.music.DeezerMusicRepository
import com.guesssong.app.net.ClientState
import com.guesssong.app.net.DiscoveredRoom
import com.guesssong.app.net.GAME_PORT
import com.guesssong.app.net.GameClient
import com.guesssong.app.net.GameServer
import com.guesssong.app.net.NetworkUtils
import com.guesssong.app.net.RoomAnnouncer
import com.guesssong.app.net.RoomScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Screen { Home, Join, Room }

data class HostInfo(val addresses: List<String>, val port: Int)

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("guesssong", Context.MODE_PRIVATE)
    private val clock: () -> Long = { SystemClock.elapsedRealtime() }
    private val music = DeezerMusicRepository()
    private val scanner = RoomScanner(app, clock)

    private var server: GameServer? = null
    private var announcer: RoomAnnouncer? = null
    private var hostSetupJob: Job? = null

    private val _nickname = MutableStateFlow(prefs.getString(KEY_NICKNAME, "").orEmpty())
    val nickname: StateFlow<String> = _nickname.asStateFlow()

    private val _screen = MutableStateFlow(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _hostInfo = MutableStateFlow<HostInfo?>(null)
    val hostInfo: StateFlow<HostInfo?> = _hostInfo.asStateFlow()

    private val _genres = MutableStateFlow(DeezerMusicRepository.FALLBACK_GENRES)
    val genres: StateFlow<List<Genre>> = _genres.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val client = MutableStateFlow<GameClient?>(null)
    val clientState: StateFlow<ClientState?> = client
        .flatMapLatest { it?.state ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val rooms: StateFlow<List<DiscoveredRoom>> = scanner.rooms

    val isHost: Boolean get() = server != null

    fun setNickname(value: String) {
        val trimmed = value.take(GameRules.MAX_NAME_LENGTH)
        _nickname.value = trimmed
        prefs.edit().putString(KEY_NICKNAME, trimmed).apply()
    }

    fun createRoom() {
        leave()
        val name = GameRules.sanitizeName(_nickname.value)
        val roomName = "Sala de $name"
        val newServer = GameServer(
            roomName = roomName,
            music = music,
            audio = MediaAudioPlayer(),
            previewDir = File(getApplication<Application>().cacheDir, "previews"),
            clock = clock,
        )
        server = newServer
        hostSetupJob = viewModelScope.launch {
            val port = try {
                withContext(Dispatchers.IO) { newServer.start() }
            } catch (e: Exception) {
                if (server === newServer) {
                    _error.value = "No se pudo abrir la sala: ${e.message}"
                    backToHome()
                }
                return@launch
            }
            // Si el usuario salió mientras se abría el puerto, leave() ya cerró este servidor.
            if (server !== newServer) return@launch
            announcer = RoomAnnouncer(roomName, port).also { it.start(viewModelScope) }
            _hostInfo.value = HostInfo(NetworkUtils.localIpv4Addresses(), port)
            connectClient("127.0.0.1", port, name)
            loadGenres()
        }
        _screen.value = Screen.Room
    }

    private fun loadGenres() {
        viewModelScope.launch {
            runCatching { music.genres() }
                .onSuccess { if (it.isNotEmpty()) _genres.value = it }
        }
    }

    fun openJoin() {
        scanner.start(viewModelScope)
        _screen.value = Screen.Join
    }

    /** Acepta "192.168.1.5" o "192.168.1.5:47778". */
    fun joinManual(address: String) {
        val trimmed = address.trim()
        val host = trimmed.substringBefore(":")
        val port = trimmed.substringAfter(":", "").toIntOrNull() ?: GAME_PORT
        if (host.isBlank()) return
        join(host, port)
    }

    fun join(host: String, port: Int) {
        scanner.stop()
        connectClient(host, port, GameRules.sanitizeName(_nickname.value))
        _screen.value = Screen.Room
    }

    private fun connectClient(host: String, port: Int, name: String) {
        client.value?.close()
        client.value = GameClient(clock).also { it.connect(host, port, name) }
    }

    fun startGame(config: GameConfig) {
        server?.startGame(config)
    }

    fun backToLobby() {
        server?.backToLobby()
    }

    fun answer(optionIndex: Int) {
        client.value?.answer(optionIndex)
    }

    fun guess(text: String) {
        client.value?.guess(text)
    }

    fun dismissNotice() {
        client.value?.dismissNotice()
    }

    fun dismissError() {
        _error.value = null
    }

    fun backToHome() {
        leave()
        _screen.value = Screen.Home
    }

    private fun leave() {
        hostSetupJob?.cancel()
        hostSetupJob = null
        scanner.stop()
        announcer?.stop()
        announcer = null
        client.value?.close()
        client.value = null
        server?.close()
        server = null
        _hostInfo.value = null
    }

    override fun onCleared() {
        leave()
    }

    private companion object {
        const val KEY_NICKNAME = "nickname"
    }
}
