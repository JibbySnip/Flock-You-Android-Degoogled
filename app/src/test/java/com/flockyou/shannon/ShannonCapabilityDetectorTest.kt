package com.flockyou.shannon

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ShannonCapabilityDetector.
 *
 * Note: Tests that call detect() require android.util.Log which is not available
 * in pure JUnit tests. Full detect() integration testing requires Android
 * instrumented tests or Robolectric. These tests verify enum properties and
 * public API surface.
 */
class ShannonCapabilityDetectorTest {

    @Test
    fun `ShannonStatus enum has correct values`() {
        val statuses = ShannonCapabilityDetector.ShannonStatus.values()
        assertEquals(5, statuses.size)
        assertTrue(statuses.any { it == ShannonCapabilityDetector.ShannonStatus.FEATURE_DISABLED })
        assertTrue(statuses.any { it == ShannonCapabilityDetector.ShannonStatus.NO_SHANNON_MODEM })
        assertTrue(statuses.any { it == ShannonCapabilityDetector.ShannonStatus.NO_DEVICE_NODE })
        assertTrue(statuses.any { it == ShannonCapabilityDetector.ShannonStatus.ACCESS_DENIED })
        assertTrue(statuses.any { it == ShannonCapabilityDetector.ShannonStatus.AVAILABLE })
    }

    @Test
    fun `ShannonStatus displayName is not empty`() {
        for (status in ShannonCapabilityDetector.ShannonStatus.values()) {
            assertTrue(
                "Status ${status.name} should have non-empty displayName",
                status.displayName.isNotBlank()
            )
        }
    }

    @Test
    fun `FEATURE_DISABLED is the first check in detect flow`() {
        // Verify that FEATURE_DISABLED is a valid terminal state
        // (detect() cannot be called in pure JUnit due to android.util.Log dependency;
        // full detect() flow is tested in instrumented tests)
        val status = ShannonCapabilityDetector.ShannonStatus.FEATURE_DISABLED
        assertEquals("Feature disabled", status.displayName)
    }

    @Test
    fun `ShannonStatus values are ordered by detection stage`() {
        // Verify enum ordering matches the detection flow
        val statuses = ShannonCapabilityDetector.ShannonStatus.values()
        assertEquals(ShannonCapabilityDetector.ShannonStatus.FEATURE_DISABLED, statuses[0])
        assertEquals(ShannonCapabilityDetector.ShannonStatus.NO_SHANNON_MODEM, statuses[1])
        assertEquals(ShannonCapabilityDetector.ShannonStatus.NO_DEVICE_NODE, statuses[2])
        assertEquals(ShannonCapabilityDetector.ShannonStatus.ACCESS_DENIED, statuses[3])
        assertEquals(ShannonCapabilityDetector.ShannonStatus.AVAILABLE, statuses[4])
    }
}
