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
     * mismo grupo y nunca coinciden con la respuesta en lo que se adivina ([target]): si se
     * adivina el artista, las 4 opciones son artistas distintos. Lista vacía si no alcanzan.
     */
    fun build(
        tracks: List<Track>,
        count: Int,
        random: Random = Random.Default,
        target: GuessTarget = GuessTarget.TITLE,
        optionCount: Int = OPTIONS_PER_QUESTION,
    ): List<Question> {
        val pool = tracks
            .filter { it.previewUrl.isNotBlank() && it.title.isNotBlank() && it.artist.isNotBlank() }
            .distinctBy { it.id }
            .distinctBy { normalizeTitle(it.title) }
        val key: (Track) -> String = { normalizeTitle(target.of(it)) }
        if (count <= 0 || optionCount <= 0 || pool.distinctBy(key).size < optionCount) return emptyList()

        return pool.shuffled(random).take(count).map { answer ->
            val distractors = pool
                .filter { key(it) != key(answer) }
                .shuffled(random)
                .distinctBy(key)
                .take(optionCount - 1)
            val options = (distractors + answer).shuffled(random)
            Question(answer = answer, options = options, correctIndex = options.indexOf(answer))
        }
    }

    /**
     * "Dai Dai (Remix)" y "Dai Dai - Live" cuentan como la misma canción. Si no queda nada
     * (p. ej. "(Intro)"), se usa el título completo.
     */
    fun normalizeTitle(title: String): String =
        title.replace(PARENTHESES, "").replace(SUFFIX, "").trim().lowercase()
            .ifBlank { title.trim().lowercase() }
}
