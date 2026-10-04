package com.guesssong.app.net

sealed interface ConnectionStatus {
    data object Connecting : ConnectionStatus
    data object Connected : ConnectionStatus
    data class Closed(val reason: String) : ConnectionStatus
}

sealed interface ClientPhase {
    data object Lobby : ClientPhase

    data class Preparing(val message: String) : ClientPhase

    data class Question(
        val round: Int,
        val totalRounds: Int,
        val options: List<OptionDto>,
        val durationMs: Long,
        /** Momento local (reloj monotónico) en que llegó la pregunta. */
        val receivedAtMs: Long,
        val selectedIndex: Int? = null,
        val answered: Int = 0,
        val totalPlayers: Int = 0,
    ) : ClientPhase

    data class Reveal(
        val round: Int,
        val totalRounds: Int,
        val options: List<OptionDto>,
        val correctIndex: Int,
        val selectedIndex: Int?,
        val title: String,
        val artist: String,
        val coverUrl: String?,
        val myGain: Int,
    ) : ClientPhase

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
                options = message.options,
                durationMs = message.durationMs,
                receivedAtMs = nowMs,
            ),
            notice = null,
        )
        is ServerMessage.AnswerProgress -> {
            val phase = state.phase
            if (phase is ClientPhase.Question && phase.round == message.round) {
                state.copy(phase = phase.copy(answered = message.answered, totalPlayers = message.total))
            } else {
                state
            }
        }
        is ServerMessage.RoundEnd -> reduceRoundEnd(state, message)
        is ServerMessage.GameOver -> state.copy(players = message.players, phase = ClientPhase.Finished)
        is ServerMessage.Notice -> state.copy(notice = message.message)
        ServerMessage.Ping -> state
    }

    private fun reduceRoundEnd(state: ClientState, message: ServerMessage.RoundEnd): ClientState {
        val question = state.phase as? ClientPhase.Question
        val myId = state.myId
        return state.copy(
            players = message.players,
            phase = ClientPhase.Reveal(
                round = message.round,
                totalRounds = message.totalRounds,
                options = question?.options.orEmpty(),
                correctIndex = message.correctIndex,
                selectedIndex = myId?.let { message.choices[it] },
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
        if (phase.selectedIndex != null || optionIndex !in phase.options.indices) return null
        return state.copy(phase = phase.copy(selectedIndex = optionIndex))
    }
}
