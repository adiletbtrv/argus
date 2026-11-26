package com.project.argus.domain.bluetooth

import com.project.argus.domain.model.CaneTelemetry
import kotlinx.coroutines.flow.StateFlow

enum class BluetoothConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DEMO_MODE,
    PERMISSION_REQUIRED,
    ERROR
}

// Domain boundary interface for Bluetooth connectivity to the Argus Smart Cane.
// Adheres strictly to unidirectional Cane to Phone communication.
// No sending or writing operations exist in this interface.
interface CaneBluetoothClient {
    val telemetryFlow: StateFlow<CaneTelemetry?>
    val connectionStatus: StateFlow<BluetoothConnectionStatus>

    // Attempts connection to paired or discovered HC-05 device.
    fun connect(targetAddress: String? = null)

    // Closes socket and disconnects without sending any bytes to the cane.
    fun disconnect()

    // Triggers a local test demo or socket reconnect for live demonstration.
    fun triggerDemoOrReconnect()
}
