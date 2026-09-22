package com.example.communication

import com.example.domain.model.MessagePacket
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface Transport {
    val transportType: TransportType
    val state: StateFlow<TransportState>
    val connectedPeerName: StateFlow<String?>
    val incomingPackets: SharedFlow<MessagePacket>

    suspend fun startServer()
    suspend fun startDiscovery(onDeviceFound: (DiscoveredDevice) -> Unit)
    suspend fun stopDiscovery()
    suspend fun connect(device: DiscoveredDevice): Boolean
    suspend fun sendPacket(packet: MessagePacket): Boolean
    suspend fun disconnect()
    fun release()
}
