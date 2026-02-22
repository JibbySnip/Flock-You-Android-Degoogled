package com.flockyou.ui.components

import androidx.compose.animation.*
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flockyou.data.model.Detection
import com.flockyou.data.model.ThreatLevel
import com.flockyou.detection.ThreatScoring
import com.flockyou.ui.components.toColor

/**
 * Compact threat score breakdown widget showing the three components
 * (likelihood, impact, confidence) as a segmented horizontal bar.
 * Tap to expand for detailed factor values.
 */
@Composable
fun ThreatScoreBreakdown(
    detection: Detection,
    modifier: Modifier = Modifier,
    compact: Boolean = true
) {
    val impactFactor = remember(detection.deviceType) {
        ThreatScoring.getImpactFactor(detection.deviceType)
    }

    // Estimate component values from the final score
    // Since we don't store individual components, we reverse-engineer approximate values
    val threatScore = detection.threatScore
    val estimatedConfidence = when {
        detection.seenCount >= 10 -> 0.9
        detection.seenCount >= 5 -> 0.8
        detection.seenCount >= 2 -> 0.7
        detection.rssi > -50 -> 0.75
        detection.rssi > -70 -> 0.6
        else -> 0.5
    }

    // Back-calculate approximate likelihood
    val estimatedLikelihood = if (impactFactor * estimatedConfidence > 0) {
        (threatScore / (impactFactor * estimatedConfidence)).toInt().coerceIn(0, 100)
    } else {
        threatScore
    }

    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Compact bar view
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                // Score label with expand indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Threat Score",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${detection.threatScore}/100",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = detection.threatLevel.toColor()
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Show breakdown",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Segmented bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    // Likelihood segment (blue)
                    val likelihoodWeight = estimatedLikelihood / 100f
                    Box(
                        modifier = Modifier
                            .weight(likelihoodWeight.coerceAtLeast(0.05f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp))
                            .background(ScoreBlue)
                    )
                    // Impact segment (orange)
                    val impactWeight = (impactFactor / 2.0).toFloat()
                    Box(
                        modifier = Modifier
                            .weight(impactWeight.coerceAtLeast(0.05f))
                            .fillMaxHeight()
                            .background(ScoreOrange)
                    )
                    // Confidence segment (green)
                    val confidenceWeight = estimatedConfidence.toFloat()
                    Box(
                        modifier = Modifier
                            .weight(confidenceWeight.coerceAtLeast(0.05f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                            .background(ScoreGreen)
                    )
                }

                if (!expanded) {
                    Spacer(modifier = Modifier.height(2.dp))
                    // Compact legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ScoreLegendDot(color = ScoreBlue, label = "Likelihood")
                        ScoreLegendDot(color = ScoreOrange, label = "Impact")
                        ScoreLegendDot(color = ScoreGreen, label = "Confidence")
                    }
                }
            }
        }

        // Expanded details
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Likelihood
                    ScoreFactorRow(
                        color = ScoreBlue,
                        label = "Base Likelihood",
                        value = "$estimatedLikelihood%",
                        description = "How likely this device type matches a surveillance device"
                    )

                    // Impact
                    val impactDescription = when {
                        impactFactor >= 2.0 -> "Can intercept communications"
                        impactFactor >= 1.5 -> "Stalking/tracking risk"
                        impactFactor >= 1.0 -> "Known surveillance"
                        impactFactor >= 0.7 -> "Consumer IoT device"
                        else -> "Infrastructure"
                    }
                    ScoreFactorRow(
                        color = ScoreOrange,
                        label = "Impact Factor",
                        value = "%.1fx".format(impactFactor),
                        description = impactDescription
                    )

                    // Confidence
                    val confidenceFactors = buildList {
                        if (detection.rssi > -50) add("Strong signal (+)")
                        if (detection.seenCount >= 5) add("Seen ${detection.seenCount}x (+)")
                        if (detection.seenCount == 1) add("Single sighting (-)")
                        if (detection.rssi < -85) add("Weak signal (-)")
                    }
                    ScoreFactorRow(
                        color = ScoreGreen,
                        label = "Confidence",
                        value = "${(estimatedConfidence * 100).toInt()}%",
                        description = if (confidenceFactors.isNotEmpty())
                            confidenceFactors.joinToString(", ")
                        else
                            "Based on signal strength and persistence"
                    )

                    // Formula
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Text(
                        text = "score = likelihood x impact x confidence",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreFactorRow(
    color: Color,
    label: String,
    value: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
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
                    text = value,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = color
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ScoreLegendDot(
    color: Color,
    label: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

// Score segment colors
private val ScoreBlue = Color(0xFF42A5F5)
private val ScoreOrange = Color(0xFFFFA726)
private val ScoreGreen = Color(0xFF66BB6A)
