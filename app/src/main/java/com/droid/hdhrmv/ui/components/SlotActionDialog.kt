package com.droid.hdhrmv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import com.droid.hdhrmv.model.SlotState
import com.droid.hdhrmv.ui.theme.AccentAmber
import com.droid.hdhrmv.ui.theme.AccentGreen
import com.droid.hdhrmv.ui.theme.BorderFocused
import com.droid.hdhrmv.ui.theme.PrimaryCyan
import com.droid.hdhrmv.ui.theme.SurfaceDark
import com.droid.hdhrmv.ui.theme.SurfaceElevated
import com.droid.hdhrmv.ui.theme.TextPrimary
import com.droid.hdhrmv.ui.theme.TextSecondary

@Composable
fun SlotActionDialog(
    slot: SlotState,
    isFullscreen: Boolean,
    onChangeChannel: () -> Unit,
    onToggleAudio: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onClearSlot: () -> Unit,
    onDismiss: () -> Unit
) {
    val channel = slot.channel
    val firstActionFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(120)
        try {
            firstActionFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Slot ${slot.slotIndex + 1} Actions",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (channel?.isHd == true) {
                        Surface(
                            color = AccentAmber,
                            shape = RoundedCornerShape(3.dp)
                        ) {
                            Text(
                                text = "HD",
                                color = Color.Black,
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                if (channel != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${channel.guideNumber} • ${channel.guideName}",
                        color = PrimaryCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SlotActionButton(
                    icon = Icons.Default.LiveTv,
                    title = "Change Channel",
                    subtitle = "Open channel lineup to tune another broadcast",
                    accentColor = PrimaryCyan,
                    modifier = Modifier.focusRequester(firstActionFocusRequester),
                    onClick = onChangeChannel
                )

                SlotActionButton(
                    icon = if (!slot.isMuted) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                    title = if (!slot.isMuted) "Mute Audio" else "Unmute & Focus Audio",
                    subtitle = if (!slot.isMuted) "Currently playing sound" else "Route sound to this stream",
                    accentColor = if (!slot.isMuted) AccentAmber else AccentGreen,
                    onClick = onToggleAudio
                )

                SlotActionButton(
                    icon = Icons.Default.Fullscreen,
                    title = if (isFullscreen) "Exit Fullscreen (Grid View)" else "Fullscreen View",
                    subtitle = if (isFullscreen) "Return to multiview layout" else "Expand this slot to full display",
                    accentColor = PrimaryCyan,
                    onClick = onToggleFullscreen
                )

                SlotActionButton(
                    icon = Icons.Default.Clear,
                    title = "Clear Slot",
                    subtitle = "Stop stream and release tuner on HDHomeRun",
                    accentColor = AccentAmber,
                    onClick = onClearSlot
                )

                // Engine & Hardware Decoding Status
                Surface(
                    color = Color(0xCC0F172A),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, Color(0x4038BDF8)),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Video Decoder", color = TextSecondary, fontSize = 11.sp)
                            Text(text = "Hardware (MediaCodec)", color = Color(0xFF34D399), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Rendering Plane", color = TextSecondary, fontSize = 11.sp)
                            Text(text = "SurfaceView (Hardware Overlay)", color = PrimaryCyan, fontWeight = FontWeight.Medium, fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Stream Protocol", color = TextSecondary, fontSize = 11.sp)
                            Text(text = "HTTP MPEG-TS (port 5004)", color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Buffer & Sync", color = TextSecondary, fontSize = 11.sp)
                            Text(text = "1000ms Adaptive Live Buffer", color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            SlotActionButton(
                icon = Icons.Default.Close,
                title = "Resume",
                subtitle = "",
                accentColor = TextSecondary,
                compact = true,
                onClick = onDismiss
            )
        },
        containerColor = SurfaceDark
    )
}

@Composable
private fun SlotActionButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isFocused) PrimaryCyan.copy(alpha = 0.22f) else SurfaceElevated,
        border = if (isFocused) BorderStroke(2.dp, BorderFocused) else BorderStroke(0.5.dp, Color(0x33334155))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isFocused) PrimaryCyan else accentColor,
                modifier = Modifier.size(if (compact) 18.dp else 22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (isFocused) PrimaryCyan else TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
