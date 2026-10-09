package com.droid.hdhrmv.data

import com.droid.hdhrmv.model.Channel
import com.droid.hdhrmv.model.HdHomeRunDevice
import com.droid.hdhrmv.model.TunerStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HdHomeRunRepositoryTest {

    private class FakeNetworkClient(
        private val responses: Map<String, String> = emptyMap()
    ) : HdHomeRunNetworkClient {
        override suspend fun get(url: String): String {
            return responses[url] ?: throw IllegalArgumentException("No response for $url")
        }
    }

    private val sampleDiscoverJson = """
        {
          "FriendlyName": "HDHomeRun FLEX 4K",
          "ModelNumber": "HDFX-4K",
          "DeviceID": "10A1D769",
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

    private val sampleStatusJson = """
        [
          {"Resource": "tuner0"},
          {"Resource": "tuner1"},
          {"Resource": "tuner2", "VctNumber": "3.1", "TargetIP": "10.1.0.50"},
          {"Resource": "tuner3"}
        ]
    """.trimIndent()

    @Test
    fun getDeviceDetails_fetchesAndParsesDevice() = runTest {
        val fakeClient = FakeNetworkClient(
            mapOf("http://10.1.0.4/discover.json" to sampleDiscoverJson)
        )
        val repository = HdHomeRunRepository(fakeClient)
        val device = repository.fetchDevice("10.1.0.4")

        assertNotNull(device)
        assertEquals("10A1D769", device.deviceId)
        assertEquals(4, device.tunerCount)
    }

    @Test
    fun getLineup_fetchesAndReturnsChannels() = runTest {
        val fakeClient = FakeNetworkClient(
            mapOf("http://10.1.0.4/lineup.json" to sampleLineupJson)
        )
        val repository = HdHomeRunRepository(fakeClient)
        val channels = repository.fetchLineup("http://10.1.0.4/lineup.json")

        assertEquals(2, channels.size)
        assertEquals("KYW-TV", channels[0].guideName)
        assertEquals("http://10.1.0.4:5004/auto/v3.1", channels[0].streamUrl)
    }

    @Test
    fun getTunerStatus_calculatesFreeTunersCorrectly() = runTest {
        val fakeClient = FakeNetworkClient(
            mapOf("http://10.1.0.4/status.json" to sampleStatusJson)
        )
        val repository = HdHomeRunRepository(fakeClient)
        val tuners = repository.fetchTunerStatus("http://10.1.0.4")

        assertEquals(4, tuners.size)
        val freeCount = HdHomeRunParser.calculateFreeTuners(tuners, 4)
        assertEquals(3, freeCount)
    }

    @Test
    fun discoverDevices_usesCachedIpFromPreferencesImmediately() = runTest {
        val fakeClient = FakeNetworkClient(
            mapOf(
                "http://10.1.0.4/discover.json" to sampleDiscoverJson,
                "http://10.1.0.4/status.json" to sampleStatusJson
            )
        )
        val prefs = InMemoryDevicePreferences(cachedIp = "10.1.0.4")
        val repository = HdHomeRunRepository(
            networkClient = fakeClient,
            preferences = prefs
        )
        val devices = repository.discoverDevices()

        assertEquals(1, devices.size)
        assertEquals("10A1D769", devices[0].deviceId)
        assertEquals("10.1.0.4", devices[0].ipAddress)
    }
}
