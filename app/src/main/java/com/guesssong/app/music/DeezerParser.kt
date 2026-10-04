package com.guesssong.app.music

import com.guesssong.app.model.Genre
import com.guesssong.app.model.Track
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class MusicException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Convierte las respuestas JSON de la API pública de Deezer a modelos del juego. */
object DeezerParser {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ErrorDto(val message: String? = null, val code: Int? = null)

    @Serializable
    private data class ArtistDto(val name: String = "")

    @Serializable
    private data class AlbumDto(@SerialName("cover_medium") val coverMedium: String? = null)

    @Serializable
    private data class TrackDto(
        val id: Long,
        val title: String = "",
        val preview: String? = null,
        val readable: Boolean = true,
        val artist: ArtistDto = ArtistDto(),
        val album: AlbumDto? = null,
    )

    @Serializable
    private data class TrackPage(val data: List<TrackDto> = emptyList(), val error: ErrorDto? = null)

    @Serializable
    private data class GenreDto(val id: Long, val name: String = "")

    @Serializable
    private data class GenrePage(val data: List<GenreDto> = emptyList(), val error: ErrorDto? = null)

    @Serializable
    private data class PlaylistDto(val id: Long, @SerialName("nb_tracks") val trackCount: Int = 0)

    @Serializable
    private data class PlaylistPage(val data: List<PlaylistDto> = emptyList(), val error: ErrorDto? = null)

    fun parseTracks(body: String): List<Track> {
        val page = decode(TrackPage.serializer(), body)
        page.error?.let { throw MusicException(errorText(it)) }
        return page.data
            .filter { it.readable && !it.preview.isNullOrBlank() }
            .map {
                Track(
                    id = it.id,
                    title = it.title,
                    artist = it.artist.name,
                    previewUrl = it.preview.orEmpty(),
                    coverUrl = it.album?.coverMedium,
                )
            }
    }

    fun parseGenres(body: String): List<Genre> {
        val page = decode(GenrePage.serializer(), body)
        page.error?.let { throw MusicException(errorText(it)) }
        return page.data.filter { it.name.isNotBlank() }.map { Genre(it.id, it.name) }
    }

    /** Ids de playlists con canciones, en el orden de relevancia de Deezer. */
    fun parsePlaylistIds(body: String): List<Long> {
        val page = decode(PlaylistPage.serializer(), body)
        page.error?.let { throw MusicException(errorText(it)) }
        return page.data.filter { it.trackCount > 0 }.map { it.id }
    }

    private fun <T> decode(serializer: kotlinx.serialization.KSerializer<T>, body: String): T =
        try {
            json.decodeFromString(serializer, body)
        } catch (e: Exception) {
            throw MusicException("Respuesta inesperada de Deezer", e)
        }

    private fun errorText(error: ErrorDto): String =
        "Deezer respondió con un error: ${error.message ?: "código ${error.code}"}"
}
