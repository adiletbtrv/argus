package com.project.argus.domain.bluetooth

import com.project.argus.domain.model.CaneTelemetry
import kotlinx.coroutines.flow.StateFlow

enum class BluetoothConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    PERMISSION_REQUIRED,
    ERROR
}

/**
 * Domain boundary interface for Bluetooth connectivity to the Argus Smart Cane.
 * Adheres strictly to unidirectional Cane -> Phone communication:
 * NO sending/writing operations exist in this interface.
 */
interface CaneBluetoothClient {
    val telemetryFlow: StateFlow<CaneTelemetry?>
    val connectionStatus: StateFlow<BluetoothConnectionStatus>

    /**
     * Attempts connection to paired or discovered HC-05 device.
     * @param targetAddress Optional MAC address; if null, defaults to first paired HC-05 / Argus device.
     */
    fun connect(targetAddress: String? = null)

    /**
     * Closes socket and disconnects without sending any bytes to the cane.
     */
    fun disconnect()

    /**
     * Triggers a local test demo or socket reconnect for live demonstration.
     */
    fun triggerDemoOrReconnect()
}
