package com.flockyou.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

/**
 * Empty state when no detections with optional action button and last scan time
 */
@Composable
fun EmptyState(
    isScanning: Boolean,
    modifier: Modifier = Modifier,
    onStartScanning: (() -> Unit)? = null,
    lastScanTime: Long? = null
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (isScanning) Icons.Outlined.RadarOutlined else Icons.Outlined.SearchOff,
            contentDescription = if (isScanning) "Scanning for surveillance devices" else "No detections found",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isScanning) "Scanning for devices..." else "No detections yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = if (isScanning)
                "Surveillance devices will appear here when detected"
            else
                "Start scanning to detect surveillance devices",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )

        // Show last scan time if available
        lastScanTime?.let { timestamp ->
            if (timestamp > 0 && !isScanning) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Last scan: ${dateFormat.format(Date(timestamp))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        // Show start scanning button if not scanning and callback provided
        if (!isScanning && onStartScanning != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onStartScanning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Scanning")
            }
        }
    }
}

// Add RadarOutlined icon since it doesn't exist in material icons
private val Icons.Outlined.RadarOutlined: ImageVector
    get() = Icons.Default.Radar

/**
 * Empty state shown when filters are active but no detections match.
 * Offers a "Clear Filters" button to help the user recover.
 */
@Composable
fun FilteredEmptyState(
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.FilterAlt,
            contentDescription = "No matches",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No detections match your filters",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Try adjusting your filters or search query",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onClearFilters) {
            Icon(
                imageVector = Icons.Default.FilterAltOff,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Clear Filters")
        }
    }
}

/**
 * Shared section header component for settings screens
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .padding(vertical = 8.dp)
            .semantics { heading() }
    )
}
