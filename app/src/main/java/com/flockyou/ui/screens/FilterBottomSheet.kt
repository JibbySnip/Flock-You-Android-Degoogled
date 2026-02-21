@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.flockyou.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.Divider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flockyou.data.model.*
import com.flockyou.ui.components.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterBottomSheet(
    currentThreatFilter: ThreatLevel?,
    currentTypeFilters: Set<DeviceType>,
    filterMatchAll: Boolean,
    filterProtocols: Set<DetectionProtocol>,
    filterTimeRange: TimeRange,
    filterCustomStartTime: Long?,
    filterCustomEndTime: Long?,
    filterSignalStrength: Set<SignalStrength>,
    filterActiveOnly: Boolean,
    onThreatFilterChange: (ThreatLevel?) -> Unit,
    onTypeFilterToggle: (DeviceType) -> Unit,
    onMatchAllChange: (Boolean) -> Unit,
    onProtocolToggle: (DetectionProtocol) -> Unit,
    onTimeRangeChange: (TimeRange) -> Unit,
    onCustomTimeRangeChange: (Long, Long) -> Unit,
    onSignalStrengthToggle: (SignalStrength) -> Unit,
    onActiveOnlyChange: (Boolean) -> Unit,
    onClearFilters: () -> Unit,
    onDismiss: () -> Unit
) {
    // Section expansion states
    var timeRangeExpanded by remember { mutableStateOf(filterTimeRange != TimeRange.ALL_TIME) }
    var protocolExpanded by remember { mutableStateOf(filterProtocols.isNotEmpty()) }
    var threatExpanded by remember { mutableStateOf(currentThreatFilter != null) }
    var signalExpanded by remember { mutableStateOf(filterSignalStrength.isNotEmpty()) }
    var deviceTypeExpanded by remember { mutableStateOf(currentTypeFilters.isNotEmpty()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filters",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onClearFilters) {
                    Text("Clear")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Filters Row
            Text(
                text = "Quick Filters",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            QuickFiltersRow(
                activeOnly = filterActiveOnly,
                timeRange = filterTimeRange,
                onActiveOnlyChange = onActiveOnlyChange,
                onTimeRangeChange = onTimeRangeChange
            )

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))

            // Time Range Section
            CollapsibleFilterSection(
                title = "Time Range",
                selectedCount = if (filterTimeRange != TimeRange.ALL_TIME) 1 else 0,
                expanded = timeRangeExpanded,
                onExpandedChange = { timeRangeExpanded = it }
            ) {
                TimeRangeFilterSection(
                    selected = filterTimeRange,
                    customStart = filterCustomStartTime,
                    customEnd = filterCustomEndTime,
                    onSelectPreset = onTimeRangeChange,
                    onSelectCustom = onCustomTimeRangeChange
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))

            // Protocol Section
            CollapsibleFilterSection(
                title = "Protocol",
                selectedCount = filterProtocols.size,
                expanded = protocolExpanded,
                onExpandedChange = { protocolExpanded = it }
            ) {
                ProtocolFilterSection(
                    selected = filterProtocols,
                    onToggle = onProtocolToggle
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))

            // Threat Level Section
            CollapsibleFilterSection(
                title = "Threat Level",
                selectedCount = if (currentThreatFilter != null) 1 else 0,
                expanded = threatExpanded,
                onExpandedChange = { threatExpanded = it }
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThreatLevel.entries.forEach { level ->
                        FilterChip(
                            selected = currentThreatFilter == level,
                            onClick = {
                                onThreatFilterChange(if (currentThreatFilter == level) null else level)
                            },
                            label = { Text(level.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = level.toColor().copy(alpha = 0.2f),
                                selectedLabelColor = level.toColor()
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))

            // Signal Strength Section
            CollapsibleFilterSection(
                title = "Signal Strength",
                selectedCount = filterSignalStrength.size,
                expanded = signalExpanded,
                onExpandedChange = { signalExpanded = it }
            ) {
                SignalStrengthFilterSection(
                    selected = filterSignalStrength,
                    onToggle = onSignalStrengthToggle
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))

            // Device Type Section
            CollapsibleFilterSection(
                title = "Device Type",
                selectedCount = currentTypeFilters.size,
                expanded = deviceTypeExpanded,
                onExpandedChange = { deviceTypeExpanded = it }
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DeviceType.entries.forEach { type ->
                        FilterChip(
                            selected = type in currentTypeFilters,
                            onClick = { onTypeFilterToggle(type) },
                            label = { Text(type.name.replace("_", " ")) },
                            leadingIcon = {
                                Icon(
                                    imageVector = type.toIcon(),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))

            // AND/OR Logic Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Filter Logic",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (filterMatchAll) "Match ALL selected filters"
                               else "Match ANY selected filter",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = filterMatchAll,
                        onClick = { onMatchAllChange(true) },
                        label = { Text("AND") }
                    )
                    FilterChip(
                        selected = !filterMatchAll,
                        onClick = { onMatchAllChange(false) },
                        label = { Text("OR") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Apply Button
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apply Filters")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
