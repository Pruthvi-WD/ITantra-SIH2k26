package com.example.communication.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.example.communication.DiscoveredDevice
import com.example.communication.Transport
import com.example.communication.TransportState
import com.example.communication.TransportType
import com.example.domain.model.MessagePacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID

/**
 * Offline Bluetooth Classic RFCOMM / SPP Transport.
 * Establishes ad-hoc peer-to-peer serial communication links when Wi-Fi is disabled.
 */
class BluetoothTransport(
    private val context: Context,
    private val localDeviceId: String,
    private val localDeviceName: String
) : Transport {

    companion object {
        // Standard SPP (Serial Port Profile) UUID
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val SERVICE_NAME = "VoxLinkSPP"
        private const val TAG = "BluetoothTransport"
    }

    override val transportType: TransportType = TransportType.BLUETOOTH

    private val _state = MutableStateFlow(TransportState.DISCONNECTED)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _connectedPeerName = MutableStateFlow<String?>(null)
    override val connectedPeerName: StateFlow<String?> = _connectedPeerName.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<MessagePacket>(extraBufferCapacity = 64)
    override val incomingPackets: SharedFlow<MessagePacket> = _incomingPackets.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO)
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
    }

    private var serverSocket: BluetoothServerSocket? = null
    private var serverJob: Job? = null
    private var activeSocket: BluetoothSocket? = null
    private var socketWriter: BufferedWriter? = null
    private var readJob: Job? = null
    private var discoveryReceiver: BroadcastReceiver? = null

    @SuppressLint("MissingPermission")
    override suspend fun startServer() = withContext(Dispatchers.IO) {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "Bluetooth not available or not enabled")
            return@withContext
        }

        try {
            serverSocket?.close()
            serverSocket = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SPP_UUID)

            serverJob?.cancel()
            serverJob = scope.launch {
                while (isActive) {
                    try {
                        val socket = serverSocket?.accept() ?: break
                        val peerName = try { socket.remoteDevice.name ?: socket.remoteDevice.address } catch (e: Exception) { "BT Peer" }
                        Log.i(TAG, "Bluetooth RFCOMM connection accepted from: $peerName")
                        setupSocketIO(socket, peerName)
                    } catch (e: Exception) {
                        if (!isActive) break
                        Log.w(TAG, "Bluetooth server accept terminated", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Bluetooth server socket", e)
            _state.value = TransportState.ERROR
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun startDiscovery(onDeviceFound: (DiscoveredDevice) -> Unit) = withContext(Dispatchers.IO) {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _state.value = TransportState.ERROR
            return@withContext
        }

        _state.value = TransportState.SCANNING

        // 1. First report bonded/paired devices
        try {
            val bonded = adapter.bondedDevices
            bonded?.forEach { device ->
                val name = device.name ?: "Bluetooth Device"
                val isCommon = name.contains("VoxUnit", ignoreCase = true) || name.contains("VoxLink", ignoreCase = true)
                val dev = DiscoveredDevice(
                    id = device.address,
                    name = if (isCommon) name else "$name (Paired)",
                    transportType = TransportType.BLUETOOTH,
                    address = device.address,
                    signalQuality = "Good",
                    isCommonApp = isCommon
                )
                onDeviceFound(dev)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying bonded devices", e)
        }

        // 2. Start active discovery for nearby devices
        try {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }

            discoveryReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (BluetoothDevice.ACTION_FOUND == intent?.action) {
                        val device: BluetoothDevice? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        device?.let {
                            try {
                                val name = it.name ?: "BT Device (${it.address})"
                                val isCommon = name.contains("VoxUnit", ignoreCase = true) || name.contains("VoxLink", ignoreCase = true)
                                val dev = DiscoveredDevice(
                                    id = it.address,
                                    name = name,
                                    transportType = TransportType.BLUETOOTH,
                                    address = it.address,
                                    signalQuality = if (isCommon) "Excellent" else "Fair",
                                    isCommonApp = isCommon
                                )
                                onDeviceFound(dev)
                            } catch (e: Exception) {
                                // Missing permission
                            }
                        }
                    }
                }
            }

            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            context.registerReceiver(discoveryReceiver, filter)
            adapter.startDiscovery()
        } catch (e: Exception) {
            Log.w(TAG, "Bluetooth discovery error", e)
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun stopDiscovery() = withContext(Dispatchers.IO) {
        try {
            bluetoothAdapter?.cancelDiscovery()
            discoveryReceiver?.let {
                context.unregisterReceiver(it)
                discoveryReceiver = null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping discovery", e)
        }
        if (_state.value == TransportState.SCANNING) {
            _state.value = TransportState.DISCONNECTED
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(device: DiscoveredDevice): Boolean = withContext(Dispatchers.IO) {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) return@withContext false

        try {
            _state.value = TransportState.CONNECTING
            adapter.cancelDiscovery()

            val remoteDevice = adapter.getRemoteDevice(device.address)
            var socket: BluetoothSocket? = null

            // Try standard RFCOMM first
            try {
                socket = remoteDevice.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
            } catch (e1: Exception) {
                Log.w(TAG, "Insecure RFCOMM failed, trying secure RFCOMM: ${e1.message}")
                try {
                    socket?.close()
                    socket = remoteDevice.createRfcommSocketToServiceRecord(SPP_UUID)
                    socket.connect()
                } catch (e2: Exception) {
                    Log.w(TAG, "Secure RFCOMM failed, trying reflection fallback: ${e2.message}")
                    try {
                        socket?.close()
                        val m = remoteDevice.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                        socket = m.invoke(remoteDevice, 1) as BluetoothSocket
                        socket.connect()
                    } catch (e3: Exception) {
                        Log.e(TAG, "All Bluetooth connection attempts failed to ${device.address}", e3)
                        socket?.close()
                        _state.value = TransportState.ERROR
                        return@withContext false
                    }
                }
            }

            if (socket != null && socket.isConnected) {
                setupSocketIO(socket, device.name)
                return@withContext true
            } else {
                _state.value = TransportState.ERROR
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bluetooth connect failed to ${device.address}", e)
            _state.value = TransportState.ERROR
            return@withContext false
        }
    }

    private fun setupSocketIO(socket: BluetoothSocket, peerName: String) {
        try {
            activeSocket?.close()
            activeSocket = socket
            socketWriter = BufferedWriter(OutputStreamWriter(socket.outputStream))
            _connectedPeerName.value = peerName
            _state.value = TransportState.CONNECTED

            readJob?.cancel()
            readJob = scope.launch {
                val reader = BufferedReader(InputStreamReader(socket.inputStream))
                try {
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        if (line.isNotBlank()) {
                            try {
                                val packet = MessagePacket.fromJson(line)
                                if (packet.isChecksumValid()) {
                                    _incomingPackets.emit(packet)
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Error parsing BT packet", e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "BT socket read closed", e)
                } finally {
                    disconnect()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up BT socket IO", e)
            _state.value = TransportState.ERROR
        }
    }

    override suspend fun sendPacket(packet: MessagePacket): Boolean = withContext(Dispatchers.IO) {
        try {
            val writer = socketWriter
            if (writer == null || activeSocket?.isConnected != true) {
                return@withContext false
            }
            val json = packet.withComputedChecksum().toJson()
            writer.write(json)
            writer.newLine()
            writer.flush()
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "BT send error", e)
            disconnect()
            return@withContext false
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try {
                socketWriter?.close()
                socketWriter = null
                activeSocket?.close()
                activeSocket = null
                readJob?.cancel()
                readJob = null
                _connectedPeerName.value = null
                _state.value = TransportState.DISCONNECTED
            } catch (e: Exception) {
                Log.w(TAG, "BT disconnect error", e)
            }
        }
    }

    override fun release() {
        scope.launch {
            stopDiscovery()
            disconnect()
            serverJob?.cancel()
            serverSocket?.close()
            serverSocket = null
        }
    }
}
