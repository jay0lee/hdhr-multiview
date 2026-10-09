package com.droid.hdhrmv.ui

import com.droid.hdhrmv.data.HdHomeRunNetworkClient
import com.droid.hdhrmv.data.HdHomeRunRepository
import com.droid.hdhrmv.model.Channel
import com.droid.hdhrmv.model.HdHomeRunDevice
import com.droid.hdhrmv.model.MultiviewMode
import com.droid.hdhrmv.model.TunerStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MultiViewViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeNetworkClient : HdHomeRunNetworkClient {
        override suspend fun get(url: String): String {
            return when {
                url.endsWith("/discover.json") -> """
                    {
                      "FriendlyName": "HDHomeRun FLEX 4K",
                      "ModelNumber": "HDFX-4K",
                      "DeviceID": "10A1D769",
                      "BaseURL": "http://10.1.0.4",
                      "LineupURL": "http://10.1.0.4/lineup.json",
                      "TunerCount": 4
                    }
                """.trimIndent()
                url.endsWith("/lineup.json") -> """
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
                url.endsWith("/status.json") -> """
                    [
                      {"Resource": "tuner0"},
                      {"Resource": "tuner1"},
                      {"Resource": "tuner2"},
                      {"Resource": "tuner3"}
                    ]
                """.trimIndent()
                url.contains("ipv4-api.hdhomerun.com") -> """
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
                else -> throw IllegalArgumentException("Unhandled url: $url")
            }
        }
    }

    private lateinit var repository: HdHomeRunRepository
    private lateinit var viewModel: MultiViewViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = HdHomeRunRepository(FakeNetworkClient(), testDispatcher)
        viewModel = MultiViewViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_has4SlotsWithSlot0Focused() {
        val state = viewModel.uiState.value
        assertEquals(4, state.slots.size)
        assertEquals(0, state.focusedSlotIndex)
        assertEquals(MultiviewMode.GRID_4, state.multiviewMode)
        assertTrue(state.slots[0].isFocused)
        assertFalse(state.slots[0].isMuted)
    }

    @Test
    fun selectDevice_loadsLineupAndTuners() = runTest {
        val sampleDevice = HdHomeRunDevice(
            deviceId = "10A1D769",
            friendlyName = "HDHomeRun FLEX 4K",
            modelNumber = "HDFX-4K",
            ipAddress = "10.1.0.4",
            baseUrl = "http://10.1.0.4",
            lineupUrl = "http://10.1.0.4/lineup.json",
            tunerCount = 4
        )

        viewModel.selectDevice(sampleDevice)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(sampleDevice, state.selectedDevice)
        assertEquals(2, state.channels.size)
        assertEquals(4, state.freeTunerCount)
        assertEquals(4, state.totalTunerCount)
    }

    @Test
    fun assignChannelToSlot_updatesSlotAndState() = runTest {
        val ch = Channel("3.1", "KYW-TV", "MPEG2", "AC3", true, "http://10.1.0.4:5004/auto/v3.1")
        viewModel.setChannelForSlot(slotIndex = 0, channel = ch)

        val state = viewModel.uiState.value
        assertEquals(ch, state.slots[0].channel)
    }

    @Test
    fun setFocusedSlot_updatesFocusAndRoutesAudio() = runTest {
        viewModel.setFocusedSlot(slotIndex = 1)

        val state = viewModel.uiState.value
        assertEquals(1, state.focusedSlotIndex)
        assertTrue(state.slots[1].isFocused)
        assertFalse(state.slots[1].isMuted)
        assertTrue(state.slots[0].isMuted)
    }

    @Test
    fun setMultiviewMode_updatesMode() = runTest {
        viewModel.setMultiviewMode(MultiviewMode.FOCUS_1_PLUS_3)
        assertEquals(MultiviewMode.FOCUS_1_PLUS_3, viewModel.uiState.value.multiviewMode)

        viewModel.setMultiviewMode(MultiviewMode.PIP)
        assertEquals(MultiviewMode.PIP, viewModel.uiState.value.multiviewMode)
    }

    @Test
    fun channelPicker_opensAndCloses() = runTest {
        viewModel.openChannelPicker(slotIndex = 2)
        assertEquals(2, viewModel.uiState.value.channelPickerTargetSlot)
        assertTrue(viewModel.uiState.value.isChannelPickerOpen)

        viewModel.closeChannelPicker()
        assertNull(viewModel.uiState.value.channelPickerTargetSlot)
        assertFalse(viewModel.uiState.value.isChannelPickerOpen)
    }
}
