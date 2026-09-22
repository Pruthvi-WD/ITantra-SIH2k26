package com.example.communication

import android.content.Context
import android.os.Build
import com.example.communication.bluetooth.BluetoothTransport
import com.example.communication.wifi.LocalWiFiTransport
import com.example.domain.model.MessagePacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Transport Manager with automatic fallback:
 * Preferred: Local Wi-Fi / Hotspot -> Fallback: Bluetooth Classic RFCOMM.
 */
class TransportManager(
    context: Context,
    val localDeviceId: String = UUID.randomUUID().toString().substring(0, 8),
    val localDeviceName: String = "VoxUnit-${Build.MODEL.take(8)}"
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    val wifiTransport = LocalWiFiTransport(context, localDeviceId, localDeviceName)
    val bluetoothTransport = BluetoothTransport(context, localDeviceId, localDeviceName)

    private val _preferredTransport = MutableStateFlow(TransportType.LOCAL_WIFI)
    val preferredTransport: StateFlow<TransportType> = _preferredTransport.asStateFlow()

    private val _activeTransport = MutableStateFlow<Transport>(wifiTransport)
    val activeTransport: StateFlow<Transport> = _activeTransport.asStateFlow()

    private val _state = MutableStateFlow(TransportState.DISCONNECTED)
    val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _connectedPeerName = MutableStateFlow<String?>(null)
    val connectedPeerName: StateFlow<String?> = _connectedPeerName.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<MessagePacket>(extraBufferCapacity = 64)
    val incomingPackets: SharedFlow<MessagePacket> = _incomingPackets.asSharedFlow()

    private val _autoDiscoveredPeer = MutableStateFlow<DiscoveredDevice?>(null)
    val autoDiscoveredPeer: StateFlow<DiscoveredDevice?> = _autoDiscoveredPeer.asStateFlow()

    var isAutoConnectEnabled: Boolean = true
    private var isAutoConnecting = false

    init {
        // Forward incoming packets from both transports
        scope.launch {
            wifiTransport.incomingPackets.collect { packet ->
                _incomingPackets.emit(packet)
            }
        }
        scope.launch {
            bluetoothTransport.incomingPackets.collect { packet ->
                _incomingPackets.emit(packet)
            }
        }

        // Dynamically track active connection state from whichever transport is connected
        scope.launch {
            wifiTransport.state.collect { wifiState ->
                if (wifiState == TransportState.CONNECTED) {
                    _activeTransport.value = wifiTransport
                    _state.value = TransportState.CONNECTED
                    _connectedPeerName.value = wifiTransport.connectedPeerName.value
                } else if (_activeTransport.value == wifiTransport) {
                    if (bluetoothTransport.state.value == TransportState.CONNECTED) {
                        _activeTransport.value = bluetoothTransport
                        _state.value = TransportState.CONNECTED
                        _connectedPeerName.value = bluetoothTransport.connectedPeerName.value
                    } else {
                        _state.value = wifiState
                        _connectedPeerName.value = wifiTransport.connectedPeerName.value
                    }
                }
            }
        }
        scope.launch {
            bluetoothTransport.state.collect { btState ->
                if (btState == TransportState.CONNECTED) {
                    _activeTransport.value = bluetoothTransport
                    _state.value = TransportState.CONNECTED
                    _connectedPeerName.value = bluetoothTransport.connectedPeerName.value
                } else if (_activeTransport.value == bluetoothTransport) {
                    if (wifiTransport.state.value == TransportState.CONNECTED) {
                        _activeTransport.value = wifiTransport
                        _state.value = TransportState.CONNECTED
                        _connectedPeerName.value = wifiTransport.connectedPeerName.value
                    } else {
                        _state.value = btState
                        _connectedPeerName.value = bluetoothTransport.connectedPeerName.value
                    }
                }
            }
        }
    }

    suspend fun startAllServers() {
        wifiTransport.startServer()
        bluetoothTransport.startServer()
    }

    fun setPreferredTransport(type: TransportType) {
        _preferredTransport.value = type
        _activeTransport.value = when (type) {
            TransportType.LOCAL_WIFI -> wifiTransport
            TransportType.BLUETOOTH -> bluetoothTransport
            TransportType.WIFI_DIRECT -> wifiTransport
        }
    }

    suspend fun startDiscovery() {
        _discoveredDevices.value = emptyList()
        val deviceMap = mutableMapOf<String, DiscoveredDevice>()

        val onFound: (DiscoveredDevice) -> Unit = { device ->
            synchronized(deviceMap) {
                deviceMap[device.id] = device
                _discoveredDevices.value = deviceMap.values.toList()
            }

            // Detect devices running this application in common
            if (device.isCommonApp) {
                _autoDiscoveredPeer.value = device
                if (isAutoConnectEnabled &&
                    _state.value != TransportState.CONNECTED &&
                    !isAutoConnecting
                ) {
                    scope.launch {
                        try {
                            isAutoConnecting = true
                            // Small tie-break delay so both devices don't collide
                            kotlinx.coroutines.delay((100..400).random().toLong())
                            if (_state.value != TransportState.CONNECTED) {
                                connect(device)
                            }
                        } finally {
                            isAutoConnecting = false
                        }
                    }
                }
            }
        }

        wifiTransport.startDiscovery(onFound)
        bluetoothTransport.startDiscovery(onFound)
    }

    suspend fun stopDiscovery() {
        wifiTransport.stopDiscovery()
        bluetoothTransport.stopDiscovery()
    }

    suspend fun connect(device: DiscoveredDevice): Boolean {
        val transport = when (device.transportType) {
            TransportType.LOCAL_WIFI, TransportType.WIFI_DIRECT -> wifiTransport
            TransportType.BLUETOOTH -> bluetoothTransport
        }
        _activeTransport.value = transport
        val success = transport.connect(device)
        if (success) {
            _connectedPeerName.value = device.name
            _state.value = TransportState.CONNECTED
        }
        return success
    }

    suspend fun connectDirectIp(ip: String, port: Int = LocalWiFiTransport.TCP_PORT): Boolean {
        val dev = DiscoveredDevice(
            id = "manual_$ip",
            name = "Direct ($ip)",
            transportType = TransportType.LOCAL_WIFI,
            address = ip,
            port = port,
            signalQuality = "Direct"
        )
        return connect(dev)
    }

    suspend fun sendPacket(packet: MessagePacket): Boolean {
        val active = _activeTransport.value
        var success = active.sendPacket(packet)

        // Fallback to alternative transport if primary fails and alternative is connected
        if (!success) {
            val fallback = if (active == wifiTransport) bluetoothTransport else wifiTransport
            if (fallback.state.value == TransportState.CONNECTED) {
                success = fallback.sendPacket(packet)
                if (success) {
                    _activeTransport.value = fallback
                }
            }
        }
        return success
    }

    suspend fun disconnect() {
        wifiTransport.disconnect()
        bluetoothTransport.disconnect()
        _connectedPeerName.value = null
        _state.value = TransportState.DISCONNECTED
    }

    fun release() {
        wifiTransport.release()
        bluetoothTransport.release()
    }
}
