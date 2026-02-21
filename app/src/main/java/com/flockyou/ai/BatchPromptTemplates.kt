package com.flockyou.ai

import com.flockyou.data.model.Detection
import com.flockyou.data.model.ThreatLevel

/**
 * Batch and multi-detection prompt templates for LLM analysis.
 *
 * Provides prompts for analyzing multiple detections together:
 * - Pattern recognition across detection history
 * - Summary prompts for daily/weekly reports
 * - Batch analysis for groups of similar detections
 * - Cross-domain correlation analysis
 * - IMSI catcher combo analysis
 * - Coordination likelihood scoring
 */
object BatchPromptTemplates {

    /**
     * Build a prompt to analyze patterns across multiple detections.
     */
    fun buildPatternRecognitionPrompt(
        detections: List<Detection>,
        timeWindowDescription: String
    ): String {
        val detectionList = detections.take(10).mapIndexed { index, d ->
            """${index + 1}. ${d.deviceType.displayName}
   - Time: ${formatTimestamp(d.timestamp)}
   - Protocol: ${d.protocol.displayName}
   - Threat: ${d.threatLevel.displayName} (${d.threatScore})
   - Location: ${if (d.latitude != null && d.longitude != null) "${String.format("%.4f", d.latitude)}, ${String.format("%.4f", d.longitude)}" else "Unknown"}
   - Signal: ${d.rssi} dBm"""
        }.joinToString("\n\n")

        val content = """Analyze these surveillance detections for coordinated patterns.

Time Window: $timeWindowDescription
Total Detections: ${detections.size}

=== DETECTIONS ===
$detectionList

Look for these patterns:
1. COORDINATED SURVEILLANCE: Multiple devices working together
2. FOLLOWING PATTERN: Devices appearing wherever the user goes
3. TIMING CORRELATION: Devices activating at the same times
4. GEOGRAPHIC CLUSTERING: Multiple devices in a small area
5. ESCALATION: Threat levels increasing over time
6. MULTIMODAL: Different detection types targeting same area

For each pattern found, provide:
- Pattern type
- Which detections are involved (by number)
- Confidence level (LOW/MEDIUM/HIGH)
- What this pattern suggests
- Recommended response

Format as:
## Pattern Analysis

### [Pattern Name]
- Detections: [numbers]
- Confidence: [level]
- Interpretation: [what it means]
- Action: [what to do]

[Repeat for each pattern found]

If no significant patterns, state "No coordinated patterns detected" and explain why."""

        return wrapGemmaPrompt(content)
    }

    /**
     * Build a prompt for daily/weekly surveillance summaries.
     */
    fun buildSummaryPrompt(
        detections: List<Detection>,
        periodDescription: String,
        previousPeriodComparison: String? = null
    ): String {
        val byType = detections.groupBy { it.deviceType }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(5)

        val byThreat = detections.groupBy { it.threatLevel }
            .mapValues { it.value.size }

        val content = """Generate a surveillance exposure summary for the user.

Period: $periodDescription
Total Detections: ${detections.size}
${previousPeriodComparison?.let { "Comparison: $it" } ?: ""}

=== BY DEVICE TYPE ===
${byType.joinToString("\n") { "${it.first.displayName}: ${it.second}" }}

=== BY THREAT LEVEL ===
Critical: ${byThreat[ThreatLevel.CRITICAL] ?: 0}
High: ${byThreat[ThreatLevel.HIGH] ?: 0}
Medium: ${byThreat[ThreatLevel.MEDIUM] ?: 0}
Low: ${byThreat[ThreatLevel.LOW] ?: 0}
Info: ${byThreat[ThreatLevel.INFO] ?: 0}

=== HIGH-PRIORITY EVENTS ===
${detections.filter { it.threatLevel == ThreatLevel.CRITICAL || it.threatLevel == ThreatLevel.HIGH }
    .take(5)
    .mapIndexed { i, d -> "${i + 1}. ${d.deviceType.displayName} - ${d.threatLevel.displayName}" }
    .joinToString("\n")}

Generate a summary with:
1. HEADLINE: One sentence overview
2. KEY FINDINGS: 3-4 bullet points of most important observations
3. TREND: Is surveillance exposure increasing, decreasing, or stable?
4. HOTSPOTS: Any locations with concentrated surveillance
5. RECOMMENDATIONS: 2-3 actionable suggestions

Format as:
## $periodDescription Summary

**Headline:** [summary]

**Key Findings:**
- [finding 1]
- [finding 2]
- [finding 3]

**Trend:** [assessment]

**Hotspots:** [locations if any]

**Recommendations:**
1. [recommendation]
2. [recommendation]"""

        return wrapGemmaPrompt(content)
    }

    /**
     * Build a batch analysis prompt for multiple similar detections.
     * This allows analyzing 5-10 detections in a single LLM call for ~5x efficiency.
     *
     * @param batch The batch of detections to analyze
     * @return A prompt requesting analysis for all detections with IDs
     */
    fun buildBatchAnalysisPrompt(batch: DetectionBatch): String {
        val detectionsSection = batch.detections.mapIndexed { index, detection ->
            val sanitizedName = sanitize(detection.deviceName)
            val sanitizedSsid = sanitize(detection.ssid)
            """
[DETECTION_${index + 1}] ID: ${detection.id}
- Type: ${detection.deviceType.displayName}
- Protocol: ${detection.protocol.displayName}
- Method: ${detection.detectionMethod.displayName}
- Signal: ${detection.rssi} dBm (${detection.signalStrength.displayName})
- Threat Level: ${detection.threatLevel.displayName}
- Score: ${detection.threatScore}/100
- Times Seen: ${detection.seenCount}
${if (sanitizedName.isNotEmpty()) "- Name: $sanitizedName" else ""}
${if (sanitizedSsid.isNotEmpty()) "- SSID: $sanitizedSsid" else ""}
${detection.manufacturer?.let { "- Manufacturer: ${sanitize(it)}" } ?: ""}
${if (detection.latitude != null && detection.longitude != null) "- Location: ${String.format("%.4f", detection.latitude)}, ${String.format("%.4f", detection.longitude)}" else ""}"""
        }.joinToString("\n")

        val groupDescription = when {
            batch.groupKey.contains("BLE_TRACKER") -> "Bluetooth trackers (AirTags, Tiles, etc.)"
            batch.groupKey.contains("TRAFFIC_CAMERA") -> "Traffic/ALPR cameras"
            batch.groupKey.contains("SMART_CAMERA") -> "Smart home cameras"
            batch.groupKey.contains("NETWORK_ATTACK") -> "Network attack devices"
            batch.groupKey.contains("IMSI_CATCHER") -> "Cell-site simulators"
            batch.groupKey.contains("GNSS_THREAT") -> "GPS/GNSS threats"
            batch.groupKey.contains("RF_THREAT") -> "RF anomalies/jammers"
            batch.groupKey.contains("HACKING_TOOL") -> "Hacking tools"
            batch.groupKey.contains("AUDIO_SURVEILLANCE") -> "Audio surveillance"
            batch.groupKey.contains("VIDEO_SURVEILLANCE") -> "Video surveillance"
            else -> "surveillance devices"
        }

        val content = """Analyze this batch of ${batch.detections.size} similar $groupDescription detections.
Provide analysis for EACH detection with its ID so results can be mapped back.

=== BATCH INFO ===
Category: ${batch.groupKey}
Total: ${batch.detections.size} detections
Priority: ${when(batch.priority) { 0 -> "CRITICAL" 1 -> "HIGH" 2 -> "MEDIUM" else -> "LOW" }}

=== DETECTIONS ===
$detectionsSection

=== INSTRUCTIONS ===
For EACH detection, provide a brief analysis. Format your response EXACTLY as:

[RESULT_1] ID: <detection_id>
THREAT: <CRITICAL|HIGH|MEDIUM|LOW|INFO>
CONFIDENCE: <0-100>
FP_LIKELIHOOD: <0-100>
SUMMARY: <1-2 sentence analysis>
ACTION: <recommended action or "No action needed">

[RESULT_2] ID: <detection_id>
... (continue for all detections)

=== BATCH ANALYSIS ===
After individual analyses, provide:
BATCH_PATTERN: <any pattern across these detections>
BATCH_RISK: <overall risk assessment>
BATCH_ACTION: <coordinated action if any>

IMPORTANT:
- Include the EXACT detection ID in each result
- Keep each SUMMARY to 1-2 sentences max
- If detections share identical characteristics, you may reference "same as RESULT_N"
- Focus on what makes each detection unique or concerning"""

        return wrapGemmaPrompt(content)
    }

    /**
     * Build a simplified batch prompt for very similar detections.
     * Used when all detections in batch are essentially identical (e.g., same device type, same area).
     */
    fun buildSimplifiedBatchPrompt(batch: DetectionBatch): String {
        val representativeDetection = batch.detections.first()
        val ids = batch.detections.map { it.id }

        val content = """Analyze this group of ${batch.detections.size} identical detections.

=== REPRESENTATIVE DETECTION ===
- Type: ${representativeDetection.deviceType.displayName}
- Protocol: ${representativeDetection.protocol.displayName}
- Method: ${representativeDetection.detectionMethod.displayName}
- Threat Level: ${representativeDetection.threatLevel.displayName}
- Average Signal: ${batch.detections.map { it.rssi }.average().toInt()} dBm

=== DETECTION IDS ===
${ids.joinToString(", ")}

=== INSTRUCTIONS ===
Since these detections are nearly identical, provide ONE analysis that applies to all.

SHARED_ANALYSIS:
THREAT: <CRITICAL|HIGH|MEDIUM|LOW|INFO>
CONFIDENCE: <0-100>
FP_LIKELIHOOD: <0-100>
SUMMARY: <1-2 sentence analysis for all detections>
APPLIES_TO_IDS: ${ids.joinToString(",")}
ACTION: <recommended action>

If any detection stands out, note it separately:
EXCEPTION_ID: <id>
EXCEPTION_REASON: <why it's different>"""

        return wrapGemmaPrompt(content)
    }

    /**
     * Build a batch prompt for tracking multiple potential trackers.
     * Specialized for AirTags, Tiles, etc. where tracking detection is key.
     */
    fun buildTrackerBatchPrompt(batch: DetectionBatch): String {
        val trackers = batch.detections.mapIndexed { index, d ->
            """[TRACKER_${index + 1}] ID: ${d.id}
- Type: ${d.deviceType.displayName}
- First Seen: ${formatTimestampRelative(d.timestamp)}
- Times Seen: ${d.seenCount}
- Last RSSI: ${d.rssi} dBm
- MAC: ${d.macAddress?.takeLast(8) ?: "Unknown"}
${if (d.latitude != null) "- Location: ${String.format("%.4f", d.latitude)}, ${String.format("%.4f", d.longitude)}" else "- Location: Unknown"}"""
        }.joinToString("\n\n")

        val content = """Analyze these ${batch.detections.size} potential tracking devices for stalking risk.

=== TRACKERS DETECTED ===
$trackers

=== RISK ASSESSMENT ===
For EACH tracker, assess:
1. Is this likely YOUR tracker or someone else's?
2. How long has it been near you?
3. Has it appeared at multiple locations?
4. What's the stalking/tracking risk?

Format response as:
[TRACKER_RESULT_1] ID: <id>
OWNERSHIP: <LIKELY_YOURS|LIKELY_OTHERS|UNKNOWN>
FOLLOWING_RISK: <HIGH|MEDIUM|LOW|NONE>
DURATION: <how long detected>
ACTION: <specific action>
REASON: <brief explanation>

Continue for all trackers...

=== OVERALL ASSESSMENT ===
COMBINED_RISK: <overall tracking risk from all devices>
PATTERN: <any concerning pattern across devices>
PRIORITY_ACTION: <most important action to take>"""

        return wrapGemmaPrompt(content)
    }

    /**
     * Build a prompt for cross-domain correlation analysis.
     * Used to analyze patterns across BLE, WiFi, Cellular, and GNSS detections.
     *
     * @param detections List of detections involved in the correlation
     * @param correlationType The type of correlation pattern detected
     * @param confidenceFactors Factors that support this correlation
     * @return A prompt for LLM-enhanced correlation analysis
     */
    fun buildCorrelationAnalysisPrompt(
        detections: List<Detection>,
        correlationType: String,
        confidenceFactors: List<String>
    ): String {
        val detectionsSection = detections.mapIndexed { index, d ->
            """Detection ${index + 1}:
- Type: ${d.deviceType.displayName}
- Protocol: ${d.protocol.displayName}
- Method: ${d.detectionMethod.displayName}
- Threat Level: ${d.threatLevel.displayName}
- Signal: ${d.rssi} dBm
- Time: ${formatTimestampRelative(d.timestamp)}
${d.macAddress?.let { "- Identifier: $it" } ?: ""}
${if (d.latitude != null) "- Location: ${String.format("%.5f", d.latitude)}, ${String.format("%.5f", d.longitude)}" else ""}"""
        }.joinToString("\n\n")

        val factorsSection = if (confidenceFactors.isNotEmpty()) {
            """

=== CORRELATION EVIDENCE ===
${confidenceFactors.joinToString("\n") { "- $it" }}"""
        } else ""

        val content = """You are analyzing a CROSS-DOMAIN CORRELATION of surveillance detections.

=== CORRELATION TYPE ===
$correlationType

This correlation was detected because multiple surveillance signals from DIFFERENT domains
(e.g., cellular + GPS, WiFi + Bluetooth, etc.) appear to be related.

=== DETECTIONS INVOLVED ===
$detectionsSection
$factorsSection

=== ANALYSIS REQUIRED ===
Analyze this correlation and provide:

1. **Assessment**: Is this a genuine coordinated surveillance operation, or could these be coincidental?

2. **Threat Analysis**: What is the combined threat from these correlated detections?

3. **Technical Explanation**: Why do these detections suggest coordination?
   - Explain the technical relationship between the detection types
   - Note any classic surveillance patterns (e.g., IMSI catcher + GPS jammer)

4. **Recommendations**: Provide 3-5 SPECIFIC actionable recommendations.
   - Prioritize by urgency
   - Include both immediate actions and longer-term precautions

5. **False Positive Assessment**: What factors might indicate this is a false correlation?

Format your response as:

## Assessment
[Your assessment of whether this is genuine coordinated surveillance]

## Threat Level
[CRITICAL/HIGH/MEDIUM/LOW] - [Brief justification]

## Technical Analysis
[Explanation of why these detections are correlated]

## Recommendations
1. [Most urgent action]
2. [Second priority]
3. [Third priority]
[Continue as needed]

## False Positive Indicators
[Factors that might indicate false positive]"""

        return wrapGemmaPrompt(content)
    }

    /**
     * Build a prompt for IMSI Catcher + GNSS combo analysis.
     * This is a specialized prompt for the most serious correlation type.
     */
    fun buildImsiCatcherComboPrompt(
        cellularDetection: Detection,
        gnssDetection: Detection,
        timeDiffSeconds: Long,
        encryptionDowngrade: Boolean
    ): String {
        val content = """CRITICAL ALERT: Analyze potential IMSI Catcher + GPS Interference combination.

=== DETECTION 1: CELLULAR ANOMALY ===
Device Type: ${cellularDetection.deviceType.displayName}
Detection Method: ${cellularDetection.detectionMethod.displayName}
Threat Level: ${cellularDetection.threatLevel.displayName}
Signal Strength: ${cellularDetection.rssi} dBm
Encryption Downgrade: ${if (encryptionDowngrade) "YES - Active encryption weakening detected" else "No"}

=== DETECTION 2: GNSS ANOMALY ===
Device Type: ${gnssDetection.deviceType.displayName}
Detection Method: ${gnssDetection.detectionMethod.displayName}
Threat Level: ${gnssDetection.threatLevel.displayName}
${if (gnssDetection.deviceType.name.contains("JAMMER")) "Type: GPS JAMMING - signals blocked" else "Type: GPS SPOOFING - fake signals"}

=== CORRELATION ===
Time Between Detections: ${timeDiffSeconds} seconds
${if (cellularDetection.latitude != null && gnssDetection.latitude != null)
    "Spatial Proximity: Both detected in same general area" else "Location: Unknown"}

=== CONTEXT ===
This combination is a CLASSIC SIGNATURE of law enforcement IMSI catcher (StingRay) deployment:
1. IMSI catchers force phones to connect and downgrade encryption
2. GPS jammers are deployed to prevent location logging
3. This makes it harder for targets to prove they were surveilled

=== REQUIRED ANALYSIS ===

1. **Confidence Assessment**: How confident are you this is a real IMSI catcher operation?
   Rate 0-100% and explain your reasoning.

2. **What Data Is At Risk**:
   - List specifically what data could be captured
   - Explain encryption implications
   - Note if location tracking is enabled

3. **Immediate Actions** (in order of priority):
   - What should the user do RIGHT NOW?
   - What should they avoid doing?

4. **Documentation Advice**:
   - What should they document for potential legal action?
   - Should they file a FOIA request?

5. **False Positive Analysis**:
   - What could cause these detections to be false positives?
   - What additional evidence would confirm this is real?

Format as structured markdown with clear headers."""

        return wrapGemmaPrompt(content)
    }

    /**
     * Build a prompt for multi-detection pattern analysis.
     * Asks LLM to identify patterns across multiple detections.
     */
    fun buildMultiDetectionPatternPrompt(
        detections: List<Detection>,
        timeWindowDescription: String
    ): String {
        val detectionList = detections.take(15).mapIndexed { index, d ->
            """${index + 1}. ${d.deviceType.displayName}
   Protocol: ${d.protocol.displayName}
   Method: ${d.detectionMethod.displayName}
   Threat: ${d.threatLevel.displayName}
   Time: ${formatTimestampRelative(d.timestamp)}
   Signal: ${d.rssi} dBm
   ${if (d.latitude != null) "Location: ${String.format("%.4f", d.latitude)}, ${String.format("%.4f", d.longitude)}" else ""}"""
        }.joinToString("\n\n")

        val protocolCounts = detections.groupBy { it.protocol }.mapValues { it.value.size }
        val threatCounts = detections.groupBy { it.threatLevel }.mapValues { it.value.size }

        val content = """Analyze these ${detections.size} surveillance detections for CROSS-DOMAIN patterns.

=== TIME WINDOW ===
$timeWindowDescription

=== DETECTION SUMMARY ===
Total: ${detections.size} detections

By Protocol:
${protocolCounts.entries.joinToString("\n") { "- ${it.key.displayName}: ${it.value}" }}

By Threat Level:
${threatCounts.entries.sortedByDescending { it.key.ordinal }.joinToString("\n") { "- ${it.key.displayName}: ${it.value}" }}

=== INDIVIDUAL DETECTIONS ===
$detectionList

=== PATTERN ANALYSIS REQUIRED ===

Identify any of these CROSS-DOMAIN patterns:

1. **IMSI Catcher + GPS Jamming**: Cell anomaly within 5 minutes of GNSS interference
   - Classic StingRay deployment pattern

2. **Following Pattern**: Same device identifier appearing at 3+ distinct locations
   - Indicates personal tracking

3. **Coordinated Surveillance**: Multiple protocols (WiFi + BLE + Cell) active together
   - Suggests organized operation

4. **Tracker Network**: Multiple Bluetooth trackers with similar behavior
   - Could indicate multiple planted trackers

5. **Timing Synchronization**: Multiple activations within 60 seconds
   - Suggests coordinated operation

6. **Spatial Clustering**: Multiple threats within 200m radius
   - Indicates surveillance hotspot

For EACH pattern found, provide:
- Pattern Type
- Detection Numbers Involved (e.g., "1, 4, 7")
- Confidence Level (HIGH/MEDIUM/LOW)
- Brief Explanation
- Recommended Action

Format as:

## Patterns Detected

### [Pattern Name 1]
- **Detections**: [numbers]
- **Confidence**: [level]
- **Analysis**: [explanation]
- **Action**: [recommendation]

### [Pattern Name 2]
...

## Summary
[Overall assessment and priority action]

If NO patterns are found, explain why these detections appear to be unrelated."""

        return wrapGemmaPrompt(content)
    }

    /**
     * Build a prompt for requesting coordination likelihood score.
     */
    fun buildCoordinationLikelihoodPrompt(
        detections: List<Detection>,
        spatialProximityMeters: Float?,
        temporalWindowSeconds: Long
    ): String {
        val protocols = detections.map { it.protocol }.toSet()
        val maxThreat = detections.maxOfOrNull { it.threatLevel } ?: ThreatLevel.INFO

        val content = """Assess the likelihood that these detections represent COORDINATED surveillance.

=== DETECTIONS ===
Count: ${detections.size}
Protocols: ${protocols.joinToString { it.displayName }}
Highest Threat: ${maxThreat.displayName}
Time Window: ${temporalWindowSeconds} seconds
${spatialProximityMeters?.let { "Spatial Proximity: ${it.toInt()} meters" } ?: "Spatial Proximity: Unknown"}

=== DEVICE TYPES ===
${detections.map { it.deviceType.displayName }.distinct().joinToString("\n") { "- $it" }}

=== SCORING FACTORS ===
Consider these factors for coordination likelihood:

INCREASES likelihood:
- Multiple protocols active (WiFi + BLE + Cellular)
- Close temporal proximity (< 60 seconds)
- Close spatial proximity (< 100 meters)
- IMSI catcher + GPS interference combination
- Multiple high-threat detections
- Known surveillance device combinations

DECREASES likelihood:
- Single protocol type
- Long time between detections
- Common consumer devices (Ring, Nest)
- Different geographic areas
- Mixed threat levels with mostly LOW/INFO

=== PROVIDE ===
1. COORDINATION_SCORE: 0-100 (likelihood this is coordinated)
2. CONFIDENCE: 0-100 (how confident are you in this score)
3. REASONING: Brief explanation (2-3 sentences)
4. RECOMMENDED_ACTION: What should the user do?

Format EXACTLY as:
COORDINATION_SCORE: [number]
CONFIDENCE: [number]
REASONING: [text]
RECOMMENDED_ACTION: [text]"""

        return wrapGemmaPrompt(content)
    }

    private fun formatTimestampRelative(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < 60_000 -> "Just now"
            diff < 3600_000 -> "${diff / 60_000}m ago"
            diff < 86400_000 -> "${diff / 3600_000}h ago"
            else -> "${diff / 86400_000}d ago"
        }
    }
}
