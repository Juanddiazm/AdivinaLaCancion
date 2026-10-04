package com.guesssong.app.game

import java.text.Normalizer

/** Decide si lo que escribió un jugador "vale" aunque tenga errores de ortografía. */
object AnswerMatcher {
    const val MAX_GUESS_LENGTH = 80

    /** Fracción de caracteres que se perdonan en respuestas largas. */
    private const val TOLERANCE = 0.25

    /** Tildes de letras latinas; las marcas de otros alfabetos (p. ej. kana) cambian la letra. */
    private val LATIN_DIACRITICS = Regex("(?<=\\p{IsLatin})\\p{Mn}+")
    private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")
    private val REPEATED_LETTER = Regex("(\\p{L})\\1+")
    private val ARTIST_SEPARATORS = Regex(
        "\\s*(?:,|&|\\+|\\bfeat\\b\\.?|\\bft\\b\\.?|\\bx\\b|\\by\\b|\\band\\b)\\s*",
        RegexOption.IGNORE_CASE,
    )
    private val LEADING_ARTICLES = setOf("el", "la", "los", "las", "lo", "un", "una", "the", "a")

    fun matches(guess: String, answer: String): Boolean {
        val expected = normalize(answer)
        val actual = normalize(guess.take(MAX_GUESS_LENGTH))
        if (actual.isEmpty() || expected.isEmpty()) return false
        return levenshtein(actual, expected) <= allowedEdits(expected.length)
    }

    /** Para colaboraciones ("Calvin Harris & Rihanna") vale cualquiera de los artistas. */
    fun matchesArtist(guess: String, artist: String): Boolean {
        if (matches(guess, artist)) return true
        val parts = artist.split(ARTIST_SEPARATORS).filter { it.isNotBlank() }
        return parts.size > 1 && parts.any { matches(guess, it) }
    }

    /** Errores permitidos: ninguno en palabras muy cortas, ~25 % en las largas. */
    private fun allowedEdits(length: Int): Int = when {
        length <= 4 -> 0
        length <= 7 -> 1
        else -> (length * TOLERANCE).toInt()
    }

    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD).replace(LATIN_DIACRITICS, "")
        val withoutAccents = Normalizer.normalize(decomposed, Normalizer.Form.NFC)
        val words = QuestionFactory.normalizeTitle(withoutAccents)
            .replace(NON_ALPHANUMERIC, " ")
            .replace(REPEATED_LETTER, "$1")
            .split(" ")
            .filter { it.isNotBlank() }
        val withoutArticle = if (words.size > 1 && words.first() in LEADING_ARTICLES) words.drop(1) else words
        return withoutArticle.joinToString(" ")
    }

    fun levenshtein(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
