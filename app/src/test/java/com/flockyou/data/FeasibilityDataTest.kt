package com.flockyou.data

import com.flockyou.data.model.Detection
import com.flockyou.data.model.DetectionMethod
import com.flockyou.data.model.DetectionProtocol
import com.flockyou.data.model.DetectionSource
import com.flockyou.data.model.DeviceType
import com.flockyou.data.model.SignalStrength
import com.flockyou.data.model.ThreatLevel
import com.flockyou.privilege.PrivilegeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeasibilityDataTest {

    // ============================================================================
    // 1. Protocol-Level Feasibility Per Privilege Mode
    // ============================================================================

    @Test
    fun `BLE protocol is DEGRADED on sideload`() {
        val result = FeasibilityData.getProtocolFeasibility(DetectionProtocol.BLUETOOTH_LE, PrivilegeMode.Sideload)
        assertEquals(FeasibilityLevel.DEGRADED, result.level)
        assertTrue(result.whatDoesnt.isNotEmpty())
        assertNotNull(result.upgradeNote)
    }

    @Test
    fun `BLE protocol is FULL on OEM`() {
        val result = FeasibilityData.getProtocolFeasibility(DetectionProtocol.BLUETOOTH_LE, PrivilegeMode.OEM())
        assertEquals(FeasibilityLevel.FULL, result.level)
        assertTrue(result.whatDoesnt.isEmpty())
        assertNull(result.upgradeNote)
    }

    @Test
    fun `WiFi protocol is DEGRADED on all modes`() {
        // WiFi is DEGRADED even on OEM due to no monitor mode
        for (mode in listOf(PrivilegeMode.Sideload, PrivilegeMode.System(), PrivilegeMode.OEM())) {
            val result = FeasibilityData.getProtocolFeasibility(DetectionProtocol.WIFI, mode)
            assertEquals("WiFi should be DEGRADED on $mode", FeasibilityLevel.DEGRADED, result.level)
        }
    }

    @Test
    fun `Cellular is HEURISTIC_ONLY on sideload`() {
        val result = FeasibilityData.getProtocolFeasibility(DetectionProtocol.CELLULAR, PrivilegeMode.Sideload)
        assertEquals(FeasibilityLevel.HEURISTIC_ONLY, result.level)
        assertTrue(result.summary.contains("cannot", ignoreCase = true) || result.summary.contains("heuristic", ignoreCase = true))
    }

    @Test
    fun `Cellular is HEURISTIC_ONLY on system`() {
        val result = FeasibilityData.getProtocolFeasibility(DetectionProtocol.CELLULAR, PrivilegeMode.System())
        assertEquals(FeasibilityLevel.HEURISTIC_ONLY, result.level)
    }

    @Test
    fun `Cellular is FULL on OEM`() {
        val result = FeasibilityData.getProtocolFeasibility(DetectionProtocol.CELLULAR, PrivilegeMode.OEM())
        assertEquals(FeasibilityLevel.FULL, result.level)
        assertTrue(result.summary.contains("Shannon SDM", ignoreCase = true))
    }

    @Test
    fun `RF is NOT_FEASIBLE on all modes`() {
        for (mode in listOf(PrivilegeMode.Sideload, PrivilegeMode.System(), PrivilegeMode.OEM())) {
            val result = FeasibilityData.getProtocolFeasibility(DetectionProtocol.RF, mode)
            assertEquals("RF should be NOT_FEASIBLE on $mode", FeasibilityLevel.NOT_FEASIBLE, result.level)
        }
    }

    @Test
    fun `GNSS is DEGRADED on all modes`() {
        for (mode in listOf(PrivilegeMode.Sideload, PrivilegeMode.System(), PrivilegeMode.OEM())) {
            val result = FeasibilityData.getProtocolFeasibility(DetectionProtocol.GNSS, mode)
            assertEquals("GNSS should be DEGRADED on $mode", FeasibilityLevel.DEGRADED, result.level)
        }
    }

    @Test
    fun `Satellite is HEURISTIC_ONLY on all modes`() {
        for (mode in listOf(PrivilegeMode.Sideload, PrivilegeMode.System(), PrivilegeMode.OEM())) {
            val result = FeasibilityData.getProtocolFeasibility(DetectionProtocol.SATELLITE, mode)
            assertEquals("Satellite should be HEURISTIC_ONLY on $mode", FeasibilityLevel.HEURISTIC_ONLY, result.level)
        }
    }

    @Test
    fun `Ultrasonic is DEGRADED on all modes`() {
        for (mode in listOf(PrivilegeMode.Sideload, PrivilegeMode.System(), PrivilegeMode.OEM())) {
            val result = FeasibilityData.getProtocolFeasibility(DetectionProtocol.AUDIO, mode)
            assertEquals("Ultrasonic should be DEGRADED on $mode", FeasibilityLevel.DEGRADED, result.level)
        }
    }

    // ============================================================================
    // 2. Shannon SDM Pattern Feasibility
    // ============================================================================

    @Test
    fun `Shannon SDM patterns are NOT_FEASIBLE on sideload`() {
        val sdmPatterns = listOf(
            CellularPattern.SDM_NULL_CIPHER,
            CellularPattern.SDM_IMSI_PAGING,
            CellularPattern.SDM_SILENT_SMS,
            CellularPattern.SDM_FORCED_2G,
            CellularPattern.SDM_AUTH_ANOMALY
        )
        for (pattern in sdmPatterns) {
            val result = FeasibilityData.getPatternFeasibility(pattern, PrivilegeMode.Sideload)
            assertNotNull("$pattern should have feasibility note on sideload", result)
            assertEquals("$pattern should be NOT_FEASIBLE on sideload", FeasibilityLevel.NOT_FEASIBLE, result!!.level)
            assertTrue(result.note.contains("OEM", ignoreCase = true))
        }
    }

    @Test
    fun `Shannon SDM patterns are NOT_FEASIBLE on system`() {
        val sdmPatterns = listOf(
            CellularPattern.SDM_NULL_CIPHER,
            CellularPattern.SDM_IMSI_PAGING,
            CellularPattern.SDM_SILENT_SMS,
            CellularPattern.SDM_FORCED_2G,
            CellularPattern.SDM_AUTH_ANOMALY
        )
        for (pattern in sdmPatterns) {
            val result = FeasibilityData.getPatternFeasibility(pattern, PrivilegeMode.System())
            assertNotNull("$pattern should have feasibility note on system", result)
            assertEquals("$pattern should be NOT_FEASIBLE on system", FeasibilityLevel.NOT_FEASIBLE, result!!.level)
        }
    }

    @Test
    fun `Shannon SDM patterns have no caveat on OEM`() {
        val sdmPatterns = listOf(
            CellularPattern.SDM_NULL_CIPHER,
            CellularPattern.SDM_IMSI_PAGING,
            CellularPattern.SDM_SILENT_SMS,
            CellularPattern.SDM_FORCED_2G,
            CellularPattern.SDM_AUTH_ANOMALY
        )
        for (pattern in sdmPatterns) {
            val result = FeasibilityData.getPatternFeasibility(pattern, PrivilegeMode.OEM())
            assertNull("$pattern should have no caveat on OEM", result)
        }
    }

    // ============================================================================
    // 3. Other Pattern Feasibility
    // ============================================================================

    @Test
    fun `ENCRYPTION_DOWNGRADE is HEURISTIC_ONLY on sideload`() {
        val result = FeasibilityData.getPatternFeasibility(CellularPattern.ENCRYPTION_DOWNGRADE, PrivilegeMode.Sideload)
        assertNotNull(result)
        assertEquals(FeasibilityLevel.HEURISTIC_ONLY, result!!.level)
    }

    @Test
    fun `ENCRYPTION_DOWNGRADE is FULL on OEM`() {
        val result = FeasibilityData.getPatternFeasibility(CellularPattern.ENCRYPTION_DOWNGRADE, PrivilegeMode.OEM())
        assertNotNull(result)
        assertEquals(FeasibilityLevel.FULL, result!!.level)
        assertTrue(result.note.contains("Shannon SDM"))
    }

    @Test
    fun `STINGRAY_WIFI is HEURISTIC_ONLY on all modes`() {
        for (mode in listOf(PrivilegeMode.Sideload, PrivilegeMode.System(), PrivilegeMode.OEM())) {
            val result = FeasibilityData.getPatternFeasibility(WifiPattern.STINGRAY_WIFI, mode)
            assertNotNull("STINGRAY_WIFI should have feasibility note on $mode", result)
            assertEquals(FeasibilityLevel.HEURISTIC_ONLY, result!!.level)
        }
    }

    @Test
    fun `GNSS SPOOFING pattern is DEGRADED`() {
        val result = FeasibilityData.getPatternFeasibility(GnssPattern.SPOOFING, PrivilegeMode.Sideload)
        assertNotNull(result)
        assertEquals(FeasibilityLevel.DEGRADED, result!!.level)
    }

    @Test
    fun `GNSS MULTIPATH pattern is DEGRADED`() {
        val result = FeasibilityData.getPatternFeasibility(GnssPattern.MULTIPATH, PrivilegeMode.Sideload)
        assertNotNull(result)
        assertEquals(FeasibilityLevel.DEGRADED, result!!.level)
    }

    @Test
    fun `Patterns without caveats return null`() {
        // BLE patterns have no per-pattern feasibility notes
        val result = FeasibilityData.getPatternFeasibility(BlePattern.AXON_DEVICES, PrivilegeMode.Sideload)
        assertNull(result)
    }

    // ============================================================================
    // 4. LLM Context Strings
    // ============================================================================

    @Test
    fun `Sideload LLM context contains limitation keywords`() {
        val context = FeasibilityData.getLlmPrivilegeModeContext(PrivilegeMode.Sideload)
        assertTrue(context.contains("CANNOT confirm IMSI"))
        assertTrue(context.contains("NO Shannon SDM") || context.contains("Shannon SDM diagnostic access NOT available"))
        assertTrue(context.contains("sideloaded", ignoreCase = true))
        assertTrue(context.contains("LOWER your confidence", ignoreCase = true))
    }

    @Test
    fun `OEM LLM context contains full capability keywords`() {
        val context = FeasibilityData.getLlmPrivilegeModeContext(PrivilegeMode.OEM())
        assertTrue(context.contains("Shannon SDM"))
        assertTrue(context.contains("DEFINITIVE"))
        assertTrue(context.contains("OEM", ignoreCase = true))
    }

    @Test
    fun `Compact sideload context contains limitation keywords`() {
        val context = FeasibilityData.getCompactLlmPrivilegeModeContext(PrivilegeMode.Sideload)
        assertTrue(context.contains("NO IMSI confirm") || context.contains("NO Shannon SDM"))
        assertTrue(context.contains("sideload", ignoreCase = true))
    }

    @Test
    fun `Compact OEM context contains SDM keyword`() {
        val context = FeasibilityData.getCompactLlmPrivilegeModeContext(PrivilegeMode.OEM())
        assertTrue(context.contains("Shannon SDM"))
    }

    // ============================================================================
    // 5. Detection Reliability Notes
    // ============================================================================

    @Test
    fun `Shannon SDM detection returns definitive reliability note`() {
        val detection = makeDetection(
            protocol = DetectionProtocol.CELLULAR,
            detectionSource = DetectionSource.SHANNON_SDM
        )
        val note = FeasibilityData.getDetectionReliabilityNote(detection, PrivilegeMode.OEM())
        assertNotNull(note)
        assertTrue(note!!.contains("definitive", ignoreCase = true))
        assertTrue(note.contains("Shannon", ignoreCase = true))
    }

    @Test
    fun `Cellular detection on sideload mentions heuristic limitation`() {
        val detection = makeDetection(protocol = DetectionProtocol.CELLULAR)
        val note = FeasibilityData.getDetectionReliabilityNote(detection, PrivilegeMode.Sideload)
        assertNotNull(note)
        assertTrue(note!!.contains("heuristic", ignoreCase = true) || note.contains("cannot be confirmed", ignoreCase = true))
    }

    @Test
    fun `RF detection always mentions Flipper Zero`() {
        val detection = makeDetection(protocol = DetectionProtocol.RF)
        val note = FeasibilityData.getDetectionReliabilityNote(detection, PrivilegeMode.Sideload)
        assertNotNull(note)
        assertTrue(note!!.contains("Flipper Zero"))
    }

    @Test
    fun `WiFi deauth detection mentions heuristic`() {
        val detection = makeDetection(
            protocol = DetectionProtocol.WIFI,
            detectionMethod = DetectionMethod.WIFI_DEAUTH_ATTACK
        )
        val note = FeasibilityData.getDetectionReliabilityNote(detection, PrivilegeMode.Sideload)
        assertNotNull(note)
        assertTrue(note!!.contains("heuristic", ignoreCase = true) || note.contains("disconnect frequency"))
    }

    @Test
    fun `BLE detection on sideload mentions duty cycling`() {
        val detection = makeDetection(protocol = DetectionProtocol.BLUETOOTH_LE)
        val note = FeasibilityData.getDetectionReliabilityNote(detection, PrivilegeMode.Sideload)
        assertNotNull(note)
        assertTrue(note!!.contains("duty-cycled") || note.contains("randomized"))
    }

    @Test
    fun `BLE detection on OEM returns null`() {
        val detection = makeDetection(protocol = DetectionProtocol.BLUETOOTH_LE)
        val note = FeasibilityData.getDetectionReliabilityNote(detection, PrivilegeMode.OEM())
        assertNull(note)
    }

    // ============================================================================
    // 6. NOT_FEASIBLE Only Where Expected
    // ============================================================================

    @Test
    fun `NOT_FEASIBLE protocol only returned for RF`() {
        val allProtocols = DetectionProtocol.values()
        val allModes = listOf(PrivilegeMode.Sideload, PrivilegeMode.System(), PrivilegeMode.OEM())

        for (protocol in allProtocols) {
            for (mode in allModes) {
                val result = FeasibilityData.getProtocolFeasibility(protocol, mode)
                if (result.level == FeasibilityLevel.NOT_FEASIBLE) {
                    assertEquals(
                        "Only RF protocol should be NOT_FEASIBLE, but $protocol on $mode was",
                        DetectionProtocol.RF, protocol
                    )
                }
            }
        }
    }

    @Test
    fun `All 7 protocols covered`() {
        val mode = PrivilegeMode.Sideload
        for (protocol in DetectionProtocol.values()) {
            val result = FeasibilityData.getProtocolFeasibility(protocol, mode)
            assertNotNull("Protocol $protocol should return feasibility data", result)
            assertTrue("Summary should not be empty for $protocol", result.summary.isNotEmpty())
        }
    }

    // ============================================================================
    // Helpers
    // ============================================================================

    private fun makeDetection(
        protocol: DetectionProtocol = DetectionProtocol.BLUETOOTH_LE,
        detectionMethod: DetectionMethod = DetectionMethod.BLE_DEVICE_NAME,
        detectionSource: DetectionSource = DetectionSource.NATIVE_BLE
    ): Detection {
        return Detection(
            protocol = protocol,
            detectionMethod = detectionMethod,
            deviceType = DeviceType.UNKNOWN_SURVEILLANCE,
            rssi = -70,
            signalStrength = SignalStrength.MEDIUM,
            threatLevel = ThreatLevel.LOW,
            detectionSource = detectionSource
        )
    }
}
