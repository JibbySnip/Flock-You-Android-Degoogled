package com.flockyou.ui.screens

import com.flockyou.data.model.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for MainViewModel filter logic.
 *
 * The filter functions in MainViewModelFilters.kt are extension functions on MainViewModel
 * that read/write _uiState (an internal MutableStateFlow). We use a relaxed MockK mock
 * of MainViewModel with a real MutableStateFlow backing _uiState, allowing us to test
 * the filter logic without needing the Android framework or Hilt DI.
 */
class MainViewModelFilterTest {

    private lateinit var viewModel: MainViewModel
    private lateinit var uiState: MutableStateFlow<MainUiState>

    // Reusable test detections
    private val now = 1_700_000_000_000L // Fixed timestamp for deterministic tests

    private val criticalBleDetection = Detection(
        id = "critical-ble-1",
        timestamp = now - 1_000_000, // ~17 minutes ago
        protocol = DetectionProtocol.BLUETOOTH_LE,
        detectionMethod = DetectionMethod.BLE_DEVICE_NAME,
        deviceType = DeviceType.STINGRAY_IMSI,
        rssi = -55,
        signalStrength = SignalStrength.GOOD,
        threatLevel = ThreatLevel.CRITICAL,
        threatScore = 95,
        isActive = true,
        fpScore = null
    )

    private val highWifiDetection = Detection(
        id = "high-wifi-1",
        timestamp = now - 3_600_000, // 1 hour ago
        protocol = DetectionProtocol.WIFI,
        detectionMethod = DetectionMethod.SSID_PATTERN,
        deviceType = DeviceType.FLOCK_SAFETY_CAMERA,
        rssi = -70,
        signalStrength = SignalStrength.MEDIUM,
        threatLevel = ThreatLevel.HIGH,
        threatScore = 75,
        isActive = true,
        fpScore = 0.1f
    )

    private val mediumCellularDetection = Detection(
        id = "medium-cellular-1",
        timestamp = now - 86_400_000, // 1 day ago
        protocol = DetectionProtocol.CELLULAR,
        detectionMethod = DetectionMethod.CELL_SIGNAL_ANOMALY,
        deviceType = DeviceType.BODY_CAMERA,
        rssi = -80,
        signalStrength = SignalStrength.WEAK,
        threatLevel = ThreatLevel.MEDIUM,
        threatScore = 55,
        isActive = false,
        fpScore = 0.3f
    )

    private val lowInfoDetection = Detection(
        id = "low-info-1",
        timestamp = now - 604_800_000, // 7 days ago
        protocol = DetectionProtocol.BLUETOOTH_LE,
        detectionMethod = DetectionMethod.BLE_SERVICE_UUID,
        deviceType = DeviceType.RING_DOORBELL,
        rssi = -90,
        signalStrength = SignalStrength.VERY_WEAK,
        threatLevel = ThreatLevel.INFO,
        threatScore = 10,
        isActive = false,
        fpScore = 0.8f // High FP score
    )

    private val falsePositiveDetection = Detection(
        id = "fp-detection-1",
        timestamp = now - 500_000,
        protocol = DetectionProtocol.WIFI,
        detectionMethod = DetectionMethod.SSID_PATTERN,
        deviceType = DeviceType.NEST_CAMERA,
        rssi = -65,
        signalStrength = SignalStrength.GOOD,
        threatLevel = ThreatLevel.LOW,
        threatScore = 25,
        isActive = true,
        fpScore = 0.9f // Very high FP score
    )

    private val rfDetection = Detection(
        id = "rf-1",
        timestamp = now - 2_000_000,
        protocol = DetectionProtocol.RF,
        detectionMethod = DetectionMethod.RF_DRONE,
        deviceType = DeviceType.DRONE,
        rssi = -60,
        signalStrength = SignalStrength.GOOD,
        threatLevel = ThreatLevel.HIGH,
        threatScore = 72,
        isActive = true,
        fpScore = null
    )

    private val audioDetection = Detection(
        id = "audio-1",
        timestamp = now - 100_000,
        protocol = DetectionProtocol.AUDIO,
        detectionMethod = DetectionMethod.ULTRASONIC_TRACKING_BEACON,
        deviceType = DeviceType.ULTRASONIC_BEACON,
        rssi = -45,
        signalStrength = SignalStrength.EXCELLENT,
        threatLevel = ThreatLevel.MEDIUM,
        threatScore = 60,
        isActive = true,
        fpScore = 0.2f
    )

    private val allDetections = listOf(
        criticalBleDetection,
        highWifiDetection,
        mediumCellularDetection,
        lowInfoDetection,
        falsePositiveDetection,
        rfDetection,
        audioDetection
    )

    @Before
    fun setup() {
        uiState = MutableStateFlow(MainUiState(detections = allDetections))
        viewModel = mockk<MainViewModel>(relaxed = true)
        every { viewModel._uiState } returns uiState
    }

    // ============================================================================
    // 1. No Filters - Default Behavior
    // ============================================================================

    @Test
    fun `getFilteredDetections returns all detections when no filters set and FP hiding disabled`() {
        uiState.value = uiState.value.copy(hideFalsePositives = false)
        val result = viewModel.getFilteredDetections()
        assertEquals(allDetections.size, result.size)
    }

    @Test
    fun `getFilteredDetections hides false positives by default`() {
        // Default state has hideFalsePositives = true, fpFilterThreshold = 0.6f
        val result = viewModel.getFilteredDetections()
        // lowInfoDetection (fpScore=0.8) and falsePositiveDetection (fpScore=0.9) should be hidden
        assertFalse("Should not contain detection with fpScore 0.8", result.any { it.id == "low-info-1" })
        assertFalse("Should not contain detection with fpScore 0.9", result.any { it.id == "fp-detection-1" })
        assertTrue("Should contain detection with fpScore 0.1", result.any { it.id == "high-wifi-1" })
        assertTrue("Should contain detection with null fpScore", result.any { it.id == "critical-ble-1" })
    }

    // ============================================================================
    // 2. Threat Level Filter
    // ============================================================================

    @Test
    fun `setThreatFilter filters by CRITICAL threat level`() {
        uiState.value = uiState.value.copy(hideFalsePositives = false)
        viewModel.setThreatFilter(ThreatLevel.CRITICAL)
        val result = viewModel.getFilteredDetections()
        assertTrue("All results should be CRITICAL", result.all { it.threatLevel == ThreatLevel.CRITICAL })
        assertEquals(1, result.size)
        assertEquals("critical-ble-1", result[0].id)
    }

    @Test
    fun `setThreatFilter filters by HIGH threat level`() {
        uiState.value = uiState.value.copy(hideFalsePositives = false)
        viewModel.setThreatFilter(ThreatLevel.HIGH)
        val result = viewModel.getFilteredDetections()
        assertTrue("All results should be HIGH", result.all { it.threatLevel == ThreatLevel.HIGH })
        assertEquals(2, result.size) // highWifiDetection and rfDetection
    }

    @Test
    fun `setThreatFilter with null clears the filter`() {
        uiState.value = uiState.value.copy(hideFalsePositives = false)
        viewModel.setThreatFilter(ThreatLevel.CRITICAL)
        viewModel.setThreatFilter(null)
        val result = viewModel.getFilteredDetections()
        assertEquals(allDetections.size, result.size)
    }

    // ============================================================================
    // 3. Device Type Filter
    // ============================================================================

    @Test
    fun `addDeviceTypeFilter filters to specific device type`() {
        uiState.value = uiState.value.copy(hideFalsePositives = false)
        viewModel.addDeviceTypeFilter(DeviceType.FLOCK_SAFETY_CAMERA)
        val result = viewModel.getFilteredDetections()
        assertEquals(1, result.size)
        assertEquals(DeviceType.FLOCK_SAFETY_CAMERA, result[0].deviceType)
    }

    @Test
    fun `addDeviceTypeFilter with multiple types shows all matching`() {
        uiState.value = uiState.value.copy(hideFalsePositives = false)
        viewModel.addDeviceTypeFilter(DeviceType.FLOCK_SAFETY_CAMERA)
        viewModel.addDeviceTypeFilter(DeviceType.DRONE)
        val result = viewModel.getFilteredDetections()
        assertEquals(2, result.size)
        assertTrue(result.any { it.deviceType == DeviceType.FLOCK_SAFETY_CAMERA })
        assertTrue(result.any { it.deviceType == DeviceType.DRONE })
    }

    @Test
    fun `removeDeviceTypeFilter removes a type from filter`() {
        uiState.value = uiState.value.copy(hideFalsePositives = false)
        viewModel.addDeviceTypeFilter(DeviceType.FLOCK_SAFETY_CAMERA)
        viewModel.addDeviceTypeFilter(DeviceType.DRONE)
        viewModel.removeDeviceTypeFilter(DeviceType.FLOCK_SAFETY_CAMERA)
        val result = viewModel.getFilteredDetections()
        assertEquals(1, result.size)
        assertEquals(DeviceType.DRONE, result[0].deviceType)
    }

    @Test
    fun `toggleDeviceTypeFilter adds when not present`() {
        uiState.value = uiState.value.copy(hideFalsePositives = false)
        viewModel.toggleDeviceTypeFilter(DeviceType.STINGRAY_IMSI)
        val state = uiState.value
        assertTrue(DeviceType.STINGRAY_IMSI in state.filterDeviceTypes)
    }

    @Test
    fun `toggleDeviceTypeFilter removes when present`() {
        uiState.value = uiState.value.copy(
            hideFalsePositives = false,
            filterDeviceTypes = setOf(DeviceType.STINGRAY_IMSI)
        )
        viewModel.toggleDeviceTypeFilter(DeviceType.STINGRAY_IMSI)
        val state = uiState.value
        assertFalse(DeviceType.STINGRAY_IMSI in state.filterDeviceTypes)
    }

    // ============================================================================
    // 4. Protocol Filter
    // ============================================================================

    @Test
    fun `toggleProtocolFilter adds protocol when not present`() {
        viewModel.toggleProtocolFilter(DetectionProtocol.WIFI)
        assertTrue(DetectionProtocol.WIFI in uiState.value.filterProtocols)
    }

    @Test
    fun `toggleProtocolFilter removes protocol when already present`() {
        uiState.value = uiState.value.copy(filterProtocols = setOf(DetectionProtocol.WIFI))
        viewModel.toggleProtocolFilter(DetectionProtocol.WIFI)
        assertFalse(DetectionProtocol.WIFI in uiState.value.filterProtocols)
    }

    @Test
    fun `protocol filter shows only matching protocol detections`() {
        uiState.value = uiState.value.copy(
            hideFalsePositives = false,
            filterProtocols = setOf(DetectionProtocol.CELLULAR)
        )
        val result = viewModel.getFilteredDetections()
        assertTrue("All results should be CELLULAR protocol", result.all { it.protocol == DetectionProtocol.CELLULAR })
        assertEquals(1, result.size)
    }

    @Test
    fun `empty protocol filter shows all protocols`() {
        uiState.value = uiState.value.copy(
            hideFalsePositives = false,
            filterProtocols = emptySet()
        )
        val result = viewModel.getFilteredDetections()
        assertEquals(allDetections.size, result.size)
    }

    @Test
    fun `multiple protocol filters show detections from any matching protocol`() {
        uiState.value = uiState.value.copy(
            hideFalsePositives = false,
            filterProtocols = setOf(DetectionProtocol.WIFI, DetectionProtocol.RF)
        )
        val result = viewModel.getFilteredDetections()
        assertTrue("All results should be WIFI or RF",
            result.all { it.protocol == DetectionProtocol.WIFI || it.protocol == DetectionProtocol.RF })
        assertEquals(3, result.size) // highWifiDetection, falsePositiveDetection, rfDetection
    }

    // ============================================================================
    // 5. Signal Strength Filter
    // ============================================================================

    @Test
    fun `signal strength filter shows only matching signal strengths`() {
        uiState.value = uiState.value.copy(
            hideFalsePositives = false,
            filterSignalStrength = setOf(SignalStrength.EXCELLENT)
        )
        val result = viewModel.getFilteredDetections()
        assertTrue("All results should have EXCELLENT signal",
            result.all { it.signalStrength == SignalStrength.EXCELLENT })
        assertEquals(1, result.size) // audioDetection
    }

    @Test
    fun `multiple signal strength filters show any matching`() {
        uiState.value = uiState.value.copy(
            hideFalsePositives = false,
            filterSignalStrength = setOf(SignalStrength.EXCELLENT, SignalStrength.GOOD)
        )
        val result = viewModel.getFilteredDetections()
        assertTrue("All results should have EXCELLENT or GOOD signal",
            result.all {
                it.signalStrength == SignalStrength.EXCELLENT || it.signalStrength == SignalStrength.GOOD
            })
        // audioDetection(EXCELLENT), criticalBleDetection(GOOD), falsePositiveDetection(GOOD), rfDetection(GOOD)
        assertEquals(4, result.size)
    }

    @Test
    fun `toggleSignalStrengthFilter adds when not present`() {
        viewModel.toggleSignalStrengthFilter(SignalStrength.WEAK)
        assertTrue(SignalStrength.WEAK in uiState.value.filterSignalStrength)
    }

    @Test
    fun `toggleSignalStrengthFilter removes when present`() {
        uiState.value = uiState.value.copy(filterSignalStrength = setOf(SignalStrength.WEAK))
        viewModel.toggleSignalStrengthFilter(SignalStrength.WEAK)
        assertFalse(SignalStrength.WEAK in uiState.value.filterSignalStrength)
    }

    // ============================================================================
    // 6. Active Only Filter
    // ============================================================================

    @Test
    fun `active only filter shows only active detections`() {
        uiState.value = uiState.value.copy(
            hideFalsePositives = false,
            filterActiveOnly = true
        )
        val result = viewModel.getFilteredDetections()
        assertTrue("All results should be active", result.all { it.isActive })
        // Active: criticalBle, highWifi, falsePositive, rf, audio = 5
        assertEquals(5, result.size)
    }

    @Test
    fun `active only false shows all detections including inactive`() {
        uiState.value = uiState.value.copy(
            hideFalsePositives = false,
            filterActiveOnly = false
        )
        val result = viewModel.getFilteredDetections()
        assertEquals(allDetections.size, result.size)
    }

    @Test
    fun `setActiveOnly updates state correctly`() {
        viewModel.setActiveOnly(true)
        assertTrue(uiState.value.filterActiveOnly)
        viewModel.setActiveOnly(false)
        assertFalse(uiState.value.filterActiveOnly)
    }

    // ============================================================================
    // 7. Time Range Filter
    // ============================================================================

    @Test
    fun `ALL_TIME time range shows all detections`() {
        uiState.value = uiState.value.copy(
            hideFalsePositives = false,
            filterTimeRange = TimeRange.ALL_TIME
        )
        val result = viewModel.getFilteredDetections()
        assertEquals(allDetections.size, result.size)
    }

    @Test
    fun `CUSTOM time range filters by start and end`() {
        val start = now - 2_000_000 // 2M ms ago
        val end = now - 500_000     // 500K ms ago
        uiState.value = uiState.value.copy(
            hideFalsePositives = false,
            filterTimeRange = TimeRange.CUSTOM,
            filterCustomStartTime = start,
            filterCustomEndTime = end
        )
        val result = viewModel.getFilteredDetections()
        assertTrue("All results should be within custom range",
            result.all { it.timestamp in start..end })
        // criticalBle(now-1M), falsePositive(now-500K), rf(now-2M) are in range
        assertEquals(3, result.size)
    }

    @Test
    fun `CUSTOM time range with null start uses 0`() {
        uiState.value = uiState.value.copy(
            hideFalsePositives = false,
            filterTimeRange = TimeRange.CUSTOM,
            filterCustomStartTime = null,
            filterCustomEndTime = now - 600_000_000 // very old boundary
        )
        val result = viewModel.getFilteredDetections()
        // Only lowInfoDetection at now - 604_800_000 is before the end time
        assertTrue("Results should be before end time",
            result.all { it.timestamp <= now - 600_000_000 })
    }

    @Test
    fun `CUSTOM time range with null end uses Long MAX_VALUE`() {
        uiState.value = uiState.value.copy(
            hideFalsePositives = false,
            filterTimeRange = TimeRange.CUSTOM,
            filterCustomStartTime = now - 500_001, // just before falsePositiveDetection
            filterCustomEndTime = null
        )
        val result = viewModel.getFilteredDetections()
        assertTrue("All results should be after start time",
            result.all { it.timestamp >= now - 500_001 })
    }

    @Test
    fun `setTimeRange updates state`() {
        viewModel.setTimeRange(TimeRange.LAST_HOUR)
        assertEquals(TimeRange.LAST_HOUR, uiState.value.filterTimeRange)
    }

    @Test
    fun `setTimeRange clears custom times for non-CUSTOM ranges`() {
        uiState.value = uiState.value.copy(
            filterCustomStartTime = 100L,
            filterCustomEndTime = 200L
        )
        viewModel.setTimeRange(TimeRange.LAST_24H)
        assertNull(uiState.value.filterCustomStartTime)
        assertNull(uiState.value.filterCustomEndTime)
    }

    @Test
    fun `setTimeRange preserves custom times for CUSTOM range`() {
        uiState.value = uiState.value.copy(
            filterCustomStartTime = 100L,
            filterCustomEndTime = 200L
        )
        viewModel.setTimeRange(TimeRange.CUSTOM)
        assertEquals(100L, uiState.value.filterCustomStartTime)
        assertEquals(200L, uiState.value.filterCustomEndTime)
    }

    @Test
    fun `setCustomTimeRange sets CUSTOM range and times`() {
        viewModel.setCustomTimeRange(1000L, 2000L)
        assertEquals(TimeRange.CUSTOM, uiState.value.filterTimeRange)
        assertEquals(1000L, uiState.value.filterCustomStartTime)
        assertEquals(2000L, uiState.value.filterCustomEndTime)
    }

    // ============================================================================
    // 8. False Positive Filter
    // ============================================================================

    @Test
    fun `hideFalsePositives true filters detections above threshold`() {
        uiState.value = uiState.value.copy(
            hideFalsePositives = true,
            fpFilterThreshold = 0.5f
        )
        val result = viewModel.getFilteredDetections()
        // Filtered out: lowInfoDetection(0.8), falsePositiveDetection(0.9)
        assertFalse(result.any { it.id == "low-info-1" })
        assertFalse(result.any { it.id == "fp-detection-1" })
        // Kept: criticalBle(null), highWifi(0.1), mediumCellular(0.3), rf(null), audio(0.2)
        assertEquals(5, result.size)
    }

    @Test
    fun `hideFalsePositives false shows all detections regardless of fpScore`() {
        uiState.value = uiState.value.copy(hideFalsePositives = false)
        val result = viewModel.getFilteredDetections()
        assertEquals(allDetections.size, result.size)
    }

    @Test
    fun `null fpScore is treated as 0 and passes FP filter`() {
        uiState.value = uiState.value.copy(
            hideFalsePositives = true,
            fpFilterThreshold = 0.01f // Very low threshold
        )
        val result = viewModel.getFilteredDetections()
        // Detections with null fpScore should pass (treated as 0.0)
        assertTrue("Null fpScore detection should pass", result.any { it.id == "critical-ble-1" })
        assertTrue("Null fpScore detection should pass", result.any { it.id == "rf-1" })
    }

    @Test
    fun `setFpFilterThreshold updates state`() {
        viewModel.setFpFilterThreshold(0.75f)
        assertEquals(0.75f, uiState.value.fpFilterThreshold)
    }

    @Test
    fun `setFpFilterThreshold clamps to 0-1 range`() {
        viewModel.setFpFilterThreshold(1.5f)
        assertEquals(1.0f, uiState.value.fpFilterThreshold)

        viewModel.setFpFilterThreshold(-0.5f)
        assertEquals(0.0f, uiState.value.fpFilterThreshold)
    }

    @Test
    fun `toggleHideFalsePositives toggles state`() {
        val initial = uiState.value.hideFalsePositives
        viewModel.toggleHideFalsePositives()
        assertEquals(!initial, uiState.value.hideFalsePositives)
        viewModel.toggleHideFalsePositives()
        assertEquals(initial, uiState.value.hideFalsePositives)
    }

    @Test
    fun `getFalsePositiveCount returns correct count based on threshold`() {
        uiState.value = uiState.value.copy(fpFilterThreshold = 0.6f)
        // lowInfoDetection(0.8) and falsePositiveDetection(0.9) are >= 0.6
        assertEquals(2, viewModel.getFalsePositiveCount())
    }

    @Test
    fun `getFalsePositiveCount treats null fpScore as 0`() {
        uiState.value = uiState.value.copy(fpFilterThreshold = 0.0f)
        // All detections have fpScore >= 0.0 (null treated as 0, 0 >= 0 is true)
        assertEquals(allDetections.size, viewModel.getFalsePositiveCount())
    }

    // ============================================================================
    // 9. Combined Filters (AND logic for threat+type with filterMatchAll)
    // ============================================================================

    @Test
    fun `filterMatchAll true uses AND logic for threat and device type`() {
        uiState.value = uiState.value.copy(
            hideFalsePositives = false,
            filterThreatLevel = ThreatLevel.HIGH,
            filterDeviceTypes = setOf(DeviceType.FLOCK_SAFETY_CAMERA),
            filterMatchAll = true
        )
        val result = viewModel.getFilteredDetections()
        // AND: must be HIGH AND FLOCK_SAFETY_CAMERA
        assertEquals(1, result.size)
        assertEquals("high-wifi-1", result[0].id)
    }

    @Test
    fun `filterMatchAll false uses OR logic for threat and device type`() {
        uiState.value = uiState.value.copy(
            hideFalsePositives = false,
            filterThreatLevel = ThreatLevel.CRITICAL,
            filterDeviceTypes = setOf(DeviceType.DRONE),
            filterMatchAll = false
        )
        val result = viewModel.getFilteredDetections()
        // OR: CRITICAL (criticalBle) OR DRONE (rfDetection)
        assertEquals(2, result.size)
        assertTrue(result.any { it.id == "critical-ble-1" })
        assertTrue(result.any { it.id == "rf-1" })
    }

    @Test
    fun `filterMatchAll false with only threat level set still works`() {
        uiState.value = uiState.value.copy(
            hideFalsePositives = false,
            filterThreatLevel = ThreatLevel.CRITICAL,
            filterDeviceTypes = emptySet(),
            filterMatchAll = false
        )
        val result = viewModel.getFilteredDetections()
        // Only threat level set, OR mode falls back to AND (per code logic)
        assertEquals(1, result.size)
        assertEquals("critical-ble-1", result[0].id)
    }

    @Test
    fun `filterMatchAll false with only device type set still works`() {
        uiState.value = uiState.value.copy(
            hideFalsePositives = false,
            filterThreatLevel = null,
            filterDeviceTypes = setOf(DeviceType.DRONE),
            filterMatchAll = false
        )
        val result = viewModel.getFilteredDetections()
        assertEquals(1, result.size)
        assertEquals("rf-1", result[0].id)
    }

    @Test
    fun `setFilterMatchAll updates state`() {
        viewModel.setFilterMatchAll(false)
        assertFalse(uiState.value.filterMatchAll)
        viewModel.setFilterMatchAll(true)
        assertTrue(uiState.value.filterMatchAll)
    }

    // ============================================================================
    // 10. Combined Multi-Dimensional Filters
    // ============================================================================

    @Test
    fun `protocol and threat level filters combine with AND`() {
        uiState.value = uiState.value.copy(
            hideFalsePositives = false,
            filterThreatLevel = ThreatLevel.HIGH,
            filterProtocols = setOf(DetectionProtocol.WIFI)
        )
        val result = viewModel.getFilteredDetections()
        // HIGH + WIFI = highWifiDetection only
        assertEquals(1, result.size)
        assertEquals("high-wifi-1", result[0].id)
    }

    @Test
    fun `signal strength and active only combine with AND`() {
        uiState.value = uiState.value.copy(
            hideFalsePositives = false,
            filterSignalStrength = setOf(SignalStrength.GOOD),
            filterActiveOnly = true
        )
        val result = viewModel.getFilteredDetections()
        // GOOD signal + active: criticalBle(GOOD,active), highWifi(MEDIUM,no), falsePositive(GOOD,active), rf(GOOD,active)
        // Wait - highWifi is MEDIUM signal, so excluded
        assertTrue("All results should be active with GOOD signal",
            result.all { it.signalStrength == SignalStrength.GOOD && it.isActive })
        assertEquals(3, result.size) // criticalBle, falsePositive, rf
    }

    @Test
    fun `all filters combined narrow results correctly`() {
        uiState.value = uiState.value.copy(
            hideFalsePositives = false,
            filterThreatLevel = ThreatLevel.HIGH,
            filterDeviceTypes = setOf(DeviceType.FLOCK_SAFETY_CAMERA, DeviceType.DRONE),
            filterMatchAll = true,
            filterProtocols = setOf(DetectionProtocol.WIFI, DetectionProtocol.RF),
            filterSignalStrength = setOf(SignalStrength.MEDIUM, SignalStrength.GOOD),
            filterActiveOnly = true
        )
        val result = viewModel.getFilteredDetections()
        // HIGH + (FLOCK_SAFETY_CAMERA or DRONE) AND + (WIFI or RF) + (MEDIUM or GOOD) + active
        // highWifiDetection: HIGH, FLOCK_SAFETY_CAMERA, WIFI, MEDIUM, active -> YES
        // rfDetection: HIGH, DRONE, RF, GOOD, active -> YES
        assertEquals(2, result.size)
    }

    @Test
    fun `FP filter combines with all other filters`() {
        uiState.value = uiState.value.copy(
            hideFalsePositives = true,
            fpFilterThreshold = 0.6f,
            filterProtocols = setOf(DetectionProtocol.WIFI)
        )
        val result = viewModel.getFilteredDetections()
        // WIFI detections: highWifi(0.1) and falsePositive(0.9)
        // FP filter removes falsePositive(0.9 >= 0.6)
        assertEquals(1, result.size)
        assertEquals("high-wifi-1", result[0].id)
    }

    // ============================================================================
    // 11. Clear Filters
    // ============================================================================

    @Test
    fun `clearFilters resets all filter state to defaults`() {
        uiState.value = uiState.value.copy(
            filterThreatLevel = ThreatLevel.CRITICAL,
            filterDeviceTypes = setOf(DeviceType.STINGRAY_IMSI),
            filterProtocols = setOf(DetectionProtocol.CELLULAR),
            filterTimeRange = TimeRange.LAST_HOUR,
            filterCustomStartTime = 100L,
            filterCustomEndTime = 200L,
            filterSignalStrength = setOf(SignalStrength.EXCELLENT),
            filterActiveOnly = true
        )
        viewModel.clearFilters()
        val state = uiState.value
        assertNull(state.filterThreatLevel)
        assertTrue(state.filterDeviceTypes.isEmpty())
        assertTrue(state.filterProtocols.isEmpty())
        assertEquals(TimeRange.ALL_TIME, state.filterTimeRange)
        assertNull(state.filterCustomStartTime)
        assertNull(state.filterCustomEndTime)
        assertTrue(state.filterSignalStrength.isEmpty())
        assertFalse(state.filterActiveOnly)
    }

    @Test
    fun `clearFilters does not affect hideFalsePositives`() {
        uiState.value = uiState.value.copy(hideFalsePositives = true)
        viewModel.clearFilters()
        // hideFalsePositives should NOT be reset by clearFilters
        assertTrue(uiState.value.hideFalsePositives)
    }

    @Test
    fun `clearFilters does not affect fpFilterThreshold`() {
        uiState.value = uiState.value.copy(fpFilterThreshold = 0.9f)
        viewModel.clearFilters()
        assertEquals(0.9f, uiState.value.fpFilterThreshold)
    }

    // ============================================================================
    // 12. Active Filter Count
    // ============================================================================

    @Test
    fun `getActiveFilterCount returns 0 when no filters active`() {
        uiState.value = uiState.value.copy(
            filterThreatLevel = null,
            filterDeviceTypes = emptySet(),
            filterProtocols = emptySet(),
            filterTimeRange = TimeRange.ALL_TIME,
            filterSignalStrength = emptySet(),
            filterActiveOnly = false
        )
        assertEquals(0, viewModel.getActiveFilterCount())
    }

    @Test
    fun `getActiveFilterCount counts threat level as 1`() {
        uiState.value = uiState.value.copy(filterThreatLevel = ThreatLevel.HIGH)
        val count = viewModel.getActiveFilterCount()
        assertTrue("Count should include threat level filter", count >= 1)
    }

    @Test
    fun `getActiveFilterCount counts each device type individually`() {
        uiState.value = uiState.value.copy(
            filterThreatLevel = null,
            filterDeviceTypes = setOf(DeviceType.STINGRAY_IMSI, DeviceType.DRONE, DeviceType.BODY_CAMERA),
            filterProtocols = emptySet(),
            filterTimeRange = TimeRange.ALL_TIME,
            filterSignalStrength = emptySet(),
            filterActiveOnly = false
        )
        assertEquals(3, viewModel.getActiveFilterCount())
    }

    @Test
    fun `getActiveFilterCount counts each protocol individually`() {
        uiState.value = uiState.value.copy(
            filterThreatLevel = null,
            filterDeviceTypes = emptySet(),
            filterProtocols = setOf(DetectionProtocol.WIFI, DetectionProtocol.BLUETOOTH_LE),
            filterTimeRange = TimeRange.ALL_TIME,
            filterSignalStrength = emptySet(),
            filterActiveOnly = false
        )
        assertEquals(2, viewModel.getActiveFilterCount())
    }

    @Test
    fun `getActiveFilterCount counts time range as 1 when not ALL_TIME`() {
        uiState.value = uiState.value.copy(
            filterThreatLevel = null,
            filterDeviceTypes = emptySet(),
            filterProtocols = emptySet(),
            filterTimeRange = TimeRange.LAST_HOUR,
            filterSignalStrength = emptySet(),
            filterActiveOnly = false
        )
        assertEquals(1, viewModel.getActiveFilterCount())
    }

    @Test
    fun `getActiveFilterCount counts signal strengths individually`() {
        uiState.value = uiState.value.copy(
            filterThreatLevel = null,
            filterDeviceTypes = emptySet(),
            filterProtocols = emptySet(),
            filterTimeRange = TimeRange.ALL_TIME,
            filterSignalStrength = setOf(SignalStrength.EXCELLENT, SignalStrength.GOOD),
            filterActiveOnly = false
        )
        assertEquals(2, viewModel.getActiveFilterCount())
    }

    @Test
    fun `getActiveFilterCount counts active only as 1`() {
        uiState.value = uiState.value.copy(
            filterThreatLevel = null,
            filterDeviceTypes = emptySet(),
            filterProtocols = emptySet(),
            filterTimeRange = TimeRange.ALL_TIME,
            filterSignalStrength = emptySet(),
            filterActiveOnly = true
        )
        assertEquals(1, viewModel.getActiveFilterCount())
    }

    @Test
    fun `getActiveFilterCount sums all active filters`() {
        uiState.value = uiState.value.copy(
            filterThreatLevel = ThreatLevel.HIGH,            // +1
            filterDeviceTypes = setOf(DeviceType.DRONE),     // +1
            filterProtocols = setOf(DetectionProtocol.RF),   // +1
            filterTimeRange = TimeRange.LAST_24H,            // +1
            filterSignalStrength = setOf(SignalStrength.GOOD, SignalStrength.EXCELLENT), // +2
            filterActiveOnly = true                          // +1
        )
        assertEquals(7, viewModel.getActiveFilterCount())
    }

    // ============================================================================
    // 13. Edge Cases
    // ============================================================================

    @Test
    fun `getFilteredDetections with empty detection list returns empty`() {
        uiState.value = uiState.value.copy(
            detections = emptyList(),
            hideFalsePositives = false
        )
        val result = viewModel.getFilteredDetections()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getFilteredDetections with filter that matches nothing returns empty`() {
        uiState.value = uiState.value.copy(
            hideFalsePositives = false,
            filterThreatLevel = ThreatLevel.LOW,
            filterDeviceTypes = setOf(DeviceType.STINGRAY_IMSI), // No LOW + STINGRAY
            filterMatchAll = true
        )
        val result = viewModel.getFilteredDetections()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `FP threshold at exactly detection fpScore filters it out`() {
        // fpScore of 0.8 with threshold 0.8 should be filtered (>= comparison)
        uiState.value = uiState.value.copy(
            hideFalsePositives = true,
            fpFilterThreshold = 0.8f
        )
        val result = viewModel.getFilteredDetections()
        assertFalse("Detection with fpScore exactly at threshold should be filtered",
            result.any { it.id == "low-info-1" }) // fpScore = 0.8
    }

    @Test
    fun `FP threshold just above detection fpScore keeps it`() {
        uiState.value = uiState.value.copy(
            hideFalsePositives = true,
            fpFilterThreshold = 0.81f
        )
        val result = viewModel.getFilteredDetections()
        assertTrue("Detection with fpScore below threshold should pass",
            result.any { it.id == "low-info-1" }) // fpScore = 0.8 < 0.81
    }

    // ============================================================================
    // 14. TimeRange Enum Properties
    // ============================================================================

    @Test
    fun `TimeRange ALL_TIME has null durationMs`() {
        assertNull(TimeRange.ALL_TIME.durationMs)
    }

    @Test
    fun `TimeRange CUSTOM has null durationMs`() {
        assertNull(TimeRange.CUSTOM.durationMs)
    }

    @Test
    fun `TimeRange LAST_HOUR has correct durationMs`() {
        assertEquals(3_600_000L, TimeRange.LAST_HOUR.durationMs)
    }

    @Test
    fun `TimeRange LAST_24H has correct durationMs`() {
        assertEquals(86_400_000L, TimeRange.LAST_24H.durationMs)
    }

    @Test
    fun `TimeRange LAST_7D has correct durationMs`() {
        assertEquals(604_800_000L, TimeRange.LAST_7D.durationMs)
    }

    @Test
    fun `TimeRange LAST_30D has correct durationMs`() {
        assertEquals(2_592_000_000L, TimeRange.LAST_30D.durationMs)
    }

    @Test
    fun `all TimeRange values have non-empty labels`() {
        for (range in TimeRange.entries) {
            assertTrue("${range.name} should have a non-empty label", range.label.isNotEmpty())
        }
    }
}
