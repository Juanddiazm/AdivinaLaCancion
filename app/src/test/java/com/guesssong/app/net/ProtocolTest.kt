package com.guesssong.app.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {
    @Test
    fun `client messages round trip`() {
        val messages = listOf(ClientMessage.Join("Ana"), ClientMessage.Answer(3, 2), ClientMessage.Pong)
        messages.forEach { assertEquals(it, Protocol.decodeClient(Protocol.encode(it))) }
    }

    @Test
    fun `server messages round trip`() {
        val players = listOf(PlayerDto("a", "Ana", 100, true))
        val messages = listOf(
            ServerMessage.Welcome("a", "Sala"),
            ServerMessage.Rejected("llena"),
            ServerMessage.Players(players),
            ServerMessage.BackToLobby,
            ServerMessage.Preparing("…"),
            ServerMessage.RoundStart(1, 10, listOf(OptionDto("T", "A")), 20_000),
            ServerMessage.AnswerProgress(1, 2, 3),
            ServerMessage.RoundEnd(1, 10, 2, "T", "A", null, mapOf("a" to 900), mapOf("a" to 2), players),
            ServerMessage.GameOver(players),
            ServerMessage.Notice("hola"),
            ServerMessage.Ping,
        )
        messages.forEach { assertEquals(it, Protocol.decodeServer(Protocol.encode(it))) }
    }

    @Test
    fun `encoded message is a single line with type discriminator`() {
        val line = Protocol.encode(ClientMessage.Join("Ana\nBeto"))
        assertTrue(!line.contains('\n'))
        assertTrue(line.contains("\"type\":\"join\""))
    }

    @Test
    fun `garbage decodes to null`() {
        assertNull(Protocol.decodeClient("not json"))
        assertNull(Protocol.decodeServer("{\"type\":\"unknown\"}"))
    }

    @Test
    fun `unknown fields are ignored for forward compatibility`() {
        val decoded = Protocol.decodeClient("{\"type\":\"answer\",\"round\":1,\"optionIndex\":0,\"extra\":true}")
        assertEquals(ClientMessage.Answer(1, 0), decoded)
    }
}
