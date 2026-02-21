package com.flockyou.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flockyou.data.model.*
import com.flockyou.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Enrichment status row showing AI analysis state and prioritize button
 */
@Composable
private fun EnrichmentStatusRow(
    detection: Detection,
    isAnalyzing: Boolean,
    isEnrichmentPending: Boolean,
    onPrioritizeEnrichment: ((Detection) -> Unit)?
) {
    // Determine enrichment state
    val hasBasicEnrichment = detection.fpScore != null && detection.analyzedAt != null
    val isLlmEnriched = detection.llmAnalyzed
    // Detection needs LLM enrichment if it hasn't been LLM analyzed yet
    // (even if it has basic rule-based analysis)
    val needsLlmEnrichment = !isLlmEnriched

    // FP thresholds (matching FalsePositiveAnalyzer)
    val isFalsePositive = hasBasicEnrichment && (detection.fpScore ?: 0f) >= 0.4f
    val fpConfidenceLevel = when {
        (detection.fpScore ?: 0f) >= 0.8f -> "High confidence"
        (detection.fpScore ?: 0f) >= 0.6f -> "Likely"
        (detection.fpScore ?: 0f) >= 0.4f -> "Possibly"
        else -> null
    }

    // Only show this row if there's something to display
    // Show analyzed state when LLM enriched AND not currently processing
    if (!needsLlmEnrichment && !isAnalyzing && !isEnrichmentPending) {
        Spacer(modifier = Modifier.height(4.dp))

        // Show FP indicator if flagged as false positive
        if (isFalsePositive && fpConfidenceLevel != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = "False positive",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$fpConfidenceLevel false positive",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    detection.fpReason?.let { reason ->
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "\u2022 ${reason.take(40)}${if (reason.length > 40) "..." else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (isLlmEnriched) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI analyzed",
                            tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
            return
        }

        // Show a subtle "analyzed" indicator when LLM was used (not FP)
        if (isLlmEnriched || hasBasicEnrichment) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isLlmEnriched) Icons.Default.AutoAwesome else Icons.Default.CheckCircle,
                    contentDescription = "Analyzed",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isLlmEnriched) "AI analyzed" else "Analyzed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                // Show FP score if analyzed but not flagged as FP
                detection.fpScore?.let { score ->
                    if (score > 0f && score < 0.4f) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "\u2022 FP: ${(score * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
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
                    // Currently being analyzed or queued for analysis
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isAnalyzing) "Analyzing..." else "Queued for analysis",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                needsLlmEnrichment -> {
                    // Missing enrichment - show warning icon and prioritize button
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
 * Detection list item card
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectionCard(
    detection: Detection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    advancedMode: Boolean = false,
    onAnalyzeClick: ((Detection) -> Unit)? = null,
    isAnalyzing: Boolean = false,
    onPrioritizeEnrichment: ((Detection) -> Unit)? = null,
    isEnrichmentPending: Boolean = false,
    ouiLookupViewModel: OuiLookupViewModel = hiltViewModel()
) {
    val threatColor = detection.threatLevel.toColor()
    val dateFormat = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }

    // Calculate relative time
    val relativeTime = remember(detection.timestamp) {
        val now = System.currentTimeMillis()
        val diff = now - detection.timestamp
        when {
            diff < 60_000 -> "Just now"
            diff < 3600_000 -> "${diff / 60_000}m ago"
            diff < 86400_000 -> "${diff / 3600_000}h ago"
            else -> dateFormat.format(Date(detection.timestamp))
        }
    }

    // State for expanding/collapsing the analysis section (collapsed by default)
    var showAnalysis by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        onClick = onClick
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Colored left border based on threat level (4dp width)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Threat indicator
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(threatColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = detection.deviceType.toIcon(),
                            contentDescription = "${detection.deviceType.displayName}, Threat level: ${detection.threatLevel.name}",
                            tint = threatColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = detection.deviceType.name.replace("_", " "),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        ThreatBadge(threatLevel = detection.threatLevel)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Standardized display: "Device Type / Category - Primary Identifier"
                    // Format: Cellular: "Network Type - Cell ID"
                    //         WiFi/BLE: "Manufacturer - MAC"
                    // Collect OUI lookup results once, before the when block
                    val lookupResults by ouiLookupViewModel.lookupResults.collectAsState()

                    // Trigger OUI lookup for WIFI and BLUETOOTH_LE protocols if manufacturer is unknown
                    val macAddress = detection.macAddress
                    LaunchedEffect(macAddress, detection.protocol) {
                        if ((detection.protocol == DetectionProtocol.WIFI || detection.protocol == DetectionProtocol.BLUETOOTH_LE)
                            && detection.manufacturer == null && macAddress != null) {
                            ouiLookupViewModel.lookupManufacturer(macAddress)
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = detection.protocol.toIcon(),
                            contentDescription = "${detection.protocol.displayName} network",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))

                        // Standardized format for all protocols
                        val displayText = when (detection.protocol) {
                            DetectionProtocol.CELLULAR -> {
                                // Cellular: "Network Type - Cell ID"
                                val networkType = detection.manufacturer ?: "Unknown Network"
                                val cellId = detection.firmwareVersion?.removePrefix("Cell ID: ") ?: "?"
                                "$networkType \u2022 Cell $cellId"
                            }
                            DetectionProtocol.WIFI -> {
                                // WiFi: "Manufacturer - MAC" or "SSID - MAC"
                                val resolvedManufacturer = detection.manufacturer
                                    ?: macAddress?.let { ouiLookupViewModel.getCachedManufacturer(it) }
                                val identifier = detection.macAddress ?: detection.ssid ?: "Unknown"
                                if (resolvedManufacturer != null) {
                                    "$resolvedManufacturer \u2022 $identifier"
                                } else {
                                    detection.ssid?.let { "$it \u2022 $identifier" } ?: identifier
                                }
                            }
                            DetectionProtocol.BLUETOOTH_LE -> {
                                // BLE: "Manufacturer - MAC"
                                val resolvedManufacturer = detection.manufacturer
                                    ?: macAddress?.let { ouiLookupViewModel.getCachedManufacturer(it) }
                                val identifier = detection.macAddress ?: "Unknown"
                                if (resolvedManufacturer != null) {
                                    "$resolvedManufacturer \u2022 $identifier"
                                } else {
                                    identifier
                                }
                            }
                            else -> {
                                // Other protocols: show whatever identifier is available
                                detection.macAddress ?: detection.ssid ?: detection.manufacturer ?: "Unknown"
                            }
                        }

                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    // Relative time only (removed exact timestamp to reduce clutter)
                    Text(
                        text = relativeTime,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (relativeTime == "Just now") threatColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    SignalIndicator(
                        rssi = detection.rssi,
                        signalStrength = detection.signalStrength
                    )
                }
            }

            // Location and metadata row
            if (detection.latitude != null || detection.seenCount > 1 || detection.isActive) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Location
                    if (detection.latitude != null && detection.longitude != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Location: %.4f, %.4f".format(detection.latitude, detection.longitude),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "%.4f, %.4f".format(detection.latitude, detection.longitude),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Seen count
                    if (detection.seenCount > 1) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "Seen ${detection.seenCount} times",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${detection.seenCount}x",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Active indicator
                    if (detection.isActive) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = StatusActive.copy(alpha = 0.2f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(StatusActive)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "ACTIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusActive
                                )
                            }
                        }
                    }
                }
            }

            // Enrichment status indicator
            EnrichmentStatusRow(
                detection = detection,
                isAnalyzing = isAnalyzing,
                isEnrichmentPending = isEnrichmentPending,
                onPrioritizeEnrichment = onPrioritizeEnrichment
            )

            // Advanced mode: Show additional technical details
            if (advancedMode) {
                var showRawData by remember { mutableStateOf(false) }

                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Detection method and protocol
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Method: ${detection.detectionMethod.displayName}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Score: ${detection.threatScore}/100",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = threatColor
                            )
                        }

                        // Service UUIDs if present
                        detection.serviceUuids?.let { uuids ->
                            if (uuids.isNotEmpty() && uuids != "[]") {
                                Text(
                                    text = "UUIDs: $uuids",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Matched patterns if present
                        detection.matchedPatterns?.let { patterns ->
                            if (patterns.isNotEmpty() && patterns != "[]") {
                                Text(
                                    text = "Patterns: $patterns",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // ID for debugging
                        Text(
                            text = "ID: ${detection.id.take(8)}...",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )

                        // Raw data frame toggle and display
                        detection.rawData?.let { rawData ->
                            if (rawData.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Divider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )

                                // Raw data toggle header
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showRawData = !showRawData }
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Code,
                                            contentDescription = "Raw data frame",
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Raw Data Frame",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "(${rawData.length / 2} bytes)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                    Icon(
                                        imageVector = if (showRawData) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (showRawData) "Collapse" else "Expand",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // Expandable raw data content
                                AnimatedVisibility(
                                    visible = showRawData,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        // Format hex data with spacing for readability
                                        val formattedHex = rawData.chunked(2).joinToString(" ")
                                        val formattedWithLineBreaks = formattedHex.chunked(48).joinToString("\n") // 16 bytes per line

                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.surface
                                        ) {
                                            SelectionContainer {
                                                Text(
                                                    text = formattedWithLineBreaks,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.padding(8.dp)
                                                )
                                            }
                                        }

                                        // ASCII representation
                                        val asciiRepresentation = rawData.chunked(2).mapNotNull { hex ->
                                            try {
                                                val byte = hex.toInt(16)
                                                if (byte in 32..126) byte.toChar() else '.'
                                            } catch (e: Exception) {
                                                null
                                            }
                                        }.joinToString("")

                                        if (asciiRepresentation.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "ASCII:",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.surface
                                            ) {
                                                SelectionContainer {
                                                    Text(
                                                        text = asciiRepresentation.chunked(32).joinToString("\n"),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                                        modifier = Modifier.padding(8.dp)
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
            }

                // Collapsible Analysis Section - collapsed by default to reduce density
                Spacer(modifier = Modifier.height(8.dp))

                // Toggle button to show/hide analysis
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAnalysis = !showAnalysis },
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = "Analysis and Tips",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Analysis & Tips",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = if (showAnalysis) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (showAnalysis) "Collapse analysis" else "Expand analysis",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Expandable analysis content
                AnimatedVisibility(
                    visible = showAnalysis,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        InlineAnalysisSection(
                            detection = detection,
                            isAnalyzing = isAnalyzing,
                            onAnalyzeClick = onAnalyzeClick
                        )
                    }
                }
            }
        }
    }
}

/**
 * Inline AI analysis section that shows contextual, actionable insights
 * directly in the detection card without requiring user interaction.
 */
@Composable
private fun InlineAnalysisSection(
    detection: Detection,
    isAnalyzing: Boolean,
    onAnalyzeClick: ((Detection) -> Unit)?
) {
    val insight = generateContextualInsight(detection)
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = insight.backgroundColor
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            // Main insight row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .semantics { stateDescription = if (expanded) "Insight expanded" else "Insight collapsed" },
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = insight.icon,
                    contentDescription = insight.headline,
                    tint = insight.iconColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = insight.headline,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = insight.textColor
                    )
                    Text(
                        text = insight.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = insight.textColor.copy(alpha = 0.85f)
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = insight.textColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Expanded details
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Divider(color = insight.textColor.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Action items
                    insight.actions.forEach { action ->
                        Row(
                            modifier = Modifier.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "\u2192",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = insight.iconColor
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = action,
                                style = MaterialTheme.typography.bodySmall,
                                color = insight.textColor.copy(alpha = 0.9f)
                            )
                        }
                    }

                    // Technical details
                    if (insight.technicalDetails.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = insight.technicalDetails,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = insight.textColor.copy(alpha = 0.6f)
                        )
                    }

                    // FP analysis if available
                    detection.fpReason?.let { reason ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Analysis reason",
                                tint = insight.textColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = reason,
                                style = MaterialTheme.typography.labelSmall,
                                color = insight.textColor.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Data class for contextual insights
 */
private data class ContextualInsight(
    val headline: String,
    val summary: String,
    val actions: List<String>,
    val technicalDetails: String,
    val icon: ImageVector,
    val backgroundColor: Color,
    val iconColor: Color,
    val textColor: Color
)

/**
 * Generate contextual, actionable insights based on detection characteristics
 */
@Composable
private fun generateContextualInsight(detection: Detection): ContextualInsight {
    val isFalsePositive = detection.fpScore != null && detection.fpScore >= 0.4f
    val fpConfidence = detection.fpScore ?: 0f

    // Determine insight based on threat level and device characteristics
    return when {
        // High confidence false positive
        isFalsePositive && fpConfidence >= 0.7f -> {
            ContextualInsight(
                headline = "Likely Safe",
                summary = getFpSummary(detection),
                actions = listOf(
                    "No immediate action needed",
                    "Common in residential/commercial areas",
                    "Dismiss or mark as safe if seen regularly"
                ),
                technicalDetails = "FP Score: ${(fpConfidence * 100).toInt()}% \u2022 ${detection.detectionMethod.displayName}",
                icon = Icons.Default.CheckCircle,
                backgroundColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                iconColor = MaterialTheme.colorScheme.tertiary,
                textColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }

        // Critical threat
        detection.threatLevel == ThreatLevel.CRITICAL -> {
            ContextualInsight(
                headline = "Active Surveillance",
                summary = getCriticalSummary(detection),
                actions = listOf(
                    "Leave the area if possible",
                    "Disable WiFi/Bluetooth temporarily",
                    "Note location and time for records",
                    "Consider if this is expected (police station, courthouse)"
                ),
                technicalDetails = "Threat: ${detection.threatScore}/100 \u2022 Signal: ${detection.rssi}dBm (${detection.signalStrength.description})",
                icon = Icons.Default.Warning,
                backgroundColor = MaterialTheme.colorScheme.errorContainer,
                iconColor = MaterialTheme.colorScheme.error,
                textColor = MaterialTheme.colorScheme.onErrorContainer
            )
        }

        // High threat
        detection.threatLevel == ThreatLevel.HIGH -> {
            ContextualInsight(
                headline = "Surveillance Device",
                summary = getHighThreatSummary(detection),
                actions = getHighThreatActions(detection),
                technicalDetails = "Threat: ${detection.threatScore}/100 \u2022 ${detection.protocol.displayName} \u2022 ${detection.signalStrength.description}",
                icon = Icons.Default.Shield,
                backgroundColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                iconColor = MaterialTheme.colorScheme.error,
                textColor = MaterialTheme.colorScheme.onErrorContainer
            )
        }

        // Medium threat
        detection.threatLevel == ThreatLevel.MEDIUM -> {
            ContextualInsight(
                headline = "Possible Surveillance",
                summary = getMediumThreatSummary(detection),
                actions = listOf(
                    "Monitor if this device follows you",
                    "Check if seen in multiple locations",
                    if (detection.seenCount > 1) "Seen ${detection.seenCount}x - pattern emerging" else "First sighting - may be transient"
                ),
                technicalDetails = "Threat: ${detection.threatScore}/100 \u2022 ${detection.detectionMethod.displayName}",
                icon = Icons.Default.Visibility,
                backgroundColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                iconColor = MaterialTheme.colorScheme.secondary,
                textColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        // Low threat / Info
        else -> {
            ContextualInsight(
                headline = "Device of Interest",
                summary = getLowThreatSummary(detection),
                actions = listOf(
                    "Likely benign but worth noting",
                    if (detection.seenCount == 1) "Single detection - probably passing device" else "Seen ${detection.seenCount}x at this location"
                ),
                technicalDetails = "${detection.deviceType.displayName} \u2022 ${detection.protocol.displayName}",
                icon = Icons.Default.Info,
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                textColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun getFpSummary(detection: Detection): String {
    return when (detection.fpCategory) {
        "BENIGN_DEVICE" -> "This matches a known consumer device pattern - likely a neighbor's router, smart device, or personal electronics."
        "CONSUMER_SMART_HOME" -> "Consumer smart home device (Ring, Nest, etc.) - common in residential areas, not surveillance."
        "PUBLIC_INFRASTRUCTURE" -> "Public WiFi or business network infrastructure - expected in commercial areas."
        "HOME_LOCATION" -> "Detected near your home - likely your own device or a neighbor's."
        "WORK_LOCATION" -> "Normal workplace infrastructure - expected security/networking equipment."
        "WEAK_SIGNAL" -> "Very weak signal from a distant device - not targeting you specifically."
        "TRANSIENT" -> "Brief one-time detection - likely a passing vehicle or pedestrian's device."
        else -> "Analysis suggests this is a common device, not targeted surveillance."
    }
}

private fun getCriticalSummary(detection: Detection): String {
    return when (detection.deviceType) {
        DeviceType.RAVEN_GUNSHOT_DETECTOR -> "Acoustic gunshot detection system - actively listening to audio in the area."
        DeviceType.STINGRAY_IMSI -> "Cell site simulator detected - can intercept calls and track phones."
        DeviceType.SHOTSPOTTER -> "ShotSpotter acoustic sensor - monitors audio for gunfire detection."
        DeviceType.FACIAL_RECOGNITION -> "Facial recognition system - capturing and analyzing faces."
        else -> "Active surveillance system capable of capturing audio, video, or communications."
    }
}

private fun getHighThreatSummary(detection: Detection): String {
    return when (detection.deviceType) {
        DeviceType.FLOCK_SAFETY_CAMERA -> "License plate reader capturing all passing vehicles. Your plate is being logged."
        DeviceType.RING_DOORBELL, DeviceType.NEST_CAMERA -> "Smart camera with cloud recording. Video may be shared with police."
        DeviceType.BODY_CAMERA -> "Law enforcement body camera - you may be recorded."
        DeviceType.CCTV_CAMERA, DeviceType.PTZ_CAMERA -> "Surveillance camera with ${if (detection.rssi > -60) "clear view of you" else "coverage of this area"}."
        DeviceType.WIFI_PINEAPPLE -> "Network attack device - do not connect to unknown WiFi here."
        DeviceType.DRONE -> "Drone with camera detected ${detection.signalStrength.description.lowercase()}."
        else -> "${detection.deviceType.displayName} confirmed. Recording or monitoring likely active."
    }
}

private fun getMediumThreatSummary(detection: Detection): String {
    return when (detection.detectionMethod) {
        DetectionMethod.WIFI_EVIL_TWIN -> "Possible fake WiFi network mimicking a legitimate one. Don't connect."
        DetectionMethod.WIFI_FOLLOWING -> "This network has appeared at multiple locations you've visited."
        DetectionMethod.TRACKER_FOLLOWING -> "Bluetooth tracker detected multiple times - may be following you."
        DetectionMethod.CELL_ENCRYPTION_DOWNGRADE -> "Your phone was forced to weaker encryption - potential interception."
        else -> "${detection.deviceType.displayName} - monitoring capabilities unconfirmed."
    }
}

private fun getLowThreatSummary(detection: Detection): String {
    return when {
        detection.deviceType in listOf(DeviceType.BLUETOOTH_BEACON, DeviceType.RETAIL_TRACKER) ->
            "Retail tracking beacon - stores use these for analytics and targeted ads."
        detection.deviceType == DeviceType.AMAZON_SIDEWALK ->
            "Amazon Sidewalk mesh network node - helps Amazon devices connect but also tracks."
        detection.protocol == DetectionProtocol.WIFI && detection.threatLevel == ThreatLevel.INFO ->
            "WiFi network with surveillance-like name but may be legitimate."
        else -> "Flagged due to ${detection.detectionMethod.displayName} but threat level is low."
    }
}

private fun getHighThreatActions(detection: Detection): List<String> {
    return when (detection.deviceType) {
        DeviceType.FLOCK_SAFETY_CAMERA -> listOf(
            "Your license plate is being recorded",
            "Data shared with law enforcement",
            "Common near neighborhoods, parking lots"
        )
        DeviceType.WIFI_PINEAPPLE -> listOf(
            "Do NOT connect to any WiFi here",
            "Disable auto-connect on your phone",
            "Use cellular data only in this area"
        )
        DeviceType.STINGRAY_IMSI -> listOf(
            "Phone calls may be intercepted",
            "Consider airplane mode",
            "Use encrypted messaging apps only"
        )
        else -> listOf(
            "Be aware of your surroundings",
            "Limit sensitive conversations",
            if (detection.rssi > -60) "Device is very close (${detection.signalStrength.description.lowercase()})" else "Device is ${detection.signalStrength.description.lowercase()}"
        )
    }
}

/**
 * Enhanced threat badge with more prominent styling for CRITICAL/HIGH threats.
 * Uses 14sp font size with more padding for better visibility.
 */
@Composable
fun ThreatBadge(
    threatLevel: ThreatLevel,
    modifier: Modifier = Modifier
) {
    val color = threatLevel.toColor()
    val isCriticalOrHigh = threatLevel == ThreatLevel.CRITICAL || threatLevel == ThreatLevel.HIGH

    Surface(
        modifier = modifier.semantics { stateDescription = "Threat level: ${threatLevel.name}" },
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = if (isCriticalOrHigh) 0.3f else 0.2f),
        shadowElevation = if (isCriticalOrHigh) 2.dp else 0.dp
    ) {
        Text(
            text = threatLevel.name,
            fontSize = 14.sp,
            fontWeight = if (isCriticalOrHigh) FontWeight.ExtraBold else FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/**
 * Intuitive signal indicator showing text label (Strong/Medium/Weak) with simple icon.
 * More user-friendly than technical dBm values.
 */
@Composable
fun SignalIndicator(
    rssi: Int,
    signalStrength: SignalStrength,
    modifier: Modifier = Modifier
) {
    val color = signalStrength.toColor()

    // Human-readable signal label
    val signalLabel = when (signalStrength) {
        SignalStrength.EXCELLENT -> "Strong"
        SignalStrength.GOOD -> "Strong"
        SignalStrength.MEDIUM -> "Medium"
        SignalStrength.WEAK -> "Weak"
        SignalStrength.VERY_WEAK -> "Weak"
        SignalStrength.UNKNOWN -> "Unknown"
    }

    // Simple icon based on signal strength (using available material icons)
    val signalIcon = when (signalStrength) {
        SignalStrength.EXCELLENT, SignalStrength.GOOD -> Icons.Default.NetworkWifi
        SignalStrength.MEDIUM -> Icons.Default.SignalCellularAlt
        SignalStrength.WEAK, SignalStrength.VERY_WEAK -> Icons.Default.SignalCellularAlt
        SignalStrength.UNKNOWN -> Icons.Default.SignalCellularOff
    }

    Row(
        modifier = modifier.semantics {
            stateDescription = "Signal strength: $signalLabel, $rssi dBm"
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = signalIcon,
            contentDescription = "$signalLabel signal",
            tint = color,
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = signalLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}
