package com.guesssong.app.net

import com.guesssong.app.audio.AudioPlayer
import com.guesssong.app.game.GameConfig
import com.guesssong.app.game.tracks
import com.guesssong.app.model.Genre
import com.guesssong.app.model.MusicSource
import com.guesssong.app.model.Track
import com.guesssong.app.music.MusicException
import com.guesssong.app.music.MusicRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Partida completa por TCP real en localhost, con música y audio simulados. */
class GameServerIntegrationTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val clock: () -> Long = { System.nanoTime() / 1_000_000 }
    private val audio = FakeAudio()
    private val clients = mutableListOf<GameClient>()
    private lateinit var server: GameServer

    private class FakeAudio : AudioPlayer {
        val plays = AtomicInteger()
        override suspend fun prepare(file: File) = file.exists()
        override fun play() { plays.incrementAndGet() }
        override fun stop() = Unit
        override fun release() = Unit
    }

    private class FakeMusic(private val result: () -> List<Track>) : MusicRepository {
        override suspend fun genres() = listOf(Genre(0, "Todos"))
        override suspend fun loadTracks(source: MusicSource) = result()
    }

    private fun startServer(music: MusicRepository): Int {
        server = GameServer(
            roomName = "Sala de prueba",
            music = music,
            audio = audio,
            previewDir = tmp.newFolder("previews"),
            clock = clock,
            download = { _, target -> target.apply { writeText("mp3") } },
            timing = GameServer.Timing(roundIntroMs = 30, revealMs = 30),
            preferredPort = 0,
        )
        return server.start()
    }

    private suspend fun join(port: Int, name: String): GameClient {
        val client = GameClient(clock).also { clients += it }
        client.connect("127.0.0.1", port, name)
        client.state.first { it.connection == ConnectionStatus.Connected }
        return client
    }

    private suspend fun GameClient.awaitQuestion(round: Int): ClientPhase.Question =
        state.first { (it.phase as? ClientPhase.Question)?.round == round }.phase as ClientPhase.Question

    private suspend fun GameClient.awaitReveal(round: Int): ClientPhase.Reveal =
        state.first { (it.phase as? ClientPhase.Reveal)?.round == round }.phase as ClientPhase.Reveal

    @After
    fun tearDown() {
        clients.forEach { it.close() }
        server.close()
    }

    @Test
    fun `full game with two players awards points and ends with ranking`() = runBlocking<Unit> {
        withTimeout(20_000) {
            val port = startServer(FakeMusic { tracks(12) })
            val ana = join(port, "Ana")
            val beto = join(port, "Beto")
            ana.state.first { it.players.size == 2 }

            server.startGame(GameConfig(MusicSource.Chart(0, "Top"), rounds = 2, roundSeconds = 5))

            var expectedAna = 0
            var expectedBeto = 0
            for (round in 1..2) {
                ana.awaitQuestion(round)
                beto.awaitQuestion(round)
                ana.answer(0)
                beto.answer(1)
                val anaReveal = ana.awaitReveal(round)
                val betoReveal = beto.awaitReveal(round)

                assertEquals(0, anaReveal.selectedIndex)
                assertEquals(anaReveal.correctIndex, betoReveal.correctIndex)
                assertEquals(anaReveal.correctIndex == 0, anaReveal.myGain > 0)
                assertEquals(betoReveal.correctIndex == 1, betoReveal.myGain > 0)
                expectedAna += anaReveal.myGain
                expectedBeto += betoReveal.myGain
            }

            val final = ana.state.first { it.phase == ClientPhase.Finished }
            val scores = final.players.associate { it.name to it.score }
            assertEquals(mapOf("Ana" to expectedAna, "Beto" to expectedBeto), scores)
            assertEquals(2, audio.plays.get())
        }
    }

    @Test
    fun `round ends early when the only other player disconnects`() = runBlocking<Unit> {
        withTimeout(10_000) {
            val port = startServer(FakeMusic { tracks(8) })
            val ana = join(port, "Ana")
            val beto = join(port, "Beto")
            ana.state.first { it.players.size == 2 }

            // 30 s por ronda: si el servidor esperara a Beto, el timeout del test fallaría.
            server.startGame(GameConfig(MusicSource.Chart(0, "Top"), rounds = 1, roundSeconds = 30))
            ana.awaitQuestion(1)
            beto.close()
            ana.state.first { s -> s.players.any { it.name == "Beto" && !it.connected } }
            ana.answer(2)

            ana.awaitReveal(1)
            ana.state.first { it.phase == ClientPhase.Finished }
        }
    }

    @Test
    fun `music failure returns everyone to lobby with a notice`() = runBlocking<Unit> {
        withTimeout(10_000) {
            val port = startServer(FakeMusic { throw MusicException("Sin internet") })
            val ana = join(port, "Ana")

            server.startGame(GameConfig(MusicSource.Search("rock"), rounds = 3))

            val state = ana.state.first { it.notice != null }
            assertEquals("Sin internet", state.notice)
            assertEquals(ClientPhase.Lobby, state.phase)
        }
    }

    @Test
    fun `too few songs returns to lobby`() = runBlocking<Unit> {
        withTimeout(10_000) {
            val port = startServer(FakeMusic { tracks(2) })
            val ana = join(port, "Ana")
            server.startGame(GameConfig(MusicSource.Search("nada"), rounds = 3))
            val state = ana.state.first { it.notice != null }
            assertTrue(state.notice!!.contains("suficientes"))
        }
    }
}
