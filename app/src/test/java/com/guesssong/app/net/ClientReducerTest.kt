package com.guesssong.app.net

import com.guesssong.app.game.AnswerMode
import com.guesssong.app.game.GuessTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientReducerTest {
    private val options = listOf(OptionDto("A"), OptionDto("B"), OptionDto("C"), OptionDto("D"))
    private val welcomed = ClientReducer.reduce(ClientState(), ServerMessage.Welcome("me", "Sala de Ana"), 0)
    private val inQuestion = ClientReducer.reduce(
        welcomed,
        ServerMessage.RoundStart(2, 5, AnswerMode.CHOICES, GuessTarget.TITLE, options, 15_000, maxAttempts = 3),
        nowMs = 42,
    )
    private val inTyping = ClientReducer.reduce(
        welcomed,
        ServerMessage.RoundStart(2, 5, AnswerMode.TYPING, GuessTarget.ARTIST, emptyList(), 30_000, maxAttempts = 3),
        nowMs = 42,
    )

    @Test
    fun `welcome connects and stores identity`() {
        assertEquals(ConnectionStatus.Connected, welcomed.connection)
        assertEquals("me", welcomed.myId)
        assertEquals("Sala de Ana", welcomed.roomName)
    }

    @Test
    fun `rejected closes with reason`() {
        val state = ClientReducer.reduce(ClientState(), ServerMessage.Rejected("llena"), 0)
        assertEquals(ConnectionStatus.Closed("llena"), state.connection)
    }

    @Test
    fun `round start enters question with local receive time`() {
        val phase = inQuestion.phase as ClientPhase.Question
        assertEquals(2, phase.round)
        assertEquals(42, phase.receivedAtMs)
        assertNull(phase.selectedIndex)
    }

    @Test
    fun `select marks answer once`() {
        val selected = ClientReducer.select(inQuestion, 3)!!
        assertEquals(3, (selected.phase as ClientPhase.Question).selectedIndex)
        assertNull(ClientReducer.select(selected, 1))
        assertNull(ClientReducer.select(inQuestion, 7))
        assertNull(ClientReducer.select(welcomed, 0))
    }

    @Test
    fun `progress only applies to current round`() {
        val updated = ClientReducer.reduce(inQuestion, ServerMessage.AnswerProgress(2, 1, 4), 0)
        assertEquals(1, (updated.phase as ClientPhase.Question).answered)
        val ignored = ClientReducer.reduce(inQuestion, ServerMessage.AnswerProgress(1, 3, 4), 0)
        assertEquals(inQuestion, ignored)
    }

    @Test
    fun `round end reveals my result`() {
        val selected = ClientReducer.select(inQuestion, 1)!!
        val players = listOf(PlayerDto("me", "Ana", 800, true))
        val end = ServerMessage.RoundEnd(2, 5, 1, "B", "b", "https://c", mapOf("me" to 800), mapOf("me" to 1), players = players)

        val state = ClientReducer.reduce(selected, end, 0)

        val reveal = state.phase as ClientPhase.Reveal
        assertEquals(800, reveal.myGain)
        assertEquals(1, reveal.selectedIndex)
        assertEquals(1, reveal.correctIndex)
        assertEquals(options, reveal.options)
        assertEquals(players, state.players)
    }

    @Test
    fun `round end without my answer shows no selection`() {
        val end = ServerMessage.RoundEnd(2, 5, 1, "B", "b", null, emptyMap(), emptyMap(), players = emptyList())
        val reveal = ClientReducer.reduce(inQuestion, end, 0).phase as ClientPhase.Reveal
        assertNull(reveal.selectedIndex)
        assertEquals(0, reveal.myGain)
    }

    @Test
    fun `game over and back to lobby`() {
        val over = ClientReducer.reduce(inQuestion, ServerMessage.GameOver(emptyList()), 0)
        assertTrue(over.phase is ClientPhase.Finished)
        val lobby = ClientReducer.reduce(over.copy(notice = "x"), ServerMessage.BackToLobby, 0)
        assertEquals(ClientPhase.Lobby, lobby.phase)
        assertNull(lobby.notice)
    }

    @Test
    fun `ranking sorts by score`() {
        val state = welcomed.copy(
            players = listOf(PlayerDto("a", "Ana", 10, true), PlayerDto("b", "Beto", 90, true)),
        )
        assertEquals(listOf("Beto", "Ana"), state.ranking.map { it.name })
    }

    @Test
    fun `typing round starts with full attempts and no options`() {
        val phase = inTyping.phase as ClientPhase.Question
        assertEquals(AnswerMode.TYPING, phase.answerMode)
        assertEquals(GuessTarget.ARTIST, phase.target)
        assertEquals(3, phase.attemptsLeft)
        assertNull(ClientReducer.select(inTyping, 0))
        assertNull(ClientReducer.guess(inQuestion, "algo"))
    }

    @Test
    fun `guess waits for the host and reacts to each result`() {
        val checking = ClientReducer.guess(inTyping, "  shakiraa ")!!
        val phase = checking.phase as ClientPhase.Question
        assertEquals(GuessStatus.CHECKING, phase.guessStatus)
        assertEquals("shakiraa", phase.lastGuess)
        assertNull(ClientReducer.guess(checking, "otra"))

        val wrong = ClientReducer.reduce(checking, ServerMessage.GuessResult(2, correct = false, attemptsLeft = 2), 0)
        assertEquals(GuessStatus.WRONG, (wrong.phase as ClientPhase.Question).guessStatus)
        assertFalse((wrong.phase as ClientPhase.Question).isDone)

        val out = ClientReducer.reduce(wrong, ServerMessage.GuessResult(2, correct = false, attemptsLeft = 0), 0)
        assertEquals(GuessStatus.OUT_OF_ATTEMPTS, (out.phase as ClientPhase.Question).guessStatus)
        assertNull(ClientReducer.guess(out, "otra"))

        val right = ClientReducer.reduce(checking, ServerMessage.GuessResult(2, correct = true, attemptsLeft = 2), 0)
        assertTrue((right.phase as ClientPhase.Question).isDone)
    }

    @Test
    fun `blank guess is not sent`() {
        assertNull(ClientReducer.guess(inTyping, "   "))
    }

    @Test
    fun `typing reveal shows what I wrote`() {
        val end = ServerMessage.RoundEnd(
            2, 5, 0, "She Wolf", "Shakira", null,
            gains = mapOf("me" to 700), choices = emptyMap(), guesses = mapOf("me" to "shakiraa"), players = emptyList(),
        )
        val reveal = ClientReducer.reduce(inTyping, end, 0).phase as ClientPhase.Reveal
        assertEquals("shakiraa", reveal.myGuess)
        assertTrue(reveal.didAnswer)
        assertEquals(AnswerMode.TYPING, reveal.answerMode)
    }

    @Test
    fun `ignored guess returns to idle instead of hanging in checking`() {
        val checking = ClientReducer.guess(inTyping, "algo")!!
        val ignored = ClientReducer.reduce(
            checking, ServerMessage.GuessResult(2, correct = false, attemptsLeft = 3, accepted = false), 0,
        )
        val phase = ignored.phase as ClientPhase.Question
        assertEquals(GuessStatus.IDLE, phase.guessStatus)
        assertEquals(3, phase.attemptsLeft)
    }
}
