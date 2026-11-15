package com.project.argus.data.bluetooth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothCaneClientTest {

    @Test
    fun `isCaneDeviceName accepts HC-05 HC-06 and Argus names case insensitively`() {
        assertTrue(BluetoothCaneClient.isCaneDeviceName("HC-05"))
        assertTrue(BluetoothCaneClient.isCaneDeviceName("hc-05-cane"))
        assertTrue(BluetoothCaneClient.isCaneDeviceName("HC-06"))
        assertTrue(BluetoothCaneClient.isCaneDeviceName("Argus Cane"))
        assertTrue(BluetoothCaneClient.isCaneDeviceName("ARGUS_BT"))
    }

    @Test
    fun `isCaneDeviceName rejects unrelated paired devices`() {
        assertFalse(BluetoothCaneClient.isCaneDeviceName(null))
        assertFalse(BluetoothCaneClient.isCaneDeviceName(""))
        assertFalse(BluetoothCaneClient.isCaneDeviceName("   "))
        assertFalse(BluetoothCaneClient.isCaneDeviceName("Sony WH-1000XM5"))
        assertFalse(BluetoothCaneClient.isCaneDeviceName("JBL Flip 6"))
        assertFalse(BluetoothCaneClient.isCaneDeviceName("Mi Band 7"))
    }
}
