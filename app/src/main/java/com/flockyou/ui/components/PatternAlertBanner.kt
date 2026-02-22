package com.flockyou.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flockyou.ai.correlation.CorrelatedThreat
import com.flockyou.ai.correlation.CorrelationType
import com.flockyou.data.model.ThreatLevel
import com.flockyou.ui.components.toColor

/**
 * Banner card shown at the top of the detection list when cross-detection
 * pattern analysis has found correlated threats (coordinated surveillance,
 * following patterns, IMSI catcher combos, etc.).
 *
 * Each correlated threat is shown as a dismissible colored banner with
 * pattern type icon, description, affected detection count, and severity.
 */
@Composable
fun PatternAlertBanner(
    correlatedThreats: List<CorrelatedThreat>,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (correlatedThreats.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Section header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "PATTERN ALERTS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
            ) {
                Text(
                    text = "${correlatedThreats.size}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }

        // Individual alert cards
        correlatedThreats.forEach { threat ->
            PatternAlertCard(
                correlatedThreat = threat,
                onDismiss = { onDismiss(threat.id) }
            )
        }
    }
}

@Composable
private fun PatternAlertCard(
    correlatedThreat: CorrelatedThreat,
    onDismiss: () -> Unit
) {
    val threatColor = correlatedThreat.combinedThreatLevel.toColor()
    val icon = correlatedThreat.correlationType.toIcon()
    val typeLabel = correlatedThreat.correlationType.toLabel()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = threatColor.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Pattern type icon
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = threatColor.copy(alpha = 0.2f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = typeLabel,
                        tint = threatColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = threatColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Severity badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = threatColor.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = correlatedThreat.combinedThreatLevel.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = threatColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = correlatedThreat.analysis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Detection count and correlation score
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DeviceHub,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${correlatedThreat.detections.size} related detections",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(correlatedThreat.correlationScore * 100).toInt()}% confidence",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Dismiss button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// toIcon() is defined in IncidentCard.kt as a public extension on CorrelationType

/**
 * Get human-readable label for correlation type
 */
private fun CorrelationType.toLabel(): String = this.displayName
