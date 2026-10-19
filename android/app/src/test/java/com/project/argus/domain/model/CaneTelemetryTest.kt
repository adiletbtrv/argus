package com.project.argus.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaneTelemetryTest {

    @Test
    fun parse_validCenterTelemetry_returnsObject() {
        val line = "DIST:42,DIR:CENTER"
        val result = CaneTelemetry.parse(line)

        assertNotNull(result)
        assertEquals(42, result!!.distanceCm)
        assertEquals(CaneDirection.CENTER, result.direction)
        assertFalse(result.isCriticalDistance)
    }

    @Test
    fun parse_validLeftTelemetry_withCriticalDistance() {
        val line = "DIST:25,DIR:LEFT"
        val result = CaneTelemetry.parse(line)

        assertNotNull(result)
        assertEquals(25, result!!.distanceCm)
        assertEquals(CaneDirection.LEFT, result.direction)
        assertTrue(result.isCriticalDistance)
    }

    @Test
    fun parse_validRightTelemetry_edgeThreshold35() {
        val line = "DIST:35,DIR:RIGHT"
        val result = CaneTelemetry.parse(line)

        assertNotNull(result)
        assertEquals(35, result!!.distanceCm)
        assertEquals(CaneDirection.RIGHT, result.direction)
        assertFalse(result.isCriticalDistance)
    }

    @Test
    fun parse_negativeDistance_notCritical() {
        val line = "DIST:-1,DIR:CENTER"
        val result = CaneTelemetry.parse(line)

        assertNotNull(result)
        assertEquals(-1, result!!.distanceCm)
        assertFalse(result.isCriticalDistance)
    }

    @Test
    fun parse_invalidFormat_returnsNull() {
        assertNull(CaneTelemetry.parse("INVALID_PAYLOAD"))
        assertNull(CaneTelemetry.parse("DIST:abc,DIR:CENTER"))
        assertNull(CaneTelemetry.parse(""))
    }
}
