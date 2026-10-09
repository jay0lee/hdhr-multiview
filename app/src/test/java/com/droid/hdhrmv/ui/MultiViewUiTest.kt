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
}
