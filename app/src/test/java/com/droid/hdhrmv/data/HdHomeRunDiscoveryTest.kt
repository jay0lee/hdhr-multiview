package com.droid.hdhrmv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.CRC32

class HdHomeRunDiscoveryTest {

    @Test
    fun buildDiscoverPacket_createsValidHdHomeRunFrame() {
        val packet = HdHomeRunDiscovery.buildDiscoverPacket()
        // Packet must be at least 14 bytes: 2 (type) + 2 (length) + 6 (payload) + 4 (crc)
        assertEquals(14, packet.size)

        // Type: 0x0002 (big endian)
        assertEquals(0x00.toByte(), packet[0])
        assertEquals(0x02.toByte(), packet[1])

        // Payload length: 6 (big endian)
        assertEquals(0x00.toByte(), packet[2])
        assertEquals(0x06.toByte(), packet[3])

        // Payload: Tag 0x01 (DEVICE_TYPE), len 4, value 0x00000001 (TUNER)
        assertEquals(0x01.toByte(), packet[4])
        assertEquals(0x04.toByte(), packet[5])
        assertEquals(0x00.toByte(), packet[6])
        assertEquals(0x00.toByte(), packet[7])
        assertEquals(0x00.toByte(), packet[8])
        assertEquals(0x01.toByte(), packet[9])

        // CRC32 check: CRC of first 10 bytes must match the trailing 4 bytes in little-endian
        val crc = CRC32().apply { update(packet, 0, 10) }.value
        val expectedCrc0 = (crc and 0xFF).toByte()
        val expectedCrc1 = ((crc shr 8) and 0xFF).toByte()
        val expectedCrc2 = ((crc shr 16) and 0xFF).toByte()
        val expectedCrc3 = ((crc shr 24) and 0xFF).toByte()

        assertEquals(expectedCrc0, packet[10])
        assertEquals(expectedCrc1, packet[11])
        assertEquals(expectedCrc2, packet[12])
        assertEquals(expectedCrc3, packet[13])
    }

    @Test
    fun crcMatchesSiliconDustConstantVector() {
        val packet = HdHomeRunDiscovery.buildDiscoverPacket()
        val hex = packet.joinToString("") { "%02x".format(it) }
        // Verified against Python implementation of SiliconDust protocol
        assertEquals("00020006010400000001393077e7", hex)
    }
}
