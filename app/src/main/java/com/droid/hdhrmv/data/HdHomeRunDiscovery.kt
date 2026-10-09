package com.droid.hdhrmv.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

class HdHomeRunDiscovery(
    private val networkClient: HdHomeRunNetworkClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        const val DISCOVERY_PORT = 65001
        const val CLOUD_DISCOVERY_URL = "http://ipv4-api.hdhomerun.com/discover"
    }

    suspend fun discoverIps(): Set<String> = withContext(ioDispatcher) {
        val foundIps = mutableSetOf<String>()

        // 1. Cloud Discovery fallback / primary check
        try {
            val json = networkClient.get(CLOUD_DISCOVERY_URL)
            val cloudDevices = HdHomeRunParser.parseCloudDiscover(json)
            cloudDevices.forEach { foundIps.add(it.localIp) }
        } catch (_: Exception) {
            // Cloud discovery may fail if offline or blocked
        }

        // 2. UDP Broadcast Discovery
        try {
            val udpIps = discoverViaUdpBroadcast()
            foundIps.addAll(udpIps)
        } catch (_: Exception) {
            // UDP broadcast may be restricted on some Wi-Fi APs
        }

        foundIps
    }

    private fun discoverViaUdpBroadcast(timeoutMs: Int = 1200): Set<String> {
        val ips = mutableSetOf<String>()
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.broadcast = true
            socket.soTimeout = timeoutMs

            // HDHomeRun Discover Request packet: 0x0002 (type), length 0
            val sendData = byteArrayOf(0x00, 0x02, 0x00, 0x00)
            val broadcastAddr = InetAddress.getByName("255.255.255.255")
            val packet = DatagramPacket(sendData, sendData.size, broadcastAddr, DISCOVERY_PORT)
            socket.send(packet)

            val receiveBuffer = ByteArray(1024)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)

            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                try {
                    socket.receive(receivePacket)
                    val senderIp = receivePacket.address.hostAddress
                    if (!senderIp.isNullOrEmpty()) {
                        ips.add(senderIp)
                    }
                } catch (_: SocketTimeoutException) {
                    break
                }
            }
        } catch (_: Exception) {
            // Broadcast failure
        } finally {
            socket?.close()
        }
        return ips
    }
}
