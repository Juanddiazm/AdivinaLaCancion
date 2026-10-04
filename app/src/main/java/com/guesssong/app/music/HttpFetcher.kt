package com.guesssong.app.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** HTTP mínimo con HttpURLConnection, suficiente para la API de Deezer y los previews. */
object HttpFetcher {
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val MAX_DOWNLOAD_BYTES = 5L * 1024 * 1024

    suspend fun getText(url: String): String = withContext(Dispatchers.IO) {
        open(url).useConnection { it.inputStream.bufferedReader(Charsets.UTF_8).readText() }
    }

    /** Descarga un archivo pequeño (preview de 30 s ≈ 0.5 MB) a [target]. */
    suspend fun download(url: String, target: File): File = withContext(Dispatchers.IO) {
        open(url).useConnection { connection ->
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_DOWNLOAD_BYTES) throw IOException("Preview demasiado grande")
                        output.write(buffer, 0, read)
                    }
                }
            }
        }
        target
    }

    private fun open(url: String): HttpURLConnection {
        require(url.startsWith("https://")) { "Solo se permiten URLs https" }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
        }
    }

    private inline fun <T> HttpURLConnection.useConnection(block: (HttpURLConnection) -> T): T =
        try {
            val code = responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            block(this)
        } finally {
            disconnect()
        }
}
