package com.guesssong.app.net

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

@Serializable
data class RoomAnnouncement(
    val app: String = APP_TAG,
    val version: Int = PROTOCOL_VERSION,
    val name: String,
    val port: Int,
) {
    companion object {
        const val APP_TAG = "guesssong"
    }
}

data class DiscoveredRoom(val name: String, val host: String, val port: Int, val lastSeenMs: Long)

/** El host grita "aquí estoy" por UDP broadcast cada segundo. */
class RoomAnnouncer(private val roomName: String, private val gamePort: Int) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job != null) return
        job = scope.launch(Dispatchers.IO) {
            val payload = Protocol.json
                .encodeToString(RoomAnnouncement.serializer(), RoomAnnouncement(name = roomName, port = gamePort))
                .toByteArray(Charsets.UTF_8)
            DatagramSocket().use { socket ->
                socket.broadcast = true
                while (isActive) {
                    for (address in NetworkUtils.broadcastAddresses()) {
                        runCatching { socket.send(DatagramPacket(payload, payload.size, address, DISCOVERY_PORT)) }
                    }
                    delay(ANNOUNCE_INTERVAL_MS)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private companion object {
        const val ANNOUNCE_INTERVAL_MS = 1_000L
    }
}

/** Escucha los anuncios de salas en la red local. */
class RoomScanner(context: Context, private val clock: () -> Long) {
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val _rooms = MutableStateFlow<List<DiscoveredRoom>>(emptyList())
    val rooms: StateFlow<List<DiscoveredRoom>> = _rooms.asStateFlow()
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job != null) return
        _rooms.value = emptyList()
        job = scope.launch(Dispatchers.IO) {
            // Algunos teléfonos descartan paquetes broadcast sin este lock.
            val lock = wifi.createMulticastLock("guesssong-discovery").apply { setReferenceCounted(false) }
            lock.acquire()
            try {
                while (isActive) {
                    try {
                        listen()
                    } catch (e: IOException) {
                        // Wi-Fi apagado o puerto ocupado: reintentar sin tumbar la app.
                        Log.w(TAG, "Escaneo interrumpido: ${e.message}")
                        pruneStale()
                        delay(RETRY_DELAY_MS)
                    }
                }
            } finally {
                lock.release()
            }
        }
    }

    private suspend fun listen() {
        DatagramSocket(null).use { socket ->
            socket.reuseAddress = true
            socket.broadcast = true
            socket.soTimeout = RECEIVE_TIMEOUT_MS
            socket.bind(InetSocketAddress(DISCOVERY_PORT))
            val buffer = ByteArray(2048)
            while (currentCoroutineContext().isActive) {
                receive(socket, buffer)
                pruneStale()
            }
        }
    }

    private fun receive(socket: DatagramSocket, buffer: ByteArray) {
        val packet = DatagramPacket(buffer, buffer.size)
        try {
            socket.receive(packet)
        } catch (e: SocketTimeoutException) {
            return
        }
        val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
        val announcement = runCatching {
            Protocol.json.decodeFromString(RoomAnnouncement.serializer(), text)
        }.getOrNull() ?: return
        if (announcement.app != RoomAnnouncement.APP_TAG || announcement.version != PROTOCOL_VERSION) return
        if (announcement.port !in 1..65535) return
        val host = packet.address?.hostAddress ?: return
        val room = DiscoveredRoom(announcement.name.take(40), host, announcement.port, clock())
        _rooms.update { rooms ->
            (rooms.filterNot { it.host == host && it.port == room.port } + room)
                .sortedBy { it.name }
                .take(MAX_ROOMS)
        }
    }

    private fun pruneStale() {
        val now = clock()
        _rooms.update { rooms -> rooms.filter { now - it.lastSeenMs < STALE_AFTER_MS } }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private companion object {
        const val TAG = "RoomScanner"
        const val RECEIVE_TIMEOUT_MS = 1_000
        const val STALE_AFTER_MS = 4_000L
        const val RETRY_DELAY_MS = 2_000L
        const val MAX_ROOMS = 20
    }
}
