package com.project.argus.ui.camera

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class CaneDistanceDisplayTest {

    @Test
    fun resolveCaneDistanceDisplay_nullDistance_returnsUnknown() {
        val display = resolveCaneDistanceDisplay(null)

        assertEquals(CaneDistanceStatus.UNKNOWN, display.status)
        assertEquals("--", display.label)
        assertEquals(Color(0xFF263238).copy(alpha = 0.9f), display.containerColor)
    }

    @Test
    fun resolveCaneDistanceDisplay_nonPositiveDistances_returnClear() {
        val negativeDisplay = resolveCaneDistanceDisplay(-1)
        assertEquals(CaneDistanceStatus.CLEAR, negativeDisplay.status)
        assertEquals("> 200 СМ", negativeDisplay.label)
        assertEquals(Color(0xFF2E7D32), negativeDisplay.containerColor)

        val zeroDisplay = resolveCaneDistanceDisplay(0)
        assertEquals(CaneDistanceStatus.CLEAR, zeroDisplay.status)
        assertEquals("> 200 СМ", zeroDisplay.label)
        assertEquals(Color(0xFF2E7D32), zeroDisplay.containerColor)
    }

    @Test
    fun resolveCaneDistanceDisplay_criticalRange1Until35_returnsCritical() {
        val lowerEdge = resolveCaneDistanceDisplay(1)
        assertEquals(CaneDistanceStatus.CRITICAL, lowerEdge.status)
        assertEquals("1 СМ", lowerEdge.label)
        assertEquals(Color(0xFFD32F2F), lowerEdge.containerColor)

        val upperEdge = resolveCaneDistanceDisplay(34)
        assertEquals(CaneDistanceStatus.CRITICAL, upperEdge.status)
        assertEquals("34 СМ", upperEdge.label)
        assertEquals(Color(0xFFD32F2F), upperEdge.containerColor)
    }

    @Test
    fun resolveCaneDistanceDisplay_warningRange35To50_returnsWarning() {
        val lowerEdge = resolveCaneDistanceDisplay(35)
        assertEquals(CaneDistanceStatus.WARNING, lowerEdge.status)
        assertEquals("35 СМ", lowerEdge.label)
        assertEquals(Color(0xFFE65100), lowerEdge.containerColor)

        val upperEdge = resolveCaneDistanceDisplay(50)
        assertEquals(CaneDistanceStatus.WARNING, upperEdge.status)
        assertEquals("50 СМ", upperEdge.label)
        assertEquals(Color(0xFFE65100), upperEdge.containerColor)
    }

    @Test
    fun resolveCaneDistanceDisplay_above50_returnsSafe() {
        val lowerEdge = resolveCaneDistanceDisplay(51)
        assertEquals(CaneDistanceStatus.SAFE, lowerEdge.status)
        assertEquals("51 СМ", lowerEdge.label)
        assertEquals(Color(0xFF2E7D32), lowerEdge.containerColor)

        val farDistance = resolveCaneDistanceDisplay(120)
        assertEquals(CaneDistanceStatus.SAFE, farDistance.status)
        assertEquals("120 СМ", farDistance.label)
        assertEquals(Color(0xFF2E7D32), farDistance.containerColor)
    }
}
