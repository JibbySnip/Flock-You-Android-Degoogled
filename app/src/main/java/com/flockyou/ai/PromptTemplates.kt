package com.flockyou.ai

import com.flockyou.data.model.Detection
import com.flockyou.monitoring.GnssSatelliteMonitor.GnssAnomalyAnalysis
import com.flockyou.service.CellularMonitor.CellularAnomalyAnalysis
import com.flockyou.service.RogueWifiMonitor.FollowingNetworkAnalysis
import com.flockyou.service.RfSignalAnalyzer.HiddenNetworkAnalysis
import com.flockyou.service.RfSignalAnalyzer.RfEnvironmentStatus
import com.flockyou.service.UltrasonicDetector.BeaconAnalysis

/**
 * Centralized prompt templates for LLM-based analysis.
 *
 * Provides multiple prompt strategies:
 * - Chain-of-thought: For complex multi-step reasoning
 * - Few-shot: With examples for consistent output format
 * - Structured output: JSON-formatted responses for parsing
 * - Enriched prompts: Leveraging detector-specific analysis data (see [EnrichedPromptTemplates])
 * - Batch/pattern prompts: Multi-detection analysis (see [BatchPromptTemplates])
 * - Enterprise descriptions: Rule-based detection descriptions (see [DescriptionGenerator])
 */
object PromptTemplates {

    // ==================== CHAIN OF THOUGHT PROMPTS ====================

    /**
     * Build a chain-of-thought prompt for complex analysis.
     * Uses step-by-step reasoning to analyze surveillance detections.
     */
    fun buildChainOfThoughtPrompt(
        detection: Detection,
        enrichedData: EnrichedDetectorData? = null
    ): String {
        val enrichedSection = enrichedData?.let { buildEnrichedDataSection(it) } ?: ""

        val content = """You are a privacy security expert analyzing a detected surveillance device.

Think through this step-by-step:

STEP 1 - Identify the Device:
What type of surveillance device is this? What is its primary purpose?

STEP 2 - Assess Capabilities:
What data can this device collect? How does it operate?

STEP 3 - Evaluate Risk:
Given the device type and signal strength, what is the actual privacy risk?

STEP 4 - Consider Context:
Based on the detection method and any enriched data, is this detection reliable?

STEP 5 - Recommend Actions:
What specific steps should the user take?

=== DETECTION DATA ===
Device Type: ${detection.deviceType.displayName}
Protocol: ${detection.protocol.displayName}
Detection Method: ${detection.detectionMethod.displayName}
Signal: ${detection.signalStrength.displayName} (${detection.rssi} dBm)
Threat Level: ${detection.threatLevel.displayName}
Threat Score: ${detection.threatScore}/100
${sanitize(detection.manufacturer)?.takeIf { it.isNotEmpty() }?.let { "Manufacturer: $it" } ?: ""}
${sanitize(detection.deviceName)?.takeIf { it.isNotEmpty() }?.let { "Device Name: $it" } ?: ""}
${sanitize(detection.ssid)?.takeIf { it.isNotEmpty() }?.let { "Network SSID: $it" } ?: ""}
${sanitize(detection.matchedPatterns)?.takeIf { it.isNotEmpty() }?.let { "Matched Patterns: $it" } ?: ""}
$enrichedSection

Provide your analysis following these steps."""

        return wrapGemmaPrompt(content)
    }

    // ==================== FEW-SHOT PROMPTS ====================

    /**
     * Build a few-shot prompt with examples for consistent output format.
     */
    fun buildFewShotPrompt(
        detection: Detection,
        enrichedData: EnrichedDetectorData? = null
    ): String {
        val enrichedSection = enrichedData?.let { buildEnrichedDataSection(it) } ?: ""

        val content = """You are a surveillance detection expert. Analyze detected devices and provide clear, actionable guidance.

=== EXAMPLE 1 ===
Device: Flock Safety ALPR Camera
Signal: Good (-55 dBm)
Threat: HIGH

Analysis:
## Flock Safety ALPR Camera

**What It Does:** This is a license plate recognition camera that photographs every vehicle passing by. It records your plate number, vehicle make/model, color, and timestamp.

**Data Collected:**
- License plate numbers
- Vehicle descriptions
- Time and location of each pass
- Direction of travel

**Privacy Risk:** HIGH - Your movements are being logged in a database that may be shared with law enforcement and retained for years.

**Actions:**
1. Be aware this intersection is monitored
2. Consider alternate routes if privacy is a concern
3. Check if your city has an ALPR policy you can review

=== EXAMPLE 2 ===
Device: IMSI Catcher (Cell Site Simulator)
Signal: Strong (-45 dBm)
Threat: CRITICAL
Enriched: Encryption downgrade 5G→2G, IMSI Score 78%

Analysis:
## IMSI Catcher Detection

**What It Does:** This is a cell site simulator (StingRay) that forces your phone to connect to it instead of a real tower. It can intercept calls, texts, and track your location.

**Data Collected:**
- Phone IMSI/IMEI identifiers
- Call and text metadata
- Real-time location
- Potentially call/text content (on 2G)

**Privacy Risk:** CRITICAL - Your phone has been forced to 2G with weak encryption. Communications may be intercepted.

**Actions:**
1. IMMEDIATELY enable airplane mode
2. Leave this area if possible
3. Use end-to-end encrypted apps only (Signal, WhatsApp)
4. Report to EFF or ACLU if you suspect targeting

=== YOUR ANALYSIS ===
Device: ${detection.deviceType.displayName}
Signal: ${detection.signalStrength.displayName} (${detection.rssi} dBm)
Threat: ${detection.threatLevel.displayName}
${enrichedSection}

Provide your analysis in the same format as the examples above."""

        return wrapGemmaPrompt(content)
    }

    // ==================== STRUCTURED OUTPUT PROMPTS ====================

    /**
     * Build a prompt requesting enterprise-grade JSON-structured output for parsing.
     * This prompt requests comprehensive, actionable intelligence including:
     * - False positive assessment
     * - Severity-aligned messaging
     * - User-friendly and technical explanations
     * - Contextual analysis
     */
    fun buildStructuredOutputPrompt(
        detection: Detection,
        enrichedData: EnrichedDetectorData? = null
    ): String {
        val enrichedSection = enrichedData?.let { buildEnrichedDataSection(it) } ?: ""

        // Calculate estimated false positive likelihood for context
        val estimatedFpLikelihood = estimateFalsePositiveLikelihood(detection, enrichedData)

        val content = """Analyze this surveillance detection and respond in JSON format only.
IMPORTANT: Your analysis must be calibrated to the actual threat level and false positive likelihood.
- If false_positive_likelihood > 50%, treat this as LIKELY BENIGN and use reassuring language
- Match your headline and urgency to the actual threat - don't be alarmist for low-threat detections
- For LOW/INFO threat levels, use phrases like "minor", "normal", "routine", NOT "suspicious" or "detected"

Detection:
- Type: ${detection.deviceType.displayName}
- Protocol: ${detection.protocol.displayName}
- Method: ${detection.detectionMethod.displayName}
- Signal: ${detection.rssi} dBm (${detection.signalStrength.displayName})
- Threat Level: ${detection.threatLevel.displayName}
- Threat Score: ${detection.threatScore}/100
- Times Seen: ${detection.seenCount}
${sanitize(detection.manufacturer)?.takeIf { it.isNotEmpty() }?.let { "- Manufacturer: $it" } ?: ""}
${sanitize(detection.deviceName)?.takeIf { it.isNotEmpty() }?.let { "- Name: $it" } ?: ""}
$enrichedSection

Estimated False Positive Likelihood: ${estimatedFpLikelihood}%
${if (estimatedFpLikelihood > 50) "NOTE: This detection is MORE LIKELY to be a false alarm than a real threat." else ""}

Respond with this exact JSON structure:
{
  "headline": "5-10 word summary matching severity (reassuring if likely FP, urgent only if CRITICAL)",
  "threat_level_assessment": "${detection.threatLevel.displayName}",

  "what_detected": {
    "device_summary": "What this device is in 1 sentence",
    "device_purpose": "What it does in 1-2 sentences",
    "data_collection": "What data it can collect"
  },

  "why_flagged": {
    "trigger_indicators": ["specific indicator 1", "specific indicator 2"],
    "threat_reasoning": "Why this threat level was assigned"
  },

  "confidence_assessment": {
    "confidence_score": 0-100,
    "confidence_reasoning": "Why this confidence level",
    "false_positive_likelihood": 0-100,
    "false_positive_reasons": ["reason 1", "reason 2"],
    "is_most_likely_benign": true/false,
    "benign_explanation": "If likely benign, explain why (null if not benign)"
  },

  "actionable_intelligence": {
    "immediate_action": {
      "action": "What to do NOW (or null if no action needed)",
      "urgency": "IMMEDIATE|SOON|WHEN_CONVENIENT|FOR_AWARENESS|NONE",
      "reason": "Why this action"
    },
    "monitoring_recommendation": "How to monitor going forward",
    "documentation_suggestion": "Whether to document this (null if not needed)"
  },

  "explanations": {
    "simple_explanation": "Non-technical explanation for regular users using everyday analogies",
    "technical_details": "Technical details for advanced users"
  }
}

CRITICAL RULES:
1. If false_positive_likelihood > 50, set is_most_likely_benign to true and provide benign_explanation
2. If threat_level is LOW or INFO, use calm language - NOT "suspicious" or "detected threat"
3. Match immediate_action urgency to actual threat - only use IMMEDIATE for CRITICAL threats
4. Be specific in trigger_indicators - don't say "suspicious patterns" without details

JSON response:"""

        return wrapGemmaPrompt(content)
    }

    // ==================== ENRICHED DETECTOR-SPECIFIC PROMPT DELEGATES ====================

    /**
     * Build an enriched prompt for cellular/IMSI catcher detections.
     * Delegates to [EnrichedPromptTemplates].
     */
    fun buildCellularEnrichedPrompt(
        detection: Detection,
        analysis: CellularAnomalyAnalysis
    ): String = EnrichedPromptTemplates.buildCellularEnrichedPrompt(detection, analysis)

    /**
     * Build an enriched prompt for GNSS spoofing/jamming detections.
     * Delegates to [EnrichedPromptTemplates].
     */
    fun buildGnssEnrichedPrompt(
        detection: Detection,
        analysis: GnssAnomalyAnalysis
    ): String = EnrichedPromptTemplates.buildGnssEnrichedPrompt(detection, analysis)

    /**
     * Build an enriched prompt for ultrasonic beacon detections.
     * Delegates to [EnrichedPromptTemplates].
     */
    fun buildUltrasonicEnrichedPrompt(
        detection: Detection,
        analysis: BeaconAnalysis
    ): String = EnrichedPromptTemplates.buildUltrasonicEnrichedPrompt(detection, analysis)

    /**
     * Build an enriched prompt for following WiFi network detections.
     * Delegates to [EnrichedPromptTemplates].
     */
    fun buildWifiFollowingEnrichedPrompt(
        detection: Detection,
        analysis: FollowingNetworkAnalysis
    ): String = EnrichedPromptTemplates.buildWifiFollowingEnrichedPrompt(detection, analysis)

    /**
     * Build a prompt for satellite/NTN detection analysis with enriched data.
     * Delegates to [EnrichedPromptTemplates].
     */
    fun buildSatelliteEnrichedPrompt(
        detection: Detection,
        enrichedData: EnrichedDetectorData.Satellite
    ): String = EnrichedPromptTemplates.buildSatelliteEnrichedPrompt(detection, enrichedData)

    /**
     * Build an enriched prompt for BLE device detections.
     * Delegates to [EnrichedPromptTemplates].
     */
    fun buildBleEnrichedPrompt(
        detection: Detection,
        analysis: BleAnalysisData
    ): String = EnrichedPromptTemplates.buildBleEnrichedPrompt(detection, analysis)

    /** Build an enriched prompt for standard WiFi SSID/MAC pattern match detections.
     * Delegates to [EnrichedPromptTemplates].
     */
    fun buildWifiSsidEnrichedPrompt(
        detection: Detection,
        enrichedData: EnrichedDetectorData.WifiSsidMatch
    ): String = EnrichedPromptTemplates.buildWifiSsidEnrichedPrompt(detection, enrichedData)

    /**
     * Build an enriched prompt for RF environment anomaly detections.
     * Delegates to [EnrichedPromptTemplates].
     */
    fun buildRfEnvironmentEnrichedPrompt(
        detection: Detection,
        enrichedData: EnrichedDetectorData.RfEnvironment
    ): String = EnrichedPromptTemplates.buildRfEnvironmentEnrichedPrompt(detection, enrichedData)

    // ==================== USER-FRIENDLY EXPLANATION PROMPTS ====================

    /**
     * User explanation levels
     */
    enum class ExplanationLevel(val description: String) {
        SIMPLE("For users with no technical knowledge"),
        STANDARD("For general audience"),
        TECHNICAL("For tech-savvy users")
    }

    /**
     * Build a prompt for user-friendly explanations at different technical levels.
     */
    fun buildUserFriendlyPrompt(
        detection: Detection,
        enrichedData: EnrichedDetectorData? = null,
        level: ExplanationLevel = ExplanationLevel.STANDARD
    ): String {
        val enrichedSection = enrichedData?.let { buildEnrichedDataSection(it) } ?: ""

        val levelInstructions = when (level) {
            ExplanationLevel.SIMPLE -> """
Write for someone with NO technical knowledge.
- Use simple, everyday words only
- Short sentences (max 15 words each)
- Use relatable analogies (like mail being read, being followed, etc.)
- NO technical terms at all
- Explain like you're talking to your grandmother"""

            ExplanationLevel.STANDARD -> """
Write for a general adult audience.
- Clear, accessible language
- Brief technical terms OK if explained
- Balance detail with clarity
- Focus on practical implications"""

            ExplanationLevel.TECHNICAL -> """
Write for a tech-savvy user.
- Include technical details
- Reference specific protocols/standards
- Explain attack vectors and mechanisms
- Provide detailed countermeasures"""
        }

        // Sanitize device name if we need to display it
        val sanitizedDeviceName = sanitize(detection.deviceName)

        val content = """You are explaining a surveillance detection to a user.

$levelInstructions

=== DETECTION ===
Device: ${detection.deviceType.displayName}${if (sanitizedDeviceName.isNotEmpty()) " ($sanitizedDeviceName)" else ""}
Type: ${detection.protocol.displayName}
Threat: ${detection.threatLevel.displayName} (${detection.threatScore}/100)
Signal: ${detection.signalStrength.displayName}
${enrichedSection}

Provide:
1. HEADLINE: Maximum 5 words summarizing the situation
2. WHAT'S HAPPENING: ${if (level == ExplanationLevel.SIMPLE) "2 short sentences" else "2-3 sentences"}
3. WHY IT MATTERS: ${if (level == ExplanationLevel.SIMPLE) "1 sentence using an analogy" else "Privacy/safety implications"}
4. WHAT TO DO: 3 numbered actions, ${if (level == ExplanationLevel.SIMPLE) "very simple steps" else "specific steps"}
5. URGENCY: LOW, MEDIUM, HIGH, or IMMEDIATE

Format exactly as:
HEADLINE: [5 words max]

WHAT'S HAPPENING:
[explanation]

WHY IT MATTERS:
[implications]

WHAT TO DO:
1. [action]
2. [action]
3. [action]

URGENCY: [level]"""

        return wrapGemmaPrompt(content)
    }

    // ==================== HELPER FUNCTIONS ====================

    /**
     * Build the enriched data section based on what type of data is available.
     */
    internal fun buildEnrichedDataSection(data: EnrichedDetectorData): String {
        return when (data) {
            is EnrichedDetectorData.Cellular -> {
                val a = data.analysis
                """
=== ENRICHED CELLULAR DATA ===
IMSI Catcher Score: ${a.imsiCatcherScore}%
Encryption: ${a.currentEncryption.displayName}
Movement: ${a.movementType.displayName} (${String.format("%.0f", a.speedKmh)} km/h)
Cell Trust: ${a.cellTrustScore}%
${if (a.impossibleSpeed) "WARNING: IMPOSSIBLE MOVEMENT DETECTED" else ""}
${if (a.encryptionDowngraded) "WARNING: ENCRYPTION DOWNGRADED" else ""}
${if (a.falsePositiveLikelihood > 30f) "WARNING: FP LIKELIHOOD: ${String.format("%.0f", a.falsePositiveLikelihood)}% - MAY BE NORMAL HANDOFF" else ""}
${if (a.isLikelyNormalHandoff) "WARNING: LIKELY NORMAL CELL HANDOFF" else ""}
${if (a.isLikely5gBeamSteering) "WARNING: LIKELY 5G BEAM STEERING" else ""}"""
            }
            is EnrichedDetectorData.Gnss -> {
                val a = data.analysis
                """
=== ENRICHED GNSS DATA ===
Spoofing Likelihood: ${String.format("%.0f", a.spoofingLikelihood)}%
Jamming Likelihood: ${String.format("%.0f", a.jammingLikelihood)}%
Geometry Score: ${String.format("%.0f", a.geometryScore * 100)}%
C/N0: ${String.format("%.1f", a.currentCn0Mean)} dB-Hz
${if (a.cn0TooUniform) "WARNING: SIGNAL UNIFORMITY SUSPICIOUS" else ""}
${if (a.lowElevHighSignalCount > 0) "WARNING: ${a.lowElevHighSignalCount} LOW-ELEV HIGH-SIGNAL SATELLITES" else ""}
${if (a.falsePositiveLikelihood > 30f) "WARNING: FP LIKELIHOOD: ${String.format("%.0f", a.falsePositiveLikelihood)}% - MAY BE NORMAL GPS" else ""}
${if (a.isLikelyUrbanMultipath) "WARNING: LIKELY URBAN MULTIPATH (building reflections)" else ""}
${if (a.isLikelyIndoorSignalLoss) "WARNING: LIKELY INDOOR SIGNAL ATTENUATION" else ""}"""
            }
            is EnrichedDetectorData.Ultrasonic -> {
                val a = data.analysis
                """
=== ENRICHED ULTRASONIC DATA ===
Beacon Type: ${a.matchedSource.displayName}
Category: ${a.sourceCategory.displayName}
Frequency: ${a.frequencyHz} Hz
Amplitude: ${String.format("%.1f", a.peakAmplitudeDb)} dB peak, ${String.format("%.1f", a.avgAmplitudeDb)} dB avg
SNR: ${String.format("%.1f", a.snrDb)} dB
Amplitude Profile: ${a.amplitudeProfile.displayName}
Frequency Stable: ${if (a.isFrequencyStable) "YES" else "No (${String.format("%.1f", a.frequencyStabilityHz)}Hz drift)"}
${if (a.hasKnownModulationPattern) "Modulation: ${a.modulationPatternType}" else ""}
Environment: ${a.environmentalContext.displayName}
Cross-Location: ${if (a.followingUser) "YES - ${a.locationsDetected} locations" else "No"}
${if (a.isFollowingAcrossLocations) "WARNING: FOLLOWING ACROSS HOME + OTHER LOCATIONS" else ""}
Tracking Likelihood: ${String.format("%.0f", a.trackingLikelihood)}%
Persistence: ${String.format("%.0f", a.persistenceScore * 100)}%
Duration: ${a.detectionDurationMs / 1000}s, ${a.totalDetectionCount} detections
${if (a.falsePositiveLikelihood > 30f) "WARNING: FP LIKELIHOOD: ${String.format("%.0f", a.falsePositiveLikelihood)}% - MAY BE NOISE" else ""}
${if (a.isLikelyAmbientNoise) "WARNING: LIKELY AMBIENT NOISE (${a.concurrentBeaconCount} concurrent beacons)" else ""}
${if (a.isLikelyDeviceArtifact) "WARNING: LIKELY DEVICE ARTIFACT" else ""}"""
            }
            is EnrichedDetectorData.WifiFollowing -> {
                val a = data.analysis
                """
=== ENRICHED WIFI FOLLOWING DATA ===
Following Confidence: ${String.format("%.0f", a.followingConfidence)}%
Sightings: ${a.sightingCount} times at ${a.distinctLocations} locations
Path Correlation: ${String.format("%.0f", a.pathCorrelation * 100)}%
${if (a.vehicleMounted) "WARNING: VEHICLE MOUNTED DEVICE" else ""}
${if (a.possibleFootSurveillance) "WARNING: POSSIBLE FOOT SURVEILLANCE" else ""}
${if (a.leadsUser) "WARNING: NETWORK LEADS USER (arrives before you)" else ""}
${if (a.falsePositiveLikelihood > 30f) "WARNING: FP LIKELIHOOD: ${String.format("%.0f", a.falsePositiveLikelihood)}% - MAY BE COINCIDENCE" else ""}
${if (a.isLikelyNeighborNetwork) "WARNING: LIKELY NEIGHBOR/BUSINESS WIFI" else ""}
${if (a.isLikelyCommuterDevice) "WARNING: LIKELY COMMUTER ON SAME ROUTE" else ""}"""
            }
            is EnrichedDetectorData.Satellite -> {
                """
=== ENRICHED SATELLITE/NTN DATA ===
Detector Type: ${data.detectorType}
Timestamp: ${formatTimestamp(data.timestamp)}
Provider: ${data.metadata["provider"] ?: "Unknown"}
Anomaly: ${data.metadata["anomalyType"] ?: "Unknown"} (${data.metadata["anomalySeverity"] ?: "Unknown"})
${data.metadata["satelliteId"]?.let { "Satellite ID: $it" } ?: ""}
${data.signalCharacteristics.entries.joinToString("\n") { "${it.key}: ${it.value}" }}
${data.metadata["hasTerrestrialCoverage"]?.let { "Terrestrial Coverage: $it" } ?: ""}
${data.metadata["isUrbanArea"]?.let { "Urban Area: $it" } ?: ""}
${data.metadata["recentHandoffCount"]?.let { "Recent Handoffs: $it" } ?: ""}
${if (data.riskIndicators.isNotEmpty()) "WARNING: RISK INDICATORS: ${data.riskIndicators.joinToString(", ")}" else ""}
NOTE: T-Mobile Starlink and Skylo are legitimate NTN services
${data.metadata.entries.joinToString("\n") { "${it.key}: ${it.value}" }}"""
            }
            is EnrichedDetectorData.RfEnvironment -> {
                val hiddenAnalysis = data.hiddenNetworkAnalysis
                val envStatus = data.environmentStatus
                """
=== ENRICHED RF ENVIRONMENT DATA ===
Anomaly Type: ${data.anomalyType}
Confidence: ${data.anomalyConfidence}
Description: ${data.anomalyDescription}
RF Threat Score: ${data.rfThreatScore}/100
Detection Method: ${if (data.isWifiProxyBased) "WiFi-proxy inference (not true RF measurement)" else "Direct RF measurement"}
Total Networks: ${data.totalNetworks}
Hidden Networks: ${data.hiddenNetworkCount} (${if (data.totalNetworks > 0) String.format("%.0f", data.hiddenNetworkCount.toFloat() / data.totalNetworks * 100) else "0"}%)
Avg Signal: ${data.avgSignalStrength} dBm
Signal Variance: ${String.format("%.1f", data.signalVariance)}
${if (data.suspiciousPatterns.isNotEmpty()) "Suspicious Patterns: ${data.suspiciousPatterns.joinToString(", ")}" else ""}
${if (hiddenAnalysis != null) """Hidden Network Analysis:
  Hidden vs Visible Signal: ${hiddenAnalysis.hiddenAvgSignalStrength} dBm vs ${hiddenAnalysis.visibleAvgSignalStrength} dBm
  ${if (hiddenAnalysis.hiddenSignalStrongerThanVisible) "WARNING: HIDDEN SIGNALS STRONGER THAN VISIBLE" else ""}
  Signal Variance: ${String.format("%.1f", hiddenAnalysis.hiddenSignalVariance)} (${if (hiddenAnalysis.hiddenSignalVariance < 50f) "LOW - same hardware likely" else "normal"})
  ${if (hiddenAnalysis.channelConcentration) "WARNING: CHANNEL CONCENTRATION DETECTED" else ""}
  Surveillance Vendor OUIs: ${hiddenAnalysis.knownSurveillanceOuiCount}
  ${if (hiddenAnalysis.simultaneousAppearance) "WARNING: SIMULTANEOUS APPEARANCE OF HIDDEN NETWORKS" else ""}""" else ""}
${if (envStatus != null) """Environment Status:
  Noise Level: ${envStatus.noiseLevel.displayName}
  Jammer Suspected: ${envStatus.jammerSuspected}
  Drones Detected: ${envStatus.dronesDetected}
  Surveillance Cameras: ${envStatus.surveillanceCameras}
  Environment Risk: ${envStatus.environmentRisk.displayName}""" else ""}
${if (data.falsePositiveLikelihood > 30f) "FP LIKELIHOOD: ${String.format("%.0f", data.falsePositiveLikelihood)}% - MAY BE NORMAL RF ENVIRONMENT" else ""}
${if (data.fpIndicators.isNotEmpty()) "FP Indicators: ${data.fpIndicators.joinToString(", ")}" else ""}
Contributing Factors:
${data.contributingFactors.joinToString("\n") { "- $it" }.ifEmpty { "- None" }}"""
            }
            is EnrichedDetectorData.Ble -> {
                val a = data.analysis
                """
=== ENRICHED BLE DATA ===
Device: ${a.deviceName ?: "Unknown"} (${a.deviceCategory.displayName})
MAC: ${a.macAddress}
Manufacturer: ${a.manufacturer ?: "Unknown"}
Signal: ${a.rssi} dBm${a.estimatedDistanceMeters?.let { " (~${String.format("%.1f", it)}m)" } ?: ""}
Advertising Rate: ${String.format("%.1f", a.advertisingRate)} pps
Connectable: ${a.isConnectable}
${if (a.isTrackerDevice && a.isFollowingUser) "FOLLOWING: Detected at ${a.distinctLocationCount} locations over ${a.durationMinutes} min (suspicion: ${a.suspicionScore}/100)" else ""}
${if (a.isTrackerDevice && a.isPossessionSignal) "POSSESSION: Strong consistent signal suggests tracker on person/in belongings" else ""}
${if (a.isBleSpam) "BLE SPAM: ${a.spamType} attack - ${a.spamEventsCount} events at ${String.format("%.1f", a.spamEventsPerSecond)}/sec" else ""}
${if (a.falsePositiveLikelihood > 30f) "FP LIKELIHOOD: ${String.format("%.0f", a.falsePositiveLikelihood)}% - ${if (a.isLikelyConsumerDevice) "LIKELY CONSUMER DEVICE" else if (a.isPassingBy) "LIKELY PASSING BY" else if (a.isLikelyOwnDevice) "LIKELY OWN DEVICE" else "MAY BE BENIGN"}" else ""}"""
            }
            is EnrichedDetectorData.WifiSsidMatch -> {
                """
=== ENRICHED WIFI SSID MATCH DATA ===
SSID: ${data.ssid.ifEmpty { "(Hidden)" }}
BSSID: ${data.bssid}
Channel: ${data.channel} (${data.frequencyMhz} MHz, ${data.frequencyBand})
Security: ${data.capabilities}
Signal: ${data.rssiDbm} dBm
Hidden Network: ${if (data.isHidden) "YES" else "No"}
OUI Vendor: ${data.ouiVendor ?: "Unknown"}
Detection Method: ${data.detectionMethodName}
Pattern Match: ${data.matchedPatternDescription}
Pattern Manufacturer: ${data.patternManufacturer ?: "Unknown"}
Nearby AP Count: ${data.nearbyApCount} (environment density)"""
            }
        }
    }

    /**
     * Estimate false positive likelihood based on detection data and enriched data.
     * Delegates to [DescriptionGenerator] for the shared implementation.
     */
    fun estimateFalsePositiveLikelihood(
        detection: Detection,
        enrichedData: EnrichedDetectorData?
    ): Int = DescriptionGenerator.estimateFalsePositiveLikelihood(detection, enrichedData)

    // ==================== PATTERN RECOGNITION PROMPT DELEGATES ====================

    /**
     * Build a prompt to analyze patterns across multiple detections.
     * Delegates to [BatchPromptTemplates].
     */
    fun buildPatternRecognitionPrompt(
        detections: List<Detection>,
        timeWindowDescription: String
    ): String = BatchPromptTemplates.buildPatternRecognitionPrompt(detections, timeWindowDescription)

    // ==================== ENTERPRISE DETECTION DESCRIPTION TYPES ====================

    /**
     * Enterprise-grade detection description that provides comprehensive, actionable intelligence.
     * Every description answers: WHAT, WHY, HOW CONFIDENT, WHAT TO DO, and CONTEXT.
     */
    data class EnterpriseDetectionDescription(
        // Core identification
        val headline: String,                    // 5-10 word summary
        val threatLevel: String,                 // CRITICAL, HIGH, MEDIUM, LOW, INFO

        // WHAT was detected
        val deviceSummary: String,               // What this device is
        val devicePurpose: String,               // What it does
        val dataCollectionSummary: String,       // What data it can collect

        // WHY it's flagged
        val triggerIndicators: List<String>,     // Specific indicators that triggered detection
        val threatReasoning: String,             // Why this threat level was assigned

        // HOW CONFIDENT
        val confidenceScore: Int,                // 0-100
        val confidenceReasoning: String,         // Why this confidence level

        // FALSE POSITIVE assessment
        val falsePositiveLikelihood: Int,        // 0-100
        val falsePositiveReasons: List<String>,  // Why this might be a false alarm
        val isMostLikelyBenign: Boolean,         // If FP > 50%, this is true
        val benignExplanation: String?,          // If likely benign, explain why

        // CONTEXT
        val isNormalForLocation: Boolean?,       // Is this expected at this location?
        val isRecurring: Boolean,                // Seen before?
        val correlatedDetections: List<String>,  // Other detections that correlate
        val environmentalContext: String?,       // Near gov building, protest, etc.

        // WHAT TO DO - actionable intelligence
        val immediateAction: ActionItem?,        // Do this NOW if needed
        val monitoringRecommendation: String,    // How to monitor going forward
        val documentationSuggestion: String?,    // Screenshot, report, etc.
        val additionalResources: List<String>,   // Links/resources for more info

        // User-friendly versions
        val simpleExplanation: String,           // Non-technical explanation
        val technicalDetails: String             // For advanced users
    )

    /**
     * Actionable item with urgency level
     */
    data class ActionItem(
        val action: String,
        val urgency: ActionUrgency,
        val reason: String
    )

    enum class ActionUrgency {
        IMMEDIATE,   // Do this right now
        SOON,        // Within the next hour
        WHEN_CONVENIENT,  // When you have time
        FOR_AWARENESS     // Just know about this
    }

    /**
     * Generate enterprise-grade detection description based on detection data and analysis.
     * Delegates to [DescriptionGenerator].
     */
    fun generateEnterpriseDescription(
        detection: Detection,
        enrichedData: EnrichedDetectorData? = null,
        contextualInsights: ContextualInsights? = null,
        falsePositiveResult: FalsePositiveAnalysisResult? = null
    ): EnterpriseDetectionDescription = DescriptionGenerator.generateEnterpriseDescription(
        detection, enrichedData, contextualInsights, falsePositiveResult
    )

    /**
     * Format the enterprise description as a user-facing string.
     * Delegates to [DescriptionGenerator].
     */
    fun formatEnterpriseDescriptionForUser(desc: EnterpriseDetectionDescription): String =
        DescriptionGenerator.formatEnterpriseDescriptionForUser(desc)

    /**
     * Data class for contextual insights used in enterprise descriptions.
     */
    data class ContextualInsights(
        val isKnownLocation: Boolean,
        val locationPattern: String?,
        val timePattern: String?,
        val clusterInfo: String?,
        val historicalContext: String?
    )

    /**
     * Data class for false positive analysis result.
     */
    data class FalsePositiveAnalysisResult(
        val likelihood: Int,
        val reasons: List<String>
    )

    // ==================== SUMMARY PROMPT DELEGATES ====================

    /**
     * Build a prompt for daily/weekly surveillance summaries.
     * Delegates to [BatchPromptTemplates].
     */
    fun buildSummaryPrompt(
        detections: List<Detection>,
        periodDescription: String,
        previousPeriodComparison: String? = null
    ): String = BatchPromptTemplates.buildSummaryPrompt(detections, periodDescription, previousPeriodComparison)

    // ==================== BATCH ANALYSIS PROMPT DELEGATES ====================

    /**
     * Build a batch analysis prompt for multiple similar detections.
     * Delegates to [BatchPromptTemplates].
     */
    fun buildBatchAnalysisPrompt(batch: DetectionBatch): String =
        BatchPromptTemplates.buildBatchAnalysisPrompt(batch)

    /**
     * Build a simplified batch prompt for very similar detections.
     * Delegates to [BatchPromptTemplates].
     */
    fun buildSimplifiedBatchPrompt(batch: DetectionBatch): String =
        BatchPromptTemplates.buildSimplifiedBatchPrompt(batch)

    /**
     * Build a batch prompt for tracking multiple potential trackers.
     * Delegates to [BatchPromptTemplates].
     */
    fun buildTrackerBatchPrompt(batch: DetectionBatch): String =
        BatchPromptTemplates.buildTrackerBatchPrompt(batch)

    // ==================== CROSS-DOMAIN CORRELATION PROMPT DELEGATES ====================

    /**
     * Build a prompt for cross-domain correlation analysis.
     * Delegates to [BatchPromptTemplates].
     */
    fun buildCorrelationAnalysisPrompt(
        detections: List<Detection>,
        correlationType: String,
        confidenceFactors: List<String>
    ): String = BatchPromptTemplates.buildCorrelationAnalysisPrompt(detections, correlationType, confidenceFactors)

    /**
     * Build a prompt for IMSI Catcher + GNSS combo analysis.
     * Delegates to [BatchPromptTemplates].
     */
    fun buildImsiCatcherComboPrompt(
        cellularDetection: Detection,
        gnssDetection: Detection,
        timeDiffSeconds: Long,
        encryptionDowngrade: Boolean
    ): String = BatchPromptTemplates.buildImsiCatcherComboPrompt(
        cellularDetection, gnssDetection, timeDiffSeconds, encryptionDowngrade
    )

    /**
     * Build a prompt for multi-detection pattern analysis.
     * Delegates to [BatchPromptTemplates].
     */
    fun buildMultiDetectionPatternPrompt(
        detections: List<Detection>,
        timeWindowDescription: String
    ): String = BatchPromptTemplates.buildMultiDetectionPatternPrompt(detections, timeWindowDescription)

    /**
     * Build a prompt for requesting coordination likelihood score.
     * Delegates to [BatchPromptTemplates].
     */
    fun buildCoordinationLikelihoodPrompt(
        detections: List<Detection>,
        spatialProximityMeters: Float?,
        temporalWindowSeconds: Long
    ): String = BatchPromptTemplates.buildCoordinationLikelihoodPrompt(
        detections, spatialProximityMeters, temporalWindowSeconds
    )
}

// Note: DetectionBatch is defined in DetectionBatcher.kt to avoid circular dependencies

// ==================== PACKAGE-LEVEL HELPER FUNCTIONS ====================

/**
 * Maximum length for user-provided strings to prevent prompt stuffing.
 */
private const val MAX_INPUT_LENGTH = 256

/**
 * Sanitize user-provided input to prevent prompt injection attacks.
 *
 * This function:
 * 1. Truncates excessively long strings
 * 2. Removes or escapes control characters
 * 3. Strips potential prompt injection markers
 * 4. Normalizes whitespace
 *
 * @param input The raw input from external sources (device names, SSIDs, etc.)
 * @param maxLength Maximum allowed length (default 256)
 * @return Sanitized string safe for prompt interpolation
 */
internal fun sanitize(input: String?, maxLength: Int = MAX_INPUT_LENGTH): String {
    if (input.isNullOrBlank()) return ""

    return input
        // Truncate to max length
        .take(maxLength)
        // Remove control characters except space, tab, newline
        .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]"), "")
        // Strip potential prompt injection markers
        .replace(Regex("</?(?:start_of_turn|end_of_turn|system|user|model|assistant|human)>", RegexOption.IGNORE_CASE), "[FILTERED]")
        .replace(Regex("\\[/?(?:INST|SYS|SYSTEM|USER)\\]", RegexOption.IGNORE_CASE), "[FILTERED]")
        // Normalize excessive whitespace
        .replace(Regex("\\s{3,}"), "  ")
        .trim()
}

/**
 * Sanitize a list of strings.
 */
internal fun sanitizeList(items: List<String>, maxLength: Int = MAX_INPUT_LENGTH): List<String> {
    return items.map { sanitize(it, maxLength) }.filter { it.isNotEmpty() }
}

/**
 * Gemma instruction format wrapper
 */
internal fun wrapGemmaPrompt(userContent: String): String {
    return """<start_of_turn>user
$userContent
<end_of_turn>
<start_of_turn>model
"""
}

/**
 * Format a timestamp as a relative time string.
 */
internal fun formatTimestamp(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000} minutes ago"
        diff < 86400_000 -> "${diff / 3600_000} hours ago"
        else -> "${diff / 86400_000} days ago"
    }
}

/**
 * Sealed class representing enriched detector data for different detection types.
 */
sealed class EnrichedDetectorData {
    data class Cellular(val analysis: CellularAnomalyAnalysis) : EnrichedDetectorData()
    data class Gnss(val analysis: GnssAnomalyAnalysis) : EnrichedDetectorData()
    data class Ultrasonic(val analysis: BeaconAnalysis) : EnrichedDetectorData()
    data class WifiFollowing(val analysis: FollowingNetworkAnalysis) : EnrichedDetectorData()
    data class Satellite(
        val detectorType: String,
        val metadata: Map<String, String>,
        val signalCharacteristics: Map<String, String>,
        val riskIndicators: List<String>,
        val timestamp: Long
    ) : EnrichedDetectorData()

    /**
     * Enriched RF environment data for LLM analysis.
     *
     * Captures the RF environment context when an RF anomaly is detected, including
     * signal characteristics, hidden network analysis, anomaly details, and
     * environmental status. This enables the LLM to provide more accurate and
     * contextual analysis of RF detections.
     *
     * Note on detection feasibility:
     * - JAMMER: Inferred indirectly from WiFi signal loss patterns (not true RF measurement)
     * - DRONE: Detected via WiFi OUI/SSID matching (limited to controllers with WiFi)
     * - SURVEILLANCE_AREA: Detected via camera vendor OUI counts (high urban FP rate)
     * - SPECTRUM_ANOMALY: Inferred from WiFi signal statistics (not true spectrum analysis)
     * - HIDDEN_TRANSMITTER: Inferred from hidden WiFi networks (not true RF sweep)
     * - SUB_GHZ: Requires Flipper Zero or SDR hardware -- not available via WiFi proxy
     */
    data class RfEnvironment(
        /** The specific anomaly that triggered this detection */
        val anomalyType: String,
        /** Confidence level of the anomaly detection */
        val anomalyConfidence: String,
        /** Human-readable anomaly description */
        val anomalyDescription: String,
        /** Contributing factors that led to the detection */
        val contributingFactors: List<String>,
        /** Total WiFi networks observed in the scan */
        val totalNetworks: Int,
        /** Number of hidden (no SSID) networks */
        val hiddenNetworkCount: Int,
        /** Average signal strength across all networks (dBm) */
        val avgSignalStrength: Int,
        /** Signal variance across all networks */
        val signalVariance: Float,
        /** Suspicious patterns identified in the RF environment */
        val suspiciousPatterns: List<String>,
        /** RF threat score (0-100) combining all anomaly indicators */
        val rfThreatScore: Int,
        /** Detailed hidden network analysis, if available */
        val hiddenNetworkAnalysis: HiddenNetworkAnalysis?,
        /** Current RF environment status, if available */
        val environmentStatus: RfEnvironmentStatus?,
        /** False positive likelihood estimate (0-100) */
        val falsePositiveLikelihood: Float,
        /** Indicators suggesting this is a false positive */
        val fpIndicators: List<String>,
        /** Whether this detection is based on WiFi proxy data rather than true RF measurement */
        val isWifiProxyBased: Boolean = true
    ) : EnrichedDetectorData()

    data class Ble(val analysis: BleAnalysisData) : EnrichedDetectorData()

    /**
     * Enriched data for standard WiFi SSID/MAC pattern match detections.
     *
     * Standard WiFi detections (SSID pattern, MAC prefix) previously had no enriched
     * data for LLM analysis, unlike WiFi Following which has [FollowingNetworkAnalysis].
     * This data class provides the scan context so the LLM can give more informed analysis
     * about the detected surveillance device's proximity, network characteristics, and
     * environmental context.
     */
    data class WifiSsidMatch(
        /** The matched SSID (may be empty for hidden networks) */
        val ssid: String,
        /** The BSSID (MAC address) of the access point */
        val bssid: String,
        /** WiFi channel number */
        val channel: Int,
        /** Operating frequency in MHz */
        val frequencyMhz: Int,
        /** Security/protocol capabilities string (e.g., "[WPA2-PSK-CCMP][ESS]") */
        val capabilities: String,
        /** Signal strength in dBm */
        val rssiDbm: Int,
        /** Whether the network has a hidden/empty SSID */
        val isHidden: Boolean,
        /** OUI vendor name resolved from BSSID prefix, if known */
        val ouiVendor: String?,
        /** Total number of APs visible in the same scan */
        val nearbyApCount: Int,
        /** The matched pattern description */
        val matchedPatternDescription: String,
        /** The detection method used (SSID_PATTERN or MAC_PREFIX) */
        val detectionMethodName: String,
        /** Manufacturer from the pattern database, if known */
        val patternManufacturer: String?,
        /** WiFi frequency band (2.4 GHz, 5 GHz, or 6 GHz) */
        val frequencyBand: String
    ) : EnrichedDetectorData()
}

/**
 * BLE enriched analysis data for LLM prompts.
 *
 * Captures detailed BLE detection context including device identification,
 * signal characteristics, tracker following analysis, and false positive indicators.
 * This enables the LLM to provide more accurate and contextual analysis for
 * BLE-based surveillance detections.
 */
data class BleAnalysisData(
    // Device identification
    val deviceName: String?,
    val macAddress: String,
    val manufacturer: String?,
    val deviceCategory: BleDeviceCategory,

    // Signal characteristics
    val rssi: Int,
    val advertisingRate: Float,
    val isConnectable: Boolean,
    val txPowerLevel: Int?,
    val estimatedDistanceMeters: Double?,

    // Service and manufacturer data
    val serviceUuids: List<String>,
    val manufacturerIds: List<Int>,

    // Tracker following analysis (if applicable)
    val isTrackerDevice: Boolean,
    val trackerSightingCount: Int,
    val distinctLocationCount: Int,
    val isFollowingUser: Boolean,
    val durationMinutes: Long,
    val averageRssi: Int,
    val rssiVariance: Double,
    val isPossessionSignal: Boolean,
    val suspicionScore: Int,
    val suspicionReasons: List<String>,

    // BLE spam analysis (if applicable)
    val isBleSpam: Boolean,
    val spamType: String?,
    val spamEventsCount: Int,
    val spamEventsPerSecond: Float,

    // False positive assessment
    val falsePositiveLikelihood: Float,
    val fpIndicators: List<String>,
    val isLikelyConsumerDevice: Boolean,
    val isLikelyOwnDevice: Boolean,
    val isPassingBy: Boolean
)

/**
 * Categories for BLE device classification in enriched prompts.
 */
enum class BleDeviceCategory(val displayName: String) {
    SURVEILLANCE("Surveillance Equipment"),
    LAW_ENFORCEMENT("Law Enforcement Technology"),
    FORENSICS("Forensics Tool"),
    CONSUMER_TRACKER("Consumer Tracker"),
    SMART_HOME("Smart Home Device"),
    HACKING_TOOL("Hacking Tool"),
    BLE_SPAM("BLE Spam Attack"),
    BEACON("Retail/Advertising Beacon"),
    UNKNOWN("Unknown BLE Device")
}
