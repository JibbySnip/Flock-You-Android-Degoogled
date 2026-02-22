package com.flockyou.ui.screens

import com.flockyou.data.model.*
import com.flockyou.service.CellularMonitor
import com.flockyou.service.RfSignalAnalyzer
import com.flockyou.service.RogueWifiMonitor
import com.flockyou.service.UltrasonicDetector
import kotlinx.coroutines.flow.update

// ========== Filter / Sort Logic (extension functions on MainViewModel) ==========

fun MainViewModel.setSortOrder(order: SortOrder) {
    _uiState.update { it.copy(sortOrder = order) }
}

fun MainViewModel.setSearchQuery(query: String) {
    _uiState.update { it.copy(searchQuery = query) }
}

fun MainViewModel.setThreatFilter(threatLevel: ThreatLevel?) {
    _uiState.update { it.copy(filterThreatLevel = threatLevel) }
}

fun MainViewModel.addDeviceTypeFilter(deviceType: DeviceType) {
    _uiState.update { it.copy(filterDeviceTypes = it.filterDeviceTypes + deviceType) }
}

fun MainViewModel.removeDeviceTypeFilter(deviceType: DeviceType) {
    _uiState.update { it.copy(filterDeviceTypes = it.filterDeviceTypes - deviceType) }
}

fun MainViewModel.toggleDeviceTypeFilter(deviceType: DeviceType) {
    _uiState.update { state ->
        if (deviceType in state.filterDeviceTypes) {
            state.copy(filterDeviceTypes = state.filterDeviceTypes - deviceType)
        } else {
            state.copy(filterDeviceTypes = state.filterDeviceTypes + deviceType)
        }
    }
}

fun MainViewModel.setFilterMatchAll(matchAll: Boolean) {
    _uiState.update { it.copy(filterMatchAll = matchAll) }
}

fun MainViewModel.clearFilters() {
    _uiState.update {
        it.copy(
            filterThreatLevel = null,
            filterDeviceTypes = emptySet(),
            filterProtocols = emptySet(),
            filterTimeRange = TimeRange.ALL_TIME,
            filterCustomStartTime = null,
            filterCustomEndTime = null,
            filterSignalStrength = emptySet(),
            filterActiveOnly = false,
            searchQuery = ""
        )
    }
}

/**
 * Returns true if any filters (excluding sort order) are active.
 */
fun MainViewModel.hasActiveFilters(): Boolean {
    val state = _uiState.value
    return state.filterThreatLevel != null ||
        state.filterDeviceTypes.isNotEmpty() ||
        state.filterProtocols.isNotEmpty() ||
        state.filterTimeRange != TimeRange.ALL_TIME ||
        state.filterSignalStrength.isNotEmpty() ||
        state.filterActiveOnly ||
        state.searchQuery.isNotBlank()
}

// Protocol filter methods
fun MainViewModel.toggleProtocolFilter(protocol: DetectionProtocol) {
    _uiState.update { state ->
        if (protocol in state.filterProtocols) {
            state.copy(filterProtocols = state.filterProtocols - protocol)
        } else {
            state.copy(filterProtocols = state.filterProtocols + protocol)
        }
    }
}

// Time range filter methods
fun MainViewModel.setTimeRange(range: TimeRange) {
    _uiState.update {
        if (range != TimeRange.CUSTOM) {
            it.copy(
                filterTimeRange = range,
                filterCustomStartTime = null,
                filterCustomEndTime = null
            )
        } else {
            it.copy(filterTimeRange = range)
        }
    }
}

fun MainViewModel.setCustomTimeRange(start: Long, end: Long) {
    _uiState.update {
        it.copy(
            filterTimeRange = TimeRange.CUSTOM,
            filterCustomStartTime = start,
            filterCustomEndTime = end
        )
    }
}

// Signal strength filter methods
fun MainViewModel.toggleSignalStrengthFilter(strength: SignalStrength) {
    _uiState.update { state ->
        if (strength in state.filterSignalStrength) {
            state.copy(filterSignalStrength = state.filterSignalStrength - strength)
        } else {
            state.copy(filterSignalStrength = state.filterSignalStrength + strength)
        }
    }
}

// Active only filter method
fun MainViewModel.setActiveOnly(activeOnly: Boolean) {
    _uiState.update { it.copy(filterActiveOnly = activeOnly) }
}

/**
 * Returns the count of active filters for displaying on filter badges.
 */
fun MainViewModel.getActiveFilterCount(): Int {
    val state = _uiState.value
    var count = 0
    if (state.filterThreatLevel != null) count++
    if (state.filterDeviceTypes.isNotEmpty()) count += state.filterDeviceTypes.size
    if (state.filterProtocols.isNotEmpty()) count += state.filterProtocols.size
    if (state.filterTimeRange != TimeRange.ALL_TIME) count++
    if (state.filterSignalStrength.isNotEmpty()) count += state.filterSignalStrength.size
    if (state.filterActiveOnly) count++
    return count
}

/**
 * Toggle whether to hide false positive detections.
 */
fun MainViewModel.toggleHideFalsePositives() {
    _uiState.update { it.copy(hideFalsePositives = !it.hideFalsePositives) }
}

/**
 * Set false positive filter threshold.
 * Detections with fpScore >= threshold will be hidden when hideFalsePositives is true.
 */
fun MainViewModel.setFpFilterThreshold(threshold: Float) {
    _uiState.update { it.copy(fpFilterThreshold = threshold.coerceIn(0f, 1f)) }
}

/**
 * Get count of detections that are filtered as false positives.
 */
fun MainViewModel.getFalsePositiveCount(): Int {
    val state = _uiState.value
    return state.detections.count { detection ->
        val fpScore = detection.fpScore ?: 0f
        fpScore >= state.fpFilterThreshold
    }
}

fun MainViewModel.getFilteredDetections(): List<Detection> {
    val state = _uiState.value
    val filtered = state.detections.filter { detection ->
        // 1. FP filter - hide detections flagged as false positives
        val fpPass = if (state.hideFalsePositives) {
            val fpScore = detection.fpScore ?: 0f
            fpScore < state.fpFilterThreshold
        } else {
            true
        }

        // 2. Threat Level filter
        val threatPass = state.filterThreatLevel?.let { detection.threatLevel == it } ?: true

        // 3. Device Type filter
        val typePass = if (state.filterDeviceTypes.isEmpty()) {
            true
        } else {
            detection.deviceType in state.filterDeviceTypes
        }

        // 4. Protocol filter
        val protocolPass = if (state.filterProtocols.isEmpty()) {
            true
        } else {
            detection.protocol in state.filterProtocols
        }

        // 5. Time Range filter
        val timePass = when (state.filterTimeRange) {
            TimeRange.ALL_TIME -> true
            TimeRange.CUSTOM -> {
                val start = state.filterCustomStartTime ?: 0L
                val end = state.filterCustomEndTime ?: Long.MAX_VALUE
                detection.timestamp in start..end
            }
            else -> {
                val cutoff = System.currentTimeMillis() - (state.filterTimeRange.durationMs ?: 0L)
                detection.timestamp >= cutoff
            }
        }

        // 6. Signal Strength filter
        val signalPass = if (state.filterSignalStrength.isEmpty()) {
            true
        } else {
            detection.signalStrength in state.filterSignalStrength
        }

        // 7. Active Only filter
        val activePass = !state.filterActiveOnly || detection.isActive

        // 8. Search query filter
        val searchPass = if (state.searchQuery.isBlank()) {
            true
        } else {
            val query = state.searchQuery.lowercase()
            detection.deviceType.displayName.lowercase().contains(query) ||
                (detection.macAddress?.lowercase()?.contains(query) == true) ||
                (detection.ssid?.lowercase()?.contains(query) == true) ||
                (detection.deviceName?.lowercase()?.contains(query) == true) ||
                (detection.manufacturer?.lowercase()?.contains(query) == true) ||
                (detection.userNote?.lowercase()?.contains(query) == true)
        }

        // Combine threat+type with AND/OR logic (existing behavior)
        val threatTypePass = if (state.filterMatchAll) {
            // AND: both conditions must match
            threatPass && typePass
        } else {
            // OR: either condition can match (if both are set)
            if (state.filterThreatLevel != null && state.filterDeviceTypes.isNotEmpty()) {
                threatPass || typePass
            } else {
                // If only one filter type is set, just use that
                threatPass && typePass
            }
        }

        // All other filters are always AND-ed together
        fpPass && threatTypePass && protocolPass && timePass && signalPass && activePass && searchPass
    }

    // Apply sorting
    return when (state.sortOrder) {
        SortOrder.NEWEST_FIRST -> filtered.sortedByDescending { it.timestamp }
        SortOrder.OLDEST_FIRST -> filtered.sortedBy { it.timestamp }
        SortOrder.THREAT_SCORE_DESC -> filtered.sortedByDescending { it.threatScore }
        SortOrder.SIGNAL_STRENGTH_DESC -> filtered.sortedByDescending { it.rssi }
        SortOrder.SEEN_COUNT_DESC -> filtered.sortedByDescending { it.seenCount }
    }
}

/**
 * Returns RF anomalies filtered based on advanced mode.
 * Low-confidence anomalies (interference, spectrum anomalies, unusual activity)
 * are hidden from non-advanced users to reduce noise.
 */
fun MainViewModel.getFilteredRfAnomalies(): List<RfSignalAnalyzer.RfAnomaly> {
    val state = _uiState.value
    return if (state.advancedMode) {
        state.rfAnomalies
    } else {
        state.rfAnomalies.filter { !it.isAdvancedOnly }
    }
}

/**
 * Returns cellular anomalies filtered to exclude those marked as false positives.
 * Matches anomalies to detections by timestamp proximity and type.
 */
fun MainViewModel.getFilteredCellularAnomalies(): List<CellularMonitor.CellularAnomaly> {
    val state = _uiState.value
    if (!state.hideFalsePositives) {
        return state.cellularAnomalies
    }

    // Get cellular detections that are marked as FP
    val fpCellularDetections = state.detections.filter { detection ->
        detection.protocol == com.flockyou.data.model.DetectionProtocol.CELLULAR &&
        (detection.fpScore ?: 0f) >= state.fpFilterThreshold
    }

    if (fpCellularDetections.isEmpty()) {
        return state.cellularAnomalies
    }

    // Filter out anomalies that match FP detections (by timestamp within 5 seconds and similar characteristics)
    return state.cellularAnomalies.filter { anomaly ->
        val matchesFpDetection = fpCellularDetections.any { detection ->
            val timeDiff = kotlin.math.abs(anomaly.timestamp - detection.timestamp)
            val cellIdMatch = detection.manufacturer?.contains(anomaly.cellId?.toString() ?: "") == true
            timeDiff < 5000 && cellIdMatch
        }
        !matchesFpDetection
    }
}

/**
 * Returns GNSS anomalies filtered to exclude those marked as false positives.
 */
fun MainViewModel.getFilteredGnssAnomalies(): List<com.flockyou.monitoring.GnssSatelliteMonitor.GnssAnomaly> {
    val state = _uiState.value
    if (!state.hideFalsePositives) {
        return state.gnssAnomalies
    }

    val fpGnssDetections = state.detections.filter { detection ->
        detection.protocol == com.flockyou.data.model.DetectionProtocol.GNSS &&
        (detection.fpScore ?: 0f) >= state.fpFilterThreshold
    }

    if (fpGnssDetections.isEmpty()) {
        return state.gnssAnomalies
    }

    return state.gnssAnomalies.filter { anomaly ->
        val matchesFpDetection = fpGnssDetections.any { detection ->
            val timeDiff = kotlin.math.abs(anomaly.timestamp - detection.timestamp)
            timeDiff < 5000
        }
        !matchesFpDetection
    }
}

/**
 * Returns satellite anomalies filtered to exclude those marked as false positives.
 */
fun MainViewModel.getFilteredSatelliteAnomalies(): List<com.flockyou.monitoring.SatelliteMonitor.SatelliteAnomaly> {
    val state = _uiState.value
    if (!state.hideFalsePositives) {
        return state.satelliteAnomalies
    }

    val fpSatelliteDetections = state.detections.filter { detection ->
        detection.protocol == com.flockyou.data.model.DetectionProtocol.SATELLITE &&
        (detection.fpScore ?: 0f) >= state.fpFilterThreshold
    }

    if (fpSatelliteDetections.isEmpty()) {
        return state.satelliteAnomalies
    }

    return state.satelliteAnomalies.filter { anomaly ->
        val matchesFpDetection = fpSatelliteDetections.any { detection ->
            val timeDiff = kotlin.math.abs(anomaly.timestamp - detection.timestamp)
            timeDiff < 5000
        }
        !matchesFpDetection
    }
}

/**
 * Returns ultrasonic anomalies filtered to exclude those marked as false positives.
 */
fun MainViewModel.getFilteredUltrasonicAnomalies(): List<UltrasonicDetector.UltrasonicAnomaly> {
    val state = _uiState.value
    if (!state.hideFalsePositives) {
        return state.ultrasonicAnomalies
    }

    val fpUltrasonicDetections = state.detections.filter { detection ->
        detection.protocol == com.flockyou.data.model.DetectionProtocol.AUDIO &&
        (detection.fpScore ?: 0f) >= state.fpFilterThreshold
    }

    if (fpUltrasonicDetections.isEmpty()) {
        return state.ultrasonicAnomalies
    }

    return state.ultrasonicAnomalies.filter { anomaly ->
        val matchesFpDetection = fpUltrasonicDetections.any { detection ->
            val timeDiff = kotlin.math.abs(anomaly.timestamp - detection.timestamp)
            // Match by frequency stored in ssid field
            val freqMatch = detection.ssid?.contains(anomaly.frequency.toString()) == true
            timeDiff < 5000 && freqMatch
        }
        !matchesFpDetection
    }
}

/**
 * Returns rogue WiFi anomalies filtered to exclude those marked as false positives.
 */
fun MainViewModel.getFilteredRogueWifiAnomalies(): List<RogueWifiMonitor.WifiAnomaly> {
    val state = _uiState.value
    if (!state.hideFalsePositives) {
        return state.rogueWifiAnomalies
    }

    val fpWifiDetections = state.detections.filter { detection ->
        detection.protocol == com.flockyou.data.model.DetectionProtocol.WIFI &&
        (detection.fpScore ?: 0f) >= state.fpFilterThreshold
    }

    if (fpWifiDetections.isEmpty()) {
        return state.rogueWifiAnomalies
    }

    return state.rogueWifiAnomalies.filter { anomaly ->
        val matchesFpDetection = fpWifiDetections.any { detection ->
            val timeDiff = kotlin.math.abs(anomaly.timestamp - detection.timestamp)
            // Match by BSSID/MAC or SSID
            val bssidMatch = detection.macAddress?.equals(anomaly.bssid, ignoreCase = true) == true
            val ssidMatch = detection.ssid?.equals(anomaly.ssid, ignoreCase = true) == true
            timeDiff < 10000 && (bssidMatch || ssidMatch)
        }
        !matchesFpDetection
    }
}

/**
 * Returns RF anomalies filtered based on advanced mode AND false positive status.
 */
fun MainViewModel.getFilteredRfAnomaliesWithFp(): List<RfSignalAnalyzer.RfAnomaly> {
    val state = _uiState.value

    // First apply advanced mode filter
    val advancedFiltered = if (state.advancedMode) {
        state.rfAnomalies
    } else {
        state.rfAnomalies.filter { !it.isAdvancedOnly }
    }

    if (!state.hideFalsePositives) {
        return advancedFiltered
    }

    val fpRfDetections = state.detections.filter { detection ->
        detection.protocol == com.flockyou.data.model.DetectionProtocol.RF &&
        (detection.fpScore ?: 0f) >= state.fpFilterThreshold
    }

    if (fpRfDetections.isEmpty()) {
        return advancedFiltered
    }

    return advancedFiltered.filter { anomaly ->
        val matchesFpDetection = fpRfDetections.any { detection ->
            val timeDiff = kotlin.math.abs(anomaly.timestamp - detection.timestamp)
            timeDiff < 5000
        }
        !matchesFpDetection
    }
}
