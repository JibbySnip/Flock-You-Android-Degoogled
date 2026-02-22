package com.flockyou.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.flockyou.data.*
import com.flockyou.data.model.DetectionProtocol
import com.flockyou.privilege.PrivilegeMode
import com.flockyou.privilege.PrivilegeModeDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetectionSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DetectionSettingsRepository
) : ViewModel() {

    val privilegeMode: PrivilegeMode = PrivilegeModeDetector.detect(context)

    val settings: StateFlow<DetectionSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DetectionSettings())
    
    fun toggleCellularPattern(pattern: CellularPattern, enabled: Boolean) {
        viewModelScope.launch { repository.toggleCellularPattern(pattern, enabled) }
    }
    
    fun toggleSatellitePattern(pattern: SatellitePattern, enabled: Boolean) {
        viewModelScope.launch { repository.toggleSatellitePattern(pattern, enabled) }
    }
    
    fun toggleBlePattern(pattern: BlePattern, enabled: Boolean) {
        viewModelScope.launch { repository.toggleBlePattern(pattern, enabled) }
    }
    
    fun toggleWifiPattern(pattern: WifiPattern, enabled: Boolean) {
        viewModelScope.launch { repository.toggleWifiPattern(pattern, enabled) }
    }

    fun toggleGnssPattern(pattern: GnssPattern, enabled: Boolean) {
        viewModelScope.launch { repository.toggleGnssPattern(pattern, enabled) }
    }

    fun toggleRfPattern(pattern: RfPattern, enabled: Boolean) {
        viewModelScope.launch { repository.toggleRfPattern(pattern, enabled) }
    }

    fun toggleUltrasonicPattern(pattern: UltrasonicPattern, enabled: Boolean) {
        viewModelScope.launch { repository.toggleUltrasonicPattern(pattern, enabled) }
    }

    fun setGlobalEnabled(
        cellular: Boolean? = null,
        satellite: Boolean? = null,
        ble: Boolean? = null,
        wifi: Boolean? = null,
        gnss: Boolean? = null,
        rf: Boolean? = null,
        ultrasonic: Boolean? = null
    ) {
        viewModelScope.launch { repository.setGlobalDetectionEnabled(cellular, satellite, ble, wifi, gnss, rf, ultrasonic) }
    }
    
    fun updateCellularThresholds(thresholds: CellularThresholds) {
        viewModelScope.launch { repository.updateCellularThresholds(thresholds) }
    }

    fun updateSatelliteThresholds(thresholds: SatelliteThresholds) {
        viewModelScope.launch { repository.updateSatelliteThresholds(thresholds) }
    }

    fun updateBleThresholds(thresholds: BleThresholds) {
        viewModelScope.launch { repository.updateBleThresholds(thresholds) }
    }

    fun updateWifiThresholds(thresholds: WifiThresholds) {
        viewModelScope.launch { repository.updateWifiThresholds(thresholds) }
    }

    // Individual cellular threshold updates (prevents race conditions)
    fun updateCellularSignalSpikeThreshold(value: Int) {
        viewModelScope.launch { repository.updateCellularSignalSpikeThreshold(value) }
    }

    fun updateCellularRapidSwitchStationary(value: Int) {
        viewModelScope.launch { repository.updateCellularRapidSwitchStationary(value) }
    }

    fun updateCellularRapidSwitchMoving(value: Int) {
        viewModelScope.launch { repository.updateCellularRapidSwitchMoving(value) }
    }

    fun updateCellularTrustedThreshold(value: Int) {
        viewModelScope.launch { repository.updateCellularTrustedThreshold(value) }
    }

    fun updateCellularAnomalyInterval(value: Long) {
        viewModelScope.launch { repository.updateCellularAnomalyInterval(value) }
    }

    // Individual satellite threshold updates
    fun updateSatelliteUnexpectedThreshold(value: Long) {
        viewModelScope.launch { repository.updateSatelliteUnexpectedThreshold(value) }
    }

    fun updateSatelliteRapidHandoffThreshold(value: Long) {
        viewModelScope.launch { repository.updateSatelliteRapidHandoffThreshold(value) }
    }

    fun updateSatelliteMinTerrestrialSignal(value: Int) {
        viewModelScope.launch { repository.updateSatelliteMinTerrestrialSignal(value) }
    }

    fun updateSatelliteRapidSwitchWindow(value: Long) {
        viewModelScope.launch { repository.updateSatelliteRapidSwitchWindow(value) }
    }

    fun updateSatelliteRapidSwitchCount(value: Int) {
        viewModelScope.launch { repository.updateSatelliteRapidSwitchCount(value) }
    }

    // Individual BLE threshold updates
    fun updateBleMinRssi(value: Int) {
        viewModelScope.launch { repository.updateBleMinRssi(value) }
    }

    fun updateBleProximityRssi(value: Int) {
        viewModelScope.launch { repository.updateBleProximityRssi(value) }
    }

    fun updateBleTrackingDuration(value: Long) {
        viewModelScope.launch { repository.updateBleTrackingDuration(value) }
    }

    fun updateBleTrackingCount(value: Int) {
        viewModelScope.launch { repository.updateBleTrackingCount(value) }
    }

    // Individual WiFi threshold updates
    fun updateWifiMinSignal(value: Int) {
        viewModelScope.launch { repository.updateWifiMinSignal(value) }
    }

    fun updateWifiStrongSignal(value: Int) {
        viewModelScope.launch { repository.updateWifiStrongSignal(value) }
    }

    fun updateWifiTrackingDuration(value: Long) {
        viewModelScope.launch { repository.updateWifiTrackingDuration(value) }
    }

    fun updateWifiTrackingCount(value: Int) {
        viewModelScope.launch { repository.updateWifiTrackingCount(value) }
    }

    fun updateWifiMinTrackingDistance(value: Double) {
        viewModelScope.launch { repository.updateWifiMinTrackingDistance(value) }
    }

    // Individual GNSS threshold updates
    fun updateGnssCn0Deviation(value: Double) {
        viewModelScope.launch { repository.updateGnssCn0Deviation(value) }
    }

    fun updateGnssClockDrift(value: Double) {
        viewModelScope.launch { repository.updateGnssClockDrift(value) }
    }

    fun updateGnssMinSatellites(value: Int) {
        viewModelScope.launch { repository.updateGnssMinSatellites(value) }
    }

    fun updateGnssPositionJump(value: Double) {
        viewModelScope.launch { repository.updateGnssPositionJump(value) }
    }

    // Individual RF threshold updates
    fun updateRfHiddenNetworkThreshold(value: Int) {
        viewModelScope.launch { repository.updateRfHiddenNetworkThreshold(value) }
    }

    fun updateRfJammerDetectionThreshold(value: Int) {
        viewModelScope.launch { repository.updateRfJammerDetectionThreshold(value) }
    }

    fun updateRfSubGhzFrequencies(value: String) {
        viewModelScope.launch { repository.updateRfSubGhzFrequencies(value) }
    }

    // Individual Ultrasonic threshold updates
    fun updateUltrasonicMinAmplitude(value: Double) {
        viewModelScope.launch { repository.updateUltrasonicMinAmplitude(value) }
    }

    fun updateUltrasonicFreqStart(value: Int) {
        viewModelScope.launch { repository.updateUltrasonicFreqStart(value) }
    }

    fun updateUltrasonicFreqEnd(value: Int) {
        viewModelScope.launch { repository.updateUltrasonicFreqEnd(value) }
    }

    fun resetToDefaults() {
        viewModelScope.launch { repository.resetToDefaults() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectionSettingsScreen(
    viewModel: DetectionSettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val privilegeMode = viewModel.privilegeMode

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Cellular", "Satellite", "BLE", "WiFi", "GNSS", "RF", "Ultrasonic")

    var showResetDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Detection Patterns")
                        Text(
                            text = "Configure detection rules and thresholds",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Help")
                    }
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab row
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = when (index) {
                                        0 -> Icons.Default.CellTower
                                        1 -> Icons.Default.SatelliteAlt
                                        2 -> Icons.Default.Bluetooth
                                        3 -> Icons.Default.Wifi
                                        4 -> Icons.Default.GpsFixed
                                        5 -> Icons.Default.Radio
                                        else -> Icons.Default.Mic
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(title)
                            }
                        }
                    )
                }
            }
            
            // Tab content
            when (selectedTab) {
                0 -> CellularSettingsContent(
                    settings = settings,
                    privilegeMode = privilegeMode,
                    onTogglePattern = { pattern, enabled ->
                        viewModel.toggleCellularPattern(pattern, enabled)
                    },
                    onToggleGlobal = { enabled ->
                        viewModel.setGlobalEnabled(cellular = enabled)
                    },
                    onUpdateSignalSpike = { viewModel.updateCellularSignalSpikeThreshold(it) },
                    onUpdateRapidSwitchStationary = { viewModel.updateCellularRapidSwitchStationary(it) },
                    onUpdateRapidSwitchMoving = { viewModel.updateCellularRapidSwitchMoving(it) },
                    onUpdateTrustedThreshold = { viewModel.updateCellularTrustedThreshold(it) },
                    onUpdateAnomalyInterval = { viewModel.updateCellularAnomalyInterval(it) }
                )
                1 -> SatelliteSettingsContent(
                    settings = settings,
                    privilegeMode = privilegeMode,
                    onTogglePattern = { pattern, enabled ->
                        viewModel.toggleSatellitePattern(pattern, enabled)
                    },
                    onToggleGlobal = { enabled ->
                        viewModel.setGlobalEnabled(satellite = enabled)
                    },
                    onUpdateUnexpectedThreshold = { viewModel.updateSatelliteUnexpectedThreshold(it) },
                    onUpdateRapidHandoffThreshold = { viewModel.updateSatelliteRapidHandoffThreshold(it) },
                    onUpdateMinTerrestrialSignal = { viewModel.updateSatelliteMinTerrestrialSignal(it) },
                    onUpdateRapidSwitchWindow = { viewModel.updateSatelliteRapidSwitchWindow(it) },
                    onUpdateRapidSwitchCount = { viewModel.updateSatelliteRapidSwitchCount(it) }
                )
                2 -> BleSettingsContent(
                    settings = settings,
                    privilegeMode = privilegeMode,
                    onTogglePattern = { pattern, enabled ->
                        viewModel.toggleBlePattern(pattern, enabled)
                    },
                    onToggleGlobal = { enabled ->
                        viewModel.setGlobalEnabled(ble = enabled)
                    },
                    onUpdateMinRssi = { viewModel.updateBleMinRssi(it) },
                    onUpdateProximityRssi = { viewModel.updateBleProximityRssi(it) },
                    onUpdateTrackingDuration = { viewModel.updateBleTrackingDuration(it) },
                    onUpdateTrackingCount = { viewModel.updateBleTrackingCount(it) }
                )
                3 -> WifiSettingsContent(
                    settings = settings,
                    privilegeMode = privilegeMode,
                    onTogglePattern = { pattern, enabled ->
                        viewModel.toggleWifiPattern(pattern, enabled)
                    },
                    onToggleGlobal = { enabled ->
                        viewModel.setGlobalEnabled(wifi = enabled)
                    },
                    onUpdateMinSignal = { viewModel.updateWifiMinSignal(it) },
                    onUpdateStrongSignal = { viewModel.updateWifiStrongSignal(it) },
                    onUpdateTrackingDuration = { viewModel.updateWifiTrackingDuration(it) },
                    onUpdateTrackingCount = { viewModel.updateWifiTrackingCount(it) },
                    onUpdateMinTrackingDistance = { viewModel.updateWifiMinTrackingDistance(it) }
                )
                4 -> GnssSettingsContent(
                    settings = settings,
                    privilegeMode = privilegeMode,
                    onTogglePattern = { pattern, enabled ->
                        viewModel.toggleGnssPattern(pattern, enabled)
                    },
                    onToggleGlobal = { enabled ->
                        viewModel.setGlobalEnabled(gnss = enabled)
                    },
                    onUpdateCn0Deviation = { viewModel.updateGnssCn0Deviation(it) },
                    onUpdateClockDrift = { viewModel.updateGnssClockDrift(it) },
                    onUpdateMinSatellites = { viewModel.updateGnssMinSatellites(it) },
                    onUpdatePositionJump = { viewModel.updateGnssPositionJump(it) }
                )
                5 -> RfSettingsContent(
                    settings = settings,
                    privilegeMode = privilegeMode,
                    onTogglePattern = { pattern, enabled ->
                        viewModel.toggleRfPattern(pattern, enabled)
                    },
                    onToggleGlobal = { enabled ->
                        viewModel.setGlobalEnabled(rf = enabled)
                    },
                    onUpdateHiddenNetworkThreshold = { viewModel.updateRfHiddenNetworkThreshold(it) },
                    onUpdateJammerDetectionThreshold = { viewModel.updateRfJammerDetectionThreshold(it) },
                    onUpdateSubGhzFrequencies = { viewModel.updateRfSubGhzFrequencies(it) }
                )
                6 -> UltrasonicSettingsContent(
                    settings = settings,
                    privilegeMode = privilegeMode,
                    onTogglePattern = { pattern, enabled ->
                        viewModel.toggleUltrasonicPattern(pattern, enabled)
                    },
                    onToggleGlobal = { enabled ->
                        viewModel.setGlobalEnabled(ultrasonic = enabled)
                    },
                    onUpdateMinAmplitude = { viewModel.updateUltrasonicMinAmplitude(it) },
                    onUpdateFreqStart = { viewModel.updateUltrasonicFreqStart(it) },
                    onUpdateFreqEnd = { viewModel.updateUltrasonicFreqEnd(it) }
                )
            }
        }
    }
    
    // Reset confirmation dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
            title = { Text("Reset to Defaults?") },
            text = { Text("This will reset all detection patterns and thresholds to their default values.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetToDefaults()
                        showResetDialog = false
                    }
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Help dialog
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            title = { Text("Detection Tuning Help") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "These settings control which surveillance devices are detected and how sensitive the detection is.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    HelpSection(
                        title = "Pattern Toggles",
                        description = "Enable or disable specific detection categories. Disabling a pattern stops alerts for that type of device."
                    )

                    HelpSection(
                        title = "Threshold Sliders",
                        description = "Adjust detection sensitivity. Lower thresholds = more sensitive (more alerts, possible false positives). Higher thresholds = less sensitive (fewer alerts, may miss weak signals)."
                    )

                    HelpSection(
                        title = "RSSI Values",
                        description = "Signal strength in dBm. -30 = very strong (close), -80 = weak (far). Set min RSSI higher to ignore distant devices."
                    )

                    HelpSection(
                        title = "Tracking Alerts",
                        description = "Duration and count thresholds determine when a device is flagged as \"following\" you across locations."
                    )

                    HelpSection(
                        title = "GNSS/GPS",
                        description = "Detects GPS spoofing, jamming, and satellite anomalies. C/N0 measures signal quality; deviations indicate interference."
                    )

                    HelpSection(
                        title = "RF Analysis",
                        description = "Detects jammers, drones, and hidden transmitters. Requires Flipper Zero or system privileges for sub-GHz scanning."
                    )

                    HelpSection(
                        title = "Ultrasonic",
                        description = "Detects inaudible ultrasonic beacons used for ad tracking and cross-device linking. Privacy consent required."
                    )

                    Text(
                        text = "Tip: If you get too many false positives, increase thresholds. If you're missing detections, decrease them.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }
}

@Composable
private fun HelpSection(
    title: String,
    description: String
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ==================== Cellular Settings ====================

@Composable
private fun CellularSettingsContent(
    settings: DetectionSettings,
    privilegeMode: PrivilegeMode,
    onTogglePattern: (CellularPattern, Boolean) -> Unit,
    onToggleGlobal: (Boolean) -> Unit,
    onUpdateSignalSpike: (Int) -> Unit,
    onUpdateRapidSwitchStationary: (Int) -> Unit,
    onUpdateRapidSwitchMoving: (Int) -> Unit,
    onUpdateTrustedThreshold: (Int) -> Unit,
    onUpdateAnomalyInterval: (Long) -> Unit
) {
    var showThresholds by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Global toggle
        item {
            GlobalToggleCard(
                title = "Cellular Detection",
                description = "Monitor for IMSI catchers and cell anomalies",
                icon = Icons.Default.CellTower,
                enabled = settings.enableCellularDetection,
                onToggle = onToggleGlobal,
                enabledCount = settings.enabledCellularPatterns.size,
                totalCount = CellularPattern.values().size
            )
        }

        // Feasibility banner
        item {
            FeasibilityInfoBanner(
                feasibility = FeasibilityData.getProtocolFeasibility(DetectionProtocol.CELLULAR, privilegeMode)
            )
        }

        // Thresholds section
        item {
            ThresholdsSectionCard(
                title = "Cellular Thresholds",
                expanded = showThresholds,
                onToggle = { showThresholds = !showThresholds }
            ) {
                CellularThresholdsContent(
                    thresholds = settings.cellularThresholds,
                    onUpdateSignalSpike = onUpdateSignalSpike,
                    onUpdateRapidSwitchStationary = onUpdateRapidSwitchStationary,
                    onUpdateRapidSwitchMoving = onUpdateRapidSwitchMoving,
                    onUpdateTrustedThreshold = onUpdateTrustedThreshold,
                    onUpdateAnomalyInterval = onUpdateAnomalyInterval
                )
            }
        }

        // Pattern toggles
        item {
            Text(
                text = "Detection Patterns",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        items(CellularPattern.values().toList()) { pattern ->
            val feasibility = FeasibilityData.getPatternFeasibility(pattern, privilegeMode)
            PatternToggleCard(
                name = pattern.displayName,
                description = pattern.description,
                enabled = pattern in settings.enabledCellularPatterns,
                globalEnabled = settings.enableCellularDetection,
                onToggle = { onTogglePattern(pattern, it) },
                feasibilityNote = feasibility?.note,
                feasibilityLevel = feasibility?.level
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun CellularThresholdsContent(
    thresholds: CellularThresholds,
    onUpdateSignalSpike: (Int) -> Unit,
    onUpdateRapidSwitchStationary: (Int) -> Unit,
    onUpdateRapidSwitchMoving: (Int) -> Unit,
    onUpdateTrustedThreshold: (Int) -> Unit,
    onUpdateAnomalyInterval: (Long) -> Unit
) {
    var signalSpike by remember(thresholds.signalSpikeThreshold) { mutableStateOf(thresholds.signalSpikeThreshold.toFloat()) }
    var rapidSwitchStationary by remember(thresholds.rapidSwitchCountStationary) { mutableStateOf(thresholds.rapidSwitchCountStationary.toFloat()) }
    var rapidSwitchMoving by remember(thresholds.rapidSwitchCountMoving) { mutableStateOf(thresholds.rapidSwitchCountMoving.toFloat()) }
    var trustedThreshold by remember(thresholds.trustedCellThreshold) { mutableStateOf(thresholds.trustedCellThreshold.toFloat()) }
    var anomalyInterval by remember(thresholds.minAnomalyIntervalMs) { mutableStateOf(thresholds.minAnomalyIntervalMs.toFloat() / 1000f) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ThresholdSlider(
            label = "Signal Spike Threshold",
            value = signalSpike,
            valueRange = 10f..50f,
            unit = "dBm",
            description = "Minimum signal change to trigger spike alert",
            onValueChange = { signalSpike = it },
            onValueChangeFinished = {
                onUpdateSignalSpike(signalSpike.toInt())
            }
        )

        ThresholdSlider(
            label = "Rapid Switch (Stationary)",
            value = rapidSwitchStationary,
            valueRange = 1f..10f,
            unit = "/min",
            steps = 8,
            description = "Max cell switches per minute while stationary",
            onValueChange = { rapidSwitchStationary = it },
            onValueChangeFinished = {
                onUpdateRapidSwitchStationary(rapidSwitchStationary.toInt())
            }
        )

        ThresholdSlider(
            label = "Rapid Switch (Moving)",
            value = rapidSwitchMoving,
            valueRange = 3f..20f,
            unit = "/min",
            description = "Max cell switches per minute while moving",
            onValueChange = { rapidSwitchMoving = it },
            onValueChangeFinished = {
                onUpdateRapidSwitchMoving(rapidSwitchMoving.toInt())
            }
        )

        ThresholdSlider(
            label = "Trusted Cell Threshold",
            value = trustedThreshold,
            valueRange = 2f..20f,
            unit = "times",
            steps = 17,
            description = "Times seen before cell tower is trusted",
            onValueChange = { trustedThreshold = it },
            onValueChangeFinished = {
                onUpdateTrustedThreshold(trustedThreshold.toInt())
            }
        )

        ThresholdSlider(
            label = "Anomaly Cooldown",
            value = anomalyInterval,
            valueRange = 10f..300f,
            unit = "sec",
            description = "Minimum time between same anomaly alerts",
            onValueChange = { anomalyInterval = it },
            onValueChangeFinished = {
                onUpdateAnomalyInterval((anomalyInterval * 1000).toLong())
            }
        )
    }
}

// ==================== Satellite Settings ====================

@Composable
private fun SatelliteSettingsContent(
    settings: DetectionSettings,
    privilegeMode: PrivilegeMode,
    onTogglePattern: (SatellitePattern, Boolean) -> Unit,
    onToggleGlobal: (Boolean) -> Unit,
    onUpdateUnexpectedThreshold: (Long) -> Unit,
    onUpdateRapidHandoffThreshold: (Long) -> Unit,
    onUpdateMinTerrestrialSignal: (Int) -> Unit,
    onUpdateRapidSwitchWindow: (Long) -> Unit,
    onUpdateRapidSwitchCount: (Int) -> Unit
) {
    var showThresholds by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Global toggle
        item {
            GlobalToggleCard(
                title = "Satellite Detection",
                description = "Monitor for satellite connection anomalies",
                icon = Icons.Default.SatelliteAlt,
                enabled = settings.enableSatelliteDetection,
                onToggle = onToggleGlobal,
                enabledCount = settings.enabledSatellitePatterns.size,
                totalCount = SatellitePattern.values().size
            )
        }

        // Feasibility banner
        item {
            FeasibilityInfoBanner(
                feasibility = FeasibilityData.getProtocolFeasibility(DetectionProtocol.SATELLITE, privilegeMode)
            )
        }

        // Thresholds section
        item {
            ThresholdsSectionCard(
                title = "Satellite Thresholds",
                expanded = showThresholds,
                onToggle = { showThresholds = !showThresholds }
            ) {
                SatelliteThresholdsContent(
                    thresholds = settings.satelliteThresholds,
                    onUpdateUnexpectedThreshold = onUpdateUnexpectedThreshold,
                    onUpdateRapidHandoffThreshold = onUpdateRapidHandoffThreshold,
                    onUpdateMinTerrestrialSignal = onUpdateMinTerrestrialSignal,
                    onUpdateRapidSwitchWindow = onUpdateRapidSwitchWindow,
                    onUpdateRapidSwitchCount = onUpdateRapidSwitchCount
                )
            }
        }

        // Pattern toggles
        item {
            Text(
                text = "Detection Patterns",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        items(SatellitePattern.values().toList()) { pattern ->
            PatternToggleCard(
                name = pattern.displayName,
                description = pattern.description,
                enabled = pattern in settings.enabledSatellitePatterns,
                globalEnabled = settings.enableSatelliteDetection,
                onToggle = { onTogglePattern(pattern, it) }
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun SatelliteThresholdsContent(
    thresholds: SatelliteThresholds,
    onUpdateUnexpectedThreshold: (Long) -> Unit,
    onUpdateRapidHandoffThreshold: (Long) -> Unit,
    onUpdateMinTerrestrialSignal: (Int) -> Unit,
    onUpdateRapidSwitchWindow: (Long) -> Unit,
    onUpdateRapidSwitchCount: (Int) -> Unit
) {
    var unexpectedThreshold by remember(thresholds.unexpectedSatelliteThresholdMs) { mutableStateOf(thresholds.unexpectedSatelliteThresholdMs.toFloat() / 1000f) }
    var rapidHandoff by remember(thresholds.rapidHandoffThresholdMs) { mutableStateOf(thresholds.rapidHandoffThresholdMs.toFloat() / 1000f) }
    var minTerrestrialSignal by remember(thresholds.minSignalForTerrestrial) { mutableStateOf(thresholds.minSignalForTerrestrial.toFloat()) }
    var rapidSwitchWindow by remember(thresholds.rapidSwitchingWindowMs) { mutableStateOf(thresholds.rapidSwitchingWindowMs.toFloat() / 1000f) }
    var rapidSwitchCount by remember(thresholds.rapidSwitchingCount) { mutableStateOf(thresholds.rapidSwitchingCount.toFloat()) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ThresholdSlider(
            label = "Unexpected Satellite Threshold",
            value = unexpectedThreshold,
            valueRange = 1f..30f,
            unit = "sec",
            description = "Time window to detect unexpected satellite",
            onValueChange = { unexpectedThreshold = it },
            onValueChangeFinished = {
                onUpdateUnexpectedThreshold((unexpectedThreshold * 1000).toLong())
            }
        )

        ThresholdSlider(
            label = "Rapid Handoff Threshold",
            value = rapidHandoff,
            valueRange = 0.5f..10f,
            unit = "sec",
            description = "Time for rapid handoff detection",
            onValueChange = { rapidHandoff = it },
            onValueChangeFinished = {
                onUpdateRapidHandoffThreshold((rapidHandoff * 1000).toLong())
            }
        )

        ThresholdSlider(
            label = "Min Terrestrial Signal",
            value = minTerrestrialSignal,
            valueRange = -120f..-70f,
            unit = "dBm",
            description = "Minimum signal for good terrestrial coverage",
            onValueChange = { minTerrestrialSignal = it },
            onValueChangeFinished = {
                onUpdateMinTerrestrialSignal(minTerrestrialSignal.toInt())
            }
        )

        ThresholdSlider(
            label = "Rapid Switching Window",
            value = rapidSwitchWindow,
            valueRange = 30f..300f,
            unit = "sec",
            description = "Time window for rapid switching detection",
            onValueChange = { rapidSwitchWindow = it },
            onValueChangeFinished = {
                onUpdateRapidSwitchWindow((rapidSwitchWindow * 1000).toLong())
            }
        )

        ThresholdSlider(
            label = "Rapid Switching Count",
            value = rapidSwitchCount,
            valueRange = 2f..10f,
            unit = "times",
            steps = 7,
            description = "Switches in window to trigger alert",
            onValueChange = { rapidSwitchCount = it },
            onValueChangeFinished = {
                onUpdateRapidSwitchCount(rapidSwitchCount.toInt())
            }
        )
    }
}

// ==================== BLE Settings ====================

@Composable
private fun BleSettingsContent(
    settings: DetectionSettings,
    privilegeMode: PrivilegeMode,
    onTogglePattern: (BlePattern, Boolean) -> Unit,
    onToggleGlobal: (Boolean) -> Unit,
    onUpdateMinRssi: (Int) -> Unit,
    onUpdateProximityRssi: (Int) -> Unit,
    onUpdateTrackingDuration: (Long) -> Unit,
    onUpdateTrackingCount: (Int) -> Unit
) {
    var showThresholds by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Global toggle
        item {
            GlobalToggleCard(
                title = "BLE Detection",
                description = "Scan for surveillance Bluetooth devices",
                icon = Icons.Default.Bluetooth,
                enabled = settings.enableBleDetection,
                onToggle = onToggleGlobal,
                enabledCount = settings.enabledBlePatterns.size,
                totalCount = BlePattern.values().size
            )
        }

        // Feasibility banner
        item {
            FeasibilityInfoBanner(
                feasibility = FeasibilityData.getProtocolFeasibility(DetectionProtocol.BLUETOOTH_LE, privilegeMode)
            )
        }

        // Thresholds section
        item {
            ThresholdsSectionCard(
                title = "BLE Thresholds",
                expanded = showThresholds,
                onToggle = { showThresholds = !showThresholds }
            ) {
                BleThresholdsContent(
                    thresholds = settings.bleThresholds,
                    onUpdateMinRssi = onUpdateMinRssi,
                    onUpdateProximityRssi = onUpdateProximityRssi,
                    onUpdateTrackingDuration = onUpdateTrackingDuration,
                    onUpdateTrackingCount = onUpdateTrackingCount
                )
            }
        }

        // Pattern toggles
        item {
            Text(
                text = "Detection Patterns",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        items(BlePattern.values().toList()) { pattern ->
            PatternToggleCard(
                name = pattern.displayName,
                description = pattern.description,
                enabled = pattern in settings.enabledBlePatterns,
                globalEnabled = settings.enableBleDetection,
                onToggle = { onTogglePattern(pattern, it) }
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun BleThresholdsContent(
    thresholds: BleThresholds,
    onUpdateMinRssi: (Int) -> Unit,
    onUpdateProximityRssi: (Int) -> Unit,
    onUpdateTrackingDuration: (Long) -> Unit,
    onUpdateTrackingCount: (Int) -> Unit
) {
    var minRssi by remember(thresholds.minRssiForAlert) { mutableStateOf(thresholds.minRssiForAlert.toFloat()) }
    var proximityRssi by remember(thresholds.proximityAlertRssi) { mutableStateOf(thresholds.proximityAlertRssi.toFloat()) }
    var trackingDuration by remember(thresholds.trackingDurationMs) { mutableStateOf(thresholds.trackingDurationMs.toFloat() / 60000f) }
    var trackingCount by remember(thresholds.minSeenCountForTracking) { mutableStateOf(thresholds.minSeenCountForTracking.toFloat()) }
    var showRssiHelp by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // RSSI Help Card (expandable)
        RssiHelpCard(
            expanded = showRssiHelp,
            onToggle = { showRssiHelp = !showRssiHelp }
        )

        // Preset buttons
        RssiPresetButtons(
            currentMinRssi = minRssi.toInt(),
            currentProximityRssi = proximityRssi.toInt(),
            onPresetSelected = { preset ->
                when (preset) {
                    RssiPreset.SENSITIVE -> {
                        minRssi = -90f
                        proximityRssi = -60f
                        onUpdateMinRssi(-90)
                        onUpdateProximityRssi(-60)
                    }
                    RssiPreset.BALANCED -> {
                        minRssi = -75f
                        proximityRssi = -50f
                        onUpdateMinRssi(-75)
                        onUpdateProximityRssi(-50)
                    }
                    RssiPreset.CONSERVATIVE -> {
                        minRssi = -60f
                        proximityRssi = -40f
                        onUpdateMinRssi(-60)
                        onUpdateProximityRssi(-40)
                    }
                }
            }
        )

        Divider()

        ThresholdSliderWithRssiContext(
            label = "Minimum RSSI for Alert",
            value = minRssi,
            valueRange = -100f..-50f,
            unit = "dBm",
            description = "Minimum signal strength to trigger alert",
            onValueChange = { minRssi = it },
            onValueChangeFinished = {
                onUpdateMinRssi(minRssi.toInt())
            }
        )

        ThresholdSliderWithRssiContext(
            label = "Proximity Alert RSSI",
            value = proximityRssi,
            valueRange = -70f..-30f,
            unit = "dBm",
            description = "Signal strength for close proximity warning",
            onValueChange = { proximityRssi = it },
            onValueChangeFinished = {
                onUpdateProximityRssi(proximityRssi.toInt())
            }
        )

        ThresholdSlider(
            label = "Tracking Duration",
            value = trackingDuration,
            valueRange = 1f..30f,
            unit = "min",
            description = "Time before tracking alert is triggered",
            onValueChange = { trackingDuration = it },
            onValueChangeFinished = {
                onUpdateTrackingDuration((trackingDuration * 60000).toLong())
            }
        )

        ThresholdSlider(
            label = "Tracking Sighting Count",
            value = trackingCount,
            valueRange = 2f..10f,
            unit = "times",
            steps = 7,
            description = "Minimum sightings for tracking alert",
            onValueChange = { trackingCount = it },
            onValueChangeFinished = {
                onUpdateTrackingCount(trackingCount.toInt())
            }
        )
    }
}

// RSSI Preset enum
private enum class RssiPreset {
    SENSITIVE, BALANCED, CONSERVATIVE
}

// RSSI Help Card component
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RssiHelpCard(
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        onClick = onToggle
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "What is RSSI?",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        text = "RSSI (Received Signal Strength Indicator) measures signal power in dBm (decibels-milliwatts).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Real-world meaning:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    RssiExampleRow("-30 dBm", "Very close (< 1 meter)", MaterialTheme.colorScheme.error)
                    RssiExampleRow("-50 dBm", "Same room (1-5 meters)", MaterialTheme.colorScheme.tertiary)
                    RssiExampleRow("-70 dBm", "Nearby (5-15 meters)", MaterialTheme.colorScheme.primary)
                    RssiExampleRow("-80 dBm", "Far away (15-30 meters)", MaterialTheme.colorScheme.onSurfaceVariant)
                    RssiExampleRow("-90 dBm", "Barely detectable", MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Lower values (more negative) = weaker signal = farther away",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun RssiExampleRow(
    rssi: String,
    meaning: String,
    color: Color
) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = color.copy(alpha = 0.2f),
            modifier = Modifier.width(70.dp)
        ) {
            Text(
                text = rssi,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = meaning,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// Preset buttons component
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RssiPresetButtons(
    currentMinRssi: Int,
    currentProximityRssi: Int,
    onPresetSelected: (RssiPreset) -> Unit
) {
    val selectedPreset = when {
        currentMinRssi <= -85 && currentProximityRssi <= -55 -> RssiPreset.SENSITIVE
        currentMinRssi in -80..-65 && currentProximityRssi in -55..-45 -> RssiPreset.BALANCED
        currentMinRssi >= -65 && currentProximityRssi >= -45 -> RssiPreset.CONSERVATIVE
        else -> null
    }

    Column {
        Text(
            text = "Quick Presets",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedPreset == RssiPreset.SENSITIVE,
                onClick = { onPresetSelected(RssiPreset.SENSITIVE) },
                label = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Sensitive", style = MaterialTheme.typography.labelMedium)
                        Text("More alerts", style = MaterialTheme.typography.labelSmall)
                    }
                },
                leadingIcon = if (selectedPreset == RssiPreset.SENSITIVE) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectedPreset == RssiPreset.BALANCED,
                onClick = { onPresetSelected(RssiPreset.BALANCED) },
                label = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Balanced", style = MaterialTheme.typography.labelMedium)
                        Text("Recommended", style = MaterialTheme.typography.labelSmall)
                    }
                },
                leadingIcon = if (selectedPreset == RssiPreset.BALANCED) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectedPreset == RssiPreset.CONSERVATIVE,
                onClick = { onPresetSelected(RssiPreset.CONSERVATIVE) },
                label = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Conservative", style = MaterialTheme.typography.labelMedium)
                        Text("Fewer alerts", style = MaterialTheme.typography.labelSmall)
                    }
                },
                leadingIcon = if (selectedPreset == RssiPreset.CONSERVATIVE) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// Threshold slider with RSSI context visualization
@Composable
private fun ThresholdSliderWithRssiContext(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    unit: String,
    description: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    steps: Int = 0
) {
    val rssiMeaning = getRssiMeaning(value.toInt())

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (value == value.toInt().toFloat()) value.toInt() else "%.1f".format(value)} $unit",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = rssiMeaning,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
        Text(
            text = description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
        // Visual distance indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Far away",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Very close",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun getRssiMeaning(rssi: Int): String {
    return when {
        rssi >= -35 -> "touching"
        rssi >= -50 -> "very close"
        rssi >= -60 -> "same room"
        rssi >= -70 -> "nearby"
        rssi >= -80 -> "moderate distance"
        rssi >= -90 -> "far away"
        else -> "barely detectable"
    }
}

// ==================== WiFi Settings ====================

@Composable
private fun WifiSettingsContent(
    settings: DetectionSettings,
    privilegeMode: PrivilegeMode,
    onTogglePattern: (WifiPattern, Boolean) -> Unit,
    onToggleGlobal: (Boolean) -> Unit,
    onUpdateMinSignal: (Int) -> Unit,
    onUpdateStrongSignal: (Int) -> Unit,
    onUpdateTrackingDuration: (Long) -> Unit,
    onUpdateTrackingCount: (Int) -> Unit,
    onUpdateMinTrackingDistance: (Double) -> Unit
) {
    var showThresholds by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Global toggle
        item {
            GlobalToggleCard(
                title = "WiFi Detection",
                description = "Scan for surveillance WiFi networks",
                icon = Icons.Default.Wifi,
                enabled = settings.enableWifiDetection,
                onToggle = onToggleGlobal,
                enabledCount = settings.enabledWifiPatterns.size,
                totalCount = WifiPattern.values().size
            )
        }

        // Feasibility banner
        item {
            FeasibilityInfoBanner(
                feasibility = FeasibilityData.getProtocolFeasibility(DetectionProtocol.WIFI, privilegeMode)
            )
        }

        // Thresholds section
        item {
            ThresholdsSectionCard(
                title = "WiFi Thresholds",
                expanded = showThresholds,
                onToggle = { showThresholds = !showThresholds }
            ) {
                WifiThresholdsContent(
                    thresholds = settings.wifiThresholds,
                    onUpdateMinSignal = onUpdateMinSignal,
                    onUpdateStrongSignal = onUpdateStrongSignal,
                    onUpdateTrackingDuration = onUpdateTrackingDuration,
                    onUpdateTrackingCount = onUpdateTrackingCount,
                    onUpdateMinTrackingDistance = onUpdateMinTrackingDistance
                )
            }
        }

        // Pattern toggles
        item {
            Text(
                text = "Detection Patterns",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        items(WifiPattern.values().toList()) { pattern ->
            val feasibility = FeasibilityData.getPatternFeasibility(pattern, privilegeMode)
            PatternToggleCard(
                name = pattern.displayName,
                description = pattern.description,
                enabled = pattern in settings.enabledWifiPatterns,
                globalEnabled = settings.enableWifiDetection,
                onToggle = { onTogglePattern(pattern, it) },
                feasibilityNote = feasibility?.note,
                feasibilityLevel = feasibility?.level
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun WifiThresholdsContent(
    thresholds: WifiThresholds,
    onUpdateMinSignal: (Int) -> Unit,
    onUpdateStrongSignal: (Int) -> Unit,
    onUpdateTrackingDuration: (Long) -> Unit,
    onUpdateTrackingCount: (Int) -> Unit,
    onUpdateMinTrackingDistance: (Double) -> Unit
) {
    var minSignal by remember(thresholds.minSignalForAlert) { mutableStateOf(thresholds.minSignalForAlert.toFloat()) }
    var strongSignal by remember(thresholds.strongSignalThreshold) { mutableStateOf(thresholds.strongSignalThreshold.toFloat()) }
    var trackingDuration by remember(thresholds.trackingDurationMs) { mutableStateOf(thresholds.trackingDurationMs.toFloat() / 60000f) }
    var trackingCount by remember(thresholds.minSeenCountForTracking) { mutableStateOf(thresholds.minSeenCountForTracking.toFloat()) }
    var minTrackingDistance by remember(thresholds.minTrackingDistanceMeters) { mutableStateOf((thresholds.minTrackingDistanceMeters / 1609.0).toFloat()) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ThresholdSlider(
            label = "Minimum Signal for Alert",
            value = minSignal,
            valueRange = -90f..-50f,
            unit = "dBm",
            description = "Minimum signal level to trigger alert",
            onValueChange = { minSignal = it },
            onValueChangeFinished = {
                onUpdateMinSignal(minSignal.toInt())
            }
        )

        ThresholdSlider(
            label = "Strong Signal Threshold",
            value = strongSignal,
            valueRange = -70f..-30f,
            unit = "dBm",
            description = "Signal level for strong signal alert",
            onValueChange = { strongSignal = it },
            onValueChangeFinished = {
                onUpdateStrongSignal(strongSignal.toInt())
            }
        )

        ThresholdSlider(
            label = "Tracking Duration",
            value = trackingDuration,
            valueRange = 1f..30f,
            unit = "min",
            description = "Time before tracking alert is triggered",
            onValueChange = { trackingDuration = it },
            onValueChangeFinished = {
                onUpdateTrackingDuration((trackingDuration * 60000).toLong())
            }
        )

        ThresholdSlider(
            label = "Tracking Sighting Count",
            value = trackingCount,
            valueRange = 2f..10f,
            unit = "times",
            steps = 7,
            description = "Minimum sightings for tracking alert",
            onValueChange = { trackingCount = it },
            onValueChangeFinished = {
                onUpdateTrackingCount(trackingCount.toInt())
            }
        )

        ThresholdSlider(
            label = "Min Tracking Distance",
            value = minTrackingDistance,
            valueRange = 0.1f..5f,
            unit = "mi",
            description = "Minimum distance traveled for tracking alert",
            onValueChange = { minTrackingDistance = it },
            onValueChangeFinished = {
                onUpdateMinTrackingDistance(minTrackingDistance.toDouble() * 1609.0)
            }
        )
    }
}

// ==================== Common Components ====================

@Composable
private fun GlobalToggleCard(
    title: String,
    description: String,
    icon: ImageVector,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    enabledCount: Int,
    totalCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$enabledCount of $totalCount patterns enabled",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThresholdsSectionCard(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (expanded) "Tap to collapse" else "Tap to expand",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }
            
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(16.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun FeasibilityInfoBanner(
    feasibility: ProtocolFeasibility
) {
    val (containerColor, contentColor, iconTint) = when (feasibility.level) {
        FeasibilityLevel.FULL -> Triple(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            MaterialTheme.colorScheme.onPrimaryContainer,
            MaterialTheme.colorScheme.primary
        )
        FeasibilityLevel.DEGRADED -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
            MaterialTheme.colorScheme.onTertiaryContainer,
            MaterialTheme.colorScheme.tertiary
        )
        FeasibilityLevel.HEURISTIC_ONLY -> Triple(
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
            MaterialTheme.colorScheme.onSecondaryContainer,
            MaterialTheme.colorScheme.secondary
        )
        FeasibilityLevel.NOT_FEASIBLE -> Triple(
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
            MaterialTheme.colorScheme.onErrorContainer,
            MaterialTheme.colorScheme.error
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = when (feasibility.level) {
                    FeasibilityLevel.FULL -> Icons.Default.CheckCircle
                    FeasibilityLevel.DEGRADED -> Icons.Default.Info
                    FeasibilityLevel.HEURISTIC_ONLY -> Icons.Default.Warning
                    FeasibilityLevel.NOT_FEASIBLE -> Icons.Default.Error
                },
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "${feasibility.level.displayName}: ${feasibility.summary}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = contentColor
                )
                feasibility.upgradeNote?.let { note ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = note,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PatternToggleCard(
    name: String,
    description: String,
    enabled: Boolean,
    globalEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    feasibilityNote: String? = null,
    feasibilityLevel: FeasibilityLevel? = null
) {
    val isNotFeasible = feasibilityLevel == FeasibilityLevel.NOT_FEASIBLE
    val effectiveEnabled = globalEnabled && !isNotFeasible

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isNotFeasible -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                !globalEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                enabled -> MaterialTheme.colorScheme.surface
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (effectiveEnabled) MaterialTheme.colorScheme.onSurface
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isNotFeasible) 0.5f else 1f)
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isNotFeasible) 0.5f else 1f)
                )
                if (feasibilityNote != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = feasibilityNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (feasibilityLevel) {
                            FeasibilityLevel.NOT_FEASIBLE -> MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            FeasibilityLevel.HEURISTIC_ONLY -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        }
                    )
                }
            }
            Switch(
                checked = enabled && effectiveEnabled,
                onCheckedChange = { onToggle(it) },
                enabled = effectiveEnabled
            )
        }
    }
}

// ==================== GNSS Settings ====================

@Composable
private fun GnssSettingsContent(
    settings: DetectionSettings,
    privilegeMode: PrivilegeMode,
    onTogglePattern: (GnssPattern, Boolean) -> Unit,
    onToggleGlobal: (Boolean) -> Unit,
    onUpdateCn0Deviation: (Double) -> Unit,
    onUpdateClockDrift: (Double) -> Unit,
    onUpdateMinSatellites: (Int) -> Unit,
    onUpdatePositionJump: (Double) -> Unit
) {
    var showThresholds by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GlobalToggleCard(
                title = "GNSS/GPS Detection",
                description = "Monitor for GPS spoofing and jamming",
                icon = Icons.Default.GpsFixed,
                enabled = settings.enableGnssDetection,
                onToggle = onToggleGlobal,
                enabledCount = settings.enabledGnssPatterns.size,
                totalCount = GnssPattern.values().size
            )
        }

        // Feasibility banner
        item {
            FeasibilityInfoBanner(
                feasibility = FeasibilityData.getProtocolFeasibility(DetectionProtocol.GNSS, privilegeMode)
            )
        }

        item {
            ThresholdsSectionCard(
                title = "GNSS Thresholds",
                expanded = showThresholds,
                onToggle = { showThresholds = !showThresholds }
            ) {
                GnssThresholdsContent(
                    thresholds = settings.gnssThresholds,
                    onUpdateCn0Deviation = onUpdateCn0Deviation,
                    onUpdateClockDrift = onUpdateClockDrift,
                    onUpdateMinSatellites = onUpdateMinSatellites,
                    onUpdatePositionJump = onUpdatePositionJump
                )
            }
        }

        item {
            Text(
                text = "Detection Patterns",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        items(GnssPattern.values().toList()) { pattern ->
            val feasibility = FeasibilityData.getPatternFeasibility(pattern, privilegeMode)
            PatternToggleCard(
                name = pattern.displayName,
                description = pattern.description,
                enabled = pattern in settings.enabledGnssPatterns,
                globalEnabled = settings.enableGnssDetection,
                onToggle = { onTogglePattern(pattern, it) },
                feasibilityNote = feasibility?.note,
                feasibilityLevel = feasibility?.level
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun GnssThresholdsContent(
    thresholds: GnssThresholds,
    onUpdateCn0Deviation: (Double) -> Unit,
    onUpdateClockDrift: (Double) -> Unit,
    onUpdateMinSatellites: (Int) -> Unit,
    onUpdatePositionJump: (Double) -> Unit
) {
    var cn0Deviation by remember(thresholds.cn0DeviationThreshold) { mutableStateOf(thresholds.cn0DeviationThreshold.toFloat()) }
    var clockDrift by remember(thresholds.clockDriftThreshold) { mutableStateOf(thresholds.clockDriftThreshold.toFloat()) }
    var minSatellites by remember(thresholds.minSatellites) { mutableStateOf(thresholds.minSatellites.toFloat()) }
    var positionJump by remember(thresholds.positionJumpThreshold) { mutableStateOf(thresholds.positionJumpThreshold.toFloat()) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ThresholdSlider(
            label = "C/N0 Deviation Threshold",
            value = cn0Deviation,
            valueRange = 5f..20f,
            unit = "dB-Hz",
            description = "Signal quality deviation to trigger spoofing alert",
            onValueChange = { cn0Deviation = it },
            onValueChangeFinished = {
                onUpdateCn0Deviation(cn0Deviation.toDouble())
            }
        )

        ThresholdSlider(
            label = "Clock Drift Threshold",
            value = clockDrift,
            valueRange = 50f..500f,
            unit = "ns",
            description = "Clock drift threshold for anomaly detection",
            onValueChange = { clockDrift = it },
            onValueChangeFinished = {
                onUpdateClockDrift(clockDrift.toDouble())
            }
        )

        ThresholdSlider(
            label = "Minimum Satellites",
            value = minSatellites,
            valueRange = 3f..8f,
            unit = "sats",
            steps = 4,
            description = "Minimum satellites required for valid fix",
            onValueChange = { minSatellites = it },
            onValueChangeFinished = {
                onUpdateMinSatellites(minSatellites.toInt())
            }
        )

        ThresholdSlider(
            label = "Position Jump Threshold",
            value = positionJump,
            valueRange = 50f..500f,
            unit = "m",
            description = "Distance for impossible position jump detection",
            onValueChange = { positionJump = it },
            onValueChangeFinished = {
                onUpdatePositionJump(positionJump.toDouble())
            }
        )
    }
}

// ==================== RF Settings ====================

@Composable
private fun RfSettingsContent(
    settings: DetectionSettings,
    privilegeMode: PrivilegeMode,
    onTogglePattern: (RfPattern, Boolean) -> Unit,
    onToggleGlobal: (Boolean) -> Unit,
    onUpdateHiddenNetworkThreshold: (Int) -> Unit,
    onUpdateJammerDetectionThreshold: (Int) -> Unit,
    onUpdateSubGhzFrequencies: (String) -> Unit
) {
    var showThresholds by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GlobalToggleCard(
                title = "RF Analysis Detection",
                description = "Monitor for RF jammers, drones, and surveillance",
                icon = Icons.Default.Radio,
                enabled = settings.enableRfDetection,
                onToggle = onToggleGlobal,
                enabledCount = settings.enabledRfPatterns.size,
                totalCount = RfPattern.values().size
            )
        }

        // Feasibility banner (replaces hardcoded RF info banner)
        item {
            FeasibilityInfoBanner(
                feasibility = FeasibilityData.getProtocolFeasibility(DetectionProtocol.RF, privilegeMode)
            )
        }

        item {
            ThresholdsSectionCard(
                title = "RF Thresholds",
                expanded = showThresholds,
                onToggle = { showThresholds = !showThresholds }
            ) {
                RfThresholdsContent(
                    thresholds = settings.rfThresholds,
                    onUpdateHiddenNetworkThreshold = onUpdateHiddenNetworkThreshold,
                    onUpdateJammerDetectionThreshold = onUpdateJammerDetectionThreshold,
                    onUpdateSubGhzFrequencies = onUpdateSubGhzFrequencies
                )
            }
        }

        item {
            Text(
                text = "Detection Patterns",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        items(RfPattern.values().toList()) { pattern ->
            PatternToggleCard(
                name = pattern.displayName,
                description = pattern.description,
                enabled = pattern in settings.enabledRfPatterns,
                globalEnabled = settings.enableRfDetection,
                onToggle = { onTogglePattern(pattern, it) }
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun RfThresholdsContent(
    thresholds: RfThresholds,
    onUpdateHiddenNetworkThreshold: (Int) -> Unit,
    onUpdateJammerDetectionThreshold: (Int) -> Unit,
    onUpdateSubGhzFrequencies: (String) -> Unit
) {
    var hiddenNetwork by remember(thresholds.hiddenNetworkThreshold) { mutableStateOf(thresholds.hiddenNetworkThreshold.toFloat()) }
    var jammerDetection by remember(thresholds.jammerDetectionThreshold) { mutableStateOf(thresholds.jammerDetectionThreshold.toFloat()) }
    var subGhzFreqs by remember(thresholds.subGhzFrequencies) { mutableStateOf(thresholds.subGhzFrequencies) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ThresholdSlider(
            label = "Hidden Network Threshold",
            value = hiddenNetwork,
            valueRange = 2f..15f,
            unit = "networks",
            steps = 12,
            description = "Number of hidden networks to trigger alert",
            onValueChange = { hiddenNetwork = it },
            onValueChangeFinished = {
                onUpdateHiddenNetworkThreshold(hiddenNetwork.toInt())
            }
        )

        ThresholdSlider(
            label = "Jammer Detection Threshold",
            value = jammerDetection,
            valueRange = 10f..60f,
            unit = "dB",
            description = "Signal drop threshold for jammer detection",
            onValueChange = { jammerDetection = it },
            onValueChangeFinished = {
                onUpdateJammerDetectionThreshold(jammerDetection.toInt())
            }
        )

        // Sub-GHz frequencies text field
        Column {
            Text(
                text = "Sub-GHz Frequencies",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Comma-separated MHz values to monitor",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = subGhzFreqs,
                onValueChange = { subGhzFreqs = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace
                ),
                singleLine = true,
                label = { Text("Frequencies (MHz)") }
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = { onUpdateSubGhzFrequencies(subGhzFreqs) }
            ) {
                Text("Apply")
            }
        }
    }
}

// ==================== Ultrasonic Settings ====================

@Composable
private fun UltrasonicSettingsContent(
    settings: DetectionSettings,
    privilegeMode: PrivilegeMode,
    onTogglePattern: (UltrasonicPattern, Boolean) -> Unit,
    onToggleGlobal: (Boolean) -> Unit,
    onUpdateMinAmplitude: (Double) -> Unit,
    onUpdateFreqStart: (Int) -> Unit,
    onUpdateFreqEnd: (Int) -> Unit
) {
    var showThresholds by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GlobalToggleCard(
                title = "Ultrasonic Detection",
                description = "Monitor for ultrasonic tracking beacons",
                icon = Icons.Default.Mic,
                enabled = settings.enableUltrasonicDetection,
                onToggle = onToggleGlobal,
                enabledCount = settings.enabledUltrasonicPatterns.size,
                totalCount = UltrasonicPattern.values().size
            )
        }

        // Feasibility banner (replaces hardcoded Ultrasonic info banner)
        item {
            FeasibilityInfoBanner(
                feasibility = FeasibilityData.getProtocolFeasibility(DetectionProtocol.AUDIO, privilegeMode)
            )
        }

        item {
            ThresholdsSectionCard(
                title = "Ultrasonic Thresholds",
                expanded = showThresholds,
                onToggle = { showThresholds = !showThresholds }
            ) {
                UltrasonicThresholdsContent(
                    thresholds = settings.ultrasonicThresholds,
                    onUpdateMinAmplitude = onUpdateMinAmplitude,
                    onUpdateFreqStart = onUpdateFreqStart,
                    onUpdateFreqEnd = onUpdateFreqEnd
                )
            }
        }

        item {
            Text(
                text = "Detection Patterns",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        items(UltrasonicPattern.values().toList()) { pattern ->
            PatternToggleCard(
                name = pattern.displayName,
                description = pattern.description,
                enabled = pattern in settings.enabledUltrasonicPatterns,
                globalEnabled = settings.enableUltrasonicDetection,
                onToggle = { onTogglePattern(pattern, it) }
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun UltrasonicThresholdsContent(
    thresholds: UltrasonicThresholds,
    onUpdateMinAmplitude: (Double) -> Unit,
    onUpdateFreqStart: (Int) -> Unit,
    onUpdateFreqEnd: (Int) -> Unit
) {
    var minAmplitude by remember(thresholds.minAmplitude) { mutableStateOf(thresholds.minAmplitude.toFloat()) }
    var freqStart by remember(thresholds.frequencyRangeStart) { mutableStateOf(thresholds.frequencyRangeStart.toFloat()) }
    var freqEnd by remember(thresholds.frequencyRangeEnd) { mutableStateOf(thresholds.frequencyRangeEnd.toFloat()) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ThresholdSlider(
            label = "Minimum Amplitude",
            value = minAmplitude,
            valueRange = -50f..-20f,
            unit = "dB",
            description = "Minimum signal amplitude for detection",
            onValueChange = { minAmplitude = it },
            onValueChangeFinished = {
                onUpdateMinAmplitude(minAmplitude.toDouble())
            }
        )

        ThresholdSlider(
            label = "Frequency Range Start",
            value = freqStart,
            valueRange = 16000f..20000f,
            unit = "Hz",
            description = "Start of ultrasonic detection range",
            onValueChange = { freqStart = it },
            onValueChangeFinished = {
                onUpdateFreqStart(freqStart.toInt())
            }
        )

        ThresholdSlider(
            label = "Frequency Range End",
            value = freqEnd,
            valueRange = 18000f..24000f,
            unit = "Hz",
            description = "End of ultrasonic detection range",
            onValueChange = { freqEnd = it },
            onValueChangeFinished = {
                onUpdateFreqEnd(freqEnd.toInt())
            }
        )
    }
}

// ==================== Common Components ====================

@Composable
private fun ThresholdSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    unit: String,
    description: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    steps: Int = 0
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${if (value == value.toInt().toFloat()) value.toInt() else "%.1f".format(value)} $unit",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
