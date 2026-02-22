package com.flockyou.ai

import android.util.Log
import com.flockyou.data.*
import com.flockyou.data.model.Detection
import com.flockyou.data.model.DeviceType
import com.flockyou.data.model.DetectionMethod
import com.flockyou.data.model.DetectionProtocol
import com.flockyou.data.model.ThreatLevel
import com.flockyou.data.repository.DetectionRepository
import com.flockyou.detection.DetectionRegistry
import com.flockyou.detection.profile.DeviceTypeProfileRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "AnalysisHelpers"

// ==================== CONTEXTUAL INSIGHTS ====================

/**
 * Gather contextual insights for a detection from the detection repository.
 */
internal suspend fun gatherContextualInsights(
    detection: Detection,
    detectionRepository: DetectionRepository
): ContextualInsights {
    val allDetections = detectionRepository.getAllDetectionsSnapshot()

    // Find detections at same location
    val detectionLat = detection.latitude
    val detectionLon = detection.longitude
    val nearbyDetections = if (detectionLat != null && detectionLon != null) {
        allDetections.filter { other ->
            val otherLat = other.latitude
            val otherLon = other.longitude
            otherLat != null && otherLon != null &&
            calculateDistanceMeters(detectionLat, detectionLon, otherLat, otherLon) < CLUSTER_RADIUS_METERS
        }
    } else emptyList()

    // Find same device seen before
    val sameDeviceHistory = allDetections.filter { other ->
        (detection.macAddress != null && other.macAddress == detection.macAddress) ||
        (detection.ssid != null && other.ssid == detection.ssid)
    }.sortedBy { it.timestamp }

    // Analyze time patterns
    val timePattern = analyzeTimePattern(sameDeviceHistory)

    // Detect clusters
    val clusterInfo = if (nearbyDetections.size > 2) {
        "Part of ${nearbyDetections.size}-device surveillance cluster within ${CLUSTER_RADIUS_METERS.toInt()}m"
    } else null

    // Historical context
    val historicalContext = if (sameDeviceHistory.size > 1) {
        val firstSeen = sameDeviceHistory.first().timestamp
        val daysSince = (System.currentTimeMillis() - firstSeen) / (1000 * 60 * 60 * 24)
        "First seen $daysSince days ago, detected ${sameDeviceHistory.size} times"
    } else null

    return ContextualInsights(
        isKnownLocation = nearbyDetections.size > 1,
        locationPattern = if (nearbyDetections.size > 1) "Seen ${nearbyDetections.size} times at this location" else null,
        timePattern = timePattern,
        clusterInfo = clusterInfo,
        historicalContext = historicalContext
    )
}

private fun analyzeTimePattern(detections: List<Detection>): String? {
    if (detections.size < 3) return null

    val hours = detections.map { detection ->
        java.util.Calendar.getInstance().apply {
            timeInMillis = detection.timestamp
        }.get(java.util.Calendar.HOUR_OF_DAY)
    }

    val avgHour = hours.average()
    return when {
        avgHour < 6 -> "Usually active late night (midnight-6am)"
        avgHour < 12 -> "Usually active in morning"
        avgHour < 18 -> "Usually active in afternoon"
        else -> "Usually active in evening/night"
    }
}

// ==================== FALSE POSITIVE CONTEXT ====================

/**
 * Build context information for false positive analysis.
 * Uses contextual insights and detection location to determine user context.
 */
internal fun buildFpContextInfo(
    detection: Detection,
    contextualInsights: ContextualInsights?
): FpContextInfo {
    // Determine time of day
    val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val isNightTime = currentHour < 6 || currentHour >= 22

    // Check if at a known/familiar location
    val isKnownLocation = contextualInsights?.isKnownLocation ?: false

    return FpContextInfo(
        isAtHome = isKnownLocation && detection.latitude != null,
        homeLatitude = if (isKnownLocation) detection.latitude else null,
        homeLongitude = if (isKnownLocation) detection.longitude else null,
        isAtWork = false, // Would need user preference storage
        isKnownSafeArea = isKnownLocation,
        isNightTime = isNightTime,
        recentlyTraveled = false // Would need location history
    )
}

// ==================== FEEDBACK ADJUSTMENT ====================

/**
 * Adjust analysis results based on historical user feedback.
 *
 * This method applies confidence adjustments learned from user interactions:
 * - Lowers confidence if users frequently dismiss/mark as FP for this device type
 * - Raises confidence if users frequently investigate/confirm/report for this device type
 * - Adds contextual notes about historical accuracy
 *
 * @param analysis The original analysis result
 * @param detection The detection being analyzed
 * @param feedbackTracker The tracker to get feedback-based adjustments from
 * @return Adjusted analysis result with feedback-based modifications
 */
internal suspend fun adjustAnalysisWithFeedback(
    analysis: AiAnalysisResult,
    detection: Detection,
    feedbackTracker: AnalysisFeedbackTracker
): AiAnalysisResult {
    return try {
        // Delegate to the feedback tracker for the actual adjustment
        val adjustedResult = feedbackTracker.adjustAnalysisWithFeedback(
            analysis = analysis,
            deviceType = detection.deviceType
        )

        Log.d(TAG, "Feedback adjustment applied for ${detection.deviceType}: " +
            "confidence ${analysis.confidence} -> ${adjustedResult.confidence}")

        adjustedResult
    } catch (e: Exception) {
        Log.e(TAG, "Error applying feedback adjustment", e)
        // Return original analysis if adjustment fails
        analysis
    }
}

// ==================== PROMPT SELECTION ====================

/**
 * Select the best prompt template based on detection type and available enriched data.
 * Priority order:
 * 1. Enriched detector-specific prompts (when enriched data is available)
 * 2. Profile AI prompt template (device-type specific templates from DeviceTypeProfile)
 * 3. Chain-of-thought reasoning (for high-threat or complex detections)
 * 4. Few-shot prompting (for standard detections)
 */
internal fun selectPromptForDetection(
    detection: Detection,
    contextualInsights: ContextualInsights?,
    enrichedData: EnrichedDetectorData?
): String {
    // If we have enriched data, use detector-specific prompts
    if (enrichedData != null) {
        return when (enrichedData) {
            is EnrichedDetectorData.Cellular ->
                PromptTemplates.buildCellularEnrichedPrompt(detection, enrichedData.analysis)
            is EnrichedDetectorData.Gnss ->
                PromptTemplates.buildGnssEnrichedPrompt(detection, enrichedData.analysis)
            is EnrichedDetectorData.Ultrasonic ->
                PromptTemplates.buildUltrasonicEnrichedPrompt(detection, enrichedData.analysis)
            is EnrichedDetectorData.WifiFollowing ->
                PromptTemplates.buildWifiFollowingEnrichedPrompt(detection, enrichedData.analysis)
            is EnrichedDetectorData.Satellite ->
                PromptTemplates.buildSatelliteEnrichedPrompt(detection, enrichedData)
            is EnrichedDetectorData.RfEnvironment ->
                PromptTemplates.buildRfEnvironmentEnrichedPrompt(detection, enrichedData)
            is EnrichedDetectorData.Ble ->
                PromptTemplates.buildBleEnrichedPrompt(detection, enrichedData.analysis)
            is EnrichedDetectorData.WifiSsidMatch ->
                PromptTemplates.buildWifiSsidEnrichedPrompt(detection, enrichedData)
            is EnrichedDetectorData.Shannon ->
                PromptTemplates.buildChainOfThoughtPrompt(detection, enrichedData)
        }
    }

    // Check for profile-based AI prompt template
    val profilePrompt = getProfileAiPromptTemplate(detection.deviceType)
    if (profilePrompt != null) {
        return buildProfileBasedPrompt(detection, profilePrompt, contextualInsights)
    }

    // For high-threat or complex detections, use chain-of-thought reasoning
    if (detection.threatLevel == ThreatLevel.CRITICAL ||
        detection.threatLevel == ThreatLevel.HIGH ||
        contextualInsights?.clusterInfo != null) {
        return PromptTemplates.buildChainOfThoughtPrompt(detection, enrichedData)
    }

    // For standard detections, use few-shot prompting
    return PromptTemplates.buildFewShotPrompt(detection, enrichedData)
}

/**
 * Get AI prompt template from profile system.
 * Only DeviceTypeProfileRegistry (centralized profile) has aiPromptTemplate.
 */
private fun getProfileAiPromptTemplate(deviceType: DeviceType): String? {
    // Use DeviceTypeProfileRegistry for AI prompt templates
    val profile = DeviceTypeProfileRegistry.getProfile(deviceType)
    return profile.aiPromptTemplate
}

/**
 * Build a prompt using the profile's AI prompt template.
 * Wraps the template with detection context for comprehensive analysis.
 */
private fun buildProfileBasedPrompt(
    detection: Detection,
    profileTemplate: String,
    contextualInsights: ContextualInsights?
): String {
    val contextSection = contextualInsights?.let {
        buildString {
            appendLine("\n=== CONTEXTUAL DATA ===")
            it.locationPattern?.let { appendLine("Location: $it") }
            it.timePattern?.let { appendLine("Time Pattern: $it") }
            it.clusterInfo?.let { appendLine("Cluster: $it") }
            it.historicalContext?.let { appendLine("History: $it") }
        }
    } ?: ""

    return """<start_of_turn>user
You are a privacy security expert analyzing a detected surveillance device.

$profileTemplate

=== DETECTION DATA ===
Device Type: ${detection.deviceType.displayName}
Protocol: ${detection.protocol.displayName}
Detection Method: ${detection.detectionMethod.displayName}
Signal: ${detection.signalStrength.displayName} (${detection.rssi} dBm)
Threat Level: ${detection.threatLevel.displayName}
Threat Score: ${detection.threatScore}/100
${detection.manufacturer?.let { "Manufacturer: $it" } ?: ""}
${detection.deviceName?.let { "Device Name: $it" } ?: ""}
${detection.ssid?.let { "Network SSID: $it" } ?: ""}
${detection.matchedPatterns?.let { "Matched Patterns: $it" } ?: ""}
$contextSection

Provide your analysis with specific recommendations for this detection.
<end_of_turn>
<start_of_turn>model
"""
}

// ==================== USER-FRIENDLY EXPLANATION ====================

/**
 * Generate user-friendly explanation for a detection at the specified level.
 * Available levels: SIMPLE (for non-technical users), STANDARD, TECHNICAL.
 */
internal suspend fun generateUserFriendlyExplanationInternal(
    detection: Detection,
    level: PromptTemplates.ExplanationLevel,
    mediaPipeLlmClient: MediaPipeLlmClient,
    detectionRegistry: DetectionRegistry
): UserFriendlyExplanation {
    // Try MediaPipe LLM if ready (has prompt-based generation)
    if (mediaPipeLlmClient.isReady()) {
        val prompt = PromptTemplates.buildUserFriendlyPrompt(detection, null, level)
        val llmResponse = mediaPipeLlmClient.generateResponse(prompt)

        // Parse LLM response if available
        if (llmResponse != null) {
            return LlmOutputParser.parseUserFriendlyExplanation(llmResponse)
        }
    }

    // Fallback to rule-based (also used for Gemini Nano since it doesn't have prompt-based API)
    return generateRuleBasedExplanation(detection, level, detectionRegistry)
}

/**
 * Generate rule-based user-friendly explanation as fallback.
 */
private fun generateRuleBasedExplanation(
    detection: Detection,
    level: PromptTemplates.ExplanationLevel,
    detectionRegistry: DetectionRegistry
): UserFriendlyExplanation {
    val deviceInfo = getComprehensiveDeviceInfo(detection.deviceType)
    val recommendations = getSmartRecommendations(detection, null, detectionRegistry)

    val (headline, whatIsHappening, whyItMatters) = when (level) {
        PromptTemplates.ExplanationLevel.SIMPLE -> Triple(
            "Device Found Nearby",
            "A ${detection.deviceType.displayName} was detected. " +
                "This is a device that ${deviceInfo.simpleDescription ?: "can monitor activity"}.",
            deviceInfo.simplePrivacyImpact ?: "This device may be recording information about you."
        )
        PromptTemplates.ExplanationLevel.STANDARD -> Triple(
            "${detection.deviceType.displayName} Detected",
            deviceInfo.description,
            "Privacy Impact: ${detection.threatLevel.displayName}. ${deviceInfo.privacyImpact ?: ""}"
        )
        PromptTemplates.ExplanationLevel.TECHNICAL -> Triple(
            "${detection.deviceType.displayName} - ${detection.detectionMethod.displayName}",
            "${deviceInfo.description}\n\nDetection: ${detection.detectionMethod.description}",
            "Threat Score: ${detection.threatScore}/100. ${detection.matchedPatterns ?: ""}"
        )
    }

    val urgency = when (detection.threatLevel) {
        ThreatLevel.CRITICAL -> UrgencyLevel.IMMEDIATE
        ThreatLevel.HIGH -> UrgencyLevel.HIGH
        ThreatLevel.MEDIUM -> UrgencyLevel.MEDIUM
        else -> UrgencyLevel.LOW
    }

    return UserFriendlyExplanation(
        headline = headline,
        whatIsHappening = whatIsHappening,
        whyItMatters = whyItMatters,
        whatToDo = recommendations.take(3),
        urgency = urgency
    )
}

// ==================== CACHE MANAGEMENT ====================

/**
 * Cache entry for analysis results with variable expiry based on device type.
 */
internal data class CacheEntry(
    val result: AiAnalysisResult,
    val timestamp: Long,
    val detection: Detection,
    val expiryMs: Long
)

/** Fast-path cache data */
internal data class CachedAnalysis(
    val result: AiAnalysisResult,
    val timestamp: Long,
    val expiryMs: Long
)

/** Cache statistics tracking */
data class CacheStats(
    var hits: Int = 0,
    var misses: Int = 0,
    var fastPathHits: Int = 0,
    var semanticHits: Int = 0
) {
    val hitRate: Float get() = if (hits + misses > 0) hits.toFloat() / (hits + misses) else 0f
    val totalRequests: Int get() = hits + misses
    fun reset() { hits = 0; misses = 0; fastPathHits = 0; semanticHits = 0 }
}

// Cache constants
internal const val CACHE_EXPIRY_MS = 30 * 60 * 1000L // 30 minutes
internal const val MAX_CACHE_SIZE = 100
internal const val CACHE_EXPIRY_CONSUMER_DEVICE_MS = 2 * 60 * 60 * 1000L // 2 hours for Ring, Nest, etc.
internal const val CACHE_EXPIRY_INFRASTRUCTURE_MS = 2 * 60 * 60 * 1000L // 2 hours for WiFi routers
internal const val CACHE_EXPIRY_UNKNOWN_MS = 30 * 60 * 1000L // 30 min for unknown/suspicious

// Common benign device types for cache pre-population
internal val consumerDeviceTypes = setOf(
    DeviceType.RING_DOORBELL,
    DeviceType.NEST_CAMERA,
    DeviceType.WYZE_CAMERA,
    DeviceType.ARLO_CAMERA,
    DeviceType.EUFY_CAMERA,
    DeviceType.BLINK_CAMERA,
    DeviceType.SIMPLISAFE_DEVICE,
    DeviceType.ADT_DEVICE,
    DeviceType.VIVINT_DEVICE,
    DeviceType.AMAZON_SIDEWALK
)

internal val infrastructureDeviceTypes = setOf(
    DeviceType.BLUETOOTH_BEACON,
    DeviceType.RETAIL_TRACKER
)

/**
 * Generate a fast-path cache key using device type + detection method + protocol.
 * This allows quick lookups for identical device classification patterns.
 */
internal fun getFastCacheKey(detection: Detection): String {
    return "${detection.deviceType.name}:${detection.detectionMethod.name}:${detection.protocol.name}"
}

/**
 * Get appropriate cache expiry time based on device type classification.
 * Consumer devices and infrastructure get longer expiry (2 hours),
 * unknown/suspicious devices keep shorter expiry (30 min).
 */
internal fun getCacheExpiryForDevice(deviceType: DeviceType): Long {
    return when {
        deviceType in consumerDeviceTypes -> CACHE_EXPIRY_CONSUMER_DEVICE_MS
        deviceType in infrastructureDeviceTypes -> CACHE_EXPIRY_INFRASTRUCTURE_MS
        // Unknown or suspicious device types get shorter expiry
        deviceType == DeviceType.UNKNOWN_SURVEILLANCE -> CACHE_EXPIRY_UNKNOWN_MS
        deviceType == DeviceType.STINGRAY_IMSI -> CACHE_EXPIRY_UNKNOWN_MS
        deviceType == DeviceType.SURVEILLANCE_VAN -> CACHE_EXPIRY_UNKNOWN_MS
        deviceType == DeviceType.HIDDEN_CAMERA -> CACHE_EXPIRY_UNKNOWN_MS
        deviceType == DeviceType.ROGUE_AP -> CACHE_EXPIRY_UNKNOWN_MS
        deviceType == DeviceType.TRACKING_DEVICE -> CACHE_EXPIRY_UNKNOWN_MS
        deviceType == DeviceType.GNSS_SPOOFER -> CACHE_EXPIRY_UNKNOWN_MS
        deviceType == DeviceType.GNSS_JAMMER -> CACHE_EXPIRY_UNKNOWN_MS
        deviceType == DeviceType.RF_JAMMER -> CACHE_EXPIRY_UNKNOWN_MS
        // Default to standard 30-minute expiry
        else -> CACHE_EXPIRY_MS
    }
}

/**
 * Try to get a result from the fast-path cache.
 * Returns null if no valid cached result exists.
 */
internal fun tryFastPathCache(
    detection: Detection,
    fastPathCache: ConcurrentHashMap<String, CachedAnalysis>,
    cacheStats: CacheStats
): AiAnalysisResult? {
    val key = getFastCacheKey(detection)
    val cached = fastPathCache[key] ?: return null
    val now = System.currentTimeMillis()

    if (now - cached.timestamp > cached.expiryMs) {
        // Entry expired, remove it
        fastPathCache.remove(key)
        return null
    }

    cacheStats.fastPathHits++
    cacheStats.hits++
    Log.d(TAG, "Fast-path cache hit for key: $key")
    return cached.result
}

/**
 * Add a result to the fast-path cache.
 */
internal fun addToFastPathCache(
    detection: Detection,
    result: AiAnalysisResult,
    fastPathCache: ConcurrentHashMap<String, CachedAnalysis>
) {
    val key = getFastCacheKey(detection)
    val expiryMs = getCacheExpiryForDevice(detection.deviceType)
    fastPathCache[key] = CachedAnalysis(
        result = result,
        timestamp = System.currentTimeMillis(),
        expiryMs = expiryMs
    )
    Log.d(TAG, "Added to fast-path cache: $key (expiry: ${expiryMs / 1000 / 60} min)")
}

/**
 * Prune analysis and fast-path caches to prevent unbounded growth.
 */
internal fun pruneCache(
    analysisCache: MutableMap<String, CacheEntry>,
    fastPathCache: ConcurrentHashMap<String, CachedAnalysis>
) {
    if (analysisCache.size >= MAX_CACHE_SIZE) {
        val now = System.currentTimeMillis()
        // Remove expired entries using each entry's own expiry time
        val expired = analysisCache.entries
            .filter { now - it.value.timestamp > it.value.expiryMs }
            .map { it.key }
        expired.forEach { analysisCache.remove(it) }

        if (analysisCache.size >= MAX_CACHE_SIZE) {
            val oldest = analysisCache.entries
                .sortedBy { it.value.timestamp }
                .take(MAX_CACHE_SIZE / 4)
                .map { it.key }
            oldest.forEach { analysisCache.remove(it) }
        }
    }

    // Also prune fast-path cache (max 200 entries)
    if (fastPathCache.size > 200) {
        val now = System.currentTimeMillis()
        val expiredKeys = fastPathCache.entries
            .filter { now - it.value.timestamp > it.value.expiryMs }
            .map { it.key }
        expiredKeys.forEach { fastPathCache.remove(it) }
    }
}

// ==================== SEMANTIC CACHE ====================

private const val SEMANTIC_SIMILARITY_THRESHOLD = 0.85f

/**
 * Find a semantically similar cached result.
 * Returns the cached result if a detection with similar characteristics exists.
 */
internal fun findSimilarCachedResult(
    detection: Detection,
    analysisCache: Map<String, CacheEntry>,
    cacheStats: CacheStats,
    semanticCacheEnabled: Boolean = true
): AiAnalysisResult? {
    if (!semanticCacheEnabled) return null

    val now = System.currentTimeMillis()

    for ((_, entry) in analysisCache) {
        // Skip expired entries (use variable expiry from entry)
        if (now - entry.timestamp > entry.expiryMs) continue

        val similarity = computeDetectionSimilarity(detection, entry.detection)
        if (similarity >= SEMANTIC_SIMILARITY_THRESHOLD) {
            cacheStats.semanticHits++
            cacheStats.hits++
            Log.d(TAG, "Semantic cache hit! Similarity: ${(similarity * 100).toInt()}%")
            return entry.result.copy(
                analysis = entry.result.analysis?.let {
                    "$it\n\n_[Analysis from similar detection]_"
                }
            )
        }
    }

    return null
}

/**
 * Compute semantic similarity between two detections.
 * Returns a value between 0.0 (completely different) and 1.0 (identical).
 */
internal fun computeDetectionSimilarity(a: Detection, b: Detection): Float {
    var score = 0f
    var weight = 0f

    // Device type must match (heaviest weight)
    if (a.deviceType == b.deviceType) {
        score += 0.35f
    }
    weight += 0.35f

    // Protocol match
    if (a.protocol == b.protocol) {
        score += 0.15f
    }
    weight += 0.15f

    // Detection method match
    if (a.detectionMethod == b.detectionMethod) {
        score += 0.15f
    }
    weight += 0.15f

    // Threat level match
    if (a.threatLevel == b.threatLevel) {
        score += 0.1f
    }
    weight += 0.1f

    // Signal strength within 10 dBm
    if (kotlin.math.abs(a.rssi - b.rssi) <= 10) {
        score += 0.1f
    }
    weight += 0.1f

    // Threat score within 10 points
    if (kotlin.math.abs(a.threatScore - b.threatScore) <= 10) {
        score += 0.1f
    }
    weight += 0.1f

    // Manufacturer match (if both have it)
    if (a.manufacturer != null && b.manufacturer != null && a.manufacturer == b.manufacturer) {
        score += 0.05f
    }
    weight += 0.05f

    return score / weight
}

// ==================== CACHE WARM-UP ====================

/**
 * Pre-populate the fast-path cache with common benign device patterns.
 * This improves hit rates by caching analysis for devices that are frequently encountered.
 */
internal fun prepopulateCommonPatterns(
    fastPathCache: ConcurrentHashMap<String, CachedAnalysis>
) {
    Log.d(TAG, "Pre-populating common device patterns...")

    // Common consumer smart home devices with pre-built analysis
    val commonPatterns = listOf(
        // Ring devices
        Triple(
            DeviceType.RING_DOORBELL,
            DetectionMethod.SSID_PATTERN,
            "Consumer smart home device. Ring Doorbells are Amazon-owned smart doorbells that can record video and audio. While they contribute to neighborhood surveillance networks, they are legitimate consumer products. Privacy concern is moderate due to data sharing with law enforcement."
        ),
        // Nest/Google cameras
        Triple(
            DeviceType.NEST_CAMERA,
            DetectionMethod.SSID_PATTERN,
            "Consumer smart home device. Google Nest cameras are cloud-connected security cameras. They may share data with Google and, under certain conditions, law enforcement. Privacy concern is moderate."
        ),
        // Wyze cameras
        Triple(
            DeviceType.WYZE_CAMERA,
            DetectionMethod.SSID_PATTERN,
            "Consumer smart home device. Wyze cameras are affordable smart cameras popular for home security. Data is stored in the cloud. Privacy concern is low to moderate."
        ),
        // Arlo cameras
        Triple(
            DeviceType.ARLO_CAMERA,
            DetectionMethod.SSID_PATTERN,
            "Consumer smart home device. Arlo cameras are wireless security cameras with cloud storage. They have good encryption but data is cloud-accessible. Privacy concern is low to moderate."
        ),
        // Eufy cameras
        Triple(
            DeviceType.EUFY_CAMERA,
            DetectionMethod.SSID_PATTERN,
            "Consumer smart home device. Eufy cameras emphasize local storage but have had past privacy controversies regarding cloud uploads. Privacy concern is low to moderate."
        ),
        // Blink cameras
        Triple(
            DeviceType.BLINK_CAMERA,
            DetectionMethod.SSID_PATTERN,
            "Consumer smart home device. Blink is an Amazon-owned camera brand with cloud storage. Similar privacy implications to Ring. Privacy concern is moderate."
        ),
        // SimpliSafe
        Triple(
            DeviceType.SIMPLISAFE_DEVICE,
            DetectionMethod.SSID_PATTERN,
            "Consumer home security system. SimpliSafe is a popular DIY home security system. Professional monitoring available. Privacy concern is low."
        ),
        // ADT
        Triple(
            DeviceType.ADT_DEVICE,
            DetectionMethod.SSID_PATTERN,
            "Professional home security system. ADT is a traditional security company with professional monitoring. Privacy concern is low."
        ),
        // Vivint
        Triple(
            DeviceType.VIVINT_DEVICE,
            DetectionMethod.SSID_PATTERN,
            "Professional smart home security. Vivint provides professional installation and monitoring with smart home integration. Privacy concern is low."
        ),
        // Amazon Sidewalk
        Triple(
            DeviceType.AMAZON_SIDEWALK,
            DetectionMethod.SSID_PATTERN,
            "Amazon Sidewalk network device. Sidewalk creates a shared network using customer devices. This extends Amazon's tracking capabilities but is opt-out. Privacy concern is moderate due to mesh network tracking potential."
        ),
        // Bluetooth beacons (infrastructure)
        Triple(
            DeviceType.BLUETOOTH_BEACON,
            DetectionMethod.BLE_SERVICE_UUID,
            "Retail/commercial Bluetooth beacon. Used for indoor navigation and proximity marketing. Common in stores and malls. Privacy concern is low to moderate depending on location tracking usage."
        ),
        // Retail trackers
        Triple(
            DeviceType.RETAIL_TRACKER,
            DetectionMethod.BLE_SERVICE_UUID,
            "Retail analytics device. Used by stores for foot traffic analysis and customer behavior tracking. Privacy concern is moderate as it tracks movement patterns."
        )
    )

    val now = System.currentTimeMillis()

    for ((deviceType, detectionMethod, analysisText) in commonPatterns) {
        // Create analysis result for each common pattern
        val result = AiAnalysisResult(
            success = true,
            analysis = analysisText,
            modelUsed = "cached_pattern",
            processingTimeMs = 0,
            structuredData = StructuredAnalysis(
                deviceCategory = when (deviceType) {
                    in consumerDeviceTypes -> "Consumer Smart Home"
                    in infrastructureDeviceTypes -> "Commercial Infrastructure"
                    else -> "Unknown"
                },
                surveillanceType = "Passive Observation",
                dataCollectionTypes = listOf("Video", "Audio", "Presence"),
                riskScore = when (deviceType) {
                    DeviceType.RING_DOORBELL, DeviceType.NEST_CAMERA, DeviceType.AMAZON_SIDEWALK -> 30
                    DeviceType.RETAIL_TRACKER -> 40
                    else -> 20
                },
                riskFactors = listOf("Consumer surveillance device", "Data may be cloud-stored"),
                mitigationActions = emptyList(),
                contextualInsights = null
            )
        )

        // Add to fast-path cache with consumer device expiry (2 hours)
        val protocols = listOf(DetectionProtocol.WIFI, DetectionProtocol.BLUETOOTH_LE)
        for (protocol in protocols) {
            val key = "${deviceType.name}:${detectionMethod.name}:${protocol.name}"
            fastPathCache[key] = CachedAnalysis(
                result = result,
                timestamp = now,
                expiryMs = CACHE_EXPIRY_CONSUMER_DEVICE_MS
            )
        }
    }

    Log.d(TAG, "Pre-populated ${fastPathCache.size} fast-path cache entries")
}
