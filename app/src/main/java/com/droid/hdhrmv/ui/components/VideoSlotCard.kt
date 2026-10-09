package com.droid.hdhrmv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droid.hdhrmv.model.SlotState
import com.droid.hdhrmv.ui.theme.AccentAmber
import com.droid.hdhrmv.ui.theme.BorderFocused
import com.droid.hdhrmv.ui.theme.PrimaryCyan
import com.droid.hdhrmv.ui.theme.SurfaceDark
import com.droid.hdhrmv.ui.theme.SurfaceElevated
import com.droid.hdhrmv.ui.theme.TextPrimary
import com.droid.hdhrmv.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoSlotCard(
    slot: SlotState,
    modifier: Modifier = Modifier,
    renderVideo: @Composable () -> Unit = {},
    onSlotClick: () -> Unit,
    onSlotLongClick: () -> Unit = {},
    onSlotFocused: () -> Unit = {},
    onChannelClick: () -> Unit,
    onMuteToggle: () -> Unit,
    onFocusClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDpadFocused by interactionSource.collectIsFocusedAsState()

    val isSlotActive = slot.isFocused || isDpadFocused
    val borderColor = if (isSlotActive) BorderFocused else Color(0x33334155)
    val borderWidth = if (isSlotActive) 3.dp else 0.5.dp

    var showControls by remember { mutableStateOf(true) }

    LaunchedEffect(isDpadFocused) {
        if (isDpadFocused) {
            showControls = true
            onSlotFocused()
        }
    }

    LaunchedEffect(showControls) {
        if (showControls) {
            delay(4000)
            showControls = false
        }
    }

    Surface(
        modifier = modifier
            .border(borderWidth, borderColor)
            .focusable(interactionSource = interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    showControls = !showControls
                    onSlotClick()
                },
                onLongClick = {
                    onSlotLongClick()
                }
            ),
        color = Color.Black
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (slot.channel != null) {
                // Real Video Player View (100% edge-to-edge)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    renderVideo()
                }

                // Buffering indicator
                if (slot.isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(36.dp)
                            .align(Alignment.Center),
                        color = PrimaryCyan
                    )
                }

                // Overlaid Top HUD (Animated Auto-Hide)
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .focusProperties { canFocus = false },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Channel badge
                        Surface(
                            color = Color(0xCC0F172A),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.clickable { onChannelClick() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = slot.channel.guideNumber,
                                    color = PrimaryCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = slot.channel.guideName,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                                if (slot.channel.isHd) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = AccentAmber,
                                        shape = RoundedCornerShape(2.dp)
                                    ) {
                                        Text(
                                            text = "HD",
                                            color = Color.Black,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 9.sp,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Audio Focus / Mute Chip
                        Surface(
                            color = if (!slot.isMuted) Color(0xCC059669) else Color(0xCC1E293B),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.clickable { onMuteToggle() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (!slot.isMuted) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeMute,
                                    contentDescription = if (!slot.isMuted) "Audio Active" else "Muted",
                                    tint = if (!slot.isMuted) TextPrimary else TextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (!slot.isMuted) "AUDIO" else "MUTE",
                                    color = if (!slot.isMuted) TextPrimary else TextSecondary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                // Overlaid Bottom HUD (Animated Auto-Hide)
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .focusProperties { canFocus = false },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color(0xCC0F172A),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Slot ${slot.slotIndex + 1}",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }

                        Surface(
                            color = Color(0xCC0F172A),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = onChannelClick,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LiveTv,
                                        contentDescription = "Change Channel",
                                        tint = PrimaryCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = onFocusClick,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fullscreen,
                                        contentDescription = "Maximize Slot",
                                        tint = TextPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Empty Slot State: sleek & minimal
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics(mergeDescendants = true) {}
                        .clickable { onSlotClick() }
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = SurfaceElevated,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Select Channel",
                            tint = PrimaryCyan,
                            modifier = Modifier
                                .padding(10.dp)
                                .fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.padding(2.dp))
                    Text(
                        text = "Slot ${slot.slotIndex + 1}",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { onSlotClick() }
                    )
                    Spacer(modifier = Modifier.padding(2.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = PrimaryCyan,
                        modifier = Modifier.clickable { onSlotClick() }
                    ) {
                        Text(
                            text = "Choose Channel",
                            color = SurfaceDark,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
