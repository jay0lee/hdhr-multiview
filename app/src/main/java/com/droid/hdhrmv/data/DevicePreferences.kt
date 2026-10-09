package com.droid.hdhrmv.data

import android.content.Context
import android.content.SharedPreferences

interface DevicePreferences {
    fun getLastConnectedIp(): String?
    fun setLastConnectedIp(ip: String)
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
}

class InMemoryDevicePreferences(
    private var cachedIp: String? = null
) : DevicePreferences {
    override fun getLastConnectedIp(): String? = cachedIp

    override fun setLastConnectedIp(ip: String) {
        if (ip.isNotBlank() && ip != "127.0.0.1") {
            cachedIp = ip
        }
    }
}
