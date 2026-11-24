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
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

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
    private val activeSocket = AtomicReference<BluetoothSocket?>(null)
    private val connectionGeneration = AtomicLong(0L)

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

    private fun hasBluetoothScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
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
        val generation = connectionGeneration.incrementAndGet()

        connectionJob = coroutineScope.launch(ioDispatcher) {
            if (connectionGeneration.get() != generation) return@launch
            _connectionStatus.value = BluetoothConnectionStatus.CONNECTING

            val targetDevice: BluetoothDevice? = try {
                if (!targetAddress.isNullOrBlank()) {
                    adapter.getRemoteDevice(targetAddress)
                } else {
                    findPairedCaneDevice(adapter)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving Bluetooth cane device", e)
                if (connectionGeneration.get() == generation) {
                    _connectionStatus.value = BluetoothConnectionStatus.ERROR
                }
                return@launch
            }

            if (connectionGeneration.get() != generation) return@launch

            if (targetDevice == null) {
                Log.w(TAG, "No paired HC-05/HC-06/Argus cane device found")
                if (connectionGeneration.get() == generation) {
                    _connectionStatus.value = BluetoothConnectionStatus.DISCONNECTED
                }
                return@launch
            }

            connectAndReadSocket(
                adapter = adapter,
                targetDevice = targetDevice,
                generation = generation,
                setErrorStatusOnFailure = true
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectAndReadSocket(
        adapter: BluetoothAdapter,
        targetDevice: BluetoothDevice,
        generation: Long,
        setErrorStatusOnFailure: Boolean
    ): Boolean {
        var socket: BluetoothSocket? = null
        return try {
            if (connectionGeneration.get() != generation) {
                return false
            }

            // Cancel discovery when BLUETOOTH_SCAN is granted to avoid slow connections
            if (hasBluetoothScanPermission()) {
                runCatching {
                    if (adapter.isDiscovering) {
                        adapter.cancelDiscovery()
                    }
                }
            }

            if (connectionGeneration.get() != generation) {
                return false
            }

            Log.d(TAG, "Connecting to ${targetDevice.name} (${targetDevice.address})...")
            val createdSocket = targetDevice.createRfcommSocketToServiceRecord(SPP_UUID)
            socket = createdSocket
            if (connectionGeneration.get() != generation) {
                runCatching { createdSocket.close() }
                return false
            }

            activeSocket.set(createdSocket)
            if (connectionGeneration.get() != generation) {
                closeOwnedSocket(createdSocket)
                return false
            }

            createdSocket.connect()
            if (connectionGeneration.get() != generation) {
                closeOwnedSocket(createdSocket)
                return false
            }

            _connectionStatus.value = BluetoothConnectionStatus.CONNECTED
            Log.i(TAG, "Successfully connected to cane via SPP")

            // Start unidirectional read loop
            readTelemetryLoop(createdSocket, generation)
            true
        } catch (e: CancellationException) {
            closeOwnedSocket(socket)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to Bluetooth cane", e)
            closeOwnedSocket(socket)
            if (setErrorStatusOnFailure && connectionGeneration.get() == generation) {
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

    private fun readTelemetryLoop(socket: BluetoothSocket, generation: Long) {
        val reader = BufferedReader(InputStreamReader(socket.inputStream))
        try {
            while (coroutineScope.isActive && socket.isConnected && connectionGeneration.get() == generation) {
                val line = reader.readLine() ?: break
                if (connectionGeneration.get() != generation) break
                if (line.isNotBlank()) {
                    val telemetry = CaneTelemetry.parse(line)
                    if (telemetry != null && connectionGeneration.get() == generation) {
                        _telemetryFlow.value = telemetry
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bluetooth read stream ended or encountered an error: ${e.message}")
        } finally {
            closeOwnedSocket(socket)
            if (connectionGeneration.get() == generation) {
                _connectionStatus.value = BluetoothConnectionStatus.DISCONNECTED
            }
        }
    }

    override fun disconnect() {
        connectionGeneration.incrementAndGet()
        connectionJob?.cancel()
        connectionJob = null
        demoJob?.cancel()
        demoJob = null
        activeSocket.getAndSet(null)?.let { socket ->
            runCatching { socket.close() }
        }
        _connectionStatus.value = BluetoothConnectionStatus.DISCONNECTED
    }

    private fun closeOwnedSocket(socket: BluetoothSocket?) {
        if (socket == null) return
        activeSocket.compareAndSet(socket, null)
        runCatching { socket.close() }
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
        val generation = connectionGeneration.incrementAndGet()
        connectionJob = coroutineScope.launch(ioDispatcher) {
            if (connectionGeneration.get() != generation) return@launch
            _connectionStatus.value = BluetoothConnectionStatus.CONNECTING
            val connected = connectAndReadSocket(
                adapter = adapter,
                targetDevice = pairedCane,
                generation = generation,
                setErrorStatusOnFailure = false
            )
            if (!connected && isActive && connectionGeneration.get() == generation) {
                Log.d(TAG, "Socket connection failed -> falling back to local demo mode")
                emitDemoSequence(generation)
            }
        }
    }

    /**
     * Local test simulation for jury presentation when hardware cane is not connected.
     * Emits realistic telemetry events locally without sending anything to hardware.
     */
    private fun runLocalDemoSequence() {
        disconnect()
        val generation = connectionGeneration.incrementAndGet()
        demoJob = coroutineScope.launch(ioDispatcher) {
            emitDemoSequence(generation)
        }
    }

    private suspend fun emitDemoSequence(generation: Long) {
        if (connectionGeneration.get() != generation) return
        _connectionStatus.value = BluetoothConnectionStatus.DEMO_MODE
        // Demo sequence: CENTER 60cm -> LEFT 28cm (critical) -> RIGHT 45cm
        emitDemoStep(generation, CaneTelemetry(distanceCm = 60, direction = CaneDirection.CENTER))
        kotlinx.coroutines.delay(1200)
        emitDemoStep(generation, CaneTelemetry(distanceCm = 28, direction = CaneDirection.LEFT))
        kotlinx.coroutines.delay(1500)
        emitDemoStep(generation, CaneTelemetry(distanceCm = 45, direction = CaneDirection.RIGHT))
    }

    private fun emitDemoStep(generation: Long, telemetry: CaneTelemetry) {
        if (connectionGeneration.get() == generation) {
            _telemetryFlow.value = telemetry
        }
    }
}
