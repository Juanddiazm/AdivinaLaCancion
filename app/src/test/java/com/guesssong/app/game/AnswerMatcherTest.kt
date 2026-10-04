package com.guesssong.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerMatcherTest {
    private fun accepts(guess: String, answer: String) =
        assertTrue("\"$guess\" debería valer por \"$answer\"", AnswerMatcher.matches(guess, answer))

    private fun rejects(guess: String, answer: String) =
        assertFalse("\"$guess\" NO debería valer por \"$answer\"", AnswerMatcher.matches(guess, answer))

    @Test
    fun `accepts the typo example from the user`() {
        accepts("el bale de llo pobreee", "El Baile de los Pobres")
    }

    @Test
    fun `accepts exact answers regardless of case, accents and punctuation`() {
        accepts("cancion de amor", "Canción de Amor")
        accepts("DESPACITO!!", "Despacito")
        accepts("dont stop me now", "Don't Stop Me Now")
    }

    @Test
    fun `ignores featured artists and version suffixes in the answer`() {
        accepts("despacito", "Despacito (feat. Justin Bieber) - Remix")
        accepts("under pressure", "Under Pressure (feat. David Bowie)")
    }

    @Test
    fun `ignores leading articles on either side`() {
        accepts("beatles", "The Beatles")
        accepts("the beatles", "Beatles")
        accepts("baile de los pobres", "El Baile de los Pobres")
    }

    @Test
    fun `accepts small typos in artist names`() {
        accepts("shakiraa", "Shakira")
        accepts("bad buny", "Bad Bunny")
        accepts("enanitos berdes", "Los Enanitos Verdes")
    }

    @Test
    fun `rejects different songs`() {
        rejects("waka waka", "She Wolf")
        rejects("el baile de los ricos", "El Baile de los Pobres")
        rejects("toxic", "Toxicity")
    }

    @Test
    fun `short answers need to be nearly exact`() {
        accepts("agua", "AGUA")
        rejects("awa", "AGUA")
        rejects("abc", "Sol")
    }

    @Test
    fun `blank guesses never match`() {
        rejects("", "Despacito")
        rejects("   !!  ", "Despacito")
    }

    @Test
    fun `normalize collapses repeated letters and spacing`() {
        assertEquals("bale de lo pobre", AnswerMatcher.normalize("  El   baaale de llo pobreee "))
    }

    @Test
    fun `levenshtein distance`() {
        assertEquals(0, AnswerMatcher.levenshtein("abc", "abc"))
        assertEquals(3, AnswerMatcher.levenshtein("", "abc"))
        assertEquals(3, AnswerMatcher.levenshtein("kitten", "sitting"))
    }

    @Test
    fun `titles made only of parentheses can still be guessed`() {
        accepts("intro", "(Intro)")
        accepts("interlude", "[Interlude]")
    }

    @Test
    fun `numbers are not collapsed`() {
        accepts("1999", "1999")
        rejects("19", "1999")
        rejects("2", "22")
    }

    @Test
    fun `non latin scripts keep their marks`() {
        accepts("さくら", "さくら")
        rejects("か", "が")
    }

    @Test
    fun `artist guesses accept any artist of a collaboration`() {
        assertTrue(AnswerMatcher.matchesArtist("rihanna", "Calvin Harris & Rihanna"))
        assertTrue(AnswerMatcher.matchesArtist("calvin haris", "Calvin Harris & Rihanna"))
        assertTrue(AnswerMatcher.matchesArtist("drake", "Drake feat. Rihanna"))
        assertTrue(AnswerMatcher.matchesArtist("calvin harris y rihanna", "Calvin Harris & Rihanna"))
        assertFalse(AnswerMatcher.matchesArtist("beyonce", "Calvin Harris & Rihanna"))
    }
}
