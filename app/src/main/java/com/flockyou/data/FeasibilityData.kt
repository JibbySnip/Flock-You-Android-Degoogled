package com.flockyou.data

import com.flockyou.data.model.Detection
import com.flockyou.data.model.DetectionProtocol
import com.flockyou.data.model.DetectionSource
import com.flockyou.privilege.PrivilegeMode

/**
 * Feasibility level for a detection protocol or pattern given the current privilege mode.
 */
enum class FeasibilityLevel(val displayName: String) {
    FULL("Full Detection"),
    DEGRADED("Degraded"),
    HEURISTIC_ONLY("Heuristic Only"),
    NOT_FEASIBLE("Not Available")
}

/**
 * Protocol-level feasibility information for display in settings banners.
 */
data class ProtocolFeasibility(
    val level: FeasibilityLevel,
    val summary: String,
    val whatWorks: List<String>,
    val whatDoesnt: List<String>,
    val upgradeNote: String?
)

/**
 * Pattern-level feasibility note for display below pattern toggles.
 */
data class PatternFeasibility(
    val level: FeasibilityLevel,
    val note: String
)

/**
 * Centralized feasibility data keyed by protocol + privilege mode.
 *
 * Provides honest information about what each detection protocol can and cannot do
 * based on the app's install mode (sideload vs system vs OEM).
 */
object FeasibilityData {

    // ==================== PROTOCOL-LEVEL FEASIBILITY ====================

    fun getProtocolFeasibility(protocol: DetectionProtocol, mode: PrivilegeMode): ProtocolFeasibility {
        return when (protocol) {
            DetectionProtocol.BLUETOOTH_LE -> getBleProtocolFeasibility(mode)
            DetectionProtocol.WIFI -> getWifiProtocolFeasibility(mode)
            DetectionProtocol.CELLULAR -> getCellularProtocolFeasibility(mode)
            DetectionProtocol.GNSS -> getGnssProtocolFeasibility(mode)
            DetectionProtocol.RF -> getRfProtocolFeasibility(mode)
            DetectionProtocol.AUDIO -> getUltrasonicProtocolFeasibility(mode)
            DetectionProtocol.SATELLITE -> getSatelliteProtocolFeasibility(mode)
        }
    }

    private fun getBleProtocolFeasibility(mode: PrivilegeMode): ProtocolFeasibility {
        return when (mode) {
            is PrivilegeMode.Sideload -> ProtocolFeasibility(
                level = FeasibilityLevel.DEGRADED,
                summary = "Duty-cycled scanning (25s on / 5s off), MAC addresses randomized",
                whatWorks = listOf(
                    "Device name pattern matching",
                    "Service UUID identification",
                    "Manufacturer data analysis",
                    "Tracker following detection (with reduced accuracy)"
                ),
                whatDoesnt = listOf(
                    "Continuous scanning (duty-cycled by Android)",
                    "Real MAC addresses (randomized since Android 8)",
                    "AirTag detection shares data with all Apple devices"
                ),
                upgradeNote = "System install enables continuous scanning and real MAC addresses"
            )
            is PrivilegeMode.System -> ProtocolFeasibility(
                level = FeasibilityLevel.DEGRADED,
                summary = "Continuous scanning available, real MACs possible if granted",
                whatWorks = listOf(
                    "Continuous BLE scanning (60s on / 1s off)",
                    "Real MAC addresses (if PEERS_MAC_ADDRESS granted)",
                    "Device name and UUID matching",
                    "Tracker following detection"
                ),
                whatDoesnt = listOf(
                    "AirTag detection still shares data with Apple network"
                ),
                upgradeNote = "OEM install enables full BLE capabilities"
            )
            is PrivilegeMode.OEM -> ProtocolFeasibility(
                level = FeasibilityLevel.FULL,
                summary = "Full BLE detection: continuous scanning, real MACs, no restrictions",
                whatWorks = listOf(
                    "Continuous BLE scanning",
                    "Real MAC addresses",
                    "Full tracker following detection",
                    "All pattern matching capabilities"
                ),
                whatDoesnt = emptyList(),
                upgradeNote = null
            )
        }
    }

    private fun getWifiProtocolFeasibility(mode: PrivilegeMode): ProtocolFeasibility {
        return when (mode) {
            is PrivilegeMode.Sideload -> ProtocolFeasibility(
                level = FeasibilityLevel.DEGRADED,
                summary = "Throttled to 4 scans per 2 minutes. No monitor mode - deauth detection is heuristic only",
                whatWorks = listOf(
                    "SSID pattern matching for surveillance devices",
                    "MAC OUI prefix identification",
                    "Evil twin detection (same SSID, different BSSID)",
                    "Following network detection across locations"
                ),
                whatDoesnt = listOf(
                    "Monitor mode (cannot observe raw 802.11 frames)",
                    "True deauth frame detection (heuristic via disconnect frequency)",
                    "Karma attack confirmation (heuristic only)",
                    "Frequent scanning (throttled by Android)"
                ),
                upgradeNote = "System install bypasses WiFi scan throttling"
            )
            is PrivilegeMode.System -> ProtocolFeasibility(
                level = FeasibilityLevel.DEGRADED,
                summary = "Scan throttling bypassed. Still no monitor mode - deauth detection is heuristic only",
                whatWorks = listOf(
                    "Frequent WiFi scanning (10s intervals)",
                    "SSID and MAC pattern matching",
                    "Evil twin and following network detection"
                ),
                whatDoesnt = listOf(
                    "Monitor mode (cannot observe raw 802.11 frames)",
                    "True deauth frame detection (heuristic only)"
                ),
                upgradeNote = "Monitor mode requires custom OS regardless of install mode"
            )
            is PrivilegeMode.OEM -> ProtocolFeasibility(
                level = FeasibilityLevel.DEGRADED,
                summary = "Full scan rate. No monitor mode without custom OS - deauth detection remains heuristic",
                whatWorks = listOf(
                    "Frequent WiFi scanning (10s intervals)",
                    "Full SSID and MAC pattern matching",
                    "Evil twin and following network detection"
                ),
                whatDoesnt = listOf(
                    "Monitor mode (hardware/OS limitation, not privilege)"
                ),
                upgradeNote = null
            )
        }
    }

    private fun getCellularProtocolFeasibility(mode: PrivilegeMode): ProtocolFeasibility {
        return when (mode) {
            is PrivilegeMode.Sideload -> ProtocolFeasibility(
                level = FeasibilityLevel.HEURISTIC_ONLY,
                summary = "Heuristic anomaly detection only. Cannot confirm IMSI capture, no modem diagnostics",
                whatWorks = listOf(
                    "Network type change detection (5G/4G to 2G)",
                    "Cell tower change monitoring",
                    "Signal strength anomaly tracking",
                    "Suspicious MCC/MNC identification"
                ),
                whatDoesnt = listOf(
                    "Confirming IMSI capture (no IMEI/IMSI access)",
                    "Null cipher detection (no modem access)",
                    "Silent SMS detection",
                    "Shannon SDM modem diagnostics"
                ),
                upgradeNote = "OEM install enables IMEI/IMSI access and Shannon SDM modem diagnostics"
            )
            is PrivilegeMode.System -> ProtocolFeasibility(
                level = FeasibilityLevel.HEURISTIC_ONLY,
                summary = "Enhanced monitoring but still heuristic. No IMEI/IMSI or modem diagnostics",
                whatWorks = listOf(
                    "Enhanced cell monitoring with faster intervals",
                    "Network type change detection",
                    "Cell tower trust database",
                    "Signal strength anomaly tracking"
                ),
                whatDoesnt = listOf(
                    "IMEI/IMSI access",
                    "Null cipher detection",
                    "Silent SMS detection",
                    "Shannon SDM modem diagnostics"
                ),
                upgradeNote = "OEM install enables IMEI/IMSI access and Shannon SDM modem diagnostics"
            )
            is PrivilegeMode.OEM -> ProtocolFeasibility(
                level = FeasibilityLevel.FULL,
                summary = "Full cellular detection: IMEI/IMSI access + Shannon SDM modem diagnostics on supported devices",
                whatWorks = listOf(
                    "IMEI/IMSI access for confirmed IMSI catcher detection",
                    "Shannon SDM: null cipher detection (definitive)",
                    "Shannon SDM: IMSI paging observation",
                    "Shannon SDM: silent SMS interception",
                    "Shannon SDM: forced 2G redirect detection",
                    "All heuristic anomaly detection"
                ),
                whatDoesnt = emptyList(),
                upgradeNote = null
            )
        }
    }

    private fun getGnssProtocolFeasibility(mode: PrivilegeMode): ProtocolFeasibility {
        // GNSS feasibility is the same across all modes - hardware limitation
        return ProtocolFeasibility(
            level = FeasibilityLevel.DEGRADED,
            summary = "C/N0 analysis available but high false positive rate in urban environments. Disabled by default",
            whatWorks = listOf(
                "C/N0 signal quality analysis",
                "Multi-constellation cross-validation",
                "Clock drift detection",
                "Position jump detection"
            ),
            whatDoesnt = listOf(
                "Reliable spoofing confirmation in urban areas (multipath interference)",
                "Distinguishing spoofing from normal GPS variation indoors"
            ),
            upgradeNote = null
        )
    }

    private fun getRfProtocolFeasibility(mode: PrivilegeMode): ProtocolFeasibility {
        // RF is NOT_FEASIBLE without Flipper Zero regardless of mode
        return ProtocolFeasibility(
            level = FeasibilityLevel.NOT_FEASIBLE,
            summary = "WiFi-proxy inference only. Direct RF measurement requires Flipper Zero hardware",
            whatWorks = listOf(
                "WiFi signal pattern analysis (jammer inference)",
                "Drone WiFi signature matching",
                "Hidden network counting"
            ),
            whatDoesnt = listOf(
                "True RF spectrum measurement",
                "Sub-GHz signal detection (315/433/868/915 MHz)",
                "Direct jammer detection",
                "Hidden transmitter RF sweep"
            ),
            upgradeNote = "Connect Flipper Zero for true RF spectrum analysis"
        )
    }

    private fun getUltrasonicProtocolFeasibility(mode: PrivilegeMode): ProtocolFeasibility {
        return when (mode) {
            is PrivilegeMode.OEM -> ProtocolFeasibility(
                level = FeasibilityLevel.DEGRADED,
                summary = "MEMS microphone sensitivity drops 10-20dB above 18kHz. Boot restriction lifted on OEM",
                whatWorks = listOf(
                    "Ultrasonic beacon detection (18-22kHz)",
                    "Cross-device tracking beacon identification",
                    "Persistent at boot (OEM privilege)"
                ),
                whatDoesnt = listOf(
                    "Full ultrasonic sensitivity (MEMS hardware limitation)"
                ),
                upgradeNote = null
            )
            else -> ProtocolFeasibility(
                level = FeasibilityLevel.DEGRADED,
                summary = "MEMS microphone sensitivity drops 10-20dB above 18kHz. Requires privacy consent for mic access",
                whatWorks = listOf(
                    "Ultrasonic beacon detection (18-22kHz)",
                    "Cross-device tracking beacon identification"
                ),
                whatDoesnt = listOf(
                    "Full ultrasonic sensitivity (MEMS hardware limitation)",
                    "Auto-start at boot (requires manual activation)"
                ),
                upgradeNote = if (mode is PrivilegeMode.Sideload)
                    "OEM install enables auto-start at boot" else null
            )
        }
    }

    private fun getSatelliteProtocolFeasibility(mode: PrivilegeMode): ProtocolFeasibility {
        // Satellite is heuristic-only across all modes - minimal deployment
        return ProtocolFeasibility(
            level = FeasibilityLevel.HEURISTIC_ONLY,
            summary = "Forward-looking detection. NTN satellite services (T-Mobile Starlink, Skylo) are minimal deployment",
            whatWorks = listOf(
                "NTN connection monitoring (Android 15+)",
                "Unexpected satellite connection detection",
                "Timing anomaly analysis"
            ),
            whatDoesnt = listOf(
                "Reliable threat vs legitimate NTN service distinction",
                "Coverage on pre-Android 15 devices"
            ),
            upgradeNote = null
        )
    }

    // ==================== PATTERN-LEVEL FEASIBILITY ====================

    /**
     * Get feasibility note for a specific pattern. Returns null if no caveat needed.
     */
    fun getPatternFeasibility(pattern: Any, mode: PrivilegeMode): PatternFeasibility? {
        return when (pattern) {
            // Shannon SDM patterns - NOT_FEASIBLE on sideload/system
            is CellularPattern -> getCellularPatternFeasibility(pattern, mode)
            is WifiPattern -> getWifiPatternFeasibility(pattern, mode)
            is GnssPattern -> getGnssPatternFeasibility(pattern, mode)
            else -> null
        }
    }

    private fun getCellularPatternFeasibility(pattern: CellularPattern, mode: PrivilegeMode): PatternFeasibility? {
        return when (pattern) {
            CellularPattern.SDM_NULL_CIPHER,
            CellularPattern.SDM_IMSI_PAGING,
            CellularPattern.SDM_SILENT_SMS,
            CellularPattern.SDM_FORCED_2G,
            CellularPattern.SDM_AUTH_ANOMALY -> {
                when (mode) {
                    is PrivilegeMode.OEM -> null // Full capability, no caveat
                    else -> PatternFeasibility(
                        level = FeasibilityLevel.NOT_FEASIBLE,
                        note = "Requires Shannon modem diagnostic access (OEM only)"
                    )
                }
            }
            CellularPattern.ENCRYPTION_DOWNGRADE -> {
                when (mode) {
                    is PrivilegeMode.OEM -> PatternFeasibility(
                        level = FeasibilityLevel.FULL,
                        note = "Shannon SDM confirms actual cipher algorithm (A5/0, EEA0)"
                    )
                    else -> PatternFeasibility(
                        level = FeasibilityLevel.HEURISTIC_ONLY,
                        note = "Detects network type change but cannot confirm cipher algorithm"
                    )
                }
            }
            else -> null
        }
    }

    private fun getWifiPatternFeasibility(pattern: WifiPattern, mode: PrivilegeMode): PatternFeasibility? {
        return when (pattern) {
            WifiPattern.STINGRAY_WIFI -> PatternFeasibility(
                level = FeasibilityLevel.HEURISTIC_ONLY,
                note = "SSID pattern matching only - cannot confirm cell site simulator"
            )
            else -> null
        }
    }

    private fun getGnssPatternFeasibility(pattern: GnssPattern, mode: PrivilegeMode): PatternFeasibility? {
        return when (pattern) {
            GnssPattern.SPOOFING -> PatternFeasibility(
                level = FeasibilityLevel.DEGRADED,
                note = "High false positive rate in urban environments"
            )
            GnssPattern.MULTIPATH -> PatternFeasibility(
                level = FeasibilityLevel.DEGRADED,
                note = "Difficult to distinguish from real urban multipath"
            )
            else -> null
        }
    }

    // ==================== LLM CONTEXT ====================

    /**
     * Build the privilege mode context section for LLM prompts.
     */
    fun getLlmPrivilegeModeContext(mode: PrivilegeMode): String {
        return when (mode) {
            is PrivilegeMode.Sideload -> """
=== APP DETECTION CAPABILITIES ===
Install Mode: Standard (sideloaded)
IMPORTANT: Factor these limitations into your analysis:
- WiFi: Scans throttled (4/2min). NO monitor mode. Deauth/karma detection is heuristic only.
- BLE: Duty-cycled scanning (25s/5s). MAC addresses RANDOMIZED. AirTag shares data with all Apple devices.
- Cellular: Heuristic anomaly detection only. CANNOT confirm IMSI capture. No IMEI/IMSI access. No null cipher detection. No silent SMS detection. Shannon SDM diagnostic access NOT available.
- GNSS: C/N0 analysis available but high urban false positive rate.
- RF: WiFi-proxy inference ONLY. No true RF measurement without Flipper Zero.
- Ultrasonic: MEMS microphone sensitivity drops 10-20dB above 18kHz.
If confidence depends on capabilities this app CANNOT confirm, LOWER your confidence score and mention this limitation.""".trimIndent()

            is PrivilegeMode.System -> """
=== APP DETECTION CAPABILITIES ===
Install Mode: System (privileged app)
Enhanced over sideload but still limited:
- WiFi: Scan throttling bypassed. Still NO monitor mode. Deauth/karma detection is heuristic only.
- BLE: Continuous scanning available. Real MACs possible if PEERS_MAC_ADDRESS granted.
- Cellular: Enhanced monitoring but still heuristic. CANNOT confirm IMSI capture. No IMEI/IMSI access. No Shannon SDM.
- GNSS: C/N0 analysis available but high urban false positive rate.
- RF: WiFi-proxy inference ONLY. No true RF measurement without Flipper Zero.
- Ultrasonic: MEMS microphone sensitivity drops 10-20dB above 18kHz.
If confidence depends on capabilities this app CANNOT confirm, LOWER your confidence score and mention this limitation.""".trimIndent()

            is PrivilegeMode.OEM -> """
=== APP DETECTION CAPABILITIES ===
Install Mode: OEM (platform-signed)
Full capabilities available:
- IMEI/IMSI access for confirmed IMSI catcher detection
- Shannon SDM diagnostic access on supported devices (Pixel 6-9, Samsung Exynos): null cipher detection, IMSI paging observation, silent SMS interception, forced 2G redirect detection
- Continuous BLE/WiFi scanning, real MAC addresses, persistent process
Shannon SDM detections are DEFINITIVE modem-level evidence, not heuristic inference.""".trimIndent()
        }
    }

    /**
     * Build compact privilege mode context for MediaPipe/smaller models.
     */
    fun getCompactLlmPrivilegeModeContext(mode: PrivilegeMode): String {
        return when (mode) {
            is PrivilegeMode.Sideload -> """LIMITS:sideload
-wifi:4/2min,no monitor
-ble:duty-cycled,MAC rand
-cell:heuristic,NO IMSI confirm,NO Shannon SDM
-rf:wifi-proxy only
Lower conf if unconfirmable"""
            is PrivilegeMode.System -> """LIMITS:system
-wifi:no monitor
-ble:continuous,MACs possible
-cell:heuristic,NO IMSI confirm,NO Shannon SDM
-rf:wifi-proxy only
Lower conf if unconfirmable"""
            is PrivilegeMode.OEM -> """MODE:OEM(full)
-IMEI/IMSI access
-Shannon SDM:null cipher,IMSI paging,silent SMS,forced 2G
SDM detections=DEFINITIVE modem evidence"""
        }
    }

    // ==================== DETECTION RELIABILITY NOTES ====================

    /**
     * Get a one-line reliability note for a specific detection, factoring in privilege mode.
     * Returns null if no note is needed (detection is fully reliable).
     */
    fun getDetectionReliabilityNote(detection: Detection, mode: PrivilegeMode): String? {
        // Shannon SDM detections are definitive regardless of context
        if (detection.detectionSource == DetectionSource.SHANNON_SDM) {
            return "Detection confirmed via Shannon modem diagnostic signaling. This is definitive modem-level evidence."
        }

        return when (detection.protocol) {
            DetectionProtocol.CELLULAR -> getCellularReliabilityNote(mode)
            DetectionProtocol.WIFI -> getWifiReliabilityNote(detection, mode)
            DetectionProtocol.RF -> "Inferred from WiFi signal patterns only. Direct RF measurement requires Flipper Zero."
            DetectionProtocol.BLUETOOTH_LE -> getBleReliabilityNote(mode)
            DetectionProtocol.GNSS -> "GNSS anomaly detection has elevated false positive rates in urban environments."
            DetectionProtocol.AUDIO -> "MEMS microphone sensitivity drops significantly above 18kHz, limiting ultrasonic range."
            DetectionProtocol.SATELLITE -> "NTN satellite detection is forward-looking. Legitimate services (T-Mobile Starlink, Skylo) may trigger alerts."
        }
    }

    private fun getCellularReliabilityNote(mode: PrivilegeMode): String {
        return when (mode) {
            is PrivilegeMode.OEM -> "OEM privileges enable enhanced cellular monitoring with IMEI/IMSI access."
            else -> "Detection is based on cellular API heuristics. IMSI capture cannot be confirmed without OEM privileges."
        }
    }

    private fun getWifiReliabilityNote(detection: Detection, mode: PrivilegeMode): String? {
        return when (detection.detectionMethod) {
            com.flockyou.data.model.DetectionMethod.WIFI_DEAUTH_ATTACK ->
                "Detected via disconnect frequency heuristic. Android cannot enter WiFi monitor mode - true deauth frame observation not possible."
            com.flockyou.data.model.DetectionMethod.WIFI_KARMA_ATTACK ->
                "Inferred from probe response behavior. Cannot confirm true karma attack without monitor mode."
            else -> {
                if (mode is PrivilegeMode.Sideload)
                    "WiFi scanning is throttled to 4 scans per 2 minutes on sideloaded installs."
                else null
            }
        }
    }

    private fun getBleReliabilityNote(mode: PrivilegeMode): String? {
        return when (mode) {
            is PrivilegeMode.Sideload ->
                "BLE scanning is duty-cycled (25s on / 5s off) and MAC addresses are randomized."
            else -> null
        }
    }
}
