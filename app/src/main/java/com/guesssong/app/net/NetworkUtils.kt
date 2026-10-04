package com.guesssong.app.net

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

object NetworkUtils {

    /** IPs IPv4 de la red local (Wi-Fi o hotspot) para mostrarlas y unirse a mano. */
    fun localIpv4Addresses(): List<String> = interfaces()
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .filter { !it.isLoopbackAddress && it.isSiteLocalAddress }
        .mapNotNull { it.hostAddress }
        .distinct()

    /** Direcciones de broadcast de cada interfaz, más la global. */
    fun broadcastAddresses(): List<InetAddress> {
        val perInterface = interfaces()
            .flatMap { it.interfaceAddresses }
            .mapNotNull { it.broadcast }
        return (perInterface + InetAddress.getByName("255.255.255.255")).distinct()
    }

    private fun interfaces(): List<NetworkInterface> = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback }
    }.getOrDefault(emptyList())
}
