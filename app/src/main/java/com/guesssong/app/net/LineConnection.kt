package com.guesssong.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.IOException
import java.io.Reader
import java.net.Socket

/** Socket TCP que intercambia mensajes como líneas de texto UTF-8. */
class LineConnection(private val socket: Socket) : Closeable {
    private val reader: Reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
    private val writer = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)
    private val writeLock = Mutex()

    val remoteAddress: String get() = socket.inetAddress?.hostAddress.orEmpty()

    init {
        socket.tcpNoDelay = true
        socket.keepAlive = true
        socket.soTimeout = READ_TIMEOUT_MS
    }

    /** False si la conexión ya está rota. */
    suspend fun send(line: String): Boolean = withContext(Dispatchers.IO) {
        writeLock.withLock {
            try {
                writer.write(line)
                writer.write("\n")
                writer.flush()
                true
            } catch (e: IOException) {
                false
            }
        }
    }

    /** Lee la siguiente línea (bloqueante). Null al cerrar; IOException si se excede el tamaño. */
    fun readLine(): String? {
        val builder = StringBuilder()
        while (true) {
            val c = reader.read()
            if (c < 0) return if (builder.isEmpty()) null else builder.toString()
            if (c == '\n'.code) return builder.toString()
            if (builder.length >= MAX_LINE_LENGTH) throw IOException("Mensaje demasiado largo")
            builder.append(c.toChar())
        }
    }

    override fun close() {
        runCatching { socket.close() }
    }

    companion object {
        const val MAX_LINE_LENGTH = 64 * 1024
        /** Si no llega nada (ni ping) en este tiempo, la conexión se da por muerta. */
        const val READ_TIMEOUT_MS = 20_000
        const val PING_INTERVAL_MS = 5_000L
    }
}
