package com.flockyou.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flockyou.data.model.*
import com.flockyou.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Parse matched patterns JSON string into human-readable label/value pairs.
 * Patterns are stored as JSON arrays like ["MAC_PREFIX:Flock","SSID_REGEX:FLOCK-*"]
 */
fun parseMatchedPatterns(patterns: String?): List<Pair<String, String>> {
    if (patterns.isNullOrBlank() || patterns == "[]" || patterns == "null") return emptyList()
    return try {
        // Strip JSON array brackets and split
        val cleaned = patterns.trimStart('[').trimEnd(']')
        cleaned.split(",").mapNotNull { entry ->
            val trimmed = entry.trim().trim('"')
            if (trimmed.isBlank()) return@mapNotNull null
            val colonIndex = trimmed.indexOf(':')
            if (colonIndex > 0) {
                val type = trimmed.substring(0, colonIndex)
                val value = trimmed.substring(colonIndex + 1)
                val humanType = when (type.uppercase()) {
                    "MAC_PREFIX", "MAC_OUI" -> "MAC OUI"
                    "SSID_REGEX", "SSID_PATTERN" -> "SSID"
                    "BLE_NAME", "BLE_NAME_REGEX" -> "BLE Name"
                    "SERVICE_UUID", "BLE_UUID" -> "Service UUID"
                    "MANUFACTURER_ID", "MFR_ID" -> "Manufacturer"
                    "ADVERTISING_RATE" -> "Ad Rate"
                    "BEHAVIORAL" -> "Behavior"
                    "TRACKER_SPEC" -> "Tracker"
                    "RAVEN_UUID" -> "Raven UUID"
                    else -> type.replace("_", " ").lowercase()
                        .replaceFirstChar { it.uppercase() }
                }
                Pair(humanType, value)
            } else {
                Pair("Pattern", trimmed)
            }
        }
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * Expandable detection card with action buttons for marking reviewed/false positive.
 *
 * Features:
 * - Tap to expand: Shows full details (in advanced mode)
 * - Tap to open detail sheet (in simple mode)
 * - Action buttons for mark reviewed/false positive when expanded
 * - Respects simple/advanced mode for info density
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableDetectionCard(
    detection: Detection,
    onClick: () -> Unit,
    onMarkReviewed: (Detection) -> Unit,
    onMarkFalsePositive: (Detection) -> Unit,
    modifier: Modifier = Modifier,
    advancedMode: Boolean = false,
    isExpanded: Boolean = false,
    onExpandToggle: () -> Unit = {},
    onAnalyzeClick: ((Detection) -> Unit)? = null,
    isAnalyzing: Boolean = false,
    onPrioritizeEnrichment: ((Detection) -> Unit)? = null,
    isEnrichmentPending: Boolean = false,
    relatedCount: Int = 0,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
    onToggleSelection: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    ouiLookupViewModel: OuiLookupViewModel = hiltViewModel()
) {
    val threatColor = detection.threatLevel.toColor()
    val dateFormat = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    // Calculate relative time (not memoized so it updates on recomposition)
    val relativeTime = run {
        val now = System.currentTimeMillis()
        val diff = now - detection.timestamp
        when {
            diff < 60_000 -> "Just now"
            diff < 3600_000 -> "${diff / 60_000}m ago"
            diff < 86400_000 -> "${diff / 3600_000}h ago"
            else -> dateFormat.format(Date(detection.timestamp))
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        ),
        onClick = {
            if (selectionMode && onToggleSelection != null) {
                onToggleSelection()
            } else if (advancedMode) {
                if (isExpanded) {
                    onClick()
                } else {
                    onExpandToggle()
                }
            } else {
                onClick()
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                // Colored left border based on threat level
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(threatColor)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(12.dp)
                ) {
                    // Compact header row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Threat indicator icon
                        Box(
                            modifier = Modifier
                                .size(if (advancedMode) 48.dp else 40.dp)
                                .clip(RoundedCornerShape(if (advancedMode) 12.dp else 10.dp))
                                .background(threatColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = detection.deviceType.toIcon(),
                                contentDescription = "${detection.deviceType.displayName}, Threat level: ${detection.threatLevel.name}",
                                tint = threatColor,
                                modifier = Modifier.size(if (advancedMode) 28.dp else 24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Main content
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // In simple mode, show friendly name; advanced shows raw type
                                Text(
                                    text = if (advancedMode) {
                                        detection.deviceType.name.replace("_", " ")
                                    } else {
                                        detection.deviceType.displayName
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                ThreatBadge(threatLevel = detection.threatLevel)
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Simple mode: friendly description
                            // Advanced mode: technical details
                            if (!advancedMode) {
                                // Simple mode - show friendly description
                                Text(
                                    text = getSimpleDescription(detection),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                // Advanced mode - show protocol and identifier
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = detection.protocol.toIcon(),
                                        contentDescription = "${detection.protocol.displayName} network",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = getAdvancedIdentifier(detection),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Right side - time and signal
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = relativeTime,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (relativeTime == "Just now") threatColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            if (advancedMode) {
                                // Show RSSI in advanced mode
                                Text(
                                    text = "${detection.rssi} dBm",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = detection.signalStrength.toColor()
                                )
                            } else {
                                // Show friendly signal indicator in simple mode
                                SignalIndicator(
                                    rssi = detection.rssi,
                                    signalStrength = detection.signalStrength
                                )
                            }

                            // Expand indicator when in advanced mode
                            if (advancedMode) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Expanded details section
                    AnimatedVisibility(
                        visible = isExpanded && advancedMode,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            Divider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Technical details grid
                            ExpandedTechnicalDetails(
                                detection = detection,
                                timeFormat = timeFormat,
                                dateFormat = dateFormat
                            )

                            // Threat score breakdown
                            Spacer(modifier = Modifier.height(8.dp))
                            ThreatScoreBreakdown(detection = detection)

                            // Enrichment status
                            EnrichmentStatusRowPublic(
                                detection = detection,
                                isAnalyzing = isAnalyzing,
                                isEnrichmentPending = isEnrichmentPending,
                                onPrioritizeEnrichment = onPrioritizeEnrichment
                            )

                            // Action buttons when expanded (only if not already reviewed)
                            val isAlreadyReviewed = detection.fpCategory == "USER_REVIEWED" ||
                                detection.fpCategory == "USER_MARKED_FP"

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!isAlreadyReviewed) {
                                    OutlinedButton(
                                        onClick = { onMarkReviewed(detection) },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Reviewed", style = MaterialTheme.typography.labelMedium)
                                    }
                                    OutlinedButton(
                                        onClick = { onMarkFalsePositive(detection) },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.tertiary
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VerifiedUser,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("False Positive", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                IconButton(onClick = onClick) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInFull,
                                        contentDescription = "View full details"
                                    )
                                }
                            }
                        }
                    }

                    // Non-expanded: show quick metadata in simple mode
                    if (!isExpanded && !advancedMode) {
                        // Show confirmed threat badge
                        if (detection.confirmedThreat) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(MaterialTheme.colorScheme.error)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "CONFIRMED",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        // Show user note if present
                        detection.userNote?.let { note ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.StickyNote2,
                                    contentDescription = "User note",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = note,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Show seen count if > 1
                        if (detection.seenCount > 1) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = "Seen ${detection.seenCount} times",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Seen ${detection.seenCount} times",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // Related detection count chip
                                if (relatedCount > 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Link,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                text = "$relatedCount related",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (relatedCount > 0) {
                            // Show related count even without seen count
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Link,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "$relatedCount related",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Expanded technical details for detection card
 */
@Composable
private fun ExpandedTechnicalDetails(
    detection: Detection,
    timeFormat: SimpleDateFormat,
    dateFormat: SimpleDateFormat
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // MAC Address
            detection.macAddress?.let { mac ->
                DetailRow(label = "MAC Address", value = mac, isMonospace = true)
            }

            // SSID
            detection.ssid?.let { ssid ->
                DetailRow(label = "SSID/Name", value = ssid)
            }

            // Protocol and Method
            DetailRow(
                label = "Protocol",
                value = "${detection.protocol.displayName} / ${detection.detectionMethod.displayName}"
            )

            // Signal details
            DetailRow(
                label = "Signal",
                value = "${detection.rssi} dBm (${detection.signalStrength.displayName})"
            )

            // Threat score
            DetailRow(
                label = "Threat Score",
                value = "${detection.threatScore}/100"
            )

            // Location if available
            if (detection.latitude != null && detection.longitude != null) {
                DetailRow(
                    label = "Location",
                    value = "%.5f, %.5f".format(detection.latitude, detection.longitude),
                    isMonospace = true
                )
            }

            // Timestamps
            val fullTimestamp = "${dateFormat.format(Date(detection.timestamp))} ${timeFormat.format(Date(detection.timestamp))}"
            DetailRow(label = "First Seen", value = fullTimestamp)

            if (detection.lastSeenTimestamp != detection.timestamp) {
                val lastSeenFull = "${dateFormat.format(Date(detection.lastSeenTimestamp))} ${timeFormat.format(Date(detection.lastSeenTimestamp))}"
                DetailRow(label = "Last Seen", value = lastSeenFull)
            }

            // Seen count
            if (detection.seenCount > 1) {
                DetailRow(label = "Seen Count", value = "${detection.seenCount}x")
            }

            // Service UUIDs
            detection.serviceUuids?.let { uuids ->
                if (uuids.isNotEmpty() && uuids != "[]") {
                    DetailRow(label = "Service UUIDs", value = uuids, isMonospace = true, maxLines = 2)
                }
            }

            // Matched patterns as human-readable chips
            val parsedPatterns = remember(detection.matchedPatterns) {
                parseMatchedPatterns(detection.matchedPatterns)
            }
            if (parsedPatterns.isNotEmpty()) {
                Text(
                    text = "Matched Patterns:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    parsedPatterns.forEach { (type, value) ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "$type: $value",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Detection source
            DetailRow(label = "Source", value = detection.detectionSource.displayName)

            // ID for debugging
            DetailRow(label = "ID", value = detection.id, isMonospace = true, maxLines = 1)
        }
    }
}

/**
 * Helper row for detail display
 */
@Composable
private fun DetailRow(
    label: String,
    value: String,
    isMonospace: Boolean = false,
    maxLines: Int = 1
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Public version of EnrichmentStatusRow for use in SwipeableDetectionCard
 */
@Composable
fun EnrichmentStatusRowPublic(
    detection: Detection,
    isAnalyzing: Boolean,
    isEnrichmentPending: Boolean,
    onPrioritizeEnrichment: ((Detection) -> Unit)?
) {
    val hasBasicEnrichment = detection.fpScore != null && detection.analyzedAt != null
    val isLlmEnriched = detection.llmAnalyzed
    val needsLlmEnrichment = !isLlmEnriched

    // Only show if there's something to display
    if (!needsLlmEnrichment && !isAnalyzing && !isEnrichmentPending) {
        // Show analyzed state
        if (hasBasicEnrichment) {
            val isFalsePositive = (detection.fpScore ?: 0f) >= 0.4f
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isLlmEnriched) Icons.Default.AutoAwesome else Icons.Default.CheckCircle,
                    contentDescription = "Analyzed",
                    tint = if (isFalsePositive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isLlmEnriched) "AI analyzed" else "Analyzed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                detection.fpScore?.let { score ->
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "FP: ${(score * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (score >= 0.4f) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
        return
    }

    Spacer(modifier = Modifier.height(8.dp))

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = when {
            isAnalyzing || isEnrichmentPending -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            needsLlmEnrichment -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else -> Color.Transparent
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                isAnalyzing || isEnrichmentPending -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isAnalyzing) "Analyzing..." else "Queued",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                needsLlmEnrichment -> {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Pending analysis",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Pending AI analysis",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (onPrioritizeEnrichment != null) {
                        TextButton(
                            onClick = { onPrioritizeEnrichment(detection) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerticalAlignTop,
                                contentDescription = "Prioritize",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Prioritize",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Get a simple, user-friendly description for a detection (used in simple mode)
 */
private fun getSimpleDescription(detection: Detection): String {
    return when {
        detection.fpCategory == "USER_REVIEWED" -> "Reviewed - ${detection.deviceType.displayName}"
        detection.fpCategory == "USER_MARKED_FP" -> "False positive - safe"
        (detection.fpScore ?: 0f) >= 0.7f -> "Likely safe - ${detection.fpReason ?: "common device"}"
        detection.threatLevel == ThreatLevel.CRITICAL -> "Active surveillance detected nearby"
        detection.threatLevel == ThreatLevel.HIGH -> "Surveillance device confirmed"
        detection.threatLevel == ThreatLevel.MEDIUM -> "Possible surveillance equipment"
        detection.isActive -> "Currently active nearby"
        detection.seenCount > 3 -> "Seen ${detection.seenCount} times in this area"
        else -> detection.deviceType.displayName
    }
}

/**
 * Get technical identifier string for advanced mode
 */
private fun getAdvancedIdentifier(detection: Detection): String {
    return when (detection.protocol) {
        DetectionProtocol.CELLULAR -> {
            val networkType = detection.manufacturer ?: "Unknown"
            val cellId = detection.firmwareVersion?.removePrefix("Cell ID: ") ?: "?"
            "$networkType / Cell $cellId"
        }
        DetectionProtocol.WIFI -> {
            detection.macAddress ?: detection.ssid ?: "Unknown"
        }
        DetectionProtocol.BLUETOOTH_LE -> {
            detection.macAddress ?: detection.deviceName ?: "Unknown"
        }
        else -> {
            detection.macAddress ?: detection.ssid ?: detection.manufacturer ?: "Unknown"
        }
    }
}

/**
 * Advanced mode toggle button for app bar
 */
@Composable
fun AdvancedModeToggle(
    advancedMode: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onToggle,
        modifier = modifier
    ) {
        Icon(
            imageVector = if (advancedMode) Icons.Default.Code else Icons.Default.Visibility,
            contentDescription = if (advancedMode) "Switch to simple mode" else "Switch to advanced mode",
            tint = if (advancedMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Advanced mode toggle chip for inline use
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedModeChip(
    advancedMode: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = advancedMode,
        onClick = onToggle,
        label = {
            Text(
                text = if (advancedMode) "Advanced" else "Simple",
                style = MaterialTheme.typography.labelSmall
            )
        },
        leadingIcon = {
            Icon(
                imageVector = if (advancedMode) Icons.Default.Code else Icons.Default.Visibility,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        modifier = modifier
    )
}
