@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.flockyou.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flockyou.data.model.*
import com.flockyou.scanner.flipper.FlipperConnectionState
import com.flockyou.service.CellularMonitor
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import com.flockyou.R
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.flockyou.ui.components.*
import com.flockyou.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateToMap: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToNearby: () -> Unit = {},
    onNavigateToRfDetection: () -> Unit = {},
    onNavigateToUltrasonicDetection: () -> Unit = {},
    onNavigateToSatelliteDetection: () -> Unit = {},
    onNavigateToWifiSecurity: () -> Unit = {},
    onNavigateToServiceHealth: () -> Unit = {},
    onNavigateToActiveProbes: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val prioritizedEnrichmentIds by viewModel.prioritizedEnrichmentIds.collectAsState()
    val flipperUiSettings by viewModel.flipperUiSettings.collectAsState()
    val flipperSettings by viewModel.flipperSettings.collectAsState()
    val flipperIsInstalling by viewModel.flipperIsInstalling.collectAsState()
    val flipperInstallProgress by viewModel.flipperInstallProgress.collectAsState()
    val relatedDetections by viewModel.relatedDetections.collectAsState()

    // Filtered anomalies (excludes FP-marked detections)
    val filteredCellularAnomalies = remember(uiState.cellularAnomalies, uiState.detections, uiState.hideFalsePositives, uiState.fpFilterThreshold) {
        viewModel.getFilteredCellularAnomalies()
    }
    val filteredSatelliteAnomalies = remember(uiState.satelliteAnomalies, uiState.detections, uiState.hideFalsePositives, uiState.fpFilterThreshold) {
        viewModel.getFilteredSatelliteAnomalies()
    }
    val filteredRogueWifiAnomalies = remember(uiState.rogueWifiAnomalies, uiState.detections, uiState.hideFalsePositives, uiState.fpFilterThreshold) {
        viewModel.getFilteredRogueWifiAnomalies()
    }

    val context = LocalContext.current
    var showFilterSheet by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var selectedDetection by remember { mutableStateOf<Detection?>(null) }

    // Snackbar host state for showing refresh completion toast
    val snackbarHostState = remember { SnackbarHostState() }

    // Pager state for swipe navigation between tabs
    // Use pagerState as single source of truth for tab position
    val pagerState = rememberPagerState(
        initialPage = uiState.selectedTab,
        pageCount = { 4 }  // Home, History, Cellular, Flipper
    )
    val coroutineScope = rememberCoroutineScope()

    // Track detection count before refresh for delta calculation
    var preRefreshCount by remember { mutableStateOf(0) }

    // Pull-to-refresh state
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            preRefreshCount = uiState.totalCount
            isRefreshing = true
            viewModel.requestRefresh()
            coroutineScope.launch {
                delay(1500) // Give time for data to refresh
                isRefreshing = false
                // Show completion snackbar with detection delta
                // Read current value directly from viewModel to avoid stale closure
                val currentCount = viewModel.uiState.value.totalCount
                val newDetections = currentCount - preRefreshCount
                val message = when {
                    newDetections > 0 -> "Updated - $newDetections new detection${if (newDetections > 1) "s" else ""}"
                    newDetections < 0 -> "Updated - Removed ${-newDetections} detection${if (-newDetections > 1) "s" else ""}"
                    else -> "Updated - No new detections"
                }
                snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
            }
        }
    )

    // Track if we're currently animating from a programmatic navigation
    // This prevents the pager from fighting with the ViewModel during animations
    var isNavigatingProgrammatically by remember { mutableStateOf(false) }

    // Sync ViewModel state when pager settles after user swipe
    // Use snapshotFlow with settledPage to only trigger when page is fully settled
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collectLatest { settledPage ->
            // Only update ViewModel if this wasn't a programmatic navigation
            // and the values are actually different
            if (!isNavigatingProgrammatically && uiState.selectedTab != settledPage) {
                viewModel.selectTab(settledPage)
            }
            isNavigatingProgrammatically = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.app_title_flock),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = " ${stringResource(R.string.app_title_you)}",
                            fontWeight = FontWeight.Light
                        )
                    }
                },
                actions = {
                    // Show filter button on history and home tabs with badge showing active filter count
                    if (uiState.selectedTab == 0 || uiState.selectedTab == 1) {
                        val filterCount = viewModel.getActiveFilterCount()
                        FilterButton(
                            filterCount = filterCount,
                            onClick = { showFilterSheet = true }
                        )
                    }
                    IconButton(onClick = onNavigateToNearby) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = "Nearby Devices"
                        )
                    }
                    IconButton(onClick = onNavigateToMap) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = "Map"
                        )
                    }
                    // Export debug info button - only shown in advanced mode
                    if (uiState.advancedMode) {
                        IconButton(
                            onClick = {
                                val debugInfo = viewModel.exportAllDebugInfo()
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.debug_export_subject))
                                    putExtra(Intent.EXTRA_TEXT, debugInfo)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Export Debug Info"))
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = "Export Debug Info"
                            )
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            // Helper function to navigate to a page with debounce protection
            val navigateToPage: (Int) -> Unit = { targetPage ->
                // Skip if we're already on this page or animation is in progress
                if (pagerState.currentPage != targetPage && !pagerState.isScrollInProgress) {
                    isNavigatingProgrammatically = true
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(targetPage)
                    }
                }
            }

            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    selected = pagerState.currentPage == 0,
                    onClick = { navigateToPage(0) }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text("History") },
                    selected = pagerState.currentPage == 1,
                    onClick = { navigateToPage(1) }
                )
                NavigationBarItem(
                    icon = {
                        BadgedBox(
                            badge = {
                                if (filteredCellularAnomalies.isNotEmpty()) {
                                    Badge { Text(filteredCellularAnomalies.size.toString()) }
                                } else {
                                    // Show checkmark badge when no anomalies
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.tertiary
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.CellTower, contentDescription = "Cellular")
                        }
                    },
                    label = { Text("Cellular") },
                    selected = pagerState.currentPage == 2,
                    onClick = { navigateToPage(2) }
                )
                NavigationBarItem(
                    icon = {
                        BadgedBox(
                            badge = {
                                when (uiState.flipperConnectionState) {
                                    FlipperConnectionState.READY -> {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.tertiary
                                        ) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(10.dp)
                                            )
                                        }
                                    }
                                    FlipperConnectionState.CONNECTING,
                                    FlipperConnectionState.CONNECTED,
                                    FlipperConnectionState.DISCOVERING_SERVICES -> {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.secondary
                                        ) {
                                            Icon(
                                                Icons.Default.Sync,
                                                contentDescription = null,
                                                modifier = Modifier.size(10.dp)
                                            )
                                        }
                                    }
                                    FlipperConnectionState.ERROR -> {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.error
                                        ) {
                                            Icon(
                                                Icons.Default.Warning,
                                                contentDescription = null,
                                                modifier = Modifier.size(10.dp)
                                            )
                                        }
                                    }
                                    else -> {} // No badge when disconnected
                                }
                            }
                        ) {
                            Icon(Icons.Default.Usb, contentDescription = "Flipper")
                        }
                    },
                    label = { Text("Flipper") },
                    selected = pagerState.currentPage == 3,
                    onClick = { navigateToPage(3) }
                )
            }
        }
    ) { paddingValues ->
        // Swipeable HorizontalPager for tab navigation with pull-to-refresh
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Satellite connection banner - shown across all tabs when satellite is active
            val satState = uiState.satelliteState
            if (satState?.isConnected == true) {
                SatelliteConnectionBanner(satelliteState = satState)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .pullRefresh(pullRefreshState)
            ) { page ->
                when (page) {
                    0 -> {
                        // Home tab - Status and modules only (no errors, no recent detections)
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                        // Status card without errors
                        item(key = "status_card") {
                            StatusCard(
                                isScanning = uiState.isScanning,
                                totalDetections = uiState.totalCount,
                                highThreatCount = uiState.highThreatCount,
                                onToggleScan = { viewModel.toggleScanning() },
                                scanStatus = uiState.scanStatus,
                                bleStatus = uiState.bleStatus,
                                wifiStatus = uiState.wifiStatus,
                                locationStatus = uiState.locationStatus,
                                cellularStatus = uiState.cellularStatus,
                                satelliteStatus = uiState.satelliteStatus,
                                recentErrors = emptyList(), // Don't show errors on home
                                onClearErrors = { }
                            )
                        }

                        // Cellular status card (show when scanning or has anomalies)
                        if (uiState.isScanning || filteredCellularAnomalies.isNotEmpty()) {
                            item(key = "cellular_status_card") {
                                CellularStatusCard(
                                    cellStatus = uiState.cellStatus,
                                    anomalies = filteredCellularAnomalies,
                                    isMonitoring = uiState.cellularStatus == com.flockyou.service.SubsystemStatus.Active
                                )
                            }
                        }

                        // Detection Modules section
                        item(key = "detection_modules_header") {
                            Text(
                                text = "DETECTION MODULES",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        item(key = "detection_modules_grid") {
                            DetectionModulesGrid(
                                onNavigateToRfDetection = onNavigateToRfDetection,
                                onNavigateToUltrasonicDetection = onNavigateToUltrasonicDetection,
                                onNavigateToSatelliteDetection = onNavigateToSatelliteDetection,
                                onNavigateToWifiSecurity = onNavigateToWifiSecurity,
                                wifiAnomalyCount = filteredRogueWifiAnomalies.size,
                                rfAnomalyCount = viewModel.getFilteredRfAnomalies().size,
                                ultrasonicBeaconCount = uiState.ultrasonicBeacons.size,
                                satelliteAnomalyCount = filteredSatelliteAnomalies.size
                            )
                        }

                        // Service Health shortcut card
                        item(key = "service_health_shortcut") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                ),
                                onClick = onNavigateToServiceHealth
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MonitorHeart,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Service Health",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "View detector status and errors",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Go",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        }
                    }
                    1 -> {
                        // History tab - Detection list with filters
                        val filteredDetections = viewModel.getFilteredDetections()

                        // Track expanded detection IDs (persists during scroll)
                        val expandedDetectionIds = remember { mutableStateMapOf<String, Boolean>() }

                        // Track last action for undo
                        var lastMarkedDetection by remember { mutableStateOf<Detection?>(null) }
                        var lastActionType by remember { mutableStateOf<String?>(null) }

                        // Handle undo action
                        LaunchedEffect(lastMarkedDetection, lastActionType) {
                            if (lastMarkedDetection != null && lastActionType != null) {
                                val result = snackbarHostState.showSnackbar(
                                    message = when (lastActionType) {
                                        "reviewed" -> "Marked as reviewed"
                                        "false_positive" -> "Marked as false positive"
                                        else -> "Updated"
                                    },
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    lastMarkedDetection?.let { viewModel.undoMarkDetection(it) }
                                }
                                lastMarkedDetection = null
                                lastActionType = null
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                        // Filter chips if filters active (only on history tab)
                        if (uiState.filterThreatLevel != null || uiState.filterDeviceTypes.isNotEmpty()) {
                            item(key = "filter_chips") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Filter mode indicator
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Filter mode:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        FilterChip(
                                            selected = uiState.filterMatchAll,
                                            onClick = { viewModel.setFilterMatchAll(!uiState.filterMatchAll) },
                                            label = { Text(if (uiState.filterMatchAll) "Match ALL" else "Match ANY") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (uiState.filterMatchAll) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        )
                                    }

                                    // Active filter chips
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        uiState.filterThreatLevel?.let { level ->
                                            FilterChip(
                                                selected = true,
                                                onClick = { viewModel.setThreatFilter(null) },
                                                label = { Text(level.name) },
                                                trailingIcon = {
                                                    Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = "Remove filter",
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            )
                                        }
                                    }

                                    // Device type filter chips (can have multiple now)
                                    if (uiState.filterDeviceTypes.isNotEmpty()) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            uiState.filterDeviceTypes.take(3).forEach { type ->
                                                FilterChip(
                                                    selected = true,
                                                    onClick = { viewModel.removeDeviceTypeFilter(type) },
                                                    label = { Text(type.name.replace("_", " ")) },
                                                    trailingIcon = {
                                                        Icon(
                                                            Icons.Default.Close,
                                                            contentDescription = "Remove filter",
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                )
                                            }
                                            if (uiState.filterDeviceTypes.size > 3) {
                                                FilterChip(
                                                    selected = true,
                                                    onClick = { showFilterSheet = true },
                                                    label = { Text("+${uiState.filterDeviceTypes.size - 3} more") }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // FP filter toggle and section header
                        item(key = "section_header") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "DETECTION HISTORY",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    // FP filter toggle - always visible so users can toggle it
                                    val fpCount = viewModel.getFalsePositiveCount()
                                    FilterChip(
                                        selected = !uiState.hideFalsePositives,
                                        onClick = { viewModel.toggleHideFalsePositives() },
                                        label = {
                                            Text(
                                                text = if (uiState.hideFalsePositives) {
                                                    if (fpCount > 0) "Show FPs ($fpCount hidden)" else "FP filter on"
                                                } else {
                                                    "Showing all"
                                                },
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (uiState.hideFalsePositives)
                                                    Icons.Default.VisibilityOff
                                                else
                                                    Icons.Default.Visibility,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        when {
                            uiState.isLoading -> {
                                item(key = "loading_state") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator()
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = "Loading detections...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            filteredDetections.isEmpty() -> {
                                item(key = "empty_state") {
                                    EmptyState(
                                        isScanning = uiState.isScanning,
                                        onStartScanning = { viewModel.toggleScanning() }
                                    )
                                }
                            }
                            else -> {
                                items(
                                    items = filteredDetections,
                                    key = { it.id }
                                ) { detection ->
                                    SwipeableDetectionCard(
                                        detection = detection,
                                        onClick = { selectedDetection = detection },
                                        onMarkReviewed = { det ->
                                            viewModel.markAsReviewed(det)
                                            lastMarkedDetection = det
                                            lastActionType = "reviewed"
                                        },
                                        onMarkFalsePositive = { det ->
                                            viewModel.markAsFalsePositive(det)
                                            lastMarkedDetection = det
                                            lastActionType = "false_positive"
                                        },
                                        advancedMode = uiState.advancedMode,
                                        isExpanded = expandedDetectionIds[detection.id] == true,
                                        onExpandToggle = {
                                            expandedDetectionIds[detection.id] = !(expandedDetectionIds[detection.id] ?: false)
                                        },
                                        onAnalyzeClick = if (viewModel.isAiAnalysisAvailable()) {
                                            { viewModel.analyzeDetection(it) }
                                        } else null,
                                        isAnalyzing = uiState.analyzingDetectionId == detection.id,
                                        onPrioritizeEnrichment = { viewModel.prioritizeEnrichment(it) },
                                        isEnrichmentPending = prioritizedEnrichmentIds.contains(detection.id)
                                    )
                                }
                            }
                        }
                        }
                    }
                    2 -> {
                        // Cellular tab content
                        CellularTabContent(
                            modifier = Modifier.fillMaxSize(),
                            cellStatus = uiState.cellStatus,
                            cellularStatus = uiState.cellularStatus,
                            cellularAnomalies = filteredCellularAnomalies,
                            seenCellTowers = uiState.seenCellTowers,
                            cellularEvents = uiState.cellularEvents,
                            satelliteState = uiState.satelliteState,
                            satelliteAnomalies = filteredSatelliteAnomalies,
                            isScanning = uiState.isScanning,
                            onToggleScan = { viewModel.toggleScanning() },
                            onClearCellularHistory = { viewModel.clearCellularHistory() },
                            onClearSatelliteHistory = { viewModel.clearSatelliteHistory() }
                        )
                    }
                    3 -> {
                        // Flipper Zero tab content
                        FlipperTabContent(
                            modifier = Modifier.fillMaxSize(),
                            connectionState = uiState.flipperConnectionState,
                            connectionType = uiState.flipperConnectionType,
                            flipperStatus = uiState.flipperStatus,
                            isScanning = uiState.flipperIsScanning,
                            detectionCount = uiState.flipperDetectionCount,
                            wipsAlertCount = uiState.flipperWipsAlertCount,
                            lastError = uiState.flipperLastError,
                            advancedMode = uiState.advancedMode,
                            scanSchedulerStatus = uiState.flipperScanSchedulerStatus,
                            // UX improvement parameters
                            autoReconnectState = uiState.flipperAutoReconnectState,
                            discoveredDevices = uiState.flipperDiscoveredDevices,
                            recentDevices = uiState.flipperRecentDevices,
                            isScanningForDevices = uiState.flipperIsScanningForDevices,
                            connectionRssi = uiState.flipperConnectionRssi,
                            showDevicePicker = uiState.flipperShowDevicePicker,
                            // Settings parameters
                            flipperSettings = flipperSettings,
                            isInstalling = flipperIsInstalling,
                            installProgress = flipperInstallProgress,
                            // Callbacks
                            flipperUiSettings = flipperUiSettings,
                            onConnect = { viewModel.showFlipperDevicePicker() },
                            onDisconnect = { viewModel.disconnectFlipper() },
                            onTogglePause = { viewModel.toggleFlipperPause() },
                            onTriggerManualScan = { scanType -> viewModel.triggerFlipperManualScan(scanType) },
                            onViewModeChange = { viewModel.setFlipperViewMode(it) },
                            onStatusCardExpandedChange = { viewModel.setFlipperStatusCardExpanded(it) },
                            onSchedulerCardExpandedChange = { viewModel.setFlipperSchedulerCardExpanded(it) },
                            onStatsCardExpandedChange = { viewModel.setFlipperStatsCardExpanded(it) },
                            onCapabilitiesCardExpandedChange = { viewModel.setFlipperCapabilitiesCardExpanded(it) },
                            onAdvancedCardExpandedChange = { viewModel.setFlipperAdvancedCardExpanded(it) },
                            // Device picker callbacks
                            onShowDevicePicker = { viewModel.showFlipperDevicePicker() },
                            onHideDevicePicker = { viewModel.hideFlipperDevicePicker() },
                            onStartDeviceScan = { viewModel.startFlipperDeviceScan() },
                            onStopDeviceScan = { viewModel.stopFlipperDeviceScan() },
                            onSelectDiscoveredDevice = { viewModel.connectToDiscoveredFlipper(it) },
                            onSelectRecentDevice = { viewModel.connectToRecentFlipper(it) },
                            onRemoveRecentDevice = { viewModel.removeFlipperFromHistory(it) },
                            onCancelAutoReconnect = { viewModel.cancelFlipperAutoReconnect() },
                            onConnectUsb = { viewModel.connectFlipperViaUsb() },
                            // Settings callbacks
                            onInstallFap = { viewModel.installFapToFlipper() },
                            onPreferredConnectionChange = { viewModel.setFlipperPreferredConnection(it) },
                            onAutoConnectUsbChange = { viewModel.setFlipperAutoConnectUsb(it) },
                            onAutoConnectBluetoothChange = { viewModel.setFlipperAutoConnectBluetooth(it) },
                            onEnableWifiScanningChange = { viewModel.setFlipperEnableWifiScanning(it) },
                            onEnableSubGhzScanningChange = { viewModel.setFlipperEnableSubGhzScanning(it) },
                            onEnableBleScanningChange = { viewModel.setFlipperEnableBleScanning(it) },
                            onEnableIrScanningChange = { viewModel.setFlipperEnableIrScanning(it) },
                            onEnableNfcScanningChange = { viewModel.setFlipperEnableNfcScanning(it) },
                            onWipsEnabledChange = { viewModel.setFlipperWipsEnabled(it) },
                            onWipsEvilTwinChange = { viewModel.setFlipperWipsEvilTwinDetection(it) },
                            onWipsDeauthChange = { viewModel.setFlipperWipsDeauthDetection(it) },
                            onWipsKarmaChange = { viewModel.setFlipperWipsKarmaDetection(it) },
                            onWipsRogueApChange = { viewModel.setFlipperWipsRogueApDetection(it) },
                            onNavigateToActiveProbes = onNavigateToActiveProbes
                        )
                    }
                }
            }

            // Pull-to-refresh indicator with text
            Column(
                modifier = Modifier.align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PullRefreshIndicator(
                    refreshing = isRefreshing,
                    state = pullRefreshState,
                    contentColor = MaterialTheme.colorScheme.primary
                )
                // Show refreshing text below the indicator
                AnimatedVisibility(
                    visible = isRefreshing,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        modifier = Modifier.padding(top = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Refreshing detections...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        } // Close weighted Box
        } // Close outer Column
    }

    // Filter bottom sheet
    if (showFilterSheet) {
        FilterBottomSheet(
            currentThreatFilter = uiState.filterThreatLevel,
            currentTypeFilters = uiState.filterDeviceTypes,
            filterMatchAll = uiState.filterMatchAll,
            filterProtocols = uiState.filterProtocols,
            filterTimeRange = uiState.filterTimeRange,
            filterCustomStartTime = uiState.filterCustomStartTime,
            filterCustomEndTime = uiState.filterCustomEndTime,
            filterSignalStrength = uiState.filterSignalStrength,
            filterActiveOnly = uiState.filterActiveOnly,
            onThreatFilterChange = { viewModel.setThreatFilter(it) },
            onTypeFilterToggle = { viewModel.toggleDeviceTypeFilter(it) },
            onMatchAllChange = { viewModel.setFilterMatchAll(it) },
            onProtocolToggle = { viewModel.toggleProtocolFilter(it) },
            onTimeRangeChange = { viewModel.setTimeRange(it) },
            onCustomTimeRangeChange = { start, end -> viewModel.setCustomTimeRange(start, end) },
            onSignalStrengthToggle = { viewModel.toggleSignalStrengthFilter(it) },
            onActiveOnlyChange = { viewModel.setActiveOnly(it) },
            onClearFilters = { viewModel.clearFilters() },
            onDismiss = { showFilterSheet = false }
        )
    }

    // Clear all confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
            title = { Text("Clear All Detections?") },
            text = { Text("This will permanently delete all detection history. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllDetections()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Detection detail sheet
    selectedDetection?.let { detection ->
        // Load related detections when the sheet is shown
        LaunchedEffect(detection.id) {
            viewModel.loadRelatedDetections(detection)
        }

        DetectionDetailSheet(
            detection = detection,
            privilegeMode = viewModel.privilegeMode,
            onDismiss = {
                viewModel.clearRelatedDetections()
                selectedDetection = null
            },
            onDelete = {
                viewModel.deleteDetection(detection)
                viewModel.clearRelatedDetections()
                selectedDetection = null
            },
            onMarkSafe = {
                viewModel.markAsFalsePositive(detection)
                viewModel.clearRelatedDetections()
                selectedDetection = null
            },
            onMarkThreat = {
                viewModel.markAsConfirmedThreat(detection)
                viewModel.clearRelatedDetections()
                selectedDetection = null
            },
            onShare = {
                val shareText = buildDetectionShareText(detection, context)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "${context.getString(R.string.share_detection_subject)}: ${detection.deviceType.displayName}")
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Detection"))
            },
            onNavigate = if (detection.latitude != null && detection.longitude != null) {
                {
                    val geoUri = Uri.parse("geo:${detection.latitude},${detection.longitude}?q=${detection.latitude},${detection.longitude}(${detection.deviceType.displayName})")
                    val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
                    if (mapIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(mapIntent)
                    }
                }
            } else null,
            onAddNote = { note ->
                viewModel.updateUserNote(detection, note)
            },
            onExport = {
                val exportText = buildDetectionExportJson(detection)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_detection_export_subject))
                    putExtra(Intent.EXTRA_TEXT, exportText)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Export Detection"))
            },
            advancedMode = uiState.advancedMode,
            relatedDetections = relatedDetections,
            onRelatedDetectionClick = { relatedDetection ->
                // Select the clicked related detection
                selectedDetection = relatedDetection
                viewModel.onRelatedDetectionSelected(relatedDetection)
            },
            onSeeAllRelatedClick = null
        )
    }

    // AI Analysis result dialog
    uiState.analysisResult?.let { result ->
        AiAnalysisResultDialog(
            result = result,
            onDismiss = { viewModel.clearAnalysisResult() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CellularTabContent(
    modifier: Modifier = Modifier,
    cellStatus: CellularMonitor.CellStatus?,
    cellularStatus: com.flockyou.service.SubsystemStatus,
    cellularAnomalies: List<CellularMonitor.CellularAnomaly>,
    seenCellTowers: List<CellularMonitor.SeenCellTower>,
    cellularEvents: List<CellularMonitor.CellularEvent>,
    satelliteState: com.flockyou.monitoring.SatelliteMonitor.SatelliteConnectionState?,
    satelliteAnomalies: List<com.flockyou.monitoring.SatelliteMonitor.SatelliteAnomaly>,
    isScanning: Boolean,
    onToggleScan: () -> Unit,
    onClearCellularHistory: () -> Unit,
    onClearSatelliteHistory: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    var showTimelineSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Timeline Bottom Sheet
    if (showTimelineSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTimelineSheet = false },
            sheetState = sheetState,
            modifier = Modifier.fillMaxHeight(0.9f)
        ) {
            CellularTimelineScreen(
                events = cellularEvents,
                seenTowers = seenCellTowers,
                cellStatus = cellStatus,
                onClearHistory = onClearCellularHistory
            )
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Status card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when (cellularStatus) {
                        is com.flockyou.service.SubsystemStatus.Active ->
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        is com.flockyou.service.SubsystemStatus.PermissionDenied ->
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CellTower,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Cellular Monitoring",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Button(
                            onClick = onToggleScan,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isScanning)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(if (isScanning) "Stop" else "Start")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when (cellularStatus) {
                            is com.flockyou.service.SubsystemStatus.Active -> StatusActive
                            is com.flockyou.service.SubsystemStatus.PermissionDenied -> StatusError
                            else -> StatusInactive
                        }
                    ) {
                        Text(
                            text = when (cellularStatus) {
                                is com.flockyou.service.SubsystemStatus.Active -> "\uD83D\uDFE2 Active"
                                is com.flockyou.service.SubsystemStatus.PermissionDenied -> "\u26D4 No Permission"
                                is com.flockyou.service.SubsystemStatus.Error -> "\u26A0\uFE0F Error"
                                else -> "\u26AA Idle"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (cellularStatus is com.flockyou.service.SubsystemStatus.PermissionDenied) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "\u26A0\uFE0F READ_PHONE_STATE permission required for IMSI catcher detection",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        PermissionRecoveryButton()
                    }
                }
            }
        }

        // Current cell info
        if (cellStatus != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "\uD83D\uDCF6 Current Cell Tower",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when (cellStatus.networkGeneration) {
                                    "5G" -> Network5G
                                    "4G" -> Network4G
                                    "3G" -> Network3G
                                    "2G" -> Network2G
                                    else -> StatusInactive
                                }
                            ) {
                                Text(
                                    text = cellStatus.networkGeneration,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cellStatus.networkType,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                cellStatus.operator?.let { op ->
                                    Text(
                                        text = op,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${cellStatus.signalStrength} dBm",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${cellStatus.signalBars}/4 bars",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Cell ID", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(cellStatus.cellId, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            }
                            cellStatus.mcc?.let {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("MCC", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                }
                            }
                            cellStatus.mnc?.let {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("MNC", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Anomalies section
        if (cellularAnomalies.isNotEmpty()) {
            item {
                Text(
                    text = "\u26A0\uFE0F Detected Anomalies (${cellularAnomalies.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }

            items(
                items = cellularAnomalies.take(10),
                key = { it.id }
            ) { anomaly ->
                CellularAnomalyCard(anomaly = anomaly, dateFormat = dateFormat)
            }
        }

        // Cell tower history
        if (seenCellTowers.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "\uD83D\uDDFC Cell Tower History (${seenCellTowers.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { showTimelineSheet = true }) {
                            Icon(
                                Icons.Default.Timeline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Timeline")
                        }
                        TextButton(onClick = onClearCellularHistory) {
                            Text("Clear")
                        }
                    }
                }
            }

            items(
                items = seenCellTowers.take(5),
                key = { "${it.mcc}-${it.mnc}-${it.lac}-${it.cellId}" }
            ) { tower ->
                CellTowerHistoryCard(tower = tower, dateFormat = dateFormat)
            }

            // Show "View All" if there are more towers
            if (seenCellTowers.size > 5) {
                item {
                    TextButton(
                        onClick = { showTimelineSheet = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("View all ${seenCellTowers.size} towers \u2192")
                    }
                }
            }
        }

        // Satellite status card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        satelliteState?.isConnected == true -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.SatelliteAlt,
                                contentDescription = null,
                                tint = if (satelliteState?.isConnected == true)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "\uD83D\uDEF0\uFE0F Satellite Status",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                val satState = satelliteState
                                Text(
                                    text = when {
                                        satState?.isConnected == true ->
                                            "Connected: ${satState.connectionType.name.replace("_", " ")}"
                                        isScanning -> "Monitoring"
                                        else -> "Not connected"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (satelliteState?.isConnected == true) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = StatusActive.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "CONNECTED",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusActive,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    satelliteState?.let { satState ->
                        if (satState.isConnected && satState.provider != com.flockyou.monitoring.SatelliteMonitor.SatelliteProvider.UNKNOWN) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Provider: ${satState.provider.name} | Network: ${satState.networkName ?: "Unknown"}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Satellite anomalies
        if (satelliteAnomalies.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "\uD83D\uDEF0\uFE0F Satellite Anomalies (${satelliteAnomalies.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onClearSatelliteHistory) {
                        Text("Clear")
                    }
                }
            }

            items(
                items = satelliteAnomalies.take(10),
                key = { "${it.type}-${it.timestamp}-${it.hashCode()}" }
            ) { anomaly ->
                SatelliteAnomalyHistoryCard(anomaly = anomaly, dateFormat = dateFormat)
            }
        }

        // Info card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "\u2139\uFE0F About IMSI Catcher Detection",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This monitors for signs of cell site simulators (StingRay, Hailstorm, etc.):\n" +
                            "\u2022 Encryption downgrades (4G/5G \u2192 2G)\n" +
                            "\u2022 Suspicious network identifiers\n" +
                            "\u2022 Unexpected cell tower changes\n" +
                            "\u2022 Signal anomalies",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CellularAnomalyCard(
    anomaly: CellularMonitor.CellularAnomaly,
    dateFormat: SimpleDateFormat
) {
    val severityColor = when (anomaly.severity) {
        ThreatLevel.CRITICAL -> ThreatCritical
        ThreatLevel.HIGH -> ThreatHigh
        ThreatLevel.MEDIUM -> ThreatMedium
        else -> StatusInactive
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = severityColor.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(anomaly.type.emoji, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = anomaly.type.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = severityColor
                    )
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = severityColor
                ) {
                    Text(
                        text = anomaly.severity.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = anomaly.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = dateFormat.format(Date(anomaly.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${anomaly.signalStrength} dBm \u2022 ${anomaly.networkType}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CellTowerHistoryCard(
    tower: CellularMonitor.SeenCellTower,
    dateFormat: SimpleDateFormat
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when (tower.networkGeneration) {
                            "5G" -> Network5G
                            "4G" -> Network4G
                            "3G" -> Network3G
                            "2G" -> Network2G
                            else -> StatusInactive
                        }
                    ) {
                        Text(
                            text = tower.networkGeneration,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = tower.operator ?: "Unknown",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Cell ${tower.cellId}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${tower.lastSignal} dBm",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${tower.seenCount}x",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        tower.mcc?.let {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("MCC", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            }
                        }
                        tower.mnc?.let {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("MNC", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            }
                        }
                        tower.lac?.let {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("LAC", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(it.toString(), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "First: ${dateFormat.format(Date(tower.firstSeen))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Last: ${dateFormat.format(Date(tower.lastSeen))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SatelliteAnomalyHistoryCard(
    anomaly: com.flockyou.monitoring.SatelliteMonitor.SatelliteAnomaly,
    dateFormat: SimpleDateFormat
) {
    val severityColor = when (anomaly.severity) {
        com.flockyou.monitoring.SatelliteMonitor.AnomalySeverity.CRITICAL -> ThreatCritical
        com.flockyou.monitoring.SatelliteMonitor.AnomalySeverity.HIGH -> ThreatHigh
        com.flockyou.monitoring.SatelliteMonitor.AnomalySeverity.MEDIUM -> ThreatMedium
        com.flockyou.monitoring.SatelliteMonitor.AnomalySeverity.LOW -> ThreatLow
        com.flockyou.monitoring.SatelliteMonitor.AnomalySeverity.INFO -> ThreatInfo
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = severityColor.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.SatelliteAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = severityColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = anomaly.type.name.replace("_", " "),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = dateFormat.format(Date(anomaly.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = severityColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = anomaly.severity.name,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = severityColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = anomaly.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Detection modules grid with quick access to specialized detection screens
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetectionModulesGrid(
    onNavigateToRfDetection: () -> Unit,
    onNavigateToUltrasonicDetection: () -> Unit,
    onNavigateToSatelliteDetection: () -> Unit,
    onNavigateToWifiSecurity: () -> Unit,
    wifiAnomalyCount: Int,
    rfAnomalyCount: Int,
    ultrasonicBeaconCount: Int,
    satelliteAnomalyCount: Int
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // First row: WiFi Security & RF Analysis
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DetectionModuleCard(
                modifier = Modifier.weight(1f),
                title = "WiFi Security",
                description = "Evil twin & rogue AP detection",
                icon = Icons.Default.Wifi,
                badgeCount = wifiAnomalyCount,
                iconTint = Color(0xFF2196F3),
                onClick = onNavigateToWifiSecurity
            )
            DetectionModuleCard(
                modifier = Modifier.weight(1f),
                title = "RF Analysis",
                description = "Jammers, drones & spectrum",
                icon = Icons.Default.Radio,
                badgeCount = rfAnomalyCount,
                iconTint = Color(0xFF9C27B0),
                onClick = onNavigateToRfDetection
            )
        }

        // Second row: Ultrasonic & Satellite
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DetectionModuleCard(
                modifier = Modifier.weight(1f),
                title = "Ultrasonic",
                description = "Audio tracking beacons",
                icon = Icons.Default.GraphicEq,
                badgeCount = ultrasonicBeaconCount,
                iconTint = Color(0xFFFF9800),
                onClick = onNavigateToUltrasonicDetection
            )
            DetectionModuleCard(
                modifier = Modifier.weight(1f),
                title = "Satellite",
                description = "NTN & Direct-to-Cell",
                icon = Icons.Default.SatelliteAlt,
                badgeCount = satelliteAnomalyCount,
                iconTint = Color(0xFF4CAF50),
                onClick = onNavigateToSatelliteDetection
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetectionModuleCard(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeCount: Int,
    iconTint: Color,
    onClick: () -> Unit
) {
    val hasAnomalies = badgeCount > 0
    // Animate scale for emphasis when there are anomalies
    val scale by animateFloatAsState(
        targetValue = if (hasAnomalies) 1.02f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "module_scale"
    )

    Card(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (hasAnomalies) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp)
                    )
                } else Modifier
            ),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (hasAnomalies) {
                iconTint.copy(alpha = 0.2f)
            } else {
                iconTint.copy(alpha = 0.1f)
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (hasAnomalies) 4.dp else 0.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(if (hasAnomalies) 32.dp else 28.dp)
                )
                if (hasAnomalies) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.error
                    ) {
                        Text(
                            text = badgeCount.toString(),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (hasAnomalies) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = if (hasAnomalies) "$badgeCount anomal${if (badgeCount > 1) "ies" else "y"} detected" else description,
                style = MaterialTheme.typography.labelSmall,
                color = if (hasAnomalies) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (hasAnomalies) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Permission recovery button that opens app settings
 * Used when a permission is denied and needs to be granted manually
 */
@Composable
fun PermissionRecoveryButton(
    modifier: Modifier = Modifier,
    text: String = "Grant Permission"
) {
    val context = LocalContext.current

    OutlinedButton(
        onClick = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        },
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text)
    }
}
