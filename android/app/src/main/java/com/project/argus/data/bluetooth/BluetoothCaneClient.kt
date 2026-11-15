package com.project.argus.data.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.project.argus.domain.bluetooth.BluetoothConnectionStatus
import com.project.argus.domain.bluetooth.CaneBluetoothClient
import com.project.argus.domain.model.CaneDirection
import com.project.argus.domain.model.CaneTelemetry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

/**
 * Android implementation of [CaneBluetoothClient] for HC-05 SPP Bluetooth Classic.
 *
 * Architectural constraints strictly enforced:
 * 1. UNIDIRECTIONAL: Cane -> Phone only. Absolutely NO OutputStream.write() or send() calls exist.
 * 2. Standard SPP UUID: 00001101-0000-1000-8000-00805F9B34FB.
 * 3. Thread safety & coroutine lifecycle managed on [ioDispatcher].
 * 4. Graceful handling of Android 12+ BLUETOOTH_CONNECT permission.
 */
class BluetoothCaneClient(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : CaneBluetoothClient {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _telemetryFlow = MutableStateFlow<CaneTelemetry?>(null)
    override val telemetryFlow: StateFlow<CaneTelemetry?> = _telemetryFlow.asStateFlow()

    private val _connectionStatus = MutableStateFlow(BluetoothConnectionStatus.DISCONNECTED)
    override val connectionStatus: StateFlow<BluetoothConnectionStatus> = _connectionStatus.asStateFlow()

    private var connectionJob: Job? = null
    private var demoJob: Job? = null
    private var activeSocket: BluetoothSocket? = null

    companion object {
        private const val TAG = "BluetoothCaneClient"
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val HC05_DEFAULT_NAME_PREFIX = "HC-05"
        private const val HC06_DEFAULT_NAME_PREFIX = "HC-06"
        private const val ARGUS_NAME_PREFIX = "ARGUS"

        internal fun isCaneDeviceName(name: String?): Boolean {
            if (name.isNullOrBlank()) return false
            return name.contains(HC05_DEFAULT_NAME_PREFIX, ignoreCase = true) ||
                name.contains(HC06_DEFAULT_NAME_PREFIX, ignoreCase = true) ||
                name.contains(ARGUS_NAME_PREFIX, ignoreCase = true)
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    override fun connect(targetAddress: String?) {
        if (!hasBluetoothConnectPermission()) {
            Log.w(TAG, "Cannot connect: BLUETOOTH_CONNECT permission missing")
            _connectionStatus.value = BluetoothConnectionStatus.PERMISSION_REQUIRED
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "Bluetooth adapter not available or disabled")
            _connectionStatus.value = BluetoothConnectionStatus.ERROR
            return
        }

        disconnect()

        connectionJob = coroutineScope.launch(ioDispatcher) {
            _connectionStatus.value = BluetoothConnectionStatus.CONNECTING
            val targetDevice: BluetoothDevice? = try {
                if (!targetAddress.isNullOrBlank()) {
                    adapter.getRemoteDevice(targetAddress)
                } else {
                    findPairedCaneDevice(adapter)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving Bluetooth cane device", e)
                _connectionStatus.value = BluetoothConnectionStatus.ERROR
                return@launch
            }

            if (targetDevice == null) {
                Log.w(TAG, "No paired HC-05/HC-06/Argus cane device found")
                _connectionStatus.value = BluetoothConnectionStatus.DISCONNECTED
                return@launch
            }

            connectAndReadSocket(
                adapter = adapter,
                targetDevice = targetDevice,
                setErrorStatusOnFailure = true
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectAndReadSocket(
        adapter: BluetoothAdapter,
        targetDevice: BluetoothDevice,
        setErrorStatusOnFailure: Boolean
    ): Boolean {
        return try {
            // Discovery must be canceled before connecting to avoid slow connections
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }

            Log.d(TAG, "Connecting to ${targetDevice.name} (${targetDevice.address})...")
            val socket = targetDevice.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
            activeSocket = socket
            _connectionStatus.value = BluetoothConnectionStatus.CONNECTED
            Log.i(TAG, "Successfully connected to cane via SPP")

            // Start unidirectional read loop
            readTelemetryLoop(socket)
            true
        } catch (e: CancellationException) {
            closeSocketSilently()
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to Bluetooth cane", e)
            closeSocketSilently()
            if (setErrorStatusOnFailure) {
                _connectionStatus.value = BluetoothConnectionStatus.ERROR
            }
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun findPairedCaneDevice(adapter: BluetoothAdapter): BluetoothDevice? {
        val pairedDevices = adapter.bondedDevices ?: return null
        return pairedDevices.firstOrNull { device -> isCaneDeviceName(device.name) }
    }

    private fun readTelemetryLoop(socket: BluetoothSocket) {
        val reader = BufferedReader(InputStreamReader(socket.inputStream))
        try {
            while (coroutineScope.isActive && socket.isConnected) {
                val line = reader.readLine() ?: break
                if (line.isNotBlank()) {
                    val telemetry = CaneTelemetry.parse(line)
                    if (telemetry != null) {
                        _telemetryFlow.value = telemetry
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bluetooth read stream ended or encountered an error: ${e.message}")
        } finally {
            closeSocketSilently()
            _connectionStatus.value = BluetoothConnectionStatus.DISCONNECTED
        }
    }

    override fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        demoJob?.cancel()
        demoJob = null
        closeSocketSilently()
        _connectionStatus.value = BluetoothConnectionStatus.DISCONNECTED
    }

    private fun closeSocketSilently() {
        try {
            activeSocket?.close()
        } catch (ignored: Exception) {
        } finally {
            activeSocket = null
        }
    }

    @SuppressLint("MissingPermission")
    override fun triggerDemoOrReconnect() {
        if (_connectionStatus.value == BluetoothConnectionStatus.CONNECTED) {
            Log.d(TAG, "Reconnect requested while connected -> reconnecting socket")
            connect()
            return
        }

        val adapter = bluetoothAdapter
        val pairedCane = if (hasBluetoothConnectPermission() && adapter != null && adapter.isEnabled) {
            findPairedCaneDevice(adapter)
        } else {
            null
        }

        if (pairedCane == null || adapter == null) {
            Log.d(TAG, "No connected or paired HC-05/HC-06/ARGUS found -> triggering local demo mode")
            runLocalDemoSequence()
            return
        }

        disconnect()
        connectionJob = coroutineScope.launch(ioDispatcher) {
            _connectionStatus.value = BluetoothConnectionStatus.CONNECTING
            val connected = connectAndReadSocket(
                adapter = adapter,
                targetDevice = pairedCane,
                setErrorStatusOnFailure = false
            )
            if (!connected && isActive) {
                Log.d(TAG, "Socket connection failed -> falling back to local demo mode")
                emitDemoSequence()
            }
        }
    }

    /**
     * Local test simulation for jury presentation when hardware cane is not connected.
     * Emits realistic telemetry events locally without sending anything to hardware.
     */
    private fun runLocalDemoSequence() {
        disconnect()
        demoJob = coroutineScope.launch(ioDispatcher) {
            emitDemoSequence()
        }
    }

    private suspend fun emitDemoSequence() {
        _connectionStatus.value = BluetoothConnectionStatus.DEMO_MODE
        // Demo sequence: CENTER 60cm -> LEFT 28cm (critical) -> RIGHT 45cm
        _telemetryFlow.value = CaneTelemetry(distanceCm = 60, direction = CaneDirection.CENTER)
        kotlinx.coroutines.delay(1200)
        _telemetryFlow.value = CaneTelemetry(distanceCm = 28, direction = CaneDirection.LEFT)
        kotlinx.coroutines.delay(1500)
        _telemetryFlow.value = CaneTelemetry(distanceCm = 45, direction = CaneDirection.RIGHT)
    }
}
