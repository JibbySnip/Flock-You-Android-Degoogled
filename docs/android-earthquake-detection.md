# Android Earthquake Detection and Alerting: Technical Reference

## 1. Architecture Overview

Google's Android Earthquake Alerts System (AEAS) is a dual-path earthquake early warning platform combining two distinct detection sources:

**Path 1 -- ShakeAlert Integration (US West Coast):** Partners with USGS ShakeAlert, which operates 1,675 dedicated seismic sensors across California, Oregon, and Washington. ShakeAlert data feeds are consumed by Google servers and translated into Android push notifications.

**Path 2 -- Crowdsourced Phone-as-Seismometer (Global):** Leverages 2+ billion Android phones worldwide as distributed seismometers. Each phone's accelerometer detects P-waves, reports to Google servers, and the server aggregates multiple phone triggers to confirm earthquakes.

### Detection Pipeline

```
Phone accelerometer (stationary)
    -> On-device P-wave trigger detection (STA/LTA + ANN classifier)
    -> Trigger message sent to Google earthquake detection server
        (contains: trigger time, coarse location, peak acceleration)
    -> Server-side Bayesian aggregation across multiple phones
    -> Candidate earthquake source confirmation
    -> Magnitude / hypocenter / origin time estimation
    -> Geographic targeting of alert zones
    -> Push notification via Google Play Services
    -> "Be Aware" or "Take Action" alert on user devices
```

### Operational Scale (2021-2024, from Allen et al. Science 2025)

| Metric | Value |
|--------|-------|
| Earthquakes detected by phone network | 11,000+ |
| Matched traditional seismic catalogs | 85% |
| Events triggering user alerts | 1,279 in 98 countries |
| Monthly detection rate | ~312 earthquakes |
| Monthly alert-triggering events (M >= 4.5) | ~60 |
| Monthly alerts delivered | ~18 million |
| Cumulative alerts sent | 790 million |
| False alerts over entire period | 3 |
| Magnitude range | M1.9 to M7.8 |

---

## 2. AOSP Cell Broadcast Framework

The core AOSP emergency alert infrastructure is in the CellBroadcast APEX module (`com.android.cellbroadcast`), available on Android 11+.

### Key Packages and Classes

| Package | Class | Role |
|---------|-------|------|
| `com.android.cellbroadcastservice` | `CellBroadcastService` | SMS decoding, geofencing (WEA 3.0), dedup, message broadcast |
| `com.android.cellbroadcastreceiver` | `CellBroadcastReceiver` | BroadcastReceiver for incoming alert intents |
| `com.android.cellbroadcastreceiver` | `CellBroadcastAlertService` | Alert processing, notification/dialog routing |
| `com.android.cellbroadcastreceiver` | `CellBroadcastAlertDialog` | Full-screen emergency alert UI |
| `com.android.cellbroadcastreceiver` | `CellBroadcastAlertAudio` | Sound/vibration playback service |
| `com.android.cellbroadcastreceiver` | `CellBroadcastChannelManager` | Channel range/scope validation |
| `com.android.cellbroadcastreceiver` | `CellBroadcastContentProvider` | Database persistence |
| `com.android.cellbroadcastreceiver` | `CellBroadcastConfigService` | Channel enable/disable configuration |
| `com.android.cellbroadcastreceiver` | `CellBroadcastListActivity` | Alert history UI |
| `android.telephony` | `SmsCbMessage` | Cell broadcast message data model |
| `android.telephony` | `SmsCbCmasInfo` | CMAS warning info (severity, urgency, certainty) |
| `android.telephony` | `SmsCbEtwsInfo` | ETWS warning info (earthquake/tsunami type, isPrimary) |
| `com.android.internal.telephony.gsm` | `SmsCbConstants` | Message ID constants for ETWS/CMAS |

### AOSP Source Locations

```
frameworks/base/telephony/java/com/android/internal/telephony/gsm/SmsCbConstants.java
frameworks/base/telephony/java/android/telephony/SmsCbMessage.java
frameworks/base/telephony/java/android/telephony/SmsCbCmasInfo.java
frameworks/base/telephony/java/android/telephony/SmsCbEtwsInfo.java
frameworks/base/telephony/java/android/telephony/CellBroadcastService.java
frameworks/base/core/java/android/provider/Telephony.java
packages/modules/CellBroadcastService/
packages/apps/CellBroadcastReceiver/
```

### Broadcast Intent Actions

```java
// Standard cell broadcast received (non-emergency)
"android.provider.Telephony.SMS_CB_RECEIVED"
// Requires: android.Manifest.permission.RECEIVE_SMS

// Emergency cell broadcast received (ETWS/CMAS) -- @SystemApi
"android.provider.action.SMS_EMERGENCY_CB_RECEIVED"
// Requires: android.Manifest.permission.RECEIVE_EMERGENCY_BROADCAST

// Area info updates (Channel 50)
"android.telephony.action.AREA_INFO_UPDATED"

// CellBroadcastReceiver internal actions:
"com.android.cellbroadcastreceiver.intent.action.MARK_AS_READ"
"com.android.cellbroadcastreceiver.intent.START_CONFIG"
```

### Permissions

```xml
android.permission.RECEIVE_SMS
android.permission.RECEIVE_EMERGENCY_BROADCAST  <!-- System only -->
android.permission.READ_CELL_BROADCASTS         <!-- Runtime, default SMS apps -->
com.android.cellbroadcastservice.FULL_ACCESS_CELL_BROADCAST_HISTORY  <!-- Module-signed -->
android.permission.READ_PRIVILEGED_PHONE_STATE
```

---

## 3. ETWS Message Identifiers (Earthquake & Tsunami)

From `SmsCbConstants.java`:

```java
// ETWS (Earthquake and Tsunami Warning System) -- 3GPP TS 23.041
MESSAGE_ID_ETWS_EARTHQUAKE_WARNING              = 0x1100  // 4352
MESSAGE_ID_ETWS_TSUNAMI_WARNING                 = 0x1101  // 4353
MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING  = 0x1102  // 4354
MESSAGE_ID_ETWS_TEST_MESSAGE                    = 0x1103  // 4355
MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE            = 0x1104  // 4356

MESSAGE_ID_ETWS_TYPE_MASK = 0xFFF8
MESSAGE_ID_ETWS_TYPE      = 0x1100
```

### ETWS Serial Number Flags

```java
SERIAL_NUMBER_ETWS_ACTIVATE_POPUP       = 0x1000  // 4096 -- popup display required
SERIAL_NUMBER_ETWS_EMERGENCY_USER_ALERT = 0x2000  // 8192 -- emergency user alert
```

### SmsCbEtwsInfo Warning Types

```java
ETWS_WARNING_TYPE_EARTHQUAKE             = 0
ETWS_WARNING_TYPE_TSUNAMI                = 1
ETWS_WARNING_TYPE_EARTHQUAKE_AND_TSUNAMI = 2
ETWS_WARNING_TYPE_TEST_MESSAGE           = 3
ETWS_WARNING_TYPE_OTHER_EMERGENCY        = 4
```

### SmsCbEtwsInfo Key Methods

```java
int getWarningType()          // Returns ETWS_WARNING_TYPE_* constant
boolean isPrimary()           // Primary: minimal data, <4s delivery; Secondary: detailed info
boolean isPopupAlert()        // Whether popup display is required
boolean isEmergencyUserAlert() // Whether emergency user alert is required
```

---

## 4. CMAS Message Identifiers (WEA)

```java
// CMAS (Commercial Mobile Alert System) -- used for WEA in US
MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL            = 0x1112  // 4370
MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED    = 0x1113  // 4371
MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY      = 0x1114  // 4372
MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED     = 0x1115  // 4373
MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_LIKELY        = 0x1116  // 4374
MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_OBSERVED     = 0x1117  // 4375
MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_LIKELY       = 0x1118  // 4376
MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_OBSERVED      = 0x1119  // 4377
MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY        = 0x111A  // 4378
MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY     = 0x111B  // 4379
MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST         = 0x111C  // 4380
MESSAGE_ID_CMAS_ALERT_EXERCISE                      = 0x111D  // 4381
MESSAGE_ID_CMAS_ALERT_OPERATOR_DEFINED_USE          = 0x111E  // 4382
MESSAGE_ID_CMAS_GEO_FENCING_TRIGGER                 = 0x1130  // 4440

// PWS range boundaries
MESSAGE_ID_PWS_FIRST_IDENTIFIER = 0x1100  // 4352
MESSAGE_ID_PWS_LAST_IDENTIFIER  = 0x18FF  // 6399
```

### SmsCbCmasInfo Constants

```java
// Message class
CMAS_CLASS_PRESIDENTIAL_LEVEL_ALERT       = 0x00
CMAS_CLASS_EXTREME_THREAT                 = 0x01
CMAS_CLASS_SEVERE_THREAT                  = 0x02
CMAS_CLASS_CHILD_ABDUCTION_EMERGENCY      = 0x03
CMAS_CLASS_REQUIRED_MONTHLY_TEST          = 0x04
CMAS_CLASS_CMAS_EXERCISE                  = 0x05
CMAS_CLASS_OPERATOR_DEFINED_USE           = 0x06
CMAS_CLASS_UNKNOWN                        = -1

// Category (earthquake = GEO)
CMAS_CATEGORY_GEO       = 0x00  // Geophysical (earthquake, landslide)
CMAS_CATEGORY_MET       = 0x01  // Meteorological (flood)
CMAS_CATEGORY_SAFETY    = 0x02
CMAS_CATEGORY_SECURITY  = 0x03
CMAS_CATEGORY_RESCUE    = 0x04
CMAS_CATEGORY_FIRE      = 0x05
CMAS_CATEGORY_HEALTH    = 0x06
CMAS_CATEGORY_ENV       = 0x07
CMAS_CATEGORY_TRANSPORT = 0x08
CMAS_CATEGORY_INFRA     = 0x09
CMAS_CATEGORY_CBRNE     = 0x0A
CMAS_CATEGORY_OTHER     = 0x0B
CMAS_CATEGORY_UNKNOWN   = -1

// Severity
CMAS_SEVERITY_EXTREME  = 0x00
CMAS_SEVERITY_SEVERE   = 0x01
CMAS_SEVERITY_UNKNOWN  = -1

// Urgency
CMAS_URGENCY_IMMEDIATE = 0x00
CMAS_URGENCY_EXPECTED  = 0x01
CMAS_URGENCY_UNKNOWN   = -1

// Certainty
CMAS_CERTAINTY_OBSERVED = 0x00
CMAS_CERTAINTY_LIKELY   = 0x01
CMAS_CERTAINTY_UNKNOWN  = -1

// Response type
CMAS_RESPONSE_TYPE_SHELTER  = 0x00
CMAS_RESPONSE_TYPE_EVACUATE = 0x01
CMAS_RESPONSE_TYPE_PREPARE  = 0x02
CMAS_RESPONSE_TYPE_EXECUTE  = 0x03
CMAS_RESPONSE_TYPE_MONITOR  = 0x04
CMAS_RESPONSE_TYPE_AVOID    = 0x05
CMAS_RESPONSE_TYPE_ASSESS   = 0x06
CMAS_RESPONSE_TYPE_NONE     = 0x07
CMAS_RESPONSE_TYPE_UNKNOWN  = -1
```

### SmsCbMessage Constants

```java
// Geographical scope
GEOGRAPHICAL_SCOPE_CELL_WIDE_IMMEDIATE = 0
GEOGRAPHICAL_SCOPE_PLMN_WIDE          = 1
GEOGRAPHICAL_SCOPE_LOCATION_AREA_WIDE = 2
GEOGRAPHICAL_SCOPE_CELL_WIDE          = 3

// Message format
MESSAGE_FORMAT_3GPP  = 1    // GSM/UMTS
MESSAGE_FORMAT_3GPP2 = 2    // CDMA

// Message priority
MESSAGE_PRIORITY_NORMAL      = 0
MESSAGE_PRIORITY_INTERACTIVE = 1
MESSAGE_PRIORITY_URGENT      = 2
MESSAGE_PRIORITY_EMERGENCY   = 3
```

---

## 5. WEA vs Google AEA for Earthquake Alerts

### WEA/CMAS Path (Carrier-Based)

- Used for ShakeAlert-powered earthquake early warnings on the US West Coast
- Delivered via cell broadcast infrastructure (no internet required)
- Uses CMAS Extreme category channels (4371-4374) for earthquake EEW
- Earthquake alerts classified as `CMAS_CATEGORY_GEO` with `CMAS_SEVERITY_EXTREME` and `CMAS_URGENCY_IMMEDIATE`
- Geographic targeting via WEA 3.0 geo-fencing polygons
- Cannot be opted out for presidential-level alerts; extreme/severe can be toggled

### Google AEA Path (Play Services-Based)

- Used globally, including US regions not covered by WEA ShakeAlert
- Delivered via Google Play Services push (requires internet)
- Not tied to cell broadcast channels -- uses proprietary Google notification infrastructure
- Phone acts as both sensor and alert receiver
- Can be fully disabled in Settings

---

## 6. ShakeAlert / USGS Integration

### Geographic Coverage

| Region | Status | Android Launch |
|--------|--------|---------------|
| California | Full production | October 2019 |
| Oregon | Full production | March 2021 |
| Washington | Full production | May 2021 |
| Alaska | Phase 1 planning | Under development |

Infrastructure: 1,675 seismic sensor stations. Coverage: 50+ million people.

### Alert Delivery Thresholds

| Delivery Path | Magnitude Threshold | Intensity Threshold |
|---------------|--------------------|--------------------|
| Google Android push (ShakeAlert) | M >= 4.5 | MMI >= III (weak/light) |
| WEA cell broadcast (ShakeAlert) | M >= 5.0 | MMI >= IV (moderate) |
| Google Android push (crowdsourced) | M >= 4.5 | MMI >= III |

WEA has a higher threshold because alerts cannot be targeted as precisely as Google's push system.

### Integration Flow

1. ShakeAlert seismic network detects earthquake
2. ShakeAlert Message generated with earthquake parameters
3. Google servers receive ShakeAlert Message in real-time
4. Google applies magnitude/MMI thresholds
5. Geographic targeting determines affected Android devices
6. Push notification sent via Google Play Services
7. Device displays "Be Aware" or "Take Action" alert based on expected local intensity

This path does NOT use WEA/cell broadcast -- it uses Google's own push infrastructure.

### Performance

For the Turkey April 2025 M6.2 earthquake: first alert issued 8.0 seconds after origin. Users experiencing moderate-to-strong shaking received 5-20 seconds of warning time.

---

## 7. Crowdsourced Detection (Phone-as-Seismometer)

### On-Device Detection Algorithm

Derived from the MyShake system (UC Berkeley), adapted by Google for Google Play Services.

**Pre-conditions:**
- Phone must be stationary (not being held, carried, or moved)
- Phone must have been stationary for a minimum qualifying period (~30 min)
- Location services must be enabled
- Earthquake alerts must be enabled

### Step 1: STA/LTA Trigger

When the phone is stationary, the accelerometer is continuously monitored. A Short-Term Average / Long-Term Average ratio trigger detects sudden acceleration changes:

| Parameter | Value |
|-----------|-------|
| STA window | 2 seconds |
| LTA window | 20 seconds |
| Trigger threshold | STA/LTA ratio > 4.0 |
| Detrigger threshold | ~1.5 |

### Step 2: ANN Classification

A neural network classifies whether the detected motion is earthquake-like or human activity.

**Architecture (from MyShake patent US20180376314A1):**
- Input layer: 3 nodes
- Hidden layer: 1 layer, 5 neurons, sigmoid activation
- Output layer: 1 node (earthquake probability)

**Three input features computed over a 2-second sliding window (1-second step):**

1. **IQR (Interquartile Range)**: Middle 50% amplitude range of the acceleration vector sum. Measures motion amplitude.
2. **ZC (Zero Crossing Rate)**: Maximum number of times the signal crosses baseline zero. Frequency measure.
3. **CAV (Cumulative Absolute Velocity)**: `CAV = integral(0 to 2) |a(t)| dt` where a(t) is vector sum of 3-component acceleration.

**Classification principle:** Earthquakes produce high-frequency, moderate-amplitude motion. Human activities produce either low-frequency high-amplitude (walking) or high-frequency low-amplitude (typing). IQR and ZC together separate these domains; CAV provides additional discrimination.

**Accuracy:** 98-99% true positive rate for earthquakes within 10 km; 93% true negative rate (7% false positive from human activities).

### Accelerometer Specifications

| Parameter | Value |
|-----------|-------|
| Sampling rate | 25-50 Hz (50 Hz designed, 25 Hz effective on many devices) |
| Usable frequency response | 0.5 Hz to 10 Hz |
| Classification window | 2-second windows |
| Data capture on trigger | 1 min pre-trigger + 4 min post-trigger |
| Detectable magnitude range | M2.5+ (close) to M5+ (up to ~150 km) |
| On-device ANN processing time | ~4.5 milliseconds |
| Network transmission latency | ~50 milliseconds (UDP) |

**Device quality variation:**
- Samsung devices: median std_dt of 4 ms (excellent timing stability)
- HTC devices: median std_dt of 39 ms (poor timing stability)
- STMicroelectronics accelerometers (LSM6DSL, LSM6DSM) show best performance
- Devices near highways (<1 km) show elevated noise vs rural locations

### Server-Side Aggregation

When a phone classifies motion as earthquake-like, it sends:
- Trigger timestamp
- Coarse device location (privacy-coarsened before leaving device)
- Peak Ground Acceleration (PGA) from 3-component accelerometer
- Waveform characteristics

**Aggregation algorithm:**
1. Incoming phone triggers are clustered spatially and temporally
2. Search window: ~20-second sliding window
3. Clustering requirement: >60% of active phones within a 10 km radius must have triggered
4. When threshold met, earthquake declared
5. Origin time set to earliest trigger time
6. Epicenter calculated as centroid of triggered phones within cluster
7. Magnitude estimated via regression:

```
M_est = 1.352 * log10(PGA) + 1.658 * log10(distance) + 4.858
```

8. Bayesian filters and ML algorithms validate the pattern
9. Phones with excessive background noise excluded from aggregation

**Accuracy improvements:**
- Magnitude median absolute error: reduced from 0.50 to 0.25 over 3 years
- Location error: ~3.8 km (simulated tests)
- Origin time error: ~1.7 seconds

### Network Density Requirements

| Metric | Value |
|--------|-------|
| Minimum for good performance | 300+ phones per 111 km x 111 km |
| Corresponding inter-phone distance | ~6.4 km |
| Global Android installed base | 2+ billion devices |

### Latency

| Stage | Time |
|-------|------|
| On-device classification | ~4.5 ms |
| Network transmission | ~50 ms |
| Server aggregation and confirmation | Several seconds |
| Alert push delivery | Sub-second |
| **Total typical latency** | **5-15 seconds from P-wave arrival** |

**User survey data (Science paper):**
- 36% received alert BEFORE shaking
- 28% received alert DURING shaking
- 23% received alert AFTER shaking

### False Positive Mitigation

- Extended assessment period before declaring earthquake
- Phones with excessive background noise excluded
- Spatial consistency checks (triggers must cluster geographically)
- >60% activation threshold prevents correlated non-seismic events
- Only 3 false alerts over 3 years of global operation
- Vulnerability: high-amplitude correlated events (stadium crowds, explosions near many phones)

---

## 8. Alert Delivery Mechanism

### Two Alert Types

| Property | Be Aware | Take Action |
|----------|----------|-------------|
| Trigger condition | M >= 4.5, MMI 3-4 (weak/light) | M >= 4.5, MMI >= 5 (moderate to extreme) |
| Display | Standard notification | Full-screen takeover |
| Sound | Default notification sound | Loud alarm sound |
| DND bypass | No | Yes |
| Screen wake | No | Yes |
| User action | Informational | "Drop, Cover, Hold On" instructions |
| Dismissal | Standard dismiss | Requires explicit interaction |

### Google Play Services Delivery

- Delivered via Google Play Services push notification infrastructure
- Requires Wi-Fi or cellular data connectivity
- Requires Google Play Services installed and updated
- Settings: `Safety & emergency > Earthquake alerts`

### Cell Broadcast (WEA/ETWS) Delivery

- Delivered via cellular network cell broadcast (no internet required)
- Emergency alerts use `CellBroadcastAlertDialog` for full-screen display
- `PowerManager.wakeUp()` for screen wake
- Sound delegated to `CellBroadcastAlertAudio` service

### DND Bypass

**Google AEA (Take Action):** Bypasses DND and all notification restrictions; turns on screen; plays loud sound; full-screen activity takeover.

**WEA/CMAS (CellBroadcastAlertService):** Emergency alerts use `PRIORITY_HIGH` notification with `VISIBILITY_PUBLIC` flag and `setOngoing(true)`. Direct activity launch (`CellBroadcastAlertDialog`) bypasses the notification system entirely. `PowerManager.wakeUp()` ensures screen activation.

---

## 9. AOSP CellBroadcast Module Architecture

### Module Structure

Delivered as a Mainline module in a single APEX file:

```
com.android.cellbroadcast.apex
+-- CellBroadcastService     (packages/modules/CellBroadcastService/)
+-- CellBroadcastReceiver    (packages/apps/CellBroadcastReceiver/)
```

### Message Processing Flow

```
RIL (Radio Interface Layer)
    -> InBoundSMSHandler (CellBroadcast SMS received)
    -> Framework forwards to CellBroadcastService module
    -> CellBroadcastService:
        - Parses message (3GPP / 3GPP2 format)
        - WEA 3.0 geo-fencing check (polygon/circle geometry)
        - Duplicate detection
        - Broadcasts intent to CellBroadcastReceiver
    -> CellBroadcastReceiver:
        - Receives SMS_EMERGENCY_CB_RECEIVED or SMS_CB_RECEIVED
        - Routes to CellBroadcastAlertService
    -> CellBroadcastAlertService:
        - Channel enablement validation
        - Language filtering
        - Duplicate detection (MessageServiceCategoryAndScope)
        - Database insert (CellBroadcastContentProvider)
        - Emergency: Launch CellBroadcastAlertDialog + CellBroadcastAlertAudio
        - Non-emergency: Post notification
```

### CellBroadcastAlertService Processing Details

1. Receives broadcast intent
2. Extracts `SmsCbMessage` from intent extras
3. Validates subscription ID
4. Checks channel enablement via `isChannelEnabled()`
5. Computes message hash and ETWS primary/secondary status for deduplication
6. Queries duplicate detection map (24-hour default expiry, carrier-configurable, max 1,024 cached)
7. Inserts to database via `CellBroadcastContentProvider`
8. For ETWS: checks `message.isEtwsMessage()`, inspects `SmsCbEtwsInfo.isPrimary()`, maps to `AlertType.ETWS_EARTHQUAKE` or `AlertType.ETWS_TSUNAMI`
9. Routes emergency alerts to `CellBroadcastAlertDialog` (full-screen, `FLAG_ACTIVITY_NEW_TASK`, `PowerManager.wakeUp()`)
10. Routes non-emergency to notification bar
11. Delegates sound/vibration to `CellBroadcastAlertAudio` service

### CarrierConfig Keys

```java
KEY_CARRIER_FORCE_DISABLE_ETWS_CMAS_TEST_BOOL
KEY_ALWAYS_SHOW_EMERGENCY_ALERT_ONOFF_BOOL
KEY_ENABLE_ALERTS_MASTER_TOGGLE
KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS
KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS
KEY_ENABLE_CMAS_AMBER_ALERTS
KEY_MESSAGE_EXPIRATION_TIME_LONG
```

### OEM Customization

**Runtime Resource Overlays (RROs):**
- Target package: `com.android.cellbroadcastreceiver`
- Customizable: notification colors, dialog sizes, alert tones, vibration patterns
- Cannot modify core alert processing logic

**Carrier Configuration:**
- Per-carrier config via `CarrierConfigManager` keys
- MCC/MNC-based configuration files
- Controls: alert channel enable/disable, test message visibility, message expiration

**Restrictions:**
- OEMs CANNOT modify module code (ensures consistent behavior)
- Must use RROs for visual/audio customization
- Legacy data migration required for upgrading devices

---

## 10. Japan -- ETWS (Earthquake and Tsunami Warning System)

Japan pioneered mobile earthquake alerting with ETWS, standardized in 3GPP. Supported in Android since Android 8.1 Oreo.

### Two-Tier Notification System

| Tier | Mechanism | Content | Latency |
|------|-----------|---------|---------|
| Primary | SIB10 (SystemInformationBlockType10) | Warning type only (Earthquake/Tsunami/Both/Test/Other) | < 4 seconds |
| Secondary | SIB11 (SystemInformationBlockType11) | Detailed info: epicenter, seismic intensity, magnitude | 4-9 seconds |

### SIB10 Structure (Primary Notification)

```
messageIdentifier:   16-bit (3GPP TS 23.041-9.4.1.2.2)
serialNumber:        16-bit (3GPP TS 23.041-9.4.1.2.1)
warningType:         2-octet OCTET STRING (3GPP TS 23.041-9.3.24)
warningSecurityInfo: 50-octet (optional, 3GPP TS 23.041-9.3.25)
```

### SIB11 Structure (Secondary Notification)

```
messageIdentifier:          16-bit
serialNumber:               16-bit
warningMessageSegmentType:  ENUMERATED (notLastSegment)
warningMessageSegmentNumber: INTEGER 0-63
warningMessageSegment:      Variable-length content
dataCodingScheme:           1-octet (optional, 3GPP TS 23.038-5)
```

### Repetition Intervals (Configurable)

80ms, 160ms, 320ms, 640ms, 1.28s, 2.56s, 5.12s -- repeated transmission increases reception probability.

### Android Behavior for ETWS

- Unique vibration pattern
- Text-to-speech support
- Full-screen alert dialog
- Pre-set alert sound and pre-determined text per warning type
- All citizens receive within 4-9 seconds
- NTT DoCoMo, au (KDDI), and SoftBank all implement ETWS

### 3GPP References

- TS 22.168: Requirements for ETWS
- TS 23.041: Cell Broadcast Centre interfaces
- TS 23.038: Data coding scheme
- TS 36.331: Radio Resource Control (RRC) protocol
- TS 23.828: Architecture study
- TS 36.523-14: ETWS conformance testing

---

## 11. Other International EEW Systems on Android

### Taiwan

- Operated by Central Weather Administration (CWA)
- Threshold: M >= 5.0 with expected intensity in affected areas
- Alert issuance: ~17-20 seconds after earthquake occurrence
- Delivered via Cell Broadcast Service (CBS) on 4G networks

### Mexico -- SASMEX

- Operated by CIRES, uses dedicated seismic sensors along Pacific coast
- Provides up to 60 seconds warning to Mexico City
- Cell broadcast NOT implemented by Mexican operators
- Alerts distributed via separate app (SASSLA), public loudspeakers, radio/TV

### Countries with Nationwide Cell Broadcast EEW

As of January 2026: China, Japan, Taiwan, South Korea, Israel, and Transnistria have comprehensive nationwide EEW systems delivering alerts via cell broadcast.

### Google AEA Global Coverage

Fills the gap in countries without dedicated EEW infrastructure. Operates in 98 countries, providing alerts where no traditional seismic network exists.

---

## 12. Google Play Services Components

### Module Identification

Earthquake detection is embedded within **Google Play Services** (`com.google.android.gms`), specifically the "Safety & Emergency" module. Not a standalone app.

### Internal Components

- Accelerometer monitoring runs as a background service within GMS
- Uses `SensorManager` API with `TYPE_ACCELEROMETER` sensor
- Location via Google Fused Location Provider
- Alert delivery via high-priority GMS push channel
- Registers for activity recognition to determine phone stationary state

### Requirements

- Google Play Services reasonably current
- Device must have an accelerometer
- Location services enabled
- Earthquake alerts setting on (enabled by default in most regions)
- Wi-Fi or cellular data required

### What GMS Earthquake Detection Is NOT

- NOT related to `com.google.android.gms.nearby.exposurenotification`
- NOT part of the `SafetyNet` API
- NOT part of the public `Activity Recognition` API
- NO third-party developer-facing API exists for earthquake detection

---

## 13. Settings UI

### Settings Path

**Primary:** `Settings > Safety & emergency > Earthquake alerts`
**Alternative (some devices):** `Settings > Location > Advanced > Earthquake alerts`

### Available Toggles

| Toggle | Default | Description |
|--------|---------|-------------|
| Earthquake alerts | ON | Master toggle -- enables/disables all earthquake detection and alerting |

No separate toggles for:
- Contributing sensor data vs. receiving alerts (coupled)
- ShakeAlert vs. crowdsourced detection (auto-selected by region)
- Be Aware vs. Take Action alerts (both enabled/disabled together)

### WEA/Cell Broadcast Settings (Separate Path)

`Settings > Apps & Notifications > Advanced > Emergency alerts` (varies by OEM)

| Toggle | Default | User-Disablable? |
|--------|---------|-------------------|
| Presidential alerts | ON | NO |
| Extreme threats | ON | Yes |
| Severe threats | ON | Yes |
| AMBER alerts | ON | Yes |
| Test alerts | OFF | Yes |
| Public safety | ON | Yes |

---

## 14. Privacy and Data Collection

### What Leaves the Device (On Trigger Only)

1. Trigger timestamp
2. **Coarse** device location (privacy-coarsened on device BEFORE transmission)
3. Peak ground acceleration values (3-component)
4. Waveform characteristics

### What Is NOT Sent

- Precise GPS coordinates
- User identity, Google account info, IMEI, IMSI, phone number
- Continuous accelerometer streams
- Audio, video, or any other sensor data
- Browsing history, app usage, or any non-seismic data

### Disable Options

| Action | Effect |
|--------|--------|
| Disable "Earthquake alerts" in Settings | Stops contributing sensor data AND receiving alerts |
| Disable Location services | Earthquake alerts cannot function |
| Use AOSP without GMS | No Google earthquake detection; only carrier cell broadcast |
| Use de-Googled ROM (LineageOS, GrapheneOS, CalyxOS) | Same as AOSP without GMS |

---

## 15. Quick Reference: All Constants

```java
// === ETWS Message IDs ===
MESSAGE_ID_ETWS_EARTHQUAKE_WARNING              = 0x1100  // 4352
MESSAGE_ID_ETWS_TSUNAMI_WARNING                 = 0x1101  // 4353
MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING  = 0x1102  // 4354
MESSAGE_ID_ETWS_TEST_MESSAGE                    = 0x1103  // 4355
MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE            = 0x1104  // 4356

// === CMAS Message IDs ===
MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL        = 0x1112  // 4370
MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED= 0x1113  // 4371
MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY  = 0x1114  // 4372

// === ETWS Serial Number Flags ===
SERIAL_NUMBER_ETWS_ACTIVATE_POPUP               = 0x1000
SERIAL_NUMBER_ETWS_EMERGENCY_USER_ALERT         = 0x2000

// === SmsCbEtwsInfo Warning Types ===
ETWS_WARNING_TYPE_EARTHQUAKE                    = 0
ETWS_WARNING_TYPE_TSUNAMI                       = 1
ETWS_WARNING_TYPE_EARTHQUAKE_AND_TSUNAMI        = 2
ETWS_WARNING_TYPE_TEST_MESSAGE                  = 3
ETWS_WARNING_TYPE_OTHER_EMERGENCY               = 4

// === SmsCbMessage Priority ===
MESSAGE_PRIORITY_NORMAL      = 0
MESSAGE_PRIORITY_INTERACTIVE = 1
MESSAGE_PRIORITY_URGENT      = 2
MESSAGE_PRIORITY_EMERGENCY   = 3

// === Intent Actions ===
"android.provider.Telephony.SMS_CB_RECEIVED"
"android.provider.action.SMS_EMERGENCY_CB_RECEIVED"
"android.telephony.action.AREA_INFO_UPDATED"

// === Alert Thresholds ===
// Google AEA "Be Aware":  M >= 4.5, MMI 3-4
// Google AEA "Take Action": M >= 4.5, MMI >= 5
// ShakeAlert WEA: M >= 5.0, MMI >= IV
// ShakeAlert Android: M >= 4.5, MMI >= III
```

### Detection Algorithm Parameters

```
Accelerometer sampling: 25-50 Hz
STA/LTA: 2s / 20s windows, threshold 4.0, detrigger 1.5
ANN: 3 inputs (IQR, ZC, CAV) -> 5 hidden sigmoid -> 1 output
Classification window: 2 seconds, 1-second step
Server clustering: 10 km radius, 20s window, >60% phone threshold
Magnitude: M_est = 1.352 * log10(PGA) + 1.658 * log10(dist) + 4.858
On-device ANN time: ~4.5 ms
Network latency: ~50 ms UDP
```

---

## Sources

- [Global earthquake detection using Android phones (Science, 2025)](https://www.science.org/doi/10.1126/science.ads4779)
- [Android Earthquake Alerts (Google Research)](https://research.google/blog/android-earthquake-alerts-a-global-system-for-early-warning/)
- [MyShake: A smartphone seismic network (Science Advances, 2016)](https://www.science.org/doi/10.1126/sciadv.1501055)
- [MyShake patent US20180376314A1](https://patents.google.com/patent/US20180376314A1/en)
- [Accelerometer data quality analysis (arXiv 2407.03570)](https://arxiv.org/html/2407.03570v1)
- [Controlling false alarm probability (arXiv 2210.15466)](https://arxiv.org/abs/2210.15466)
- [Google Blog: Earthquake detection and alerts](https://blog.google/products/android/earthquake-detection-and-alerts/)
- [Google Crisis Response](https://crisisresponse.google/android-alerts/)
- [CellBroadcast AOSP Module](https://source.android.com/docs/core/ota/modular-system/cellbroadcast)
- [SmsCbConstants.java (AOSP)](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/telephony/java/com/android/internal/telephony/gsm/SmsCbConstants.java)
- [SmsCbCmasInfo.java (AOSP)](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/telephony/java/android/telephony/SmsCbCmasInfo.java)
- [SmsCbMessage.java (AOSP)](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/telephony/java/android/telephony/SmsCbMessage.java)
- [CellBroadcastAlertService.java](https://github.com/yuchuangu85/Android-system-apps/blob/master/apps/CellBroadcastReceiver/src/com/android/cellbroadcastreceiver/CellBroadcastAlertService.java)
- [ShakeAlert Delivery Thresholds](https://www.shakealert.org/system-information/alert-delivery-thresholds/)
- [USGS ShakeAlert Overview](https://www.usgs.gov/programs/earthquake-hazards/science/earthquake-early-warning-overview)
- [ETWS Technical Reference (ShareTechNote)](https://www.sharetechnote.com/html/Handbook_LTE_ETWS.html)
- [PWS Message Identifier Reference](https://www.sharetechnote.com/html/Handbook_LTE_PWS_MessageIdentifier.html)
- [Google Play Services Safety & Emergency](https://support.google.com/android/answer/12464968?hl=en)
- [Earthquake Early Warning Systems (Wikipedia)](https://en.wikipedia.org/wiki/Earthquake_early_warning_system)
