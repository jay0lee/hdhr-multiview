package com.droid.hdhrmv.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droid.hdhrmv.player.AudioTranscodeManager
import com.droid.hdhrmv.ui.theme.AccentAmber
import com.droid.hdhrmv.ui.theme.AccentGreen
import com.droid.hdhrmv.ui.theme.BorderFocused
import com.droid.hdhrmv.ui.theme.PrimaryCyan
import com.droid.hdhrmv.ui.theme.SurfaceDark
import com.droid.hdhrmv.ui.theme.SurfaceElevated
import com.droid.hdhrmv.ui.theme.TextPrimary
import com.droid.hdhrmv.ui.theme.TextSecondary

@Composable
fun CustomEngineDialog(
    onDismiss: () -> Unit,
    onEngineUpdated: () -> Unit = {}
) {
    val context = LocalContext.current
    var isCustomActive by remember { mutableStateOf(AudioTranscodeManager.isCustomBinaryActive(context)) }
    var engineDescription by remember { mutableStateOf(AudioTranscodeManager.getActiveBinaryDescription(context)) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val success = AudioTranscodeManager.installCustomBinary(context, inputStream)
                    if (success) {
                        isCustomActive = AudioTranscodeManager.isCustomBinaryActive(context)
                        engineDescription = AudioTranscodeManager.getActiveBinaryDescription(context)
                        statusMessage = "Custom engine installed successfully."
                        onEngineUpdated()
                    } else {
                        statusMessage = "Installation failed. Please verify binary format."
                    }
                }
            } catch (e: Exception) {
                statusMessage = "Error reading file: ${e.message}"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = PrimaryCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Media & Audio Engine",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Configure the media processing engine. The app defaults to an unpatched upstream engine. You can supply your own custom FFmpeg binary for extended decoding capabilities.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                // Current Engine Status Card
                Surface(
                    color = SurfaceElevated,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (isCustomActive) AccentAmber else PrimaryCyan.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ACTIVE ENGINE",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Surface(
                                color = if (isCustomActive) AccentAmber.copy(alpha = 0.2f) else AccentGreen.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (isCustomActive) "USER SUPPLIED" else "DEFAULT",
                                    color = if (isCustomActive) AccentAmber else AccentGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = engineDescription,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (statusMessage != null) {
                    Text(
                        text = statusMessage!!,
                        color = if (statusMessage!!.contains("success", true)) AccentGreen else AccentAmber,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Action: Select Custom Binary
                EngineActionButton(
                    icon = Icons.Default.FileOpen,
                    title = "Select Custom FFmpeg Binary",
                    subtitle = "Load an external binary via file picker",
                    accentColor = PrimaryCyan,
                    onClick = {
                        filePickerLauncher.launch("*/*")
                    }
                )

                // Action: Reset to Default Engine
                if (isCustomActive) {
                    EngineActionButton(
                        icon = Icons.Default.Delete,
                        title = "Reset to Default Engine",
                        subtitle = "Restore the built-in upstream engine",
                        accentColor = AccentAmber,
                        onClick = {
                            AudioTranscodeManager.resetToDefaultBinary(context)
                            isCustomActive = AudioTranscodeManager.isCustomBinaryActive(context)
                            engineDescription = AudioTranscodeManager.getActiveBinaryDescription(context)
                            statusMessage = "Reset to default engine."
                            onEngineUpdated()
                        }
                    )
                }
            }
        },
        confirmButton = {
            EngineActionButton(
                icon = Icons.Default.Close,
                title = "Done",
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
private fun EngineActionButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
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
                    fontSize = if (compact) 13.sp else 14.sp,
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
