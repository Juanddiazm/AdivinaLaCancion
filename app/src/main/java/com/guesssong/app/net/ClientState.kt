package com.guesssong.app.net

import com.guesssong.app.game.AnswerMode
import com.guesssong.app.game.GuessTarget

sealed interface ConnectionStatus {
    data object Connecting : ConnectionStatus
    data object Connected : ConnectionStatus
    data class Closed(val reason: String) : ConnectionStatus
}

/** Estado del jugador en modo escribir. */
enum class GuessStatus { IDLE, CHECKING, WRONG, CORRECT, OUT_OF_ATTEMPTS }

sealed interface ClientPhase {
    data object Lobby : ClientPhase

    data class Preparing(val message: String) : ClientPhase

    data class Question(
        val round: Int,
        val totalRounds: Int,
        val answerMode: AnswerMode,
        val target: GuessTarget,
        val options: List<OptionDto>,
        val durationMs: Long,
        /** Momento local (reloj monotónico) en que llegó la pregunta. */
        val receivedAtMs: Long,
        val selectedIndex: Int? = null,
        val answered: Int = 0,
        val totalPlayers: Int = 0,
        val attemptsLeft: Int = 0,
        val guessStatus: GuessStatus = GuessStatus.IDLE,
        val lastGuess: String? = null,
    ) : ClientPhase {
        /** Ya no puede responder más en esta ronda. */
        val isDone: Boolean
            get() = selectedIndex != null ||
                guessStatus == GuessStatus.CORRECT ||
                guessStatus == GuessStatus.OUT_OF_ATTEMPTS
    }

    data class Reveal(
        val round: Int,
        val totalRounds: Int,
        val answerMode: AnswerMode,
        val options: List<OptionDto>,
        val correctIndex: Int,
        val selectedIndex: Int?,
        val myGuess: String?,
        val title: String,
        val artist: String,
        val coverUrl: String?,
        val myGain: Int,
    ) : ClientPhase {
        val didAnswer: Boolean get() = selectedIndex != null || myGuess != null
    }

    data object Finished : ClientPhase
}

data class ClientState(
    val connection: ConnectionStatus = ConnectionStatus.Connecting,
    val myId: String? = null,
    val roomName: String = "",
    val players: List<PlayerDto> = emptyList(),
    val phase: ClientPhase = ClientPhase.Lobby,
    val notice: String? = null,
) {
    val ranking: List<PlayerDto>
        get() = players.sortedWith(compareByDescending<PlayerDto> { it.score }.thenBy { it.name })
}

/** Aplica los mensajes del host al estado que muestra la UI del jugador. */
object ClientReducer {

    fun reduce(state: ClientState, message: ServerMessage, nowMs: Long): ClientState = when (message) {
        is ServerMessage.Welcome -> state.copy(
            connection = ConnectionStatus.Connected,
            myId = message.playerId,
            roomName = message.roomName,
        )
        is ServerMessage.Rejected -> state.copy(connection = ConnectionStatus.Closed(message.reason))
        is ServerMessage.Players -> state.copy(players = message.players)
        ServerMessage.BackToLobby -> state.copy(phase = ClientPhase.Lobby, notice = null)
        is ServerMessage.Preparing -> state.copy(phase = ClientPhase.Preparing(message.message))
        is ServerMessage.RoundStart -> state.copy(
            phase = ClientPhase.Question(
                round = message.round,
                totalRounds = message.totalRounds,
                answerMode = message.answerMode,
                target = message.target,
                options = message.options,
                durationMs = message.durationMs,
                receivedAtMs = nowMs,
                attemptsLeft = message.maxAttempts,
            ),
            notice = null,
        )
        is ServerMessage.AnswerProgress -> updateQuestion(state, message.round) {
            it.copy(answered = message.answered, totalPlayers = message.total)
        }
        is ServerMessage.GuessResult -> updateQuestion(state, message.round) {
            val status = when {
                !message.accepted && message.attemptsLeft > 0 -> GuessStatus.IDLE
                message.correct -> GuessStatus.CORRECT
                message.attemptsLeft <= 0 -> GuessStatus.OUT_OF_ATTEMPTS
                else -> GuessStatus.WRONG
            }
            it.copy(guessStatus = status, attemptsLeft = message.attemptsLeft)
        }
        is ServerMessage.RoundEnd -> reduceRoundEnd(state, message)
        is ServerMessage.GameOver -> state.copy(players = message.players, phase = ClientPhase.Finished)
        is ServerMessage.Notice -> state.copy(notice = message.message)
        ServerMessage.Ping -> state
    }

    private fun updateQuestion(
        state: ClientState,
        round: Int,
        transform: (ClientPhase.Question) -> ClientPhase.Question,
    ): ClientState {
        val phase = state.phase as? ClientPhase.Question ?: return state
        return if (phase.round == round) state.copy(phase = transform(phase)) else state
    }

    private fun reduceRoundEnd(state: ClientState, message: ServerMessage.RoundEnd): ClientState {
        val question = state.phase as? ClientPhase.Question
        val myId = state.myId
        return state.copy(
            players = message.players,
            phase = ClientPhase.Reveal(
                round = message.round,
                totalRounds = message.totalRounds,
                answerMode = question?.answerMode ?: AnswerMode.CHOICES,
                options = question?.options.orEmpty(),
                correctIndex = message.correctIndex,
                selectedIndex = myId?.let { message.choices[it] },
                myGuess = myId?.let { message.guesses[it] },
                title = message.title,
                artist = message.artist,
                coverUrl = message.coverUrl,
                myGain = myId?.let { message.gains[it] } ?: 0,
            ),
        )
    }

    /** Marca la opción elegida; null si ya no se puede responder. */
    fun select(state: ClientState, optionIndex: Int): ClientState? {
        val phase = state.phase as? ClientPhase.Question ?: return null
        if (phase.answerMode != AnswerMode.CHOICES || phase.isDone || optionIndex !in phase.options.indices) return null
        return state.copy(phase = phase.copy(selectedIndex = optionIndex))
    }

    /** Registra un intento escrito mientras el host lo revisa; null si no se puede enviar. */
    fun guess(state: ClientState, text: String): ClientState? {
        val phase = state.phase as? ClientPhase.Question ?: return null
        val canGuess = phase.answerMode == AnswerMode.TYPING &&
            !phase.isDone &&
            phase.guessStatus != GuessStatus.CHECKING &&
            text.isNotBlank()
        if (!canGuess) return null
        return state.copy(phase = phase.copy(guessStatus = GuessStatus.CHECKING, lastGuess = text.trim()))
    }
}
