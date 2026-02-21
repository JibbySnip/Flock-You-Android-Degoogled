# WEA, CMAS, SIB & Cell Broadcast in Android: Deep Dive

## Table of Contents

1. [WEA/CMAS Architecture in Android (AOSP)](#1-weacmas-architecture-in-android-aosp)
2. [SIB (System Information Block) Details](#2-sib-system-information-block-details)
3. [Android API and Framework Components](#3-android-api-and-framework-components)
4. [Alert Categories and Channel Mappings](#4-alert-categories-and-channel-mappings)
5. [Security and Privacy Implications](#5-security-and-privacy-implications)
6. [Detection/Monitoring Possibilities](#6-detectionmonitoring-possibilities)
7. [Recent Changes and Developments](#7-recent-changes-and-developments)
8. [Relevance to Surveillance Detection (Flock-You Context)](#8-relevance-to-surveillance-detection-flock-you-context)
9. [Sources](#9-sources)

---

## 1. WEA/CMAS Architecture in Android (AOSP)

### Overview

Wireless Emergency Alerts (WEA) is the U.S. implementation of the Commercial Mobile Alert System (CMAS), which is itself part of the broader 3GPP Public Warning System (PWS). The system uses **Cell Broadcast Service (CBS)** -- a one-to-many messaging mechanism defined in 3GPP standards -- to deliver emergency alerts from cell towers to all compatible mobile devices within a geographic area, without requiring individual addressing or a data connection.

### End-to-End Message Flow

The complete message path from alert originator to user notification:

```
Alert Originator (FEMA/NWS/AMBER)
    |
    v
Federal Alert Gateway (IPAWS)
    |
    v
CMSP Gateway (Carrier infrastructure)
    |
    v
Cell Broadcast Center (CBC)
    |  [SBc interface / N50 interface (5G)]
    v
MME (4G) / AMF (5G)
    |  [S1-MME / N2 interface]
    v
eNodeB (4G) / gNB (5G)
    |  [Over-the-air SIB broadcast]
    v
UE Modem (baseband processor)
    |  [RIL unsolicited response]
    v
Android RIL Daemon (rild)
    |  [RIL_UNSOL_RESPONSE_NEW_BROADCAST_SMS]
    v
InboundSmsHandler (framework)
    |
    v
CellBroadcastService (CBS module - APEX)
    |  [Decoding, geofencing, dedup]
    v
CellBroadcastReceiver (system app)
    |  [Display, notification, sound, vibration]
    v
User sees alert on screen
```

### CellBroadcast APEX Module (`com.android.cellbroadcast`)

Since Android 11 (API 30), the CellBroadcast functionality has been packaged as a **Mainline module** in a single APEX file (`com.android.cellbroadcast`). This is updateable via Google Play system updates independent of full Android OS releases. The module contains two primary components:

**CellBroadcastService** (in `packages/modules/CellBroadcastService/`):
- Cell Broadcast SMS decoding (GSM/CDMA PDU parsing)
- WEA 3.0 device-based geofencing (geographic polygon/circle matching)
- Message duplication checking (using serial number + PLMN + LAC + CID as composite key)
- Broadcasting intents to the CellBroadcastReceiver app
- Key classes: `CellBroadcastHandler.java`, `GsmCellBroadcastHandler.java`, `CdmaCellBroadcastHandler.java`

**CellBroadcastReceiver** (in `packages/apps/CellBroadcastReceiver/`):
- Default system app for handling emergency and non-emergency alerts
- Presents information to end users based on carrier and regional regulations
- Manages alert display (full-screen dialog, notification), audio (alert tone), and vibration
- Key classes: `CellBroadcastAlertService.java`, `CellBroadcastAlertDialog.java`, `CellBroadcastReceiver.java`, `CellBroadcastListActivity.java`

### RIL (Radio Interface Layer) Processing

The modem (baseband processor) receives the over-the-air SIB message and delivers it to Android through the RIL:

1. The modem decodes the SIB (SIB12 in LTE, SIB8 in 5G NR) and extracts the CBS PDU
2. The vendor RIL implementation generates an unsolicited response `RIL_UNSOL_RESPONSE_NEW_BROADCAST_SMS`
3. A wakelock is acquired to prevent the device from sleeping during processing
4. The response is dispatched over a socket to the Java-side `RIL.java`
5. The framework's `InboundSmsHandler` receives the cell broadcast PDU
6. The PDU is forwarded to the CellBroadcastService module for processing

### Carrier Configuration

Carrier-specific behavior is controlled through `CarrierConfigManager` keys:
- Which channels to enable/disable
- Alert sound behavior (default ringtone vs. specific tones)
- Whether to show alerts for specific categories
- Geofencing behavior
- Language preferences

---

## 2. SIB (System Information Block) Details

### LTE (4G) SIBs for Emergency Alerts (3GPP TS 36.331)

In LTE, the base station (eNodeB) broadcasts System Information Blocks as part of RRC (Radio Resource Control) signaling. Three SIBs are dedicated to PWS:

| SIB Type | Purpose | Content |
|----------|---------|---------|
| **SIB10** | ETWS Primary Notification | Warning type (earthquake/tsunami/combined/test/other), emergency user alert flag, popup flag. Small (~50 bytes), broadcast immediately. |
| **SIB11** | ETWS Secondary Notification | Full warning message text (up to 9600 bytes via segmentation, max 64 segments). Contains message identifier, serial number, data coding scheme, warning message content. |
| **SIB12** | CMAS Notification | Full CMAS/WEA alert message. Contains message identifier (channel), serial number, warning message segments, data coding scheme. Supports concurrent multiple warnings. |

**Key characteristics:**
- SIB10/11/12 are **not** part of the normal system information scheduling window. They are broadcast immediately upon reception from the core network.
- A **paging indication** (with ETWS/CMAS flag) is sent first to wake up idle-mode UEs so they can acquire the SIB
- SIBs are broadcast on the DL-SCH (Downlink Shared Channel) within the BCCH (Broadcast Control Channel)
- Messages can be segmented: up to 64 segments per message for large warnings
- SIB broadcasts are **unauthenticated** -- this is the fundamental security weakness

### 5G NR SIBs for Emergency Alerts (3GPP TS 38.331)

In 5G NR, the SIB numbering was reorganized:

| SIB Type | Purpose | Equivalent LTE SIB |
|----------|---------|---------------------|
| **SIB6** | ETWS Primary Notification | SIB10 |
| **SIB7** | ETWS Secondary Notification | SIB11 |
| **SIB8** | CMAS/WEA Notification | SIB12 |

**5G NR-specific behavior:**
- UEs in both `RRC_IDLE` and `RRC_INACTIVE` states monitor paging occasions for ETWS/CMAS indications
- Upon receiving a paging message with ETWS/CMAS indication, the UE acquires the corresponding SIB **without** waiting for the next modification period (immediate acquisition)
- The gNB (5G base station) uses the `schedulingInfo2` in SIB1 to indicate SIB6/7/8 scheduling
- 5G NR supports improved area targeting compared to LTE

### ETWS SIB Details

ETWS (Earthquake and Tsunami Warning System, originating from Japan's J-Alert) uses a two-phase notification:

1. **Primary Notification** (SIB10/SIB6): Extremely fast delivery (~4 seconds from earthquake detection). Contains only the warning type (no text). The phone immediately displays a pre-stored alert message. Fields include:
   - `messageIdentifier` (4352-4356)
   - `serialNumber` (with emergency user alert and popup activation bits)
   - `warningType` (earthquake/tsunami/combined/test/other)

2. **Secondary Notification** (SIB11/SIB7): Carries the full text message with detailed warning information. May arrive seconds to minutes after the primary notification. Supports segmentation for longer messages.

### 3GPP Specifications

| Specification | Title | Relevance |
|---------------|-------|-----------|
| **TS 23.041** | Technical realization of Cell Broadcast Service (CBS) | Core CBS specification: message format, identifiers, protocols |
| **TS 23.038** | Alphabets and language-specific information | Data coding scheme for CBS messages |
| **TS 22.268** | PWS Stage 1 requirements | Functional requirements for Public Warning Systems |
| **TS 36.331** | LTE RRC | SIB10/11/12 definition for LTE |
| **TS 38.331** | NR RRC | SIB6/7/8 definition for 5G NR |
| **TS 29.168** | SBc interface (CBC to MME) | Cell Broadcast Center interface |
| **TS 23.401** | GPRS enhancements for E-UTRAN | PWS procedures in LTE core |
| **TS 23.501** | 5GS architecture | PWS procedures in 5G core |

---

## 3. Android API and Framework Components

### `SmsCbMessage` Class

Path: `frameworks/base/telephony/java/android/telephony/SmsCbMessage.java`

The primary class representing a cell broadcast message. Key fields:

| Field | Type | Description |
|-------|------|-------------|
| `mMessageFormat` | int | `MESSAGE_FORMAT_3GPP` (GSM/UMTS/LTE) or `MESSAGE_FORMAT_3GPP2` (CDMA) |
| `mGeographicalScope` | int | Cell-wide immediate, PLMN-wide, LA/TA-wide, or cell-wide normal |
| `mSerialNumber` | int | 16-bit serial number (includes GS, message code, update number) |
| `mServiceCategory` | int | Message Identifier (channel number, e.g. 4370 for Presidential) |
| `mLanguage` | String | ISO-639 language code |
| `mBody` | String | Message text content |
| `mPriority` | int | `MESSAGE_PRIORITY_NORMAL` or `MESSAGE_PRIORITY_EMERGENCY` |
| `mEtwsWarningInfo` | SmsCbEtwsInfo | ETWS-specific info (warning type, emergency/popup flags) |
| `mCmasWarningInfo` | SmsCbCmasInfo | CMAS-specific info (severity, urgency, certainty, etc.) |
| `mLocation` | SmsCbLocation | PLMN, LAC, CID information |
| `mSlotIndex` | int | SIM slot |
| `mReceivedTimeMillis` | long | Timestamp of reception |
| `mDataCodingScheme` | int | CBS DCS as per 3GPP TS 23.038 |
| `mMaximumWaitTimeSec` | int | WEA 3.0 maximum wait time for geofencing |
| `mGeometries` | List\<Geometry\> | WEA 3.0 geographic polygons/circles |

Key methods:
- `isEmergencyMessage()` -- returns true if priority is `MESSAGE_PRIORITY_EMERGENCY`
- `isEtwsMessage()` -- true for ETWS channels (4352-4356)
- `isCmasMessage()` -- true for CMAS channels (4370-4399+)
- `getServiceCategory()` -- returns the channel/message identifier
- `getGeometries()` -- WEA 3.0 geofencing polygons

### `SmsCbCmasInfo` Class

Path: `frameworks/base/telephony/java/android/telephony/SmsCbCmasInfo.java`

Contains CMAS Type 1 elements for warning notifications. Fields:

| Field | Values | Description |
|-------|--------|-------------|
| `mMessageClass` | PRESIDENTIAL, EXTREME, SEVERE, CHILD_ABDUCTION_EMERGENCY, REQUIRED_MONTHLY_TEST, EXERCISE, OPERATOR_DEFINED, PUBLIC_SAFETY, STATE_LOCAL_TEST | Alert class/level |
| `mSeverity` | EXTREME, SEVERE, UNKNOWN | Per CAP (Common Alerting Protocol) |
| `mUrgency` | IMMEDIATE, EXPECTED, UNKNOWN | How quickly action is needed |
| `mCertainty` | OBSERVED, LIKELY, UNKNOWN | Confidence in the threat |
| `mCategory` | GEO, MET, SAFETY, SECURITY, RESCUE, FIRE, HEALTH, ENV, TRANSPORT, INFRA, CBRNE, OTHER, UNKNOWN | Alert category (CDMA only) |
| `mResponseType` | SHELTER, EVACUATE, PREPARE, EXECUTE, MONITOR, AVOID, ASSESS, NONE, UNKNOWN | Recommended response (CDMA only) |

Note: Category and response type are **only** available for CDMA (3GPP2) notifications. For GSM/UMTS/LTE (3GPP), only severity, urgency, and certainty are provided (except for Presidential alerts, which omit even these).

### `SmsCbConstants` Class

Path: `frameworks/base/telephony/java/com/android/internal/telephony/gsm/SmsCbConstants.java`

Defines all channel/message identifier constants. This is a `@hide` internal class. Full listing in Section 4 below.

### `SmsCbEtwsInfo` Class

Contains ETWS-specific information:
- `mWarningType`: EARTHQUAKE, TSUNAMI, EARTHQUAKE_AND_TSUNAMI, TEST, OTHER
- `mEmergencyUserAlert`: boolean flag for activating emergency alert tone
- `mActivatePopup`: boolean flag for displaying popup
- `mPrimaryNotificationTimestamp`: timestamp of primary notification

### `Telephony.CellBroadcasts` Content Provider

Content URI: `content://cellbroadcasts` (system) / `content://cellbroadcast-legacy` (legacy)

Key columns:

| Column | Type | Description |
|--------|------|-------------|
| `GEOGRAPHICAL_SCOPE` | int | Scope of the message |
| `SERIAL_NUMBER` | int | CBS serial number |
| `SERVICE_CATEGORY` | int | Message identifier (channel) |
| `LANGUAGE_CODE` | text | ISO-639 language |
| `MESSAGE_BODY` | text | Alert message text |
| `MESSAGE_FORMAT` | int | 3GPP or 3GPP2 |
| `MESSAGE_PRIORITY` | int | Normal or emergency |
| `ETWS_WARNING_TYPE` | int | ETWS type if applicable |
| `CMAS_MESSAGE_CLASS` | int | CMAS class if applicable |
| `CMAS_SEVERITY` | int | CMAS severity |
| `CMAS_URGENCY` | int | CMAS urgency |
| `CMAS_CERTAINTY` | int | CMAS certainty |
| `RECEIVED_TIME` | long | Reception timestamp |
| `MESSAGE_BROADCASTED` | int | Whether shown to user |
| `MESSAGE_DISPLAYED` | int | Whether dialog was displayed |
| `PLMN` | text | PLMN of broadcast sender |
| `LAC` | int | Location Area Code |
| `CID` | int | Cell ID |
| `GEOMETRIES` | text | WEA 3.0 geometry data |
| `MAXIMUM_WAIT_TIME` | int | WEA 3.0 max wait time |

**Duplicate detection key**: `SERIAL_NUMBER + PLMN + LAC + CID` uniquely identifies a broadcast for deduplication.

### CellBroadcastAlertService

Path: `packages/apps/CellBroadcastReceiver/src/com/android/cellbroadcastreceiver/CellBroadcastAlertService.java`

This `IntentService` handles the display and audio for received cell broadcasts:
- Receives intents from `CellBroadcastService` via `CellBroadcastReceiver`
- Determines alert behavior based on channel (Presidential vs Extreme vs Severe, etc.)
- Manages the alert tone and vibration pattern
- Creates the full-screen `CellBroadcastAlertDialog` or notification
- Emergency messages display with a flashing animated exclamation mark icon
- Presidential alerts cannot be dismissed or opted out of

---

## 4. Alert Categories and Channel Mappings

### Complete Channel/Message Identifier Table

Based on 3GPP TS 23.041 and AOSP `SmsCbConstants.java`:

#### ETWS Channels (Earthquake and Tsunami Warning System)

| Channel (Dec) | Channel (Hex) | AOSP Constant | Purpose |
|---------------|---------------|---------------|---------|
| 4352 | 0x1100 | `MESSAGE_ID_ETWS_EARTHQUAKE_WARNING` | Earthquake warning |
| 4353 | 0x1101 | `MESSAGE_ID_ETWS_TSUNAMI_WARNING` | Tsunami warning |
| 4354 | 0x1102 | `MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING` | Combined earthquake+tsunami |
| 4355 | 0x1103 | `MESSAGE_ID_ETWS_TEST_MESSAGE` | ETWS test message |
| 4356 | 0x1104 | `MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE` | Other ETWS emergency types |
| 4357-4359 | 0x1105-0x1107 | (reserved) | Reserved for future ETWS use |

#### CMAS Channels -- Primary Language (Mandatory Reception)

| Channel (Dec) | Channel (Hex) | AOSP Constant | Purpose | User Can Opt-Out? |
|---------------|---------------|---------------|---------|-------------------|
| 4370 | 0x1112 | `MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL` | **Presidential Alert** | **NO** |
| 4371 | 0x1113 | `MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED` | Extreme, Immediate, Observed | Yes |
| 4372 | 0x1114 | `MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY` | Extreme, Immediate, Likely | Yes |
| 4373 | 0x1115 | `MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED` | Extreme, Expected, Observed | Yes |
| 4374 | 0x1116 | `MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_LIKELY` | Extreme, Expected, Likely | Yes |
| 4375 | 0x1117 | `MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_OBSERVED` | Severe, Immediate, Observed | Yes |
| 4376 | 0x1118 | `MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_LIKELY` | Severe, Immediate, Likely | Yes |
| 4377 | 0x1119 | `MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_OBSERVED` | Severe, Expected, Observed | Yes |
| 4378 | 0x111A | `MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY` | Severe, Expected, Likely | Yes |
| 4379 | 0x111B | `MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY` | **AMBER Alert** | Yes |
| 4380 | 0x111C | `MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST` | Required Monthly Test (RMT) | Yes |
| 4381 | 0x111D | `MESSAGE_ID_CMAS_ALERT_EXERCISE` | Exercise/drill notification | Yes |
| 4382 | 0x111E | `MESSAGE_ID_CMAS_ALERT_OPERATOR_DEFINED_USE` | Operator-defined use | Yes |
| 4396 | 0x112C | `MESSAGE_ID_CMAS_ALERT_PUBLIC_SAFETY` | Public Safety Message | Yes |
| 4398 | 0x112E | `MESSAGE_ID_CMAS_ALERT_STATE_LOCAL_TEST` | State/Local Test Alert | Yes |

#### CMAS Channels -- Secondary Language (Optional Reception)

| Channel (Dec) | Channel (Hex) | AOSP Constant | Purpose |
|---------------|---------------|---------------|---------|
| 4383 | 0x111F | `MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL_LANGUAGE` | Presidential (secondary language) |
| 4384 | 0x1120 | `MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED_LANGUAGE` | Extreme/Immediate/Observed (2nd lang) |
| 4385 | 0x1121 | `MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY_LANGUAGE` | Extreme/Immediate/Likely (2nd lang) |
| 4386-4391 | 0x1122-0x1127 | (continuing pattern) | Remaining extreme/severe (2nd lang) |
| 4392 | 0x1128 | `MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY_LANGUAGE` | AMBER (secondary language) |
| 4393 | 0x1129 | `MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST_LANGUAGE` | RMT (secondary language) |
| 4394 | 0x112A | `MESSAGE_ID_CMAS_ALERT_EXERCISE_LANGUAGE` | Exercise (secondary language) |
| 4395 | 0x112B | `MESSAGE_ID_CMAS_ALERT_OPERATOR_DEFINED_USE_LANGUAGE` | Operator-defined (secondary language) |
| 4397 | 0x112D | `MESSAGE_ID_CMAS_ALERT_PUBLIC_SAFETY_LANGUAGE` | Public Safety (secondary language) |
| 4399 | 0x112F | `MESSAGE_ID_CMAS_ALERT_STATE_LOCAL_TEST_LANGUAGE` | State/Local Test (secondary language) |

#### Special Channels

| Channel (Dec) | Channel (Hex) | AOSP Constant | Purpose |
|---------------|---------------|---------------|---------|
| 4440 | 0x1130 | `MESSAGE_ID_CMAS_GEO_FENCING_TRIGGER` | WEA 3.0 geo-fencing trigger message |

#### ETWS Serial Number Flags

| Value (Hex) | AOSP Constant | Purpose |
|-------------|---------------|---------|
| 0x1000 | `SERIAL_NUMBER_ETWS_ACTIVATE_POPUP` | Activate popup display on device |
| 0x2000 | `SERIAL_NUMBER_ETWS_EMERGENCY_USER_ALERT` | Activate emergency user alert (sound/vibration) |

### Regional Alert Systems

| System | Country/Region | Channels Used | Notes |
|--------|---------------|---------------|-------|
| **WEA/CMAS** | United States | 4370-4399, 4440 | FCC-mandated, Presidential alerts non-blockable |
| **EU-Alert** | European Union | 4370+ (uses same CMAS channels) | ETSI TS 102 900 standard, national implementations vary |
| **NL-Alert** | Netherlands | 4370 (and legacy channels 919, 500-599, 900-999) | Operational since 2012, basis for EU-Alert |
| **DE-Alert** | Germany | 4370+ | Implemented after 2021 Ahrtal flood failures |
| **FR-Alert** | France | 4370+ | Operational since 2022 |
| **J-Alert/ETWS** | Japan | 4352-4356 | Fastest delivery (~4s), earthquake primary focus |
| **KPAS** | South Korea | 4370+ | Integrated with national EAS |
| **IT-Alert** | Italy | 4370+ | Operational since 2024 |

### PWS Message Identifier Range

Per 3GPP TS 23.041, the complete PWS range is:
- **4352-6399** (0x1100-0x18FF): Reserved for Public Warning System use
- **4352-4359**: ETWS identifiers
- **4360-4369**: Reserved for future PWS use
- **4370-4399**: CMAS identifiers
- **4400-6399**: Reserved for future PWS use

---

## 5. Security and Privacy Implications

### The Fundamental Vulnerability: Unauthenticated SIB Broadcast

The most critical security finding across all research is that **System Information Block messages are inherently unauthenticated**. The eNodeB (4G) or gNB (5G) broadcasts SIBs independently from the mutual authentication procedure between the UE and the network. This means:

- Any device that can act as a base station can broadcast SIB10/11/12 (LTE) or SIB6/7/8 (5G NR)
- The UE modem accepts and processes these messages without any cryptographic verification
- The OS then displays the alert to the user unconditionally (for Presidential alerts)
- **All modem chipsets that fully comply with 3GPP standards exhibit this behavior**

### "This is Your President Speaking" -- The Seminal Research

The landmark paper by Gyuhong Lee, Jihoon Lee, Jinsu Kim, Jihoon Kim, and Dongkwan Kim (MobiSys 2019, published by CU Boulder/KAIST researchers) demonstrated the first practical WEA spoofing attack:

**Equipment used:**
- A commercially-available Software Defined Radio (SDR)
- Modified open-source NextEPC (core network emulator)
- Modified open-source srsLTE (LTE base station software)
- Total cost: approximately $1,000 in off-the-shelf hardware

**Attack procedure:**
1. Deploy a rogue LTE base station (fake eNodeB) within range of target devices
2. Transmit a **paging signal** with the CMAS indication bit set, waking up all idle UEs
3. Broadcast a spoofed SIB12 message containing a fabricated Presidential Alert
4. Victim phones receive, decode, and display the fake alert without any verification

**Key findings:**
- **90% success rate** within a 4,435 m^2 area of a 16,859 m^2 building using a single rogue base station at 0.1W transmit power
- In an **outdoor stadium scenario**, 49,300 out of 50,000 seats were successfully attacked using 4 rogue base stations at 1W each
- Alerts could be broadcast every 160 milliseconds (or once per second for Presidential Alerts with NextEPC modifications)
- The attack works against **all tested smartphones** from all major U.S. carriers regardless of vendor or model
- Presidential Alerts are the most dangerous vector because users **cannot opt out** of receiving them

**Responsible disclosure:** The researchers disclosed their findings in January 2019 to FEMA, FCC, DHS, NIST, 3GPP, GSMA, AT&T, Verizon, T-Mobile, Sprint, U.S. Cellular, Samsung, Google, and Apple.

### IMSI Catcher and WEA Spoofing Convergence

IMSI catchers (StingRays, cell-site simulators) and WEA spoofers share the same fundamental capability: **acting as a fake base station**. This creates a critical overlap:

1. **Any IMSI catcher can potentially send fake WEA alerts.** Since IMSI catchers already masquerade as legitimate cell towers, adding cell broadcast transmission is a software-level change.

2. **WEA spoofing can serve as a distraction or panic weapon.** During an IMSI catcher surveillance operation, a fake emergency alert could be used to cause evacuation or confusion, providing cover for other activities.

3. **Forced 2G downgrade attacks** used by IMSI catchers also increase WEA vulnerability, as 2G cell broadcast has even fewer protections.

4. **The same SDR hardware** (HackRF, USRP, bladeRF) used for IMSI catching can be repurposed for WEA spoofing with modified open-source software.

### Proposed Countermeasures and WEA Security Evolution

**Digital Signatures (Proposed but not yet deployed):**
The ACM paper "Securing the Wireless Emergency Alerts System" (2021) analyzed approaches to authenticate WEA messages:

- **Signing SIB12 messages**: Conceptually straightforward but requires all operators and devices to agree on key management
- **Key distribution via NAS**: Use the authenticated Non-Access Stratum channel to send/update public keys to devices
- **Key distribution via OTA SIM provisioning**: Over-The-Air updates to the SIM card with trusted public keys
- **64-byte digital signature**: Feasible within the 360-character WEA message limit (mandated by FCC since May 2019)
- **Challenge**: Backward compatibility with billions of existing devices

**WEA 3.0 Improvements (ATIS specifications, effective November 2019):**
- **Device-Based Geo-Fencing (ATIS-0700041)**: Devices validate whether they are within the alert polygon/circle before displaying, reducing over-alerting but not addressing authentication
- **Enhanced message content**: Up to 360 characters (from 90 in WEA 1.0)
- **Alert preservation**: Must retain alerts for 24 hours
- **Improved geo-targeting**: Circle and polygon area definitions rather than just cell-sector coverage

**What WEA 3.0 does NOT fix:**
- Still no cryptographic authentication of broadcast messages
- Still no way for the device to verify the alert came from a legitimate source
- Geo-fencing can actually help attackers target more precisely

### CVE-2024-20154: MediaTek Modem Vulnerability

A critical vulnerability (CVE-2024-20154) in MediaTek modem firmware demonstrates how fake base stations can exploit modem-level bugs:

- **Type**: Out-of-bounds write in modem firmware
- **Severity**: Critical (CVSS ~9.8)
- **Attack vector**: Attacker sets up a rogue base station; victim device connects automatically
- **Impact**: Remote code execution at the modem level -- complete device compromise
- **Affected devices**: Over 40 MediaTek chipset models used in Samsung, Xiaomi, Motorola, Oppo devices
- **No user interaction required**
- **Relevance**: This shows that fake base stations can do far more than just send spoofed alerts -- they can achieve full device compromise through modem vulnerabilities

### Android's (Lack of) Alert Validation

Android's current cell broadcast processing performs **no cryptographic validation** of alert authenticity:

1. **Modem level**: Accepts any well-formed SIB with valid channel identifiers
2. **RIL level**: Passes PDU to framework without authentication checks
3. **CellBroadcastService**: Performs format validation and deduplication, but no source authentication
4. **CellBroadcastReceiver**: Displays alert based on channel number alone

The deduplication mechanism (serial number + PLMN + LAC + CID) provides some protection against replay attacks of the same message, but an attacker can easily use different serial numbers.

---

## 6. Detection/Monitoring Possibilities

### Permissions and Access Restrictions

| Permission / API | Protection Level | Availability | Description |
|-----------------|-----------------|--------------|-------------|
| `android.permission.RECEIVE_EMERGENCY_BROADCAST` | `signature\|privileged` | **System apps only** | Receive real-time emergency broadcast intents |
| `android.permission.READ_CELL_BROADCASTS` | `dangerous` | Third-party (pre-Android 14) | Read previously received cell broadcasts from ContentProvider |
| `Telephony.CellBroadcasts` ContentProvider | System-only URI | **System apps only** (post Android 11) | Access cell broadcast message database |
| `cellbroadcast-legacy` ContentProvider | Module-internal | **System/module only** | Legacy content provider within APEX module |
| `SMS_CB_RECEIVED_ACTION` | (deprecated) | Varies by Android version | Intent action for received cell broadcasts |

### What Third-Party Apps CAN Do

1. **Read cell broadcast history** (limited): On older Android versions (pre-11), apps with `READ_CELL_BROADCASTS` permission could query the content provider. Post-Android 11 with the APEX module, this is restricted to system apps.

2. **Monitor cellular network state**: Apps with `READ_PHONE_STATE` can observe cell tower changes, signal strength, and network type -- useful for detecting the prerequisite conditions for a WEA spoofing attack (e.g., forced downgrade to 2G, rapid cell tower switching).

3. **Observe timing anomalies**: A surveillance detection app can track:
   - Sudden cell tower changes that precede emergency alerts
   - Signal strength anomalies (fake base stations often have abnormally high signal)
   - Network type downgrades (4G/5G to 2G) that precede alerts
   - Multiple emergency alerts in rapid succession from the same "tower"
   - Alerts received while in areas without known coverage

4. **Cross-reference with location**: Compare alert geography with device location to detect geofencing anomalies.

5. **SnoopSnitch-style analysis**: On rooted devices with Qualcomm chipsets, the SnoopSnitch app demonstrated that baseband debug information can be used to detect IMSI catchers -- the same approach could detect fake WEA senders.

### What System/OEM Apps CAN Do

For the Flock-You app in **system** or **OEM** privilege modes:

1. **Register for `RECEIVE_EMERGENCY_BROADCAST`**: Receive real-time cell broadcast intents as they arrive
2. **Query the CellBroadcasts ContentProvider**: Access full history including PLMN, LAC, CID data
3. **Analyze alert metadata**: Compare PLMN/LAC/CID of alert sender against known cell tower database
4. **Access baseband diagnostics**: Some chipsets expose modem diagnostic information to privileged apps
5. **Monitor RIL events**: System apps can register telephony callbacks for detailed radio events

### Detection Heuristics for Spoofed Alerts

Potential indicators that a WEA alert may be spoofed:

| Indicator | Weight | Description |
|-----------|--------|-------------|
| **Cell tower change before alert** | HIGH | Sudden forced handover to unknown cell immediately before alert |
| **Unknown PLMN/LAC/CID** | HIGH | Alert originates from a cell not in trusted tower database |
| **Signal strength anomaly** | MEDIUM | Alert-sending cell has abnormally strong signal (SDR nearby) |
| **Network downgrade** | HIGH | Forced to 2G/GSM before alert (common IMSI catcher technique) |
| **Rapid repeated alerts** | MEDIUM | Multiple Presidential Alerts in quick succession (legitimate ones are extremely rare) |
| **Geographic inconsistency** | MEDIUM | Alert references area that doesn't match device location |
| **No corroboration** | LOW | Alert not corroborated by nearby devices, internet, or other sources |
| **Timing anomaly** | MEDIUM | Alert at unusual time, or cell tower appeared moments before |
| **Missing CMAS fields** | LOW | Malformed CMAS type 1 elements (severity/urgency/certainty) |
| **Presidential alert frequency** | HIGH | As of 2024, only ONE Presidential Alert has ever been sent (Oct 2018 test) |

### Correlation with Existing Flock-You Detection

The CellularDetectionHandler in Flock-You already tracks several of these indicators:
- **Encryption downgrade detection** (5G/4G to 2G)
- **Rapid cell switching** (threshold: 3/min stationary, 8/min moving)
- **Signal spike detection** (>25 dBm change)
- **Unknown cell tower detection** (trust database with 5-sighting threshold)
- **LAC/TAC anomaly detection**

These existing capabilities map directly to WEA spoofing preconditions. A fake emergency alert arriving within a temporal window of these cellular anomalies would be a strong indicator of a spoofed alert, potentially warranting a `CROSS_DOMAIN` correlation alert at CRITICAL severity.

---

## 7. Recent Changes and Developments

### 5G NR PWS Evolution

The transition to 5G NR brought several changes to the Public Warning System:

1. **New SIB numbering**: SIB6/7/8 replace SIB10/11/12 from LTE
2. **Improved paging**: Both `RRC_IDLE` and `RRC_INACTIVE` states can receive ETWS/CMAS paging indications
3. **Better geo-targeting**: 5G NR's smaller cell sizes inherently improve geographic precision
4. **Faster delivery**: 5G's lower latency improves alert delivery times

### 5G Network Architecture for CMAS

In the 5G core network, the CMAS architecture introduces:

- **N50 interface**: Between AMF (Access and Mobility Management Function) and PWS-IWF (Public Warning System InterWorking Function)
- **SBc interface**: Between PWS-IWF and CBC (Cell Broadcast Center)
- **PWS-IWF deployment options**: Can be co-located with AMF, with CBC, or standalone
- **SCTP protocol**: Used for reliable signaling between AMF and NG-RAN (N2) and between PWS-IWF and CBC (SBc)

Message flow in 5G:
```
CBE -> CBC -> [SBc] -> PWS-IWF -> [N50] -> AMF -> [N2] -> gNB -> [SIB8 broadcast] -> UE
```

### Android Mainline Module Updates

Since Android 11, the CellBroadcast APEX module can be updated independently:
- Module uses only `@SystemApi` (no `@hide` APIs) for framework interaction
- Carrier-specific configurations via `CarrierConfigManager`
- OEM customization through overlay resources
- Post-Android 13: improved message formatting and multilingual support

### FEMA/FCC Cybersecurity Efforts

In August 2022, FEMA warned that Emergency Alert System (EAS) encoder/decoder software had known vulnerabilities that could allow hackers to transmit fake messages over TV, radio, and cable networks. While this relates to the EAS (not WEA specifically), it highlights the broader concern about emergency alert system security.

The FCC has pursued rulemaking (November 2022 Federal Register) on "Protecting the Nation's Communications Systems From Cybersecurity Threats" covering both EAS and WEA systems.

### NDSS 2025: IMSI Catcher Detection

A recent paper at NDSS 2025 titled "Detecting IMSI-Catchers by Characterizing Identity Exposing Messages" presents a statistical detection approach:
- Monitors the **IMSI Exposure (IE) Ratio** of base stations
- Normal base stations maintain IE ratios below 3% (LTE) or 6% (GSM)
- IMSI catchers produce IE ratios of 30-100%
- Achieves **99.9% detection rate**
- The study identified 53 messages an IMSI catcher must use to extract identifiers

This is directly relevant to WEA spoofing detection: if an IMSI catcher is detected via IE ratio analysis, any WEA alerts from that same cell should be flagged as potentially spoofed.

---

## 8. Relevance to Surveillance Detection (Flock-You Context)

### Threat Model

A fake WEA/CMAS alert represents a significant surveillance and safety threat:

1. **Panic induction**: Fake Presidential Alerts about imminent nuclear attack, bioweapon release, etc. could cause mass panic and stampedes
2. **Evacuation exploitation**: Fake alerts could force evacuation of a building, allowing physical intrusion or surveillance equipment placement
3. **Attention diversion**: A fake alert could divert attention from an ongoing IMSI catcher operation
4. **Social engineering**: Fake local alerts could be used to manipulate behavior of targeted individuals
5. **Attribution to state actors**: Only nation-state level actors were historically assumed to have this capability; the MobiSys 2019 research proved $1,000 in hardware is sufficient

### Recommended Integration Points

For the Flock-You app's detection pipeline, WEA spoofing detection could integrate with the existing `CellularDetectionHandler`:

1. **New DeviceType**: `WEA_SPOOFER` or `FAKE_EMERGENCY_ALERT` with impact factor 2.0 (communication interception / public safety threat)
2. **Cross-domain correlation**: Emergency alert reception within temporal window of cellular anomalies (unknown cell, signal spike, encryption downgrade) should trigger CRITICAL severity
3. **Alert metadata analysis** (system/OEM only): If `RECEIVE_EMERGENCY_BROADCAST` is available, analyze PLMN/LAC/CID against trusted cell database
4. **Behavioral indicators** (all privilege levels): Monitor for cell tower changes, signal anomalies, and network downgrades that precede alert reception

### Key AOSP Source Paths

| Component | AOSP Path |
|-----------|-----------|
| SmsCbMessage | `frameworks/base/telephony/java/android/telephony/SmsCbMessage.java` |
| SmsCbCmasInfo | `frameworks/base/telephony/java/android/telephony/SmsCbCmasInfo.java` |
| SmsCbEtwsInfo | `frameworks/base/telephony/java/android/telephony/SmsCbEtwsInfo.java` |
| SmsCbConstants | `frameworks/base/telephony/java/com/android/internal/telephony/gsm/SmsCbConstants.java` |
| SmsCbLocation | `frameworks/base/telephony/java/android/telephony/SmsCbLocation.java` |
| CellBroadcastService (abstract) | `frameworks/base/telephony/java/android/telephony/CellBroadcastService.java` |
| CellBroadcastHandler | `packages/modules/CellBroadcastService/src/com/android/cellbroadcastservice/CellBroadcastHandler.java` |
| GsmCellBroadcastHandler | `packages/modules/CellBroadcastService/src/com/android/cellbroadcastservice/GsmCellBroadcastHandler.java` |
| CdmaCellBroadcastHandler | `packages/modules/CellBroadcastService/src/com/android/cellbroadcastservice/CdmaCellBroadcastHandler.java` |
| CellBroadcastAlertService | `packages/apps/CellBroadcastReceiver/src/com/android/cellbroadcastreceiver/CellBroadcastAlertService.java` |
| CellBroadcastAlertDialog | `packages/apps/CellBroadcastReceiver/src/com/android/cellbroadcastreceiver/CellBroadcastAlertDialog.java` |
| CellBroadcastReceiver | `packages/apps/CellBroadcastReceiver/src/com/android/cellbroadcastreceiver/CellBroadcastReceiver.java` |
| Telephony.CellBroadcasts | `frameworks/base/core/java/android/provider/Telephony.java` (inner class) |
| RIL | `frameworks/opt/telephony/src/java/com/android/internal/telephony/RIL.java` |
| ril.h (RIL_UNSOL definitions) | `hardware/ril/include/telephony/ril.h` |

---

## 9. Sources

- [CellBroadcast - Android Open Source Project](https://source.android.com/docs/core/ota/modular-system/cellbroadcast)
- [SmsCbConstants.java - AOSP Mirror](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/telephony/java/com/android/internal/telephony/gsm/SmsCbConstants.java)
- [SmsCbMessage.java - AOSP Mirror](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/telephony/java/android/telephony/SmsCbMessage.java)
- [SmsCbCmasInfo.java - AOSP Mirror](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/telephony/java/android/telephony/SmsCbCmasInfo.java)
- [CellBroadcastService.java - AOSP Mirror](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/telephony/java/android/telephony/CellBroadcastService.java)
- [CellBroadcastAlertService.java - Android System Apps](https://github.com/yuchuangu85/Android-system-apps/blob/master/apps/CellBroadcastReceiver/src/com/android/cellbroadcastreceiver/CellBroadcastAlertService.java)
- [This is Your President Speaking: Spoofing Alerts in 4G LTE Networks - ACM MobiSys 2019](https://dl.acm.org/doi/10.1145/3307334.3326082)
- [This is Your President Speaking - Full Paper PDF](https://ericw.us/trow/lte-alerts.pdf)
- [Securing the Wireless Emergency Alerts System - Communications of the ACM](https://cacm.acm.org/research/securing-the-wireless-emergency-alerts-system/)
- [5G CMAS - Commercial Mobile Alert Service - Techplayon](https://www.techplayon.com/5g-cmas-commercial-mobile-alert-service/)
- [5G NR System Information Block Type 8 - SIB8 - Techplayon](https://www.techplayon.com/5g-nr-system-information-block-type-8-sib8/)
- [PWS/CMAS/ETWS/CBS Message Identifiers - ShareTechNote](https://www.sharetechnote.com/html/Handbook_LTE_PWS_MessageIdentifier.html)
- [PWS/CMAS/ETWS/CBS Overview - ShareTechNote](https://www.sharetechnote.com/html/Handbook_LTE_CMAS.html)
- [3GPP TS 23.041 - Cell Broadcast Service](https://www.tech-invite.com/3m23/toc/tinv-3gpp-23-041_q.html)
- [ETSI TS 123 041 V15.2.0](https://www.etsi.org/deliver/etsi_ts/123000_123099/123041/15.02.00_60/ts_123041v150200p.pdf)
- [EU-Alert - Wikipedia](https://en.wikipedia.org/wiki/EU-Alert)
- [Wireless Emergency Alerts - Wikipedia](https://en.wikipedia.org/wiki/Wireless_Emergency_Alerts)
- [CVE-2024-20154: Critical RCE Flaw in MediaTek Chipsets](https://securityonline.info/cve-2024-20154-critical-rce-flaw-in-mediatek-chipsets-impacts-millions/)
- [Detecting IMSI-Catchers by Characterizing Identity Exposing Messages - NDSS 2025](https://www.ndss-symposium.org/wp-content/uploads/2025-1115-paper.pdf)
- [IMSI Catcher Detector (NDSS 2025 implementation) - GitHub](https://github.com/eylonK14/IMSICatcherDetector)
- [Emergency Presidential Alerts Can Be Spoofed - Help Net Security](https://www.helpnetsecurity.com/2019/06/25/emergency-presidential-alerts/)
- [ATIS WEA 3.0 Device-Based Geo-Fencing Standard](https://atis.org/press-releases/new-atis-standards-advance-geo-targeting-capabilities-of-the-wireless-emergency-alerts-system/)
- [False Base Station or IMSI Catcher - CableLabs](https://www.cablelabs.com/blog/false-base-station-or-imsi-catcher-what-you-need-to-know)
- [EFF: Gotta Catch 'Em All - How IMSI Catchers Exploit Cell Networks](https://www.eff.org/wp/gotta-catch-em-all-understanding-how-imsi-catchers-exploit-cell-networks)
- [Cisco AMF CMAS Service Support](https://www.cisco.com/c/en/us/td/docs/wireless/ucc/amf/2025-02/config-and-admin/b_ucc-5g-amf-config-and-admin-guide_2025-02/m_cmas_service_support.html)
- [android.permission.RECEIVE_EMERGENCY_BROADCAST](http://androidpermissions.com/permission/android.permission.RECEIVE_EMERGENCY_BROADCAST)
- [LineageOS CellBroadcastService Module - GitHub](https://github.com/LineageOS/android_packages_modules_CellBroadcastService)
- [SimulatingCellbroadcast - Bypass Modem Level Testing - GitHub](https://github.com/garyckhsu/SimulatingCellbroadcast)
- [RIL Refactoring - Android Open Source Project](https://source.android.com/docs/core/connect/ril)
- [5G NR CMAS for PWS - Telcosought](http://telcosought.com/5g-core/5g-cmas-commercial-mobile-alert-service-for-pws/)
- [Cell Broadcast - Wikipedia](https://en.wikipedia.org/wiki/Cell_Broadcast)
