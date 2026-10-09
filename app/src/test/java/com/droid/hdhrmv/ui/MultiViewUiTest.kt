package com.droid.hdhrmv.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.droid.hdhrmv.data.HdHomeRunNetworkClient
import com.droid.hdhrmv.data.HdHomeRunRepository
import com.droid.hdhrmv.model.Channel
import com.droid.hdhrmv.model.HdHomeRunDevice
import com.droid.hdhrmv.player.MultiViewPlayerController
import com.droid.hdhrmv.ui.components.ChannelPickerDialog
import com.droid.hdhrmv.ui.components.QuickStartChannelDialog
import com.droid.hdhrmv.ui.theme.HDHRMultiViewTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class MultiViewUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testDispatcher = StandardTestDispatcher()

    private class FakePlayerController : MultiViewPlayerController {
        val playedSlots = mutableMapOf<Int, String>()
        val mutedSlots = mutableMapOf<Int, Boolean>()

        override fun getPlayer(slotIndex: Int): ExoPlayer? = null
        override fun play(slotIndex: Int, streamUrl: String) {
            playedSlots[slotIndex] = streamUrl
        }
        override fun stop(slotIndex: Int) {
            playedSlots.remove(slotIndex)
        }
        override fun setMuted(slotIndex: Int, isMuted: Boolean) {
            mutedSlots[slotIndex] = isMuted
        }
        @Composable
        override fun VideoView(slotIndex: Int, modifier: Modifier) {}
        override fun release() {}
    }

    private class FakeNetworkClient : HdHomeRunNetworkClient {
        override suspend fun get(url: String): String = ""
    }

    private lateinit var viewModel: MultiViewViewModel
    private lateinit var playerController: FakePlayerController

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val repo = HdHomeRunRepository(FakeNetworkClient(), testDispatcher)
        viewModel = MultiViewViewModel(repo)
        playerController = FakePlayerController()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun mainScreen_displaysTitleAndFourSlots() = runTest {
        composeTestRule.setContent {
            HDHRMultiViewTheme {
                MainScreen(
                    viewModel = viewModel,
                    playerController = playerController
                )
            }
        }
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("HDHR MultiView").assertIsDisplayed()
        composeTestRule.onNodeWithText("Slot 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Slot 2").assertIsDisplayed()
        composeTestRule.onNodeWithText("Slot 3").assertIsDisplayed()
        composeTestRule.onNodeWithText("Slot 4").assertIsDisplayed()
    }

    @Test
    fun mainScreen_channelSelection_triggersPlayback() = runTest {
        val testChannel = Channel(
            guideNumber = "3.1",
            guideName = "KYW-TV",
            videoCodec = "MPEG2",
            audioCodec = "AC3",
            isHd = true,
            streamUrl = "http://10.1.0.4:5004/auto/v3.1"
        )

        composeTestRule.setContent {
            HDHRMultiViewTheme {
                MainScreen(
                    viewModel = viewModel,
                    playerController = playerController
                )
            }
        }

        viewModel.setChannelForSlot(0, testChannel)
        advanceUntilIdle()

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("3.1").assertIsDisplayed()
        composeTestRule.onNodeWithText("KYW-TV").assertIsDisplayed()
        assertEquals("http://10.1.0.4:5004/auto/v3.1", playerController.playedSlots[0])
    }

    @Test
    fun mainScreen_clickingEmptySlot_opensChannelPicker() = runTest {
        composeTestRule.setContent {
            HDHRMultiViewTheme {
                MainScreen(
                    viewModel = viewModel,
                    playerController = playerController
                )
            }
        }
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Slot 1").performClick()
        composeTestRule.waitForIdle()

        assertTrue(viewModel.uiState.value.isChannelPickerOpen)
        assertEquals(0, viewModel.uiState.value.channelPickerTargetSlot)
    }

    @Test
    fun channelPickerDialog_selectingChannel_triggersCallback() {
        val ch1 = Channel("3.1", "KYW-TV", "MPEG2", "AC3", true, "http://10.1.0.4:5004/auto/v3.1")
        val ch2 = Channel("10.1", "WCAU-TV", "MPEG2", "AC3", true, "http://10.1.0.4:5004/auto/v10.1")
        var selectedChannel: Channel? = null

        composeTestRule.setContent {
            HDHRMultiViewTheme {
                ChannelPickerDialog(
                    slotIndex = 0,
                    channels = listOf(ch1, ch2),
                    onChannelSelected = { selectedChannel = it },
                    onClearSlot = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("KYW-TV").performClick()
        composeTestRule.waitForIdle()

        assertEquals(ch1, selectedChannel)
    }

    @Test
    fun quickStartDialog_duplicateChannelNumbers_rendersWithoutCrashing() {
        // Two channels sharing identical guideNumber to simulate multi-tuner or simulcast lineup scans
        val ch1 = Channel("3.1", "KYW-TV Main", "MPEG2", "AC3", true, "http://10.1.0.4:5004/auto/v3.1")
        val ch2 = Channel("3.1", "KYW-TV Alt", "HEVC", "AC3", true, "http://10.1.0.4:5004/auto/v3.1-alt")

        composeTestRule.setContent {
            HDHRMultiViewTheme {
                QuickStartChannelDialog(
                    channels = listOf(ch1, ch2),
                    onLaunch = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Quick Start MultiView").assertIsDisplayed()
        composeTestRule.onNodeWithText("KYW-TV Main").assertIsDisplayed()
        composeTestRule.onNodeWithText("KYW-TV Alt").assertIsDisplayed()
    }

    @Test
    fun quickStartDialog_autoFillAndLaunch_triggersLaunchCallback() {
        val ch1 = Channel("3.1", "KYW-TV", "MPEG2", "AC3", true, "http://10.1.0.4:5004/auto/v3.1")
        val ch2 = Channel("10.1", "WCAU-TV", "MPEG2", "AC3", true, "http://10.1.0.4:5004/auto/v10.1")
        var launchedChannels: List<Channel>? = null

        composeTestRule.setContent {
            HDHRMultiViewTheme {
                QuickStartChannelDialog(
                    channels = listOf(ch1, ch2),
                    onLaunch = { launchedChannels = it },
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Auto-Fill Top 4").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Launch MultiView (2/4)").performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf(ch1, ch2), launchedChannels)
    }

    @Test
    fun rapidChannelChanges_synchronouslySyncsSlots() = runTest {
        val ch1 = Channel("3.1", "KYW-TV", "MPEG2", "AC3", true, "http://10.1.0.4:5004/auto/v3.1")
        val ch2 = Channel("10.1", "WCAU-TV", "MPEG2", "AC3", true, "http://10.1.0.4:5004/auto/v10.1")
        val ch3 = Channel("12.1", "WHYY", "MPEG2", "AC3", true, "http://10.1.0.4:5004/auto/v12.1")

        composeTestRule.setContent {
            HDHRMultiViewTheme {
                MainScreen(
                    viewModel = viewModel,
                    playerController = playerController
                )
            }
        }

        viewModel.setChannelForSlot(0, ch1)
        viewModel.setChannelForSlot(0, ch2)
        viewModel.setChannelForSlot(0, ch3)
        advanceUntilIdle()
        composeTestRule.waitForIdle()

        assertEquals("http://10.1.0.4:5004/auto/v12.1", playerController.playedSlots[0])
        assertEquals(ch3, viewModel.uiState.value.slots[0].channel)
    }
}
