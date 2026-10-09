package com.droid.hdhrmv.data

import com.droid.hdhrmv.model.Channel
import com.droid.hdhrmv.model.DiscoveredCloudDevice
import com.droid.hdhrmv.model.HdHomeRunDevice
import com.droid.hdhrmv.model.TunerStatus
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

object HdHomeRunParser {

    private val moshi: Moshi = Moshi.Builder().build()

    @Suppress("UNCHECKED_CAST")
    fun parseDevice(json: String, fallbackIp: String): HdHomeRunDevice {
        val adapter = moshi.adapter(Map::class.java)
        val map = adapter.fromJson(json) as? Map<String, Any?> ?: emptyMap()

        val deviceId = map["DeviceID"] as? String ?: ""
        val friendlyName = map["FriendlyName"] as? String ?: "HDHomeRun"
        val modelNumber = map["ModelNumber"] as? String ?: ""
        val baseUrl = map["BaseURL"] as? String ?: "http://$fallbackIp"
        val lineupUrl = map["LineupURL"] as? String ?: "$baseUrl/lineup.json"
        val tunerCount = (map["TunerCount"] as? Number)?.toInt() ?: 2

        return HdHomeRunDevice(
            deviceId = deviceId,
            friendlyName = friendlyName,
            modelNumber = modelNumber,
            ipAddress = fallbackIp,
            baseUrl = baseUrl,
            lineupUrl = lineupUrl,
            tunerCount = tunerCount
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun parseLineup(json: String, fallbackIp: String? = null): List<Channel> {
        val type = Types.newParameterizedType(List::class.java, Map::class.java)
        val adapter = moshi.adapter<List<Map<String, Any?>>>(type)
        val list = adapter.fromJson(json) ?: emptyList()

        return list.mapNotNull { item ->
            val guideNumber = item["GuideNumber"] as? String ?: return@mapNotNull null
            val guideName = item["GuideName"] as? String ?: guideNumber
            val videoCodec = item["VideoCodec"] as? String
            val audioCodec = item["AudioCodec"] as? String
            val isHd = ((item["HD"] as? Number)?.toInt() ?: 0) == 1
            val rawUrl = item["URL"] as? String ?: ""
            val favorite = ((item["Favorite"] as? Number)?.toInt() ?: 0) == 1
            val signalStrength = (item["SignalStrength"] as? Number)?.toInt()
            val signalQuality = (item["SignalQuality"] as? Number)?.toInt()

            val streamUrl = resolveStreamUrl(rawUrl, guideNumber, fallbackIp)

            Channel(
                guideNumber = guideNumber,
                guideName = guideName,
                videoCodec = videoCodec,
                audioCodec = audioCodec,
                isHd = isHd,
                streamUrl = streamUrl,
                favorite = favorite,
                signalStrength = signalStrength,
                signalQuality = signalQuality
            )
        }
    }

    fun resolveStreamUrl(rawUrl: String, guideNumber: String, fallbackIp: String?): String {
        val cleanFallback = fallbackIp?.removePrefix("http://")?.substringBefore(":")?.substringBefore("/")
        if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            if (rawUrl.contains(".local") && !cleanFallback.isNullOrBlank()) {
                val pathAndQuery = rawUrl.substringAfter(".local:5004", rawUrl.substringAfter(".local", "/auto/v$guideNumber"))
                return "http://$cleanFallback:5004$pathAndQuery"
            }
            return rawUrl
        }
        if (rawUrl.startsWith("/")) {
            return if (!cleanFallback.isNullOrBlank()) "http://$cleanFallback:5004$rawUrl" else rawUrl
        }
        return if (!cleanFallback.isNullOrBlank()) "http://$cleanFallback:5004/auto/v$guideNumber" else ""
    }

    @Suppress("UNCHECKED_CAST")
    fun parseTunerStatus(json: String): List<TunerStatus> {
        val type = Types.newParameterizedType(List::class.java, Map::class.java)
        val adapter = moshi.adapter<List<Map<String, Any?>>>(type)
        val list = adapter.fromJson(json) ?: emptyList()

        return list.map { item ->
            val resource = item["Resource"] as? String ?: "tuner"
            val vctNumber = item["VctNumber"] as? String
            val vctName = item["VctName"] as? String
            val targetIp = item["TargetIP"] as? String

            TunerStatus(
                resource = resource,
                vctNumber = vctNumber,
                vctName = vctName,
                targetIp = targetIp
            )
        }
    }

    fun calculateFreeTuners(tuners: List<TunerStatus>, totalTuners: Int): Int {
        if (tuners.isEmpty()) return totalTuners
        val busyCount = tuners.count { !it.isFree }
        return (totalTuners - busyCount).coerceAtLeast(0)
    }

    @Suppress("UNCHECKED_CAST")
    fun parseCloudDiscover(json: String): List<DiscoveredCloudDevice> {
        val type = Types.newParameterizedType(List::class.java, Map::class.java)
        val adapter = moshi.adapter<List<Map<String, Any?>>>(type)
        val list = adapter.fromJson(json) ?: emptyList()

        return list.mapNotNull { item ->
            val deviceId = item["DeviceID"] as? String ?: return@mapNotNull null
            val localIp = item["LocalIP"] as? String ?: return@mapNotNull null
            val baseUrl = item["BaseURL"] as? String ?: "http://$localIp"
            val discoverUrl = item["DiscoverURL"] as? String ?: "$baseUrl/discover.json"
            val lineupUrl = item["LineupURL"] as? String ?: "$baseUrl/lineup.json"

            DiscoveredCloudDevice(
                deviceId = deviceId,
                localIp = localIp,
                baseUrl = baseUrl,
                discoverUrl = discoverUrl,
                lineupUrl = lineupUrl
            )
        }
    }
}
