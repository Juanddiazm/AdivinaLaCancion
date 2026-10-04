package com.guesssong.app.game

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoringTest {
    @Test
    fun `instant correct answer gets max points`() {
        assertEquals(Scoring.MAX_POINTS, Scoring.pointsFor(true, 0, 20_000))
    }

    @Test
    fun `correct answer at the buzzer gets min points`() {
        assertEquals(Scoring.MIN_POINTS, Scoring.pointsFor(true, 20_000, 20_000))
    }

    @Test
    fun `correct answer halfway gets proportional points`() {
        assertEquals(550, Scoring.pointsFor(true, 10_000, 20_000))
    }

    @Test
    fun `late answer within grace is clamped to min points`() {
        assertEquals(Scoring.MIN_POINTS, Scoring.pointsFor(true, 20_400, 20_000))
    }

    @Test
    fun `wrong answer gets zero`() {
        assertEquals(0, Scoring.pointsFor(false, 0, 20_000))
    }

    @Test
    fun `invalid duration gets zero`() {
        assertEquals(0, Scoring.pointsFor(true, 0, 0))
    }
}
