package com.guesssong.app.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientReducerTest {
    private val options = listOf(OptionDto("A", "a"), OptionDto("B", "b"), OptionDto("C", "c"), OptionDto("D", "d"))
    private val welcomed = ClientReducer.reduce(ClientState(), ServerMessage.Welcome("me", "Sala de Ana"), 0)
    private val inQuestion = ClientReducer.reduce(welcomed, ServerMessage.RoundStart(2, 5, options, 15_000), nowMs = 42)

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
        val end = ServerMessage.RoundEnd(2, 5, 1, "B", "b", "https://c", mapOf("me" to 800), mapOf("me" to 1), players)

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
        val end = ServerMessage.RoundEnd(2, 5, 1, "B", "b", null, emptyMap(), emptyMap(), emptyList())
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
}
