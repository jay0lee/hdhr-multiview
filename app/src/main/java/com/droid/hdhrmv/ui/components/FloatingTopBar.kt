package com.droid.hdhrmv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.droid.hdhrmv.model.HdHomeRunDevice
import com.droid.hdhrmv.model.MultiviewMode
import com.droid.hdhrmv.ui.theme.AccentAmber
import com.droid.hdhrmv.ui.theme.AccentGreen
import com.droid.hdhrmv.ui.theme.PrimaryCyan
import com.droid.hdhrmv.ui.theme.SurfaceElevated
import com.droid.hdhrmv.ui.theme.TextPrimary
import com.droid.hdhrmv.ui.theme.TextSecondary

@Composable
fun FloatingTopBar(
    device: HdHomeRunDevice?,
    freeTuners: Int,
    totalTuners: Int,
    currentMode: MultiviewMode,
    isDiscovering: Boolean,
    onModeSelected: (MultiviewMode) -> Unit,
    onRefresh: () -> Unit,
    onManualIpClick: () -> Unit,
    canShowSidePanel: Boolean,
    onToggleSidePanel: () -> Unit,
    onOpenEngineSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var modeMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        color = Color(0xD90F172A), // Semi-transparent glassmorphic slate
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0x4038BDF8))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Device & Tuner Status (flexibly sized to avoid pushing controls off-screen)
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = "HDHR MultiView",
                    tint = PrimaryCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "HDHR",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Device connection pill
                val devicePillInteraction = remember { MutableInteractionSource() }
                val isDevicePillFocused by devicePillInteraction.collectIsFocusedAsState()
                Surface(
                    color = if (isDevicePillFocused) PrimaryCyan.copy(alpha = 0.25f) else SurfaceElevated.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(10.dp),
                    border = if (isDevicePillFocused) androidx.compose.foundation.BorderStroke(1.5.dp, PrimaryCyan) else null,
                    modifier = Modifier
                        .focusable(interactionSource = devicePillInteraction)
                        .clickable(interactionSource = devicePillInteraction, indication = null) { onManualIpClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Router,
                            contentDescription = "Device",
                            tint = if (device != null) AccentGreen else TextSecondary,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = device?.friendlyName ?: if (isDiscovering) "..." else "None",
                            color = TextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (device != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    val tunerColor = if (freeTuners > 0) AccentGreen else AccentAmber
                    Surface(
                        color = SurfaceElevated.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(tunerColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$freeTuners/$totalTuners",
                                color = TextPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Right: Layout Selector & Side Panel Toggle & Refresh
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Layout Mode Dropdown
                val layoutModeInteraction = remember { MutableInteractionSource() }
                val isLayoutModeFocused by layoutModeInteraction.collectIsFocusedAsState()
                Box {
                    Surface(
                        color = if (isLayoutModeFocused) PrimaryCyan.copy(alpha = 0.25f) else SurfaceElevated.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(8.dp),
                        border = if (isLayoutModeFocused) androidx.compose.foundation.BorderStroke(1.5.dp, PrimaryCyan) else null,
                        modifier = Modifier
                            .focusable(interactionSource = layoutModeInteraction)
                            .clickable(interactionSource = layoutModeInteraction, indication = null) { modeMenuExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.GridView,
                                contentDescription = "View Mode",
                                tint = PrimaryCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = currentMode.displayName,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = modeMenuExpanded,
                        onDismissRequest = { modeMenuExpanded = false }
                    ) {
                        MultiviewMode.values().forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = mode.displayName,
                                        fontWeight = if (mode == currentMode) FontWeight.Bold else FontWeight.Normal,
                                        color = if (mode == currentMode) PrimaryCyan else TextPrimary
                                    )
                                },
                                onClick = {
                                    onModeSelected(mode)
                                    modeMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                if (canShowSidePanel) {
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onToggleSidePanel,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ViewSidebar,
                            contentDescription = "Show Side Panel",
                            tint = PrimaryCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                val refreshInteraction = remember { MutableInteractionSource() }
                val isRefreshFocused by refreshInteraction.collectIsFocusedAsState()
                IconButton(
                    onClick = onRefresh,
                    interactionSource = refreshInteraction,
                    modifier = Modifier
                        .size(28.dp)
                        .focusable(interactionSource = refreshInteraction)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = if (isRefreshFocused) PrimaryCyan else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                val settingsInteraction = remember { MutableInteractionSource() }
                val isSettingsFocused by settingsInteraction.collectIsFocusedAsState()
                IconButton(
                    onClick = onOpenEngineSettings,
                    interactionSource = settingsInteraction,
                    modifier = Modifier
                        .size(28.dp)
                        .focusable(interactionSource = settingsInteraction)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Media Engine Settings",
                        tint = if (isSettingsFocused) PrimaryCyan else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
