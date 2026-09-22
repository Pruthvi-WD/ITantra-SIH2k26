package com.example.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.communication.DiscoveredDevice
import com.example.communication.TransportState
import com.example.communication.TransportType
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
import kotlinx.coroutines.launch

@Composable
fun DevicesScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val transportState by viewModel.transportManager.state.collectAsState()
    val connectedPeerName by viewModel.transportManager.connectedPeerName.collectAsState()
    val discoveredDevices by viewModel.transportManager.discoveredDevices.collectAsState()
    val activeTransport by viewModel.transportManager.preferredTransport.collectAsState()

    var directIpInput by remember { mutableStateOf("192.168.43.1") }
    var isDirectConnecting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.transportManager.startDiscovery()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoxDarkCanvas)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    coroutineScope.launch { viewModel.transportManager.stopDiscovery() }
                    onNavigateBack()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = VoxTextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "DEVICE DISCOVERY",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = VoxTextPrimary
                        )
                    )
                    Text(
                        text = "ID: ${viewModel.transportManager.localDeviceId} • ${viewModel.transportManager.localDeviceName}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            color = VoxCyanPrimary
                        )
                    )
                }
            }

            IconButton(
                onClick = {
                    coroutineScope.launch {
                        viewModel.transportManager.startDiscovery()
                    }
                },
                modifier = Modifier.testTag("refresh_discovery_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Scan",
                    tint = VoxCyanPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Transport Type Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(VoxDarkSurface, RoundedCornerShape(10.dp))
                .border(1.dp, VoxDarkCardBorder, RoundedCornerShape(10.dp))
                .padding(4.dp)
        ) {
            val isWifi = activeTransport == TransportType.LOCAL_WIFI
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isWifi) VoxCyanPrimary else Color.Transparent)
                    .clickable { viewModel.transportManager.setPreferredTransport(TransportType.LOCAL_WIFI) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "LOCAL WI-FI",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isWifi) Color.Black else VoxTextSecondary
                    )
                )
            }

            val isBt = activeTransport == TransportType.BLUETOOTH
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isBt) VoxCyanPrimary else Color.Transparent)
                    .clickable { viewModel.transportManager.setPreferredTransport(TransportType.BLUETOOTH) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "BLUETOOTH",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isBt) Color.Black else VoxTextSecondary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Active Connection Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = VoxDarkSurface),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, VoxDarkCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "CONNECTION STATUS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = VoxTextSecondary
                        )
                    )
                    Text(
                        text = if (transportState == TransportState.CONNECTED) "LINKED: ${connectedPeerName ?: "Peer"}"
                        else "DISCONNECTED (SCANNING)",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (transportState == TransportState.CONNECTED) VoxEmeraldActive else VoxAmberWarning
                        )
                    )
                }

                if (transportState == TransportState.CONNECTED) {
                    Button(
                        onClick = {
                            coroutineScope.launch { viewModel.transportManager.disconnect() }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VoxDarkCardBorder),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("DISCONNECT", color = VoxTextPrimary, fontSize = 11.sp)
                    }
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = VoxCyanPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Direct IP Connect Card (For instant 2-Phone Hotspot Link)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = VoxDarkSurface),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, VoxDarkCardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "DIRECT HOTSPOT LINK",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = VoxCyanPrimary
                    )
                )
                Text(
                    text = "If device A turns on Hotspot, device B connects and links directly to gateway IP (192.168.43.1).",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        color = VoxTextMuted
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = directIpInput,
                        onValueChange = { directIpInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("direct_ip_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = VoxTextPrimary,
                            unfocusedTextColor = VoxTextPrimary,
                            focusedBorderColor = VoxCyanPrimary,
                            unfocusedBorderColor = VoxDarkCardBorder
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            isDirectConnecting = true
                            coroutineScope.launch {
                                viewModel.transportManager.connectDirectIp(directIpInput.trim())
                                isDirectConnecting = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VoxCyanPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("connect_direct_ip_button")
                    ) {
                        Text("LINK", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Discovered Devices List
        Text(
            text = "NEARBY PEERS (${discoveredDevices.size})",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = VoxTextSecondary
            )
        )
        Spacer(modifier = Modifier.height(6.dp))

        if (discoveredDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(VoxDarkSurface, RoundedCornerShape(12.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = VoxTextMuted,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Scanning for offline peers...",
                        style = MaterialTheme.typography.bodyMedium.copy(color = VoxTextMuted)
                    )
                    Text(
                        text = "Ensure other device has VoxLink open on same Wi-Fi/Hotspot or Bluetooth",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = VoxTextMuted
                        )
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(discoveredDevices) { device ->
                    DeviceItemCard(
                        device = device,
                        isConnected = device.name == connectedPeerName,
                        onConnect = {
                            coroutineScope.launch {
                                viewModel.transportManager.connect(device)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceItemCard(
    device: DiscoveredDevice,
    isConnected: Boolean,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onConnect() },
        colors = CardDefaults.cardColors(containerColor = VoxDarkSurface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isConnected) VoxEmeraldActive else VoxDarkCardBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(VoxDarkCard, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (device.transportType == TransportType.BLUETOOTH) Icons.Default.Bluetooth else Icons.Default.Wifi,
                        contentDescription = null,
                        tint = VoxCyanPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = VoxTextPrimary
                            )
                        )
                        if (device.isCommonApp) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(VoxCyanPrimary.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "VOXLINK PEER",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        color = VoxCyanPrimary
                                    )
                                )
                            }
                        }
                    }
                    Text(
                        text = "${device.transportType.name} • ${device.address}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = VoxTextSecondary
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isConnected) VoxEmeraldActive else VoxCyanPrimary)
                    .clickable { onConnect() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = when {
                        isConnected -> "LINKED"
                        device.isCommonApp -> "PAIR"
                        else -> "CONNECT"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                )
            }
        }
    }
}
