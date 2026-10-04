package com.guesssong.app.model

/** De dónde sacar las canciones de una partida. */
sealed interface MusicSource {
    val label: String

    /** Top de Deezer para un género (id 0 = todos). */
    data class Chart(val genreId: Long, override val label: String) : MusicSource

    /** Búsqueda libre: artista, playlist, década… */
    data class Search(val query: String) : MusicSource {
        override val label: String get() = "\"$query\""
    }
}

data class Genre(val id: Long, val name: String)
