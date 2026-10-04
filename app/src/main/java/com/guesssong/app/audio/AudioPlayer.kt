package com.guesssong.app.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

interface AudioPlayer {
    /** Deja lista la canción para sonar al instante. False si el archivo no se pudo leer. */
    suspend fun prepare(file: File): Boolean
    fun play()
    fun stop()
    fun release()
}

/** Seguro entre hilos: prepare() corre en IO y release() puede llegar desde el hilo principal. */
class MediaAudioPlayer : AudioPlayer {
    private var player: MediaPlayer? = null
    private var released = false

    override suspend fun prepare(file: File): Boolean = withContext(Dispatchers.IO) {
        synchronized(this@MediaAudioPlayer) { releaseCurrent() }
        val mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
        }
        try {
            mediaPlayer.setDataSource(file.absolutePath)
            mediaPlayer.prepare()
            synchronized(this@MediaAudioPlayer) {
                if (released) {
                    mediaPlayer.release()
                    false
                } else {
                    player = mediaPlayer
                    true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo preparar ${file.name}", e)
            mediaPlayer.release()
            false
        }
    }

    @Synchronized
    override fun play() {
        runCatching { player?.start() }.onFailure { Log.w(TAG, "Fallo al reproducir", it) }
    }

    @Synchronized
    override fun stop() {
        runCatching { player?.takeIf { it.isPlaying }?.pause() }
    }

    @Synchronized
    override fun release() {
        released = true
        releaseCurrent()
    }

    private fun releaseCurrent() {
        player?.let { runCatching { it.release() } }
        player = null
    }

    private companion object {
        const val TAG = "AudioPlayer"
    }
}
