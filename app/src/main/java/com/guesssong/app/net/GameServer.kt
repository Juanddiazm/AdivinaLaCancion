package com.guesssong.app.net

import android.util.Log
import com.guesssong.app.audio.AudioPlayer
import com.guesssong.app.game.AnswerMode
import com.guesssong.app.game.GameConfig
import com.guesssong.app.game.GameRules
import com.guesssong.app.game.GameState
import com.guesssong.app.game.GuessFeedback
import com.guesssong.app.game.GuessTarget
import com.guesssong.app.game.Question
import com.guesssong.app.game.QuestionFactory
import com.guesssong.app.game.RoundOutcome
import com.guesssong.app.music.HttpFetcher
import com.guesssong.app.music.MusicException
import com.guesssong.app.music.MusicRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Corre en el teléfono del host: acepta jugadores por TCP, lleva el estado de la partida
 * (fuente de verdad), reproduce la música y anuncia cada ronda.
 */
class GameServer(
    val roomName: String,
    private val music: MusicRepository,
    private val audio: AudioPlayer,
    private val previewDir: File,
    private val clock: () -> Long,
    private val download: suspend (String, File) -> File = HttpFetcher::download,
    private val random: Random = Random.Default,
    private val timing: Timing = Timing(),
    private val preferredPort: Int = GAME_PORT,
) {
    /** Pausas entre fases; configurables para los tests. */
    data class Timing(val roundIntroMs: Long = 2_500L, val revealMs: Long = 6_000L)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val state = MutableStateFlow(GameState())
    private val peers = ConcurrentHashMap<String, Peer>()
    private val isRunning = MutableStateFlow(false)
    private val pendingConnections = AtomicInteger()
    /** Cambia con cada partida: una partida cancelada que aún termina no toca la nueva. */
    private val generation = AtomicInteger()
    private var serverSocket: ServerSocket? = null
    private var gameJob: Job? = null

    /** Un jugador conectado con su cola de salida (mantiene el orden de los mensajes). */
    private class Peer(val connection: LineConnection) {
        val outbox = Channel<String>(OUTBOX_CAPACITY)
    }

    /** Abre el puerto de juego (fijo si está libre) y devuelve el puerto real. Bloqueante. */
    @Synchronized
    fun start(): Int {
        if (!scope.isActive) throw IOException("La sala ya se cerró")
        val socket = openServerSocket()
        serverSocket = socket
        scope.launch(Dispatchers.IO) { acceptLoop(socket) }
        scope.launch { pingLoop() }
        return socket.localPort
    }

    private fun openServerSocket(): ServerSocket = try {
        ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(preferredPort))
        }
    } catch (e: IOException) {
        ServerSocket(0)
    }

    @Synchronized
    fun startGame(config: GameConfig) {
        if (gameJob?.isActive == true) return
        val game = generation.incrementAndGet()
        gameJob = scope.launch { runGame(config, game) }
    }

    @Synchronized
    fun backToLobby() {
        gameJob?.cancel()
        generation.incrementAndGet()
        isRunning.value = false
        audio.stop()
        state.update { GameRules.resetForNewGame(it) }
        broadcast(ServerMessage.BackToLobby)
        broadcastPlayers()
    }

    @Synchronized
    fun close() {
        scope.cancel()
        runCatching { serverSocket?.close() }
        peers.values.forEach { it.connection.close() }
        peers.clear()
        audio.release()
        previewDir.deleteRecursively()
    }

    // region Conexiones

    private suspend fun acceptLoop(server: ServerSocket) {
        while (scope.isActive) {
            val socket = try {
                server.accept()
            } catch (e: IOException) {
                break
            }
            if (pendingConnections.get() >= MAX_PENDING_CONNECTIONS) {
                runCatching { socket.close() }
                continue
            }
            scope.launch(Dispatchers.IO) { handleClient(socket) }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        val connection = runCatching { LineConnection(socket) }.getOrElse {
            runCatching { socket.close() }
            return
        }
        var playerId: String? = null
        var peer: Peer? = null
        pendingConnections.incrementAndGet()
        // Quien no se presenta a tiempo no puede quedarse ocupando un hilo.
        val joinWatchdog = scope.launch {
            delay(JOIN_TIMEOUT_MS)
            connection.close()
        }
        try {
            val firstLine = try {
                connection.readLine()
            } finally {
                joinWatchdog.cancel()
                pendingConnections.decrementAndGet()
            }
            val join = Protocol.decodeClient(firstLine ?: return) as? ClientMessage.Join ?: return
            val rejection = rejectionFor(join)
            if (rejection != null) {
                connection.send(Protocol.encode(ServerMessage.Rejected(rejection)))
                return
            }
            val id = registerPlayer(join.name)
            playerId = id
            peer = Peer(connection).also { newPeer ->
                peers.put(id, newPeer)?.connection?.close()
                scope.launch(Dispatchers.IO) { writeLoop(newPeer) }
            }
            send(peer, ServerMessage.Welcome(id, roomName))
            if (isRunning.value) send(peer, ServerMessage.Preparing("Partida en curso: entras en la próxima ronda…"))
            broadcastPlayers()
            readLoop(id, connection)
        } catch (e: IOException) {
            Log.d(TAG, "Conexión cerrada: ${e.message}")
        } finally {
            connection.close()
            peer?.outbox?.close()
            if (playerId != null && peer != null && peers.remove(playerId, peer)) onPlayerLeft(playerId)
        }
    }

    private fun rejectionFor(join: ClientMessage.Join): String? = when {
        join.protocolVersion != PROTOCOL_VERSION ->
            "Tu versión de la app no coincide con la del host. Instalen el mismo APK."
        GameRules.connectedCount(state.value) >= MAX_PLAYERS -> "La sala está llena ($MAX_PLAYERS jugadores)."
        else -> null
    }

    private fun registerPlayer(name: String): String {
        val candidateId = UUID.randomUUID().toString().take(8)
        var assignedId = candidateId
        state.update { current ->
            val (next, id) = GameRules.addPlayer(current, candidateId, name)
            assignedId = id
            next
        }
        return assignedId
    }

    private fun readLoop(playerId: String, connection: LineConnection) {
        while (true) {
            val line = connection.readLine() ?: return
            when (val message = Protocol.decodeClient(line)) {
                is ClientMessage.Answer -> onAnswer(playerId, message)
                is ClientMessage.Guess -> onGuess(playerId, message)
                else -> Unit
            }
        }
    }

    private suspend fun writeLoop(peer: Peer) {
        for (line in peer.outbox) {
            if (!peer.connection.send(line)) {
                peer.connection.close()
                break
            }
        }
    }

    private fun onAnswer(playerId: String, message: ClientMessage.Answer) {
        state.update { GameRules.submitAnswer(it, playerId, message.round, message.optionIndex, clock()) }
        broadcastProgress()
    }

    private fun onGuess(playerId: String, message: ClientMessage.Guess) {
        var feedback: GuessFeedback? = null
        state.update { current ->
            val (next, result) = GameRules.submitGuess(current, playerId, message.round, message.text, clock())
            feedback = result
            next
        }
        val reply = feedback
            ?.let { ServerMessage.GuessResult(message.round, it.isCorrect, it.attemptsLeft) }
            ?: ServerMessage.GuessResult(
                round = message.round,
                correct = false,
                attemptsLeft = GameRules.remainingAttempts(state.value, playerId),
                accepted = false,
            )
        // Siempre se responde, para que el jugador nunca se quede en "Revisando…".
        peers[playerId]?.let { send(it, reply) }
        if (feedback != null) broadcastProgress()
    }

    private fun onPlayerLeft(playerId: String) {
        state.update {
            if (isRunning.value) GameRules.disconnectPlayer(it, playerId) else GameRules.removePlayer(it, playerId)
        }
        broadcastPlayers()
        broadcastProgress()
    }

    private suspend fun pingLoop() {
        while (scope.isActive) {
            delay(LineConnection.PING_INTERVAL_MS)
            broadcast(ServerMessage.Ping)
        }
    }

    private fun send(peer: Peer, message: ServerMessage) {
        // Si la cola se llena el jugador no está leyendo: se le desconecta.
        if (peer.outbox.trySend(Protocol.encode(message)).isFailure) peer.connection.close()
    }

    private fun broadcast(message: ServerMessage) {
        peers.values.forEach { send(it, message) }
    }

    private fun broadcastPlayers() {
        broadcast(ServerMessage.Players(playerDtos(state.value)))
    }

    private fun broadcastProgress() {
        val current = state.value
        val round = current.round ?: return
        broadcast(
            ServerMessage.AnswerProgress(
                round = round.number,
                answered = GameRules.answeredCount(current),
                total = GameRules.participantCount(current),
            ),
        )
    }

    private fun playerDtos(current: GameState): List<PlayerDto> =
        GameRules.leaderboard(current).map { PlayerDto(it.id, it.name, it.score, it.connected) }

    // endregion

    // region Partida

    private suspend fun runGame(config: GameConfig, game: Int) {
        val gameDir = File(previewDir, "game_$game")
        isRunning.value = true
        state.update { GameRules.resetForNewGame(it) }
        broadcastPlayers()
        try {
            broadcast(ServerMessage.Preparing("Buscando canciones de ${config.source.label}…"))
            val tracks = music.loadTracks(config.source)
            val questions = QuestionFactory.build(
                tracks = tracks,
                count = config.rounds + SPARE_QUESTIONS,
                random = random,
                target = config.target,
                optionCount = config.optionCount,
            )
            if (questions.size < MIN_QUESTIONS) {
                returnToLobby(notEnoughSongsMessage(config))
                return
            }
            val played = playRounds(questions, minOf(config.rounds, questions.size), config, gameDir)
            audio.stop()
            if (played == 0) {
                returnToLobby("No se pudo descargar ninguna canción. Revisa el internet del host.")
                return
            }
            broadcast(ServerMessage.GameOver(playerDtos(state.value)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: MusicException) {
            returnToLobby(e.message ?: "Error con Deezer")
        } catch (e: Exception) {
            Log.e(TAG, "La partida falló", e)
            returnToLobby("Algo falló preparando la partida. Intenta de nuevo.")
        } finally {
            gameDir.deleteRecursively()
            if (generation.get() == game) {
                audio.stop()
                state.update { it.copy(round = null) }
                isRunning.value = false
            }
        }
    }

    /** Juega hasta [total] rondas; si un preview no baja, usa una pregunta de reserva. */
    private suspend fun playRounds(
        questions: List<Question>,
        total: Int,
        config: GameConfig,
        gameDir: File,
    ): Int = coroutineScope {
        gameDir.mkdirs()
        var played = 0
        var index = 0
        var pending: Deferred<File?> = prefetch(questions[0], File(gameDir, "preview_0.mp3"))
        while (played < total && index < questions.size) {
            val question = questions[index]
            val file = pending.await()
            index++
            if (index < questions.size) pending = prefetch(questions[index], File(gameDir, "preview_$index.mp3"))
            if (file == null || !audio.prepare(file)) {
                Log.w(TAG, "Se salta ${question.answer.title}: preview no disponible")
                continue
            }
            played++
            playRound(played, total, question, config)
            file.delete()
        }
        pending.cancel()
        played
    }

    private fun CoroutineScope.prefetch(question: Question, target: File): Deferred<File?> = async(Dispatchers.IO) {
        try {
            download(question.answer.previewUrl, target)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Fallo al descargar preview", e)
            null
        }
    }

    private suspend fun playRound(number: Int, total: Int, question: Question, config: GameConfig) {
        broadcast(ServerMessage.Preparing("Ronda $number de $total"))
        delay(timing.roundIntroMs)

        state.update {
            GameRules.startRound(it, number, question, clock(), config.roundDurationMs, config.answerMode, config.target)
        }
        audio.play()
        val options = if (config.answerMode == AnswerMode.CHOICES) {
            question.options.map { OptionDto(config.target.of(it)) }
        } else {
            emptyList()
        }
        broadcast(
            ServerMessage.RoundStart(
                round = number,
                totalRounds = total,
                answerMode = config.answerMode,
                target = config.target,
                options = options,
                durationMs = config.roundDurationMs,
                maxAttempts = GameRules.MAX_GUESS_ATTEMPTS,
            ),
        )
        broadcastProgress()
        withTimeoutOrNull(config.roundDurationMs + GameRules.LATE_GRACE_MS) {
            state.first { GameRules.allAnswered(it) }
        }

        var outcome: RoundOutcome? = null
        state.update { current -> GameRules.finishRound(current).also { outcome = it.second }.first }
        val result = outcome ?: return
        broadcast(roundEndMessage(result, total))
        delay(timing.revealMs)
        audio.stop()
    }

    private fun roundEndMessage(outcome: RoundOutcome, total: Int): ServerMessage.RoundEnd {
        val answer = outcome.question.answer
        return ServerMessage.RoundEnd(
            round = outcome.roundNumber,
            totalRounds = total,
            correctIndex = outcome.question.correctIndex,
            title = answer.title,
            artist = answer.artist,
            coverUrl = answer.coverUrl,
            gains = outcome.gains,
            choices = outcome.choices,
            guesses = outcome.guesses,
            players = playerDtos(state.value),
        )
    }

    private fun notEnoughSongsMessage(config: GameConfig): String =
        if (config.target == GuessTarget.ARTIST && config.answerMode == AnswerMode.CHOICES) {
            "Para adivinar el artista con opciones hacen falta al menos 4 artistas distintos en " +
                "${config.source.label}. Prueba otra búsqueda o el modo escribir."
        } else {
            "No encontré suficientes canciones para ${config.source.label}. Prueba con otra opción."
        }

    private fun returnToLobby(notice: String) {
        state.update { GameRules.resetForNewGame(it) }
        broadcast(ServerMessage.BackToLobby)
        broadcast(ServerMessage.Notice(notice))
        broadcastPlayers()
    }

    // endregion

    private companion object {
        const val TAG = "GameServer"
        const val MAX_PLAYERS = 16
        const val MAX_PENDING_CONNECTIONS = 8
        const val JOIN_TIMEOUT_MS = 5_000L
        const val OUTBOX_CAPACITY = 64
        const val SPARE_QUESTIONS = 3
        const val MIN_QUESTIONS = 1
    }
}
