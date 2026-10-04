package com.guesssong.app.music

import com.guesssong.app.model.Genre
import com.guesssong.app.model.MusicSource
import com.guesssong.app.model.Track
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.IOException
import java.net.URLEncoder

interface MusicRepository {
    suspend fun genres(): List<Genre>
    suspend fun loadTracks(source: MusicSource): List<Track>
}

class DeezerMusicRepository(
    private val fetch: suspend (String) -> String = HttpFetcher::getText,
) : MusicRepository {

    override suspend fun genres(): List<Genre> = request { DeezerParser.parseGenres(fetch("$BASE/genre")) }

    override suspend fun loadTracks(source: MusicSource): List<Track> = request {
        when (source) {
            is MusicSource.Chart -> DeezerParser.parseTracks(
                fetch("$BASE/chart/${source.genreId}/tracks?limit=$PAGE_SIZE"),
            )
            is MusicSource.Search -> searchTracks(source.query)
        }
    }

    /** Canciones que coinciden con la búsqueda + las de las playlists más relevantes. */
    private suspend fun searchTracks(query: String): List<Track> = coroutineScope {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val direct = async { DeezerParser.parseTracks(fetch("$BASE/search?q=$q&limit=$PAGE_SIZE")) }
        val playlistIds = DeezerParser.parsePlaylistIds(fetch("$BASE/search/playlist?q=$q&limit=5"))
        val fromPlaylists = playlistIds.take(SEARCH_PLAYLISTS).map { id ->
            async { DeezerParser.parseTracks(fetch("$BASE/playlist/$id/tracks?limit=$PAGE_SIZE")) }
        }
        (fromPlaylists.awaitAll().flatten() + direct.await()).distinctBy { it.id }
    }

    private suspend fun <T> request(block: suspend () -> T): T = try {
        block()
    } catch (e: MusicException) {
        throw e
    } catch (e: IOException) {
        throw MusicException("No se pudo conectar con Deezer. ¿Hay internet?", e)
    }

    companion object {
        private const val BASE = "https://api.deezer.com"
        private const val PAGE_SIZE = 100
        private const val SEARCH_PLAYLISTS = 2

        val FALLBACK_GENRES = listOf(
            Genre(0, "Todos"), Genre(132, "Pop"), Genre(116, "Rap/Hip Hop"),
            Genre(122, "Reggaetón"), Genre(152, "Rock"), Genre(113, "Dance"),
            Genre(165, "R&B"), Genre(85, "Alternativo"), Genre(106, "Electro"),
            Genre(67, "Salsa"), Genre(71, "Cumbia"), Genre(197, "Latino"),
        )
    }
}
