package com.example.communication.wifi

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.example.communication.DiscoveredDevice
import com.example.communication.Transport
import com.example.communication.TransportState
import com.example.communication.TransportType
import com.example.domain.model.MessagePacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Local Wi-Fi / Hotspot Transport.
 * Transmits semantic text packets over local TCP/UDP sockets with zero cloud or internet reliance.
 * Uses UDP broadcast beacons (Port 48770) for discovery and TCP streams (Port 48771) for packet delivery.
 */
class LocalWiFiTransport(
    private val context: Context,
    private val localDeviceId: String,
    private val localDeviceName: String
) : Transport {

    companion object {
        const val DISCOVERY_PORT = 48770
        const val TCP_PORT = 48771
        private const val TAG = "LocalWiFiTransport"
    }

    override val transportType: TransportType = TransportType.LOCAL_WIFI

    private val _state = MutableStateFlow(TransportState.DISCONNECTED)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _connectedPeerName = MutableStateFlow<String?>(null)
    override val connectedPeerName: StateFlow<String?> = _connectedPeerName.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<MessagePacket>(extraBufferCapacity = 64)
    override val incomingPackets: SharedFlow<MessagePacket> = _incomingPackets.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var clientSocket: Socket? = null
    private var clientWriter: BufferedWriter? = null
    private var readJob: Job? = null
    private var beaconJob: Job? = null
    private var beaconListenJob: Job? = null

    private var multicastLock: WifiManager.MulticastLock? = null

    init {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("voxlink_multicast").apply {
                setReferenceCounted(true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "MulticastLock unavailable", e)
        }
    }

    override suspend fun startServer() = withContext(Dispatchers.IO) {
        try {
            serverSocket?.close()
            serverSocket = ServerSocket(TCP_PORT).apply {
                reuseAddress = true
            }
            _state.value = TransportState.DISCONNECTED

            serverJob?.cancel()
            serverJob = scope.launch {
                while (isActive) {
                    try {
                        val socket = serverSocket?.accept() ?: break
                        Log.i(TAG, "TCP connection accepted from: ${socket.inetAddress.hostAddress}")
                        setupSocketIO(socket, "Peer (${socket.inetAddress.hostAddress})")
                    } catch (e: Exception) {
                        if (!isActive) break
                        Log.w(TAG, "Server socket accept error", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind server socket", e)
            _state.value = TransportState.ERROR
        }
    }

    override suspend fun startDiscovery(onDeviceFound: (DiscoveredDevice) -> Unit) = withContext(Dispatchers.IO) {
        multicastLock?.acquire()
        _state.value = TransportState.SCANNING

        // 1. Listen for UDP broadcast beacons
        beaconListenJob?.cancel()
        beaconListenJob = scope.launch {
            var datagramSocket: DatagramSocket? = null
            try {
                datagramSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(DISCOVERY_PORT))
                }
                val buffer = ByteArray(1024)

                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    datagramSocket.receive(packet)
                    val jsonStr = String(packet.data, 0, packet.length)
                    try {
                        val obj = JSONObject(jsonStr)
                        val id = obj.getString("id")
                        val name = obj.getString("name")
                        val port = obj.optInt("port", TCP_PORT)
                        val peerIp = packet.address.hostAddress ?: continue

                        if (id != localDeviceId) {
                            val device = DiscoveredDevice(
                                id = id,
                                name = name,
                                transportType = TransportType.LOCAL_WIFI,
                                address = peerIp,
                                port = port,
                                signalQuality = "Excellent",
                                isCommonApp = true
                            )
                            onDeviceFound(device)
                        }
                    } catch (e: Exception) {
                        // Malformed packet
                    }
                }
            } catch (e: Exception) {
                if (isActive) Log.w(TAG, "Beacon listener error", e)
            } finally {
                datagramSocket?.close()
            }
        }

        // 2. Broadcast our own presence beacon periodically
        beaconJob?.cancel()
        beaconJob = scope.launch {
            var broadcastSocket: DatagramSocket? = null
            try {
                broadcastSocket = DatagramSocket().apply {
                    broadcast = true
                }
                val beaconJson = JSONObject().apply {
                    put("id", localDeviceId)
                    put("name", localDeviceName)
                    put("port", TCP_PORT)
                    put("app", "VoxLink")
                }.toString()
                val bytes = beaconJson.toByteArray()
                val broadcastAddr = InetAddress.getByName("255.255.255.255")
                val packet = DatagramPacket(bytes, bytes.size, broadcastAddr, DISCOVERY_PORT)

                while (isActive) {
                    broadcastSocket.send(packet)
                    delay(1200)
                }
            } catch (e: Exception) {
                if (isActive) Log.w(TAG, "Beacon broadcast error", e)
            } finally {
                broadcastSocket?.close()
            }
        }
    }

    override suspend fun stopDiscovery() = withContext(Dispatchers.IO) {
        beaconJob?.cancel()
        beaconJob = null
        beaconListenJob?.cancel()
        beaconListenJob = null
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (e: Exception) {
            // Ignored
        }
        if (_state.value == TransportState.SCANNING) {
            _state.value = TransportState.DISCONNECTED
        }
    }

    override suspend fun connect(device: DiscoveredDevice): Boolean = withContext(Dispatchers.IO) {
        try {
            _state.value = TransportState.CONNECTING
            val socket = Socket()
            socket.connect(InetSocketAddress(device.address, device.port), 4000)
            setupSocketIO(socket, device.name)
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to ${device.address}:${device.port}", e)
            _state.value = TransportState.ERROR
            return@withContext false
        }
    }

    private fun setupSocketIO(socket: Socket, peerName: String) {
        try {
            clientSocket?.close()
            clientSocket = socket
            clientWriter = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
            _connectedPeerName.value = peerName
            _state.value = TransportState.CONNECTED

            readJob?.cancel()
            readJob = scope.launch {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                try {
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        if (line.isNotBlank()) {
                            try {
                                val packet = MessagePacket.fromJson(line)
                                if (packet.isChecksumValid()) {
                                    _incomingPackets.emit(packet)
                                } else {
                                    Log.w(TAG, "Received packet with invalid checksum: ${packet.messageId}")
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Error parsing incoming packet JSON: $line", e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Socket read terminated", e)
                } finally {
                    disconnect()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Socket setup failed", e)
            _state.value = TransportState.ERROR
        }
    }

    override suspend fun sendPacket(packet: MessagePacket): Boolean = withContext(Dispatchers.IO) {
        try {
            val writer = clientWriter
            if (writer == null || clientSocket?.isConnected != true) {
                Log.w(TAG, "Cannot send packet: socket not connected")
                return@withContext false
            }
            val json = packet.withComputedChecksum().toJson()
            writer.write(json)
            writer.newLine()
            writer.flush()
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Send packet error", e)
            disconnect()
            return@withContext false
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try {
                clientWriter?.close()
                clientWriter = null
                clientSocket?.close()
                clientSocket = null
                readJob?.cancel()
                readJob = null
                _connectedPeerName.value = null
                _state.value = TransportState.DISCONNECTED
            } catch (e: Exception) {
                Log.w(TAG, "Error closing client socket", e)
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
