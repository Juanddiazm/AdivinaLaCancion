package com.guesssong.app.game

data class PlayerState(
    val id: String,
    val name: String,
    val score: Int = 0,
    val connected: Boolean = true,
)

/** Respuesta final de un jugador en la ronda (ya no puede volver a responder). */
data class SubmittedAnswer(
    val isCorrect: Boolean,
    val elapsedMs: Long,
    /** Solo en modo opciones. */
    val optionIndex: Int? = null,
)

data class RoundState(
    val number: Int,
    val question: Question,
    val startedAtMs: Long,
    val durationMs: Long,
    /** Jugadores conectados al empezar la ronda; quien entra después mira hasta la siguiente. */
    val participants: Set<String>,
    val answerMode: AnswerMode = AnswerMode.CHOICES,
    val target: GuessTarget = GuessTarget.TITLE,
    val answers: Map<String, SubmittedAnswer> = emptyMap(),
    /** Modo escribir: intentos usados y último texto enviado por jugador. */
    val attempts: Map<String, Int> = emptyMap(),
    val lastGuesses: Map<String, String> = emptyMap(),
)

data class GameState(
    val players: Map<String, PlayerState> = emptyMap(),
    val round: RoundState? = null,
)

data class RoundOutcome(
    val roundNumber: Int,
    val question: Question,
    /** Puntos ganados en la ronda por jugador. */
    val gains: Map<String, Int>,
    /** Opción elegida por cada jugador que respondió (modo opciones). */
    val choices: Map<String, Int>,
    /** Último texto enviado por cada jugador (modo escribir). */
    val guesses: Map<String, String>,
)

/** Lo que se le responde a un jugador tras cada intento escrito. */
data class GuessFeedback(val isCorrect: Boolean, val attemptsLeft: Int)

/** Reglas puras del juego: cada función recibe un estado y devuelve uno nuevo. */
object GameRules {
    const val MAX_NAME_LENGTH = 16
    const val DEFAULT_NAME = "Jugador"
    const val MAX_GUESS_ATTEMPTS = 3

    /** Margen para respuestas que llegan justo al final por latencia de red. */
    const val LATE_GRACE_MS = 500L

    fun sanitizeName(raw: String): String =
        raw.trim().replace(Regex("\\s+"), " ").take(MAX_NAME_LENGTH).ifBlank { DEFAULT_NAME }

    /**
     * Agrega un jugador. Si había uno desconectado con el mismo nombre, lo reconecta
     * conservando su puntaje y devuelve su id original.
     */
    fun addPlayer(state: GameState, newId: String, rawName: String): Pair<GameState, String> {
        val name = sanitizeName(rawName)
        val returning = state.players.values.firstOrNull { !it.connected && it.name == name }
        if (returning != null) {
            val updated = returning.copy(connected = true)
            return state.copy(players = state.players + (updated.id to updated)) to updated.id
        }
        val uniqueName = uniqueName(name, state.players.values.map { it.name }.toSet())
        val player = PlayerState(id = newId, name = uniqueName)
        return state.copy(players = state.players + (newId to player)) to newId
    }

    private fun uniqueName(name: String, taken: Set<String>): String {
        if (name !in taken) return name
        return generateSequence(2) { it + 1 }
            .map { "${name.take(MAX_NAME_LENGTH - 3)} $it" }
            .first { it !in taken }
    }

    fun disconnectPlayer(state: GameState, id: String): GameState {
        val player = state.players[id] ?: return state
        return state.copy(players = state.players + (id to player.copy(connected = false)))
    }

    fun removePlayer(state: GameState, id: String): GameState =
        state.copy(players = state.players - id)

    /** En el lobby se descartan los desconectados y se reinician los puntajes. */
    fun resetForNewGame(state: GameState): GameState = GameState(
        players = state.players
            .filterValues { it.connected }
            .mapValues { (_, p) -> p.copy(score = 0) },
        round = null,
    )

    fun startRound(
        state: GameState,
        number: Int,
        question: Question,
        nowMs: Long,
        durationMs: Long,
        answerMode: AnswerMode = AnswerMode.CHOICES,
        target: GuessTarget = GuessTarget.TITLE,
    ): GameState = state.copy(
        round = RoundState(
            number = number,
            question = question,
            startedAtMs = nowMs,
            durationMs = durationMs,
            participants = state.players.values.filter { it.connected }.map { it.id }.toSet(),
            answerMode = answerMode,
            target = target,
        ),
    )

    /** ¿Puede este jugador responder ahora mismo en esta ronda y modo? */
    private fun canAnswer(round: RoundState, mode: AnswerMode, playerId: String, roundNumber: Int, elapsed: Long) =
        round.answerMode == mode &&
            round.number == roundNumber &&
            playerId in round.participants &&
            playerId !in round.answers &&
            elapsed in 0..(round.durationMs + LATE_GRACE_MS)

    /** Modo opciones: registra la primera respuesta válida; cualquier otra cosa se ignora. */
    fun submitAnswer(
        state: GameState,
        playerId: String,
        roundNumber: Int,
        optionIndex: Int,
        nowMs: Long,
    ): GameState {
        val round = state.round ?: return state
        val elapsed = nowMs - round.startedAtMs
        if (!canAnswer(round, AnswerMode.CHOICES, playerId, roundNumber, elapsed)) return state
        if (optionIndex !in round.question.options.indices) return state
        val answer = SubmittedAnswer(
            isCorrect = optionIndex == round.question.correctIndex,
            elapsedMs = elapsed,
            optionIndex = optionIndex,
        )
        return state.copy(round = round.copy(answers = round.answers + (playerId to answer)))
    }

    /**
     * Modo escribir: evalúa un intento con tolerancia a errores. El jugador termina al acertar
     * o al gastar sus [MAX_GUESS_ATTEMPTS] intentos. Feedback null si el intento se ignoró.
     */
    fun submitGuess(
        state: GameState,
        playerId: String,
        roundNumber: Int,
        text: String,
        nowMs: Long,
    ): Pair<GameState, GuessFeedback?> {
        val round = state.round ?: return state to null
        val elapsed = nowMs - round.startedAtMs
        val guess = text.trim().take(AnswerMatcher.MAX_GUESS_LENGTH)
        if (guess.isEmpty() || !canAnswer(round, AnswerMode.TYPING, playerId, roundNumber, elapsed)) {
            return state to null
        }
        val attempts = (round.attempts[playerId] ?: 0) + 1
        val expected = round.target.of(round.question.answer)
        val isCorrect = when (round.target) {
            GuessTarget.TITLE -> AnswerMatcher.matches(guess, expected)
            GuessTarget.ARTIST -> AnswerMatcher.matchesArtist(guess, expected)
        }
        val finished = isCorrect || attempts >= MAX_GUESS_ATTEMPTS
        val answers = if (finished) round.answers + (playerId to SubmittedAnswer(isCorrect, elapsed)) else round.answers
        val next = round.copy(
            answers = answers,
            attempts = round.attempts + (playerId to attempts),
            lastGuesses = round.lastGuesses + (playerId to guess),
        )
        return state.copy(round = next) to GuessFeedback(isCorrect, MAX_GUESS_ATTEMPTS - attempts)
    }

    /** Intentos que le quedan al jugador en la ronda actual (0 si ya terminó). */
    fun remainingAttempts(state: GameState, playerId: String): Int {
        val round = state.round ?: return 0
        if (playerId in round.answers) return 0
        return MAX_GUESS_ATTEMPTS - (round.attempts[playerId] ?: 0)
    }

    fun connectedCount(state: GameState): Int = state.players.values.count { it.connected }

    /** Participantes de la ronda que siguen conectados. */
    fun participantCount(state: GameState): Int {
        val round = state.round ?: return 0
        return round.participants.count { state.players[it]?.connected == true }
    }

    /** Jugadores conectados que ya terminaron la ronda. */
    fun answeredCount(state: GameState): Int {
        val round = state.round ?: return 0
        return round.answers.keys.count { state.players[it]?.connected == true }
    }

    fun allAnswered(state: GameState): Boolean {
        if (state.round == null) return false
        val participants = participantCount(state)
        return participants > 0 && answeredCount(state) >= participants
    }

    /** Cierra la ronda: suma puntos y deja el estado sin ronda activa. */
    fun finishRound(state: GameState): Pair<GameState, RoundOutcome?> {
        val round = state.round ?: return state to null
        val gains = round.answers.mapValues { (_, answer) ->
            Scoring.pointsFor(answer.isCorrect, answer.elapsedMs, round.durationMs)
        }
        val players = state.players.mapValues { (id, p) -> p.copy(score = p.score + (gains[id] ?: 0)) }
        val outcome = RoundOutcome(
            roundNumber = round.number,
            question = round.question,
            gains = gains,
            choices = round.answers.mapNotNull { (id, a) -> a.optionIndex?.let { id to it } }.toMap(),
            guesses = round.lastGuesses,
        )
        return GameState(players = players, round = null) to outcome
    }

    fun leaderboard(state: GameState): List<PlayerState> =
        state.players.values.sortedWith(compareByDescending<PlayerState> { it.score }.thenBy { it.name })
}
