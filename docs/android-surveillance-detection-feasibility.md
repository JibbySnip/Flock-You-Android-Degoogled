# Android as a Surveillance Detection Platform: Practicality, Feasibility, and Limitations

A deep technical analysis of using stock Android devices to detect surveillance equipment, cyber attacks, and privacy threats -- what actually works, what doesn't, and why.

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [The Android Security Model: What Apps Can and Cannot See](#the-android-security-model)
3. [Protocol-by-Protocol Analysis](#protocol-by-protocol-analysis)
   - [Bluetooth Low Energy (BLE)](#1-bluetooth-low-energy-ble)
   - [WiFi](#2-wifi)
   - [Cellular / IMSI Catcher Detection](#3-cellular--imsi-catcher-detection)
   - [GNSS Spoofing & Jamming](#4-gnss-spoofing--jamming)
   - [Ultrasonic Beacon Detection](#5-ultrasonic-beacon-detection)
   - [RF Spectrum Analysis](#6-rf-spectrum-analysis)
   - [Satellite / Non-Terrestrial Network (NTN)](#7-satellite--non-terrestrial-network-ntn)
4. [Cross-Protocol Correlation](#cross-protocol-correlation)
5. [The Privilege Gap: Sideload vs System vs OEM](#the-privilege-gap)
6. [False Positive Economics](#false-positive-economics)
7. [Comparison with Prior Art](#comparison-with-prior-art)
8. [What Android Gets Right](#what-android-gets-right)
9. [Hard Limitations That Cannot Be Overcome](#hard-limitations-that-cannot-be-overcome)
10. [The Honest Verdict](#the-honest-verdict)
11. [References & Sources](#references--sources)

---

## Executive Summary

Android is a surprisingly capable -- but fundamentally constrained -- platform for surveillance detection. The BLE and WiFi radios expose rich advertisement and scan data that enables legitimate pattern-based detection of commercial surveillance equipment. Cellular APIs provide enough metadata to flag IMSI catcher indicators. GNSS raw measurements support academically-validated spoofing detection. But Android's security sandbox prevents access to raw radio frames, baseband modems, and RF spectrum data, making certain categories of detection impossible without root privileges or external hardware.

**The honest summary:**

| Capability | Feasibility | Confidence |
|---|---|---|
| Detecting known surveillance devices by BLE/WiFi signatures | High | High |
| Tracking BLE trackers following you across locations | High | Medium-High |
| Detecting IMSI catchers via cellular anomalies | Medium | Medium |
| Detecting GNSS spoofing via signal analysis | Medium | Medium (high FP rate) |
| Detecting ultrasonic tracking beacons | Medium-Low | Medium |
| Detecting WiFi deauth attacks | Low | Low (indirect only) |
| RF spectrum analysis without external hardware | None | N/A |
| Proving an IMSI catcher captured your identity | None (without OEM) | N/A |

The greatest strength of this approach is the sheer breadth of data Android *does* expose through its BLE and WiFi scanning APIs. The greatest weakness is the inability to access raw radio frames or baseband data, which means many detections are heuristic indicators rather than definitive proof.

---

## The Android Security Model

Before evaluating specific detection protocols, it's essential to understand what Android's security architecture permits and prohibits.

### What Apps Can Access

Android provides high-level, abstracted APIs for radio interfaces. An app running as a foreground service with appropriate permissions can access:

- **BLE**: Full advertisement packets including device name, MAC address, RSSI, service UUIDs, manufacturer-specific data (keyed by company ID), raw advertisement bytes, TX power level, connectable flag, and advertising set metadata (Android 8+).
- **WiFi**: Scan results with SSID, BSSID, RSSI, frequency, capabilities (encryption/auth), channel width, WiFi standard (Android 11+), and timestamps.
- **Cellular**: Cell tower identity (CID, LAC/TAC, MCC/MNC, PCI/PSC, ARFCN/EARFCN), signal strength (RSRP, RSSI, RSRQ, SINR), timing advance, connection status, and network type.
- **GNSS**: Satellite status (count, C/N0, elevation, azimuth, constellation, ephemeris/almanac flags), raw measurements (pseudorange, carrier phase, Doppler, clock data -- chipset-dependent), and carrier frequency (dual-frequency on flagships since ~2018).
- **Audio**: Full microphone access via AudioRecord at up to 48 kHz sample rate, sufficient for near-ultrasonic analysis.

### What Apps Cannot Access

The following are blocked by Android's SELinux mandatory access control, Linux DAC isolation, and API restrictions:

- **Raw 802.11 frames**: No monitor mode. Apps cannot see management frames (deauth, association, probe request/response). This fundamentally limits WiFi attack detection.
- **Baseband modem data**: The cellular baseband runs isolated firmware. No app -- even with root on most devices -- can observe raw signaling-layer events, cipher suite negotiation, silent SMS, or IMSI capture.
- **RF spectrum**: No API exists for wideband RF reception. The BLE, WiFi, and cellular radios are not exposed as software-defined radios.
- **Other apps' radio data**: Sandboxing prevents observing other apps' Bluetooth connections or network traffic.
- **Kernel-level sensor data**: No access to raw hardware interrupts, `/proc` entries for other processes, or system radio logs.

### Platform Throttling

Android aggressively throttles radio access to conserve battery:

| Radio | Throttle | Android Version | Bypass |
|-------|----------|----------------|--------|
| BLE scan starts | 5 per 30 seconds | Android 7+ | None without system privileges |
| BLE duty cycling | `LOW_LATENCY` ~100%, `BALANCED` ~3%, `LOW_POWER` ~0.5% | Android 8+ | Foreground service with `LOW_LATENCY` |
| BLE screen-off | Paused without ScanFilter | Android 8.1+ | Must register at least one ScanFilter |
| WiFi scans (foreground) | 4 per 2 minutes | Android 9+ | System API `setScanThrottleEnabled(false)` |
| WiFi scans (background) | 1 per 30 minutes | Android 8+ | Foreground service; passive scan listening (Android 10+) |
| Background location | Separate permission grant | Android 10+ | `ACCESS_BACKGROUND_LOCATION` + foreground service |

### Foreground Service Constraints (Android 14+)

Continuous scanning requires a foreground service with mandatory type declarations:

| Detection Protocol | Required FGS Type | Key Restriction |
|---|---|---|
| BLE scanning | `connectedDevice` | Must hold `BLUETOOTH_SCAN` |
| WiFi/Cellular/GNSS | `location` | Must hold `ACCESS_FINE_LOCATION` |
| Ultrasonic detection | `microphone` | **Cannot auto-start from boot** on Android 14+ |

A single service can declare multiple types (e.g., `connectedDevice|location|microphone`), but must hold all permissions for all types at `startForeground()` time. The `location` and `connectedDevice` types have no time limits, making them suitable for continuous scanning. The microphone restriction is the most impactful: ultrasonic detection cannot begin automatically after a reboot without user interaction.

### OEM Background Killing

The most insidious reliability problem is not from Android itself but from OEM customizations. Xiaomi (MIUI), Huawei (EMUI), Samsung (One UI), OnePlus (OxygenOS), and others aggressively kill background processes beyond what stock Android does. No programmatic solution exists -- users must manually exempt the app in their device's battery settings. The [dontkillmyapp.com](https://dontkillmyapp.com) project documents per-OEM workarounds.

---

## Protocol-by-Protocol Analysis

### 1. Bluetooth Low Energy (BLE)

**Feasibility: HIGH** -- the richest and most reliable detection protocol available to non-root Android apps.

#### What's Available

BLE advertisement data is the single richest data source available to a surveillance detection app. Every BLE device broadcasts advertisement packets that contain identifying information, and Android exposes nearly the complete packet contents through `ScanResult`/`ScanRecord`:

| Data | Available | Surveillance Detection Use |
|---|---|---|
| Device name | Yes | Pattern matching against 560+ known device name regexes |
| Manufacturer data | Yes | Keyed by 16-bit company ID (Apple 0x004C, Samsung 0x0075, Tile 0x00C7, Google 0x00E0, Nordic 0x0059) |
| Service UUIDs | Yes | Identifying specific services (e.g., Raven gunshot detector: 7 known UUIDs) |
| Raw advertisement bytes | Yes | Full payload for deep analysis |
| RSSI | Yes | Signal strength for distance estimation and proximity alerts |
| TX Power Level | Yes | When advertised; enables distance calculation |
| Connectable flag | Yes | Distinguishes beacons from interactive devices |

#### What Works Well

**Pattern-based device identification** is highly reliable. Surveillance equipment, police body cameras, trackers, and hacking tools all broadcast identifiable BLE signatures:

- **Body cameras**: Axon body cameras use Nordic Semiconductor BLE (manufacturer ID 0x0059). When activated (recording starts), the Axon Signal system spikes from ~1 advertisement per second to 20-50+ per second -- a dramatic and detectable behavioral change. This is a genuinely novel detection method based on real device behavior.
- **Trackers**: AirTag (Apple Find My UUID), Tile (Tile service UUID + manufacturer 0x00C7), Samsung SmartTag (Samsung Find My UUID) are identifiable by their service UUIDs.
- **Gunshot detectors**: ShotSpotter/Raven devices expose 7 specific BLE service UUIDs documented by GainSec security research (Device Info 0x180a, GPS 0x3100, Power 0x3200, Network 0x3300, Upload Stats 0x3400, Diagnostics 0x3500).
- **Hacking tools**: Flipper Zero (all firmware variants: unleashed, roguemaster, xtreme, momentum), Hak5 tools (Bash Bunny, LAN Turtle, USB Rubber Ducky, Key Croc, Shark Jack, Screen Crab), SDR tools (HackRF, PortaPack), RFID tools (Proxmark, ChameleonMini) all have known BLE name patterns.

**Tracker following detection** works by tracking BLE device sightings across user locations. A "distinct location" requires >5 minutes elapsed AND >50 meters distance (Haversine formula). Three or more distinct locations triggers a "following" alert. Possession detection (device likely in your bag) uses RSSI > -55 dBm with variance < 10.0. This is mathematically sound and practically useful.

**Flipper Zero BLE spam detection** tracks Apple accessory advertisements and Google Fast Pair advertisements in a 20-second sliding window. Thresholds are tuned conservatively (30 Apple ads or 25 Fast Pair ads or 12 unique name changes) with a confirmation window (2 detections within 30 seconds) and 3-minute cooldown.

#### Limitations

**MAC address randomization** is the primary challenge. Since Android 10, the local device's WiFi MAC is randomized per-network. More importantly for detection, many BLE devices rotate their own Resolvable Private Addresses (RPA). The workaround is generating stable composite identifiers from service UUIDs + manufacturer data pattern + device name + advertising payload hash. This works for devices with consistent advertising profiles but fails for devices that also rotate their advertising data.

**Duty cycling** limits detection cadence. On a sideloaded (non-system) app:
- Scan window: 25 seconds active, 5 seconds cooldown
- Maximum: 5 scan starts per 30-second window
- Screen-off: Requires registered ScanFilter to continue scanning
- Background scanning frequency reduced on Android 12+

A device that broadcasts infrequently might be missed during the 5-second cooldown. System/OEM installs get 60-second continuous scans with 1-second cooldowns, dramatically improving detection coverage.

**AirTag-specific detection is imprecise**. AirTags use Apple manufacturer data (0x004C), but so do AirPods, Apple Watches, MacBooks, and every other Apple device. AirTag-specific byte patterns (`isLikelyAirTag()`) help but are imperfect. Apple's Find My network protocol uses encrypted rotating identifiers specifically designed to resist third-party tracking -- which ironically makes detecting AirTags harder for a privacy app.

#### Verdict

BLE detection is the strongest pillar. The combination of rich advertisement data, well-documented device signatures, and proven tracker-following algorithms makes it the most practical surveillance detection protocol on Android. The primary risk is false positives from consumer devices that share manufacturer IDs or naming patterns with surveillance equipment.

---

### 2. WiFi

**Feasibility: MEDIUM** -- good for pattern matching, limited for attack detection.

#### What Works

**SSID pattern matching** is straightforward and reliable. Android's WiFi scan results include the full SSID and BSSID. A database of 600+ SSID regex patterns can identify:

- Flock Safety ALPR cameras
- Police technology (Axon, Motorola Solutions, L3Harris)
- Forensics tools (Cellebrite UFED, GrayKey, MSAB XRY, Oxygen Forensic)
- Smart home cameras (Ring, Nest, Wyze, Arlo, Eufy, Blink)
- WiFi hacking tools (WiFi Pineapple)
- Hidden camera default SSIDs (ipc-\*, camera-\*, wificam-\*)

**Evil twin detection** is partially feasible. Apps can see multiple access points broadcasting the same SSID with different BSSIDs. While this is the normal case for large WiFi deployments (mesh networks, enterprise APs), comparing signal strength patterns, OUI diversity, and sighting history can flag suspicious duplicates. The key challenge is false positive suppression -- extensive filtering is needed for common SSIDs (xfinitywifi, NETGEAR-Guest, etc.), multi-band routers, mesh systems, and apartment buildings.

**Network following detection** tracks BSSIDs across user locations. If the same access point MAC appears at locations separated by >1 mile and >5 minutes, it may be a mobile surveillance device. False positive handling includes: checking for mobile hotspot OUIs, commuter patterns (same AP near train stations), and public transit WiFi.

**OUI-based camera detection** identifies surveillance camera manufacturers (Hikvision, Dahua, Axis, Panasonic) by their MAC prefix. Combined with hidden SSID detection, this can identify surveillance camera clusters. Five or more cameras from surveillance-specific OUIs trigger an alert.

#### What Doesn't Work

**Deauth attack detection is NOT feasible** on stock Android. Detecting deauthentication frames requires WiFi monitor mode, which Android does not expose to apps. The indirect approach -- counting WiFi disconnection events and flagging rapid disconnects -- has an extremely high false positive rate because normal network instability, AP load balancing, and poor signal all cause frequent disconnects. The code is honest about this: "Stock Android CANNOT observe raw 802.11 deauth frames... has a HIGH FALSE POSITIVE RATE."

**Karma attack detection is NOT feasible**. Detecting a Karma attack (where a rogue AP responds to all probe requests) requires observing probe request/response frames, which are management frames invisible to the app layer. This is explicitly documented as a placeholder.

**Man-in-the-middle detection is NOT feasible** at the WiFi layer. Detecting ARP spoofing, SSL stripping, or traffic interception requires raw packet capture or network-level inspection, neither of which is available without root.

#### Scan Throttling Impact

The 4-scans-per-2-minutes limit (Android 9+) significantly constrains WiFi detection. At best, a sideloaded app gets a WiFi scan every 30 seconds. An evil twin that appears briefly between scans may be missed entirely. System/OEM installs can disable throttling via the hidden `setScanThrottleEnabled(false)` API and scan every 10 seconds.

One mitigation: Android 10+ apps can passively receive scan results from system-initiated scans or other apps' scans via `SCAN_RESULTS_AVAILABLE_ACTION`, without counting against the throttle. This provides "free" scan data but at unpredictable intervals.

#### Verdict

WiFi detection is strong for identifying known surveillance equipment by SSID/OUI patterns and detecting evil twin anomalies with extensive false positive filtering. It is weak for detecting active WiFi attacks (deauth, Karma, MITM) because Android does not expose raw 802.11 frames. The scan throttle is a meaningful constraint for sideloaded installs.

---

### 3. Cellular / IMSI Catcher Detection

**Feasibility: MEDIUM** -- useful heuristics, but cannot definitively confirm an IMSI catcher without privileged access.

#### The Core Challenge

IMSI catchers (StingRay, DRTbox, Hailstorm) work by impersonating legitimate cell towers and forcing target phones to connect. The attack happens entirely at the baseband/radio layer, which is isolated from the Android application processor. No app can observe the actual IMSI capture process, cipher suite negotiation, or signaling-layer messages.

What apps *can* observe are the side effects of an IMSI catcher's operation through the `TelephonyManager` API:

#### Available Data (Without Root)

| Data Point | API | Detection Use |
|---|---|---|
| Cell ID (CID/NCI) | `CellInfo.getCellIdentity()` | Unknown cell detection, suspicious ID patterns |
| LAC/TAC | `CellIdentity` | Anomalous area codes (StingRay often uses LAC 1) |
| MCC/MNC | `CellIdentity` | Test network codes (001-01, 999-99) should never appear |
| Network type | `TelephonyManager` / `CellInfo` subtype | Encryption downgrade (LTE/5G to 2G) |
| Signal strength | `CellSignalStrength` | Abnormal spikes (>25 dBm change in 5s) |
| PCI/PSC | `CellIdentity` | Physical cell identity consistency |
| ARFCN/EARFCN | `CellIdentity` | Frequency channel validation |
| Timing advance | `CellSignalStrength` | Distance estimation anomalies |
| Connection status | `CellInfo.isRegistered()` | Cell transition tracking |

#### Detection Methods and Their Reliability

**Suspicious MCC/MNC (High Confidence)**: ITU test network codes like 001-01, 999-99, 000-00 should never appear on real cellular networks. If observed, this is a strong indicator of a misconfigured or poorly-built IMSI catcher. However, sophisticated IMSI catchers use legitimate MCC/MNC codes copied from real carriers, rendering this check useless against well-built devices.

**Encryption Downgrade (High Confidence as Indicator)**: Detecting a network type change from 5G/4G/3G to 2G (GSM) is one of the strongest available indicators. 2G uses broken encryption (A5/1, crackable in real-time; or A5/0, no encryption at all). A forced downgrade in an area with known LTE coverage is genuinely suspicious. The limitation: apps cannot observe which specific cipher suite the baseband selected -- only that the network generation changed. Android 14 added modem-level null cipher rejection as a system toggle, but apps cannot query whether it's active without `MODIFY_PRIVILEGED_PHONE_STATE`.

**Signal Spike (Low-Medium Confidence)**: A >25 dBm signal strength change within 5 seconds could indicate a nearby high-power transmitter like a DRTbox (airplane-mounted IMSI catcher). However, entering/exiting buildings, turning corners, and normal urban propagation cause similar spikes. This must be corroborated with other indicators.

**Stationary Cell Change (Low Confidence)**: If the phone switches cell towers while the user is stationary, it *could* indicate a nearby fake tower. But normal network load balancing, 5G beam steering (which causes frequent handoffs -- a 120-second grace period is needed), and carrier-specific behavior (T-Mobile is documented as particularly aggressive with handoffs) make this unreliable alone.

**Trusted Cell Database**: Building a database of known-good cell towers (trusted after 5+ sightings with consistent parameters) allows detecting unknown cells in familiar areas. This is a simplified version of the approach validated by the CellGuard research (TU Darmstadt, RAID 2024), which compared observed cells against Apple's cell location database. The limitation: this provides no protection in unfamiliar areas where all cells are "unknown."

#### What Requires OEM Privileges

The most critical IMSI catcher capability -- proving that your identity was captured -- requires `READ_PRIVILEGED_PHONE_STATE` (OEM only):

| Capability | Sideload | OEM |
|---|---|---|
| Cell tower metadata | Yes | Yes |
| Network type changes | Yes | Yes |
| Signal strength history | Yes | Yes |
| IMEI/IMSI access | **No** | **Yes** |
| Silent SMS detection | **No** | **Yes** |
| Real-time callbacks | ~1-3s latency | Immediate |

Without IMEI/IMSI access, a sideloaded app can detect *indicators* of an IMSI catcher but cannot confirm that the device's identity was actually captured.

#### Academic Context

The history of Android-based IMSI catcher detection is one of abandoned projects:

| Project | Status | Why It Matters |
|---|---|---|
| **SnoopSnitch** (SRLabs) | Abandoned | Required root + Qualcomm diag interface for real radio frame analysis. Not updated for modern Android. |
| **AIMSICD** | Abandoned (2014-2016) | CellInfo-based anomaly detection. Similar approach to what Flock-You implements but without the false positive mitigation. |
| **CellGuard** (TU Darmstadt) | Active (iOS only, 2024) | Validates the cell tower database comparison approach on iOS using Apple's private frameworks. |

The CellGuard research is particularly relevant: it demonstrates that comparing observed cells against a known-good database is the most promising non-root approach, and it found meaningful detection accuracy for simulated IMSI catchers. Flock-You's trusted cell database implements a simplified version of this concept.

#### Verdict

Cellular detection provides genuinely useful heuristics, especially encryption downgrade detection and test MCC/MNC identification. The trusted cell database approach is validated by academic research. However, without root or OEM privileges, the app cannot access the baseband or confirm identity capture. Most single-indicator anomalies are correctly scored as LOW/INFO severity. The scoring system reflects honest confidence: only multi-indicator scenarios (downgrade + signal spike + unknown tower) reach HIGH/CRITICAL.

---

### 4. GNSS Spoofing & Jamming

**Feasibility: MEDIUM** -- academically validated but operationally noisy. Disabled by default for good reason.

#### Available Data

Android exposes substantial GNSS data through two callback interfaces:

**GnssStatus (API 24+, mandatory)**: Satellite count, C/N0 (carrier-to-noise density ratio, 0-63 dB-Hz), elevation, azimuth, constellation type (GPS, GLONASS, Galileo, BeiDou, QZSS, SBAS, IRNSS), ephemeris/almanac flags, used-in-fix status, carrier frequency (API 26+, dual-frequency on flagships since ~2018), baseband C/N0 (API 30+, most MediaTek unsupported).

**GnssMeasurements (API 24+, mandatory on API 29+ with 2016+ hardware)**: Raw pseudorange, carrier phase, Doppler shift, clock data. Over 90% of Android phones in use support raw GNSS measurements as of 2025. However, the depth of available data varies dramatically by chipset:

| Measurement | Qualcomm 835+ | MediaTek Helio | Samsung Exynos |
|---|---|---|---|
| C/N0 | Full | Full | Full |
| AGC | Usually available | Often unavailable | Variable |
| Pseudorange | Full | Often unsupported | Variable |
| Carrier phase | Flagships | Rarely | Flagships |
| Clock drift | Most reliable | Variable | Variable |
| Multi-frequency | 845+ (L1+L5) | High-end only | 990+ (L1+L5) |

#### Detection Methods

**C/N0 Uniformity Analysis (Academically Validated)**: The core spoofing detection method. A real GPS sky has satellites at different elevations and distances, producing C/N0 values that vary by 10-20 dB across the constellation. A single-transmitter spoofer produces artificially uniform signal levels (variance < 0.15 dB-Hz) because all "satellite" signals traverse the same path from one antenna. This approach was validated by the Stanford GPS Lab (Miralles et al., 2018) using Android raw GNSS measurements, and Google published an official developer guide recommending it.

**Cross-Constellation Consistency**: If GPS, GLONASS, Galileo, and BeiDou all show identical signal characteristics (within 2 dB-Hz), it's likely a single transmitter. Real satellites from different constellations have different orbital geometries and signal characteristics. This is genuinely hard for a spoofer to fake convincingly across all constellations simultaneously.

**Kremlin-Circle Pattern**: A documented real-world attack where GPS signals are spoofed to show a different location while GLONASS remains unaffected (because Russia doesn't want to jam its own navigation system). The detection checks for a 15 dB-Hz difference between GPS and GLONASS signal quality.

**Jamming Detection**: Requires satellite count drop of at least 10, signal drop of at least 10 dB, AND no more than 8 visible satellites. The code explicitly prevents claiming "jamming" when 30+ satellites are visible -- a bug fix that illustrates the importance of conservative thresholds.

**Clock Anomaly**: Uses GnssMeasurement clock bias/drift data to detect timing discontinuities. Maximum allowed drift: 1ms between measurements. Requires chipset-level GnssMeasurementsEvent support, effectively disabling this check on unsupported hardware.

#### Why It's Disabled by Default

**Urban false positives are severe.** Multipath propagation (signals bouncing off buildings, vehicles, water) in urban canyons produces C/N0 and AGC patterns that can mimic both spoofing and jamming signatures. The confirmation period system mitigates this (6-10 detections within 2-5 minutes required, depending on anomaly type), but urban environments still generate excessive alerts.

**Indoor false positives**: Weak or absent GNSS signal indoors is normal, not jamming. The environment detection system auto-detects indoor conditions (signal < -85 dBm) and suppresses reporting for 5 minutes.

**Chipset variability**: Detection quality depends heavily on chipset capabilities. Budget phones with MediaTek chipsets may lack AGC, raw measurements, and multi-frequency support, making meaningful analysis impossible. The code adapts by disabling checks that require unavailable data, but this reduces detection confidence.

#### Galileo OSNMA: Future Promise, Not Current Reality

Galileo's Open Service Navigation Message Authentication (OSNMA) became operational on July 24, 2025 -- the first GNSS system to offer authenticated navigation messages. However, OSNMA requires hardware-level support in the GNSS receiver chipset. It cannot be added via software update. As of early 2026, consumer Android phones with OSNMA-capable receivers are not widely available. Including it as a future confirmation method is appropriate forward planning, but it is not a current detection capability.

#### Verdict

GNSS spoofing detection via C/N0 uniformity analysis is academically sound and validated by both Stanford research and Google's official guidance. Cross-constellation consistency checks are genuinely difficult for attackers to defeat. However, the urban false positive rate makes this impractical as an always-on detection in cities, which is where surveillance is most likely to occur. The decision to disable by default and only enable in PARANOID mode is the correct engineering tradeoff. Real-world utility is highest in open-sky environments (highways, rural areas, borders) where multipath is minimal.

---

### 5. Ultrasonic Beacon Detection

**Feasibility: MEDIUM-LOW** -- technically possible, but the threat landscape has shifted.

#### How It Works

Android's `AudioRecord` API provides full microphone access at up to 48 kHz sample rate. At the standard 44,100 Hz, the Nyquist frequency is 22,050 Hz, covering the target detection range of 18,000-22,000 Hz. The detection uses the Goertzel algorithm (more efficient than FFT for targeting specific frequencies) at 100 Hz steps across the near-ultrasonic range, with a threshold of 30 dB above the noise floor and a persistence requirement of 5 consecutive detections before alerting.

#### Hardware Reality

Phone microphones can capture near-ultrasonic frequencies, but with significant caveats:

- MEMS microphones in smartphones are optimized for human speech (100 Hz - 8 kHz). Frequency response degrades significantly above 15-18 kHz.
- Android's Compatibility Test Suite (CTS) includes near-ultrasound tests at 18.5 kHz and 19.5 kHz, confirming Google expects devices to handle these frequencies.
- At 20-22 kHz, sensitivity drops 10-20 dB below mid-frequency response. Detection requires the source to be relatively close and loud.
- **Device variability is extreme**: Pixel devices tend to have good near-ultrasonic response; budget devices may have poor response above 16 kHz.
- Concurrent microphone access: If another app holds the microphone, AudioRecord initialization fails silently.

#### The Threat Landscape in 2025-2026

The ultrasonic tracking ecosystem has contracted significantly:

| Technology | Status (2025-2026) | Notes |
|---|---|---|
| **SilverPush** (Unique Audio Beacon) | **Abandoned** | Pivoted to AI video analytics after FTC pressure (2016) |
| **Alphonso** (ACR) | **Active** but different | 250+ apps, 40M+ smart TVs. Uses audio *fingerprinting* at audible frequencies, not ultrasonic beacons. Different detection problem. |
| **Shopkick** (retail beacons) | **Contracting** | Still deployed in some retailers but significantly reduced from 2016-2018 peak |
| **Signal360** | **Active** | Mall/airport location advertising, reduced deployment |
| **LISNR** | **Active** | Ultrasonic data transfer (authentication, payments), legitimate use cases |
| **Samba TV / Inscape** | **Active** | Smart TV ACR in Samsung/Vizio/LG TVs, primarily audio fingerprinting |

A 2017 TU Braunschweig study found 234 Android apps with ultrasonic tracking SDKs. Google subsequently removed many from the Play Store. The threat is real but low-prevalence in 2025-2026.

#### False Positive Sources

Near-ultrasonic frequencies are produced by many non-surveillance sources:
- LCD backlight PWM circuits (20-25 kHz)
- Switching power supplies (20-100 kHz)
- HVAC ultrasonic humidifiers
- Dog/pest deterrent devices (17-25 kHz)
- Electric vehicle pedestrian warning systems (AVAS, 17-20 kHz)
- CRT monitor horizontal scan (15.75 kHz -- rare now)
- Keys jingling, metal-on-metal contact

The persistence requirement (5 consecutive detections) and frequency stability check (true beacons are precise within 10 Hz; noise is erratic) help distinguish real beacons from environmental sources.

#### Android 14 Boot Restriction

The most impactful limitation: `microphone`-type foreground services cannot start from `BOOT_COMPLETED` on Android 14+. Ultrasonic detection cannot auto-start after a reboot. Users must interact with the app to begin microphone monitoring. This is a significant practical limitation for a security tool that should ideally "just work."

#### Verdict

Ultrasonic beacon detection addresses a documented real-world threat (SilverPush, Alphonso) that was serious enough to prompt FTC investigation. The technical implementation (Goertzel algorithm, persistence filtering, frequency stability checks, encrypted audio buffers) is sound. However, the threat has diminished since 2016-2018, the primary remaining threat (audio fingerprinting) operates at audible frequencies outside the detection range, and false positive sources are numerous. Including ultrasonic detection as an optional protocol is appropriate; it should not be positioned as a primary detection capability.

---

### 6. RF Spectrum Analysis

**Feasibility: NONE without external hardware.**

#### The Hard Truth

Android provides absolutely no API for direct RF spectrum analysis. There is no mechanism to:
- Access raw I/Q samples from any radio
- Put any radio into a wideband receive mode
- Perform frequency sweeps
- Observe spectrum occupancy outside BLE/WiFi/cellular bands

The only RF data available is the high-level abstractions (BLE RSSI, WiFi RSSI, cellular signal strength), which are crude proxies for actual spectrum analysis.

#### WiFi-as-Proxy: What It Can Do

Without external hardware, RF "detection" is limited to observing WiFi scan results as a proxy:

- **Drone WiFi hotspots**: Many consumer drones (DJI, Parrot, Skydio, Autel, Yuneec) create WiFi access points for controller communication. Matching against 20+ drone manufacturer OUIs and SSID patterns is feasible. **Limitation**: Drones using proprietary protocols (DJI OcuSync, analog FPV) are invisible.
- **Surveillance camera density**: Counting Hikvision/Dahua/Axis OUIs in scan results. 5+ cameras = alert. **Limitation**: High false positive rate in commercial/office buildings with legitimate security cameras.
- **Jammer inference**: Sustained total WiFi signal loss could indicate jamming. **Limitation**: Cannot distinguish from entering a building or basement.

#### What Requires External Hardware

| Detection | Required Hardware | Frequency Range |
|---|---|---|
| Sub-GHz scanning (315/433/868/915 MHz) | Flipper Zero (CC1101) or SDR | 300-928 MHz |
| Wideband spectrum analysis | RTL-SDR (~$25) or HackRF (~$350) | 24 MHz - 6 GHz |
| Hidden transmitter detection | Specialized RF detector | Varies |
| True jamming characterization | SDR with appropriate antenna | 800 MHz - 6 GHz |

USB OTG SDR devices (RTL-SDR, HackRF, Airspy) work with Android via apps like RF Analyzer, but require a physical adapter, drain the battery rapidly, and are impractical for continuous background monitoring.

Flipper Zero integration via USB serial or BLE can provide sub-GHz scan data (CC1101: 300-348/387-464/779-928 MHz), but the BLE link bandwidth limits real-time data resolution, and the official Flipper Android app is management-focused rather than data-streaming. Custom Flipper Application Package (FAP) code on the Flipper side and custom BLE serial protocol handling on the Android side would be required for meaningful real-time RF data streaming.

#### Verdict

RF spectrum detection is the honest weakest link. Without external hardware, it provides only crude WiFi-based proxy analysis with high false positive rates. This limitation cannot be overcome through software -- it's a hardware constraint of the Android platform. Positioning RF detection as requiring Flipper Zero or SDR hardware, and being transparent about the WiFi-proxy limitations, is the only honest approach.

---

### 7. Satellite / Non-Terrestrial Network (NTN)

**Feasibility: LOW** -- forward-looking protocol with minimal current real-world deployment.

#### Context

3GPP Release 17 introduced Non-Terrestrial Network (NTN) support, enabling cellular connectivity via satellites. T-Mobile's Starlink Direct-to-Cell service launched SMS-only in July 2025. Android 14+ added `SatelliteManager` APIs. This creates a new potential attack surface: forcing a phone onto a satellite link (which may have different security properties than terrestrial) or spoofing NTN parameters.

#### Available Data

- `TelephonyCallback.DisplayInfoListener` (API 31+): Detects NTN overlay type changes
- `ServiceState.getNetworkRegistrationInfoList()` (API 31+): NTN registration keywords
- `CellIdentityNr.getNrarfcn()`: NTN band identification (L-Band: 422,000-434,000; S-Band: 434,001-440,000)
- `SatelliteManager` (API 34+, via reflection): `requestIsEnabled()`, `requestIsSupported()`, `requestIsProvisioned()`
- Application-layer RTT: HTTP HEAD to DNS endpoints for timing analysis (LEO 20-80ms, MEO 100-250ms, GEO >250ms, ground-based spoofing <15ms)

#### Detection Methods

- **Unexpected satellite connection**: Terrestrial coverage available but phone connects via satellite
- **Forced handoff**: Suspicious handoff to satellite when not in a remote area
- **Timing anomaly**: RTT < 15ms on a claimed satellite connection indicates ground-based spoofing
- **NTN band validation**: NRARFCN checked against valid NTN frequency ranges
- **Provider allowlisting**: T-Mobile Starlink and Skylo NTN patterns are whitelisted to prevent false positives for legitimate services

#### Verdict

This is almost entirely forward-looking. NTN satellite connectivity is in its infancy (SMS-only, limited geographic coverage). Most users will never trigger NTN detections. The code appropriately handles the fringe-coverage transition false positive case (briefly entering/exiting Starlink coverage areas). As NTN deployment expands, this protocol will become increasingly relevant. For now, it represents future-proofing rather than practical current utility.

---

## Cross-Protocol Correlation

The most powerful detections come from correlating signals across multiple protocols simultaneously. Single-protocol anomalies are often ambiguous; multi-protocol coincidences are much more suspicious.

| Correlation Pattern | Protocols | Multiplier | Rationale |
|---|---|---|---|
| **IMSI Catcher Combo** | Cellular + GNSS | 2.5x | Cell anomaly + GPS spoofing within 5 min = classic IMSI catcher + GPS jamming combo |
| **Following Pattern** | BLE (same device at 3+ locations) | 1.5x | Persistent tracker across multiple user locations |
| **Coordinated Surveillance** | Multiple protocols within 100m and 10 min | 2.0x | Multiple surveillance indicators converging |
| **Tracker Network** | BLE (multiple trackers, same manufacturer) | 1.8x | Multiple trackers from one manufacturer on same target |
| **Spatial Clustering** | Any (3+ devices within 200m) | 1.3x | Concentration of surveillance devices in small area |

Cross-protocol correlation is where an Android-based approach has a unique advantage over single-purpose detection tools. A dedicated IMSI catcher detector sees only cellular. A tracker detector sees only BLE. An app monitoring all seven protocols simultaneously can identify coordinated surveillance that no single-protocol tool would flag.

---

## The Privilege Gap

The three-tier privilege architecture (sideload/system/OEM) maps directly to Android's actual capability boundaries:

| Capability | Sideload | System | OEM | Impact on Detection |
|---|---|---|---|---|
| WiFi scan rate | 4/2 min | Unrestricted | Unrestricted | Evil twin detection speed |
| BLE scan duty cycle | 25s on / 5s off | 60s on / 1s off | Continuous | Tracker detection coverage |
| Real WiFi MACs | No (randomized) | Yes (`PEERS_MAC_ADDRESS`) | Yes | AP tracking accuracy |
| IMEI/IMSI access | No | No | Yes | **IMSI capture proof** |
| Silent SMS detection | No | No | Yes | Targeted surveillance detection |
| Process persistence | Standard | Enhanced | Persistent | Long-term monitoring reliability |
| WiFi throttle bypass | No | Yes (hidden API) | Yes | Scan cadence |

**The critical gap is IMEI/IMSI access.** Without OEM privileges, the app can detect indicators of IMSI catcher operation (encryption downgrade, signal anomalies, unknown cells) but cannot confirm that the device's identity was actually captured. This is the difference between "something suspicious is happening with the cell network" and "an IMSI catcher just stole your phone's identity."

For a sideloaded app, the most impactful limitation is BLE duty cycling and WiFi scan throttling, which reduce detection cadence and create time windows where threats can be missed.

---

## False Positive Economics

The fundamental tradeoff in surveillance detection is false positives vs false negatives. For a privacy/security tool:

- **False negatives are dangerous**: Missing a real IMSI catcher or tracker can compromise someone's safety.
- **False positives are corrosive**: Constant alerts for harmless devices destroy user trust and cause "alert fatigue," leading users to disable the tool entirely.

The scoring system addresses this with conservative defaults:

```
threat_score = base_likelihood x impact_factor x confidence

Example: Single weak BLE signal matching a body camera name pattern
  base_likelihood = 85 (name match)
  impact_factor = 1.0 (body camera)
  confidence = 0.5 (base) - 0.3 (single indicator) - 0.1 (weak signal) = 0.1 (minimum clamp)
  threat_score = 85 x 1.0 x 0.1 = 8.5 (INFO severity)

Example: Cellular encryption downgrade + signal spike + unknown tower, while stationary
  base_likelihood = 90 (test MCC/MNC + downgrade)
  impact_factor = 2.0 (STINGRAY_IMSI)
  confidence = 0.5 + 0.3 (cross-protocol) + 0.2 (persistence) + 0.1 (strong signal) = 1.0 (capped)
  threat_score = 90 x 2.0 x 1.0 = 100 (CRITICAL)
```

This means:
- A single weak signal matching one pattern = INFO (logged, not alerted)
- Multiple strong corroborating indicators = CRITICAL (immediate alert)
- The minimum confidence clamp (0.1) ensures nothing is completely invisible, but single-indicator detections stay at low severity

The four protection presets formalize this:
- **ESSENTIAL** (1.5x threshold): Only IMSI catchers, body cameras, Flock Safety. Minimal false positives.
- **BALANCED** (1.0x default): All default patterns. Moderate false positive rate.
- **PARANOID** (0.7x sensitive): All patterns including GNSS and RF. Higher false positive rate accepted.
- **CUSTOM**: User-defined balance.

---

## Comparison with Prior Art

| Tool | Platform | Root Required | Active | Approach | Limitations |
|---|---|---|---|---|---|
| **SnoopSnitch** (SRLabs) | Android (Qualcomm only) | Yes | Abandoned | Qualcomm diag interface, raw radio frames | Requires root + specific chipset. Not updated for modern Android. |
| **AIMSICD** | Android | Partial | Abandoned (2014-2016) | CellInfo anomalies, tower database | No false positive mitigation. Abandoned. |
| **CellGuard** | iOS only | No | Active (2024) | Apple cell location database comparison | iOS only. Relies on Apple's private database. |
| **Haven** (Guardian Project) | Android | No | Maintained | Physical surveillance (camera, accelerometer, microphone) | Different threat model (room monitoring vs radio detection) |
| **AirGuard** | Android | No | Active | AirTag/tracker detection only | Single protocol (BLE trackers only) |
| **Flock-You** | Android | No (sideload) / Partial (system/OEM) | Active | 7 protocols, 75+ device signatures, cross-protocol correlation | Heuristic-based; cannot access baseband |

The key differentiator is breadth: no other tool attempts to correlate across BLE, WiFi, cellular, GNSS, audio, RF, and satellite protocols simultaneously. Each individual protocol has limitations, but the combination provides detection capability that no single-protocol tool can match.

---

## What Android Gets Right

Despite the limitations, Android is a surprisingly good platform for surveillance detection:

1. **BLE advertisement data is rich.** Nearly the complete advertisement packet is exposed, including manufacturer-specific data keyed by company ID. This is the foundation for reliable device identification.

2. **Raw GNSS measurements are available.** Over 90% of Android phones support raw GNSS data (mandatory since API 29 on 2016+ hardware). This enables academically-validated spoofing detection techniques that were previously limited to specialized receivers.

3. **Cellular metadata is substantial.** Cell ID, LAC/TAC, MCC/MNC, signal strength, timing advance, and network type are all available without root. This is enough for meaningful IMSI catcher heuristics.

4. **Foreground services enable continuous monitoring.** The `location` and `connectedDevice` foreground service types have no time limits, enabling indefinite scanning.

5. **Multi-radio simultaneous access.** An Android app can monitor BLE, WiFi, cellular, GNSS, and microphone simultaneously. This cross-protocol capability is unique to smartphones.

6. **Privacy-first architecture is natural.** Android's sandboxing means an on-device-only detection app inherently cannot leak data to cloud services (assuming no network permissions are granted). The security model supports the privacy threat model.

---

## Hard Limitations That Cannot Be Overcome

These are fundamental platform constraints, not engineering challenges:

1. **No raw 802.11 frame access.** Without monitor mode, WiFi attack detection (deauth, Karma, MITM) is limited to indirect heuristics. This cannot be fixed without rooting the device or using external hardware (ESP8266/ESP32 deauth detector).

2. **No baseband access.** The cellular modem is a black box. IMSI capture, cipher suite selection, silent SMS, and signaling-layer events are invisible. Even root cannot access the baseband on most devices. Only Qualcomm devices with root + specific diag drivers (as used by SnoopSnitch) can observe raw radio frames.

3. **No RF spectrum access.** Android is not an SDR. Sub-GHz detection (garage door frequencies, car key fobs, surveillance transmitters) requires external hardware (Flipper Zero, RTL-SDR, HackRF).

4. **MAC randomization.** Both the local device and many remote BLE devices rotate MAC addresses. Stable tracking requires alternative fingerprinting (advertising payload analysis), which is imperfect.

5. **Scan throttling.** WiFi (4 scans/2 min) and BLE duty cycling create detection gaps where threats can be present but invisible. Cannot be bypassed without system privileges.

6. **OEM background killing.** Aggressive battery optimization by device manufacturers (Xiaomi, Huawei, Samsung, OnePlus) can kill the scanning service with no reliable programmatic workaround.

7. **Microphone boot restriction.** Ultrasonic detection cannot auto-start after reboot on Android 14+.

---

## The Honest Verdict

**Android-based surveillance detection is a legitimate and useful capability with well-defined boundaries.**

It is **not** a replacement for professional TSCM (Technical Surveillance Countermeasures) equipment. It cannot detect a properly-built IMSI catcher with certainty. It cannot find hidden RF transmitters without external hardware. It cannot observe WiFi attack frames.

It **is** effective at:
- Identifying known surveillance equipment (body cameras, ALPR cameras, forensics tools) by their radio signatures
- Detecting BLE trackers following you across locations
- Flagging cellular anomalies consistent with IMSI catchers
- Detecting GNSS spoofing in open-sky environments
- Correlating multi-protocol surveillance indicators that single-purpose tools miss

The greatest strength is **breadth and correlation**: monitoring 7 protocols simultaneously on a device the user already carries. The greatest weakness is **depth**: each protocol provides heuristic indicators rather than definitive proof, constrained by Android's security sandbox.

For journalists, activists, lawyers, and privacy-conscious individuals, this represents a meaningful step up from no detection capability -- provided users understand what it can and cannot tell them. The most important design decision is **honest communication**: scoring anomalies conservatively, labeling feasibility clearly, disabling high-false-positive protocols by default, and never claiming certainty where only probability exists.

---

## References & Sources

### Academic Research
- Miralles, D. et al. (2018). "Android Raw GNSS Measurements as the New Anti-Spoofing and Anti-Jamming Solution." Stanford GPS Lab. [Paper](https://web.stanford.edu/group/scpnt/gpslab/pubs/papers/MirallesIONGNSS2018Android_AntiSpoof.pdf)
- TU Darmstadt (2024). "CellGuard: Busting Rogue Base Stations." RAID 2024. [Paper](https://dl.acm.org/doi/fullHtml/10.1145/3678890.3678898)
- TU Braunschweig (2017). "Privacy Threats through Ultrasonic Side Channels on Mobile Devices." IEEE EuroS&P. [Paper](https://ieeexplore.ieee.org/document/7961150)
- NDSS (2025). "Detecting IMSI-Catchers by Characterizing Identity Exposing Messages." [Paper](https://www.ndss-symposium.org/wp-content/uploads/2025-1115-paper.pdf)
- Northeastern University (2023). "Multi-feature GNSS Spoofing Detection on Android." [Thesis](https://repository.library.northeastern.edu/files/neu:ms39s814q/fulltext.pdf)
- MDPI Electronics (2025). "GNSS Spoofing Modeling with Android Raw Data." [Paper](https://www.mdpi.com/2079-9292/14/5/898)

### Android Developer Documentation
- [Detect GNSS Jamming and Spoofing](https://developer.android.com/develop/sensors-and-location/sensors/gnss-spoof-jam)
- [Raw GNSS Measurements](https://developer.android.com/develop/sensors-and-location/sensors/gnss)
- [BLE Scanning](https://developer.android.com/develop/connectivity/bluetooth/ble/find-ble-devices)
- [Bluetooth Permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
- [WiFi Scanning Overview](https://developer.android.com/develop/connectivity/wifi/wifi-scan)
- [WiFi RTT](https://developer.android.com/develop/connectivity/wifi/wifi-rtt)
- [Foreground Service Types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [Application Sandbox](https://source.android.com/docs/security/app-sandbox)
- [SELinux Concepts](https://source.android.com/docs/security/features/selinux/concepts)
- [Disable 2G](https://source.android.com/docs/security/features/cellular-security/disable-2g)
- [Near Ultrasound CTS Tests](https://source.android.com/docs/compatibility/cts/near-ultrasound)

### Security Research & Tools
- GainSec: Raven/ShotSpotter BLE vulnerability research
- SRLabs: [SnoopSnitch](https://play.google.com/store/apps/details?id=de.srlabs.snoopsnitch)
- CellularPrivacy: [AIMSICD](https://github.com/CellularPrivacy/Android-IMSI-Catcher-Detector)
- DeFlock: [deflock.me](https://deflock.me)
- EFF: IMSI catcher documentation
- Don't Kill My App: [dontkillmyapp.com](https://dontkillmyapp.com)

### Industry & Standards
- EU Agency for the Space Programme: [Galileo OSNMA](https://www.euspa.europa.eu/newsroom-events/news/introducing-new-galileo-authentication-service-osnma-join-webinar-september)
- Galileo OSNMA Operational (August 2025): [EU Commission](https://defence-industry-space.ec.europa.eu/galileos-osnma-authentication-service-now-operational-2025-08-25_en)
- Bluetooth SIG: Assigned numbers and company identifiers
- IEEE: OUI database (standards-oui.ieee.org)
- FCC: ID search (fccid.io)
- Knowles: [MEMS Microphone Frequency Response](https://www.knowles.com/docs/default-source/default-document-library/frequency-response-and-latency-of-mems-microphones---theory-and-practice.pdf)
- Argenox: [BLE Address Types](https://argenox.com/library/bluetooth-low-energy/demystifying-ble-addresses/)
