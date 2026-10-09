package com.droid.hdhrmv.data

import com.droid.hdhrmv.player.DecoderAllocationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DevicePreferencesTest {

    @Test
    fun inMemoryDevicePreferences_storesAndRetrievesDecoderMode() {
        val prefs = InMemoryDevicePreferences()
        assertNull(prefs.getDecoderMode())

        prefs.setDecoderMode(DecoderAllocationMode.HYBRID_2_HW.name)
        assertEquals(DecoderAllocationMode.HYBRID_2_HW.name, prefs.getDecoderMode())

        prefs.setDecoderMode(DecoderAllocationMode.ALL_SW.name)
        assertEquals(DecoderAllocationMode.ALL_SW.name, prefs.getDecoderMode())
    }

    @Test
    fun inMemoryDevicePreferences_ignoresLoopbackIp() {
        val prefs = InMemoryDevicePreferences()
        prefs.setLastConnectedIp("127.0.0.1")
        assertNull(prefs.getLastConnectedIp())

        prefs.setLastConnectedIp("192.168.1.100")
        assertEquals("192.168.1.100", prefs.getLastConnectedIp())
    }

    @Test
    fun decoderAllocationMode_enumProperties() {
        assertEquals("Hybrid (2 HW + 2 SW)", DecoderAllocationMode.HYBRID_2_HW.title)
        assertEquals("Single HW (1 HW + 3 SW)", DecoderAllocationMode.SINGLE_HW.title)
        assertEquals("All Hardware (4 HW)", DecoderAllocationMode.ALL_HW.title)
        assertEquals("All Software (4 SW)", DecoderAllocationMode.ALL_SW.title)
    }
}
