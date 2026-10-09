package com.droid.hdhrmv.model

data class HdHomeRunDevice(
    val deviceId: String,
    val friendlyName: String,
    val modelNumber: String,
    val ipAddress: String,
    val baseUrl: String,
    val lineupUrl: String,
    val tunerCount: Int,
    val freeTunerCount: Int = tunerCount
)

data class DiscoveredCloudDevice(
    val deviceId: String,
    val localIp: String,
    val baseUrl: String,
    val discoverUrl: String,
    val lineupUrl: String
)
