package com.guesssong.app.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val PROTOCOL_VERSION = 1
const val GAME_PORT = 47778
const val DISCOVERY_PORT = 47777

@Serializable
data class PlayerDto(val id: String, val name: String, val score: Int, val connected: Boolean)

@Serializable
data class OptionDto(val title: String, val artist: String)

/** Mensajes que un jugador envía al host. */
@Serializable
sealed interface ClientMessage {
    @Serializable
    @SerialName("join")
    data class Join(val name: String, val protocolVersion: Int = PROTOCOL_VERSION) : ClientMessage

    @Serializable
    @SerialName("answer")
    data class Answer(val round: Int, val optionIndex: Int) : ClientMessage

    @Serializable
    @SerialName("pong")
    data object Pong : ClientMessage
}

/** Mensajes que el host envía a los jugadores. */
@Serializable
sealed interface ServerMessage {
    @Serializable
    @SerialName("welcome")
    data class Welcome(val playerId: String, val roomName: String) : ServerMessage

    @Serializable
    @SerialName("rejected")
    data class Rejected(val reason: String) : ServerMessage

    @Serializable
    @SerialName("players")
    data class Players(val players: List<PlayerDto>) : ServerMessage

    @Serializable
    @SerialName("lobby")
    data object BackToLobby : ServerMessage

    @Serializable
    @SerialName("preparing")
    data class Preparing(val message: String) : ServerMessage

    @Serializable
    @SerialName("round_start")
    data class RoundStart(
        val round: Int,
        val totalRounds: Int,
        val options: List<OptionDto>,
        val durationMs: Long,
    ) : ServerMessage

    @Serializable
    @SerialName("progress")
    data class AnswerProgress(val round: Int, val answered: Int, val total: Int) : ServerMessage

    @Serializable
    @SerialName("round_end")
    data class RoundEnd(
        val round: Int,
        val totalRounds: Int,
        val correctIndex: Int,
        val title: String,
        val artist: String,
        val coverUrl: String? = null,
        val gains: Map<String, Int>,
        val choices: Map<String, Int>,
        val players: List<PlayerDto>,
    ) : ServerMessage

    @Serializable
    @SerialName("game_over")
    data class GameOver(val players: List<PlayerDto>) : ServerMessage

    @Serializable
    @SerialName("notice")
    data class Notice(val message: String) : ServerMessage

    @Serializable
    @SerialName("ping")
    data object Ping : ServerMessage
}

/** Codificación JSON de una línea por mensaje. */
object Protocol {
    val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        encodeDefaults = true
    }

    fun encode(message: ClientMessage): String = json.encodeToString(ClientMessage.serializer(), message)
    fun encode(message: ServerMessage): String = json.encodeToString(ServerMessage.serializer(), message)

    /** Null si la línea no es un mensaje válido. */
    fun decodeClient(line: String): ClientMessage? =
        runCatching { json.decodeFromString(ClientMessage.serializer(), line) }.getOrNull()

    fun decodeServer(line: String): ServerMessage? =
        runCatching { json.decodeFromString(ServerMessage.serializer(), line) }.getOrNull()
}
