package com.example.presentation.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.communication.TransportState
import com.example.domain.model.DeliveryStatus
import com.example.presentation.MainViewModel
import com.example.presentation.OperatingMode
import com.example.presentation.components.StatusPill
import com.example.presentation.components.WaveformVisualizer
import com.example.ui.theme.VoxAmberWarning
import com.example.ui.theme.VoxCrimsonDistress
import com.example.ui.theme.VoxCyanGlow
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

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateDevices: () -> Unit,
    onNavigateLanguages: () -> Unit,
    onNavigateHistory: () -> Unit,
    onNavigateDashboard: () -> Unit,
    onNavigateBenchmark: () -> Unit,
    onNavigateSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }.toTypedArray()
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.startAutoDiscovery()
    }

    LaunchedEffect(Unit) {
        permissionsLauncher.launch(requiredPermissions)
        viewModel.startAutoDiscovery()
    }

    val operatingMode by viewModel.operatingMode.collectAsState()
    val isPttPressed by viewModel.isPttPressed.collectAsState()
    val isRecording by viewModel.isAudioRecording.collectAsState()
    val recordingSeconds by viewModel.recordingDurationSeconds.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val waveformSamples by viewModel.waveformSamples.collectAsState()
    val liveTranscript by viewModel.liveTranscript.collectAsState()
    val partialTranscript by viewModel.partialTranscript.collectAsState()
    val isTranscribing by viewModel.isTranscribing.collectAsState()

    val sttLang by viewModel.languageManager.sttLanguage.collectAsState()
    val ttsLang by viewModel.languageManager.ttsLanguage.collectAsState()

    val transportState by viewModel.transportManager.state.collectAsState()
    val peerName by viewModel.transportManager.connectedPeerName.collectAsState()
    val activeTransport by viewModel.transportManager.preferredTransport.collectAsState()
    val autoDiscoveredPeer by viewModel.autoDiscoveredPeer.collectAsState()

    val isAlertVisible by viewModel.isAlertDialogVisible.collectAsState()
    val activeAlertMessage by viewModel.activeAlertMessage.collectAsState()
    var quickText by remember { mutableStateOf("") }

    // Pulse animation for PTT glow
    val infiniteTransition = rememberInfiniteTransition(label = "pttPulse")
    val pttGlowScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoxDarkCanvas)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ==========================================
        // 1. TACTICAL TOP BAR
        // ==========================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                if (transportState == TransportState.CONNECTED) VoxEmeraldActive else VoxAmberWarning,
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "VOX LINK",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            color = VoxTextPrimary
                        )
                    )
                }
                Text(
                    text = "OFFLINE D2D VOICE COMMUNICATOR",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        letterSpacing = 1.sp,
                        color = VoxCyanPrimary
                    )
                )
            }

            Row {
                IconButton(
                    onClick = onNavigateDashboard,
                    modifier = Modifier.testTag("nav_dashboard")
                ) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = "Performance Dashboard",
                        tint = VoxCyanPrimary
                    )
                }
                IconButton(
                    onClick = onNavigateSettings,
                    modifier = Modifier.testTag("nav_settings")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = VoxTextSecondary
                    )
                }
            }
        }

        // ==========================================
        // 2. CONNECTION STATE CARD
        // ==========================================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateDevices() }
                .testTag("connection_card"),
            colors = CardDefaults.cardColors(containerColor = VoxDarkSurface),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, VoxDarkCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                if (transportState == TransportState.CONNECTED) VoxEmeraldActive.copy(alpha = 0.15f)
                                else VoxAmberWarning.copy(alpha = 0.15f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radio,
                            contentDescription = null,
                            tint = if (transportState == TransportState.CONNECTED) VoxEmeraldActive else VoxAmberWarning,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (transportState == TransportState.CONNECTED) "LINKED: ${peerName ?: "Direct Peer"}"
                            else "OFFLINE LINK: NOT CONNECTED",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (transportState == TransportState.CONNECTED) VoxTextPrimary else VoxAmberWarning
                            )
                        )
                        Text(
                            text = "Transport: ${activeTransport.name.replace("_", " ")} (No SIM / No Cloud)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = VoxTextSecondary
                            )
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .background(VoxDarkCard, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (transportState == TransportState.CONNECTED) "PAIR OK" else "SCAN",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = VoxCyanPrimary
                        )
                    )
                }
            }
        }

        // Auto-Discovered Peer Banner
        if (autoDiscoveredPeer != null && transportState != TransportState.CONNECTED) {
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.pairWithDevice(autoDiscoveredPeer!!) }
                    .testTag("auto_discovered_banner"),
                colors = CardDefaults.cardColors(containerColor = VoxDarkCard),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, VoxCyanPrimary)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(VoxCyanPrimary.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Devices,
                                contentDescription = "Nearby Peer",
                                tint = VoxCyanPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "⚡ Nearby VoxLink Found!",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = VoxCyanLight
                                )
                            )
                            Text(
                                text = autoDiscoveredPeer?.name ?: "Common App Device",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = VoxTextPrimary
                                )
                            )
                        }
                    }

                    androidx.compose.material3.Button(
                        onClick = { viewModel.pairWithDevice(autoDiscoveredPeer!!) },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = VoxCyanPrimary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (transportState == TransportState.CONNECTING) "LINKING..." else "PAIR NOW",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ==========================================
        // 3. OPERATING MODE SWITCHER
        // ==========================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(VoxDarkSurface, RoundedCornerShape(12.dp))
                .border(1.dp, VoxDarkCardBorder, RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
            val isPtt = operatingMode == OperatingMode.PUSH_TO_TALK
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isPtt) VoxCyanPrimary else Color.Transparent)
                    .clickable { viewModel.setOperatingMode(OperatingMode.PUSH_TO_TALK) }
                    .padding(vertical = 10.dp)
                    .testTag("mode_ptt"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "PUSH-TO-TALK (PTT)",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isPtt) Color.Black else VoxTextSecondary
                    )
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (!isPtt) VoxCyanPrimary else Color.Transparent)
                    .clickable { viewModel.setOperatingMode(OperatingMode.CONTINUOUS_CONVERSATION) }
                    .padding(vertical = 10.dp)
                    .testTag("mode_continuous"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CONTINUOUS (HANDS-FREE)",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (!isPtt) Color.Black else VoxTextSecondary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ==========================================
        // 4. ACTIVE LANGUAGE SELECTOR BANNER
        // ==========================================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateLanguages() }
                .testTag("language_banner"),
            colors = CardDefaults.cardColors(containerColor = VoxDarkSurface),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, VoxDarkCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
                        tint = VoxCyanPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "STT: ${sttLang.nativeName} (${sttLang.englishName})  →  TTS: ${ttsLang.nativeName}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = VoxTextPrimary
                            )
                        )
                        Text(
                            text = "Sample: \"${sttLang.sampleSentence.take(28)}...\"",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = VoxTextMuted
                            )
                        )
                    }
                }
                Text(
                    text = "CHANGE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = VoxCyanPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ==========================================
        // 5. REAL-TIME AUDIO WAVEFORM
        // ==========================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = VoxDarkSurface),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, VoxDarkCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isRecording || isPttPressed) "● RECORDING VOICE (16 kHz PCM)" else "MICROPHONE IDLE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isRecording || isPttPressed) VoxEmeraldActive else VoxTextMuted
                        )
                    )
                    Text(
                        text = if (isPttPressed) "PTT CAPTURE ACTIVE" else "VAD STANDBY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isPttPressed) VoxAmberWarning else VoxCyanPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                WaveformVisualizer(
                    amplitudes = waveformSamples,
                    isRecording = isRecording || isPttPressed
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ==========================================
        // 6. GIANT PTT MICROPHONE BUTTON
        // ==========================================
        Box(
            modifier = Modifier
                .size(190.dp)
                .testTag("ptt_button_container"),
            contentAlignment = Alignment.Center
        ) {
            // Animated outer glow when transmitting
            if (isPttPressed || (operatingMode == OperatingMode.CONTINUOUS_CONVERSATION && isRecording)) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .scale(pttGlowScale)
                        .background(VoxCyanGlow, CircleShape)
                )
            }

            // Outer ring
            Box(
                modifier = Modifier
                    .size(156.dp)
                    .background(
                        Brush.radialGradient(
                            colors = if (isPttPressed) listOf(VoxCyanPrimary, Color(0xFF0284C7))
                            else listOf(VoxDarkCard, VoxDarkSurface)
                        ),
                        CircleShape
                    )
                    .border(
                        3.dp,
                        if (isPttPressed) VoxCyanLight else VoxDarkCardBorder,
                        CircleShape
                    )
                    .pointerInput(operatingMode) {
                        if (operatingMode != OperatingMode.PUSH_TO_TALK) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                permissionsLauncher.launch(requiredPermissions)
                                return@awaitEachGesture
                            }
                            val pressStartTime = System.currentTimeMillis()
                            viewModel.onPttPressed()

                            var isStillPressed = true
                            while (isStillPressed) {
                                val event = awaitPointerEvent()
                                val anyPressed = event.changes.any { it.pressed }
                                if (!anyPressed) {
                                    isStillPressed = false
                                }
                                event.changes.forEach { it.consume() }
                            }
                            val duration = System.currentTimeMillis() - pressStartTime
                            // If held for >= 250ms, release upon lift. If quick tap, stay recording or toggle.
                            if (duration >= 250L) {
                                viewModel.onPttReleased()
                            }
                        }
                    }
                    .testTag("ptt_touch_area"),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (isRecording || isPttPressed) Icons.Default.Mic else Icons.Default.MicNone,
                        contentDescription = "PTT Microphone",
                        tint = if (isPttPressed) Color.Black else VoxCyanPrimary,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (operatingMode == OperatingMode.PUSH_TO_TALK) {
                            if (isPttPressed) {
                                val s = if (recordingSeconds < 10) "0$recordingSeconds" else "$recordingSeconds"
                                "RECORDING\n00:$s"
                            } else {
                                "HOLD TO\nSPEAK"
                            }
                        } else {
                            if (isRecording) "HANDS-FREE\nACTIVE" else "MUTED"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            textAlign = TextAlign.Center,
                            color = if (isPttPressed) Color.Black else VoxTextPrimary
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Tap-To-Talk Toggle Button
        Button(
            onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    permissionsLauncher.launch(requiredPermissions)
                } else {
                    viewModel.togglePtt()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isPttPressed) Color(0xFFDC2626) else VoxCyanPrimary.copy(alpha = 0.16f),
                contentColor = if (isPttPressed) Color.White else VoxCyanLight
            ),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isPttPressed) Color(0xFFDC2626) else VoxCyanPrimary.copy(alpha = 0.5f)
            ),
            modifier = Modifier.testTag("toggle_talk_btn")
        ) {
            Icon(
                imageVector = if (isPttPressed) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = "Toggle Recording",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isPttPressed) "STOP & SEND VOICE (${recordingSeconds}s)" else "TAP TO TALK (CLICK/TOGGLE)",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ==========================================
        // 7. LIVE TRANSCRIPTION CARD
        // ==========================================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("transcript_card"),
            colors = CardDefaults.cardColors(containerColor = VoxDarkSurface),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, VoxDarkCardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ON-DEVICE TRANSCRIPT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = VoxTextSecondary
                        )
                    )
                    if (isTranscribing) {
                        Text(
                            text = "● INFERENCE ACTIVE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = VoxEmeraldActive
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                val displayText = if (partialTranscript.isNotBlank()) partialTranscript
                else if (liveTranscript.isNotBlank()) liveTranscript
                else "Press and hold the microphone button to transmit speech offline..."

                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = if (partialTranscript.isNotBlank() || liveTranscript.isNotBlank()) FontWeight.Medium else FontWeight.Normal,
                        color = if (partialTranscript.isNotBlank() || liveTranscript.isNotBlank()) VoxTextPrimary else VoxTextMuted
                    ),
                    modifier = Modifier.testTag("transcript_text")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Tactical Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "🚨 Help!" to "Emergency! Need immediate backup at our coordinates.",
                "✅ All Safe" to "All clear, we reached the designated safe location.",
                "📻 Copy?" to "Team alpha, do you copy? What is your status?",
                "📍 Checkpoint" to "Checkpoint reached. Holding position."
            ).forEach { (label, textToSend) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(VoxDarkSurface)
                        .border(1.dp, VoxDarkCardBorder, RoundedCornerShape(8.dp))
                        .clickable { viewModel.sendTextMessage(textToSend) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = VoxCyanPrimary
                        ),
                        maxLines = 1
                    )
                }
            }
        }

        // Quick Text Message Input Field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = quickText,
                onValueChange = { quickText = it },
                placeholder = {
                    Text(
                        text = "Type message or test sentence...",
                        style = MaterialTheme.typography.bodySmall.copy(color = VoxTextMuted, fontSize = 12.sp)
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("quick_text_input"),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VoxCyanPrimary,
                    unfocusedBorderColor = VoxDarkCardBorder,
                    focusedTextColor = VoxTextPrimary,
                    unfocusedTextColor = VoxTextPrimary,
                    focusedContainerColor = VoxDarkSurface,
                    unfocusedContainerColor = VoxDarkSurface
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (quickText.isNotBlank()) {
                        viewModel.sendTextMessage(quickText)
                        quickText = ""
                    }
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(VoxCyanPrimary)
                    .size(48.dp)
                    .testTag("quick_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Text",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ==========================================
        // 8. TACTICAL STATUS CHIPS ROW
        // ==========================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatusPill(
                label = "STT",
                status = if (viewModel.sttEngine.isAvailable()) "READY" else "ONLINE",
                statusColor = VoxEmeraldActive,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            StatusPill(
                label = "LINK",
                status = if (transportState == TransportState.CONNECTED) "LINKED" else "READY",
                statusColor = if (transportState == TransportState.CONNECTED) VoxEmeraldActive else VoxCyanPrimary,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            StatusPill(
                label = "TTS",
                status = "OFFLINE",
                statusColor = VoxEmeraldActive,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ==========================================
        // 9. QUICK ACTION CONTROLS ROW
        // ==========================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Emergency Alert Button
            Box(
                modifier = Modifier
                    .weight(1.3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(VoxCrimsonDistress)
                    .clickable { viewModel.showAlertDialog(true) }
                    .padding(vertical = 14.dp)
                    .testTag("alert_button"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Alert",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "EMERGENCY ALERT",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = Color.White
                        )
                    )
                }
            }

            // History Button with Queued Transfer Badge
            val queuedTransferCount = messages.count {
                it.isOutgoing && (it.deliveryStatus == DeliveryStatus.QUEUED || it.deliveryStatus == DeliveryStatus.FAILED)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(VoxDarkSurface)
                    .border(
                        1.dp,
                        if (queuedTransferCount > 0) VoxAmberWarning else VoxDarkCardBorder,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onNavigateHistory() }
                    .padding(vertical = 14.dp)
                    .testTag("history_button"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "History",
                        tint = if (queuedTransferCount > 0) VoxAmberWarning else VoxCyanPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (queuedTransferCount > 0) "HISTORY ($queuedTransferCount)" else "HISTORY (${messages.size})",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (queuedTransferCount > 0) VoxAmberWarning else VoxTextPrimary
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Benchmark & Devices shortcuts
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(VoxDarkSurface)
                    .border(1.dp, VoxDarkCardBorder, RoundedCornerShape(12.dp))
                    .clickable { onNavigateDevices() }
                    .padding(vertical = 12.dp)
                    .testTag("devices_button"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = "Devices",
                        tint = VoxCyanPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "NEARBY DEVICES",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = VoxTextSecondary
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(VoxDarkSurface)
                    .border(1.dp, VoxDarkCardBorder, RoundedCornerShape(12.dp))
                    .clickable { onNavigateBenchmark() }
                    .padding(vertical = 12.dp)
                    .testTag("benchmark_button"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = "Benchmark",
                        tint = VoxEmeraldActive,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SIH BENCHMARK",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = VoxEmeraldActive
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // High-Priority Alert Received Dialog
    activeAlertMessage?.let { alertMsg ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { viewModel.dismissActiveAlert() }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2D0A0A)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(2.dp, VoxCrimsonDistress),
                modifier = Modifier.fillMaxWidth().testTag("active_alert_dialog")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = VoxCrimsonDistress,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "DISTRESS ALERT RECEIVED",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            color = VoxCrimsonDistress,
                            letterSpacing = 1.5.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "From: ${alertMsg.senderName}",
                        style = MaterialTheme.typography.labelSmall.copy(color = VoxTextSecondary)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "\"${alertMsg.text}\"",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(VoxCrimsonDistress)
                            .clickable { viewModel.dismissActiveAlert() }
                            .padding(vertical = 12.dp)
                            .testTag("ack_alert_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "ACKNOWLEDGE & DISMISS",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        )
                    }
                }
            }
        }
    }
}
