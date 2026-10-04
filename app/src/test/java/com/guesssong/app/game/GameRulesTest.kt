package com.guesssong.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GameRulesTest {
    private val question = Question(answer = track(1), options = listOf(track(2), track(1), track(3), track(4)), correctIndex = 1)

    private fun lobbyWith(vararg names: String): GameState =
        names.foldIndexed(GameState()) { i, s, name -> GameRules.addPlayer(s, "p$i", name).first }

    private fun inRound(state: GameState, startedAt: Long = 1_000, duration: Long = 20_000) =
        GameRules.startRound(state, number = 1, question = question, nowMs = startedAt, durationMs = duration)

    @Test
    fun `adds players and deduplicates names`() {
        val state = lobbyWith("Ana", "Ana", "Ana")
        assertEquals(listOf("Ana", "Ana 2", "Ana 3"), state.players.values.map { it.name })
    }

    @Test
    fun `sanitizes names`() {
        assertEquals("Jugador", GameRules.sanitizeName("   "))
        assertEquals("Juan David", GameRules.sanitizeName("  Juan   David "))
        assertEquals(GameRules.MAX_NAME_LENGTH, GameRules.sanitizeName("x".repeat(40)).length)
    }

    @Test
    fun `returning player keeps id and score`() {
        val withScore = lobbyWith("Ana").let { s ->
            s.copy(players = s.players.mapValues { it.value.copy(score = 700) })
        }
        val disconnected = GameRules.disconnectPlayer(withScore, "p0")
        val (state, id) = GameRules.addPlayer(disconnected, "new-id", "Ana")

        assertEquals("p0", id)
        assertEquals(700, state.players.getValue("p0").score)
        assertTrue(state.players.getValue("p0").connected)
        assertEquals(1, state.players.size)
    }

    @Test
    fun `scores correct fast answer and ignores wrong one`() {
        var state = inRound(lobbyWith("Ana", "Beto"))
        state = GameRules.submitAnswer(state, "p0", roundNumber = 1, optionIndex = 1, nowMs = 1_000)
        state = GameRules.submitAnswer(state, "p1", roundNumber = 1, optionIndex = 0, nowMs = 2_000)

        val (after, outcome) = GameRules.finishRound(state)

        assertEquals(Scoring.MAX_POINTS, after.players.getValue("p0").score)
        assertEquals(0, after.players.getValue("p1").score)
        assertEquals(mapOf("p0" to 1, "p1" to 0), outcome!!.choices)
        assertNull(after.round)
    }

    @Test
    fun `only the first answer counts`() {
        var state = inRound(lobbyWith("Ana"))
        state = GameRules.submitAnswer(state, "p0", 1, optionIndex = 0, nowMs = 1_500)
        val second = GameRules.submitAnswer(state, "p0", 1, optionIndex = 1, nowMs = 1_600)
        assertSame(state, second)
    }

    @Test
    fun `rejects answers for wrong round, unknown player, bad index or too late`() {
        val state = inRound(lobbyWith("Ana"))
        assertSame(state, GameRules.submitAnswer(state, "p0", roundNumber = 2, optionIndex = 1, nowMs = 1_500))
        assertSame(state, GameRules.submitAnswer(state, "ghost", 1, 1, 1_500))
        assertSame(state, GameRules.submitAnswer(state, "p0", 1, optionIndex = 9, nowMs = 1_500))
        assertSame(state, GameRules.submitAnswer(state, "p0", 1, 1, nowMs = 1_000 + 20_000 + GameRules.LATE_GRACE_MS + 1))
        assertSame(state, GameRules.submitAnswer(state, "p0", 1, 1, nowMs = 999))
    }

    @Test
    fun `answer without active round is ignored`() {
        val state = lobbyWith("Ana")
        assertSame(state, GameRules.submitAnswer(state, "p0", 1, 1, 1_000))
    }

    @Test
    fun `all answered only counts connected players`() {
        var state = inRound(lobbyWith("Ana", "Beto"))
        state = GameRules.submitAnswer(state, "p0", 1, 1, 2_000)
        assertFalse(GameRules.allAnswered(state))

        state = GameRules.disconnectPlayer(state, "p1")
        assertTrue(GameRules.allAnswered(state))
        assertEquals(1, GameRules.answeredCount(state))
        assertEquals(1, GameRules.connectedCount(state))
    }

    @Test
    fun `all answered is false with no round or no players`() {
        assertFalse(GameRules.allAnswered(lobbyWith("Ana")))
        assertFalse(GameRules.allAnswered(inRound(GameState())))
    }

    @Test
    fun `reset for new game drops disconnected and zeroes scores`() {
        val state = lobbyWith("Ana", "Beto").let { s ->
            GameRules.disconnectPlayer(s.copy(players = s.players.mapValues { it.value.copy(score = 300) }), "p1")
        }
        val reset = GameRules.resetForNewGame(state)
        assertEquals(listOf("p0"), reset.players.keys.toList())
        assertEquals(0, reset.players.getValue("p0").score)
    }

    @Test
    fun `finish round without round returns null outcome`() {
        val state = lobbyWith("Ana")
        assertNull(GameRules.finishRound(state).second)
    }

    @Test
    fun `leaderboard sorts by score then name`() {
        val state = lobbyWith("Carla", "Ana", "Beto").let { s ->
            s.copy(players = s.players.mapValues { (id, p) -> p.copy(score = if (id == "p0") 100 else 500) })
        }
        assertEquals(listOf("Ana", "Beto", "Carla"), GameRules.leaderboard(state).map { it.name })
    }

    @Test
    fun `remove player drops them`() {
        assertTrue(GameRules.removePlayer(lobbyWith("Ana"), "p0").players.isEmpty())
    }

    @Test
    fun `players joining mid round are spectators until next round`() {
        var state = inRound(lobbyWith("Ana"))
        state = GameRules.addPlayer(state, "late", "Tardío").first
        state = GameRules.submitAnswer(state, "p0", 1, 1, 2_000)

        assertTrue(GameRules.allAnswered(state))
        assertEquals(1, GameRules.participantCount(state))
        assertSame(state, GameRules.submitAnswer(state, "late", 1, 1, 2_100))
    }
}
