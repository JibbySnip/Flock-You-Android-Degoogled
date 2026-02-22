package com.flockyou.ai

import com.flockyou.data.model.Detection
import com.flockyou.data.model.DetectionMethod
import com.flockyou.data.model.DetectionProtocol
import com.flockyou.data.model.DetectionSource
import com.flockyou.data.model.DeviceType
import com.flockyou.data.model.ThreatLevel
import com.flockyou.privilege.PrivilegeMode

/**
 * Generates enterprise-grade detection descriptions with comprehensive, actionable intelligence.
 *
 * Produces structured descriptions answering: WHAT, WHY, HOW CONFIDENT, WHAT TO DO, and CONTEXT.
 * Used for rule-based (non-LLM) detection analysis and as fallback when LLM is unavailable.
 */
object DescriptionGenerator {

    /**
     * Generate enterprise-grade detection description based on detection data and analysis.
     */
    fun generateEnterpriseDescription(
        detection: Detection,
        enrichedData: EnrichedDetectorData? = null,
        contextualInsights: PromptTemplates.ContextualInsights? = null,
        falsePositiveResult: PromptTemplates.FalsePositiveAnalysisResult? = null
    ): PromptTemplates.EnterpriseDetectionDescription {
        val deviceType = detection.deviceType
        val threatLevel = detection.threatLevel
        val threatScore = detection.threatScore

        // Determine false positive likelihood
        val fpLikelihood = falsePositiveResult?.likelihood ?: estimateFalsePositiveLikelihood(detection, enrichedData)
        val isMostLikelyBenign = fpLikelihood > 50

        // Generate headline based on severity and FP likelihood
        val headline = generateHeadline(detection, isMostLikelyBenign)

        // Get device info
        val deviceInfo = getDeviceInfoForDescription(deviceType)

        // Generate trigger indicators
        val triggerIndicators = generateTriggerIndicators(detection, enrichedData)

        // Generate confidence reasoning
        val (confidenceScore, confidenceReasoning) = generateConfidenceAssessment(detection, enrichedData)

        // Generate false positive reasons
        val fpReasons = generateFalsePositiveReasons(detection, enrichedData)

        // Generate contextual assessment
        val (isNormalForLocation, environmentalContext) = assessContext(detection, contextualInsights)

        // Generate actions based on severity and FP likelihood
        val (immediateAction, monitoringRec, docSuggestion) = generateActionableIntelligence(
            detection, isMostLikelyBenign, threatLevel
        )

        // Generate explanations
        val simpleExplanation = generateSimpleExplanation(detection, isMostLikelyBenign)
        val technicalDetails = generateTechnicalDetails(detection, enrichedData)

        return PromptTemplates.EnterpriseDetectionDescription(
            headline = headline,
            threatLevel = threatLevel.displayName,
            deviceSummary = deviceInfo.summary,
            devicePurpose = deviceInfo.purpose,
            dataCollectionSummary = deviceInfo.dataCollection,
            triggerIndicators = triggerIndicators,
            threatReasoning = generateThreatReasoning(detection, enrichedData),
            confidenceScore = confidenceScore,
            confidenceReasoning = confidenceReasoning,
            falsePositiveLikelihood = fpLikelihood,
            falsePositiveReasons = fpReasons,
            isMostLikelyBenign = isMostLikelyBenign,
            benignExplanation = if (isMostLikelyBenign) generateBenignExplanation(detection, fpReasons) else null,
            isNormalForLocation = isNormalForLocation,
            isRecurring = detection.seenCount > 1,
            correlatedDetections = contextualInsights?.let { listOfNotNull(it.clusterInfo) } ?: emptyList(),
            environmentalContext = environmentalContext,
            immediateAction = immediateAction,
            monitoringRecommendation = monitoringRec,
            documentationSuggestion = docSuggestion,
            additionalResources = getResourcesForDeviceType(deviceType),
            simpleExplanation = simpleExplanation,
            technicalDetails = technicalDetails
        )
    }

    /**
     * Format the enterprise description as a user-facing string.
     */
    fun formatEnterpriseDescriptionForUser(desc: PromptTemplates.EnterpriseDetectionDescription): String {
        return buildString {
            appendLine("## ${desc.headline}")
            appendLine()

            if (desc.isMostLikelyBenign) {
                appendLine("### Likely False Alarm")
                appendLine(desc.benignExplanation ?: "This detection is probably not a real threat.")
                appendLine()
                appendLine("**Why we think this:**")
                desc.falsePositiveReasons.take(3).forEach { appendLine("- $it") }
                appendLine()
            }

            appendLine("### What Was Detected")
            appendLine(desc.deviceSummary)
            appendLine()
            appendLine("**Purpose:** ${desc.devicePurpose}")
            appendLine()
            appendLine("**Data Collection:** ${desc.dataCollectionSummary}")
            appendLine()

            appendLine("### Why This Was Flagged")
            desc.triggerIndicators.forEach { appendLine("- $it") }
            appendLine()

            appendLine("### Confidence Assessment")
            appendLine("- Confidence: ${desc.confidenceScore}%")
            appendLine("- ${desc.confidenceReasoning}")
            appendLine("- False positive likelihood: ${desc.falsePositiveLikelihood}%")
            appendLine()

            if (!desc.isMostLikelyBenign) {
                appendLine("### Recommended Actions")
                desc.immediateAction?.let {
                    appendLine("**${it.urgency.name}:** ${it.action}")
                    appendLine("*Reason: ${it.reason}*")
                    appendLine()
                }
                appendLine("**Monitoring:** ${desc.monitoringRecommendation}")
                desc.documentationSuggestion?.let { appendLine("**Documentation:** $it") }
                appendLine()
            } else {
                appendLine("### No Action Needed")
                appendLine(desc.monitoringRecommendation)
                appendLine()
            }

            if (desc.additionalResources.isNotEmpty()) {
                appendLine("### Learn More")
                desc.additionalResources.forEach { appendLine("- $it") }
            }
        }
    }

    /**
     * Generate headline that accurately reflects severity and FP likelihood.
     */
    private fun generateHeadline(detection: Detection, isMostLikelyBenign: Boolean): String {
        val deviceName = detection.deviceType.displayName

        return when {
            isMostLikelyBenign -> when (detection.threatLevel) {
                ThreatLevel.CRITICAL, ThreatLevel.HIGH ->
                    "Possible $deviceName - Likely False Alarm"
                ThreatLevel.MEDIUM ->
                    "$deviceName Detected - Probably Normal"
                else ->
                    "$deviceName Nearby - Normal Activity"
            }
            else -> when (detection.threatLevel) {
                ThreatLevel.CRITICAL ->
                    "ALERT: Active $deviceName Detected"
                ThreatLevel.HIGH ->
                    "Warning: $deviceName Confirmed"
                ThreatLevel.MEDIUM ->
                    "$deviceName Detected - Monitor"
                ThreatLevel.LOW ->
                    "$deviceName Nearby - Low Concern"
                ThreatLevel.INFO ->
                    "$deviceName Observed"
            }
        }
    }

    private data class DeviceInfoForDescription(
        val summary: String,
        val purpose: String,
        val dataCollection: String
    )

    private fun getDeviceInfoForDescription(deviceType: DeviceType): DeviceInfoForDescription {
        return when (deviceType) {
            DeviceType.STINGRAY_IMSI -> DeviceInfoForDescription(
                summary = "Cell-site simulator (also known as IMSI catcher or StingRay)",
                purpose = "Forces mobile phones to connect to it instead of legitimate cell towers, enabling interception of communications and precise location tracking",
                dataCollection = "Phone identifiers (IMSI/IMEI), call metadata, text messages, real-time location, and potentially call/text content when forcing 2G downgrade"
            )
            DeviceType.GNSS_SPOOFER -> DeviceInfoForDescription(
                summary = "GPS/GNSS spoofing device",
                purpose = "Transmits fake satellite signals to manipulate location data on devices in range",
                dataCollection = "Does not collect data directly, but manipulates your device's reported location"
            )
            DeviceType.GNSS_JAMMER -> DeviceInfoForDescription(
                summary = "GPS/GNSS jamming device",
                purpose = "Blocks legitimate satellite signals to prevent GPS positioning",
                dataCollection = "Does not collect data, but denies GPS service to devices in range"
            )
            DeviceType.ULTRASONIC_BEACON -> DeviceInfoForDescription(
                summary = "Ultrasonic tracking beacon",
                purpose = "Emits inaudible sound (18-22 kHz) to track users across devices for advertising attribution",
                dataCollection = "Cross-device identity linking, app usage correlation, physical location presence"
            )
            DeviceType.RING_DOORBELL -> DeviceInfoForDescription(
                summary = "Amazon Ring smart doorbell/camera",
                purpose = "Consumer video doorbell that records visitors and can share footage with law enforcement",
                dataCollection = "Video/audio of visitors, motion events, and can be accessed by police through Neighbors program"
            )
            DeviceType.FLOCK_SAFETY_CAMERA -> DeviceInfoForDescription(
                summary = "Flock Safety automatic license plate recognition (ALPR) camera",
                purpose = "Captures license plates of passing vehicles for law enforcement searches",
                dataCollection = "License plate numbers, vehicle make/model/color, timestamps, direction of travel"
            )
            DeviceType.AIRTAG -> DeviceInfoForDescription(
                summary = "Apple AirTag Bluetooth tracker",
                purpose = "Item tracker using Apple's Find My network of billions of devices",
                dataCollection = "Precise location tracking through crowdsourced Bluetooth detection"
            )
            DeviceType.ROGUE_AP -> DeviceInfoForDescription(
                summary = "Suspicious or rogue WiFi access point",
                purpose = "May attempt to intercept network traffic through evil twin attacks",
                dataCollection = "Network traffic, credentials if connected without VPN, browsing activity"
            )
            else -> DeviceInfoForDescription(
                summary = "${deviceType.displayName}",
                purpose = "Surveillance or monitoring device with variable capabilities",
                dataCollection = "Depends on device type - may include location, identifiers, or behavioral data"
            )
        }
    }

    private fun generateTriggerIndicators(
        detection: Detection,
        enrichedData: EnrichedDetectorData?
    ): List<String> {
        val indicators = mutableListOf<String>()

        // Add matched patterns if available
        detection.matchedPatterns?.let {
            indicators.add("Pattern match: $it")
        }

        // Add signal-based indicators
        if (detection.rssi > -50) {
            indicators.add("Very strong signal (${detection.rssi} dBm) indicates close proximity")
        }

        // Add enriched data indicators
        when (enrichedData) {
            is EnrichedDetectorData.Cellular -> {
                val analysis = enrichedData.analysis
                if (analysis.encryptionDowngraded) {
                    indicators.add("Encryption downgrade detected: ${analysis.encryptionDowngradeChain.joinToString(" -> ")}")
                }
                if (analysis.impossibleSpeed) {
                    indicators.add("Impossible tower movement detected (suggests fake cell tower)")
                }
                if (analysis.downgradeWithSignalSpike) {
                    indicators.add("Classic IMSI catcher signature: encryption downgrade with signal spike")
                }
                if (analysis.cellTrustScore < 30) {
                    indicators.add("Unfamiliar cell tower (trust score: ${analysis.cellTrustScore}%)")
                }
            }
            is EnrichedDetectorData.Gnss -> {
                val analysis = enrichedData.analysis
                if (analysis.cn0TooUniform) {
                    indicators.add("Suspiciously uniform signal strength across satellites")
                }
                if (analysis.lowElevHighSignalCount > 0) {
                    indicators.add("${analysis.lowElevHighSignalCount} satellites with impossible signal characteristics")
                }
                if (analysis.missingConstellations.isNotEmpty()) {
                    indicators.add("Missing expected satellite constellations: ${analysis.missingConstellations.joinToString { it.code }}")
                }
            }
            is EnrichedDetectorData.Ultrasonic -> {
                val analysis = enrichedData.analysis
                if (analysis.followingUser) {
                    indicators.add("Same beacon detected at ${analysis.locationsDetected} different locations you visited")
                }
                indicators.add("Frequency: ${analysis.frequencyHz} Hz matches ${analysis.matchedSource.displayName} signature")
            }
            is EnrichedDetectorData.WifiFollowing -> {
                val analysis = enrichedData.analysis
                indicators.add("Network seen ${analysis.sightingCount} times at ${analysis.distinctLocations} locations")
                if (analysis.leadsUser) {
                    indicators.add("SUSPICIOUS: Network appears at locations BEFORE you arrive")
                }
                if (analysis.vehicleMounted) {
                    indicators.add("Movement pattern suggests vehicle-mounted device")
                }
            }
            is EnrichedDetectorData.RfEnvironment -> {
                indicators.add("RF anomaly: ${enrichedData.anomalyType} (${enrichedData.anomalyConfidence})")
                if (enrichedData.hiddenNetworkAnalysis?.hiddenSignalStrongerThanVisible == true) {
                    indicators.add("Hidden networks have stronger signals than visible (surveillance indicator)")
                }
                if (enrichedData.hiddenNetworkAnalysis?.simultaneousAppearance == true) {
                    indicators.add("Multiple hidden networks appeared simultaneously (coordinated deployment)")
                }
                if (enrichedData.environmentStatus?.jammerSuspected == true) {
                    indicators.add("Sustained WiFi signal drop pattern consistent with RF jamming")
                }
                enrichedData.contributingFactors.take(3).forEach { indicators.add(it) }
                if (enrichedData.isWifiProxyBased) {
                    indicators.add("Note: Detection inferred from WiFi data, not direct RF measurement")
                }
            }
            is EnrichedDetectorData.Ble -> {
                val analysis = enrichedData.analysis
                if (analysis.isFollowingUser) {
                    indicators.add("FOLLOWING: Tracker detected at ${analysis.distinctLocationCount} distinct locations")
                }
                if (analysis.isPossessionSignal) {
                    indicators.add("Strong consistent signal (${analysis.averageRssi} dBm) suggests device on your person")
                }
                if (analysis.isBleSpam) {
                    indicators.add("BLE spam attack: ${analysis.spamEventsCount} events at ${String.format("%.1f", analysis.spamEventsPerSecond)}/sec")
                }
                if (analysis.advertisingRate > 10f) {
                    indicators.add("Elevated advertising rate: ${String.format("%.1f", analysis.advertisingRate)} pps (normal ~1 pps)")
                }
                analysis.suspicionReasons.take(3).forEach { indicators.add(it) }
            }
            else -> {}
        }

        // Add detection method
        indicators.add("Detected via: ${detection.detectionMethod.displayName}")

        return indicators
    }

    /**
     * Current privilege mode, set externally before generating descriptions.
     * This allows the rule-based generator to factor in detection limitations.
     */
    var privilegeMode: PrivilegeMode? = null

    private fun generateConfidenceAssessment(
        detection: Detection,
        enrichedData: EnrichedDetectorData?
    ): Pair<Int, String> {
        var confidence = 50 // Base confidence
        val reasons = mutableListOf<String>()

        // Adjust based on detection method reliability
        when (detection.detectionMethod) {
            DetectionMethod.MANUFACTURER_OUI -> {
                confidence += 20
                reasons.add("Manufacturer fingerprint confirmed")
            }
            DetectionMethod.SSID_PATTERN -> {
                confidence += 15
                reasons.add("SSID matches known pattern")
            }
            DetectionMethod.BEHAVIOR_ANALYSIS -> {
                confidence += 10
                reasons.add("Behavioral analysis match")
            }
            else -> {}
        }

        // Adjust based on enriched data
        when (enrichedData) {
            is EnrichedDetectorData.Cellular -> {
                val analysis = enrichedData.analysis
                confidence = analysis.imsiCatcherScore
                if (analysis.imsiCatcherScore > 70) {
                    reasons.add("High IMSI catcher score (${analysis.imsiCatcherScore}%)")
                } else {
                    reasons.add("IMSI catcher score: ${analysis.imsiCatcherScore}%")
                }
            }
            is EnrichedDetectorData.Gnss -> {
                val analysis = enrichedData.analysis
                confidence = analysis.spoofingLikelihood.toInt()
                reasons.add("Spoofing likelihood: ${analysis.spoofingLikelihood.toInt()}%")
            }
            is EnrichedDetectorData.Ultrasonic -> {
                val analysis = enrichedData.analysis
                confidence = analysis.trackingLikelihood.toInt()
                reasons.add("Tracking likelihood: ${analysis.trackingLikelihood.toInt()}%")
            }
            is EnrichedDetectorData.RfEnvironment -> {
                confidence = enrichedData.rfThreatScore
                reasons.add("RF threat score: ${enrichedData.rfThreatScore}%")
                if (enrichedData.isWifiProxyBased) {
                    reasons.add("Inferred from WiFi data (indirect measurement)")
                }
            }
            else -> {}
        }

        // Adjust based on repeated sightings
        if (detection.seenCount > 3) {
            confidence += 10
            reasons.add("Detected ${detection.seenCount} times - consistent presence")
        }

        // Privilege-aware confidence adjustments
        applyPrivilegeModeAdjustments(detection, enrichedData, confidence, reasons)?.let {
            confidence = it.first
            // reasons already mutated in-place
        }

        confidence = confidence.coerceIn(0, 100)
        val reasoning = reasons.joinToString("; ")

        return Pair(confidence, reasoning)
    }

    /**
     * Apply confidence adjustments based on app privilege mode.
     * Returns adjusted confidence or null if no adjustment needed.
     */
    private fun applyPrivilegeModeAdjustments(
        detection: Detection,
        enrichedData: EnrichedDetectorData?,
        currentConfidence: Int,
        reasons: MutableList<String>
    ): Pair<Int, Unit>? {
        val mode = privilegeMode ?: return null
        var adjusted = currentConfidence

        // Shannon SDM detections are definitive
        if (detection.detectionSource == DetectionSource.SHANNON_SDM) {
            adjusted += 20
            reasons.add("Shannon SDM: definitive modem-level detection")
            return Pair(adjusted, Unit)
        }

        when (detection.protocol) {
            DetectionProtocol.CELLULAR -> {
                when (mode) {
                    is PrivilegeMode.Sideload -> {
                        adjusted -= 15
                        reasons.add("Sideload mode: cannot confirm IMSI capture")
                    }
                    is PrivilegeMode.System -> {
                        adjusted -= 10
                        reasons.add("System mode: enhanced monitoring but no IMEI/IMSI access")
                    }
                    is PrivilegeMode.OEM -> {
                        if (enrichedData is EnrichedDetectorData.Shannon) {
                            adjusted += 10
                            reasons.add("OEM mode with Shannon SDM confirmation")
                        }
                    }
                }
            }
            DetectionProtocol.RF -> {
                if (mode is PrivilegeMode.Sideload || mode is PrivilegeMode.System) {
                    adjusted -= 20
                    reasons.add("WiFi-proxy inference only (no direct RF measurement)")
                }
            }
            DetectionProtocol.WIFI -> {
                if (mode is PrivilegeMode.Sideload) {
                    when (detection.detectionMethod) {
                        DetectionMethod.WIFI_DEAUTH_ATTACK, DetectionMethod.WIFI_KARMA_ATTACK -> {
                            adjusted -= 10
                            reasons.add("Heuristic only (no 802.11 monitor mode)")
                        }
                        else -> {}
                    }
                }
            }
            DetectionProtocol.BLUETOOTH_LE -> {
                if (mode is PrivilegeMode.Sideload) {
                    adjusted -= 5
                    reasons.add("Duty-cycled scanning, randomized MACs")
                }
            }
            else -> {}
        }

        return Pair(adjusted, Unit)
    }

    private fun generateFalsePositiveReasons(
        detection: Detection,
        enrichedData: EnrichedDetectorData?
    ): List<String> {
        val reasons = mutableListOf<String>()

        when (enrichedData) {
            is EnrichedDetectorData.Cellular -> {
                val analysis = enrichedData.analysis
                if (analysis.isLikelyNormalHandoff) {
                    reasons.add("Normal cell tower handoff while moving")
                }
                if (analysis.isLikelyCarrierBehavior) {
                    reasons.add("Known carrier network optimization behavior")
                }
                if (analysis.isLikelyEdgeCoverage) {
                    reasons.add("You are at the edge of cell coverage")
                }
                if (analysis.isLikely5gBeamSteering) {
                    reasons.add("Normal 5G beam steering/management")
                }
                reasons.addAll(analysis.fpIndicators)
            }
            is EnrichedDetectorData.Gnss -> {
                val analysis = enrichedData.analysis
                if (analysis.isLikelyUrbanMultipath) {
                    reasons.add("Urban multipath - GPS signals bouncing off buildings")
                }
                if (analysis.isLikelyIndoorSignalLoss) {
                    reasons.add("Indoor signal attenuation - normal for being inside")
                }
                if (analysis.isLikelyNormalOperation) {
                    reasons.add("Normal GPS variation during position calculation")
                }
                reasons.addAll(analysis.fpIndicators)
            }
            is EnrichedDetectorData.Ultrasonic -> {
                val analysis = enrichedData.analysis
                if (analysis.isLikelyAmbientNoise) {
                    reasons.add("Ambient ultrasonic noise (electronics, HVAC, etc.)")
                }
                if (analysis.isLikelyDeviceArtifact) {
                    reasons.add("Audio artifact from your device's hardware")
                }
                reasons.addAll(analysis.fpIndicators)
            }
            is EnrichedDetectorData.WifiFollowing -> {
                val analysis = enrichedData.analysis
                if (analysis.isLikelyNeighborNetwork) {
                    reasons.add("Common neighborhood WiFi visible from multiple spots")
                }
                if (analysis.isLikelyMobileHotspot) {
                    reasons.add("Family member or coworker's mobile hotspot")
                }
                if (analysis.isLikelyCommuterDevice) {
                    reasons.add("Someone on your same commute route (not following you)")
                }
                if (analysis.isLikelyPublicTransit) {
                    reasons.add("Public transit WiFi you use regularly")
                }
                reasons.addAll(analysis.fpIndicators)
            }
            is EnrichedDetectorData.RfEnvironment -> {
                reasons.addAll(enrichedData.fpIndicators)
                if (enrichedData.isWifiProxyBased) {
                    reasons.add("Detection based on WiFi proxy data, not direct RF measurement")
                }
            }
            else -> {
                // Generic false positive reasons based on device type
                when (detection.deviceType) {
                    DeviceType.RING_DOORBELL, DeviceType.NEST_CAMERA,
                    DeviceType.WYZE_CAMERA, DeviceType.ARLO_CAMERA -> {
                        reasons.add("Consumer home security device owned by neighbor")
                    }
                    DeviceType.BLUETOOTH_BEACON -> {
                        reasons.add("Common retail/commercial beacon for indoor navigation")
                    }
                    else -> {}
                }
            }
        }

        return reasons
    }

    private fun assessContext(
        detection: Detection,
        contextualInsights: PromptTemplates.ContextualInsights?
    ): Pair<Boolean?, String?> {
        var isNormalForLocation: Boolean? = null
        var environmentalContext: String? = null

        contextualInsights?.let {
            isNormalForLocation = it.isKnownLocation
            if (it.isKnownLocation) {
                environmentalContext = "This location is in your regular travel pattern"
            }
        }

        // TODO: In future, could integrate with location services to detect:
        // - Near government buildings
        // - Near protest locations
        // - Airport/transit hub areas
        // - High-security zones

        return Pair(isNormalForLocation, environmentalContext)
    }

    private fun generateActionableIntelligence(
        detection: Detection,
        isMostLikelyBenign: Boolean,
        threatLevel: ThreatLevel
    ): Triple<PromptTemplates.ActionItem?, String, String?> {
        // If likely benign, downgrade actions
        if (isMostLikelyBenign) {
            return Triple(
                null, // No immediate action needed
                "Continue normal monitoring. We're logging this for pattern analysis.",
                null // No documentation needed
            )
        }

        val immediateAction: PromptTemplates.ActionItem?
        val monitoringRec: String
        val docSuggestion: String?

        when (threatLevel) {
            ThreatLevel.CRITICAL -> {
                immediateAction = PromptTemplates.ActionItem(
                    action = when (detection.deviceType) {
                        DeviceType.STINGRAY_IMSI -> "Enable airplane mode or use a Faraday bag NOW"
                        DeviceType.GNSS_SPOOFER -> "DO NOT rely on GPS navigation - verify your location visually"
                        else -> "Consider leaving this area if safety allows"
                    },
                    urgency = PromptTemplates.ActionUrgency.IMMEDIATE,
                    reason = "Active surveillance device detected with high confidence"
                )
                monitoringRec = "High alert - check back frequently for changes"
                docSuggestion = "Screenshot this detection with timestamp for documentation"
            }
            ThreatLevel.HIGH -> {
                immediateAction = PromptTemplates.ActionItem(
                    action = "Use encrypted communications (Signal, WhatsApp) only",
                    urgency = PromptTemplates.ActionUrgency.SOON,
                    reason = "Your communications may be monitored"
                )
                monitoringRec = "Monitor for recurring detections in this area"
                docSuggestion = "Note this location as a surveillance hotspot"
            }
            ThreatLevel.MEDIUM -> {
                immediateAction = null
                monitoringRec = "Check this area periodically for changes"
                docSuggestion = "Optional: log this detection in your privacy journal"
            }
            else -> {
                immediateAction = null
                monitoringRec = "Standard monitoring - no special action needed"
                docSuggestion = null
            }
        }

        return Triple(immediateAction, monitoringRec, docSuggestion)
    }

    private fun getResourcesForDeviceType(deviceType: DeviceType): List<String> {
        return when (deviceType) {
            DeviceType.STINGRAY_IMSI -> listOf(
                "EFF Guide to IMSI Catchers: eff.org/pages/cell-site-simulatorsimsi-catchers",
                "ACLU StingRay Tracking Devices: aclu.org/issues/privacy-technology/surveillance-technologies/stingray-tracking-devices"
            )
            DeviceType.FLOCK_SAFETY_CAMERA, DeviceType.LICENSE_PLATE_READER -> listOf(
                "EFF Atlas of Surveillance: atlasofsurveillance.org",
                "ACLU You Are Being Tracked: aclu.org/issues/privacy-technology/location-tracking/you-are-being-tracked"
            )
            DeviceType.RING_DOORBELL -> listOf(
                "Ring & Police Partnerships: eff.org/deeplinks/2019/08/five-concerns-about-amazon-rings-deals-police"
            )
            DeviceType.AIRTAG, DeviceType.TILE_TRACKER, DeviceType.SAMSUNG_SMARTTAG -> listOf(
                "Apple AirTag Safety: support.apple.com/en-us/HT212227"
            )
            else -> emptyList()
        }
    }

    private fun generateSimpleExplanation(detection: Detection, isMostLikelyBenign: Boolean): String {
        val deviceType = detection.deviceType

        if (isMostLikelyBenign) {
            return when (deviceType) {
                DeviceType.STINGRAY_IMSI ->
                    "Your phone's connection changed, but this is probably just normal cell tower behavior. " +
                    "Think of it like switching lanes on a highway - happens all the time."
                DeviceType.GNSS_SPOOFER, DeviceType.GNSS_JAMMER ->
                    "Your GPS had some trouble, but this is most likely due to being indoors or near tall buildings. " +
                    "It's like how your car GPS sometimes loses signal in a parking garage."
                DeviceType.ULTRASONIC_BEACON ->
                    "We detected a high-frequency sound, but it's probably just background noise from electronics. " +
                    "Many everyday devices make sounds we can't hear."
                else ->
                    "We detected a ${deviceType.displayName}, but it's most likely a normal device that poses no threat to you."
            }
        }

        return when (deviceType) {
            DeviceType.STINGRAY_IMSI ->
                "A device that pretends to be a cell tower was detected. It can potentially see your phone's ID " +
                "and track your location. Think of it like someone setting up a fake checkpoint to see who passes by."
            DeviceType.GNSS_SPOOFER ->
                "Something is trying to trick your GPS into showing a wrong location. " +
                "It's like someone switching street signs to send you the wrong way."
            DeviceType.FLOCK_SAFETY_CAMERA ->
                "A camera that reads license plates was detected. It takes photos of every car that passes by " +
                "and stores them in a database that police can search."
            DeviceType.RING_DOORBELL ->
                "A Ring doorbell camera was detected nearby. These cameras record video and audio, " +
                "and the footage can be shared with police even without a warrant in some cases."
            DeviceType.AIRTAG ->
                "An Apple AirTag tracker was detected. If this isn't yours, someone might be tracking your location. " +
                "Check your belongings and car for a small round device."
            else ->
                "A ${deviceType.displayName} was detected. ${detection.detectionMethod.description}"
        }
    }

    private fun generateTechnicalDetails(detection: Detection, enrichedData: EnrichedDetectorData?): String {
        val details = StringBuilder()

        details.appendLine("=== Technical Detection Details ===")
        details.appendLine("Device Type: ${detection.deviceType.name}")
        details.appendLine("Protocol: ${detection.protocol.displayName}")
        details.appendLine("Detection Method: ${detection.detectionMethod.name}")
        details.appendLine("RSSI: ${detection.rssi} dBm")
        details.appendLine("Threat Score: ${detection.threatScore}/100")
        detection.macAddress?.let { details.appendLine("MAC: $it") }
        detection.manufacturer?.let { details.appendLine("Manufacturer OUI: $it") }
        detection.ssid?.let { details.appendLine("SSID: $it") }
        detection.matchedPatterns?.let { details.appendLine("Matched Patterns: $it") }

        when (enrichedData) {
            is EnrichedDetectorData.Cellular -> {
                val a = enrichedData.analysis
                details.appendLine("\n=== Cellular Analysis ===")
                details.appendLine("IMSI Catcher Score: ${a.imsiCatcherScore}%")
                details.appendLine("Encryption Chain: ${a.encryptionDowngradeChain.joinToString(" -> ")}")
                details.appendLine("Current Encryption: ${a.currentEncryption.displayName}")
                details.appendLine("Cell Trust Score: ${a.cellTrustScore}%")
                details.appendLine("Movement: ${a.movementType.displayName} (${String.format("%.1f", a.speedKmh)} km/h)")
                details.appendLine("False Positive Likelihood: ${String.format("%.0f", a.falsePositiveLikelihood)}%")
            }
            is EnrichedDetectorData.Gnss -> {
                val a = enrichedData.analysis
                details.appendLine("\n=== GNSS Analysis ===")
                details.appendLine("Spoofing Likelihood: ${String.format("%.0f", a.spoofingLikelihood)}%")
                details.appendLine("Jamming Likelihood: ${String.format("%.0f", a.jammingLikelihood)}%")
                details.appendLine("Constellation Match: ${a.constellationMatchScore}%")
                details.appendLine("C/N0: ${String.format("%.1f", a.currentCn0Mean)} dB-Hz")
                details.appendLine("Geometry Score: ${String.format("%.0f", a.geometryScore * 100)}%")
                details.appendLine("False Positive Likelihood: ${String.format("%.0f", a.falsePositiveLikelihood)}%")
            }
            is EnrichedDetectorData.Ultrasonic -> {
                val a = enrichedData.analysis
                details.appendLine("\n=== Ultrasonic Analysis ===")
                details.appendLine("Frequency: ${a.frequencyHz} Hz")
                details.appendLine("Matched Source: ${a.matchedSource.displayName}")
                details.appendLine("Source Category: ${a.sourceCategory.displayName}")
                details.appendLine("Tracking Likelihood: ${String.format("%.0f", a.trackingLikelihood)}%")
                details.appendLine("SNR: ${String.format("%.1f", a.snrDb)} dB")
                details.appendLine("Persistence: ${String.format("%.0f", a.persistenceScore * 100)}%")
            }
            is EnrichedDetectorData.WifiFollowing -> {
                val a = enrichedData.analysis
                details.appendLine("\n=== WiFi Following Analysis ===")
                details.appendLine("Following Confidence: ${String.format("%.0f", a.followingConfidence)}%")
                details.appendLine("Sightings: ${a.sightingCount} at ${a.distinctLocations} locations")
                details.appendLine("Path Correlation: ${String.format("%.0f", a.pathCorrelation * 100)}%")
                details.appendLine("Time Pattern: ${a.timePattern.displayName}")
                details.appendLine("Signal Consistency: ${String.format("%.0f", a.signalConsistency * 100)}%")
            }
            is EnrichedDetectorData.RfEnvironment -> {
                details.appendLine("\n=== RF Environment Analysis ===")
                details.appendLine("Anomaly Type: ${enrichedData.anomalyType}")
                details.appendLine("Confidence: ${enrichedData.anomalyConfidence}")
                details.appendLine("RF Threat Score: ${enrichedData.rfThreatScore}%")
                details.appendLine("Total Networks: ${enrichedData.totalNetworks}")
                details.appendLine("Hidden Networks: ${enrichedData.hiddenNetworkCount}")
                details.appendLine("Avg Signal: ${enrichedData.avgSignalStrength} dBm")
                details.appendLine("Signal Variance: ${String.format("%.1f", enrichedData.signalVariance)}")
                details.appendLine("WiFi Proxy Based: ${enrichedData.isWifiProxyBased}")
                details.appendLine("False Positive Likelihood: ${String.format("%.0f", enrichedData.falsePositiveLikelihood)}%")
            }
            else -> {}
        }

        return details.toString()
    }

    internal fun estimateFalsePositiveLikelihood(
        detection: Detection,
        enrichedData: EnrichedDetectorData?
    ): Int {
        // Use enriched data if available
        when (enrichedData) {
            is EnrichedDetectorData.Cellular -> return enrichedData.analysis.falsePositiveLikelihood.toInt()
            is EnrichedDetectorData.Gnss -> return enrichedData.analysis.falsePositiveLikelihood.toInt()
            is EnrichedDetectorData.Ultrasonic -> return enrichedData.analysis.falsePositiveLikelihood.toInt()
            is EnrichedDetectorData.WifiFollowing -> return enrichedData.analysis.falsePositiveLikelihood.toInt()
            is EnrichedDetectorData.RfEnvironment -> return enrichedData.falsePositiveLikelihood.toInt()
            is EnrichedDetectorData.Ble -> return enrichedData.analysis.falsePositiveLikelihood.toInt()
            else -> {}
        }

        // Estimate based on device type and threat level
        return when (detection.deviceType) {
            // Consumer devices - high FP likelihood
            DeviceType.RING_DOORBELL, DeviceType.NEST_CAMERA, DeviceType.WYZE_CAMERA,
            DeviceType.ARLO_CAMERA, DeviceType.EUFY_CAMERA, DeviceType.BLINK_CAMERA -> 80

            // Trackers - medium FP if weak signal
            DeviceType.AIRTAG, DeviceType.TILE_TRACKER, DeviceType.SAMSUNG_SMARTTAG ->
                if (detection.rssi < -70) 60 else 20

            // Infrastructure - high FP
            DeviceType.BLUETOOTH_BEACON, DeviceType.RETAIL_TRACKER -> 75

            // Serious threats - low FP if high confidence
            DeviceType.STINGRAY_IMSI, DeviceType.GNSS_SPOOFER, DeviceType.GNSS_JAMMER ->
                if (detection.threatScore > 70) 20 else 50

            else -> 50 // Unknown - 50/50
        }
    }

    private fun generateThreatReasoning(detection: Detection, enrichedData: EnrichedDetectorData?): String {
        val threatLevel = detection.threatLevel
        val deviceType = detection.deviceType

        val baseReason = when (threatLevel) {
            ThreatLevel.CRITICAL -> "This device type can actively intercept or manipulate data"
            ThreatLevel.HIGH -> "This device can collect identifying information about you"
            ThreatLevel.MEDIUM -> "This device may track your presence or behavior"
            ThreatLevel.LOW -> "This device has limited surveillance capability"
            ThreatLevel.INFO -> "This device poses minimal direct privacy risk"
        }

        val specificReason = when (enrichedData) {
            is EnrichedDetectorData.Cellular -> {
                val a = enrichedData.analysis
                when {
                    a.encryptionDowngraded && a.downgradeWithSignalSpike ->
                        "Classic IMSI catcher signature detected: forced encryption downgrade with simultaneous signal spike"
                    a.encryptionDowngraded ->
                        "Your phone's encryption was downgraded, which could allow interception"
                    a.imsiCatcherScore > 70 ->
                        "Multiple indicators suggest cell-site simulator activity"
                    else ->
                        "Some cellular anomalies detected but not conclusive"
                }
            }
            is EnrichedDetectorData.Gnss -> {
                val a = enrichedData.analysis
                when {
                    a.spoofingLikelihood > 70 ->
                        "Satellite signals show characteristics of spoofed/fake signals"
                    a.jammingLikelihood > 70 ->
                        "GPS signal blockage pattern consistent with intentional jamming"
                    else ->
                        "GPS anomalies detected but may be environmental"
                }
            }
            is EnrichedDetectorData.RfEnvironment -> {
                when {
                    enrichedData.environmentStatus?.jammerSuspected == true ->
                        "Sustained WiFi signal loss pattern suggests active RF jamming. " +
                        "Note: inferred from WiFi data, not direct RF measurement"
                    enrichedData.hiddenNetworkAnalysis?.hiddenSignalStrongerThanVisible == true &&
                        enrichedData.hiddenNetworkAnalysis?.simultaneousAppearance == true ->
                        "Coordinated hidden network deployment detected with strong signals"
                    enrichedData.rfThreatScore > 70 ->
                        "Multiple RF environment indicators suggest surveillance activity"
                    else ->
                        "RF environment anomaly detected but may be normal urban conditions"
                }
            }
            else -> ""
        }

        return if (specificReason.isNotEmpty()) {
            "$baseReason. $specificReason"
        } else {
            baseReason
        }
    }

    private fun generateBenignExplanation(detection: Detection, fpReasons: List<String>): String {
        val mainReason = fpReasons.firstOrNull() ?: "Environmental factors"

        return "This is most likely NOT a real threat. $mainReason. " +
               "We're logging this detection for pattern analysis, but no action is needed. " +
               "Common causes include: ${fpReasons.take(3).joinToString(", ").ifEmpty { "normal network behavior" }}."
    }
}
