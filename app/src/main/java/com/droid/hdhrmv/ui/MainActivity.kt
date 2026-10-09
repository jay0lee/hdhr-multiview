package com.droid.hdhrmv.ui

import android.media.MediaCodecList
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.droid.hdhrmv.data.DefaultHdHomeRunNetworkClient
import com.droid.hdhrmv.data.HdHomeRunRepository
import com.droid.hdhrmv.model.MultiviewMode
import com.droid.hdhrmv.player.MultiViewPlayerController
import com.droid.hdhrmv.player.VlcMultiViewPlayerController
import com.droid.hdhrmv.ui.theme.HDHRMultiViewTheme

import android.app.UiModeManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.view.KeyEvent
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "HDHR_MainActivity"
    }

    private lateinit var playerController: MultiViewPlayerController
    private lateinit var viewModel: MultiViewViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
        enableImmersiveFullscreen()

        // Google TV / Android TV detection: lock to landscape for TV, allow adaptive rotation for phones/tablets
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTv = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        if (isTv) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }

        // Diagnostic: Log all available broadcast-relevant audio and video decoders
        try {
            val mcl = MediaCodecList(MediaCodecList.ALL_CODECS)
            for (codec in mcl.codecInfos) {
                if (!codec.isEncoder) {
                    val types = codec.supportedTypes.filter {
                        it.contains("mpeg2", true) ||
                        it.contains("mp2v", true) ||
                        it.contains("ac3", true) ||
                        it.contains("eac3", true) ||
                        it.contains("hevc", true)
                    }
                    if (types.isNotEmpty()) {
                        Log.i(TAG, "Available Decoder: ${codec.name} supports $types (softwareOnly=${codec.isSoftwareOnly})")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Codec diagnostics error", e)
        }

        val networkClient = DefaultHdHomeRunNetworkClient()
        val repository = HdHomeRunRepository(networkClient)

        viewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MultiViewViewModel(repository) as T
                }
            }
        )[MultiViewViewModel::class.java]

        playerController = VlcMultiViewPlayerController(this)

        setContent {
            HDHRMultiViewTheme {
                MainScreen(
                    viewModel = viewModel,
                    playerController = playerController
                )
            }
        }
    }

    private fun enableImmersiveFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableImmersiveFullscreen()
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        enableImmersiveFullscreen()
    }

    override fun onStop() {
        super.onStop()
        for (i in 0..3) {
            playerController.pause(i)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playerController.release()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val currentState = viewModel.uiState.value
        val focusedSlot = currentState.focusedSlotIndex

        // If an overlay dialog is open, let dialog handle navigation keys
        if (currentState.isChannelPickerOpen || currentState.slotActionTargetSlot != null) {
            return super.onKeyDown(keyCode, event)
        }

        // When Top Bar menu is focused
        if (currentState.isTopBarFocused) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    viewModel.setTopBarFocused(false)
                    return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    viewModel.setTopBarFocused(false)
                    return true
                }
                else -> {
                    // Let Compose handle DPAD_LEFT, DPAD_RIGHT, DPAD_CENTER, ENTER on Top Bar controls
                    return super.onKeyDown(keyCode, event)
                }
            }
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                when (currentState.multiviewMode) {
                    MultiviewMode.FULLSCREEN -> {
                        viewModel.selectPreviousSlot()
                        return true
                    }
                    MultiviewMode.GRID_4 -> {
                        when (focusedSlot) {
                            1 -> viewModel.setFocusedSlot(0)
                            3 -> viewModel.setFocusedSlot(2)
                            else -> {}
                        }
                        return true
                    }
                    MultiviewMode.CAROUSEL -> {
                        viewModel.selectPreviousSlot()
                        return true
                    }
                    else -> {
                        viewModel.selectPreviousSlot()
                        return true
                    }
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                when (currentState.multiviewMode) {
                    MultiviewMode.FULLSCREEN -> {
                        viewModel.selectNextSlot()
                        return true
                    }
                    MultiviewMode.GRID_4 -> {
                        when (focusedSlot) {
                            0 -> viewModel.setFocusedSlot(1)
                            2 -> viewModel.setFocusedSlot(3)
                            else -> {}
                        }
                        return true
                    }
                    MultiviewMode.CAROUSEL -> {
                        viewModel.selectNextSlot()
                        return true
                    }
                    else -> {
                        viewModel.selectNextSlot()
                        return true
                    }
                }
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                when (currentState.multiviewMode) {
                    MultiviewMode.GRID_4 -> {
                        when (focusedSlot) {
                            2 -> viewModel.setFocusedSlot(0)
                            3 -> viewModel.setFocusedSlot(1)
                            0, 1 -> {
                                // Top row: move directly to Top Bar menu!
                                viewModel.setTopBarFocused(true)
                            }
                            else -> viewModel.setTopBarFocused(true)
                        }
                        return true
                    }
                    MultiviewMode.FULLSCREEN,
                    MultiviewMode.CAROUSEL,
                    MultiviewMode.PIP,
                    MultiviewMode.FOCUS_1_PLUS_3 -> {
                        // Move directly to Top Bar menu!
                        viewModel.setTopBarFocused(true)
                        return true
                    }
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                when (currentState.multiviewMode) {
                    MultiviewMode.GRID_4 -> {
                        when (focusedSlot) {
                            0 -> viewModel.setFocusedSlot(2)
                            1 -> viewModel.setFocusedSlot(3)
                            // Bottom row slots (2, 3): stay on bottom row
                            else -> {}
                        }
                        return true
                    }
                    else -> return true
                }
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (event?.repeatCount == 0) {
                    event.startTracking()
                }
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_UP -> {
                viewModel.nextChannel(focusedSlot)
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                viewModel.previousChannel(focusedSlot)
                return true
            }
            KeyEvent.KEYCODE_GUIDE,
            KeyEvent.KEYCODE_PROG_RED -> {
                viewModel.openChannelPicker(focusedSlot)
                return true
            }
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_INFO -> {
                viewModel.openSlotActions(focusedSlot)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> {
                playerController.togglePause(focusedSlot)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                playerController.resume(focusedSlot)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                playerController.pause(focusedSlot)
                return true
            }
            KeyEvent.KEYCODE_VOLUME_MUTE -> {
                viewModel.toggleSlotMute(focusedSlot)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        val currentState = viewModel.uiState.value
        val focusedSlot = currentState.focusedSlotIndex

        if (currentState.isChannelPickerOpen || currentState.slotActionTargetSlot != null || currentState.isTopBarFocused) {
            return super.onKeyLongPress(keyCode, event)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                // Long-press toggles Fullscreen directly
                if (currentState.multiviewMode == MultiviewMode.FULLSCREEN) {
                    viewModel.setMultiviewMode(MultiviewMode.GRID_4)
                } else {
                    viewModel.setFocusedSlot(focusedSlot)
                    viewModel.setMultiviewMode(MultiviewMode.FULLSCREEN)
                }
                return true
            }
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val currentState = viewModel.uiState.value
        val focusedSlot = currentState.focusedSlotIndex

        if (currentState.isChannelPickerOpen || currentState.slotActionTargetSlot != null || currentState.isTopBarFocused) {
            return super.onKeyUp(keyCode, event)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (event?.isTracking == true && !event.isCanceled) {
                    val currentSlot = currentState.slots.getOrNull(focusedSlot)
                    if (currentSlot?.channel == null) {
                        // Empty slot click: open channel lineup immediately
                        viewModel.openChannelPicker(focusedSlot)
                    } else {
                        // Playing slot click: open Slot Action Dialog (Change Channel is focused as item 1)
                        viewModel.openSlotActions(focusedSlot)
                    }
                    return true
                }
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }
}
