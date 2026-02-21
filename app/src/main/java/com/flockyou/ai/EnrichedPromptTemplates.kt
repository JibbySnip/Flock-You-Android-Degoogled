package com.flockyou.ai

import com.flockyou.data.model.Detection
import com.flockyou.monitoring.GnssSatelliteMonitor.GnssAnomalyAnalysis
import com.flockyou.service.CellularMonitor.CellularAnomalyAnalysis
import com.flockyou.service.RogueWifiMonitor.FollowingNetworkAnalysis
import com.flockyou.service.UltrasonicDetector.BeaconAnalysis

/**
 * Enriched prompt templates for detector-specific LLM analysis.
 *
 * These prompts leverage enriched detector data (cellular analysis, GNSS analysis,
 * ultrasonic beacon analysis, etc.) to produce more accurate and contextual LLM output.
 */
object EnrichedPromptTemplates {

    /**
     * Build an enriched prompt for cellular/IMSI catcher detections.
     */
    fun buildCellularEnrichedPrompt(
        detection: Detection,
        analysis: CellularAnomalyAnalysis
    ): String {
        val fpSection = if (analysis.falsePositiveLikelihood > 30f) {
            """

=== FALSE POSITIVE ANALYSIS ===
FP Likelihood: ${String.format("%.0f", analysis.falsePositiveLikelihood)}%
Likely Normal Handoff: ${if (analysis.isLikelyNormalHandoff) "YES - Routine cell tower switch" else "No"}
Likely Carrier Behavior: ${if (analysis.isLikelyCarrierBehavior) "YES - This carrier has aggressive handoff patterns" else "No"}
Likely Edge Coverage: ${if (analysis.isLikelyEdgeCoverage) "YES - User at cell coverage boundary" else "No"}
Likely 5G Beam Steering: ${if (analysis.isLikely5gBeamSteering) "YES - Normal 5G beam management" else "No"}
FP Indicators:
${analysis.fpIndicators.joinToString("\n") { "- $it" }.ifEmpty { "- None" }}

IMPORTANT: This detection has a ${String.format("%.0f", analysis.falsePositiveLikelihood)}% chance of being a FALSE POSITIVE.
Consider these FP indicators when analyzing. If FP likelihood > 50%, lean toward normal cellular behavior."""
        } else ""

        val content = """Analyze this potential IMSI catcher/cell site simulator detection.

=== IMSI CATCHER ANALYSIS ===
IMSI Catcher Likelihood: ${analysis.imsiCatcherScore}%
Encryption Chain: ${analysis.encryptionDowngradeChain.joinToString(" -> ")}
Current Encryption: ${analysis.currentEncryption.displayName}
Encryption Downgraded: ${if (analysis.encryptionDowngraded) "YES - from ${analysis.previousEncryption?.displayName}" else "No"}
${analysis.vulnerabilityNote?.let { "Warning: $it" } ?: ""}

=== MOVEMENT CONTEXT ===
Movement Type: ${analysis.movementType.displayName}
Speed: ${String.format("%.1f", analysis.speedKmh)} km/h
Distance: ${String.format("%.0f", analysis.distanceMeters)} meters
Time Window: ${analysis.timeBetweenSamplesMs / 1000} seconds
Impossible Movement: ${if (analysis.impossibleSpeed) "YES - SUSPICIOUS" else "No"}

=== CELL TOWER TRUST ===
Cell Trust Score: ${analysis.cellTrustScore}%
Times Seen: ${analysis.cellSeenCount}
Cell Age: ${analysis.cellAgeSeconds / 60} minutes in database
Familiar Area: ${if (analysis.isInFamiliarArea) "Yes" else "No"}
Trusted Cells Nearby: ${analysis.nearbyTrustedCells}

=== SIGNAL ANALYSIS ===
Current Signal: ${analysis.currentSignalDbm} dBm (${analysis.signalQuality})
Signal Delta: ${if (analysis.signalDeltaDbm > 0) "+" else ""}${analysis.signalDeltaDbm} dBm
Signal Spike: ${if (analysis.signalSpikeDetected) "YES" else "No"}
Downgrade + Spike: ${if (analysis.downgradeWithSignalSpike) "YES - Classic IMSI signature" else "No"}
Downgrade + New Tower: ${if (analysis.downgradeWithNewTower) "YES - Suspicious" else "No"}

=== NETWORK CONTEXT ===
Network Change: ${analysis.networkGenerationChange ?: "None"}
LAC/TAC Changed: ${if (analysis.lacTacChanged) "YES - Unusual" else "No"}
Operator Changed: ${if (analysis.operatorChanged) "YES - IMSI catchers may present different operator identity" else "No"}
Roaming: ${if (analysis.isRoaming) "YES - Unexpected roaming can indicate fake base station with foreign MCC-MNC" else "No"}
$fpSection

Based on this enriched data, provide:
1. A plain-English explanation for a non-technical user about what's happening to their phone - OR explain why this is likely a false positive
2. Whether this indicates active IMSI catcher surveillance (yes/no with confidence percentage)
3. The top 3 SPECIFIC actions they should take RIGHT NOW (or "No action needed" if FP)
4. What data may have been captured (or "No data at risk" if FP)

CRITICAL: If FP Likelihood > 50%, you MUST conclude this is likely NOT an IMSI catcher.
Common false positives include: normal handoffs while driving, carrier network optimization, 5G beam steering, entering/exiting buildings, areas with poor coverage.

Format as:
## Assessment
[Your assessment - OR why this is likely a false positive]

## Actions
1. [Action 1 - OR "No action needed"]
2. [Action 2]
3. [Action 3]

## Data at Risk
[What may have been captured - OR "No data at risk - likely normal network behavior"]"""

        return wrapGemmaPrompt(content)
    }

    /**
     * Build an enriched prompt for GNSS spoofing/jamming detections.
     */
    fun buildGnssEnrichedPrompt(
        detection: Detection,
        analysis: GnssAnomalyAnalysis
    ): String {
        val fpSection = if (analysis.falsePositiveLikelihood > 30f) {
            """

=== FALSE POSITIVE ANALYSIS ===
FP Likelihood: ${String.format("%.0f", analysis.falsePositiveLikelihood)}%
Likely Normal Operation: ${if (analysis.isLikelyNormalOperation) "YES" else "No"}
Likely Urban Multipath: ${if (analysis.isLikelyUrbanMultipath) "YES - Building reflections causing signal variance" else "No"}
Likely Indoor Signal Loss: ${if (analysis.isLikelyIndoorSignalLoss) "YES - Weak indoor reception" else "No"}
FP Indicators:
${analysis.fpIndicators.joinToString("\n") { "- $it" }.ifEmpty { "- None" }}

IMPORTANT: This detection has a ${String.format("%.0f", analysis.falsePositiveLikelihood)}% chance of being a FALSE POSITIVE.
Consider these FP indicators when analyzing. If FP likelihood > 50%, lean toward dismissing as normal GPS behavior."""
        } else ""

        // Fix quality section - satellite count, HDOP/PDOP for context
        val fixQualitySection = buildString {
            appendLine("=== FIX QUALITY ===")
            appendLine("Satellites Visible: ${analysis.totalSatellitesVisible}")
            appendLine("Satellites Used in Fix: ${analysis.satellitesUsedInFix}")
            appendLine("Has Fix: ${if (analysis.hasFix) "YES" else "No"}")
            if (analysis.hdop != null) {
                appendLine("HDOP: ${String.format("%.1f", analysis.hdop)} ${when {
                    analysis.hdop!! < 1f -> "(Excellent)"
                    analysis.hdop!! < 2f -> "(Good)"
                    analysis.hdop!! < 5f -> "(Moderate)"
                    analysis.hdop!! < 10f -> "(Fair)"
                    else -> "(Poor)"
                }}")
            } else {
                appendLine("HDOP: Unavailable (chipset limitation)")
            }
            if (analysis.pdop != null) {
                appendLine("PDOP: ${String.format("%.1f", analysis.pdop)} ${when {
                    analysis.pdop!! < 1f -> "(Excellent)"
                    analysis.pdop!! < 2f -> "(Good)"
                    analysis.pdop!! < 5f -> "(Moderate)"
                    analysis.pdop!! < 10f -> "(Fair)"
                    else -> "(Poor)"
                }}")
            } else {
                appendLine("PDOP: Unavailable (chipset limitation)")
            }
        }.trimEnd()

        // Environment context section - critical for FP assessment
        val environmentSection = if (analysis.environmentType != "Unknown") {
            """

=== ENVIRONMENT CONTEXT ===
Environment: ${analysis.environmentType} (${String.format("%.0f", analysis.environmentConfidence * 100)}% confidence)
Guidance: ${analysis.environmentGuidance}
NOTE: Environmental context is critical for false positive assessment. ${analysis.environmentType} environments commonly cause signal anomalies that mimic attacks."""
        } else ""

        // Cross-constellation consistency line
        val crossConstellationLine = when {
            analysis.missingConstellations.isEmpty() && !analysis.unexpectedConstellation ->
                "Cross-Constellation Consistency: All constellations consistent"
            analysis.missingConstellations.isNotEmpty() ->
                "Cross-Constellation Consistency: Missing ${analysis.missingConstellations.size} expected constellation(s) - possible selective jamming"
            else ->
                "Cross-Constellation Consistency: Unexpected constellation detected - anomalous"
        }

        val content = """Analyze this GNSS (GPS/satellite) anomaly detection.

=== SPOOFING/JAMMING LIKELIHOOD ===
Spoofing Likelihood: ${String.format("%.0f", analysis.spoofingLikelihood)}%
Jamming Likelihood: ${String.format("%.0f", analysis.jammingLikelihood)}%

$fixQualitySection

=== CONSTELLATION ANALYSIS ===
Expected Constellations: ${analysis.expectedConstellations.joinToString { it.code }}
Observed Constellations: ${analysis.observedConstellations.joinToString { it.code }}
Missing Constellations: ${analysis.missingConstellations.joinToString { it.code }.ifEmpty { "None" }}
Constellation Match: ${analysis.constellationMatchScore}%
Unexpected Constellation: ${if (analysis.unexpectedConstellation) "YES" else "No"}
$crossConstellationLine

=== SIGNAL ANALYSIS (C/N0) ===
Historical Baseline: ${String.format("%.1f", analysis.historicalCn0Mean)} +/- ${String.format("%.1f", analysis.historicalCn0StdDev)} dB-Hz
Current C/N0: ${String.format("%.1f", analysis.currentCn0Mean)} dB-Hz
Deviation: ${String.format("%.1f", analysis.cn0DeviationSigmas)}s from baseline
Signal Uniformity: ${if (analysis.cn0TooUniform) "TOO UNIFORM - Spoofing indicator" else "Normal variance"}
Variance: ${String.format("%.2f", analysis.cn0Variance)}

=== SATELLITE GEOMETRY ===
Geometry Score: ${String.format("%.0f", analysis.geometryScore * 100)}%
Elevation Distribution: ${analysis.elevationDistribution}
Azimuth Coverage: ${String.format("%.0f", analysis.azimuthCoverage)}%
Low-Elev High-Signal: ${analysis.lowElevHighSignalCount} satellites (spoofing indicator if > 0)

=== CLOCK ANALYSIS ===
Cumulative Drift: ${analysis.cumulativeDriftNs / 1_000_000} ms
Drift Trend: ${analysis.driftTrend.displayName}
Drift Anomalous: ${if (analysis.driftAnomalous) "YES" else "No"}
Drift Jumps: ${analysis.driftJumpCount}
$environmentSection
$fpSection

=== INDICATORS ===
Spoofing Indicators:
${analysis.spoofingIndicators.joinToString("\n") { "- $it" }.ifEmpty { "- None detected" }}

Jamming Indicators:
${analysis.jammingIndicators.joinToString("\n") { "- $it" }.ifEmpty { "- None detected" }}

Based on this enriched data, provide:
1. A clear explanation of whether the user's GPS/location is being manipulated - OR explain why this is likely a false positive
2. What this means for their safety and privacy (if applicable)
3. The top 3 actions they should take (or "No action needed" if FP)
4. Whether they should trust their current location on maps
5. Whether the environmental context supports or contradicts an attack scenario

CRITICAL: If FP Likelihood > 50%, you MUST conclude this is likely NOT an attack.
Common false positives include: urban multipath (signal reflections off buildings), indoor signal attenuation, normal GPS variation during cold start, transitioning between environments.
Environmental context (if available) should heavily influence your assessment - indoor/urban canyon environments cause signal anomalies that closely mimic spoofing/jamming.

Format as:
## Assessment
[Your assessment - is GPS being spoofed or jammed? OR why this is likely a false positive. Factor in environment context.]

## Impact
[What this means for the user - OR "Likely no impact - normal GPS behavior"]

## Actions
1. [Action 1 - OR "No action needed"]
2. [Action 2]
3. [Action 3]

## Location Trustworthiness
[Can they trust their GPS right now? Reference fix quality and satellite count.]"""

        return wrapGemmaPrompt(content)
    }

    /**
     * Build an enriched prompt for ultrasonic beacon detections.
     */
    fun buildUltrasonicEnrichedPrompt(
        detection: Detection,
        analysis: BeaconAnalysis
    ): String {
        val fpSection = if (analysis.falsePositiveLikelihood > 30f) {
            """
=== FALSE POSITIVE ANALYSIS ===
FP Likelihood: ${String.format("%.0f", analysis.falsePositiveLikelihood)}%
Concurrent Beacons: ${analysis.concurrentBeaconCount} (detected at same time)
Likely Ambient Noise: ${if (analysis.isLikelyAmbientNoise) "YES" else "No"}
Likely Device Artifact: ${if (analysis.isLikelyDeviceArtifact) "YES" else "No"}
FP Indicators:
${analysis.fpIndicators.joinToString("\n") { "- $it" }.ifEmpty { "- None" }}

IMPORTANT: This detection has a ${String.format("%.0f", analysis.falsePositiveLikelihood)}% chance of being a FALSE POSITIVE.
Consider these FP indicators when analyzing. If FP likelihood > 50%, lean toward dismissing as noise."""
        } else ""

        val content = """Analyze this ultrasonic tracking beacon detection.

=== BEACON FINGERPRINT ===
Frequency: ${analysis.frequencyHz} Hz
Beacon Type: ${analysis.matchedSource.displayName}
Category: ${analysis.sourceCategory.displayName}
Source Confidence: ${String.format("%.0f", analysis.sourceConfidence)}%
Frequency Stable: ${if (analysis.isFrequencyStable) "YES (within +/-10Hz - beacon characteristic)" else "No (drifting - may be environmental noise)"}
Frequency Stability: ${String.format("%.1f", analysis.frequencyStabilityHz)} Hz variance
${if (analysis.hasKnownModulationPattern) "Modulation Pattern: ${analysis.modulationPatternType} (data encoding detected)" else "Modulation Pattern: None detected"}

=== AMPLITUDE ANALYSIS ===
Peak Amplitude: ${String.format("%.1f", analysis.peakAmplitudeDb)} dB
Average Amplitude: ${String.format("%.1f", analysis.avgAmplitudeDb)} dB
Amplitude Variance: ${String.format("%.2f", analysis.amplitudeVariance)}
Amplitude Profile: ${analysis.amplitudeProfile.displayName}
SNR: ${String.format("%.1f", analysis.snrDb)} dB

=== CROSS-LOCATION TRACKING ===
Following User: ${if (analysis.followingUser) "YES - Detected at multiple of your locations" else "No"}
Cross-Location Following: ${if (analysis.isFollowingAcrossLocations) "YES - Same beacon seen at home and other locations" else "No"}
Locations Detected: ${analysis.locationsDetected}
Total Detections: ${analysis.totalDetectionCount}
Detection Duration: ${analysis.detectionDurationMs / 60000} minutes
Persistence Score: ${String.format("%.0f", analysis.persistenceScore * 100)}%

=== ENVIRONMENT CONTEXT ===
Environment: ${analysis.environmentalContext.displayName}
Noise Floor: ${String.format("%.1f", analysis.noiseFloorDb)} dB
$fpSection

=== SOURCE INTELLIGENCE ===
Probable Source: ${analysis.probableSourceDescription.ifEmpty { "Unknown" }}
What It Does: ${analysis.whatItDoes.ifEmpty { "Unknown" }}
Recommended Action: ${analysis.recommendedAction.ifEmpty { "Monitor and verify" }}

=== RISK ASSESSMENT ===
Tracking Likelihood: ${String.format("%.0f", analysis.trackingLikelihood)}%
Risk Indicators:
${analysis.riskIndicators.joinToString("\n") { "- $it" }.ifEmpty { "- None" }}

Based on this enriched data, explain:
1. What this ultrasonic beacon is doing (in plain English) - OR explain why this is likely a false positive
2. How it tracks users across devices (if applicable)
3. What company/technology is likely behind it (or "Likely not a real beacon" if FP)
4. How to stop or avoid this tracking (or "No action needed" if FP)

CRITICAL: If FP Likelihood > 50%, you MUST conclude this is likely NOT a real tracking beacon.
Common false positives include: ambient ultrasonic noise, electronic interference, device speaker/microphone artifacts.

Format as:
## What's Happening
[Explanation of the beacon OR why it's a false positive]

## How It Tracks You
[Tracking mechanism OR "Not applicable - likely false positive"]

## Likely Source
[Who is behind this OR "Environmental noise / device artifact"]

## Protection Steps
1. [Step 1 OR "No action needed"]
2. [Step 2]
3. [Step 3]"""

        return wrapGemmaPrompt(content)
    }

    /**
     * Build an enriched prompt for following WiFi network detections.
     */
    fun buildWifiFollowingEnrichedPrompt(
        detection: Detection,
        analysis: FollowingNetworkAnalysis
    ): String {
        val fpSection = if (analysis.falsePositiveLikelihood > 30f) {
            """

=== FALSE POSITIVE ANALYSIS ===
FP Likelihood: ${String.format("%.0f", analysis.falsePositiveLikelihood)}%
Likely Neighbor Network: ${if (analysis.isLikelyNeighborNetwork) "YES - Common business/residential WiFi in your area" else "No"}
Likely Mobile Hotspot: ${if (analysis.isLikelyMobileHotspot) "YES - Personal hotspot from family/coworker" else "No"}
Likely Commuter Device: ${if (analysis.isLikelyCommuterDevice) "YES - Same commute pattern (not following you)" else "No"}
Likely Public Transit: ${if (analysis.isLikelyPublicTransit) "YES - Bus/train WiFi you regularly use" else "No"}
FP Indicators:
${analysis.fpIndicators.joinToString("\n") { "- $it" }.ifEmpty { "- None" }}

IMPORTANT: This detection has a ${String.format("%.0f", analysis.falsePositiveLikelihood)}% chance of being a FALSE POSITIVE.
Consider these FP indicators when analyzing. If FP likelihood > 50%, lean toward coincidental overlap."""
        } else ""

        val content = """Analyze this "following network" detection - a WiFi network appearing at multiple locations.

=== FOLLOWING PATTERN ===
Network SSID: ${sanitize(detection.ssid, 64).ifEmpty { "Unknown" }}
MAC Address: ${sanitize(detection.macAddress, 32).ifEmpty { "Unknown" }}
Times Spotted: ${analysis.sightingCount}
Distinct Locations: ${analysis.distinctLocations}
Following Confidence: ${String.format("%.0f", analysis.followingConfidence)}%
Tracking Duration: ${analysis.trackingDurationMs / 60000} minutes

=== TEMPORAL PATTERNS ===
Time Pattern: ${analysis.timePattern.displayName}
Avg Time Between Sightings: ${analysis.avgTimeBetweenSightingsMs / 60000} minutes
Following Duration: ${analysis.followingDurationMs / 60000} minutes

=== MOVEMENT CORRELATION ===
Path Correlation: ${String.format("%.0f", analysis.pathCorrelation * 100)}%
Leads User: ${if (analysis.leadsUser) "YES - Appears BEFORE you arrive (very suspicious)" else "No"}
${analysis.lagTimeMs?.let { "Lag Time: ${it / 1000} seconds behind you" } ?: ""}

=== SIGNAL ANALYSIS ===
Signal Consistency: ${String.format("%.0f", analysis.signalConsistency * 100)}%
Signal Trend: ${analysis.signalTrend.displayName}
Average Signal: ${analysis.avgSignalStrength} dBm
Signal Variance: ${String.format("%.1f", analysis.signalVariance)}

=== DEVICE CLASSIFICATION ===
Likely Mobile Device: ${if (analysis.likelyMobile) "YES" else "No - Fixed location"}
Vehicle Mounted: ${if (analysis.vehicleMounted) "YES - Large movements suggest vehicle" else "No"}
Foot Surveillance: ${if (analysis.possibleFootSurveillance) "POSSIBLE - Slower, closer movements" else "No"}

=== RISK INDICATORS ===
${analysis.riskIndicators.joinToString("\n") { "- $it" }.ifEmpty { "- None" }}
$fpSection

Based on this enriched data, provide:
1. Whether this network is genuinely following the user or a coincidence - OR explain why this is likely a false positive
2. The likely type of device/vehicle carrying this network (if applicable)
3. Whether this indicates physical surveillance or stalking (if applicable)
4. Immediate safety recommendations (or "No action needed" if FP)

CRITICAL: If FP Likelihood > 50%, you MUST conclude this is likely NOT surveillance.
Common false positives include: neighbor's WiFi visible from multiple locations, coworker/family member's mobile hotspot, commuters on same route, public transit WiFi, nearby businesses.

Format as:
## Assessment
[Is this network following the user? OR why this is likely a coincidence]

## Device Analysis
[What type of device is this likely? OR "Likely benign - neighbor/commuter/family device"]

## Safety Concern
[Physical safety assessment OR "No safety concern - likely coincidental overlap"]

## Immediate Actions
1. [Action 1 - OR "No action needed"]
2. [Action 2]
3. [Action 3]"""

        return wrapGemmaPrompt(content)
    }

    /**
     * Build a prompt for satellite/NTN detection analysis with enriched data.
     *
     * The enriched data covers all 9 SatellitePattern types:
     * - UNEXPECTED_SATELLITE: hasTerrestrialCoverage, isUrbanArea, lastTerrestrialSignalDbm
     * - FORCED_HANDOFF: recentHandoffCount, timeSinceGoodTerrestrialMs
     * - SUSPICIOUS_NTN_PARAMS: orbitalType, expectedRtt, measuredRtt, ntnBand, harqProcessCount
     * - UNKNOWN_SATELLITE_NETWORK: provider identification
     * - SATELLITE_IN_COVERAGE: hasTerrestrialCoverage, lastTerrestrialSignalDbm
     * - RAPID_SATELLITE_SWITCHING: recentHandoffCount
     * - NTN_BAND_MISMATCH: isValidNtnBand, frequency, ntnBand
     * - TIMING_ANOMALY: measuredRtt, expectedRtt, orbitalType
     * - DOWNGRADE_TO_SATELLITE: detectionMethod, hasTerrestrialCoverage
     */
    fun buildSatelliteEnrichedPrompt(
        detection: Detection,
        enrichedData: EnrichedDetectorData.Satellite
    ): String {
        // Extract key context fields for targeted analysis guidance
        val provider = enrichedData.metadata["provider"] ?: "Unknown"
        val anomalyType = enrichedData.metadata["anomalyType"] ?: "Unknown"
        val hasTerrestrial = enrichedData.metadata["hasTerrestrialCoverage"] == "true"
        val isUrban = enrichedData.metadata["isUrbanArea"] == "true"
        val isValidBand = enrichedData.metadata["isValidNtnBand"] != "false"
        val measuredRtt = enrichedData.signalCharacteristics["measuredRtt"]
        val orbitalType = enrichedData.signalCharacteristics["orbitalType"]

        // Build context-specific analysis guidance based on the anomaly type
        val contextGuidance = when {
            anomalyType.contains("TIMING") || anomalyType.contains("RTT") ->
                "Focus on timing analysis: compare measured RTT against expected range for the claimed orbit type. RTT <15ms is physically impossible from space."
            anomalyType.contains("BAND") || anomalyType.contains("NRARFCN") ->
                "Focus on frequency analysis: verify whether the reported frequency/ARFCN falls within valid 3GPP NTN bands (L-band n253-n255: 1525-1660MHz, S-band n256: 1980-2200MHz)."
            anomalyType.contains("FORCED") || anomalyType.contains("HANDOFF") || anomalyType.contains("DOWNGRADE") ->
                "Focus on handoff analysis: assess whether the satellite transition was justified given terrestrial coverage availability."
            anomalyType.contains("UNKNOWN") ->
                "Focus on provider identification: unknown satellite networks are high-priority since legitimate providers are well-documented."
            anomalyType.contains("SWITCHING") || anomalyType.contains("RAPID") ->
                "Focus on switching pattern: frequent handoffs may indicate interference, jamming, or cell site simulator activity."
            hasTerrestrial && isUrban ->
                "Focus on coverage context: satellite in urban area with terrestrial coverage is unusual and warrants investigation."
            else ->
                "Provide a balanced analysis considering both legitimate NTN usage and potential surveillance indicators."
        }

        val content = """Analyze this satellite/NTN (Non-Terrestrial Network) detection for potential surveillance.

=== DETECTION INFO ===
Device Type: ${detection.deviceType.displayName}
Threat Level: ${detection.threatLevel.displayName}
Threat Score: ${detection.threatScore}
First Detected: ${formatTimestamp(detection.timestamp)}
${if (detection.latitude != null && detection.longitude != null) "Location: ${String.format("%.4f", detection.latitude)}, ${String.format("%.4f", detection.longitude)}" else "Location: Unknown"}

=== SATELLITE/NTN CHARACTERISTICS ===
Detector Type: ${enrichedData.detectorType}
${enrichedData.signalCharacteristics.entries.joinToString("\n") { "${it.key}: ${it.value}" }}

=== NTN CONTEXT ===
Provider: $provider
${if (orbitalType != null && orbitalType != "unknown") "Orbital Type: $orbitalType" else ""}
${if (measuredRtt != null && measuredRtt != "unknown") "Measured RTT: ${measuredRtt}ms" else ""}
Terrestrial Coverage Available: $hasTerrestrial
Urban Area: $isUrban
Valid NTN Band: $isValidBand

=== METADATA ===
${enrichedData.metadata.entries.joinToString("\n") { "${it.key}: ${it.value}" }}

=== RISK INDICATORS ===
${enrichedData.riskIndicators.joinToString("\n") { "- $it" }.ifEmpty { "- None identified" }}

=== ANALYSIS GUIDANCE ===
$contextGuidance

IMPORTANT CONTEXT: T-Mobile Starlink and Skylo NTN are legitimate commercial satellite services. A satellite connection from a known provider is NOT inherently suspicious. Focus on WHY the satellite connection occurred (forced handoff? good terrestrial coverage available? timing anomalies?) rather than the mere presence of satellite connectivity.

Based on this enriched satellite/NTN data, provide:
1. Assessment of whether this indicates unauthorized satellite tracking or interception
2. The likely origin and purpose of this satellite signal
3. Whether this is consistent with legitimate NTN usage or anomalous behavior
4. Risk level for the user
5. Recommended actions

Format as:
## Assessment
[Is this satellite signal indicative of surveillance?]

## Signal Analysis
[Technical analysis of the satellite characteristics]

## Legitimate vs Anomalous
[Is this consistent with normal satellite connectivity, or are there anomalies?]

## Risk Level
[Overall risk assessment]

## Recommended Actions
1. [Action 1]
2. [Action 2]
3. [Action 3]"""

        return wrapGemmaPrompt(content)
    }

    /**
     * Build an enriched prompt for BLE device detections.
     *
     * Provides detailed BLE context including device identification, signal analysis,
     * tracker following patterns, BLE spam characteristics, and false positive indicators.
     */
    fun buildBleEnrichedPrompt(
        detection: Detection,
        analysis: BleAnalysisData
    ): String {
        val fpSection = if (analysis.falsePositiveLikelihood > 30f) {
            """

=== FALSE POSITIVE ANALYSIS ===
FP Likelihood: ${String.format("%.0f", analysis.falsePositiveLikelihood)}%
Likely Consumer Device: ${if (analysis.isLikelyConsumerDevice) "YES - Common consumer BLE device" else "No"}
Likely Own Device: ${if (analysis.isLikelyOwnDevice) "YES - Appears to be user's own device" else "No"}
Passing By: ${if (analysis.isPassingBy) "YES - Weak/transient signal, likely not targeting you" else "No"}
FP Indicators:
${analysis.fpIndicators.joinToString("\n") { "- $it" }.ifEmpty { "- None" }}

IMPORTANT: This detection has a ${String.format("%.0f", analysis.falsePositiveLikelihood)}% chance of being a FALSE POSITIVE.
Consider these FP indicators when analyzing. If FP likelihood > 50%, lean toward benign explanation."""
        } else ""

        val trackerSection = if (analysis.isTrackerDevice) {
            """

=== TRACKER FOLLOWING ANALYSIS ===
Following User: ${if (analysis.isFollowingUser) "YES - Detected at ${analysis.distinctLocationCount} distinct locations" else "No"}
Sightings: ${analysis.trackerSightingCount}
Distinct Locations: ${analysis.distinctLocationCount}
Duration: ${analysis.durationMinutes} minutes
Average Signal: ${analysis.averageRssi} dBm
Signal Variance: ${String.format("%.1f", analysis.rssiVariance)}
Possession Signal: ${if (analysis.isPossessionSignal) "YES - Strong consistent signal suggests tracker is on your person or in your belongings" else "No"}
Suspicion Score: ${analysis.suspicionScore}/100
${if (analysis.suspicionReasons.isNotEmpty()) "Suspicion Indicators:\n${analysis.suspicionReasons.joinToString("\n") { "- $it" }}" else ""}"""
        } else ""

        val spamSection = if (analysis.isBleSpam) {
            """

=== BLE SPAM ATTACK ANALYSIS ===
Spam Type: ${analysis.spamType ?: "Unknown"}
Total Events: ${analysis.spamEventsCount}
Rate: ${String.format("%.1f", analysis.spamEventsPerSecond)} events/second
This indicates an active BLE spam attack, likely from a Flipper Zero or similar device."""
        } else ""

        val content = """Analyze this Bluetooth Low Energy (BLE) surveillance detection.

=== DEVICE IDENTIFICATION ===
Device Name: ${sanitize(analysis.deviceName) ?: "Unknown"}
MAC Address: ${sanitize(analysis.macAddress)}
Manufacturer: ${sanitize(analysis.manufacturer) ?: "Unknown"}
Device Category: ${analysis.deviceCategory.displayName}
Device Type: ${detection.deviceType.displayName}
Detection Method: ${detection.detectionMethod.displayName}

=== SIGNAL CHARACTERISTICS ===
RSSI: ${analysis.rssi} dBm (${detection.signalStrength.displayName})
Advertising Rate: ${String.format("%.1f", analysis.advertisingRate)} packets/sec
Connectable: ${if (analysis.isConnectable) "Yes" else "No"}
${analysis.txPowerLevel?.let { "TX Power Level: $it dBm" } ?: ""}
${analysis.estimatedDistanceMeters?.let { "Estimated Distance: ${String.format("%.1f", it)} meters" } ?: ""}

=== BLE SERVICE & MANUFACTURER DATA ===
Service UUIDs: ${if (analysis.serviceUuids.isNotEmpty()) analysis.serviceUuids.joinToString(", ") else "(none)"}
Manufacturer IDs: ${if (analysis.manufacturerIds.isNotEmpty()) analysis.manufacturerIds.joinToString(", ") { "0x${"%04X".format(it)}" } else "(none)"}
$trackerSection
$spamSection
$fpSection

Based on this enriched BLE data, provide:
1. A plain-English explanation of what this device is and what it does - OR explain why this is likely a false positive
2. Whether this device poses a genuine privacy/surveillance threat
3. The top 3 SPECIFIC actions the user should take (or "No action needed" if FP)
4. What data this device may be collecting about the user (or "No data at risk" if FP)

CRITICAL: If FP Likelihood > 50%, you MUST conclude this is likely NOT a surveillance device.
Common BLE false positives include: neighbor's smart home devices, passing pedestrians' trackers, consumer IoT devices, retail beacons in stores, fitness wearables.

Format as:
## Assessment
[Your assessment - what is this device? OR why this is likely a false positive]

## Privacy Risk
[Privacy implications - OR "Likely no privacy risk - benign BLE device"]

## Actions
1. [Action 1 - OR "No action needed"]
2. [Action 2]
3. [Action 3]

## Data Collection
[What data may be collected - OR "No data at risk - likely normal BLE device"]"""

        return wrapGemmaPrompt(content)
    }

    /** Build an enriched prompt for standard WiFi SSID/MAC pattern match detections. */
    fun buildWifiSsidEnrichedPrompt(detection: Detection, enrichedData: EnrichedDetectorData.WifiSsidMatch): String {
        val proximityAssessment = when {
            enrichedData.rssiDbm > -40 -> "VERY CLOSE: Device within 5-10 meters"
            enrichedData.rssiDbm > -50 -> "CLOSE: Device within 10-25 meters"
            enrichedData.rssiDbm > -65 -> "MODERATE: Device within 25-50 meters"
            enrichedData.rssiDbm > -80 -> "FAR: Device approximately 50-100 meters away"
            else -> "VERY FAR: Device more than 100 meters away"
        }
        val envNote = when {
            enrichedData.nearbyApCount > 30 -> "Dense WiFi environment (${enrichedData.nearbyApCount} APs)"
            enrichedData.nearbyApCount < 5 -> "Sparse WiFi environment (${enrichedData.nearbyApCount} APs)"
            else -> "Normal WiFi density (${enrichedData.nearbyApCount} APs)"
        }
        return wrapGemmaPrompt("""Analyze this WiFi surveillance device detection matched via ${enrichedData.detectionMethodName}.

=== DETECTION INFO ===
Device Type: ${detection.deviceType.displayName}
Threat Level: ${detection.threatLevel.displayName}
Threat Score: ${detection.threatScore}/100

=== NETWORK CHARACTERISTICS ===
SSID: ${enrichedData.ssid.ifEmpty { "(Hidden Network)" }}
BSSID (MAC): ${enrichedData.bssid}
Channel: ${enrichedData.channel} (${enrichedData.frequencyMhz} MHz, ${enrichedData.frequencyBand})
Security/Capabilities: ${enrichedData.capabilities}
Signal Strength: ${enrichedData.rssiDbm} dBm
Hidden Network: ${if (enrichedData.isHidden) "YES" else "No"}

=== IDENTIFICATION ===
OUI Vendor: ${enrichedData.ouiVendor ?: "Unknown"}
Pattern Manufacturer: ${enrichedData.patternManufacturer ?: "Unknown"}
Pattern Match: ${enrichedData.matchedPatternDescription}

=== ENVIRONMENT ===
$envNote
Proximity: $proximityAssessment

Provide:
1. Assessment of what this device is and its surveillance capabilities
2. Whether the signal strength and environment suggest this is nearby or distant
3. Privacy implications specific to this device type
4. Recommended actions for the user""")
    }

    /**
     * Build an enriched prompt for RF environment anomaly detections.
     *
     * Most RF detections are inferred from WiFi scan data rather than true RF
     * spectrum analysis (which requires SDR hardware like Flipper Zero). The
     * prompt communicates this limitation to the LLM.
     */
    fun buildRfEnvironmentEnrichedPrompt(
        detection: Detection,
        enrichedData: EnrichedDetectorData.RfEnvironment
    ): String {
        val sb = StringBuilder()
        sb.appendLine("Analyze this RF environment anomaly.")
        sb.appendLine()
        sb.appendLine("=== RF ANOMALY ===")
        sb.appendLine("Type: ${enrichedData.anomalyType}")
        sb.appendLine("Confidence: ${enrichedData.anomalyConfidence}")
        sb.appendLine("Score: ${enrichedData.rfThreatScore}/100")
        sb.appendLine("Method: ${if (enrichedData.isWifiProxyBased) "WiFi-proxy (LIMITED)" else "Direct RF"}")
        sb.appendLine("Networks: ${enrichedData.totalNetworks} total, ${enrichedData.hiddenNetworkCount} hidden")
        sb.appendLine("Signal: ${enrichedData.avgSignalStrength} dBm, variance ${String.format("%.1f", enrichedData.signalVariance)}")

        enrichedData.hiddenNetworkAnalysis?.let { a ->
            sb.appendLine()
            sb.appendLine("=== HIDDEN NETWORK ANALYSIS ===")
            sb.appendLine("Hidden vs Visible: ${a.hiddenAvgSignalStrength} dBm vs ${a.visibleAvgSignalStrength} dBm")
            sb.appendLine("Hidden Stronger: ${if (a.hiddenSignalStrongerThanVisible) "YES" else "No"}")
            sb.appendLine("Variance: ${String.format("%.1f", a.hiddenSignalVariance)}${if (a.hiddenSignalVariance < 50f) " (LOW - same hardware)" else ""}")
            sb.appendLine("Surveillance OUIs: ${a.knownSurveillanceOuiCount}")
            sb.appendLine("Channel Concentration: ${if (a.channelConcentration) "YES" else "No"}")
            sb.appendLine("Simultaneous Appearance: ${if (a.simultaneousAppearance) "YES" else "No"}")
        }

        enrichedData.environmentStatus?.let { s ->
            sb.appendLine()
            sb.appendLine("=== ENVIRONMENT ===")
            sb.appendLine("Noise: ${s.noiseLevel.displayName}, Jammer: ${if (s.jammerSuspected) "YES" else "No"}")
            sb.appendLine("Drones: ${s.dronesDetected}, Cameras: ${s.surveillanceCameras}")
            sb.appendLine("Risk: ${s.environmentRisk.displayName}")
        }

        sb.appendLine()
        sb.appendLine("=== FACTORS ===")
        if (enrichedData.contributingFactors.isNotEmpty()) {
            enrichedData.contributingFactors.forEach { sb.appendLine("- $it") }
        } else {
            sb.appendLine("- None")
        }

        if (enrichedData.falsePositiveLikelihood > 30f) {
            sb.appendLine()
            sb.appendLine("=== FALSE POSITIVE ANALYSIS ===")
            sb.appendLine("FP Likelihood: ${String.format("%.0f", enrichedData.falsePositiveLikelihood)}%")
            if (enrichedData.fpIndicators.isNotEmpty()) {
                enrichedData.fpIndicators.forEach { sb.appendLine("- $it") }
            }
            sb.appendLine("IMPORTANT: ${String.format("%.0f", enrichedData.falsePositiveLikelihood)}% chance of FALSE POSITIVE.")
            if (enrichedData.isWifiProxyBased) {
                sb.appendLine("Based on WiFi data, not direct RF measurement.")
            }
        }

        if (enrichedData.isWifiProxyBased) {
            sb.appendLine()
            sb.appendLine("NOTE: WiFi proxy cannot detect true RF jamming, sub-GHz signals, or perform spectrum analysis without SDR hardware.")
        }

        sb.appendLine()
        sb.appendLine("Provide:")
        sb.appendLine("1. Is this genuine surveillance or false positive?")
        sb.appendLine("2. Privacy impact")
        sb.appendLine("3. Recommended actions")
        sb.appendLine()
        sb.appendLine("Format as:")
        sb.appendLine("## Assessment")
        sb.appendLine("[Analysis or why likely normal]")
        sb.appendLine()
        sb.appendLine("## Actions")
        sb.appendLine("1. [Action or \"No action needed\"]")
        sb.appendLine("2. [Action 2]")
        sb.appendLine("3. [Action 3]")

        return wrapGemmaPrompt(sb.toString())
    }
}
