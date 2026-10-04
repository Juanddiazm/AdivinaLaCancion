package com.guesssong.app.game

import com.guesssong.app.model.MusicSource
import com.guesssong.app.model.Track
import kotlinx.serialization.Serializable

/** Cómo responden los jugadores. */
@Serializable
enum class AnswerMode { CHOICES, TYPING }

/** Qué tienen que adivinar. */
@Serializable
enum class GuessTarget {
    TITLE,
    ARTIST,
    ;

    fun of(track: Track): String = when (this) {
        TITLE -> track.title
        ARTIST -> track.artist
    }
}

data class GameConfig(
    val source: MusicSource,
    val rounds: Int = 10,
    val roundSeconds: Int = 20,
    val answerMode: AnswerMode = AnswerMode.CHOICES,
    val target: GuessTarget = GuessTarget.TITLE,
) {
    val roundDurationMs: Long get() = roundSeconds * 1000L

    /** Opciones por pregunta: escribiendo no hay opciones, solo la respuesta. */
    val optionCount: Int
        get() = if (answerMode == AnswerMode.CHOICES) QuestionFactory.OPTIONS_PER_QUESTION else 1

    companion object {
        val ROUND_CHOICES = listOf(5, 10, 15, 20)
        val SECONDS_CHOICES = listOf(10, 15, 20, 30)
    }
}
