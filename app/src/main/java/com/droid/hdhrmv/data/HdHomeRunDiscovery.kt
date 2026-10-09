package com.droid.hdhrmv.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.zip.CRC32

class HdHomeRunDiscovery(
    private val networkClient: HdHomeRunNetworkClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        const val DISCOVERY_PORT = 65001
        const val CLOUD_DISCOVERY_URL = "http://ipv4-api.hdhomerun.com/discover"

        fun buildDiscoverPacket(): ByteArray {
            // HDHomeRun frame format:
            // uint16 type (0x0002 HDHOMERUN_TYPE_DISCOVER_REQ)
            // uint16 payload length (0x0006)
            // Payload TLV:
            //   uint8 tag (0x01 HDHOMERUN_TAG_DEVICE_TYPE)
            //   varlen length (0x04)
            //   uint32 value (0x00000001 HDHOMERUN_DEVICE_TYPE_TUNER)
            // uint32 CRC32 (IEEE 802.3, little-endian)
            val payload = byteArrayOf(
                0x01.toByte(), // TAG_DEVICE_TYPE
                0x04.toByte(), // length 4
                0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte() // DEVICE_TYPE_TUNER
            )
            val header = byteArrayOf(
                0x00.toByte(), 0x02.toByte(), // TYPE_DISCOVER_REQ
                0x00.toByte(), payload.size.toByte() // payload length
            )
            val frame = header + payload
            val crc = CRC32().apply { update(frame) }.value
            val crcBytes = byteArrayOf(
                (crc and 0xFF).toByte(),
                ((crc shr 8) and 0xFF).toByte(),
                ((crc shr 16) and 0xFF).toByte(),
                ((crc shr 24) and 0xFF).toByte()
            )
            return frame + crcBytes
        }
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

        // 2. Multi-Interface UDP Broadcast Discovery (Standard HDHomeRun protocol)
        try {
            val udpIps = discoverViaUdpBroadcast()
            foundIps.addAll(udpIps)
        } catch (_: Exception) {
            // UDP broadcast may be restricted on some Wi-Fi APs
        }

        // 3. If still nothing found, perform rapid subnet sweep probe
        if (foundIps.isEmpty()) {
            try {
                val subnetIps = discoverViaSubnetSweep()
                foundIps.addAll(subnetIps)
            } catch (_: Exception) {
                // Subnet probe error
            }
        }

        foundIps
    }

    private fun getBroadcastAddresses(): List<InetAddress> {
        val broadcastAddresses = mutableListOf<InetAddress>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                for (interfaceAddress in iface.interfaceAddresses) {
                    val bcast = interfaceAddress.broadcast
                    if (bcast != null) {
                        broadcastAddresses.add(bcast)
                    }
                }
            }
        } catch (_: Exception) {}
        try {
            broadcastAddresses.add(InetAddress.getByName("255.255.255.255"))
        } catch (_: Exception) {}
        return broadcastAddresses.distinct()
    }

    private fun discoverViaUdpBroadcast(timeoutMs: Int = 1200): Set<String> {
        val ips = mutableSetOf<String>()
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.broadcast = true
            socket.soTimeout = timeoutMs

            val sendData = buildDiscoverPacket()
            val targetAddresses = getBroadcastAddresses()

            for (addr in targetAddresses) {
                try {
                    val packet = DatagramPacket(sendData, sendData.size, addr, DISCOVERY_PORT)
                    socket.send(packet)
                } catch (_: Exception) {
                    // Ignore single address broadcast error
                }
            }

            val receiveBuffer = ByteArray(1024)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)

            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                try {
                    socket.receive(receivePacket)
                    val senderIp = receivePacket.address.hostAddress
                    if (!senderIp.isNullOrEmpty() && senderIp != "127.0.0.1") {
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

    private suspend fun discoverViaSubnetSweep(): Set<String> = withContext(ioDispatcher) {
        val foundIps = mutableSetOf<String>()
        val localIps = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                for (addr in iface.inetAddresses) {
                    val ip = addr.hostAddress ?: continue
                    if (ip.contains(".") && !ip.startsWith("127.")) {
                        localIps.add(ip)
                    }
                }
            }
        } catch (_: Exception) {}

        for (localIp in localIps) {
            val prefix = localIp.substringBeforeLast(".")
            coroutineScope {
                val jobs = (1..254).map { host ->
                    async {
                        val testIp = "$prefix.$host"
                        if (isPortOpen(testIp, 80, 150)) {
                            try {
                                val testUrl = "http://$testIp/discover.json"
                                val json = networkClient.get(testUrl)
                                if (json.contains("DeviceID", ignoreCase = true) || json.contains("HDHomeRun", ignoreCase = true)) {
                                    testIp
                                } else null
                            } catch (_: Exception) {
                                null
                            }
                        } else null
                    }
                }
                jobs.awaitAll().filterNotNull().forEach { foundIps.add(it) }
            }
        }
        foundIps
    }

    private fun isPortOpen(ip: String, port: Int = 80, timeoutMs: Int = 150): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
