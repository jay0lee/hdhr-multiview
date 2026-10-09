package com.droid.hdhrmv.data

import android.content.Context
import android.content.SharedPreferences

interface DevicePreferences {
    fun getLastConnectedIp(): String?
    fun setLastConnectedIp(ip: String)
    fun getDecoderMode(): String?
    fun setDecoderMode(mode: String)
}

class SharedPrefsDevicePreferences(context: Context) : DevicePreferences {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("hdhr_multiview_prefs", Context.MODE_PRIVATE)

    override fun getLastConnectedIp(): String? {
        val ip = prefs.getString("last_connected_device_ip", null)
        return if (!ip.isNullOrBlank() && ip != "127.0.0.1") ip else null
    }

    override fun setLastConnectedIp(ip: String) {
        if (ip.isNotBlank() && ip != "127.0.0.1") {
            prefs.edit().putString("last_connected_device_ip", ip).apply()
        }
    }

    override fun getDecoderMode(): String? {
        return prefs.getString("decoder_allocation_mode", null)
    }

    override fun setDecoderMode(mode: String) {
        prefs.edit().putString("decoder_allocation_mode", mode).apply()
    }
}

class InMemoryDevicePreferences(
    private var cachedIp: String? = null,
    private var cachedDecoderMode: String? = null
) : DevicePreferences {
    override fun getLastConnectedIp(): String? = cachedIp

    override fun setLastConnectedIp(ip: String) {
        if (ip.isNotBlank() && ip != "127.0.0.1") {
            cachedIp = ip
        }
    }

    override fun getDecoderMode(): String? = cachedDecoderMode

    override fun setDecoderMode(mode: String) {
        cachedDecoderMode = mode
    }
}
