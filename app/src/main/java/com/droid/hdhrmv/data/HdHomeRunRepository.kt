package com.droid.hdhrmv.data

import com.droid.hdhrmv.model.Channel
import com.droid.hdhrmv.model.HdHomeRunDevice
import com.droid.hdhrmv.model.TunerStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HdHomeRunRepository(
    private val networkClient: HdHomeRunNetworkClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val discovery: HdHomeRunDiscovery = HdHomeRunDiscovery(networkClient, ioDispatcher)
) {
    suspend fun discoverDevices(): List<HdHomeRunDevice> = withContext(ioDispatcher) {
        val ips = discovery.discoverIps()
        val devices = mutableListOf<HdHomeRunDevice>()
        for (ip in ips) {
            try {
                val device = fetchDevice(ip)
                val tuners = fetchTunerStatus(device.baseUrl)
                val freeCount = HdHomeRunParser.calculateFreeTuners(tuners, device.tunerCount)
                devices.add(device.copy(freeTunerCount = freeCount))
            } catch (_: Exception) {
                // Ignore unresponsive IP
            }
        }
        devices
    }

    suspend fun fetchDevice(ip: String): HdHomeRunDevice = withContext(ioDispatcher) {
        val cleanIp = ip.removePrefix("http://").removeSuffix("/")
        val url = "http://$cleanIp/discover.json"
        val json = networkClient.get(url)
        HdHomeRunParser.parseDevice(json, cleanIp)
    }

    suspend fun fetchLineup(lineupUrl: String): List<Channel> = withContext(ioDispatcher) {
        val json = networkClient.get(lineupUrl)
        HdHomeRunParser.parseLineup(json)
    }

    suspend fun fetchTunerStatus(baseUrl: String): List<TunerStatus> = withContext(ioDispatcher) {
        val cleanBase = baseUrl.removeSuffix("/")
        val url = "$cleanBase/status.json"
        try {
            val json = networkClient.get(url)
            HdHomeRunParser.parseTunerStatus(json)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchFreeTunerCount(baseUrl: String, totalTuners: Int): Int = withContext(ioDispatcher) {
        val tuners = fetchTunerStatus(baseUrl)
        HdHomeRunParser.calculateFreeTuners(tuners, totalTuners)
    }
}
