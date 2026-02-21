package com.flockyou.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.Divider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flockyou.data.FlipperUiSettings
import com.flockyou.data.FlipperViewMode
import com.flockyou.scanner.flipper.FlipperClient
import com.flockyou.scanner.flipper.FlipperConnectionPreference
import com.flockyou.scanner.flipper.FlipperConnectionState
import com.flockyou.scanner.flipper.FlipperOnboardingSettings
import com.flockyou.scanner.flipper.FlipperSettings
import kotlinx.coroutines.delay

// ============================================================================
// Flipper Zero Tab Content
// ============================================================================

@Composable
fun FlipperTabContent(
    modifier: Modifier = Modifier,
    connectionState: FlipperConnectionState,
    connectionType: FlipperClient.ConnectionType,
    flipperStatus: com.flockyou.scanner.flipper.FlipperStatusResponse?,
    isScanning: Boolean,
    detectionCount: Int,
    wipsAlertCount: Int,
    lastError: String?,
    advancedMode: Boolean = false,
    scanSchedulerStatus: com.flockyou.scanner.flipper.ScanSchedulerStatus = com.flockyou.scanner.flipper.ScanSchedulerStatus(),
    onboardingSettings: FlipperOnboardingSettings = FlipperOnboardingSettings(),
    showSetupWizard: Boolean = false,
    // UX improvement parameters
    autoReconnectState: com.flockyou.scanner.flipper.AutoReconnectState = com.flockyou.scanner.flipper.AutoReconnectState(),
    discoveredDevices: List<com.flockyou.scanner.flipper.DiscoveredFlipperDevice> = emptyList(),
    recentDevices: List<com.flockyou.scanner.flipper.RecentFlipperDevice> = emptyList(),
    isScanningForDevices: Boolean = false,
    connectionRssi: Int? = null,
    showDevicePicker: Boolean = false,
    // Settings parameters
    flipperSettings: FlipperSettings = FlipperSettings(),
    isInstalling: Boolean = false,
    installProgress: String? = null,
    // Callbacks
    onConnect: () -> Unit = {},
    onDisconnect: () -> Unit = {},
    onTogglePause: () -> Unit = {},
    onTriggerManualScan: (com.flockyou.scanner.flipper.FlipperScanType) -> Unit = {},
    onShowSetupWizard: () -> Unit = {},
    onDismissSetupWizard: () -> Unit = {},
    onCompleteSetupWizard: () -> Unit = {},
    onLearnMore: () -> Unit = {},
    onTroubleshooting: () -> Unit = {},
    // Device picker callbacks
    onShowDevicePicker: () -> Unit = {},
    onHideDevicePicker: () -> Unit = {},
    onStartDeviceScan: () -> Unit = {},
    onStopDeviceScan: () -> Unit = {},
    onSelectDiscoveredDevice: (com.flockyou.scanner.flipper.DiscoveredFlipperDevice) -> Unit = {},
    onSelectRecentDevice: (com.flockyou.scanner.flipper.RecentFlipperDevice) -> Unit = {},
    onRemoveRecentDevice: (String) -> Unit = {},
    onCancelAutoReconnect: () -> Unit = {},
    onConnectUsb: () -> Unit = {},
    // Settings callbacks
    onInstallFap: () -> Unit = {},
    onPreferredConnectionChange: (FlipperConnectionPreference) -> Unit = {},
    onAutoConnectUsbChange: (Boolean) -> Unit = {},
    onAutoConnectBluetoothChange: (Boolean) -> Unit = {},
    onEnableWifiScanningChange: (Boolean) -> Unit = {},
    onEnableSubGhzScanningChange: (Boolean) -> Unit = {},
    onEnableBleScanningChange: (Boolean) -> Unit = {},
    onEnableIrScanningChange: (Boolean) -> Unit = {},
    onEnableNfcScanningChange: (Boolean) -> Unit = {},
    onWipsEnabledChange: (Boolean) -> Unit = {},
    onWipsEvilTwinChange: (Boolean) -> Unit = {},
    onWipsDeauthChange: (Boolean) -> Unit = {},
    onWipsKarmaChange: (Boolean) -> Unit = {},
    onWipsRogueApChange: (Boolean) -> Unit = {},
    onNavigateToActiveProbes: () -> Unit = {},
    // Additional UI settings parameters (for compatibility)
    flipperUiSettings: com.flockyou.data.FlipperUiSettings = com.flockyou.data.FlipperUiSettings(),
    onViewModeChange: (com.flockyou.data.FlipperViewMode) -> Unit = {},
    onStatusCardExpandedChange: (Boolean) -> Unit = {},
    onSchedulerCardExpandedChange: (Boolean) -> Unit = {},
    onStatsCardExpandedChange: (Boolean) -> Unit = {},
    onCapabilitiesCardExpandedChange: (Boolean) -> Unit = {},
    onAdvancedCardExpandedChange: (Boolean) -> Unit = {}
) {
    // Show setup wizard for first-time users
    if (showSetupWizard && !onboardingSettings.hasCompletedSetupWizard && connectionState == FlipperConnectionState.DISCONNECTED) {
        FlipperSetupWizard(
            onComplete = onCompleteSetupWizard,
            onDismiss = onDismissSetupWizard,
            onConnect = onConnect,
            modifier = modifier
        )
        return
    }

    // Device picker bottom sheet
    if (showDevicePicker) {
        FlipperDevicePickerBottomSheet(
            discoveredDevices = discoveredDevices,
            recentDevices = recentDevices,
            isScanning = isScanningForDevices,
            onDismiss = onHideDevicePicker,
            onStartScan = onStartDeviceScan,
            onStopScan = onStopDeviceScan,
            onSelectDiscovered = onSelectDiscoveredDevice,
            onSelectRecent = onSelectRecentDevice,
            onRemoveRecent = onRemoveRecentDevice,
            onConnectUsb = onConnectUsb
        )
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Connection Status Card with controls
        item(key = "flipper_connection") {
            FlipperConnectionCardEnhanced(
                connectionState = connectionState,
                connectionType = connectionType,
                lastError = lastError,
                autoReconnectState = autoReconnectState,
                connectionRssi = connectionRssi,
                onConnect = onShowDevicePicker,
                onDisconnect = onDisconnect,
                onCancelAutoReconnect = onCancelAutoReconnect
            )
        }

        // Show content based on connection state
        when (connectionState) {
            FlipperConnectionState.READY -> {
                // Flipper Status Card
                item(key = "flipper_status") {
                    FlipperStatusCard(
                        flipperStatus = flipperStatus,
                        isScanning = isScanning
                    )
                }

                // Scan Scheduler Status Card
                item(key = "flipper_scheduler") {
                    FlipperScanSchedulerCard(
                        scanSchedulerStatus = scanSchedulerStatus
                    )
                }

                // Scan Statistics Card
                item(key = "flipper_stats") {
                    FlipperScanStatsCard(
                        detectionCount = detectionCount,
                        wipsAlertCount = wipsAlertCount,
                        flipperStatus = flipperStatus
                    )
                }

                // Capabilities Card
                item(key = "flipper_capabilities") {
                    FlipperCapabilitiesCard(
                        flipperStatus = flipperStatus
                    )
                }

                // Flock Bridge FAP Install Card (when connected)
                item(key = "flipper_fap_install") {
                    FlipperFapInstallCard(
                        isInstalling = isInstalling,
                        installProgress = installProgress,
                        onInstall = onInstallFap
                    )
                }

                // Configuration Section Header
                item(key = "flipper_config_header") {
                    Text(
                        text = "CONFIGURATION",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // Connection Preferences Card
                item(key = "flipper_connection_prefs") {
                    FlipperConnectionPreferencesCard(
                        preferredConnection = flipperSettings.preferredConnection,
                        autoConnectUsb = flipperSettings.autoConnectUsb,
                        autoConnectBluetooth = flipperSettings.autoConnectBluetooth,
                        onPreferredConnectionChange = onPreferredConnectionChange,
                        onAutoConnectUsbChange = onAutoConnectUsbChange,
                        onAutoConnectBluetoothChange = onAutoConnectBluetoothChange
                    )
                }

                // Scan Modules Card
                item(key = "flipper_scan_modules") {
                    FlipperScanModulesCard(
                        enableWifi = flipperSettings.enableWifiScanning,
                        enableSubGhz = flipperSettings.enableSubGhzScanning,
                        enableBle = flipperSettings.enableBleScanning,
                        enableIr = flipperSettings.enableIrScanning,
                        enableNfc = flipperSettings.enableNfcScanning,
                        onWifiChange = onEnableWifiScanningChange,
                        onSubGhzChange = onEnableSubGhzScanningChange,
                        onBleChange = onEnableBleScanningChange,
                        onIrChange = onEnableIrScanningChange,
                        onNfcChange = onEnableNfcScanningChange
                    )
                }

                // WIPS Settings Card
                item(key = "flipper_wips_settings") {
                    FlipperWipsSettingsCard(
                        wipsEnabled = flipperSettings.wipsEnabled,
                        evilTwinDetection = flipperSettings.wipsEvilTwinDetection,
                        deauthDetection = flipperSettings.wipsDeauthDetection,
                        karmaDetection = flipperSettings.wipsKarmaDetection,
                        rogueApDetection = flipperSettings.wipsRogueApDetection,
                        onWipsEnabledChange = onWipsEnabledChange,
                        onEvilTwinChange = onWipsEvilTwinChange,
                        onDeauthChange = onWipsDeauthChange,
                        onKarmaChange = onWipsKarmaChange,
                        onRogueApChange = onWipsRogueApChange
                    )
                }

                // Active Probes Card (navigation entry)
                item(key = "flipper_active_probes") {
                    FlipperActiveProbesCard(
                        onNavigate = onNavigateToActiveProbes
                    )
                }

                // Advanced Mode: Detection Scheduler & Raw Data
                if (advancedMode) {
                    item(key = "flipper_advanced") {
                        FlipperAdvancedInfoCard(
                            flipperStatus = flipperStatus
                        )
                    }
                }
            }
            FlipperConnectionState.DISCONNECTED -> {
                // Show enhanced disconnected card with helpful empty state
                item(key = "flipper_disconnected") {
                    FlipperDisconnectedCardEnhanced(
                        hasEverConnected = onboardingSettings.hasEverConnected,
                        onConnect = onConnect,
                        onShowSetupWizard = onShowSetupWizard,
                        onLearnMore = onLearnMore,
                        onTroubleshooting = onTroubleshooting
                    )
                }
            }
            FlipperConnectionState.CONNECTING,
            FlipperConnectionState.CONNECTED,
            FlipperConnectionState.DISCOVERING_SERVICES,
            FlipperConnectionState.LAUNCHING_FAP -> {
                // Show connecting state
                item(key = "flipper_connecting") {
                    FlipperConnectingCard()
                }
            }
            FlipperConnectionState.ERROR -> {
                // Show error state with retry option
                item(key = "flipper_error") {
                    FlipperErrorCard(lastError = lastError, onRetry = onConnect)
                }
            }
        }
    }
}

@Composable
private fun FlipperConnectionCard(
    connectionState: FlipperConnectionState,
    connectionType: FlipperClient.ConnectionType,
    lastError: String?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (connectionState) {
                FlipperConnectionState.READY -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                FlipperConnectionState.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                FlipperConnectionState.CONNECTING,
                FlipperConnectionState.CONNECTED,
                FlipperConnectionState.DISCOVERING_SERVICES -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (connectionState) {
                        FlipperConnectionState.READY -> Icons.Default.CheckCircle
                        FlipperConnectionState.ERROR -> Icons.Default.Error
                        FlipperConnectionState.CONNECTING,
                        FlipperConnectionState.CONNECTED,
                        FlipperConnectionState.DISCOVERING_SERVICES -> Icons.Default.Sync
                        else -> Icons.Default.UsbOff
                    },
                    contentDescription = null,
                    tint = when (connectionState) {
                        FlipperConnectionState.READY -> MaterialTheme.colorScheme.primary
                        FlipperConnectionState.ERROR -> MaterialTheme.colorScheme.error
                        FlipperConnectionState.CONNECTING,
                        FlipperConnectionState.CONNECTED,
                        FlipperConnectionState.DISCOVERING_SERVICES -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Flipper Zero",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when (connectionState) {
                            FlipperConnectionState.READY -> "Connected via ${connectionType.name}"
                            FlipperConnectionState.CONNECTING -> "Connecting..."
                            FlipperConnectionState.CONNECTED -> "Handshaking..."
                            FlipperConnectionState.DISCOVERING_SERVICES -> "Discovering services..."
                            FlipperConnectionState.LAUNCHING_FAP -> "Launching Flock Bridge app..."
                            FlipperConnectionState.ERROR -> lastError ?: "Connection error"
                            FlipperConnectionState.DISCONNECTED -> "Not connected"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Connection type badge when connected
                if (connectionState == FlipperConnectionState.READY) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (connectionType == FlipperClient.ConnectionType.USB)
                                    Icons.Default.Usb else Icons.Default.Bluetooth,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = connectionType.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Connection control buttons
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (connectionState) {
                    FlipperConnectionState.READY -> {
                        OutlinedButton(
                            onClick = onDisconnect,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.LinkOff,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Disconnect")
                        }
                    }
                    FlipperConnectionState.DISCONNECTED,
                    FlipperConnectionState.ERROR -> {
                        Button(onClick = onConnect) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Connect")
                        }
                    }
                    else -> {
                        // Connecting states - show a loading indicator
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlipperStatusCard(
    flipperStatus: com.flockyou.scanner.flipper.FlipperStatusResponse?,
    isScanning: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "DEVICE STATUS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (flipperStatus != null) {
                // Battery row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when {
                                flipperStatus.batteryPercent > 80 -> Icons.Default.BatteryFull
                                flipperStatus.batteryPercent > 50 -> Icons.Default.Battery5Bar
                                flipperStatus.batteryPercent > 20 -> Icons.Default.Battery3Bar
                                else -> Icons.Default.Battery1Bar
                            },
                            contentDescription = null,
                            tint = when {
                                flipperStatus.batteryPercent > 20 -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.error
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Battery")
                    }
                    Text(
                        text = "${flipperStatus.batteryPercent}%",
                        fontWeight = FontWeight.Bold,
                        color = if (flipperStatus.batteryPercent > 20)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                // Uptime row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Uptime")
                    }
                    Text(
                        text = formatUptime(flipperStatus.uptimeSeconds),
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                // Scanning status row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (isScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scanning")
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isScanning)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (isScanning) "ACTIVE" else "IDLE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            } else {
                Text(
                    text = "Waiting for status update...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FlipperScanStatsCard(
    detectionCount: Int,
    wipsAlertCount: Int,
    flipperStatus: com.flockyou.scanner.flipper.FlipperStatusResponse?
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "SCAN STATISTICS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Stats grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "Detections",
                    value = detectionCount.toString(),
                    icon = Icons.Default.Sensors
                )
                StatItem(
                    label = "WIPS Alerts",
                    value = wipsAlertCount.toString(),
                    icon = Icons.Default.Warning,
                    valueColor = if (wipsAlertCount > 0) MaterialTheme.colorScheme.error else null
                )
            }

            if (flipperStatus != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))

                // Detailed stats from Flipper
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MiniStatItem("WiFi", flipperStatus.wifiScanCount.toString())
                    MiniStatItem("Sub-GHz", flipperStatus.subGhzDetectionCount.toString())
                    MiniStatItem("BLE", flipperStatus.bleScanCount.toString())
                    MiniStatItem("NFC", flipperStatus.nfcDetectionCount.toString())
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    valueColor: Color? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = valueColor ?: MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MiniStatItem(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Format a timestamp as relative time (e.g., "12s ago", "2m ago")
 */
@Composable
private fun formatRelativeTime(timestampMs: Long?): String {
    if (timestampMs == null) return "Never"

    // Use remember with a key that updates every second
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    val diff = now - timestampMs
    return when {
        diff < 1000 -> "Just now"
        diff < 60_000 -> "${diff / 1000}s ago"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        else -> "${diff / 86400_000}d ago"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlipperScanSchedulerCard(
    scanSchedulerStatus: com.flockyou.scanner.flipper.ScanSchedulerStatus,
    onTogglePause: () -> Unit = {},
    onTriggerManualScan: (com.flockyou.scanner.flipper.FlipperScanType) -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (scanSchedulerStatus.isPaused)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
            else
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with pause/resume button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (scanSchedulerStatus.isPaused)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (scanSchedulerStatus.isPaused) "SCANNING PAUSED" else "SCAN SCHEDULER",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (scanSchedulerStatus.isPaused)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.secondary
                        )
                        if (scanSchedulerStatus.isPaused) {
                            Text(
                                text = "Connection active, scans stopped",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Pause/Resume button
                IconButton(
                    onClick = onTogglePause,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (scanSchedulerStatus.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (scanSchedulerStatus.isPaused) "Resume scanning" else "Pause scanning",
                        tint = if (scanSchedulerStatus.isPaused)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Active scan loops with scan now buttons and timestamps
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // WiFi scan
                ScanLoopChipWithControls(
                    label = "WiFi",
                    isActive = scanSchedulerStatus.wifiScanActive && !scanSchedulerStatus.isPaused,
                    isScanning = scanSchedulerStatus.isWifiScanning,
                    intervalSeconds = scanSchedulerStatus.wifiScanIntervalSeconds,
                    lastScanTime = scanSchedulerStatus.lastWifiScanTime,
                    cooldownUntil = scanSchedulerStatus.wifiScanCooldownUntil,
                    isPaused = scanSchedulerStatus.isPaused,
                    onScanNow = { onTriggerManualScan(com.flockyou.scanner.flipper.FlipperScanType.WIFI) }
                )
                // Sub-GHz scan
                ScanLoopChipWithControls(
                    label = "Sub-GHz",
                    isActive = scanSchedulerStatus.subGhzScanActive && !scanSchedulerStatus.isPaused,
                    isScanning = scanSchedulerStatus.isSubGhzScanning,
                    intervalSeconds = scanSchedulerStatus.subGhzScanIntervalSeconds,
                    lastScanTime = scanSchedulerStatus.lastSubGhzScanTime,
                    cooldownUntil = scanSchedulerStatus.subGhzScanCooldownUntil,
                    isPaused = scanSchedulerStatus.isPaused,
                    onScanNow = { onTriggerManualScan(com.flockyou.scanner.flipper.FlipperScanType.SUB_GHZ) }
                )
                // BLE scan
                ScanLoopChipWithControls(
                    label = "BLE",
                    isActive = scanSchedulerStatus.bleScanActive && !scanSchedulerStatus.isPaused,
                    isScanning = scanSchedulerStatus.isBleScanning,
                    intervalSeconds = scanSchedulerStatus.bleScanIntervalSeconds,
                    lastScanTime = scanSchedulerStatus.lastBleScanTime,
                    cooldownUntil = scanSchedulerStatus.bleScanCooldownUntil,
                    isPaused = scanSchedulerStatus.isPaused,
                    onScanNow = { onTriggerManualScan(com.flockyou.scanner.flipper.FlipperScanType.BLE) }
                )
            }

            // Additional info (NFC, IR, WIPS)
            if (scanSchedulerStatus.wipsEnabled || scanSchedulerStatus.nfcScanEnabled || scanSchedulerStatus.irScanEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (scanSchedulerStatus.nfcScanEnabled) {
                        ScanLoopChipWithControls(
                            label = "NFC",
                            isActive = true,
                            isScanning = scanSchedulerStatus.isNfcScanning,
                            intervalSeconds = null,
                            lastScanTime = scanSchedulerStatus.lastNfcScanTime,
                            cooldownUntil = scanSchedulerStatus.nfcScanCooldownUntil,
                            isPaused = scanSchedulerStatus.isPaused,
                            isOnDemand = true,
                            onScanNow = { onTriggerManualScan(com.flockyou.scanner.flipper.FlipperScanType.NFC) }
                        )
                    }
                    if (scanSchedulerStatus.irScanEnabled) {
                        ScanLoopChipWithControls(
                            label = "IR",
                            isActive = true,
                            isScanning = scanSchedulerStatus.isIrScanning,
                            intervalSeconds = null,
                            lastScanTime = scanSchedulerStatus.lastIrScanTime,
                            cooldownUntil = scanSchedulerStatus.irScanCooldownUntil,
                            isPaused = scanSchedulerStatus.isPaused,
                            isOnDemand = true,
                            onScanNow = { onTriggerManualScan(com.flockyou.scanner.flipper.FlipperScanType.IR) }
                        )
                    }
                    if (scanSchedulerStatus.wipsEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FeatureChip("WIPS", true)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Active monitoring",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Sub-GHz frequency range info
            if (scanSchedulerStatus.subGhzScanActive && !scanSchedulerStatus.isPaused) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Sub-GHz range: ${scanSchedulerStatus.subGhzFrequencyStart / 1_000_000}-${scanSchedulerStatus.subGhzFrequencyEnd / 1_000_000} MHz",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Heartbeat status
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                PulsingDot(
                    isActive = scanSchedulerStatus.heartbeatActive,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Heartbeat: ${scanSchedulerStatus.heartbeatIntervalSeconds}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ScanLoopChipWithControls(
    label: String,
    isActive: Boolean,
    isScanning: Boolean,
    intervalSeconds: Int?,
    lastScanTime: Long?,
    cooldownUntil: Long,
    isPaused: Boolean,
    isOnDemand: Boolean = false,
    onScanNow: () -> Unit
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(100)
            now = System.currentTimeMillis()
        }
    }

    val isOnCooldown = now < cooldownUntil

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = when {
            isPaused -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            isScanning -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pulsing indicator
            PulsingDot(
                isActive = isActive && !isPaused,
                isScanning = isScanning,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))

            // Label and interval
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        isPaused -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        isActive -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (intervalSeconds != null) {
                        Text(
                            text = "Every ${intervalSeconds}s",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = " | ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    } else if (isOnDemand) {
                        Text(
                            text = "On-demand",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = " | ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    Text(
                        text = formatRelativeTime(lastScanTime),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (lastScanTime != null)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            // Scan now button
            IconButton(
                onClick = onScanNow,
                enabled = !isOnCooldown && !isScanning,
                modifier = Modifier.size(32.dp)
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Scan now",
                        modifier = Modifier.size(18.dp),
                        tint = if (isOnCooldown)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        else
                            MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun PulsingDot(
    isActive: Boolean,
    isScanning: Boolean = false,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    // Pulsing animation for actively scanning state
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Breathing animation for idle active state
    val breatheAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breatheAlpha"
    )

    Box(
        modifier = Modifier
            .size(10.dp)
            .graphicsLayer {
                if (isScanning) {
                    scaleX = pulseScale
                    scaleY = pulseScale
                    alpha = pulseAlpha
                } else if (isActive) {
                    alpha = breatheAlpha
                }
            }
            .background(
                color = if (isActive) color else color.copy(alpha = 0.3f),
                shape = CircleShape
            )
    )
}

// Keep the original ScanLoopChip for backward compatibility
@Composable
private fun ScanLoopChip(
    label: String,
    isActive: Boolean,
    intervalSeconds: Int
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isActive)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        else
            MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PulsingDot(
                isActive = isActive,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isActive)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isActive) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${intervalSeconds}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FeatureChip(
    label: String,
    isEnabled: Boolean
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isEnabled)
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
        else
            MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isEnabled) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (isEnabled)
                    MaterialTheme.colorScheme.tertiary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isEnabled)
                    MaterialTheme.colorScheme.tertiary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlipperCapabilitiesCard(
    flipperStatus: com.flockyou.scanner.flipper.FlipperStatusResponse?
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "CAPABILITIES",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (flipperStatus != null) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CapabilityChip("WiFi", flipperStatus.wifiBoardConnected)
                    CapabilityChip("Sub-GHz", flipperStatus.subGhzReady)
                    CapabilityChip("BLE", flipperStatus.bleReady)
                    CapabilityChip("IR", flipperStatus.irReady)
                    CapabilityChip("NFC", flipperStatus.nfcReady)
                }
            } else {
                Text(
                    text = "Loading capabilities...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CapabilityChip(
    label: String,
    isAvailable: Boolean
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isAvailable)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isAvailable) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (isAvailable)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isAvailable)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FlipperDisconnectedCard(
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.UsbOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Flipper Zero Not Connected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Connect your Flipper Zero via USB or Bluetooth to extend scanning capabilities with WiFi Board, Sub-GHz, and more.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onConnect) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connect Flipper")
            }
        }
    }
}

@Composable
private fun FlipperConnectingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Connecting to Flipper Zero...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Establishing connection and discovering services",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FlipperErrorCard(
    lastError: String?,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Connection Error",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = lastError ?: "Failed to connect to Flipper Zero",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry Connection")
            }
        }
    }
}

// ============================================================================
// Flipper Settings Cards
// ============================================================================

@Composable
private fun FlipperFapInstallCard(
    isInstalling: Boolean,
    installProgress: String?,
    onInstall: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "FLOCK BRIDGE APP",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        text = "Install or update the Flock Bridge FAP on your Flipper Zero to enable communication.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isInstalling) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = installProgress ?: "Installing...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        Button(
                            onClick = onInstall,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Install FAP to Flipper")
                        }
                    }

                    if (installProgress != null && !isInstalling) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = installProgress,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (installProgress.contains("complete", ignoreCase = true))
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlipperConnectionPreferencesCard(
    preferredConnection: FlipperConnectionPreference,
    autoConnectUsb: Boolean,
    autoConnectBluetooth: Boolean,
    onPreferredConnectionChange: (FlipperConnectionPreference) -> Unit,
    onAutoConnectUsbChange: (Boolean) -> Unit,
    onAutoConnectBluetoothChange: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CONNECTION PREFERENCES",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = when (preferredConnection) {
                        FlipperConnectionPreference.USB_PREFERRED -> "USB"
                        FlipperConnectionPreference.BLUETOOTH_PREFERRED -> "Bluetooth"
                        FlipperConnectionPreference.USB_ONLY -> "USB Only"
                        FlipperConnectionPreference.BLUETOOTH_ONLY -> "BT Only"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        text = "Preferred Connection",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    FlipperConnectionPreference.values().forEach { pref ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPreferredConnectionChange(pref) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = preferredConnection == pref,
                                onClick = { onPreferredConnectionChange(pref) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (pref) {
                                    FlipperConnectionPreference.USB_PREFERRED -> "USB Preferred"
                                    FlipperConnectionPreference.BLUETOOTH_PREFERRED -> "Bluetooth Preferred"
                                    FlipperConnectionPreference.USB_ONLY -> "USB Only"
                                    FlipperConnectionPreference.BLUETOOTH_ONLY -> "Bluetooth Only"
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(12.dp))

                    // Auto-connect toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Auto-connect USB",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = autoConnectUsb,
                            onCheckedChange = onAutoConnectUsbChange
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Auto-connect Bluetooth",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = autoConnectBluetooth,
                            onCheckedChange = onAutoConnectBluetoothChange
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlipperScanModulesCard(
    enableWifi: Boolean,
    enableSubGhz: Boolean,
    enableBle: Boolean,
    enableIr: Boolean,
    enableNfc: Boolean,
    onWifiChange: (Boolean) -> Unit,
    onSubGhzChange: (Boolean) -> Unit,
    onBleChange: (Boolean) -> Unit,
    onIrChange: (Boolean) -> Unit,
    onNfcChange: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val enabledCount = listOf(enableWifi, enableSubGhz, enableBle, enableIr, enableNfc).count { it }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Radar,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SCAN MODULES",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "$enabledCount/5 enabled",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    ScanModuleToggle("WiFi Scanning", "Scan for WiFi networks", enableWifi, onWifiChange)
                    ScanModuleToggle("Sub-GHz Scanning", "Scan radio frequencies (300-928 MHz)", enableSubGhz, onSubGhzChange)
                    ScanModuleToggle("BLE Scanning", "Scan Bluetooth Low Energy devices", enableBle, onBleChange)
                    ScanModuleToggle("IR Scanning", "Detect infrared signals", enableIr, onIrChange)
                    ScanModuleToggle("NFC Scanning", "Detect NFC devices", enableNfc, onNfcChange)
                }
            }
        }
    }
}

@Composable
private fun ScanModuleToggle(
    title: String,
    description: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange
        )
    }
}

@Composable
private fun FlipperWipsSettingsCard(
    wipsEnabled: Boolean,
    evilTwinDetection: Boolean,
    deauthDetection: Boolean,
    karmaDetection: Boolean,
    rogueApDetection: Boolean,
    onWipsEnabledChange: (Boolean) -> Unit,
    onEvilTwinChange: (Boolean) -> Unit,
    onDeauthChange: (Boolean) -> Unit,
    onKarmaChange: (Boolean) -> Unit,
    onRogueApChange: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val enabledCount = if (wipsEnabled) {
        listOf(evilTwinDetection, deauthDetection, karmaDetection, rogueApDetection).count { it }
    } else 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (wipsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "WIPS SETTINGS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (wipsEnabled) "$enabledCount/4 active" else "Disabled",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (wipsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        text = "Wireless Intrusion Prevention System",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Master WIPS toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enable WIPS",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Switch(
                            checked = wipsEnabled,
                            onCheckedChange = onWipsEnabledChange
                        )
                    }

                    if (wipsEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(12.dp))

                        WipsToggle("Evil Twin Detection", "Detect AP impersonation attacks", evilTwinDetection, onEvilTwinChange)
                        WipsToggle("Deauth Detection", "Detect deauthentication attacks", deauthDetection, onDeauthChange)
                        WipsToggle("Karma Detection", "Detect Karma/WiFi Pineapple attacks", karmaDetection, onKarmaChange)
                        WipsToggle("Rogue AP Detection", "Detect unauthorized access points", rogueApDetection, onRogueApChange)
                    }
                }
            }
        }
    }
}

@Composable
private fun WipsToggle(
    title: String,
    description: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange
        )
    }
}

@Composable
private fun FlipperActiveProbesCard(
    onNavigate: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigate() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Active Probes",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "WiFi pentesting tools via Flipper",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Navigate",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlipperAdvancedInfoCard(
    flipperStatus: com.flockyou.scanner.flipper.FlipperStatusResponse?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DeveloperMode,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ADVANCED INFO",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (flipperStatus != null) {
                // Protocol Version
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Protocol Version",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "v${flipperStatus.protocolVersion}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))

                // Detection Scheduler Status
                Text(
                    text = "Scanner Status",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ScannerStatusChip("SubGHz", flipperStatus.subGhzReady, flipperStatus.subGhzDetectionCount)
                    ScannerStatusChip("BLE", flipperStatus.bleReady, flipperStatus.bleScanCount)
                    ScannerStatusChip("NFC", flipperStatus.nfcReady, flipperStatus.nfcDetectionCount)
                    ScannerStatusChip("IR", flipperStatus.irReady, flipperStatus.irDetectionCount)
                    ScannerStatusChip("WiFi", flipperStatus.wifiBoardConnected, flipperStatus.wifiScanCount)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))

                // Raw Detection Counts
                Text(
                    text = "Raw Detection Counts",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    RawDataRow("WiFi Scans", flipperStatus.wifiScanCount)
                    RawDataRow("Sub-GHz Detections", flipperStatus.subGhzDetectionCount)
                    RawDataRow("BLE Scans", flipperStatus.bleScanCount)
                    RawDataRow("IR Detections", flipperStatus.irDetectionCount)
                    RawDataRow("NFC Detections", flipperStatus.nfcDetectionCount)
                    RawDataRow("WIPS Alerts", flipperStatus.wipsAlertCount)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Uptime raw
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Uptime (raw)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${flipperStatus.uptimeSeconds}s",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                Text(
                    text = "Waiting for status data...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ScannerStatusChip(
    name: String,
    isReady: Boolean,
    count: Int
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isReady)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        if (isReady) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                        RoundedCornerShape(3.dp)
                    )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "($count)",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RawDataRow(
    label: String,
    value: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun formatUptime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${secs}s"
        else -> "${secs}s"
    }
}

// ============================================================================
// Flipper UX Improvements - Device Picker & Enhanced Connection Card
// ============================================================================

/**
 * Enhanced connection card with signal strength indicator and auto-reconnect state.
 */
@Composable
private fun FlipperConnectionCardEnhanced(
    connectionState: FlipperConnectionState,
    connectionType: FlipperClient.ConnectionType,
    lastError: String?,
    autoReconnectState: com.flockyou.scanner.flipper.AutoReconnectState,
    connectionRssi: Int?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onCancelAutoReconnect: () -> Unit
) {
    // Pulsing animation for reconnecting state
    val pulseAlpha = if (autoReconnectState.isReconnecting) {
        val infiniteTransition = rememberInfiniteTransition(label = "reconnect_pulse")
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_alpha"
        ).value
    } else {
        1f
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                autoReconnectState.isReconnecting -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                connectionState == FlipperConnectionState.READY -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                connectionState == FlipperConnectionState.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                connectionState in listOf(
                    FlipperConnectionState.CONNECTING,
                    FlipperConnectionState.CONNECTED,
                    FlipperConnectionState.DISCOVERING_SERVICES
                ) -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon with optional pulse animation for reconnecting
                Box {
                    Icon(
                        imageVector = when {
                            autoReconnectState.isReconnecting -> Icons.Default.Sync
                            connectionState == FlipperConnectionState.READY -> Icons.Default.CheckCircle
                            connectionState == FlipperConnectionState.ERROR -> Icons.Default.Error
                            connectionState in listOf(
                                FlipperConnectionState.CONNECTING,
                                FlipperConnectionState.CONNECTED,
                                FlipperConnectionState.DISCOVERING_SERVICES
                            ) -> Icons.Default.Sync
                            else -> Icons.Default.UsbOff
                        },
                        contentDescription = null,
                        tint = when {
                            autoReconnectState.isReconnecting -> MaterialTheme.colorScheme.tertiary.copy(alpha = pulseAlpha)
                            connectionState == FlipperConnectionState.READY -> MaterialTheme.colorScheme.primary
                            connectionState == FlipperConnectionState.ERROR -> MaterialTheme.colorScheme.error
                            connectionState in listOf(
                                FlipperConnectionState.CONNECTING,
                                FlipperConnectionState.CONNECTED,
                                FlipperConnectionState.DISCOVERING_SERVICES
                            ) -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Flipper Zero",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            autoReconnectState.isReconnecting ->
                                "Reconnecting... (${autoReconnectState.attemptNumber}/${autoReconnectState.maxAttempts})"
                            connectionState == FlipperConnectionState.READY ->
                                "Connected via ${connectionType.name}"
                            connectionState == FlipperConnectionState.CONNECTING -> "Connecting..."
                            connectionState == FlipperConnectionState.CONNECTED -> "Handshaking..."
                            connectionState == FlipperConnectionState.DISCOVERING_SERVICES -> "Discovering services..."
                            connectionState == FlipperConnectionState.LAUNCHING_FAP -> "Launching Flock Bridge app..."
                            connectionState == FlipperConnectionState.ERROR -> lastError ?: "Connection error"
                            connectionState == FlipperConnectionState.DISCONNECTED -> "Not connected"
                            else -> "Unknown state"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Signal strength indicator (only for Bluetooth when connected)
                if (connectionState == FlipperConnectionState.READY && connectionType == FlipperClient.ConnectionType.BLUETOOTH) {
                    SignalStrengthIndicator(rssi = connectionRssi)
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Connection type badge when connected
                if (connectionState == FlipperConnectionState.READY) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (connectionType == FlipperClient.ConnectionType.USB)
                                    Icons.Default.Usb else Icons.Default.Bluetooth,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = connectionType.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Connection control buttons
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    autoReconnectState.isReconnecting -> {
                        // Show cancel button during auto-reconnect
                        OutlinedButton(
                            onClick = onCancelAutoReconnect,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cancel")
                        }
                    }
                    connectionState == FlipperConnectionState.READY -> {
                        OutlinedButton(
                            onClick = onDisconnect,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.LinkOff,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Disconnect")
                        }
                    }
                    connectionState == FlipperConnectionState.DISCONNECTED ||
                    connectionState == FlipperConnectionState.ERROR -> {
                        Button(onClick = onConnect) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan for Devices")
                        }
                    }
                    else -> {
                        // Connecting states - show a loading indicator
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Signal strength indicator based on RSSI.
 */
@Composable
private fun SignalStrengthIndicator(rssi: Int?) {
    val signalLevel = when {
        rssi == null -> 0
        rssi >= -50 -> 4  // Excellent
        rssi >= -60 -> 3  // Good
        rssi >= -70 -> 2  // Fair
        rssi >= -80 -> 1  // Weak
        else -> 0         // Very weak
    }

    val barColor = when (signalLevel) {
        4 -> Color(0xFF4CAF50)  // Green
        3 -> Color(0xFF8BC34A)  // Light Green
        2 -> Color(0xFFFFC107)  // Amber
        1 -> Color(0xFFFF9800)  // Orange
        else -> Color(0xFFF44336)  // Red
    }

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (i in 1..4) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height((8 + i * 4).dp)
                    .background(
                        if (i <= signalLevel) barColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    }

    // Show RSSI value in tooltip or small text
    rssi?.let {
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "${it}dBm",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Bottom sheet dialog for scanning and selecting Flipper devices.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlipperDevicePickerBottomSheet(
    discoveredDevices: List<com.flockyou.scanner.flipper.DiscoveredFlipperDevice>,
    recentDevices: List<com.flockyou.scanner.flipper.RecentFlipperDevice>,
    isScanning: Boolean,
    onDismiss: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onSelectDiscovered: (com.flockyou.scanner.flipper.DiscoveredFlipperDevice) -> Unit,
    onSelectRecent: (com.flockyou.scanner.flipper.RecentFlipperDevice) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onConnectUsb: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Connect to Flipper",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Recent Devices Section
            if (recentDevices.isNotEmpty()) {
                Text(
                    text = "RECENT DEVICES",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                recentDevices.forEach { device ->
                    RecentDeviceItem(
                        device = device,
                        onSelect = { onSelectRecent(device) },
                        onRemove = { onRemoveRecent(device.address) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Bluetooth Scan Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NEARBY DEVICES",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isScanning) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = onStopScan) {
                            Text("Stop")
                        }
                    }
                } else {
                    TextButton(onClick = onStartScan) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Scan")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Discovered devices list
            if (discoveredDevices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.BluetoothSearching,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isScanning) "Scanning for Flipper devices..." else "No devices found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!isScanning) {
                            Text(
                                text = "Make sure your Flipper is powered on",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            } else {
                discoveredDevices.forEach { device ->
                    DiscoveredDeviceItem(
                        device = device,
                        onSelect = { onSelectDiscovered(device) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // USB connection option
            Divider()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "OTHER OPTIONS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                onClick = onConnectUsb,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "USB Connection",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Connect Flipper via USB cable",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * List item for a recently connected device.
 */
@Composable
private fun RecentDeviceItem(
    device: com.flockyou.scanner.flipper.RecentFlipperDevice,
    onSelect: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (device.connectionType == "BLUETOOTH")
                    Icons.Default.Bluetooth else Icons.Default.Usb,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

/**
 * List item for a discovered Bluetooth device.
 */
@Composable
private fun DiscoveredDeviceItem(
    device: com.flockyou.scanner.flipper.DiscoveredFlipperDevice,
    onSelect: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
            // Signal strength
            SignalStrengthIndicator(rssi = device.rssi)
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}
