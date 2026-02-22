package com.flockyou.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flockyou.ai.correlation.CorrelatedThreat
import com.flockyou.ai.correlation.CorrelationType
import com.flockyou.data.model.Detection
import com.flockyou.data.model.ThreatLevel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Card that groups multiple related detections into a single incident.
 * Shows the correlation type, combined threat level, constituent detections,
 * time span, cross-protocol indicators, and actionable recommendations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentCard(
    correlatedThreat: CorrelatedThreat,
    onDetectionClick: (Detection) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val threatColor = correlatedThreat.combinedThreatLevel.toColor()
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    // Time span
    val timestamps = correlatedThreat.detections.map { it.timestamp }
    val timeSpan = if (timestamps.size >= 2) {
        val earliest = timestamps.min()
        val latest = timestamps.max()
        val durationMin = (latest - earliest) / 60_000
        when {
            durationMin < 1 -> "< 1 min"
            durationMin < 60 -> "${durationMin}m span"
            else -> "${durationMin / 60}h ${durationMin % 60}m span"
        }
    } else "Single event"

    // Cross-protocol indicators
    val protocols = correlatedThreat.detections.map { it.protocol }.distinct()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = threatColor.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(16.dp),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header row: icon + type + threat badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Correlation type icon
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = threatColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = correlatedThreat.correlationType.toIcon(),
                            contentDescription = null,
                            tint = threatColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = correlatedThreat.correlationType.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${correlatedThreat.detections.size} detections | $timeSpan",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Threat badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = threatColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = correlatedThreat.combinedThreatLevel.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = threatColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Analysis text
            Text(
                text = correlatedThreat.analysis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Protocol chips + confidence
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    protocols.forEach { protocol ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = protocol.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "${(correlatedThreat.correlationScore * 100).toInt()}% confidence",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expanded section: constituent detections + recommendations
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    Divider(color = threatColor.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // Constituent detections
                    Text(
                        text = "RELATED DETECTIONS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    correlatedThreat.detections.forEach { detection ->
                        CompactDetectionItem(
                            detection = detection,
                            dateFormat = dateFormat,
                            onClick = { onDetectionClick(detection) }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Recommendations
                    if (correlatedThreat.recommendations.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "RECOMMENDATIONS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        correlatedThreat.recommendations.forEach { rec ->
                            Row(
                                modifier = Modifier.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = threatColor
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = rec,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    // Metadata
                    correlatedThreat.metadata?.let { meta ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            meta.locationCount?.let {
                                MetadataChip(label = "$it locations")
                            }
                            correlatedThreat.spatialProximity?.let {
                                MetadataChip(label = "${it.toInt()}m radius")
                            }
                            meta.trackerCount?.let {
                                MetadataChip(label = "$it trackers")
                            }
                        }
                    }
                }
            }

            // Expand indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Show details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Compact single-line detection item within an incident card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactDetectionItem(
    detection: Detection,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit
) {
    val threatColor = detection.threatLevel.toColor()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Threat dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(threatColor)
            )
            Spacer(modifier = Modifier.width(8.dp))

            // Device type + name
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = detection.deviceType.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                detection.macAddress?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            // Score + time
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${detection.threatScore}/100",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = threatColor
                )
                Text(
                    text = dateFormat.format(Date(detection.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MetadataChip(label: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * Icon for each correlation type, used by both IncidentCard and PatternAlertBanner.
 */
fun CorrelationType.toIcon() = when (this) {
    CorrelationType.IMSI_CATCHER_COMBO -> Icons.Default.CellTower
    CorrelationType.FOLLOWING_PATTERN -> Icons.Default.PersonSearch
    CorrelationType.COORDINATED_SURVEILLANCE -> Icons.Default.Groups
    CorrelationType.TRACKER_NETWORK -> Icons.Default.Sensors
    CorrelationType.SPATIAL_CLUSTERING -> Icons.Default.LocationOn
    CorrelationType.TIMING_CORRELATION -> Icons.Default.Schedule
}
