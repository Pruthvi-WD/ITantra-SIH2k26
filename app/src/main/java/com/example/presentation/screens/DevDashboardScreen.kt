package com.example.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.presentation.MainViewModel
import com.example.ui.theme.VoxAmberWarning
import com.example.ui.theme.VoxCyanPrimary
import com.example.ui.theme.VoxDarkCanvas
import com.example.ui.theme.VoxDarkCard
import com.example.ui.theme.VoxDarkCardBorder
import com.example.ui.theme.VoxDarkSurface
import com.example.ui.theme.VoxEmeraldActive
import com.example.ui.theme.VoxTextMuted
import com.example.ui.theme.VoxTextPrimary
import com.example.ui.theme.VoxTextSecondary

@Composable
fun DevDashboardScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetry by viewModel.telemetry.collectAsState()
    val reliabilityManager = viewModel.reliabilityManager

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoxDarkCanvas)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = VoxTextPrimary
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = "SIH EVALUATION DASHBOARD",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = VoxTextPrimary
                    )
                )
                Text(
                    text = "Real-Time Edge-AI & Latency Telemetry",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        color = VoxCyanPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Total End-to-End Latency Hero Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("e2e_hero_card"),
            colors = CardDefaults.cardColors(containerColor = VoxDarkSurface),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, VoxCyanPrimary)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = VoxCyanPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MEASURED END-TO-END LATENCY",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = VoxTextSecondary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${telemetry.endToEndLatencyMs} ms",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Black,
                        color = VoxCyanPrimary
                    )
                )

                Text(
                    text = "Speech End (Phone A)  →  Audio Start (Phone B)",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        color = VoxEmeraldActive
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Pipeline Stages Breakdown (T0 to T9)
        MetricSectionCard(
            title = "1. STT PIPELINE (T1 → T4)",
            items = listOf(
                "Engine" to "On-Device Android STT (Offline)",
                "Active Language" to telemetry.activeSttLanguage,
                "Audio Duration" to "${telemetry.audioDurationMs} ms",
                "Inference Latency (T4-T3)" to "${telemetry.sttLatencyMs} ms",
                "Real-Time Factor (RTF)" to String.format("%.3f", telemetry.realTimeFactor),
                "RAM Allocation" to "${telemetry.memoryUsageMb} MB"
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        MetricSectionCard(
            title = "2. NETWORK TRANSPORT (T5 → T6)",
            items = listOf(
                "Transport Layer" to telemetry.activeTransport,
                "Packet Payload Size" to "${telemetry.packetSizeBytes} bytes",
                "Transmission Latency" to "${telemetry.networkLatencyMs} ms",
                "Total Packets Sent" to "${reliabilityManager.totalPacketsSent}",
                "Total Packets Received" to "${reliabilityManager.totalPacketsReceived}",
                "ACKs Confirmed" to "${reliabilityManager.totalAcksReceived}",
                "Packets Retried" to "${reliabilityManager.retriedPackets}",
                "Packets Dropped" to "${reliabilityManager.droppedPackets}"
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        MetricSectionCard(
            title = "3. TTS SYNTHESIS (T7 → T9)",
            items = listOf(
                "Engine" to "Native Android TTS (Offline)",
                "Active Voice Locale" to telemetry.activeTtsLanguage,
                "First Audio Latency (T8-T7)" to "${telemetry.ttsLatencyMs} ms",
                "Audio Track Buffer" to "16 kHz Mono PCM",
                "Audio Routing" to "Direct Speakerphone"
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        MetricSectionCard(
            title = "4. HARDWARE & DEVICE METRICS",
            items = listOf(
                "Battery Level" to "${telemetry.batteryPercent}%",
                "App Process Memory" to "${telemetry.memoryUsageMb} MB",
                "Internet Required" to "NO (100% Offline)",
                "SIM Required" to "NO (Air-Gapped D2D)",
                "Cloud API Calls" to "0 (Zero Cloud Dependency)"
            )
        )

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun MetricSectionCard(
    title: String,
    items: List<Pair<String, String>>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VoxDarkSurface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, VoxDarkCardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = VoxCyanPrimary
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            items.forEach { (key, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = key,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = VoxTextSecondary
                        )
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = VoxTextPrimary
                        )
                    )
                }
            }
        }
    }
}
