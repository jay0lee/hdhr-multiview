package com.droid.hdhrmv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droid.hdhrmv.model.Channel
import com.droid.hdhrmv.ui.theme.AccentAmber
import com.droid.hdhrmv.ui.theme.AccentGreen
import com.droid.hdhrmv.ui.theme.BorderFocused
import com.droid.hdhrmv.ui.theme.PrimaryCyan
import com.droid.hdhrmv.ui.theme.SurfaceDark
import com.droid.hdhrmv.ui.theme.SurfaceElevated
import com.droid.hdhrmv.ui.theme.TextPrimary
import com.droid.hdhrmv.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun QuickStartChannelDialog(
    channels: List<Channel>,
    deviceIp: String? = null,
    onLaunch: (List<Channel>) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedChannels = remember { mutableStateListOf<Channel>() }
    val firstItemFocusRequester = remember { FocusRequester() }
    val launchButtonFocusRequester = remember { FocusRequester() }

    // Fallback preset channels if lineup is empty
    val effectiveChannels = remember(channels, deviceIp) {
        if (channels.isNotEmpty()) {
            channels
        } else {
            val ip = deviceIp ?: "127.0.0.1"
            listOf(
                Channel("3.1", "KYW-TV (CBS)", "MPEG2", "AC3", true, "http://$ip:5004/auto/v3.1"),
                Channel("10.1", "WCAU-TV (NBC)", "MPEG2", "AC3", true, "http://$ip:5004/auto/v10.1"),
                Channel("12.1", "WHYY (PBS)", "MPEG2", "AC3", true, "http://$ip:5004/auto/v12.1"),
                Channel("29.1", "WTXFDT (FOX)", "MPEG2", "AC3", true, "http://$ip:5004/auto/v29.1")
            )
        }
    }

    // Auto-focus the first channel item on launch for immediate remote D-Pad navigation
    LaunchedEffect(Unit) {
        delay(120)
        try {
            firstItemFocusRequester.requestFocus()
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LiveTv,
                            contentDescription = null,
                            tint = PrimaryCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Quick Start MultiView",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Count Badge
                    Surface(
                        color = if (selectedChannels.size == 4) AccentGreen.copy(alpha = 0.25f) else PrimaryCyan.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, if (selectedChannels.size == 4) AccentGreen else PrimaryCyan),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "${selectedChannels.size} / 4 Selected",
                            color = if (selectedChannels.size == 4) AccentGreen else PrimaryCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Pick up to 4 channels to start watching simultaneously in grid view.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Slot Preview Chips Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (slotIdx in 0 until 4) {
                        val ch = selectedChannels.getOrNull(slotIdx)
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (ch != null) 1.5.dp else 0.5.dp,
                                    color = if (ch != null) PrimaryCyan else Color(0x33334155),
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            color = if (ch != null) PrimaryCyan.copy(alpha = 0.15f) else SurfaceElevated
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Slot ${slotIdx + 1}",
                                        color = if (ch != null) PrimaryCyan else TextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (ch != null) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable { selectedChannels.remove(ch) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (ch != null) "${ch.guideNumber} ${ch.guideName}" else "+ Empty",
                                    color = if (ch != null) TextPrimary else Color(0x66FFFFFF),
                                    fontSize = 11.sp,
                                    fontWeight = if (ch != null) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Quick Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            selectedChannels.clear()
                            val top4 = effectiveChannels.take(4)
                            selectedChannels.addAll(top4)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryCyan),
                        border = BorderStroke(1.dp, PrimaryCyan.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = PrimaryCyan
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Auto-Fill Top 4", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    if (selectedChannels.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { selectedChannels.clear() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                            border = BorderStroke(0.5.dp, Color(0x40FFFFFF)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(text = "Clear All", fontSize = 12.sp)
                        }
                    }
                }

                // Channel Lineup List
                Surface(
                    color = Color(0x800F172A),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.5.dp, Color(0x33334155)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp)
                    ) {
                        itemsIndexed(effectiveChannels, key = { _, ch -> ch.guideNumber }) { index, channel ->
                            val slotIndex = selectedChannels.indexOfFirst { it.guideNumber == channel.guideNumber }
                            val isSelected = slotIndex != -1

                            QuickStartChannelRow(
                                channel = channel,
                                slotNumber = if (isSelected) slotIndex + 1 else null,
                                modifier = Modifier
                                    .then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                                    .focusProperties { right = launchButtonFocusRequester },
                                onClick = {
                                    if (isSelected) {
                                        selectedChannels.remove(channel)
                                    } else {
                                        if (selectedChannels.size < 4) {
                                            selectedChannels.add(channel)
                                        } else {
                                            // Replace the 4th slot if already 4
                                            selectedChannels[3] = channel
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onLaunch(selectedChannels.toList())
                },
                enabled = selectedChannels.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryCyan,
                    contentColor = Color.Black,
                    disabledContainerColor = SurfaceElevated,
                    disabledContentColor = TextSecondary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.focusRequester(launchButtonFocusRequester)
            ) {
                Text(
                    text = if (selectedChannels.isEmpty()) "Select at least 1 channel" else "Launch MultiView (${selectedChannels.size}/4)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SurfaceElevated,
                    contentColor = TextPrimary
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "Skip", fontSize = 14.sp)
            }
        },
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(0.94f)
    )
}

@Composable
private fun QuickStartChannelRow(
    channel: Channel,
    slotNumber: Int?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isSelected = slotNumber != null

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isFocused) 3.dp else if (isSelected) 1.dp else 0.5.dp,
                color = if (isFocused) BorderFocused else if (isSelected) PrimaryCyan else Color(0x22334155),
                shape = RoundedCornerShape(8.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        color = when {
            isFocused && isSelected -> PrimaryCyan.copy(alpha = 0.35f)
            isFocused -> PrimaryCyan.copy(alpha = 0.22f)
            isSelected -> PrimaryCyan.copy(alpha = 0.12f)
            else -> SurfaceElevated
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Slot or Add indicator badge
                if (isSelected) {
                    Surface(
                        color = PrimaryCyan,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Slot $slotNumber",
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Surface(
                        color = Color(0x33334155),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(18.dp)
                                .padding(2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = channel.guideNumber,
                    color = PrimaryCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = channel.guideName,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Codec & HD Badges
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (channel.videoCodec != null) {
                    Text(
                        text = channel.videoCodec,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
                if (channel.isHd) {
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
        }
    }
}
