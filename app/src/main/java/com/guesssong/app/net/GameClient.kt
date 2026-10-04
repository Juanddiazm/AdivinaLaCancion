package com.guesssong.app.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/** Conexión de un jugador (incluido el host) a la sala. */
class GameClient(private val clock: () -> Long) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(ClientState())
    val state: StateFlow<ClientState> = _state.asStateFlow()
    private val outbox = Channel<String>(Channel.UNLIMITED)
    @Volatile
    private var connection: LineConnection? = null

    fun connect(host: String, port: Int, name: String) {
        scope.launch {
            val conn = try {
                val socket = Socket().apply { connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS) }
                LineConnection(socket)
            } catch (e: Exception) {
                closeWith("No se pudo conectar a $host. ¿Están en la misma red Wi-Fi?")
                return@launch
            }
            connection = conn
            // close() pudo llegar mientras conectaba: Socket.connect no se puede interrumpir.
            if (!isActive) {
                conn.close()
                return@launch
            }
            val writer = launch {
                for (line in outbox) if (!conn.send(line)) break
            }
            outbox.trySend(Protocol.encode(ClientMessage.Join(name)))
            try {
                readLoop(conn)
            } catch (e: IOException) {
                // Cae al cierre de abajo.
            } finally {
                writer.cancel()
                conn.close()
                closeWith("Se perdió la conexión con el host.")
            }
        }
    }

    private fun readLoop(conn: LineConnection) {
        while (true) {
            val line = conn.readLine() ?: return
            val message = Protocol.decodeServer(line) ?: continue
            if (message == ServerMessage.Ping) outbox.trySend(Protocol.encode(ClientMessage.Pong))
            _state.update { ClientReducer.reduce(it, message, clock()) }
            if (_state.value.connection is ConnectionStatus.Closed) return
        }
    }

    fun answer(optionIndex: Int) {
        val current = _state.value
        val updated = ClientReducer.select(current, optionIndex) ?: return
        if (!_state.compareAndSet(current, updated)) return
        val round = (updated.phase as ClientPhase.Question).round
        outbox.trySend(Protocol.encode(ClientMessage.Answer(round, optionIndex)))
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun close() {
        // Primero cancelar, luego cerrar: así connect() nunca deja una conexión viva.
        scope.cancel()
        connection?.close()
    }

    /** Conserva el primer motivo de cierre (p. ej. un rechazo del host). */
    private fun closeWith(reason: String) {
        _state.update {
            if (it.connection is ConnectionStatus.Closed) it else it.copy(connection = ConnectionStatus.Closed(reason))
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
    }
}
