package com.guesssong.app.game

import kotlin.math.roundToInt

object Scoring {
    const val MAX_POINTS = 1000
    const val MIN_POINTS = 100

    /** Respuesta correcta: entre MIN y MAX puntos según qué tan rápido se respondió. */
    fun pointsFor(isCorrect: Boolean, elapsedMs: Long, roundDurationMs: Long): Int {
        if (!isCorrect || roundDurationMs <= 0) return 0
        val clamped = elapsedMs.coerceIn(0, roundDurationMs)
        val remainingFraction = 1.0 - clamped.toDouble() / roundDurationMs
        return MIN_POINTS + ((MAX_POINTS - MIN_POINTS) * remainingFraction).roundToInt()
    }
}
