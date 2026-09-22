package com.example.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.ChatMessage
import com.example.domain.model.DeliveryStatus
import com.example.domain.model.MessageType
import com.example.presentation.MainViewModel
import com.example.ui.theme.VoxAmberWarning
import com.example.ui.theme.VoxCrimsonDistress
import com.example.ui.theme.VoxCyanLight
import com.example.ui.theme.VoxCyanPrimary
import com.example.ui.theme.VoxDarkCanvas
import com.example.ui.theme.VoxDarkCard
import com.example.ui.theme.VoxDarkCardBorder
import com.example.ui.theme.VoxDarkSurface
import com.example.ui.theme.VoxEmeraldActive
import com.example.ui.theme.VoxTextMuted
import com.example.ui.theme.VoxTextPrimary
import com.example.ui.theme.VoxTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoxDarkCanvas)
            .padding(16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                        text = "MESSAGE LOG",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = VoxTextPrimary
                        )
                    )
                    Text(
                        text = "${messages.size} Messages • Local Room DB",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            color = VoxCyanPrimary
                        )
                    )
                }
            }

            if (messages.isNotEmpty()) {
                IconButton(
                    onClick = { viewModel.clearHistory() },
                    modifier = Modifier.testTag("clear_history_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear History",
                        tint = VoxTextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        val totalTracked = messages.size
        val pendingTransferCount = messages.count {
            it.isOutgoing && (it.deliveryStatus == DeliveryStatus.QUEUED || it.deliveryStatus == DeliveryStatus.FAILED || it.deliveryStatus == DeliveryStatus.SENDING)
        }
        val deliveredCount = messages.count {
            it.deliveryStatus == DeliveryStatus.DELIVERED || it.deliveryStatus == DeliveryStatus.PLAYED || it.deliveryStatus == DeliveryStatus.ACKNOWLEDGED
        }

        // Sync & Transfer Tracker Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = VoxDarkSurface),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (pendingTransferCount > 0) VoxAmberWarning else VoxEmeraldActive
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = if (pendingTransferCount > 0) VoxAmberWarning else VoxEmeraldActive,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (pendingTransferCount > 0) "DEVICE TRANSFER: $pendingTransferCount QUEUED" else "DEVICE TRANSFER: ALL SYNCED",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                color = if (pendingTransferCount > 0) VoxAmberWarning else VoxEmeraldActive
                            )
                        )
                    }

                    Text(
                        text = "$deliveredCount/$totalTracked Sent",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = VoxTextSecondary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (pendingTransferCount > 0) {
                        "Your spoken messages are safely tracked and will be transmitted automatically when another device is paired."
                    } else {
                        "All voice recordings and transcripts have been successfully delivered to the other device."
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        color = VoxTextMuted
                    )
                )

                if (pendingTransferCount > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.transferAllQueuedMessages() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("transfer_all_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = VoxAmberWarning),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "TRANSFER ALL TO OTHER DEVICE ($pendingTransferCount)",
                            color = Color.Black,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = VoxTextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No recorded conversations yet",
                        style = MaterialTheme.typography.bodyMedium.copy(color = VoxTextMuted)
                    )
                    Text(
                        text = "Voice messages sent via PTT or Alert will be archived here.",
                        style = MaterialTheme.typography.bodySmall.copy(color = VoxTextMuted)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { message ->
                    HistoryMessageCard(
                        message = message,
                        onReplay = { viewModel.replayMessageAudio(message) },
                        onTransfer = { viewModel.retryTransferMessage(message.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryMessageCard(
    message: ChatMessage,
    onReplay: () -> Unit,
    onTransfer: () -> Unit = {}
) {
    val isAlert = message.messageType == MessageType.ALERT || message.messageType == MessageType.DISTRESS
    val timeFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val timeStr = timeFormatter.format(Date(message.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_item_${message.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isAlert) Color(0xFF240E12) else VoxDarkSurface
        ),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isAlert) VoxCrimsonDistress else VoxDarkCardBorder
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Sender, Time, Priority
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isAlert) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = VoxCrimsonDistress,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = if (message.isOutgoing) "YOU" else message.senderName.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = if (isAlert) VoxCrimsonDistress else (if (message.isOutgoing) VoxCyanPrimary else VoxEmeraldActive)
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "•  $timeStr",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            color = VoxTextMuted
                        )
                    )
                }

                Text(
                    text = message.languageCode,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = VoxTextSecondary
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Body text
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    color = VoxTextPrimary
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Footer: Delivery Status & Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusIcon = when (message.deliveryStatus) {
                        DeliveryStatus.PLAYED, DeliveryStatus.ACKNOWLEDGED, DeliveryStatus.DELIVERED -> Icons.Default.DoneAll
                        DeliveryStatus.SENDING -> Icons.Default.Sync
                        DeliveryStatus.QUEUED -> Icons.Default.Send
                        else -> Icons.Default.Done
                    }
                    val statusColor = when (message.deliveryStatus) {
                        DeliveryStatus.PLAYED, DeliveryStatus.ACKNOWLEDGED, DeliveryStatus.DELIVERED -> VoxEmeraldActive
                        DeliveryStatus.QUEUED -> VoxAmberWarning
                        DeliveryStatus.SENDING, DeliveryStatus.SENT -> VoxCyanPrimary
                        DeliveryStatus.FAILED -> VoxCrimsonDistress
                        else -> VoxAmberWarning
                    }
                    val statusLabel = when (message.deliveryStatus) {
                        DeliveryStatus.DELIVERED, DeliveryStatus.PLAYED, DeliveryStatus.ACKNOWLEDGED -> "TRANSFERRED"
                        DeliveryStatus.QUEUED -> "QUEUED (READY TO TRANSFER)"
                        DeliveryStatus.SENDING -> "TRANSFERRING..."
                        DeliveryStatus.SENT -> "SENT OVER AIR"
                        DeliveryStatus.FAILED -> "NOT TRANSFERRED"
                        else -> message.deliveryStatus.name
                    }

                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    )

                    if (message.latencyMs > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(${message.latencyMs}ms)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                color = VoxTextMuted
                            )
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (message.isOutgoing && (message.deliveryStatus == DeliveryStatus.QUEUED || message.deliveryStatus == DeliveryStatus.FAILED)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(VoxAmberWarning.copy(alpha = 0.2f))
                                .clickable { onTransfer() }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Transfer to Peer",
                                    tint = VoxAmberWarning,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "TRANSFER",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = VoxAmberWarning
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // Replay Audio (Voice Recording or Offline TTS)
                    val hasVoiceRecording = !message.audioFilePath.isNullOrBlank()
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (hasVoiceRecording) VoxCyanPrimary.copy(alpha = 0.2f) else VoxDarkCard)
                            .clickable { onReplay() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Replay Audio",
                                tint = if (hasVoiceRecording) VoxCyanLight else VoxCyanPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (hasVoiceRecording) "PLAY VOICE" else "PLAY AUDIO",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (hasVoiceRecording) VoxCyanLight else VoxCyanPrimary
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
