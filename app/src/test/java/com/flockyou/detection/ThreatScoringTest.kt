package com.flockyou.detection

import com.flockyou.data.model.DeviceType
import com.flockyou.data.model.ThreatLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreatScoringTest {

    // ============================================================================
    // 1. Severity Boundary Tests - scoreToSeverity()
    // ============================================================================

    @Test
    fun `scoreToSeverity returns INFO for score 0`() {
        assertEquals(ThreatLevel.INFO, ThreatScoring.scoreToSeverity(0))
    }

    @Test
    fun `scoreToSeverity returns INFO for score 29`() {
        assertEquals(ThreatLevel.INFO, ThreatScoring.scoreToSeverity(29))
    }

    @Test
    fun `scoreToSeverity returns LOW for score 30`() {
        assertEquals(ThreatLevel.LOW, ThreatScoring.scoreToSeverity(30))
    }

    @Test
    fun `scoreToSeverity returns LOW for score 49`() {
        assertEquals(ThreatLevel.LOW, ThreatScoring.scoreToSeverity(49))
    }

    @Test
    fun `scoreToSeverity returns MEDIUM for score 50`() {
        assertEquals(ThreatLevel.MEDIUM, ThreatScoring.scoreToSeverity(50))
    }

    @Test
    fun `scoreToSeverity returns MEDIUM for score 69`() {
        assertEquals(ThreatLevel.MEDIUM, ThreatScoring.scoreToSeverity(69))
    }

    @Test
    fun `scoreToSeverity returns HIGH for score 70`() {
        assertEquals(ThreatLevel.HIGH, ThreatScoring.scoreToSeverity(70))
    }

    @Test
    fun `scoreToSeverity returns HIGH for score 89`() {
        assertEquals(ThreatLevel.HIGH, ThreatScoring.scoreToSeverity(89))
    }

    @Test
    fun `scoreToSeverity returns CRITICAL for score 90`() {
        assertEquals(ThreatLevel.CRITICAL, ThreatScoring.scoreToSeverity(90))
    }

    @Test
    fun `scoreToSeverity returns CRITICAL for score 100`() {
        assertEquals(ThreatLevel.CRITICAL, ThreatScoring.scoreToSeverity(100))
    }

    @Test
    fun `scoreToSeverity returns INFO for negative score`() {
        // Negative scores should still map to INFO (lowest tier)
        assertEquals(ThreatLevel.INFO, ThreatScoring.scoreToSeverity(-10))
    }

    @Test
    fun `scoreToSeverity returns CRITICAL for score above 100`() {
        // Scores above 100 should still map to CRITICAL (highest tier)
        assertEquals(ThreatLevel.CRITICAL, ThreatScoring.scoreToSeverity(150))
    }

    // ============================================================================
    // 2. calculateThreat() Severity Boundary Integration Tests
    // ============================================================================

    @Test
    fun `calculateThreat produces INFO severity for low likelihood consumer device`() {
        val input = ThreatScoring.ThreatInput(
            baseLikelihood = ThreatScoring.BaseLikelihoods.KNOWN_CONSUMER_DEVICE, // 10
            deviceType = DeviceType.RING_DOORBELL, // 0.8 impact
            rssi = -70,
            isConsumerDevice = true
        )
        val result = ThreatScoring.calculateThreat(input)
        assertEquals(ThreatLevel.INFO, result.severity)
        assertTrue("Score should be in INFO range (0-29), was ${result.adjustedScore}",
            result.adjustedScore < 30)
    }

    @Test
    fun `calculateThreat produces HIGH or CRITICAL for confirmed IMSI catcher`() {
        val input = ThreatScoring.ThreatInput(
            baseLikelihood = ThreatScoring.BaseLikelihoods.ENCRYPTION_DOWNGRADE, // 75
            deviceType = DeviceType.STINGRAY_IMSI, // 2.0 impact
            rssi = -55,
            seenCount = 5,
            hasMultipleIndicators = true,
            hasCrossProtocolCorrelation = true
        )
        val result = ThreatScoring.calculateThreat(input)
        assertTrue("Confirmed IMSI catcher should be HIGH or CRITICAL, was ${result.severity}",
            result.severity == ThreatLevel.HIGH || result.severity == ThreatLevel.CRITICAL)
    }

    @Test
    fun `calculateThreat LOW likelihood IMSI catcher stays LOW severity`() {
        // Key design requirement: 20% likelihood IMSI catcher should NOT be HIGH
        val input = ThreatScoring.ThreatInput(
            baseLikelihood = 20,
            deviceType = DeviceType.STINGRAY_IMSI, // 2.0 impact
            rssi = -75,
            seenCount = 1
        )
        val result = ThreatScoring.calculateThreat(input)
        assertTrue("20% likelihood IMSI catcher should be LOW or INFO, was ${result.severity}",
            result.severity == ThreatLevel.LOW || result.severity == ThreatLevel.INFO)
    }

    // ============================================================================
    // 3. Impact Factor Delegation Tests
    // ============================================================================

    @Test
    fun `getImpactFactor delegates to ImpactFactors for maximum impact devices`() {
        val maxImpactDevices = listOf(
            DeviceType.STINGRAY_IMSI,
            DeviceType.CELLEBRITE_FORENSICS,
            DeviceType.GRAYKEY_DEVICE,
            DeviceType.MAN_IN_MIDDLE
        )
        for (type in maxImpactDevices) {
            assertEquals(
                "${type.name}: ThreatScoring should delegate to ImpactFactors",
                ImpactFactors.get(type),
                ThreatScoring.getImpactFactor(type),
                0.001
            )
        }
    }

    @Test
    fun `getImpactFactor delegates to ImpactFactors for consumer devices`() {
        val consumerDevices = listOf(
            DeviceType.RING_DOORBELL,
            DeviceType.NEST_CAMERA,
            DeviceType.WYZE_CAMERA,
            DeviceType.ARLO_CAMERA
        )
        for (type in consumerDevices) {
            assertEquals(
                "${type.name}: ThreatScoring should delegate to ImpactFactors",
                ImpactFactors.get(type),
                ThreatScoring.getImpactFactor(type),
                0.001
            )
        }
    }

    @Test
    fun `getImpactFactor delegates to ImpactFactors for trackers`() {
        val trackers = listOf(
            DeviceType.AIRTAG,
            DeviceType.TILE_TRACKER,
            DeviceType.SAMSUNG_SMARTTAG,
            DeviceType.GENERIC_BLE_TRACKER
        )
        for (type in trackers) {
            assertEquals(
                "${type.name}: ThreatScoring should delegate to ImpactFactors",
                ImpactFactors.get(type),
                ThreatScoring.getImpactFactor(type),
                0.001
            )
        }
    }

    @Test
    fun `getImpactFactor delegates to ImpactFactors for infrastructure devices`() {
        val infrastructure = listOf(
            DeviceType.TRAFFIC_SENSOR,
            DeviceType.TOLL_READER,
            DeviceType.SPEED_CAMERA
        )
        for (type in infrastructure) {
            assertEquals(
                "${type.name}: ThreatScoring should delegate to ImpactFactors",
                ImpactFactors.get(type),
                ThreatScoring.getImpactFactor(type),
                0.001
            )
        }
    }

    // ============================================================================
    // 4. Confidence Adjustment Tests
    // ============================================================================

    @Test
    fun `cross-protocol correlation adds 0_3 confidence`() {
        assertEquals(0.3, ThreatScoring.ConfidenceAdjustments.CROSS_PROTOCOL_CORRELATION, 0.001)
    }

    @Test
    fun `multiple indicators adds 0_2 confidence`() {
        assertEquals(0.2, ThreatScoring.ConfidenceAdjustments.MULTIPLE_CONFIRMING_INDICATORS, 0.001)
    }

    @Test
    fun `known false positive subtracts 0_5 confidence`() {
        assertEquals(-0.5, ThreatScoring.ConfidenceAdjustments.KNOWN_FALSE_POSITIVE_PATTERN, 0.001)
    }

    @Test
    fun `single weak indicator subtracts 0_3 confidence`() {
        assertEquals(-0.3, ThreatScoring.ConfidenceAdjustments.SINGLE_WEAK_INDICATOR, 0.001)
    }

    @Test
    fun `multipath likely subtracts 0_3 confidence`() {
        assertEquals(-0.3, ThreatScoring.ConfidenceAdjustments.MULTIPATH_LIKELY, 0.001)
    }

    @Test
    fun `high signal strength adds 0_1 confidence`() {
        assertEquals(0.1, ThreatScoring.ConfidenceAdjustments.HIGH_SIGNAL_STRENGTH, 0.001)
    }

    @Test
    fun `good signal strength adds 0_05 confidence`() {
        assertEquals(0.05, ThreatScoring.ConfidenceAdjustments.GOOD_SIGNAL_STRENGTH, 0.001)
    }

    @Test
    fun `persistence over time adds 0_2 confidence`() {
        assertEquals(0.2, ThreatScoring.ConfidenceAdjustments.PERSISTENCE_OVER_TIME, 0.001)
    }

    @Test
    fun `common consumer device subtracts 0_2 confidence`() {
        assertEquals(-0.2, ThreatScoring.ConfidenceAdjustments.COMMON_CONSUMER_DEVICE, 0.001)
    }

    @Test
    fun `brief detection subtracts 0_2 confidence`() {
        assertEquals(-0.2, ThreatScoring.ConfidenceAdjustments.BRIEF_DETECTION, 0.001)
    }

    // ============================================================================
    // 5. Confidence Adjustments Applied in calculateThreat()
    // ============================================================================

    @Test
    fun `cross-protocol correlation increases score`() {
        val baseInput = ThreatScoring.ThreatInput(
            baseLikelihood = 50,
            deviceType = DeviceType.BODY_CAMERA, // 1.0 impact
            rssi = -70,
            hasMultipleIndicators = true
        )
        val withCorrelation = baseInput.copy(hasCrossProtocolCorrelation = true)
        val baseResult = ThreatScoring.calculateThreat(baseInput)
        val correlationResult = ThreatScoring.calculateThreat(withCorrelation)
        assertTrue(
            "Cross-protocol correlation should increase score: base=${baseResult.adjustedScore}, with=${correlationResult.adjustedScore}",
            correlationResult.adjustedScore > baseResult.adjustedScore
        )
    }

    @Test
    fun `cross-protocol correlation adds factor to confidence factors list`() {
        val input = ThreatScoring.ThreatInput(
            baseLikelihood = 50,
            deviceType = DeviceType.BODY_CAMERA,
            rssi = -70,
            hasCrossProtocolCorrelation = true
        )
        val result = ThreatScoring.calculateThreat(input)
        assertTrue(
            "Confidence factors should include cross-protocol: ${result.confidenceFactors}",
            result.confidenceFactors.contains("+cross_protocol_confirmed")
        )
    }

    @Test
    fun `known false positive reduces score`() {
        val baseInput = ThreatScoring.ThreatInput(
            baseLikelihood = 50,
            deviceType = DeviceType.BODY_CAMERA,
            rssi = -70,
            hasMultipleIndicators = true
        )
        val fpInput = baseInput.copy(isKnownFalsePositivePattern = true)
        val baseResult = ThreatScoring.calculateThreat(baseInput)
        val fpResult = ThreatScoring.calculateThreat(fpInput)
        assertTrue(
            "Known false positive should reduce score: base=${baseResult.adjustedScore}, fp=${fpResult.adjustedScore}",
            fpResult.adjustedScore < baseResult.adjustedScore
        )
    }

    @Test
    fun `known false positive adds factor to confidence factors list`() {
        val input = ThreatScoring.ThreatInput(
            baseLikelihood = 50,
            deviceType = DeviceType.BODY_CAMERA,
            rssi = -70,
            isKnownFalsePositivePattern = true
        )
        val result = ThreatScoring.calculateThreat(input)
        assertTrue(
            "Confidence factors should include false positive: ${result.confidenceFactors}",
            result.confidenceFactors.contains("-known_false_positive_pattern")
        )
    }

    @Test
    fun `multiple indicators increases score vs single indicator`() {
        val singleInput = ThreatScoring.ThreatInput(
            baseLikelihood = 50,
            deviceType = DeviceType.BODY_CAMERA,
            rssi = -70,
            hasMultipleIndicators = false
        )
        val multiInput = singleInput.copy(hasMultipleIndicators = true)
        val singleResult = ThreatScoring.calculateThreat(singleInput)
        val multiResult = ThreatScoring.calculateThreat(multiInput)
        assertTrue(
            "Multiple indicators should increase score: single=${singleResult.adjustedScore}, multi=${multiResult.adjustedScore}",
            multiResult.adjustedScore > singleResult.adjustedScore
        )
    }

    @Test
    fun `consumer device flag reduces score`() {
        val baseInput = ThreatScoring.ThreatInput(
            baseLikelihood = 50,
            deviceType = DeviceType.RING_DOORBELL,
            rssi = -70,
            hasMultipleIndicators = true
        )
        val consumerInput = baseInput.copy(isConsumerDevice = true)
        val baseResult = ThreatScoring.calculateThreat(baseInput)
        val consumerResult = ThreatScoring.calculateThreat(consumerInput)
        assertTrue(
            "Consumer device flag should reduce score: base=${baseResult.adjustedScore}, consumer=${consumerResult.adjustedScore}",
            consumerResult.adjustedScore < baseResult.adjustedScore
        )
    }

    @Test
    fun `urban multipath reduces GNSS spoofer score`() {
        val baseInput = ThreatScoring.ThreatInput(
            baseLikelihood = 50,
            deviceType = DeviceType.GNSS_SPOOFER,
            rssi = -70,
            hasMultipleIndicators = true,
            environmentalFactors = ThreatScoring.EnvironmentalFactors()
        )
        val urbanInput = baseInput.copy(
            environmentalFactors = ThreatScoring.EnvironmentalFactors(isUrbanArea = true)
        )
        val baseResult = ThreatScoring.calculateThreat(baseInput)
        val urbanResult = ThreatScoring.calculateThreat(urbanInput)
        assertTrue(
            "Urban multipath should reduce GNSS spoofer score: base=${baseResult.adjustedScore}, urban=${urbanResult.adjustedScore}",
            urbanResult.adjustedScore < baseResult.adjustedScore
        )
    }

    @Test
    fun `indoor multipath reduces GNSS jammer score`() {
        val baseInput = ThreatScoring.ThreatInput(
            baseLikelihood = 50,
            deviceType = DeviceType.GNSS_JAMMER,
            rssi = -70,
            hasMultipleIndicators = true,
            environmentalFactors = ThreatScoring.EnvironmentalFactors()
        )
        val indoorInput = baseInput.copy(
            environmentalFactors = ThreatScoring.EnvironmentalFactors(isIndoor = true)
        )
        val baseResult = ThreatScoring.calculateThreat(baseInput)
        val indoorResult = ThreatScoring.calculateThreat(indoorInput)
        assertTrue(
            "Indoor multipath should reduce GNSS jammer score: base=${baseResult.adjustedScore}, indoor=${indoorResult.adjustedScore}",
            indoorResult.adjustedScore < baseResult.adjustedScore
        )
    }

    @Test
    fun `urban environment does not penalize non-GNSS devices`() {
        val baseInput = ThreatScoring.ThreatInput(
            baseLikelihood = 50,
            deviceType = DeviceType.BODY_CAMERA, // Non-GNSS device
            rssi = -70,
            hasMultipleIndicators = true,
            environmentalFactors = ThreatScoring.EnvironmentalFactors()
        )
        val urbanInput = baseInput.copy(
            environmentalFactors = ThreatScoring.EnvironmentalFactors(isUrbanArea = true)
        )
        val baseResult = ThreatScoring.calculateThreat(baseInput)
        val urbanResult = ThreatScoring.calculateThreat(urbanInput)
        assertEquals(
            "Urban environment should not penalize non-GNSS devices",
            baseResult.adjustedScore,
            urbanResult.adjustedScore
        )
    }

    // ============================================================================
    // 6. Edge Cases - Score Clamping
    // ============================================================================

    @Test
    fun `raw score is clamped to maximum 100`() {
        // Very high likelihood + high impact + high confidence should not exceed 100
        val input = ThreatScoring.ThreatInput(
            baseLikelihood = 100,
            deviceType = DeviceType.STINGRAY_IMSI, // 2.0 impact
            rssi = -40, // excellent signal
            seenCount = 10, // persistent
            hasMultipleIndicators = true,
            hasCrossProtocolCorrelation = true,
            matchQuality = ThreatScoring.MatchQuality.EXACT
        )
        val result = ThreatScoring.calculateThreat(input)
        assertTrue("Raw score should not exceed 100, was ${result.rawScore}",
            result.rawScore <= 100)
        assertTrue("Adjusted score should not exceed 100, was ${result.adjustedScore}",
            result.adjustedScore <= 100)
    }

    @Test
    fun `score is clamped to minimum 0`() {
        // Very low likelihood with all negative adjustments
        val input = ThreatScoring.ThreatInput(
            baseLikelihood = 1,
            deviceType = DeviceType.TRAFFIC_SENSOR, // 0.5 impact
            rssi = -95, // very weak signal
            seenCount = 1,
            durationMs = 1000, // brief
            isKnownFalsePositivePattern = true,
            isConsumerDevice = true,
            isInKnownSafeArea = true,
            matchQuality = ThreatScoring.MatchQuality.HEURISTIC
        )
        val result = ThreatScoring.calculateThreat(input)
        assertTrue("Raw score should not be negative, was ${result.rawScore}",
            result.rawScore >= 0)
        assertTrue("Adjusted score should not be negative, was ${result.adjustedScore}",
            result.adjustedScore >= 0)
    }

    @Test
    fun `zero likelihood produces zero score`() {
        val input = ThreatScoring.ThreatInput(
            baseLikelihood = 0,
            deviceType = DeviceType.BODY_CAMERA,
            rssi = -70
        )
        val result = ThreatScoring.calculateThreat(input)
        assertEquals("Zero likelihood should produce zero raw score", 0, result.rawScore)
        assertEquals("Zero likelihood should produce zero adjusted score", 0, result.adjustedScore)
        assertEquals("Zero likelihood should produce INFO severity", ThreatLevel.INFO, result.severity)
    }

    @Test
    fun `confidence is clamped between 0_1 and 1_0`() {
        // Stack all negative adjustments to try to push confidence below 0.1
        val input = ThreatScoring.ThreatInput(
            baseLikelihood = 50,
            deviceType = DeviceType.GNSS_SPOOFER, // triggers urban multipath
            rssi = -95, // very low signal: -0.2
            seenCount = 1,
            durationMs = 1000, // brief: -0.2
            hasMultipleIndicators = false, // single indicator: -0.3
            isKnownFalsePositivePattern = true, // -0.5
            isConsumerDevice = true, // -0.2
            isInKnownSafeArea = true, // -0.15
            matchQuality = ThreatScoring.MatchQuality.HEURISTIC, // -0.2
            environmentalFactors = ThreatScoring.EnvironmentalFactors(isUrbanArea = true) // -0.3 multipath
        )
        val result = ThreatScoring.calculateThreat(input)
        assertTrue("Confidence should be at least 0.1, was ${result.confidence}",
            result.confidence >= 0.1)
    }

    @Test
    fun `confidence does not exceed 1_0`() {
        // Stack all positive adjustments
        val input = ThreatScoring.ThreatInput(
            baseLikelihood = 50,
            deviceType = DeviceType.BODY_CAMERA,
            rssi = -40, // excellent signal: +0.1
            seenCount = 10, // persistent: +0.2
            hasMultipleIndicators = true, // +0.2
            hasCrossProtocolCorrelation = true, // +0.3
            matchQuality = ThreatScoring.MatchQuality.EXACT // +0.15
        )
        val result = ThreatScoring.calculateThreat(input)
        assertTrue("Confidence should not exceed 1.0, was ${result.confidence}",
            result.confidence <= 1.0)
    }

    // ============================================================================
    // 7. Brief/Weak Detection Penalty
    // ============================================================================

    @Test
    fun `brief weak single detection is penalized`() {
        // seenCount == 1, rssi < -80, no multiple indicators
        val weakInput = ThreatScoring.ThreatInput(
            baseLikelihood = 50,
            deviceType = DeviceType.BODY_CAMERA,
            rssi = -85,
            seenCount = 1,
            hasMultipleIndicators = false
        )
        // Same but with stronger signal (not weak)
        val strongInput = weakInput.copy(rssi = -70)

        val weakResult = ThreatScoring.calculateThreat(weakInput)
        val strongResult = ThreatScoring.calculateThreat(strongInput)
        assertTrue(
            "Brief weak single detection should score lower: weak=${weakResult.adjustedScore}, strong=${strongResult.adjustedScore}",
            weakResult.adjustedScore < strongResult.adjustedScore
        )
    }

    // ============================================================================
    // 8. Confirmed Active Threat Boost
    // ============================================================================

    @Test
    fun `confirmed active threat with high confidence gets boost`() {
        // Multiple indicators + cross-protocol + high confidence
        val baseInput = ThreatScoring.ThreatInput(
            baseLikelihood = 70,
            deviceType = DeviceType.STINGRAY_IMSI,
            rssi = -55,
            seenCount = 5,
            hasMultipleIndicators = true,
            hasCrossProtocolCorrelation = true,
            matchQuality = ThreatScoring.MatchQuality.EXACT
        )
        val result = ThreatScoring.calculateThreat(baseInput)
        // The 1.1x boost applies when hasMultipleIndicators && hasCrossProtocolCorrelation && confidence > 0.7
        // adjusted should be >= raw in this case
        assertTrue(
            "Confirmed active threat should have adjusted >= raw: raw=${result.rawScore}, adjusted=${result.adjustedScore}",
            result.adjustedScore >= result.rawScore
        )
    }

    // ============================================================================
    // 9. Match Quality Impact
    // ============================================================================

    @Test
    fun `EXACT match quality produces higher score than HEURISTIC`() {
        val exactInput = ThreatScoring.ThreatInput(
            baseLikelihood = 50,
            deviceType = DeviceType.BODY_CAMERA,
            rssi = -70,
            hasMultipleIndicators = true,
            matchQuality = ThreatScoring.MatchQuality.EXACT
        )
        val heuristicInput = exactInput.copy(matchQuality = ThreatScoring.MatchQuality.HEURISTIC)
        val exactResult = ThreatScoring.calculateThreat(exactInput)
        val heuristicResult = ThreatScoring.calculateThreat(heuristicInput)
        assertTrue(
            "EXACT match should score higher than HEURISTIC: exact=${exactResult.adjustedScore}, heuristic=${heuristicResult.adjustedScore}",
            exactResult.adjustedScore > heuristicResult.adjustedScore
        )
    }

    @Test
    fun `match quality confidence bonuses are correct`() {
        assertEquals(0.15, ThreatScoring.MatchQuality.EXACT.confidenceBonus, 0.001)
        assertEquals(0.1, ThreatScoring.MatchQuality.STRONG.confidenceBonus, 0.001)
        assertEquals(0.0, ThreatScoring.MatchQuality.PARTIAL.confidenceBonus, 0.001)
        assertEquals(-0.1, ThreatScoring.MatchQuality.WEAK.confidenceBonus, 0.001)
        assertEquals(-0.2, ThreatScoring.MatchQuality.HEURISTIC.confidenceBonus, 0.001)
    }

    // ============================================================================
    // 10. ThreatResult Structure
    // ============================================================================

    @Test
    fun `ThreatResult contains reasoning text`() {
        val input = ThreatScoring.ThreatInput(
            baseLikelihood = 50,
            deviceType = DeviceType.BODY_CAMERA,
            rssi = -70
        )
        val result = ThreatScoring.calculateThreat(input)
        assertTrue("Reasoning should not be empty", result.reasoning.isNotEmpty())
        assertTrue("Reasoning should contain severity display name",
            result.reasoning.contains(result.severity.displayName))
    }

    @Test
    fun `ThreatResult likelihood matches input`() {
        val input = ThreatScoring.ThreatInput(
            baseLikelihood = 42,
            deviceType = DeviceType.BODY_CAMERA,
            rssi = -70
        )
        val result = ThreatScoring.calculateThreat(input)
        assertEquals("Likelihood should match input", 42, result.likelihood)
    }

    @Test
    fun `ThreatResult impactFactor matches device type`() {
        val input = ThreatScoring.ThreatInput(
            baseLikelihood = 50,
            deviceType = DeviceType.STINGRAY_IMSI,
            rssi = -70
        )
        val result = ThreatScoring.calculateThreat(input)
        assertEquals("Impact factor should match device type",
            ImpactFactors.get(DeviceType.STINGRAY_IMSI), result.impactFactor, 0.001)
    }

    @Test
    fun `ThreatResult confidence factors list is populated`() {
        val input = ThreatScoring.ThreatInput(
            baseLikelihood = 50,
            deviceType = DeviceType.BODY_CAMERA,
            rssi = -70
        )
        val result = ThreatScoring.calculateThreat(input)
        assertTrue("Confidence factors list should not be empty",
            result.confidenceFactors.isNotEmpty())
    }

    // ============================================================================
    // 11. ThreatLevel Display Names
    // ============================================================================

    @Test
    fun `ThreatLevel CRITICAL has correct display name`() {
        assertEquals("Critical", ThreatLevel.CRITICAL.displayName)
    }

    @Test
    fun `ThreatLevel HIGH has correct display name`() {
        assertEquals("High", ThreatLevel.HIGH.displayName)
    }

    @Test
    fun `ThreatLevel MEDIUM has correct display name`() {
        assertEquals("Medium", ThreatLevel.MEDIUM.displayName)
    }

    @Test
    fun `ThreatLevel LOW has correct display name`() {
        assertEquals("Low", ThreatLevel.LOW.displayName)
    }

    @Test
    fun `ThreatLevel INFO has correct display name`() {
        assertEquals("Info", ThreatLevel.INFO.displayName)
    }

    @Test
    fun `all ThreatLevel values have non-empty display names`() {
        for (level in ThreatLevel.entries) {
            assertTrue(
                "${level.name} should have a non-empty display name",
                level.displayName.isNotEmpty()
            )
        }
    }

    @Test
    fun `all ThreatLevel values have non-empty descriptions`() {
        for (level in ThreatLevel.entries) {
            assertTrue(
                "${level.name} should have a non-empty description",
                level.description.isNotEmpty()
            )
        }
    }

    // ============================================================================
    // 12. Base Likelihood Constants
    // ============================================================================

    @Test
    fun `base likelihoods are in valid 0-100 range`() {
        val likelihoods = listOf(
            ThreatScoring.BaseLikelihoods.EXACT_KNOWN_THREAT_MATCH,
            ThreatScoring.BaseLikelihoods.ENCRYPTION_DOWNGRADE,
            ThreatScoring.BaseLikelihoods.ACTIVE_GNSS_SPOOFING,
            ThreatScoring.BaseLikelihoods.SUSPICIOUS_CELL_PARAMETERS,
            ThreatScoring.BaseLikelihoods.TRACKER_FOLLOWING,
            ThreatScoring.BaseLikelihoods.GNSS_SIGNAL_ANOMALY,
            ThreatScoring.BaseLikelihoods.UNKNOWN_CELL_TOWER,
            ThreatScoring.BaseLikelihoods.SINGLE_PATTERN_MATCH,
            ThreatScoring.BaseLikelihoods.CELL_CHANGE_WHILE_STATIONARY,
            ThreatScoring.BaseLikelihoods.BRIEF_ULTRASONIC,
            ThreatScoring.BaseLikelihoods.GNSS_MULTIPATH,
            ThreatScoring.BaseLikelihoods.KNOWN_CONSUMER_DEVICE,
            ThreatScoring.BaseLikelihoods.NORMAL_NETWORK_HANDOFF,
            ThreatScoring.BaseLikelihoods.BLUETOOTH_BEACON_RETAIL
        )
        for (likelihood in likelihoods) {
            assertTrue(
                "Base likelihood $likelihood should be in range 0-100",
                likelihood in 0..100
            )
        }
    }

    // ============================================================================
    // 13. Aggregate Threat Calculation
    // ============================================================================

    @Test
    fun `aggregate threat with empty detections returns INFO`() {
        val result = ThreatScoring.calculateAggregateThreat(
            ThreatScoring.AggregateInput(detections = emptyList())
        )
        assertEquals(ThreatLevel.INFO, result.overallSeverity)
        assertEquals(0, result.overallScore)
        assertEquals(0, result.incidentCount)
        assertTrue(result.correlatedProtocols.isEmpty())
    }

    // ============================================================================
    // 14. Debug Export
    // ============================================================================

    @Test
    fun `debug export contains all required fields`() {
        val input = ThreatScoring.ThreatInput(
            baseLikelihood = 50,
            deviceType = DeviceType.BODY_CAMERA,
            rssi = -70
        )
        val result = ThreatScoring.calculateThreat(input)
        val export = ThreatScoring.generateDebugExport(result)

        assertTrue("Export should contain raw_score", export.containsKey("raw_score"))
        assertTrue("Export should contain adjusted_score", export.containsKey("adjusted_score"))
        assertTrue("Export should contain severity", export.containsKey("severity"))
        assertTrue("Export should contain severity_display", export.containsKey("severity_display"))
        assertTrue("Export should contain likelihood_percent", export.containsKey("likelihood_percent"))
        assertTrue("Export should contain impact_factor", export.containsKey("impact_factor"))
        assertTrue("Export should contain confidence_percent", export.containsKey("confidence_percent"))
        assertTrue("Export should contain confidence_factors", export.containsKey("confidence_factors"))
        assertTrue("Export should contain reasoning", export.containsKey("reasoning"))
        assertTrue("Export should contain calculation_formula", export.containsKey("calculation_formula"))
    }

    @Test
    fun `debug export values match ThreatResult`() {
        val input = ThreatScoring.ThreatInput(
            baseLikelihood = 50,
            deviceType = DeviceType.BODY_CAMERA,
            rssi = -70
        )
        val result = ThreatScoring.calculateThreat(input)
        val export = ThreatScoring.generateDebugExport(result)

        assertEquals(result.rawScore, export["raw_score"])
        assertEquals(result.adjustedScore, export["adjusted_score"])
        assertEquals(result.severity.name, export["severity"])
        assertEquals(result.severity.displayName, export["severity_display"])
        assertEquals(result.likelihood, export["likelihood_percent"])
        assertEquals(result.impactFactor, export["impact_factor"])
        assertEquals((result.confidence * 100).toInt(), export["confidence_percent"])
    }

    // ============================================================================
    // 15. Signal Strength Tiers in calculateThreat
    // ============================================================================

    @Test
    fun `excellent signal strength boosts score`() {
        val weakInput = ThreatScoring.ThreatInput(
            baseLikelihood = 50,
            deviceType = DeviceType.BODY_CAMERA,
            rssi = -70, // neutral
            hasMultipleIndicators = true
        )
        val excellentInput = weakInput.copy(rssi = -40) // excellent
        val weakResult = ThreatScoring.calculateThreat(weakInput)
        val excellentResult = ThreatScoring.calculateThreat(excellentInput)
        assertTrue(
            "Excellent signal should boost score: neutral=${weakResult.adjustedScore}, excellent=${excellentResult.adjustedScore}",
            excellentResult.adjustedScore >= weakResult.adjustedScore
        )
    }

    @Test
    fun `very weak signal reduces score`() {
        val normalInput = ThreatScoring.ThreatInput(
            baseLikelihood = 50,
            deviceType = DeviceType.BODY_CAMERA,
            rssi = -70, // neutral
            hasMultipleIndicators = true
        )
        val veryWeakInput = normalInput.copy(rssi = -95) // very weak
        val normalResult = ThreatScoring.calculateThreat(normalInput)
        val veryWeakResult = ThreatScoring.calculateThreat(veryWeakInput)
        assertTrue(
            "Very weak signal should reduce score: normal=${normalResult.adjustedScore}, veryWeak=${veryWeakResult.adjustedScore}",
            veryWeakResult.adjustedScore < normalResult.adjustedScore
        )
    }

    // ============================================================================
    // 16. Persistence Adjustments in calculateThreat
    // ============================================================================

    @Test
    fun `persistent detection boosts score`() {
        val briefInput = ThreatScoring.ThreatInput(
            baseLikelihood = 50,
            deviceType = DeviceType.BODY_CAMERA,
            rssi = -70,
            seenCount = 1,
            durationMs = 5000, // 5 seconds - brief
            hasMultipleIndicators = true
        )
        val persistentInput = briefInput.copy(seenCount = 10, durationMs = 10 * 60 * 1000) // 10 minutes
        val briefResult = ThreatScoring.calculateThreat(briefInput)
        val persistentResult = ThreatScoring.calculateThreat(persistentInput)
        assertTrue(
            "Persistent detection should boost score: brief=${briefResult.adjustedScore}, persistent=${persistentResult.adjustedScore}",
            persistentResult.adjustedScore > briefResult.adjustedScore
        )
    }

    @Test
    fun `known safe area reduces score`() {
        val normalInput = ThreatScoring.ThreatInput(
            baseLikelihood = 50,
            deviceType = DeviceType.BODY_CAMERA,
            rssi = -70,
            hasMultipleIndicators = true
        )
        val safeAreaInput = normalInput.copy(isInKnownSafeArea = true)
        val normalResult = ThreatScoring.calculateThreat(normalInput)
        val safeAreaResult = ThreatScoring.calculateThreat(safeAreaInput)
        assertTrue(
            "Known safe area should reduce score: normal=${normalResult.adjustedScore}, safeArea=${safeAreaResult.adjustedScore}",
            safeAreaResult.adjustedScore < normalResult.adjustedScore
        )
    }
}
