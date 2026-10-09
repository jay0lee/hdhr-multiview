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
    private val discovery: HdHomeRunDiscovery = HdHomeRunDiscovery(networkClient, ioDispatcher),
    private val preferences: DevicePreferences = InMemoryDevicePreferences()
) {
    suspend fun discoverDevices(): List<HdHomeRunDevice> = withContext(ioDispatcher) {
        val devices = mutableListOf<HdHomeRunDevice>()
        val seenIps = mutableSetOf<String>()

        // 1. Check cached IP first for immediate reconnection
        val cachedIp = preferences.getLastConnectedIp()
        if (!cachedIp.isNullOrBlank()) {
            try {
                val device = fetchDevice(cachedIp)
                val tuners = fetchTunerStatus(device.baseUrl)
                val freeCount = HdHomeRunParser.calculateFreeTuners(tuners, device.tunerCount)
                devices.add(device.copy(freeTunerCount = freeCount))
                seenIps.add(cachedIp)
            } catch (_: Exception) {
                // Cached device may be temporarily offline
            }
        }

        // 2. Discover via cloud, multi-interface UDP broadcast, and subnet sweep
        val ips = discovery.discoverIps()
        for (ip in ips) {
            if (seenIps.contains(ip)) continue
            try {
                val device = fetchDevice(ip)
                val tuners = fetchTunerStatus(device.baseUrl)
                val freeCount = HdHomeRunParser.calculateFreeTuners(tuners, device.tunerCount)
                devices.add(device.copy(freeTunerCount = freeCount))
                seenIps.add(ip)
                if (preferences.getLastConnectedIp() == null) {
                    preferences.setLastConnectedIp(ip)
                }
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
        val device = HdHomeRunParser.parseDevice(json, cleanIp)
        preferences.setLastConnectedIp(cleanIp)
        device
    }

    suspend fun fetchLineup(lineupUrl: String, fallbackIp: String? = null): List<Channel> = withContext(ioDispatcher) {
        val json = networkClient.get(lineupUrl)
        HdHomeRunParser.parseLineup(json, fallbackIp)
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
