package com.example.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.presentation.MainViewModel
import com.example.ui.theme.VoxCrimsonDistress
import com.example.ui.theme.VoxDarkCard
import com.example.ui.theme.VoxDarkCardBorder
import com.example.ui.theme.VoxDarkSurface
import com.example.ui.theme.VoxTextMuted
import com.example.ui.theme.VoxTextPrimary
import com.example.ui.theme.VoxTextSecondary

@Composable
fun AlertEmergencyDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val sttLang by viewModel.languageManager.sttLanguage.collectAsState()
    var customAlertText by remember { mutableStateOf(sttLang.emergencyPhrase) }

    val presetTemplates = listOf(
        sttLang.emergencyPhrase,
        "SOS: Immediate medical assistance required at our coordinates!",
        "Structural danger or hazard detected! Evacuate perimeter immediately!",
        "Search & rescue unit deployed. Standby for voice coordination."
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("emergency_broadcast_dialog"),
            colors = CardDefaults.cardColors(containerColor = VoxDarkSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, VoxCrimsonDistress)
        ) {
            Column(
                modifier = Modifier.padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = VoxCrimsonDistress,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DISTRESS BROADCAST",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                color = VoxCrimsonDistress
                            )
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = VoxTextSecondary
                        )
                    }
                }

                Text(
                    text = "Alerts bypass normal queues and trigger loud audio playback on the receiver.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        color = VoxTextMuted
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "SELECT DISTRESS TEMPLATE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = VoxTextSecondary
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))

                presetTemplates.forEach { template ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (customAlertText == template) VoxCrimsonDistress.copy(alpha = 0.2f) else VoxDarkCard)
                            .border(
                                1.dp,
                                if (customAlertText == template) VoxCrimsonDistress else VoxDarkCardBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { customAlertText = template }
                            .padding(10.dp)
                    ) {
                        Text(
                            text = template,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (customAlertText == template) FontWeight.Bold else FontWeight.Normal,
                                color = VoxTextPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = customAlertText,
                    onValueChange = { customAlertText = it },
                    label = { Text("Custom Alert Text") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VoxTextPrimary,
                        unfocusedTextColor = VoxTextPrimary,
                        focusedBorderColor = VoxCrimsonDistress,
                        unfocusedBorderColor = VoxDarkCardBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_alert_input")
                )

                Spacer(modifier = Modifier.height(18.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(VoxCrimsonDistress)
                        .clickable {
                            if (customAlertText.isNotBlank()) {
                                viewModel.sendEmergencyAlert(customAlertText, isDistress = true)
                                onDismiss()
                            }
                        }
                        .padding(vertical = 14.dp)
                        .testTag("send_distress_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "BROADCAST DISTRESS NOW",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = Color.White
                        )
                    )
                }
            }
        }
    }
}
