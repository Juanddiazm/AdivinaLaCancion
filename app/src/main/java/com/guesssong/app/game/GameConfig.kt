package com.guesssong.app.game

import com.guesssong.app.model.MusicSource

data class GameConfig(
    val source: MusicSource,
    val rounds: Int = 10,
    val roundSeconds: Int = 20,
) {
    val roundDurationMs: Long get() = roundSeconds * 1000L

    companion object {
        val ROUND_CHOICES = listOf(5, 10, 15, 20)
        val SECONDS_CHOICES = listOf(10, 15, 20, 30)
    }
}
