package com.flockyou.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flockyou.ai.EnrichedDetectorData

/**
 * Renders protocol-specific enriched analysis data as visual widgets.
 * Dispatches to the appropriate widget based on EnrichedDetectorData subtype.
 */
@Composable
fun EnrichedDataSection(
    enrichedData: EnrichedDetectorData,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Section header
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Enriched Analysis",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = enrichedData.protocolLabel(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Show analysis",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    when (enrichedData) {
                        is EnrichedDetectorData.Cellular -> CellularWidget(enrichedData)
                        is EnrichedDetectorData.Gnss -> GnssWidget(enrichedData)
                        is EnrichedDetectorData.Ble -> BleWidget(enrichedData)
                        is EnrichedDetectorData.Ultrasonic -> UltrasonicWidget(enrichedData)
                        is EnrichedDetectorData.WifiFollowing -> WifiFollowingWidget(enrichedData)
                        is EnrichedDetectorData.Satellite -> SatelliteWidget(enrichedData)
                        is EnrichedDetectorData.RfEnvironment -> RfEnvironmentWidget(enrichedData)
                        is EnrichedDetectorData.WifiSsidMatch -> WifiSsidWidget(enrichedData)
                        is EnrichedDetectorData.Shannon -> ShannonWidget(enrichedData)
                    }
                }
            }
        }
    }
}

// ========== Protocol-Specific Widgets ==========

@Composable
private fun CellularWidget(data: EnrichedDetectorData.Cellular) {
    val analysis = data.analysis
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // IMSI Catcher Score gauge
        MetricRow(
            icon = Icons.Default.Security,
            label = "IMSI Catcher Score",
            value = "${analysis.imsiCatcherScore}/100",
            color = when {
                analysis.imsiCatcherScore >= 70 -> Color(0xFFE53935)
                analysis.imsiCatcherScore >= 40 -> Color(0xFFFFA726)
                else -> Color(0xFF66BB6A)
            }
        )

        // Encryption chain
        if (analysis.encryptionDowngradeChain.isNotEmpty()) {
            MetricRow(
                icon = Icons.Default.Lock,
                label = "Encryption Chain",
                value = analysis.encryptionDowngradeChain.joinToString(" -> "),
                color = if (analysis.encryptionDowngraded) Color(0xFFE53935) else Color(0xFF66BB6A)
            )
        }

        // Cell trust
        MetricRow(
            icon = Icons.Default.VerifiedUser,
            label = "Cell Trust",
            value = "${analysis.cellTrustScore}/100 (seen ${analysis.cellSeenCount}x)",
            color = when {
                analysis.cellTrustScore >= 70 -> Color(0xFF66BB6A)
                analysis.cellTrustScore >= 40 -> Color(0xFFFFA726)
                else -> Color(0xFFE53935)
            }
        )

        // Signal
        MetricRow(
            icon = Icons.Default.SignalCellularAlt,
            label = "Signal",
            value = "${analysis.currentSignalDbm} dBm (${analysis.signalQuality})",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // FP likelihood
        if (analysis.falsePositiveLikelihood > 30f) {
            FpIndicatorRow(
                likelihood = analysis.falsePositiveLikelihood,
                indicators = analysis.fpIndicators
            )
        }
    }
}

@Composable
private fun GnssWidget(data: EnrichedDetectorData.Gnss) {
    val analysis = data.analysis
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Spoofing / Jamming likelihood
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThreatGauge(
                label = "Spoofing",
                value = analysis.spoofingLikelihood,
                modifier = Modifier.weight(1f)
            )
            ThreatGauge(
                label = "Jamming",
                value = analysis.jammingLikelihood,
                modifier = Modifier.weight(1f)
            )
        }

        // C/N0 analysis
        MetricRow(
            icon = Icons.Default.BarChart,
            label = "C/N0",
            value = "%.1f dB-Hz (var: %.2f)".format(analysis.currentCn0Mean, analysis.cn0Variance),
            color = if (analysis.cn0TooUniform) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Constellation health
        MetricRow(
            icon = Icons.Default.SatelliteAlt,
            label = "Constellations",
            value = "${analysis.observedConstellations.size} observed, " +
                    "${analysis.totalSatellitesVisible} sats (${analysis.satellitesUsedInFix} in fix)",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Environment
        MetricRow(
            icon = Icons.Default.Terrain,
            label = "Environment",
            value = "${analysis.environmentType} (${(analysis.environmentConfidence * 100).toInt()}%)",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Spoofing indicators
        if (analysis.spoofingIndicators.isNotEmpty()) {
            IndicatorList(label = "Spoofing Indicators", items = analysis.spoofingIndicators)
        }

        // FP likelihood
        if (analysis.falsePositiveLikelihood > 30f) {
            FpIndicatorRow(
                likelihood = analysis.falsePositiveLikelihood,
                indicators = analysis.fpIndicators
            )
        }
    }
}

@Composable
private fun BleWidget(data: EnrichedDetectorData.Ble) {
    val analysis = data.analysis
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Tracker suspicion score
        if (analysis.isTrackerDevice) {
            MetricRow(
                icon = Icons.Default.TrackChanges,
                label = "Tracker Suspicion",
                value = "${analysis.suspicionScore}/100",
                color = when {
                    analysis.suspicionScore >= 70 -> Color(0xFFE53935)
                    analysis.suspicionScore >= 40 -> Color(0xFFFFA726)
                    else -> Color(0xFF66BB6A)
                }
            )

            // Sighting data
            MetricRow(
                icon = Icons.Default.LocationOn,
                label = "Sightings",
                value = "${analysis.trackerSightingCount} sightings across ${analysis.distinctLocationCount} locations",
                color = if (analysis.isFollowingUser) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Signal info
        MetricRow(
            icon = Icons.Default.Bluetooth,
            label = "Signal",
            value = "${analysis.rssi} dBm | ${analysis.advertisingRate} pkt/s" +
                    (analysis.estimatedDistanceMeters?.let { " | ~${"%.1f".format(it)}m" } ?: ""),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // BLE Spam
        if (analysis.isBleSpam) {
            MetricRow(
                icon = Icons.Default.Warning,
                label = "BLE Spam",
                value = "${analysis.spamType ?: "Unknown"} (${analysis.spamEventsPerSecond}/s)",
                color = Color(0xFFFFA726)
            )
        }

        // Suspicion reasons
        if (analysis.suspicionReasons.isNotEmpty()) {
            IndicatorList(label = "Suspicion Factors", items = analysis.suspicionReasons)
        }
    }
}

@Composable
private fun UltrasonicWidget(data: EnrichedDetectorData.Ultrasonic) {
    val analysis = data.analysis
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Source identification
        MetricRow(
            icon = Icons.Default.GraphicEq,
            label = "Source",
            value = analysis.probableSourceDescription,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Tracking likelihood
        ThreatGauge(
            label = "Tracking Likelihood",
            value = analysis.trackingLikelihood,
            modifier = Modifier.fillMaxWidth()
        )

        // Beacon details
        MetricRow(
            icon = Icons.Default.Hearing,
            label = "Frequency",
            value = "${analysis.frequencyHz} Hz | ${analysis.peakAmplitudeDb.toInt()} dB peak | SNR: ${analysis.snrDb.toInt()} dB",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Persistence
        MetricRow(
            icon = Icons.Default.Schedule,
            label = "Persistence",
            value = "${analysis.totalDetectionCount} detections at ${analysis.locationsDetected} locations",
            color = if (analysis.followingUser) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Risk indicators
        if (analysis.riskIndicators.isNotEmpty()) {
            IndicatorList(label = "Risk Factors", items = analysis.riskIndicators)
        }
    }
}

@Composable
private fun WifiFollowingWidget(data: EnrichedDetectorData.WifiFollowing) {
    val analysis = data.analysis
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Following confidence
        ThreatGauge(
            label = "Following Confidence",
            value = analysis.followingConfidence,
            modifier = Modifier.fillMaxWidth()
        )

        // Sighting data
        MetricRow(
            icon = Icons.Default.Wifi,
            label = "Sightings",
            value = "${analysis.sightingCount} sightings across ${analysis.distinctLocations} locations",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Movement analysis
        val movement = when {
            analysis.vehicleMounted -> "Vehicle-mounted"
            analysis.possibleFootSurveillance -> "On foot"
            analysis.likelyMobile -> "Mobile"
            else -> "Stationary"
        }
        MetricRow(
            icon = Icons.Default.DirectionsWalk,
            label = "Movement",
            value = "$movement | Path correlation: ${(analysis.pathCorrelation * 100).toInt()}%",
            color = if (analysis.possibleFootSurveillance) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Signal
        MetricRow(
            icon = Icons.Default.SignalWifi4Bar,
            label = "Signal",
            value = "${analysis.avgSignalStrength} dBm avg | Consistency: ${(analysis.signalConsistency * 100).toInt()}%",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SatelliteWidget(data: EnrichedDetectorData.Satellite) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricRow(
            icon = Icons.Default.SatelliteAlt,
            label = "Type",
            value = data.detectorType,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Key metadata
        data.metadata.forEach { (key, value) ->
            if (key in setOf("provider", "orbitalType", "anomalyType", "anomalySeverity")) {
                MetricRow(
                    icon = Icons.Default.Info,
                    label = key.replaceFirstChar { it.uppercase() },
                    value = value,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Risk indicators
        if (data.riskIndicators.isNotEmpty()) {
            IndicatorList(label = "Risk Indicators", items = data.riskIndicators)
        }
    }
}

@Composable
private fun RfEnvironmentWidget(data: EnrichedDetectorData.RfEnvironment) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricRow(
            icon = Icons.Default.Radio,
            label = "RF Threat Score",
            value = "${data.rfThreatScore}/100",
            color = when {
                data.rfThreatScore >= 70 -> Color(0xFFE53935)
                data.rfThreatScore >= 40 -> Color(0xFFFFA726)
                else -> Color(0xFF66BB6A)
            }
        )

        MetricRow(
            icon = Icons.Default.WifiFind,
            label = "Networks",
            value = "${data.totalNetworks} total, ${data.hiddenNetworkCount} hidden",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        MetricRow(
            icon = Icons.Default.SignalCellularAlt,
            label = "Signal Environment",
            value = "${data.avgSignalStrength} dBm avg (variance: ${"%.1f".format(data.signalVariance)})",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (data.suspiciousPatterns.isNotEmpty()) {
            IndicatorList(label = "Suspicious Patterns", items = data.suspiciousPatterns)
        }
    }
}

@Composable
private fun WifiSsidWidget(data: EnrichedDetectorData.WifiSsidMatch) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricRow(
            icon = Icons.Default.Wifi,
            label = "SSID",
            value = if (data.isHidden) "(Hidden Network)" else data.ssid,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        MetricRow(
            icon = Icons.Default.Router,
            label = "Details",
            value = "Ch ${data.channel} | ${data.frequencyBand} | ${data.rssiDbm} dBm",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        data.ouiVendor?.let {
            MetricRow(
                icon = Icons.Default.Business,
                label = "Vendor",
                value = it,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        MetricRow(
            icon = Icons.Default.Pattern,
            label = "Matched Pattern",
            value = data.matchedPatternDescription,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ShannonWidget(data: EnrichedDetectorData.Shannon) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricRow(
            icon = Icons.Default.Memory,
            label = "Shannon SDM",
            value = "Modem diagnostic data captured",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ========== Shared Helper Composables ==========

@Composable
private fun MetricRow(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .size(16.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = color
            )
        }
    }
}

@Composable
private fun ThreatGauge(
    label: String,
    value: Float,
    modifier: Modifier = Modifier
) {
    val gaugeColor = when {
        value >= 70f -> Color(0xFFE53935)
        value >= 40f -> Color(0xFFFFA726)
        else -> Color(0xFF66BB6A)
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${value.toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = gaugeColor
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = (value / 100f).coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(gaugeColor)
            )
        }
    }
}

@Composable
private fun IndicatorList(
    label: String,
    items: List<String>
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        items.take(5).forEach { indicator ->
            Row(
                modifier = Modifier.padding(vertical = 1.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "\u2022 ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = indicator,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
        if (items.size > 5) {
            Text(
                text = "...and ${items.size - 5} more",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun FpIndicatorRow(
    likelihood: Float,
    indicators: List<String>
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF66BB6A).copy(alpha = 0.1f)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFF66BB6A),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "FP Likelihood: ${likelihood.toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF66BB6A)
                )
            }
            if (indicators.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = indicators.joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Human-readable protocol label for enriched data type.
 */
private fun EnrichedDetectorData.protocolLabel(): String = when (this) {
    is EnrichedDetectorData.Cellular -> "Cellular"
    is EnrichedDetectorData.Gnss -> "GNSS"
    is EnrichedDetectorData.Ble -> "BLE"
    is EnrichedDetectorData.Ultrasonic -> "Ultrasonic"
    is EnrichedDetectorData.WifiFollowing -> "WiFi Following"
    is EnrichedDetectorData.Satellite -> "Satellite"
    is EnrichedDetectorData.RfEnvironment -> "RF Environment"
    is EnrichedDetectorData.WifiSsidMatch -> "WiFi SSID"
    is EnrichedDetectorData.Shannon -> "Shannon SDM"
}
