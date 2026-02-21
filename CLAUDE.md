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

### Navigation

Compose Navigation in `MainActivity.kt` (`AppNavigation()` composable). Routes include: `main`, `map`, `settings`, `detection_settings`, `security`, `privacy`, `nuke_settings`, `ai_settings`, `flipper_settings`, `active_probes`.

### Key Dependencies

- Kotlin 2.1.0, Compose BOM 2023.10.01, Hilt 2.54, Room 2.6.1 (KSP)
- SQLCipher 4.12.0 for encrypted DB
- MediaPipe 0.10.22 + LiteRT 1.0.1 for on-device LLM
- OSMDroid 6.1.18 for maps (no API key needed)
- USB Serial 3.7.0 for Flipper Zero integration

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

### Detection Patterns Database (`data/model/DetectionPatterns.kt` - 5,219 lines)

Four pattern types with pre-compiled regex caches:

1. **SSID patterns** (lines 210-820): WiFi SSID regex → DeviceType mapping. Covers Flock Safety, police tech (Axon, Motorola, L3Harris), forensics (Cellebrite, GrayKey, MSAB XRY, Oxygen), smart home (Ring, Nest, Wyze, Arlo, Eufy, Blink), traffic systems, WiFi Pineapple, retail analytics, hidden cameras.

2. **BLE name patterns** (lines 823-1386): BLE device name regex. Covers Flock Safety/Raven, police body cameras (Axon, Motorola), police vehicle systems (Whelen CenCom, Federal Signal, SoundOff), forensics tools, all tracker brands, Flipper Zero (all firmware: unleashed, roguemaster, xtreme, momentum), Hak5 tools (Bash Bunny, LAN Turtle, USB Rubber Ducky, Key Croc, Shark Jack, Screen Crab), SDR tools (HackRF, PortaPack, RTL-SDR), RFID tools (Proxmark, ChameleonMini).

3. **Raven service UUIDs** (lines 1388-1460): 7 BLE service UUIDs for ShotSpotter/Raven gunshot detectors (based on GainSec research): Device Info (0x180a), GPS (0x3100), Power (0x3200), Network (0x3300), Upload Stats (0x3400), Diagnostics (0x3500).

4. **Tracker specifications** (lines 18-122): Detailed profiles for AirTag (Apple, HIGH stalking risk, auto-alerts 8-24h), Tile (Life360, CRITICAL stalking risk - NO auto-alerts), SmartTag (Samsung, MEDIUM risk), generic BLE trackers (Chipolo, Eufy, Pebblebee).

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

**Main detection display**: History tab in `MainScreen` with `SwipeableDetectionCard` list (swipe for Mark Reviewed / Mark False Positive), filter chips (threat level, device type, protocol, time range, signal strength, FP hiding threshold), pull-to-refresh. Map tab shows clustered markers color-coded by threat level via OSMDroid.

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

**Analysis flow** (`DetectionAnalyzer.analyzeProgressively()`):
1. Check cache → instant return if hit
2. Emit rule-based result immediately (<10ms)
3. Launch LLM analysis in background
4. Emit LLM result when complete

**Enriched heuristics**: Handlers provide structured data to LLM prompts via `EnrichedDetectorData` (e.g., IMSI catcher score, encryption downgrade chain, C/N0 uniformity analysis, tracker sighting history). Cached with detection ID in `EnrichedDataCache`.

**False positive analysis** (`FalsePositiveAnalyzer`): Multi-layer FP detection using location patterns (home/work/safe areas), time-of-day context, and LLM-enhanced compact prompts. Batch-processed via `BackgroundAnalysisWorker` (hourly, 50 detections per batch).

**Cross-domain correlation** (`CrossDomainAnalyzer`):
- IMSI Catcher Combo (CRITICAL): Cell anomaly + GNSS spoofing within 5 min → 2.5x multiplier
- Following Pattern (HIGH): Same device at 3+ locations → 1.5x
- Coordinated Surveillance (CRITICAL/HIGH): Multiple protocols within 100m and 10 min → 2.0x
- Tracker Network (HIGH): Multiple trackers from same manufacturer → 1.8x
- Spatial Clustering (HIGH): 3+ devices within 200m → 1.3x

**Prompt system**: Full prompts (`PromptTemplates`), compact prompts (`CompactPromptTemplates` - 50-70% smaller for smaller models), input sanitization (strips prompt injection markers like `<start_of_turn>`, `[INST]`).

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

3. **`data/model/DetectionPatterns.kt`** - Add pattern(s): SSID regex (`PatternType.SSID_REGEX`), BLE name regex (`PatternType.BLE_NAME_REGEX`), MAC OUI prefix (`MacPrefix` with `AA:BB:CC` format), and/or BLE service UUIDs. Add `DeviceTypeInfo` entry with description, capabilities, privacy concerns, recommendations, and source URLs.

4. **Handler file** (if custom logic needed beyond pattern matching) - Add detection logic in the appropriate handler. Add LLM prompt generation for the new device type via `generatePrompt()`.

5. **`ui/components/Components.kt`** - Add icon mapping if custom icon needed (emoji from DeviceType is used by default).

6. **Tests** - Pattern regex positive/negative test cases, impact factor existence test.

### Connecting to Settings

New patterns are automatically available in the settings UI when added to the appropriate pattern enum in `DetectionSettings.kt`:
- Add to `BlePattern`, `WifiPattern`, `CellularPattern`, `GnssPattern`, `RfPattern`, `UltrasonicPattern`, or `SatellitePattern` enum
- Set `displayName` and `defaultEnabled` on the enum value
- The `DetectionCategoryCard` UI will automatically render the toggle
- Update `ProtectionPreset.kt` to include/exclude the pattern from each preset (ESSENTIAL, BALANCED, PARANOID)
- Threshold changes: add fields to the protocol's threshold data class (e.g., `BleThresholds`, `WifiThresholds`)

### LLM Assessment Integration

Add a `DeviceTypeInfo` entry in `DetectionPatterns.kt` with: description (what the device is), capabilities (what it can do), privacyConcerns (data collection, retention, access), recommendations (how to verify, what to do), and realWorldSources (URLs). This info is automatically included in LLM prompts via `generatePrompt()` in the handler.

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

## Key Constraints

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
