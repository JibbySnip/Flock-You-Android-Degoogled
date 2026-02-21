package com.flockyou.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flockyou.data.model.*
import com.flockyou.ui.components.*

@Composable
fun SatelliteConnectionBanner(
    satelliteState: com.flockyou.monitoring.SatelliteMonitor.SatelliteConnectionState
) {
    val providerName = when (satelliteState.provider) {
        com.flockyou.monitoring.SatelliteMonitor.SatelliteProvider.STARLINK -> "Starlink"
        com.flockyou.monitoring.SatelliteMonitor.SatelliteProvider.SKYLO -> "Skylo"
        com.flockyou.monitoring.SatelliteMonitor.SatelliteProvider.GLOBALSTAR -> "Globalstar"
        com.flockyou.monitoring.SatelliteMonitor.SatelliteProvider.AST_SPACEMOBILE -> "AST SpaceMobile"
        com.flockyou.monitoring.SatelliteMonitor.SatelliteProvider.LYNK -> "Lynk"
        com.flockyou.monitoring.SatelliteMonitor.SatelliteProvider.IRIDIUM -> "Iridium"
        com.flockyou.monitoring.SatelliteMonitor.SatelliteProvider.INMARSAT -> "Inmarsat"
        com.flockyou.monitoring.SatelliteMonitor.SatelliteProvider.UNKNOWN -> satelliteState.networkName ?: "Unknown"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFFFA726),
        contentColor = Color.Black
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SatelliteAlt,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Connected to $providerName Satellite",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Not a terrestrial connection",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun AiAnalysisResultDialog(
    result: com.flockyou.data.AiAnalysisResult,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Column {
                Text("AI Analysis")
                Text(
                    text = "Model: ${result.modelUsed} | ${result.processingTimeMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (result.success) {
                    // Main analysis
                    result.analysis?.let { analysis ->
                        Text(
                            text = analysis,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // Threat assessment
                    result.threatAssessment?.let { threat ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Threat Assessment",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = threat,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    // Recommendations
                    if (result.recommendations.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Recommendations",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        result.recommendations.forEach { rec ->
                            Row(
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Text("• ", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = rec,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    // Confidence indicator
                    if (result.confidence > 0f) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Confidence: ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LinearProgressIndicator(
                                progress = result.confidence,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = when {
                                    result.confidence > 0.8f -> Color(0xFF4CAF50)
                                    result.confidence > 0.5f -> Color(0xFFFFC107)
                                    else -> Color(0xFFF44336)
                                }
                            )
                            Text(
                                text = " ${(result.confidence * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Error state
                    Text(
                        text = result.error ?: "Analysis failed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LastDetectionAlert(
    detection: Detection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val threatColor = detection.threatLevel.toColor()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = threatColor.copy(alpha = 0.1f)
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = threatColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LATEST DETECTION",
                    style = MaterialTheme.typography.labelSmall,
                    color = threatColor
                )
                Text(
                    text = detection.deviceType.name.replace("_", " "),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            ThreatBadge(threatLevel = detection.threatLevel)
        }
    }
}
