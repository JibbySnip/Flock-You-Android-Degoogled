package com.flockyou.ui.screens

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import com.flockyou.data.FlipperViewMode
import com.flockyou.scanner.flipper.FlipperConnectionPreference
import com.flockyou.scanner.flipper.FlipperConnectionState
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ========== Flipper Zero Connection ==========

/**
 * Connect to Flipper Zero using preferred method from settings.
 */
fun MainViewModel.connectFlipper() {
    flipperScannerManager.connect()
}

/**
 * Connect to Flipper Zero via USB with user feedback.
 */
fun MainViewModel.connectFlipperViaUsb() {
    viewModelScope.launch {
        val success = flipperScannerManager.connectUsb()
        if (!success) {
            val usbDevices = flipperScannerManager.findUsbDevices()
            val message = if (usbDevices.isEmpty()) {
                "No Flipper found via USB. Make sure it's connected and the Flock Bridge app is running."
            } else {
                "USB device found but couldn't connect. Check USB permissions."
            }
            Toast.makeText(getApplication(), message, Toast.LENGTH_LONG).show()
        }
        // Close the device picker after attempting USB connection
        hideFlipperDevicePicker()
    }
}

/**
 * Disconnect from Flipper Zero.
 */
fun MainViewModel.disconnectFlipper() {
    flipperScannerManager.disconnect()
}

// ========== Flipper Device Picker ==========

/**
 * Show the device picker dialog/bottom sheet.
 */
fun MainViewModel.showFlipperDevicePicker() {
    _uiState.update { it.copy(flipperShowDevicePicker = true) }
    // Start scanning for devices automatically
    flipperScannerManager.startDeviceScan()
}

/**
 * Hide the device picker dialog/bottom sheet.
 */
fun MainViewModel.hideFlipperDevicePicker() {
    _uiState.update { it.copy(flipperShowDevicePicker = false) }
    // Stop scanning when picker is dismissed
    flipperScannerManager.stopDeviceScan()
}

/**
 * Start scanning for Flipper devices via Bluetooth.
 */
fun MainViewModel.startFlipperDeviceScan() {
    flipperScannerManager.startDeviceScan()
}

/**
 * Stop scanning for Flipper devices.
 */
fun MainViewModel.stopFlipperDeviceScan() {
    flipperScannerManager.stopDeviceScan()
}

/**
 * Connect to a discovered Flipper device from the picker.
 */
fun MainViewModel.connectToDiscoveredFlipper(device: com.flockyou.scanner.flipper.DiscoveredFlipperDevice) {
    flipperScannerManager.connectBluetooth(device.address, device.name)
    hideFlipperDevicePicker()
}

/**
 * Connect to a recent Flipper device from history.
 */
fun MainViewModel.connectToRecentFlipper(device: com.flockyou.scanner.flipper.RecentFlipperDevice) {
    flipperScannerManager.connectToRecentDevice(device)
    hideFlipperDevicePicker()
}

/**
 * Remove a device from connection history.
 */
fun MainViewModel.removeFlipperFromHistory(address: String) {
    flipperScannerManager.removeFromHistory(address)
}

/**
 * Cancel auto-reconnect attempts.
 */
fun MainViewModel.cancelFlipperAutoReconnect() {
    flipperScannerManager.cancelAutoReconnect()
}

/**
 * Get signal strength level (0-4) for current connection.
 */
fun MainViewModel.getFlipperSignalLevel(): Int {
    return flipperScannerManager.getSignalLevel()
}

/**
 * Pause Flipper scanning (keeps connection alive).
 */
fun MainViewModel.pauseFlipperScanning() {
    flipperScannerManager.pauseScanning()
}

/**
 * Resume Flipper scanning.
 */
fun MainViewModel.resumeFlipperScanning() {
    flipperScannerManager.resumeScanning()
}

/**
 * Toggle Flipper pause/resume state.
 */
fun MainViewModel.toggleFlipperPause() {
    flipperScannerManager.togglePauseScanning()
}

/**
 * Trigger a manual Flipper scan for a specific scan type.
 * Returns true if scan was triggered, false if on cooldown or not connected.
 */
fun MainViewModel.triggerFlipperManualScan(scanType: com.flockyou.scanner.flipper.FlipperScanType): Boolean {
    return flipperScannerManager.triggerManualScan(scanType)
}

// ========== Flipper UI Settings ==========

/**
 * Set Flipper tab view mode (Detailed or Summary).
 */
fun MainViewModel.setFlipperViewMode(mode: FlipperViewMode) {
    viewModelScope.launch {
        flipperUiSettingsRepository.setViewMode(mode)
    }
}

/**
 * Toggle Flipper status card expanded state.
 */
fun MainViewModel.setFlipperStatusCardExpanded(expanded: Boolean) {
    viewModelScope.launch {
        flipperUiSettingsRepository.setStatusCardExpanded(expanded)
    }
}

/**
 * Toggle Flipper scheduler card expanded state.
 */
fun MainViewModel.setFlipperSchedulerCardExpanded(expanded: Boolean) {
    viewModelScope.launch {
        flipperUiSettingsRepository.setSchedulerCardExpanded(expanded)
    }
}

/**
 * Toggle Flipper stats card expanded state.
 */
fun MainViewModel.setFlipperStatsCardExpanded(expanded: Boolean) {
    viewModelScope.launch {
        flipperUiSettingsRepository.setStatsCardExpanded(expanded)
    }
}

/**
 * Toggle Flipper capabilities card expanded state.
 */
fun MainViewModel.setFlipperCapabilitiesCardExpanded(expanded: Boolean) {
    viewModelScope.launch {
        flipperUiSettingsRepository.setCapabilitiesCardExpanded(expanded)
    }
}

/**
 * Toggle Flipper advanced card expanded state.
 */
fun MainViewModel.setFlipperAdvancedCardExpanded(expanded: Boolean) {
    viewModelScope.launch {
        flipperUiSettingsRepository.setAdvancedCardExpanded(expanded)
    }
}

// ========== Flipper Onboarding ==========

/**
 * Show the Flipper setup wizard.
 */
fun MainViewModel.showFlipperSetupWizard() {
    _showFlipperSetupWizard.value = true
}

/**
 * Dismiss the Flipper setup wizard without completing.
 */
fun MainViewModel.dismissFlipperSetupWizard() {
    _showFlipperSetupWizard.value = false
}

/**
 * Complete the Flipper setup wizard (with "Don't show again" selected).
 */
fun MainViewModel.completeFlipperSetupWizard() {
    viewModelScope.launch {
        flipperOnboardingRepository.setSetupWizardCompleted(true)
        _showFlipperSetupWizard.value = false
    }
}

/**
 * Record that the user has successfully connected a Flipper.
 * Called when connection state becomes READY.
 */
fun MainViewModel.recordFlipperConnected() {
    viewModelScope.launch {
        flipperOnboardingRepository.setHasEverConnected(true)
    }
}

/**
 * Toggle visibility of scan type tips in the scheduler card.
 */
fun MainViewModel.setShowScanTypeTips(show: Boolean) {
    viewModelScope.launch {
        flipperOnboardingRepository.setShowScanTypeTips(show)
    }
}

/**
 * Toggle visibility of detection explanations section.
 */
fun MainViewModel.setShowDetectionExplanations(show: Boolean) {
    viewModelScope.launch {
        flipperOnboardingRepository.setShowDetectionExplanations(show)
    }
}

/**
 * Increment Flipper tab visit count for progressive disclosure.
 */
fun MainViewModel.incrementFlipperTabVisitCount() {
    viewModelScope.launch {
        flipperOnboardingRepository.incrementFlipperTabVisitCount()
    }
}

/**
 * Reset Flipper onboarding state (for testing).
 */
fun MainViewModel.resetFlipperOnboarding() {
    viewModelScope.launch {
        flipperOnboardingRepository.resetOnboarding()
    }
}

// ========== Flipper Settings (consolidated from FlipperSettingsViewModel) ==========

/**
 * Enable/disable Flipper integration.
 */
fun MainViewModel.setFlipperEnabled(enabled: Boolean) {
    viewModelScope.launch {
        flipperSettingsRepository.setFlipperEnabled(enabled)
        if (enabled) {
            flipperScannerManager.start()
        } else {
            flipperScannerManager.stop()
        }
    }
}

/**
 * Set auto-connect via USB.
 */
fun MainViewModel.setFlipperAutoConnectUsb(enabled: Boolean) {
    viewModelScope.launch {
        flipperSettingsRepository.setAutoConnectUsb(enabled)
    }
}

/**
 * Set auto-connect via Bluetooth.
 */
fun MainViewModel.setFlipperAutoConnectBluetooth(enabled: Boolean) {
    viewModelScope.launch {
        flipperSettingsRepository.setAutoConnectBluetooth(enabled)
    }
}

/**
 * Set preferred connection method.
 */
fun MainViewModel.setFlipperPreferredConnection(preference: FlipperConnectionPreference) {
    viewModelScope.launch {
        flipperSettingsRepository.setPreferredConnection(preference)
    }
}

/**
 * Enable/disable WiFi scanning via Flipper.
 */
fun MainViewModel.setFlipperEnableWifiScanning(enabled: Boolean) {
    viewModelScope.launch {
        flipperSettingsRepository.setEnableWifiScanning(enabled)
    }
}

/**
 * Enable/disable Sub-GHz scanning via Flipper.
 */
fun MainViewModel.setFlipperEnableSubGhzScanning(enabled: Boolean) {
    viewModelScope.launch {
        flipperSettingsRepository.setEnableSubGhzScanning(enabled)
    }
}

/**
 * Enable/disable BLE scanning via Flipper.
 */
fun MainViewModel.setFlipperEnableBleScanning(enabled: Boolean) {
    viewModelScope.launch {
        flipperSettingsRepository.setEnableBleScanning(enabled)
    }
}

/**
 * Enable/disable IR scanning via Flipper.
 */
fun MainViewModel.setFlipperEnableIrScanning(enabled: Boolean) {
    viewModelScope.launch {
        flipperSettingsRepository.setEnableIrScanning(enabled)
    }
}

/**
 * Enable/disable NFC scanning via Flipper.
 */
fun MainViewModel.setFlipperEnableNfcScanning(enabled: Boolean) {
    viewModelScope.launch {
        flipperSettingsRepository.setEnableNfcScanning(enabled)
    }
}

/**
 * Enable/disable WIPS (Wireless Intrusion Prevention System).
 */
fun MainViewModel.setFlipperWipsEnabled(enabled: Boolean) {
    viewModelScope.launch {
        flipperSettingsRepository.setWipsEnabled(enabled)
    }
}

/**
 * Enable/disable Evil Twin detection.
 */
fun MainViewModel.setFlipperWipsEvilTwinDetection(enabled: Boolean) {
    viewModelScope.launch {
        flipperSettingsRepository.setWipsEvilTwinDetection(enabled)
    }
}

/**
 * Enable/disable Deauth attack detection.
 */
fun MainViewModel.setFlipperWipsDeauthDetection(enabled: Boolean) {
    viewModelScope.launch {
        flipperSettingsRepository.setWipsDeauthDetection(enabled)
    }
}

/**
 * Enable/disable Karma attack detection.
 */
fun MainViewModel.setFlipperWipsKarmaDetection(enabled: Boolean) {
    viewModelScope.launch {
        flipperSettingsRepository.setWipsKarmaDetection(enabled)
    }
}

/**
 * Enable/disable Rogue AP detection.
 */
fun MainViewModel.setFlipperWipsRogueApDetection(enabled: Boolean) {
    viewModelScope.launch {
        flipperSettingsRepository.setWipsRogueApDetection(enabled)
    }
}

/**
 * Install the Flock Bridge FAP to the connected Flipper Zero.
 */
fun MainViewModel.installFapToFlipper() {
    val connectionState = _uiState.value.flipperConnectionState
    if (connectionState != FlipperConnectionState.READY) {
        Toast.makeText(getApplication(), "Flipper not connected", Toast.LENGTH_SHORT).show()
        return
    }

    viewModelScope.launch {
        _flipperIsInstalling.value = true
        _flipperInstallProgress.value = "Preparing FAP file..."

        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                // Step 1: Extract FAP from assets to temp file
                _flipperInstallProgress.value = "Extracting FAP from app..."
                val context = getApplication<Application>()
                val tempFile = File(context.cacheDir, "flock_bridge.fap")

                try {
                    context.assets.open(MainViewModel.FAP_ASSET_PATH).use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "FAP not found in assets, will need to build it first", e)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        _flipperInstallProgress.value = "FAP not bundled. Run: ./gradlew bundleFlipperFap"
                        Toast.makeText(
                            context,
                            "FAP file not found. Build it first using Gradle.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@withContext
                }

                // Step 2: Upload to Flipper
                _flipperInstallProgress.value = "Uploading to Flipper Zero..."
                val success = flipperScannerManager.uploadFile(
                    localFile = tempFile,
                    remotePath = MainViewModel.FAP_DEST_PATH
                ) { progress ->
                    _flipperInstallProgress.value = "Uploading: ${(progress * 100).toInt()}%"
                }

                // Step 3: Cleanup temp file
                tempFile.delete()

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (success) {
                        _flipperInstallProgress.value = "Installation complete!"
                        Toast.makeText(
                            context,
                            "Flock Bridge installed! Find it in Tools on your Flipper.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        _flipperInstallProgress.value = "Installation failed"
                        Toast.makeText(
                            context,
                            "Failed to install FAP to Flipper",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error installing FAP", e)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                _flipperInstallProgress.value = "Error: ${e.message}"
                Toast.makeText(getApplication(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } finally {
            _flipperIsInstalling.value = false
            // Clear progress after delay
            viewModelScope.launch {
                delay(3000)
                _flipperInstallProgress.value = null
            }
        }
    }
}
