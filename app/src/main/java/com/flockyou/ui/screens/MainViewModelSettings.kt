package com.flockyou.ui.screens

import androidx.lifecycle.viewModelScope
import com.flockyou.data.GnssPattern
import com.flockyou.data.RetentionPeriod
import com.flockyou.data.RfPattern
import com.flockyou.data.UltrasonicPattern
import com.flockyou.worker.DataRetentionWorker
import com.flockyou.worker.OuiUpdateWorker
import kotlinx.coroutines.launch

// ========== Settings Mutations (extension functions on MainViewModel) ==========

fun MainViewModel.setAdvancedMode(enabled: Boolean) {
    viewModelScope.launch {
        settingsRepository.setAdvancedMode(enabled)
    }
}

fun MainViewModel.setShowAdvancedSettings(enabled: Boolean) {
    viewModelScope.launch {
        settingsRepository.setShowAdvancedSettings(enabled)
    }
}

/**
 * Apply a protection preset, persisting all patterns and thresholds.
 */
fun MainViewModel.applyProtectionPreset(preset: com.flockyou.data.ProtectionPreset) {
    viewModelScope.launch {
        settingsRepository.applyPreset(preset)
    }
}

/**
 * Toggle global detection types (cellular, satellite, BLE, WiFi).
 */
fun MainViewModel.setGlobalDetectionEnabled(
    cellular: Boolean? = null,
    satellite: Boolean? = null,
    ble: Boolean? = null,
    wifi: Boolean? = null,
    gnss: Boolean? = null,
    rf: Boolean? = null,
    ultrasonic: Boolean? = null
) {
    viewModelScope.launch {
        settingsRepository.setGlobalDetectionEnabled(cellular, satellite, ble, wifi, gnss, rf, ultrasonic)
    }
}

fun MainViewModel.toggleCellularPattern(pattern: com.flockyou.data.CellularPattern, enabled: Boolean) {
    viewModelScope.launch { settingsRepository.toggleCellularPattern(pattern, enabled) }
}

fun MainViewModel.toggleSatellitePattern(pattern: com.flockyou.data.SatellitePattern, enabled: Boolean) {
    viewModelScope.launch { settingsRepository.toggleSatellitePattern(pattern, enabled) }
}

fun MainViewModel.toggleBlePattern(pattern: com.flockyou.data.BlePattern, enabled: Boolean) {
    viewModelScope.launch { settingsRepository.toggleBlePattern(pattern, enabled) }
}

fun MainViewModel.toggleWifiPattern(pattern: com.flockyou.data.WifiPattern, enabled: Boolean) {
    viewModelScope.launch { settingsRepository.toggleWifiPattern(pattern, enabled) }
}

fun MainViewModel.toggleGnssPattern(pattern: GnssPattern, enabled: Boolean) {
    viewModelScope.launch { settingsRepository.toggleGnssPattern(pattern, enabled) }
}

fun MainViewModel.toggleRfPattern(pattern: RfPattern, enabled: Boolean) {
    viewModelScope.launch { settingsRepository.toggleRfPattern(pattern, enabled) }
}

fun MainViewModel.toggleUltrasonicPattern(pattern: UltrasonicPattern, enabled: Boolean) {
    viewModelScope.launch { settingsRepository.toggleUltrasonicPattern(pattern, enabled) }
}

// ========== OUI Database Management ==========

fun MainViewModel.setOuiAutoUpdate(enabled: Boolean) {
    viewModelScope.launch {
        ouiSettingsRepository.setAutoUpdateEnabled(enabled)
        if (enabled) {
            val settings = ouiSettings.value
            OuiUpdateWorker.schedulePeriodicUpdate(
                application,
                settings.updateIntervalHours,
                settings.useWifiOnly
            )
        } else {
            OuiUpdateWorker.cancelAll(application)
        }
    }
}

fun MainViewModel.setOuiUpdateInterval(hours: Int) {
    viewModelScope.launch {
        ouiSettingsRepository.setUpdateInterval(hours)
        if (ouiSettings.value.autoUpdateEnabled) {
            OuiUpdateWorker.schedulePeriodicUpdate(
                application,
                hours,
                ouiSettings.value.useWifiOnly
            )
        }
    }
}

fun MainViewModel.setOuiWifiOnly(wifiOnly: Boolean) {
    viewModelScope.launch {
        ouiSettingsRepository.setUseWifiOnly(wifiOnly)
        if (ouiSettings.value.autoUpdateEnabled) {
            OuiUpdateWorker.schedulePeriodicUpdate(
                application,
                ouiSettings.value.updateIntervalHours,
                wifiOnly
            )
        }
    }
}

fun MainViewModel.triggerOuiUpdate() {
    _isOuiUpdating.value = true

    val workId = OuiUpdateWorker.triggerImmediateUpdate(application)

    // Observe work completion
    viewModelScope.launch {
        workManager.getWorkInfoByIdFlow(workId).collect { workInfo ->
            if (workInfo?.state?.isFinished == true) {
                _isOuiUpdating.value = false
            }
        }
    }
}

// ========== Network Settings Management ==========

fun MainViewModel.setUseTorProxy(enabled: Boolean) {
    viewModelScope.launch {
        networkSettingsRepository.setUseTorProxy(enabled)
    }
}

fun MainViewModel.launchOrbot() {
    orbotHelper.launchOrbot()
}

fun MainViewModel.openOrbotInstallPage() {
    orbotHelper.openOrbotInstallPage()
}

fun MainViewModel.testTorConnection() {
    viewModelScope.launch {
        _isTorTesting.value = true
        _torConnectionStatus.value = torAwareHttpClient.testTorConnection()
        _isTorTesting.value = false
    }
}

fun MainViewModel.clearTorStatus() {
    _torConnectionStatus.value = null
}

// ========== Broadcast Settings Management ==========

fun MainViewModel.setBroadcastEnabled(enabled: Boolean) {
    viewModelScope.launch {
        broadcastSettingsRepository.setEnabled(enabled)
    }
}

fun MainViewModel.setBroadcastOnDetection(enabled: Boolean) {
    viewModelScope.launch {
        broadcastSettingsRepository.setBroadcastOnDetection(enabled)
    }
}

fun MainViewModel.setBroadcastOnCellular(enabled: Boolean) {
    viewModelScope.launch {
        broadcastSettingsRepository.setBroadcastOnCellular(enabled)
    }
}

fun MainViewModel.setBroadcastOnSatellite(enabled: Boolean) {
    viewModelScope.launch {
        broadcastSettingsRepository.setBroadcastOnSatellite(enabled)
    }
}

fun MainViewModel.setBroadcastOnWifi(enabled: Boolean) {
    viewModelScope.launch {
        broadcastSettingsRepository.setBroadcastOnWifi(enabled)
    }
}

fun MainViewModel.setBroadcastOnRf(enabled: Boolean) {
    viewModelScope.launch {
        broadcastSettingsRepository.setBroadcastOnRf(enabled)
    }
}

fun MainViewModel.setBroadcastOnUltrasonic(enabled: Boolean) {
    viewModelScope.launch {
        broadcastSettingsRepository.setBroadcastOnUltrasonic(enabled)
    }
}

fun MainViewModel.setBroadcastIncludeLocation(enabled: Boolean) {
    viewModelScope.launch {
        broadcastSettingsRepository.setIncludeLocation(enabled)
    }
}

fun MainViewModel.setBroadcastMinThreatLevel(level: String) {
    viewModelScope.launch {
        broadcastSettingsRepository.setMinThreatLevel(level)
    }
}

// ========== Privacy Settings Management ==========

fun MainViewModel.setEphemeralMode(enabled: Boolean) {
    viewModelScope.launch {
        privacySettingsRepository.setEphemeralModeEnabled(enabled)
        // Update FlipperScannerManager ephemeral mode
        flipperScannerManager.setEphemeralMode(enabled)
        if (enabled) {
            // Clear persistent storage when enabling ephemeral mode
            repository.deleteAllDetections()
        } else {
            // Clear ephemeral storage when disabling
            ephemeralRepository.clearAll()
        }
    }
}

fun MainViewModel.setRetentionPeriod(period: RetentionPeriod) {
    viewModelScope.launch {
        privacySettingsRepository.setRetentionPeriod(period)
        // Update the data retention worker schedule
        DataRetentionWorker.schedulePeriodicCleanupHours(application, period.hours)
    }
}

fun MainViewModel.setStoreLocationWithDetections(enabled: Boolean) {
    viewModelScope.launch {
        privacySettingsRepository.setStoreLocationWithDetections(enabled)
    }
}

fun MainViewModel.setAutoPurgeOnScreenLock(enabled: Boolean) {
    viewModelScope.launch {
        privacySettingsRepository.setAutoPurgeOnScreenLock(enabled)
    }
}

fun MainViewModel.setQuickWipeRequiresConfirmation(required: Boolean) {
    viewModelScope.launch {
        privacySettingsRepository.setQuickWipeRequiresConfirmation(required)
    }
}

/**
 * Perform a quick wipe - delete all detection data.
 */
fun MainViewModel.performQuickWipe() {
    viewModelScope.launch {
        // Clear persistent database
        repository.deleteAllDetections()

        // Clear ephemeral storage
        ephemeralRepository.clearAll()

        // Clear service runtime data via IPC
        serviceConnection.clearSeenDevices()
        serviceConnection.clearCellularHistory()
        serviceConnection.clearSatelliteHistory()
        serviceConnection.clearErrors()
        serviceConnection.clearLearnedSignatures()
        serviceConnection.resetDetectionCount()
    }
}

/**
 * Clear seen devices via IPC.
 */
fun MainViewModel.clearSeenDevices() {
    serviceConnection.clearSeenDevices()
}

/**
 * Clear cellular history via IPC.
 */
fun MainViewModel.clearCellularHistory() {
    serviceConnection.clearCellularHistory()
}

/**
 * Clear satellite history via IPC.
 */
fun MainViewModel.clearSatelliteHistory() {
    serviceConnection.clearSatelliteHistory()
}
