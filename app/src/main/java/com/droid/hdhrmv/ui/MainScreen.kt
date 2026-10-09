package com.droid.hdhrmv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droid.hdhrmv.model.MultiviewMode
import com.droid.hdhrmv.player.MultiViewPlayerController
import com.droid.hdhrmv.ui.components.ChannelPickerDialog
import com.droid.hdhrmv.ui.components.FloatingTopBar
import com.droid.hdhrmv.ui.components.ManualIpDialog
import com.droid.hdhrmv.ui.components.MultiviewContainer
import com.droid.hdhrmv.ui.components.SideControlPanel
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import com.droid.hdhrmv.ui.components.CustomEngineDialog
import com.droid.hdhrmv.ui.components.SlotActionDialog
import com.droid.hdhrmv.ui.theme.DarkBackground
import com.droid.hdhrmv.ui.theme.PrimaryCyan
import kotlinx.coroutines.delay

@Composable
fun MainScreen(
    viewModel: MultiViewViewModel,
    playerController: MultiViewPlayerController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isTv = remember {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showManualIpDialog by remember { mutableStateOf(false) }
    var showCustomEngineDialog by remember { mutableStateOf(false) }
    var isSidePanelCollapsed by remember { mutableStateOf(false) }
    var showOverlaidHUD by remember { mutableStateOf(true) }

    // Intercept Back button on Google TV and mobile navigation
    val isOverlayOpen = state.isChannelPickerOpen || showManualIpDialog || showCustomEngineDialog || state.slotActionTargetSlot != null
    BackHandler(enabled = isOverlayOpen || state.multiviewMode == MultiviewMode.FULLSCREEN || showOverlaidHUD) {
        when {
            state.isChannelPickerOpen -> viewModel.closeChannelPicker()
            showManualIpDialog -> showManualIpDialog = false
            showCustomEngineDialog -> showCustomEngineDialog = false
            state.slotActionTargetSlot != null -> viewModel.closeSlotActions()
            state.multiviewMode == MultiviewMode.FULLSCREEN -> viewModel.setMultiviewMode(MultiviewMode.GRID_4)
            showOverlaidHUD -> showOverlaidHUD = false
        }
    }

    // Auto-hide overlaid HUD after 6 seconds of inactivity
    LaunchedEffect(showOverlaidHUD) {
        if (showOverlaidHUD) {
            delay(6000)
            showOverlaidHUD = false
        }
    }

    // Sync state changes with playerController
    LaunchedEffect(state.slots) {
        state.slots.forEach { slot ->
            val channel = slot.channel
            if (channel != null) {
                playerController.playChannel(slot.slotIndex, channel)
            } else {
                playerController.stop(slot.slotIndex)
            }
            playerController.setMuted(slot.slotIndex, slot.isMuted)
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        val targetAspect = 16f / 9f
        val currentAspect = maxWidth / maxHeight
        // Wide display (e.g. phones in landscape 19.5:9 or 20:9) has extra horizontal room
        val hasExtraHorizontalSpace = currentAspect > 1.85f
        val showSidePanel = hasExtraHorizontalSpace && !isSidePanelCollapsed

        val remainingSideWidth = (maxWidth - (maxHeight * targetAspect)).coerceAtLeast(140.dp)

        Row(modifier = Modifier.fillMaxSize()) {
            // Main Video Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable {
                        // Tapping toggles HUD
                        showOverlaidHUD = !showOverlaidHUD
                    },
                contentAlignment = Alignment.Center
            ) {
                MultiviewContainer(
                    mode = state.multiviewMode,
                    slots = state.slots,
                    focusedSlotIndex = state.focusedSlotIndex,
                    renderVideo = { slotIndex ->
                        playerController.VideoView(slotIndex, Modifier.fillMaxSize())
                    },
                    onSlotClick = { slotIndex ->
                        if (state.multiviewMode == MultiviewMode.FULLSCREEN) {
                            // In fullscreen mode, clicking returns to 4-quadrant grid
                            viewModel.setMultiviewMode(MultiviewMode.GRID_4)
                        } else {
                            val slot = state.slots.getOrNull(slotIndex)
                            if (slot?.channel == null) {
                                viewModel.openChannelPicker(slotIndex)
                            } else if (state.focusedSlotIndex == slotIndex) {
                                // Already focused slot: expand to Fullscreen
                                viewModel.setMultiviewMode(MultiviewMode.FULLSCREEN)
                            } else {
                                // Focus slot and route audio to it
                                viewModel.setFocusedSlot(slotIndex)
                            }
                        }
                    },
                    onSlotLongClick = { slotIndex ->
                        viewModel.openSlotActions(slotIndex)
                    },
                    onChannelClick = { slotIndex ->
                        viewModel.setFocusedSlot(slotIndex)
                        viewModel.openChannelPicker(slotIndex)
                    },
                    onMuteToggle = { slotIndex ->
                        viewModel.toggleSlotMute(slotIndex)
                    },
                    onFocusClick = { slotIndex ->
                        viewModel.setFocusedSlot(slotIndex)
                        if (state.multiviewMode != MultiviewMode.FULLSCREEN) {
                            viewModel.setMultiviewMode(MultiviewMode.FULLSCREEN)
                        } else {
                            viewModel.setMultiviewMode(MultiviewMode.GRID_4)
                        }
                    },
                    onSlotSelected = { slotIndex ->
                        viewModel.setFocusedSlot(slotIndex)
                    }
                )
            }

            // Side Control Panel utilizing remaining space on wide displays
            if (showSidePanel) {
                SideControlPanel(
                    device = state.selectedDevice,
                    freeTuners = state.freeTunerCount,
                    totalTuners = state.totalTunerCount,
                    currentMode = state.multiviewMode,
                    isDiscovering = state.isDiscovering,
                    onModeSelected = { mode -> viewModel.setMultiviewMode(mode) },
                    onRefresh = {
                        viewModel.discoverDevices()
                        viewModel.refreshTunerStatus()
                    },
                    onManualIpClick = { showManualIpDialog = true },
                    onToggleCollapse = { isSidePanelCollapsed = true },
                    onOpenEngineSettings = { showCustomEngineDialog = true },
                    modifier = Modifier.width(remainingSideWidth)
                )
            }
        }

        // Overlaid HUD when side panel is hidden or on standard 16:9 display
        if (!showSidePanel) {
            AnimatedVisibility(
                visible = showOverlaidHUD,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                FloatingTopBar(
                    device = state.selectedDevice,
                    freeTuners = state.freeTunerCount,
                    totalTuners = state.totalTunerCount,
                    currentMode = state.multiviewMode,
                    isDiscovering = state.isDiscovering,
                    onModeSelected = { mode -> viewModel.setMultiviewMode(mode) },
                    onRefresh = {
                        viewModel.discoverDevices()
                        viewModel.refreshTunerStatus()
                    },
                    onManualIpClick = { showManualIpDialog = true },
                    canShowSidePanel = hasExtraHorizontalSpace,
                    onToggleSidePanel = { isSidePanelCollapsed = false },
                    onOpenEngineSettings = { showCustomEngineDialog = true }
                )
            }
        }

        // Quick HUD toggle pill when controls are hidden
        if (!showSidePanel && !showOverlaidHUD) {
            val safePadding = if (isTv) 28.dp else 14.dp
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(safePadding),
                shape = CircleShape,
                color = Color(0xB30F172A),
                border = BorderStroke(0.5.dp, Color(0x6038BDF8)),
                tonalElevation = 6.dp
            ) {
                IconButton(
                    onClick = { showOverlaidHUD = true },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = "Show Controls",
                        tint = PrimaryCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Slot Actions Dialog for remote/TV and mobile slot management
        if (state.slotActionTargetSlot != null) {
            val target = state.slotActionTargetSlot!!
            val actionSlot = state.slots.getOrNull(target)
            if (actionSlot != null) {
                SlotActionDialog(
                    slot = actionSlot,
                    isFullscreen = state.multiviewMode == MultiviewMode.FULLSCREEN,
                    onChangeChannel = {
                        viewModel.closeSlotActions()
                        viewModel.openChannelPicker(target)
                    },
                    onToggleAudio = {
                        viewModel.toggleSlotMute(target)
                        viewModel.closeSlotActions()
                    },
                    onToggleFullscreen = {
                        viewModel.setFocusedSlot(target)
                        if (state.multiviewMode == MultiviewMode.FULLSCREEN) {
                            viewModel.setMultiviewMode(MultiviewMode.GRID_4)
                        } else {
                            viewModel.setMultiviewMode(MultiviewMode.FULLSCREEN)
                        }
                        viewModel.closeSlotActions()
                    },
                    onClearSlot = {
                        viewModel.clearSlot(target)
                        viewModel.closeSlotActions()
                    },
                    onDismiss = {
                        viewModel.closeSlotActions()
                    }
                )
            }
        }

        // Global Loading Indicator during discovery
        if (state.isDiscovering && state.selectedDevice == null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                color = Color(0xCC000000),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 12.dp),
                        color = PrimaryCyan
                    )
                    Text(
                        text = "Discovering HDHomeRun on network…",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Channel Picker Dialog
        if (state.isChannelPickerOpen && state.channelPickerTargetSlot != null) {
            val slotIndex = state.channelPickerTargetSlot!!
            ChannelPickerDialog(
                slotIndex = slotIndex,
                channels = state.channels,
                deviceIp = state.selectedDevice?.ipAddress,
                onChannelSelected = { channel ->
                    viewModel.setChannelForSlot(slotIndex, channel)
                },
                onClearSlot = {
                    viewModel.setChannelForSlot(slotIndex, null)
                },
                onDismiss = {
                    viewModel.closeChannelPicker()
                }
            )
        }

        // Manual IP Dialog
        if (showManualIpDialog) {
            ManualIpDialog(
                onConnect = { ip ->
                    viewModel.connectToManualIp(ip)
                    showManualIpDialog = false
                },
                onDismiss = {
                    showManualIpDialog = false
                }
            )
        }

        // Custom Media Engine Settings Dialog
        if (showCustomEngineDialog) {
            CustomEngineDialog(
                onDismiss = {
                    showCustomEngineDialog = false
                },
                onEngineUpdated = {
                    // Re-trigger playback on currently active channels so new engine takes effect
                    state.slots.forEach { slot ->
                        val ch = slot.channel
                        if (ch != null) {
                            playerController.stop(slot.slotIndex)
                            playerController.playChannel(slot.slotIndex, ch)
                        }
                    }
                }
            )
        }

        // SnackBar for feedback & errors
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
