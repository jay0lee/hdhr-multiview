package com.droid.hdhrmv.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.IconButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droid.hdhrmv.model.Channel
import com.droid.hdhrmv.ui.theme.AccentAmber
import com.droid.hdhrmv.ui.theme.BorderFocused
import com.droid.hdhrmv.ui.theme.PrimaryCyan
import com.droid.hdhrmv.ui.theme.SurfaceDark
import com.droid.hdhrmv.ui.theme.SurfaceElevated
import com.droid.hdhrmv.ui.theme.TextPrimary
import com.droid.hdhrmv.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun ChannelPickerDialog(
    slotIndex: Int,
    channels: List<Channel>,
    deviceIp: String? = null,
    onChannelSelected: (Channel) -> Unit,
    onClearSlot: () -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showSearchField by remember { mutableStateOf(false) }
    val firstItemFocusRequester = remember { FocusRequester() }

    val filteredChannels = remember(channels, searchQuery) {
        if (searchQuery.isBlank()) channels
        else channels.filter {
            it.guideNumber.contains(searchQuery, ignoreCase = true) ||
            it.guideName.contains(searchQuery, ignoreCase = true)
        }
    }

    LaunchedEffect(filteredChannels) {
        if (filteredChannels.isNotEmpty() && !showSearchField) {
            delay(50)
            try {
                firstItemFocusRequester.requestFocus()
            } catch (_: Exception) {}
            delay(150)
            try {
                firstItemFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select Channel (Slot ${slotIndex + 1})",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showSearchField = !showSearchField },
                        modifier = Modifier
                            .size(32.dp)
                            .focusProperties { canFocus = showSearchField }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (showSearchField) PrimaryCyan else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = onClearSlot) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear Slot",
                            tint = AccentAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Clear", color = AccentAmber, fontSize = 13.sp)
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Search field (only shown if user explicitly taps the Search icon)
                if (showSearchField) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search channel…", color = TextSecondary) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = TextSecondary
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = PrimaryCyan,
                            unfocusedBorderColor = SurfaceElevated
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                val effectiveIp = deviceIp?.takeIf { it.isNotBlank() && it != "127.0.0.1" }
                if (filteredChannels.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (channels.isEmpty()) {
                                if (effectiveIp == null) "Scanning network for HDHomeRun devices…"
                                else "Loading channel lineup from HDHomeRun…"
                            } else "No channels match \"$searchQuery\"",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (searchQuery.isNotBlank() && effectiveIp != null) {
                            Button(
                                onClick = {
                                    val cleanNum = searchQuery.trim()
                                    onChannelSelected(
                                        Channel(
                                            guideNumber = cleanNum,
                                            guideName = "Channel $cleanNum",
                                            streamUrl = "http://$effectiveIp:5004/auto/v$cleanNum"
                                        )
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                            ) {
                                Text("Tune to Channel $searchQuery", color = SurfaceDark, fontWeight = FontWeight.Bold)
                            }
                        } else if (effectiveIp != null) {
                            // Quick Presets from detected HDHR
                            Text("Quick Select:", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            val presets = listOf(
                                "3.1" to "KYW-TV (CBS)",
                                "10.1" to "WCAU-TV (NBC)",
                                "12.1" to "WHYY (PBS)",
                                "17.1" to "WPHL-DT",
                                "29.1" to "WTXFDT (FOX)"
                            )
                            LazyColumn(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                                itemsIndexed(presets) { index, (num, name) ->
                                    ChannelRow(
                                        channel = Channel(
                                            guideNumber = num,
                                            guideName = name,
                                            isHd = true,
                                            streamUrl = "http://$effectiveIp:5004/auto/v$num"
                                        ),
                                        modifier = if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
                                        onClick = {
                                            onChannelSelected(
                                                Channel(
                                                    guideNumber = num,
                                                    guideName = name,
                                                    isHd = true,
                                                    streamUrl = "http://$effectiveIp:5004/auto/v$num"
                                                )
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                    ) {
                        itemsIndexed(filteredChannels, key = { index, ch -> "${ch.guideNumber}_${ch.streamUrl}_$index" }) { index, channel ->
                            ChannelRow(
                                channel = channel,
                                modifier = if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
                                onClick = { onChannelSelected(channel) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated)
            ) {
                Text(text = "Close", color = TextPrimary)
            }
        },
        containerColor = SurfaceDark
    )
}

@Composable
private fun ChannelRow(
    channel: Channel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(
                width = if (isFocused) 3.dp else 0.5.dp,
                color = if (isFocused) BorderFocused else Color(0x33334155),
                shape = RoundedCornerShape(6.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        color = if (isFocused) PrimaryCyan.copy(alpha = 0.28f) else SurfaceElevated
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LiveTv,
                    contentDescription = null,
                    tint = PrimaryCyan,
                    modifier = Modifier.size(20.dp)
                )
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
                    fontSize = 15.sp
                )
            }

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
