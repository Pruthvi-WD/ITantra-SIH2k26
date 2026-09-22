package com.example.communication

data class DiscoveredDevice(
    val id: String,
    val name: String,
    val transportType: TransportType,
    val address: String, // IP or BT MAC
    val port: Int = 48771,
    val signalQuality: String = "Good", // "Excellent", "Good", "Fair"
    val isConnected: Boolean = false,
    val isCommonApp: Boolean = false,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)
