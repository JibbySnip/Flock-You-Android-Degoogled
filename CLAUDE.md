# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Flock-You is a privacy-first surveillance detection Android app ("Watch the Watchers"). All processing is 100% on-device with zero cloud connectivity. The app detects nearby surveillance equipment, trackers, IMSI catchers, and monitoring devices across 7 detection protocols and 75+ device signatures.

## Build Commands

```bash
# Build (default: sideload debug)
./gradlew assembleSideloadDebug          # or: make build
./gradlew assembleSideloadRelease        # or: make build-release
./gradlew assembleSystemRelease          # or: make system-release
./gradlew assembleOemRelease             # or: make oem-release

# Install to device
make sideload                            # Build + install + launch debug APK

# Tests
./gradlew test                           # All unit tests (or: make test)
./gradlew testSideloadDebugUnitTest      # Sideload debug unit tests only
./gradlew connectedAndroidTest           # Instrumented tests (requires device)

# Lint
./gradlew lint                           # or: make lint

# Utilities
./gradlew updateOuiDatabase              # Download latest IEEE OUI database
./gradlew prepareFlipperFap              # Build Flipper Zero FAP (requires ufbt)
```

## Architecture

**MVVM + Clean Architecture** with Hilt DI, Jetpack Compose UI, and Room (SQLCipher-encrypted) persistence.

**File size invariant**: No file should exceed ~1,500 lines. Most should be under 800. Large files have been split along responsibility boundaries:

| Original File | Split Into | Boundary |
|----------------|-----------|----------|
| `Components.kt` | `ThemeExtensions.kt`, `StatusComponents.kt`, `DetectionCard.kt`, `CommonComponents.kt`, `SwipeableDetectionCard.kt` | Visual concern (theme helpers, status widgets, card types, generic composables) |
| `PromptTemplates.kt` | `PromptTemplates.kt`, `EnrichedPromptTemplates.kt`, `DescriptionGenerator.kt`, `BatchPromptTemplates.kt` | Prompt audience (single detection, enriched heuristic, rule-based fallback, multi-detection batch) |
| `DetectionPatterns.kt` | `DetectionPatterns.kt`, `SsidPatterns.kt`, `BleNamePatterns.kt`, `DeviceTypeInfoEntries.kt` | Pattern type (core + OUI + UUIDs, SSID regex list, BLE name regex list, device info entries) |
| `MainScreen.kt` | `MainScreen.kt`, `MainScreenFlipperTab.kt`, `DetectionDetailSheet.kt`, `FilterBottomSheet.kt`, `MainScreenDialogs.kt` | UI surface (tab scaffold, Flipper tab, detail sheet, filter sheet, dialogs) |
| `MainViewModel.kt` | `MainViewModel.kt`, `MainViewModelFilters.kt`, `MainViewModelSettings.kt`, `MainViewModelFlipper.kt`, `MainViewModelDebug.kt` | Domain (core VM, filter/sort logic, settings mutations, Flipper connection, AI/debug actions) |
| `ScanningService.kt` | `ScanningService.kt`, `ScanningServiceBroadcaster.kt`, `SubsystemManager.kt`, `DetectionProcessor.kt` + `ScanningServiceState.kt`, `ScanningServiceModels.kt` | Lifecycle vs IPC broadcast vs subsystem start/stop vs detection handling vs shared state vs data classes |
| `DetectionAnalyzer.kt` | `DetectionAnalyzer.kt`, `PatternAnalyzer.kt`, `AnalysisHelpers.kt` | Analysis scope (progressive analysis orchestrator, cross-detection pattern recognition, contextual insight gathering) |

Split files use extension functions on the parent class (e.g., `MainViewModelFilters.kt` defines extension functions on `MainViewModel`) to maintain access to internal state while keeping files focused.

### Detection Pipeline

```
Sensors → Detection Handlers → Detection Registry → Threat Scoring → LLM Analysis (optional) → Alert
   ↓              ↓                    ↓                  ↓                   ↓                  ↓
BLE/WiFi/    analyze()          Deduplication      ThreatScoring.kt    DetectionAnalyzer    Notification
Cell/GNSS/   matchesPattern()   Throttling (5s)    likelihood×impact   Gemini Nano /        + Broadcast
Audio/RF/    generatePrompt()   Composite keys     ×confidence         MediaPipe /          Intent
Satellite                                          (0-100 score)       Rule-based fallback
```

### Background Service

`ScanningService` runs as a foreground service in the `:scanning` process. Auto-restarts via `BootReceiver` and `ServiceRestartJobService`. Communicates with the UI via `ScanningServiceConnection` IPC.

The service is split across 6 files: `ScanningService.kt` (lifecycle, scan loop), `ScanningServiceBroadcaster.kt` (IPC broadcast helpers), `SubsystemManager.kt` (subsystem start/stop), `DetectionProcessor.kt` (detection handling, alerts, broadcasts), `ScanningServiceState.kt` (shared state flows), and `ScanningServiceModels.kt` (data classes). `ScanningServiceIpc.kt` provides the client-side IPC connection.

### Navigation

Compose Navigation in `MainActivity.kt` (`AppNavigation()` composable). Routes include: `main`, `map`, `settings`, `detection_settings`, `security`, `privacy`, `nuke_settings`, `ai_settings`, `flipper_settings`, `active_probes`.

### Key Dependencies

- Kotlin 2.1.0, Compose BOM 2023.10.01, Hilt 2.54, Room 2.6.1 (KSP)
- SQLCipher 4.12.0 for encrypted DB
- MediaPipe 0.10.22 + LiteRT 1.0.1 for on-device LLM
- OSMDroid 6.1.18 for maps (no API key needed)
- USB Serial 3.7.0 for Flipper Zero integration

## File Map (files >300 lines)

All paths relative to `app/src/main/java/com/flockyou/`.

### Detection Handlers & Framework

| File | Lines | Description |
|------|------:|-------------|
| `detection/handler/BleDetectionHandler.kt` | 2,901 | Bluetooth LE detection: pattern matching, behavioral analysis, tracker following, BLE spam detection |
| `detection/handler/WifiDetectionHandler.kt` | 1,372 | WiFi detection: SSID pattern matching, evil twin, deauth, hidden camera, following network |
| `detection/handler/CellularDetectionHandler.kt` | 1,363 | Cellular detection: IMSI catcher scoring, encryption downgrade, cell switching, signal anomalies |
| `detection/handler/SatelliteDetectionHandler.kt` | 1,336 | Satellite/NTN detection: forced handoff, timing anomaly, orbit analysis |
| `detection/handler/DetectionHandler.kt` | 1,158 | Base detection handler interface and shared types (DetectionContext, DetectionResult, DeviceTypeProfile) |
| `detection/handler/GnssDetectionHandler.kt` | 1,217 | GNSS spoofing/jamming detection: C/N0 analysis, geometry anomaly, constellation checks |
| `detection/handler/RfDetectionHandler.kt` | 949 | RF spectrum detection: jammers, drones, hidden transmitters (requires Flipper Zero) |
| `detection/handler/UltrasonicDetectionHandler.kt` | 821 | Ultrasonic beacon detection: ad tracking, retail beacons, cross-device linking |
| `detection/ThreatScoring.kt` | 658 | Threat score calculation: impact factors, confidence adjustments, severity levels |
| `detection/config/DetectionConfig.kt` | 997 | Unified detection configuration: per-protocol configs, device overrides, import/export |
| `detection/config/DetectionConstants.kt` | ~450 | Centralized threshold and timing constants for all 7 detection protocols |
| `detection/DetectionDeduplicator.kt` | ~550 | Multi-level deduplication: MAC match, UUID similarity, composite key, location proximity, SSID fuzzy |
| `detection/profile/DeviceTypeProfile.kt` | 1,790 | Centralized device type profiles for rule-based analysis and LLM context |
| `detection/framework/TrackerDatabase.kt` | 1,026 | Tracker sighting history, following detection, location correlation |
| `detection/framework/TrackerSignature.kt` | 709 | Tracker fingerprinting: stable IDs from randomized MACs, advertising payload hashing |
| `detection/framework/BleAddressTracker.kt` | 699 | BLE address rotation tracking, locally administered bit detection |

### Detection Patterns & Data Model

| File | Lines | Description |
|------|------:|-------------|
| `data/model/DetectionPatterns.kt` | 2,822 | Core pattern database: MAC OUI prefixes, service UUIDs, tracker specs, regex cache, DeviceTypeInfo lookup |
| `data/model/SsidPatterns.kt` | ~700 | WiFi SSID regex patterns: Flock Safety, police tech, forensics, smart home, traffic, pineapple |
| `data/model/BleNamePatterns.kt` | ~700 | BLE name regex patterns: body cameras, vehicle systems, trackers, Flipper, Hak5, SDR, RFID tools |
| `data/model/DeviceTypeInfoEntries.kt` | 1,240 | Per-device-type descriptions, capabilities, privacy concerns, recommendations, source URLs |
| `data/DetectionSettings.kt` | 935 | User-facing detection settings: per-protocol pattern enums, threshold configs, DataStore persistence |
| `data/repository/Database.kt` | 798 | Room database with SQLCipher encryption, DAOs, migrations |

### AI & Analysis

| File | Lines | Description |
|------|------:|-------------|
| `ai/DetectionAnalyzer.kt` | 1,870 | Progressive analysis orchestrator: cache, rule-based, LLM fallback chain, enriched data integration |
| `ai/PatternAnalyzer.kt` | 1,365 | Cross-detection pattern recognition: coordinated surveillance, following, timing, geographic clustering |
| `ai/AnalysisHelpers.kt` | 722 | Contextual insight gathering, threat summary generation, detection history analysis |
| `ai/PromptTemplates.kt` | 992 | Single-detection LLM prompts, data classes (ContextualInsights, FalsePositiveAnalysisResult, etc.) |
| `ai/EnrichedPromptTemplates.kt` | 755 | Enriched prompts for detector-specific data (cellular analysis, GNSS analysis, beacon analysis) |
| `ai/DescriptionGenerator.kt` | 802 | Rule-based (non-LLM) enterprise-grade detection descriptions: WHAT, WHY, CONFIDENCE, ACTION, CONTEXT |
| `ai/BatchPromptTemplates.kt` | ~500 | Multi-detection prompts: pattern recognition, daily summaries, batch analysis, cross-domain correlation |
| `ai/LlmOutputParser.kt` | 897 | LLM response parsing: structured extraction, JSON fallback, confidence calibration |
| `ai/GeminiNanoClient.kt` | 967 | Gemini Nano integration: ML Kit AICore, session management, response streaming |
| `ai/LlmEngineManager.kt` | 747 | Engine selection and fallback: AUTO mode, capability detection, engine lifecycle |
| `ai/MediaPipeLlmClient.kt` | 663 | MediaPipe Gemma integration: .task/.bin model loading, CPU/GPU inference |
| `ai/FalsePositiveAnalyzer.kt` | 946 | Multi-layer FP detection: location patterns, time-of-day, LLM-enhanced compact prompts |
| `ai/correlation/CrossDomainAnalyzer.kt` | 965 | Cross-protocol correlation: IMSI combo, following, coordinated surveillance, tracker network |

### Service & Scanning

| File | Lines | Description |
|------|------:|-------------|
| `service/ScanningService.kt` | 2,345 | Foreground service lifecycle, core scan loop, scanner coordination |
| `service/ScanningServiceIpc.kt` | 1,179 | IPC constants, client-side ServiceConnection, cross-process state sync |
| `service/ScanningServiceBroadcaster.kt` | 667 | IPC broadcast helpers: message serialization, client notification, dead client cleanup |
| `service/SubsystemManager.kt` | 876 | Subsystem start/stop for all 7 detection protocols (cellular, satellite, GNSS, RF, ultrasonic, etc.) |
| `service/DetectionProcessor.kt` | 702 | Detection result handling: database storage, privacy filtering, alerts, automation broadcasts |
| `service/ScanningServiceState.kt` | ~200 | Shared state flows for cross-process communication (process-local, synced via IPC) |
| `service/ScanningServiceModels.kt` | ~150 | Data classes: ScanConfig, SeenDevice, ScanStatus (extracted for testability) |
| `service/CellularMonitor.kt` | 2,794 | Cell tower monitoring: IMSI catcher scoring, encryption tracking, anomaly analysis |
| `service/UltrasonicDetector.kt` | 2,491 | Audio capture and FFT analysis for ultrasonic beacon detection |
| `service/RogueWifiMonitor.kt` | 2,115 | Rogue AP detection: evil twin, deauth, following network, surveillance van analysis |
| `service/RfSignalAnalyzer.kt` | 1,610 | RF spectrum analysis for Flipper Zero data: jammer, drone, hidden transmitter detection |

### Monitoring

| File | Lines | Description |
|------|------:|-------------|
| `monitoring/SatelliteMonitor.kt` | 3,151 | NTN/satellite connection monitoring: Android 15+ satellite API, connection state tracking |
| `monitoring/GnssSatelliteMonitor.kt` | 3,063 | GNSS satellite monitoring: spoofing detection, C/N0 analysis, multi-constellation tracking |
| `monitoring/SatelliteDetectionHeuristics.kt` | 2,043 | Satellite-specific heuristics: orbital analysis, timing validation, provider identification |
| `monitoring/ScannerThreadingMonitor.kt` | 761 | Scanner thread health monitoring: deadlock detection, duty cycle tracking |

### UI Screens

| File | Lines | Description |
|------|------:|-------------|
| `ui/screens/MainScreen.kt` | 1,811 | Main tab scaffold: history list, map tab, scanning controls, status bar |
| `ui/screens/MainScreenFlipperTab.kt` | 2,668 | Flipper Zero tab: RF scan display, sub-GHz analysis, signal visualization |
| `ui/screens/DetectionDetailSheet.kt` | 1,266 | Detection detail bottom sheet: full device info, threat analysis, map, actions |
| `ui/screens/FilterBottomSheet.kt` | ~500 | Filter bottom sheet: threat level, device type, protocol, time range, signal strength filters |
| `ui/screens/MainScreenDialogs.kt` | ~400 | Dialogs: clear history confirmation, satellite banner, test mode indicator |
| `ui/screens/AllDetectionsScreen.kt` | 2,484 | All detections list with advanced filtering and sorting |
| `ui/screens/NearbyDevicesScreen.kt` | 2,381 | Nearby devices radar view with real-time updates |
| `ui/screens/SatelliteDetectionScreen.kt` | 3,139 | Satellite detection detail view with orbital visualization |
| `ui/screens/SatelliteScreen.kt` | 1,322 | Satellite status overview screen |
| `ui/screens/DetectionSettingsScreen.kt` | 2,070 | Detection settings: 7-category tabs, pattern toggles, threshold sliders |
| `ui/screens/AiSettingsScreen.kt` | 1,898 | AI settings: engine selection, model download, analysis configuration |
| `ui/screens/SettingsScreen.kt` | 1,872 | Main settings screen: app preferences, privilege info, about |
| `ui/screens/ServiceHealthStatusScreen.kt` | 1,931 | Service health dashboard: scanner status, thread health, memory, battery |
| `ui/screens/MapScreen.kt` | 1,342 | Full-screen map view with clustered detection markers |
| `ui/screens/FlipperSettingsScreen.kt` | 1,141 | Flipper Zero settings: connection, FAP management, RF bands |
| `ui/screens/NukeSettingsScreen.kt` | 1,079 | Nuke settings: duress PIN, dead man's switch, geofence, SIM removal |
| `ui/screens/UltrasonicDetectionScreen.kt` | 1,077 | Ultrasonic detection detail with spectrogram |
| `ui/screens/WifiSecurityScreen.kt` | 1,074 | WiFi security analysis detail view |
| `ui/screens/PermissionSetupWizard.kt` | 1,067 | Permission onboarding wizard |
| `ui/screens/RfDetectionScreen.kt` | 1,174 | RF detection detail with spectrum visualization |
| `ui/screens/SecuritySettingsScreen.kt` | 825 | Security settings: app lock, biometrics, encryption |
| `ui/screens/ActiveProbesScreen.kt` | 823 | Active probe configuration and status |
| `ui/screens/FlipperSetupWizard.kt` | 796 | Flipper Zero setup wizard |
| `ui/screens/FlipperOnboardingComponents.kt` | 783 | Flipper onboarding UI components |
| `ui/screens/CellularTimelineScreen.kt` | 761 | Cellular event timeline visualization |
| `ui/screens/PrivacySettingsScreen.kt` | 937 | Privacy settings: data retention, ephemeral mode, export |
| `ui/screens/RuleSettingsScreen.kt` | 724 | Automation rule settings |
| `ui/screens/NotificationSettingsScreen.kt` | 722 | Notification settings per threat level and protocol |
| `ui/screens/DetectionPatternsScreen.kt` | ~500 | Detection pattern browser and search |

### UI ViewModels

| File | Lines | Description |
|------|------:|-------------|
| `ui/screens/MainViewModel.kt` | 933 | Core ViewModel: state management, service connection, detection flow observation |
| `ui/screens/MainViewModelFilters.kt` | ~400 | Filter/sort extension functions on MainViewModel |
| `ui/screens/MainViewModelSettings.kt` | ~350 | Settings mutation extension functions on MainViewModel |
| `ui/screens/MainViewModelFlipper.kt` | ~400 | Flipper Zero connection extension functions on MainViewModel |
| `ui/screens/MainViewModelDebug.kt` | 731 | AI analysis and debug extension functions on MainViewModel |

### UI Components

| File | Lines | Description |
|------|------:|-------------|
| `ui/components/DetectionCard.kt` | 1,133 | Expandable detection card: threat badge, device info, signal, matched patterns, AI summary |
| `ui/components/SwipeableDetectionCard.kt` | ~500 | Swipeable card wrapper: swipe-to-review, swipe-to-mark-FP, undo snackbar |
| `ui/components/StatusComponents.kt` | ~400 | Scanning status indicators: radar animation, scan progress, connection status |
| `ui/components/CommonComponents.kt` | ~350 | Shared composables: empty state, settings rows, section headers, info chips |
| `ui/components/ThemeExtensions.kt` | ~300 | Theme extension functions: ThreatLevel.toColor(), DeviceType.toIcon(), protocol colors |

### Other

| File | Lines | Description |
|------|------:|-------------|
| `MainActivity.kt` | 946 | App entry point, Compose navigation (AppNavigation), permission handling |
| `scanner/flipper/FlipperScannerManager.kt` | 1,248 | Flipper Zero scanner: BLE/USB connection, sub-GHz scan orchestration |
| `scanner/flipper/FlipperBluetoothClient.kt` | 794 | Flipper BLE GATT client: service discovery, characteristic read/write |
| `scanner/flipper/FlipperProtocol.kt` | 772 | Flipper serial protocol: command framing, response parsing, Protobuf |
| `scanner/system/SystemCellularScanner.kt` | 669 | System-privileged cellular scanner: real IMEI/IMSI, continuous monitoring |
| `security/AppLockManager.kt` | 753 | App lock: PIN, biometrics, duress PIN detection |
| `worker/BackgroundAnalysisWorker.kt` | 698 | Hourly background LLM analysis: batch FP scoring, enrichment prioritization |
| `data/AiSettings.kt` | 654 | AI settings DataStore: engine selection, model paths, analysis preferences |

## Sideload vs System vs OEM Variants

Three product flavors with different capability tiers, detected at runtime by `PrivilegeModeDetector` (`privilege/PrivilegeMode.kt`).

### Privilege Capabilities Matrix

| Capability | Sideload | System | OEM |
|------------|----------|--------|-----|
| WiFi Throttle Bypass | No (4 scans/2 min) | Yes (`setScanThrottleEnabled` hidden API) | Yes |
| Real MAC Addresses | No (randomized) | Yes (if `PEERS_MAC_ADDRESS` granted) | Yes |
| Continuous BLE Scan | No (25s scan, 5s cooldown) | Yes (60s scan, 1s cooldown) | Yes |
| IMEI/IMSI Access | No | No | Yes (`READ_PRIVILEGED_PHONE_STATE`) |
| Persistent Process | No | No | Yes |
| WiFi Scan Interval | 30s | 10s | 10s |
| Permissions | Runtime requests | Pre-granted via whitelist | Platform-signed |

### Scanner Factory (`scanner/ScannerFactory.kt`)

Creates privilege-appropriate scanner implementations:

```
ScannerFactory.createWifiScanner()       → StandardWifiScanner or SystemWifiScanner
ScannerFactory.createBluetoothScanner()  → StandardBluetoothScanner or SystemBluetoothScanner
ScannerFactory.createCellularScanner()   → StandardCellularScanner or SystemCellularScanner
```

Test mode: `ScannerFactory.setTestMode(true, mockWifi, mockBle, mockCellular)` swaps in mock scanners.

### OEM Feature Flags (`config/OemFeatureFlags.kt`)

Compile-time flags set in `gradle.properties`, all default to `true`. Check with `OemFeatureFlags.FLIPPER_ZERO_ENABLED`, etc. Override on CLI: `./gradlew assembleOemRelease -POEM_FEATURE_FLIPPER_ENABLED=false`

| Flag | Controls |
|------|----------|
| `OEM_FEATURE_FLIPPER_ENABLED` | Flipper Zero RF scanning integration |
| `OEM_FEATURE_ULTRASONIC_ENABLED` | Ultrasonic/audio beacon detection |
| `OEM_FEATURE_ANDROID_AUTO_ENABLED` | Android Auto car UI |
| `OEM_FEATURE_NUKE_ENABLED` | Emergency data wipe (duress PIN, dead man's switch) |
| `OEM_FEATURE_AI_ENABLED` | On-device LLM analysis |
| `OEM_FEATURE_TOR_ENABLED` | Tor network routing |
| `OEM_FEATURE_MAP_ENABLED` | Map display and geolocation |

### OEM Build Configuration

OEM package name configurable: `OEM_PACKAGE_NAME` in `gradle.properties`. Network URLs (GitHub, AI model downloads, map tiles, OUI database) are also OEM-overridable via `gradle.properties`.

OEM system integration files generated via: `./gradlew generateOemSystemFiles` (outputs `privapp-permissions.xml`, `default-permissions.xml`, setup script to `build/generated/oem/`).

## Detection Framework Reference

### Core Data Model (`data/model/Detection.kt`)

The `Detection` Room entity stores every detection with: `id` (UUID), `protocol`, `detectionMethod`, `deviceType`, `threatLevel`, `threatScore` (0-100), `rssi`, `latitude`/`longitude`, `macAddress`, `ssid`, `serviceUuids`, `matchedPatterns`, `rawData`, `seenCount`, `fpScore` (0.0-1.0, null if unanalyzed), `llmAnalyzed`, `userNote`, `confirmedThreat`.

### Seven Detection Protocols

Each protocol has a `DetectionHandler<T : DetectionContext>` with methods: `analyze(context)`, `matchesPattern(scanResult)`, `generatePrompt(detection)`, `getDeviceProfile(deviceType)`, `getThresholds()`.

**1. BLUETOOTH_LE** (`BleDetectionHandler` - 2,934 lines)
- **Context**: `DetectionContext.BluetoothLe` - deviceName, macAddress, serviceUuids, manufacturerData, advertisementData, isConnectable, txPowerLevel
- **Pattern matching**: BLE name regex, service UUID lookup, manufacturer ID (Apple 0x004C, Samsung 0x0075, Tile 0x00C7, Google 0x00E0, Nordic 0x0059), Raven gunshot detector UUIDs
- **Behavioral detection**: Advertising rate spikes (Axon Signal: 20+ pps), BLE spam (Flipper: 30+ Apple devices in 20s), tracker following (sighting history across locations), RSSI history for proximity
- **Thresholds**: RSSI min -90, proximity alert -50, immediate proximity -40, rate limit 30s
- **Detects**: AirTag, Tile, SmartTag, Flipper Zero (all firmware variants), body cameras (Axon, Motorola), Raven gunshot detectors, police radios, Hak5 tools, Proxmark, HackRF, smart home IoT

**2. WIFI** (`WifiDetectionHandler` - 1,244 lines)
- **Context**: `DetectionContext.WiFi` - ssid, bssid, channel, frequency, capabilities, isHidden, seenCount
- **Pattern matching**: SSID regex patterns, MAC OUI prefix lookup
- **Behavioral detection**: Evil twin (same SSID, different BSSID), deauth attack rate, hidden camera patterns, following network (same AP at multiple user locations), surveillance van signatures
- **Detects**: Flock Safety ALPR, Cellebrite UFED, GrayKey, police tech WiFi hotspots, WiFi Pineapple, rogue APs, hidden cameras, retail analytics

**3. CELLULAR** (`CellularDetectionHandler` - 1,254 lines)
- **Context**: `DetectionContext.Cellular` - mcc, mnc, lac, cid, psc, arfcn, networkType, isRoaming, previousCellInfo
- **Detection methods**: Encryption downgrade (5G/4G→2G), suspicious MCC-MNC (001-01, 999-99), rapid cell switching, signal spike (>25 dBm change), LAC/TAC anomaly, unknown cell tower
- **State tracking**: Cell tower trust database (trusted after 5 sightings), signal strength baseline, movement detection (stationary vs moving)
- **Thresholds**: Signal spike 25 dBm, rapid switch 3/min stationary or 8/min moving

**4. GNSS** (`GnssDetectionHandler`)
- **Context**: `DetectionContext.Gnss` - satellites (list of SatelliteInfo), hdop, pdop, fixType, cn0DbHz, agcLevel
- **Detection methods**: Spoofing (uniform C/N0 variance < 0.15), jamming (signal loss), signal anomaly, geometry anomaly (impossible positions), clock anomaly (timing discontinuity), multipath (reflection interference), constellation anomaly
- **Confirmation methods**: Cell tower triangulation, WiFi positioning cross-check, accelerometer consistency, altitude sanity, NTP time comparison, Galileo OSNMA authentication
- **Note**: Disabled by default (high false positive rate in urban environments). Enabled in PARANOID preset.

**5. AUDIO** (`UltrasonicDetectionHandler`)
- **Context**: `DetectionContext.Audio` - frequencyHz, amplitudeDb, duration, spectralPeaks, modulationType, isUltrasonic
- **Detection range**: 18,000-22,000 Hz via AudioRecord + FFT (4096 window, 44100 sample rate)
- **Detection methods**: Ad tracking beacons (SilverPush, Alphonso), TV attribution, retail beacons (Shopkick), cross-device linking, continuous monitoring
- **Thresholds**: Min amplitude -35.0 dB

**6. RF** (`RfDetectionHandler`)
- **Context**: `DetectionContext.RfSpectrum` - frequencyHz, bandwidthHz, powerDbm, modulationType, signalType, isAnomaly
- **Detection methods**: RF jammer, drone, surveillance area, spectrum anomaly, hidden transmitter
- **Sub-GHz frequencies**: 315, 433, 868, 915 MHz
- **Note**: Disabled by default (requires Flipper Zero or system-level RF access). Enabled in PARANOID preset.

**7. SATELLITE** (`SatelliteDetectionHandler`)
- **Context**: `DetectionContext.Satellite` - satelliteId, orbitType, elevation, azimuth, expectedTiming, actualTiming, handoffReason
- **Detection methods**: Unexpected NTN connection, forced handoff, suspicious NTN parameters, timing anomaly, downgrade to satellite
- **Analysis**: Orbital type detection (LEO/MEO/GEO), round-trip time validation, provider identification (Starlink etc.), terrestrial coverage assessment

### Detection Patterns Database (4 files, originally `DetectionPatterns.kt`)

The pattern database has been split across four files for maintainability:

- **`data/model/DetectionPatterns.kt`** - Core pattern infrastructure: MAC OUI prefixes, BLE service UUIDs, tracker specifications, pre-compiled regex cache, `DeviceTypeInfo` lookup dispatch. This file aggregates patterns from the other three files.
- **`data/model/SsidPatterns.kt`** - WiFi SSID regex patterns: Flock Safety, police tech (Axon, Motorola, L3Harris), forensics (Cellebrite, GrayKey, MSAB XRY, Oxygen), smart home (Ring, Nest, Wyze, Arlo, Eufy, Blink), traffic systems, WiFi Pineapple, retail analytics, hidden cameras.
- **`data/model/BleNamePatterns.kt`** - BLE device name regex patterns: Flock Safety/Raven, police body cameras (Axon, Motorola), police vehicle systems (Whelen CenCom, Federal Signal, SoundOff), forensics tools, all tracker brands, Flipper Zero (all firmware: unleashed, roguemaster, xtreme, momentum), Hak5 tools, SDR tools (HackRF, PortaPack, RTL-SDR), RFID tools (Proxmark, ChameleonMini).
- **`data/model/DeviceTypeInfoEntries.kt`** - Per-device-type descriptions, capabilities, privacy concerns, recommendations, and source URLs. Used by LLM prompts and rule-based analysis.

Additional pattern types in `DetectionPatterns.kt`:
- **Raven service UUIDs**: 7 BLE service UUIDs for ShotSpotter/Raven gunshot detectors (based on GainSec research): Device Info (0x180a), GPS (0x3100), Power (0x3200), Network (0x3300), Upload Stats (0x3400), Diagnostics (0x3500).
- **Tracker specifications**: Detailed profiles for AirTag (Apple, HIGH stalking risk, auto-alerts 8-24h), Tile (Life360, CRITICAL stalking risk - NO auto-alerts), SmartTag (Samsung, MEDIUM risk), generic BLE trackers (Chipolo, Eufy, Pebblebee).

### Threat Scoring (`detection/ThreatScoring.kt` - 658 lines)

**Formula**: `threat_score = base_likelihood × impact_factor × confidence`

**Impact factors by device type** (0.5-2.0):
- 2.0: STINGRAY_IMSI, CELLEBRITE_FORENSICS, GRAYKEY_DEVICE, MAN_IN_MIDDLE
- 1.8: GNSS_SPOOFER, GNSS_JAMMER, RF_JAMMER, WIFI_PINEAPPLE, ROGUE_AP
- 1.5: All trackers (AIRTAG, TILE, SMARTTAG), SURVEILLANCE_VAN, DRONE
- 1.2-1.3: FLOCK_SAFETY_CAMERA, HIDDEN_CAMERA, LICENSE_PLATE_READER, FACIAL_RECOGNITION
- 1.0: BODY_CAMERA, police equipment, CCTV, ULTRASONIC_BEACON
- 0.8: Consumer IoT (Ring, Nest, Wyze, etc.)
- 0.5-0.6: TRAFFIC_SENSOR, TOLL_READER, RF_INTERFERENCE

**Confidence adjustments**: +0.3 cross-protocol correlation, +0.2 persistence/multiple indicators, +0.1 high RSSI, -0.5 known false positive, -0.3 single weak indicator, -0.2 common consumer device, -0.3 multipath likely.

**Severity levels**: CRITICAL (90-100), HIGH (70-89), MEDIUM (50-69), LOW (30-49), INFO (0-29).

### Detection Settings & Presets

**DetectionSettings** (`data/DetectionSettings.kt`): User-facing settings with per-protocol pattern enums (CellularPattern, SatellitePattern, BlePattern, WifiPattern, GnssPattern, RfPattern, UltrasonicPattern), per-protocol threshold configs, and global protocol enable/disable flags. Stored via DataStore.

**DetectionConfig** (`detection/config/DetectionConfig.kt`): Newer unified configuration system with `GlobalDetectionSettings`, per-protocol config classes (BleProtocolConfig, WifiProtocolConfig, CellularProtocolConfig, etc.), device type overrides, and import/export.

**ProtectionPreset** (`data/ProtectionPreset.kt`): Four presets with different pattern selections and threshold multipliers:

| Preset | Threshold Multiplier | GNSS Enabled | RF Enabled | Focus |
|--------|---------------------|--------------|------------|-------|
| ESSENTIAL | 1.5x (conservative) | No | No | IMSI catchers, StingRay, body cams, Flock cameras only |
| BALANCED | 1.0x (default) | No | No | All default-enabled patterns |
| PARANOID | 0.7x (sensitive) | Yes | Yes | ALL patterns enabled, maximum sensitivity |
| CUSTOM | User-defined | User-defined | User-defined | User-modified settings |

### Detection Display & Settings UI

**Settings flow**: User toggles pattern in `DetectionSettingsScreen` → `DetectionSettingsViewModel` → `DetectionSettingsRepository` (DataStore) → `MainViewModel` observes changes → `ScanningService` receives via IPC.

**DetectionSettingsScreen**: Tab-based UI with 7 categories (Cellular, Satellite, BLE, WiFi, GNSS, RF, Ultrasonic). Each category has a `DetectionCategoryCard` showing: master toggle, "X of Y patterns enabled" badge, expandable individual pattern toggles with descriptions, collapsible threshold sliders (RSSI, timing, signal), and reset-to-defaults button.

**Main detection display** (split across `MainScreen.kt`, `MainScreenFlipperTab.kt`, `DetectionDetailSheet.kt`, `FilterBottomSheet.kt`, `MainScreenDialogs.kt`): History tab with `SwipeableDetectionCard` list (in `ui/components/SwipeableDetectionCard.kt`; swipe for Mark Reviewed / Mark False Positive), filter chips in `FilterBottomSheet.kt` (threat level, device type, protocol, time range, signal strength, FP hiding threshold), pull-to-refresh. Map tab shows clustered markers color-coded by threat level via OSMDroid. Detection details in `DetectionDetailSheet.kt`. Flipper tab in `MainScreenFlipperTab.kt`.

**Broadcast intents** for automation (Tasker/Automate integration):
- `com.flockyou.DETECTION` - BLE/WiFi device
- `com.flockyou.CELLULAR_ANOMALY` - IMSI catcher/cell anomaly
- `com.flockyou.SATELLITE_ANOMALY` - NTN/satellite threat
- `com.flockyou.WIFI_ANOMALY` - Evil twin/deauth
- `com.flockyou.RF_ANOMALY` - Jammer/drone
- `com.flockyou.ULTRASONIC` - Ultrasonic beacon

### LLM Assessment System (`ai/`)

**Engine fallback chain** (`LlmEngineManager`): User selection → AUTO mode → Gemini Nano → MediaPipe → Rule-based.

| Engine | Requirements | Format | Speed |
|--------|-------------|--------|-------|
| Gemini Nano | Pixel 8+, Android 14+, AICore | ML Kit managed | Best quality, NPU |
| MediaPipe (Gemma 1B/2B) | Most devices | `.task` or `.bin` (NOT raw GGUF) | Good, CPU/GPU |
| Rule-Based | Always available | DeviceTypeProfileRegistry | <10ms, no model |

**Analysis flow** (`DetectionAnalyzer.analyzeProgressively()` - split across `DetectionAnalyzer.kt`, `PatternAnalyzer.kt`, `AnalysisHelpers.kt`):
1. Check cache → instant return if hit
2. Emit rule-based result immediately (<10ms) via `DescriptionGenerator`
3. Launch LLM analysis in background
4. Cross-detection patterns analyzed by `PatternAnalyzer` (coordinated surveillance, following, clustering)
5. Contextual insights gathered by `AnalysisHelpers`
6. Emit LLM result when complete

**Enriched heuristics**: Handlers provide structured data to LLM prompts via `EnrichedDetectorData` (e.g., IMSI catcher score, encryption downgrade chain, C/N0 uniformity analysis, tracker sighting history). Cached with detection ID in `EnrichedDataCache`.

**False positive analysis** (`FalsePositiveAnalyzer`): Multi-layer FP detection using location patterns (home/work/safe areas), time-of-day context, and LLM-enhanced compact prompts. Batch-processed via `BackgroundAnalysisWorker` (hourly, 50 detections per batch).

**Cross-domain correlation** (`CrossDomainAnalyzer`):
- IMSI Catcher Combo (CRITICAL): Cell anomaly + GNSS spoofing within 5 min → 2.5x multiplier
- Following Pattern (HIGH): Same device at 3+ locations → 1.5x
- Coordinated Surveillance (CRITICAL/HIGH): Multiple protocols within 100m and 10 min → 2.0x
- Tracker Network (HIGH): Multiple trackers from same manufacturer → 1.8x
- Spatial Clustering (HIGH): 3+ devices within 200m → 1.3x

**Prompt system** (4 files): `PromptTemplates` (single-detection full prompts + data classes), `EnrichedPromptTemplates` (detector-specific enriched prompts), `BatchPromptTemplates` (multi-detection pattern/summary prompts), `CompactPromptTemplates` (50-70% smaller for smaller models). `DescriptionGenerator` provides rule-based fallback descriptions. Input sanitization strips prompt injection markers like `<start_of_turn>`, `[INST]`.

### Debug & Test Mode (`testmode/`)

**TestModeOrchestrator**: Manages test scenarios, coordinates mock scanners, injects test data into the real detection pipeline.

**Test scenarios**: Tracker Following, Cell Site Simulator (StingRay), GNSS Spoofing, Surveillance Camera, Ultrasonic Beacon, High Threat Environment, Normal Environment (for FP testing), Drone Surveillance.

**Mock scanner integration**: `ScannerFactory.setTestMode(true, mockWifi, mockBle, mockCellular)` replaces real scanners. Mock data flows through normal detection handlers. Detections marked with `[TEST_MODE]` in `matchedPatterns`. Config: `TestModeConfig(enabled, activeScenarioId, dataEmissionIntervalMs=3000)`.

### Detection Deduplication (`detection/DetectionDeduplicator.kt`)

Multi-level matching (priority order): MAC exact match → Service UUID Jaccard similarity (≥0.5) → Composite key hash (deviceType+protocol+manufacturer+MAC OUI) → Location+type proximity (10m, Haversine) → SSID fuzzy match (Levenshtein ≥85%). Rapid detection throttle: 5-second window prevents UI flooding.

**BLE randomized MAC handling**: Generates stable identifier from service UUIDs + manufacturer data pattern + device name + advertising payload hash. Detects randomized MACs via locally administered bit check.

## Adding a New Detection

### Required Changes (in order)

1. **`data/model/Detection.kt`** - Add `DeviceType` enum value (SCREAMING_SNAKE_CASE, descriptive displayName, emoji). Add `DetectionMethod` enum value if a new detection method is needed. Update `getImpactFactorForDeviceType()`.

2. **`detection/ThreatScoring.kt`** - Add impact factor in the `impactFactors` map (same value as step 1).

3. **Detection patterns** - Add pattern(s) to the appropriate split file:
   - SSID regex → `data/model/SsidPatterns.kt`
   - BLE name regex → `data/model/BleNamePatterns.kt`
   - MAC OUI prefix → `data/model/DetectionPatterns.kt` (`MacPrefix` with `AA:BB:CC` format)
   - BLE service UUIDs → `data/model/DetectionPatterns.kt`
   - `DeviceTypeInfo` entry → `data/model/DeviceTypeInfoEntries.kt` (description, capabilities, privacy concerns, recommendations, source URLs)

4. **Handler file** (if custom logic needed beyond pattern matching) - Add detection logic in the appropriate handler. Add LLM prompt generation for the new device type via `generatePrompt()`.

5. **`ui/components/ThemeExtensions.kt`** - Add icon mapping if custom icon needed (emoji from DeviceType is used by default). For card display customization, see `ui/components/DetectionCard.kt`.

6. **Tests** - Pattern regex positive/negative test cases, impact factor existence test.

### Connecting to Settings

New patterns are automatically available in the settings UI when added to the appropriate pattern enum in `DetectionSettings.kt`:
- Add to `BlePattern`, `WifiPattern`, `CellularPattern`, `GnssPattern`, `RfPattern`, `UltrasonicPattern`, or `SatellitePattern` enum
- Set `displayName` and `defaultEnabled` on the enum value
- The `DetectionCategoryCard` UI will automatically render the toggle
- Update `ProtectionPreset.kt` to include/exclude the pattern from each preset (ESSENTIAL, BALANCED, PARANOID)
- Threshold changes: add fields to the protocol's threshold data class (e.g., `BleThresholds`, `WifiThresholds`)

### LLM Assessment Integration

Add a `DeviceTypeInfo` entry in `data/model/DeviceTypeInfoEntries.kt` with: description (what the device is), capabilities (what it can do), privacyConcerns (data collection, retention, access), recommendations (how to verify, what to do), and realWorldSources (URLs). This info is automatically included in LLM prompts via `generatePrompt()` in the handler.

For enriched heuristic data: add fields to the appropriate `EnrichedDetectorData` subclass and populate them in the handler's `analyze()` method. The `DetectionAnalyzer` will automatically include enriched data in prompts when available.

### Debug & Testing

1. Add a test scenario in `testmode/TestScenario.kt` with mock scan data for the new device
2. Use test mode to verify the detection pipeline end-to-end: `ScannerFactory.setTestMode(true, ...)` → mock data → handler analysis → detection stored → UI display
3. Write unit tests for pattern matching (positive matches AND negative matches to avoid false positives)
4. Verify threat scoring produces appropriate severity for the device type

### Feasibility Research

Before adding a detection, research and document:
- **Technical signatures**: What identifiable patterns does the device emit? (SSID, BLE name, service UUIDs, MAC OUI prefix, advertising behavior)
- **Source verification**: IEEE OUI lookup (standards-oui.ieee.org), MAC lookup (maclookup.app), Bluetooth SIG assigned numbers, FCC ID search (fccid.io), nRF Connect for BLE reverse engineering
- **Surveillance context**: DeFlock (deflock.me), EFF IMSI catcher info, ACLU StingRay tracking
- **False positive risk**: How unique are the patterns? Could they match consumer devices? Test with broad regex and refine.
- **Impact factor justification**: Devices that intercept communications (2.0) vs. record (1.2-1.3) vs. observe (0.8) vs. infrastructure (0.5-0.6)
- **Privilege requirements**: Does detection need system/OEM capabilities (real MACs, continuous scanning, IMEI/IMSI)?

### Pattern Regex Quick Reference

```regex
(?i)               # Case-insensitive
^pattern           # Starts with
[_-]?              # Optional separator
.*                 # Any characters
[0-9a-fA-F]+       # Hex characters
(foo|bar)          # Alternatives
```

Common: `(?i)^device[_-]?name.*` (prefix match), `(?i)^(variant1|variant2)[_-]?.*` (multi-variant)

## Making Changes (Decision Tree)

**Adding a new device type?**
1. `data/model/Detection.kt` - add `DeviceType` enum value
2. `detection/ThreatScoring.kt` - add impact factor
3. Pattern file(s): `SsidPatterns.kt` and/or `BleNamePatterns.kt` and/or `DetectionPatterns.kt` (OUI/UUID)
4. `data/model/DeviceTypeInfoEntries.kt` - add `DeviceTypeInfo` entry
5. Handler file if custom logic needed (e.g., `BleDetectionHandler.kt`)
6. `ui/components/ThemeExtensions.kt` - add icon if needed
7. `data/DetectionSettings.kt` - add to pattern enum, `data/ProtectionPreset.kt` - add to presets
8. Tests: pattern regex, impact factor, handler logic

**Tuning detection thresholds?**
- Global thresholds: `detection/config/DetectionConstants.kt` (centralized constants for all 7 protocols)
- Per-protocol config: `detection/config/DetectionConfig.kt`
- User-facing threshold sliders: `data/DetectionSettings.kt` (threshold data classes)
- Preset multipliers: `data/ProtectionPreset.kt`

**Adding UI components?**
- Theme helpers (colors, icons): `ui/components/ThemeExtensions.kt`
- Scanning status indicators: `ui/components/StatusComponents.kt`
- Detection card display: `ui/components/DetectionCard.kt`
- Swipe behavior: `ui/components/SwipeableDetectionCard.kt`
- Generic composables (empty state, settings rows): `ui/components/CommonComponents.kt`

**Changing main screen behavior?**
- Tab scaffold/layout: `ui/screens/MainScreen.kt`
- Flipper tab: `ui/screens/MainScreenFlipperTab.kt`
- Detection detail sheet: `ui/screens/DetectionDetailSheet.kt`
- Filter UI: `ui/screens/FilterBottomSheet.kt`
- Dialogs: `ui/screens/MainScreenDialogs.kt`

**Changing ViewModel logic?**
- Core state/lifecycle: `ui/screens/MainViewModel.kt`
- Filter/sort logic: `ui/screens/MainViewModelFilters.kt`
- Settings mutations: `ui/screens/MainViewModelSettings.kt`
- Flipper connection: `ui/screens/MainViewModelFlipper.kt`
- AI analysis/debug: `ui/screens/MainViewModelDebug.kt`

**Changing service behavior?**
- Service lifecycle/scan loop: `service/ScanningService.kt`
- IPC broadcasting: `service/ScanningServiceBroadcaster.kt`
- Subsystem start/stop: `service/SubsystemManager.kt`
- Detection processing/alerts: `service/DetectionProcessor.kt`
- Cross-process state: `service/ScanningServiceState.kt`
- IPC protocol/client: `service/ScanningServiceIpc.kt`

**Changing LLM prompts?**
- Single-detection prompts: `ai/PromptTemplates.kt`
- Enriched (detector-specific) prompts: `ai/EnrichedPromptTemplates.kt`
- Multi-detection/batch prompts: `ai/BatchPromptTemplates.kt`
- Compact prompts (smaller models): `ai/CompactPromptTemplates.kt`
- Rule-based fallback descriptions: `ai/DescriptionGenerator.kt`

**Adding a new SSID pattern?** -> `data/model/SsidPatterns.kt`
**Adding a new BLE name pattern?** -> `data/model/BleNamePatterns.kt`
**Adding a MAC OUI prefix or service UUID?** -> `data/model/DetectionPatterns.kt`
**Adding device info for LLM context?** -> `data/model/DeviceTypeInfoEntries.kt`

## Key Constraints

- **File size invariant**: No file should exceed ~1,500 lines. Most should be under 800. When a file grows beyond this, split it along responsibility boundaries using extension functions to maintain access to parent class state. See the Architecture section for split file conventions.
- **Privacy invariant**: Never add code that sends data off-device. No telemetry, no cloud APIs, no analytics.
- **Privilege awareness**: Code must handle all three privilege modes (sideload/system/OEM). Use `ScannerFactory` and `PrivilegeMode` for capability branching.
- **ProGuard rules** (`app/proguard-rules.pro`): Uses conservative `keepnames` for entire app package. Add keep rules for any new classes involved in serialization, reflection, or IPC.
- **Database migrations**: Room DB is SQLCipher-encrypted. Schema changes require proper migration handling.
- **Android scan throttling**: BLE (duty cycling on Android 8+) and WiFi (4 scans per 2 minutes on Android 9+) are throttled on sideload; system/OEM modes bypass these limits.
- **Detection accuracy**: False positives are annoying but false negatives can be dangerous. Err on the side of detection. Never reduce thresholds without documented justification and confirmation that real threats won't be masked.

## Testing

- Unit tests: `app/src/test/` - JUnit 4 + Mockito + MockK + Coroutines Test + Turbine
- Instrumented tests: `app/src/androidTest/` - Espresso + Compose UI Testing + Hilt Testing
- Test runner: Custom `HiltTestRunner` for instrumented tests
- Min SDK 26 (Android 8.0), Target SDK 34 (Android 14)

## Project Setup

- Android Studio Hedgehog (2023.1.1)+, JDK 17, Android SDK 34
- CI: GitHub Actions (`.github/workflows/android-ci.yml`), uses `SKIP_FLIPPER_BUILD=true` in CI
