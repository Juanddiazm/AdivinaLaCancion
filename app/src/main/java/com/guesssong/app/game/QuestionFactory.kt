package com.guesssong.app.game

import com.guesssong.app.model.Track
import kotlin.random.Random

data class Question(
    val answer: Track,
    val options: List<Track>,
    val correctIndex: Int,
)

object QuestionFactory {
    const val OPTIONS_PER_QUESTION = 4

    private val PARENTHESES = Regex("""\s*[(\[].*?[)\]]""")
    private val SUFFIX = Regex("""\s+-\s+.*$""")

    /**
     * Arma hasta [count] preguntas sin repetir canción. Las opciones incorrectas salen del
     * mismo grupo, así que todas suenan "del mismo estilo". Lista vacía si no alcanzan.
     */
    fun build(tracks: List<Track>, count: Int, random: Random = Random.Default): List<Question> {
        val pool = tracks
            .filter { it.previewUrl.isNotBlank() && it.title.isNotBlank() }
            .distinctBy { it.id }
            .distinctBy { normalizeTitle(it.title) }
        if (pool.size < OPTIONS_PER_QUESTION || count <= 0) return emptyList()

        return pool.shuffled(random).take(count).map { answer ->
            val distractors = pool
                .filter { it.id != answer.id }
                .shuffled(random)
                .take(OPTIONS_PER_QUESTION - 1)
            val options = (distractors + answer).shuffled(random)
            Question(answer = answer, options = options, correctIndex = options.indexOf(answer))
        }
    }

    /** "Dai Dai (Remix)" y "Dai Dai - Live" cuentan como la misma canción. */
    fun normalizeTitle(title: String): String =
        title.replace(PARENTHESES, "").replace(SUFFIX, "").trim().lowercase()
}
