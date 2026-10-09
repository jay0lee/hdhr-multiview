package com.droid.hdhrmv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HdHomeRunParserTest {

    private val sampleDiscoverJson = """
        {
          "FriendlyName": "HDHomeRun FLEX 4K",
          "ModelNumber": "HDFX-4K",
          "FirmwareName": "hdhomerun_dvr_atsc3",
          "FirmwareVersion": "20260326",
          "DeviceID": "10A1D769",
          "DeviceAuth": "A0hj9OmqbLgVPgpjgTf3OkQk",
          "BaseURL": "http://10.1.0.4",
          "LineupURL": "http://10.1.0.4/lineup.json",
          "TunerCount": 4
        }
    """.trimIndent()

    private val sampleLineupJson = """
        [
          {
            "GuideNumber": "3.1",
            "GuideName": "KYW-TV",
            "VideoCodec": "MPEG2",
            "AudioCodec": "AC3",
            "HD": 1,
            "Favorite": 1,
            "URL": "http://10.1.0.4:5004/auto/v3.1"
          },
          {
            "GuideNumber": "10.1",
            "GuideName": "WCAU-TV",
            "VideoCodec": "MPEG2",
            "AudioCodec": "AC3",
            "HD": 1,
            "URL": "http://10.1.0.4:5004/auto/v10.1"
          }
        ]
    """.trimIndent()

    private val sampleStatusAllFreeJson = """
        [
          {"Resource": "tuner0"},
          {"Resource": "tuner1"},
          {"Resource": "tuner2"},
          {"Resource": "tuner3"}
        ]
    """.trimIndent()

    private val sampleStatusTwoInUseJson = """
        [
          {
            "Resource": "tuner0",
            "VctNumber": "3.1",
            "VctName": "KYW-TV",
            "TargetIP": "10.1.0.12"
          },
          {
            "Resource": "tuner1"
          },
          {
            "Resource": "tuner2",
            "VctNumber": "10.1",
            "TargetIP": "10.1.0.15"
          },
          {
            "Resource": "tuner3"
          }
        ]
    """.trimIndent()

    @Test
    fun parseDeviceJson_parsesAllFieldsCorrectly() {
        val device = HdHomeRunParser.parseDevice(sampleDiscoverJson, "10.1.0.4")
        assertEquals("10A1D769", device.deviceId)
        assertEquals("HDHomeRun FLEX 4K", device.friendlyName)
        assertEquals("HDFX-4K", device.modelNumber)
        assertEquals("10.1.0.4", device.ipAddress)
        assertEquals(4, device.tunerCount)
        assertEquals("http://10.1.0.4/lineup.json", device.lineupUrl)
    }

    @Test
    fun parseLineupJson_parsesChannels() {
        val channels = HdHomeRunParser.parseLineup(sampleLineupJson)
        assertEquals(2, channels.size)

        val ch1 = channels[0]
        assertEquals("3.1", ch1.guideNumber)
        assertEquals("KYW-TV", ch1.guideName)
        assertEquals("MPEG2", ch1.videoCodec)
        assertEquals("AC3", ch1.audioCodec)
        assertTrue(ch1.isHd)
        assertEquals("http://10.1.0.4:5004/auto/v3.1", ch1.streamUrl)

        val ch2 = channels[1]
        assertEquals("10.1", ch2.guideNumber)
        assertEquals("WCAU-TV", ch2.guideName)
    }

    @Test
    fun parseStatusJson_calculatesFreeTunersCorrectly() {
        val tunersAllFree = HdHomeRunParser.parseTunerStatus(sampleStatusAllFreeJson)
        assertEquals(4, tunersAllFree.size)
        assertEquals(4, tunersAllFree.count { it.isFree })
        assertEquals(4, HdHomeRunParser.calculateFreeTuners(tunersAllFree, 4))

        val tunersPartial = HdHomeRunParser.parseTunerStatus(sampleStatusTwoInUseJson)
        assertEquals(4, tunersPartial.size)
        assertEquals(2, tunersPartial.count { it.isFree })
        assertEquals(2, HdHomeRunParser.calculateFreeTuners(tunersPartial, 4))
    }

    @Test
    fun parseCloudDiscoverJson_extractsLocalIps() {
        val cloudJson = """
            [
              {
                "DeviceID": "10A1D769",
                "LocalIP": "10.1.0.4",
                "BaseURL": "http://10.1.0.4",
                "DiscoverURL": "http://10.1.0.4/discover.json",
                "LineupURL": "http://10.1.0.4/lineup.json"
              }
            ]
        """.trimIndent()
        val devices = HdHomeRunParser.parseCloudDiscover(cloudJson)
        assertEquals(1, devices.size)
        assertEquals("10A1D769", devices[0].deviceId)
        assertEquals("10.1.0.4", devices[0].localIp)
        assertEquals("http://10.1.0.4/discover.json", devices[0].discoverUrl)
    }
}
