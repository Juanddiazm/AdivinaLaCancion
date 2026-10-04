package com.guesssong.app.music

import com.guesssong.app.model.MusicSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class DeezerTest {
    private fun trackJson(id: Long, title: String, preview: String? = "https://p/$id.mp3", readable: Boolean = true) =
        """{"id":$id,"title":"$title","readable":$readable,"preview":${preview?.let { "\"$it\"" } ?: "null"},
           "artist":{"name":"Artist $id"},"album":{"cover_medium":"https://c/$id.jpg"}}"""

    private fun page(vararg items: String) = """{"data":[${items.joinToString(",")}],"total":${items.size}}"""

    @Test
    fun `parses tracks and drops unplayable ones`() {
        val body = page(
            trackJson(1, "Uno"),
            trackJson(2, "Sin preview", preview = ""),
            trackJson(3, "Bloqueada", readable = false),
            trackJson(4, "Null preview", preview = null),
        )
        val tracks = DeezerParser.parseTracks(body)
        assertEquals(1, tracks.size)
        assertEquals("Uno", tracks[0].title)
        assertEquals("Artist 1", tracks[0].artist)
        assertEquals("https://c/1.jpg", tracks[0].coverUrl)
    }

    @Test
    fun `api error becomes MusicException`() {
        try {
            DeezerParser.parseTracks("""{"error":{"type":"Exception","message":"Quota limit exceeded","code":4}}""")
            fail("expected exception")
        } catch (e: MusicException) {
            assertTrue(e.message!!.contains("Quota"))
        }
    }

    @Test(expected = MusicException::class)
    fun `invalid json becomes MusicException`() {
        DeezerParser.parseTracks("<html>")
    }

    @Test
    fun `parses genres and playlists`() {
        val genres = DeezerParser.parseGenres("""{"data":[{"id":0,"name":"Todos"},{"id":132,"name":"Pop"},{"id":5,"name":""}]}""")
        assertEquals(listOf(0L, 132L), genres.map { it.id })
        val ids = DeezerParser.parsePlaylistIds("""{"data":[{"id":9,"nb_tracks":0},{"id":10,"nb_tracks":50}]}""")
        assertEquals(listOf(10L), ids)
    }

    @Test
    fun `chart source hits chart endpoint`() = runTest {
        val requested = mutableListOf<String>()
        val repo = DeezerMusicRepository { url -> requested += url; page(trackJson(1, "Uno")) }

        val tracks = repo.loadTracks(MusicSource.Chart(132, "Pop"))

        assertEquals(1, tracks.size)
        assertEquals(listOf("https://api.deezer.com/chart/132/tracks?limit=100"), requested)
    }

    @Test
    fun `search merges direct results with playlists without duplicates`() = runTest {
        val repo = DeezerMusicRepository { url ->
            when {
                url.contains("/search/playlist") -> """{"data":[{"id":77,"nb_tracks":3}]}"""
                url.contains("/playlist/77/tracks") -> page(trackJson(1, "Uno"), trackJson(2, "Dos"))
                url.contains("/search?q=rock+en+espa%C3%B1ol") -> page(trackJson(2, "Dos"), trackJson(3, "Tres"))
                else -> error("unexpected $url")
            }
        }
        val tracks = repo.loadTracks(MusicSource.Search("rock en español"))
        assertEquals(setOf(1L, 2L, 3L), tracks.map { it.id }.toSet())
        assertEquals(3, tracks.size)
    }

    @Test(expected = MusicException::class)
    fun `network failure becomes friendly MusicException`() = runTest {
        DeezerMusicRepository { throw IOException("offline") }.loadTracks(MusicSource.Chart(0, "Top"))
    }
}
