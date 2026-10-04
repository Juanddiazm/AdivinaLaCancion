package com.guesssong.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuestionFactoryTest {
    @Test
    fun `builds requested number of questions with four distinct options`() {
        val questions = QuestionFactory.build(tracks(20), count = 10, random = Random(1))

        assertEquals(10, questions.size)
        questions.forEach { q ->
            assertEquals(QuestionFactory.OPTIONS_PER_QUESTION, q.options.size)
            assertEquals(q.options.size, q.options.map { it.id }.toSet().size)
            assertEquals(q.answer, q.options[q.correctIndex])
        }
    }

    @Test
    fun `answers never repeat`() {
        val questions = QuestionFactory.build(tracks(30), count = 30, random = Random(7))
        assertEquals(30, questions.map { it.answer.id }.toSet().size)
    }

    @Test
    fun `caps question count to available tracks`() {
        assertEquals(6, QuestionFactory.build(tracks(6), count = 10, random = Random(2)).size)
    }

    @Test
    fun `returns empty when not enough tracks for options`() {
        assertTrue(QuestionFactory.build(tracks(3), count = 5).isEmpty())
    }

    @Test
    fun `ignores tracks without preview and duplicate versions`() {
        val pool = listOf(
            track(1, title = "Dai Dai"),
            track(2, title = "Dai Dai (Remix)"),
            track(3, title = "Dai Dai - Live"),
            track(4, title = "Otra"),
            track(5, title = "Sin preview", preview = ""),
            track(6, title = "Tercera"),
        )
        // Quedan 3 únicas con preview: no alcanza para 4 opciones.
        assertTrue(QuestionFactory.build(pool, count = 3).isEmpty())
    }

    @Test
    fun `normalizes titles`() {
        assertEquals("despacito", QuestionFactory.normalizeTitle("Despacito (feat. Justin Bieber) - Remix"))
        assertEquals("hello", QuestionFactory.normalizeTitle(" Hello [Live] "))
    }

    @Test
    fun `zero count returns empty`() {
        assertTrue(QuestionFactory.build(tracks(10), count = 0).isEmpty())
    }
}
