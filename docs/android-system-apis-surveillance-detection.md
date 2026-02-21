# Android System APIs for Surveillance Detection: Technical Reference

## 1. Telephony & Cellular APIs

### 1.1 TelephonyManager Core Methods

**Class**: `android.telephony.TelephonyManager`

| Method | Permission | API | Visibility | Surveillance Use |
|--------|-----------|-----|------------|-----------------|
| `getAllCellInfo()` | `ACCESS_FINE_LOCATION` | 17 | Public | All visible cell towers for anomaly detection |
| `requestCellInfoUpdate(executor, cb)` | `ACCESS_FINE_LOCATION` | 29 | Public | Force-refresh cell info (getAllCellInfo returns cached on 29+) |
| `getServiceState()` | `ACCESS_COARSE_LOCATION` | 26 | Public | Network registration state, roaming, operator info |
| `getDataNetworkType()` | `READ_PHONE_STATE` | 30 | Public | Current RAT (replaces deprecated `getNetworkType()`) |
| `getNetworkOperator()` | None | 1 | Public | MCC+MNC string of registered network |
| `getNetworkOperatorName()` | None | 1 | Public | Carrier name - detect fake operator names |
| `getSimOperator()` | None | 1 | Public | SIM's MCC+MNC - compare with network for mismatch |
| `getImei()` | `READ_PRIVILEGED_PHONE_STATE` | 26 | Public (OEM only) | Device IMEI for tracking detection |
| `getSubscriberId()` | `READ_PRIVILEGED_PHONE_STATE` | 1 | Public (OEM only) | IMSI - the target of IMSI catchers |
| `getDeviceId()` | `READ_PRIVILEGED_PHONE_STATE` | 1 | Public (OEM only) | Device identifier |
| `getSimSerialNumber()` | `READ_PRIVILEGED_PHONE_STATE` | 1 | Public (OEM only) | SIM ICCID |
| `getLine1Number()` | `READ_PHONE_STATE` or `READ_SMS` or `READ_PHONE_NUMBERS` | 1 | Public | Phone number |
| `getPhoneType()` | None | 1 | Public | GSM, CDMA, SIP, or NONE |

`READ_PRIVILEGED_PHONE_STATE` is `signature|privileged` -- only available to platform-signed or privileged system apps (OEM builds).

### 1.2 CellIdentityLte (API 17)

| Method | Return | API | Description |
|--------|--------|-----|-------------|
| `getMcc()` / `getMccString()` | int/String | 17/28 | Mobile Country Code |
| `getMnc()` / `getMncString()` | int/String | 17/28 | Mobile Network Code |
| `getCi()` | int | 17 | 28-bit Cell Identity (0..268435455) |
| `getPci()` | int | 17 | Physical Cell Id (0..503) |
| `getTac()` | int | 17 | 16-bit Tracking Area Code |
| `getEarfcn()` | int | 24 | 18-bit E-UTRA ARFCN |
| `getBandwidth()` | int | 28 | Cell bandwidth in kHz |
| `getBands()` | int[] | 30 | Band numbers per 3GPP TS 36.101 |
| `getAdditionalPlmns()` | Set\<String\> | 30 | Additional PLMNs broadcast by the cell |
| `getOperatorAlphaLong()` | CharSequence | 28 | Long operator name |
| `getOperatorAlphaShort()` | CharSequence | 28 | Short operator name |
| `getClosedSubscriberGroupInfo()` | ClosedSubscriberGroupInfo | 30 | CSG info for femtocells |

**IMSI catcher indicators**: Anomalous MCC/MNC (001-01 test, 999-99), `getPci()` changes while stationary, `getBandwidth()` revealing underprovisioned fake cells.

### 1.3 CellIdentityNr (5G, API 29)

| Method | Return | API | Description |
|--------|--------|-----|-------------|
| `getMccString()` | String | 29 | Mobile Country Code |
| `getMncString()` | String | 29 | Mobile Network Code |
| `getNci()` | long | 29 | 36-bit NR Cell Identity (0..68719476735) |
| `getPci()` | int | 29 | Physical Cell Id (0..1007) |
| `getTac()` | int | 29 | 24-bit Tracking Area Code |
| `getNrarfcn()` | int | 29 | NR ARFCN (0..3279165) |
| `getBands()` | int[] | 30 | NR band numbers per 3GPP TS 38.101 |
| `getAdditionalPlmns()` | Set\<String\> | 30 | Additional PLMNs |

### 1.4 CellIdentityGsm (2G, API 17)

| Method | Return | API | Description |
|--------|--------|-----|-------------|
| `getMcc()` / `getMccString()` | int/String | 17/28 | Mobile Country Code |
| `getMnc()` / `getMncString()` | int/String | 17/28 | Mobile Network Code |
| `getLac()` | int | 17 | 16-bit Location Area Code |
| `getCid()` | int | 17 | 16-bit GSM Cell Identity |
| `getArfcn()` | int | 24 | 16-bit ARFCN |
| `getBsic()` | int | 24 | 6-bit Base Station Identity Code |

**Encryption downgrade indicator**: A device forced onto 2G (`CellInfoGsm`) when 4G/5G is available. GSM has no mutual authentication, making it vulnerable to IMSI catchers.

### 1.5 CellSignalStrength -- Key Fields

#### CellSignalStrengthLte

| Method | Return | API | Description |
|--------|--------|-----|-------------|
| `getRsrp()` | int | 26 | Reference Signal Received Power (-140..-44 dBm) |
| `getRsrq()` | int | 26 | Reference Signal Received Quality (-20..-3 dB) |
| `getRssnr()` | int | 26 | RS SNR (-20..30 dB) |
| `getCqi()` | int | 26 | Channel Quality Indicator (0..15) |
| `getTimingAdvance()` | int | 17 | **Timing advance (0..1282). Each unit ~553m distance** |
| `getRssi()` | int | 29 | RSSI in dBm |

**`getTimingAdvance()` for surveillance**: TA is proportional to distance to cell tower. An IMSI catcher deployed close to the target has TA=0-2 while pretending to be a distant macro cell. A cell suddenly appearing with TA=0 that was previously unknown is highly suspicious.

#### CellSignalStrengthNr (5G)

| Method | Return | API | Description |
|--------|--------|-----|-------------|
| `getSsRsrp()` | int | 29 | SS RSRP (-140..-44 dBm) |
| `getSsRsrq()` | int | 29 | SS RSRQ (-43..20 dB) |
| `getSsSinr()` | int | 29 | SS SINR (-23..40 dB) |
| `getCsiRsrp()` | int | 29 | CSI RSRP |
| `getCsiRsrq()` | int | 29 | CSI RSRQ |
| `getCsiSinr()` | int | 29 | CSI SINR |
| `getTimingAdvanceMicros()` | int | 34 | **Timing advance in microseconds** |

### 1.6 ServiceState Key Fields

| Method | API | Description |
|--------|-----|-------------|
| `getState()` | 1 | `STATE_IN_SERVICE`, `STATE_OUT_OF_SERVICE`, `STATE_EMERGENCY_ONLY`, `STATE_POWER_OFF` |
| `getRoaming()` | 1 | Roaming indicator |
| `getOperatorAlphaLong()` | 1 | Operator name |
| `getOperatorNumeric()` | 1 | MCC+MNC numeric |
| `isUsingCarrierAggregation()` | 28 | **CA active -- IMSI catchers cannot perform CA** |
| `getNetworkRegistrationInfoList()` | 30 | List of `NetworkRegistrationInfo` |
| `getDuplexMode()` | 28 | `DUPLEX_MODE_FDD`, `DUPLEX_MODE_TDD`, `DUPLEX_MODE_UNKNOWN` |
| `getChannelNumber()` | 28 | Channel number (EARFCN, UARFCN, or ARFCN) |
| `getCellBandwidths()` | 28 | Array of cell bandwidths in kHz |
| `isUsingNonTerrestrialNetwork()` | 35 | **NTN detection (satellite)** |

**`isUsingCarrierAggregation()` is underutilized**: IMSI catchers and StingRay devices typically cannot perform carrier aggregation. If the device was previously on CA and drops to non-CA on the same apparent cell, this is suspicious.

### 1.7 NetworkRegistrationInfo (API 30)

| Constant | Value | Description |
|----------|-------|-------------|
| `REGISTRATION_STATE_HOME` | 1 | Registered home |
| `REGISTRATION_STATE_ROAMING` | 5 | Roaming |
| `REGISTRATION_STATE_DENIED` | 3 | Registration denied |
| `DOMAIN_CS` | 1 | Circuit-switched |
| `DOMAIN_PS` | 2 | Packet-switched |
| `NR_STATE_NONE` | 0 | Not using NR |
| `NR_STATE_RESTRICTED` | 1 | NR restricted |
| `NR_STATE_NOT_RESTRICTED` | 2 | NR unrestricted |
| `NR_STATE_CONNECTED` | 3 | Connected via NR |

**Detecting encryption downgrades**: Monitor `getAccessNetworkTechnology()` transitions. LTE/NR to GSM/EDGE while stationary = forced downgrade attack signature.

### 1.8 TelephonyCallback (API 31)

| Listener Interface | Callback | Permission | Description |
|-------------------|---------|------------|-------------|
| `CellInfoListener` | `onCellInfoChanged(List<CellInfo>)` | `ACCESS_FINE_LOCATION` | Cell tower changes |
| `ServiceStateListener` | `onServiceStateChanged(ServiceState)` | `ACCESS_COARSE_LOCATION` | Service state changes |
| `SignalStrengthsListener` | `onSignalStrengthsChanged(SignalStrength)` | None | Signal strength |
| `CellLocationListener` | `onCellLocationChanged(CellLocation)` | `ACCESS_FINE_LOCATION` | Cell location |
| `BarringInfoListener` | `onBarringInfoChanged(BarringInfo)` | `ACCESS_FINE_LOCATION` + `READ_PRECISE_PHONE_STATE` | Cell barring info |
| `RegistrationFailedListener` | `onRegistrationFailed(CellIdentity, chosenPlmn, domain, causeCode, additionalCauseCode)` | `ACCESS_FINE_LOCATION` + `READ_PRECISE_PHONE_STATE` | **Registration failure** |
| `DisplayInfoListener` | `onDisplayInfoChanged(TelephonyDisplayInfo)` | None | Display info changes |
| `DataConnectionStateListener` | `onDataConnectionStateChanged(state, networkType)` | None | Data connection |
| `PreciseDataConnectionStateListener` | `onPreciseDataConnectionStateChanged(PreciseDataConnectionState)` | `READ_PRECISE_PHONE_STATE` | Precise data state |
| `LinkCapacityEstimateChangedListener` | `onLinkCapacityEstimateChanged(List<LinkCapacityEstimate>)` | `READ_PRECISE_PHONE_STATE` | Link capacity |

**`RegistrationFailedListener` is extremely valuable**: When an IMSI catcher attempts registration, `causeCode` reveals why the legitimate network rejected it:
- `2` - IMSI unknown in HLR
- `3` - Illegal MS
- `6` - Illegal ME
- `11` - PLMN not allowed
- `13` - Roaming not allowed

Repeated failures with codes 2, 3, or 6 while the device appears "connected" may indicate a fake base station.

`READ_PRECISE_PHONE_STATE` is `signature|privileged` -- only available to System/OEM builds.

### 1.9 SubscriptionManager (API 22)

| Method | Permission | Description |
|--------|-----------|-------------|
| `getActiveSubscriptionInfoList()` | `READ_PHONE_STATE` | Active SIM/eSIM subscriptions |
| `addOnSubscriptionsChangedListener(listener)` | `READ_PHONE_STATE` | Monitor subscription changes |

**`SubscriptionInfo` fields**: `getSubscriptionId()`, `getSimSlotIndex()`, `getCarrierName()`, `getMccString()`, `getMncString()`, `isEmbedded()` (eSIM, API 28), `isOpportunistic()` (API 29).

**Surveillance relevance**: Unexpected eSIM profile changes or subscription modifications without user action could indicate remote SIM provisioning.

### 1.10 Downgrade Attack Detection Strategy

```
1. ServiceState.getNetworkRegistrationInfoList() -> all registrations
2. For each: getAccessNetworkTechnology() -> current RAT
3. Track transitions over time
4. CellSignalStrengthLte.getTimingAdvance() -> distance changes
5. ServiceState.isUsingCarrierAggregation() -> CA drops
6. CellIdentity.getMcc()/getMnc() -> operator changes
```

**Attack signature**: LTE/NR -> GSM/EDGE while stationary + loss of CA + new PCI/CID + signal spike + TA near zero + registration failures on legitimate cells.

---

## 2. WiFi System APIs

### 2.1 WifiManager

| Method | Permission | API | Description |
|--------|-----------|-----|-------------|
| `startScan()` | `CHANGE_WIFI_STATE` + `ACCESS_FINE_LOCATION` | 1 (deprecated 28) | Trigger WiFi scan |
| `getScanResults()` | `ACCESS_FINE_LOCATION` + `ACCESS_WIFI_STATE` | 1 | Latest scan results |
| `isScanThrottleEnabled()` | None | 30 | Check if throttle active |

**Scan throttling** (Android 9+): Foreground 4 scans / 2 min, background 1 scan / 30 min.

**Hidden API**: `setScanThrottleEnabled(boolean)` -- `@SystemApi`, requires `NETWORK_SETTINGS` (signature-level). System/OEM only.

**Hidden API**: `WifiScanner` (`android.net.wifi.WifiScanner`) -- `@SystemApi` for continuous background scanning. Requires `NETWORK_SETTINGS` or `NETWORK_SETUP_WIZARD`.

### 2.2 ScanResult Fields

| Field/Method | Type | API | Description |
|-------------|------|-----|-------------|
| `BSSID` | String | 1 | MAC address of AP |
| `SSID` | String | 1 | Network name |
| `frequency` | int | 1 | Channel frequency in MHz |
| `level` | int | 1 | Signal strength in dBm |
| `capabilities` | String | 1 | Security capabilities (WPA2-PSK, EAP, etc.) |
| `channelWidth` | int | 23 | 20/40/80/160 MHz |
| `centerFreq0` | int | 23 | Center frequency primary (MHz) |
| `centerFreq1` | int | 23 | Center frequency secondary (MHz) |
| `timestamp` | long | 17 | Timestamp in microseconds since boot |
| `venueName` | CharSequence | 23 | Passpoint venue name |
| `operatorFriendlyName` | CharSequence | 23 | Passpoint operator name |
| `informationElements` | InformationElement[] | 30 | **Raw 802.11 Information Elements** |
| `getWifiStandard()` | int | 30 | `WIFI_STANDARD_LEGACY`, `_11N`, `_11AC`, `_11AX`, `_11AD`, `_11BE` |
| `is80211mcResponder()` | boolean | 23 | Supports WiFi RTT |
| `isPasspointNetwork()` | boolean | 23 | Is Hotspot 2.0 |

**`informationElements` (API 30)**: Each `ScanResult.InformationElement` contains `id` (int), `idExt` (int), `bytes` (byte[]). Key IDs:
- **0**: SSID
- **1**: Supported Rates
- **3**: DS Parameter Set (channel)
- **7**: Country Information
- **48**: RSN (WPA2 cipher suites and AKM)
- **127**: Extended Capabilities
- **191**: VHT Capabilities
- **221**: Vendor Specific (WPS, WPA, Microsoft vendor OUI)

**Evil twin detection**: Compare full IE sets between scans. Two APs with the same SSID but different IE signatures (vendor-specific elements, capability bits) indicate one is likely spoofed.

### 2.3 WiFi RTT (API 28)

**Class**: `android.net.wifi.rtt.WifiRttManager`
**Permission**: `ACCESS_FINE_LOCATION` + `ACCESS_WIFI_STATE` + `CHANGE_WIFI_STATE` + `NEARBY_WIFI_DEVICES` (API 33+)

**RangingResult methods**:
- `getDistanceMm()` -- Distance in millimeters
- `getDistanceStdDevMm()` -- Standard deviation
- `getRssi()` -- RSSI at ranging time
- `getStatus()` -- `STATUS_SUCCESS`, `STATUS_FAIL`, `STATUS_RESPONDER_DOES_NOT_SUPPORT_IEEE80211MC`

**Surveillance use**: Measure precise distance to suspicious APs. If an AP claims to be a known network but is at an unexpected distance, it may be rogue. Multilateration with 3+ APs can verify position without GPS.

### 2.4 WiFi Direct (P2P, API 14)

**Class**: `android.net.wifi.p2p.WifiP2pManager`

**WifiP2pDevice fields**:
- `deviceName` -- Human-readable name
- `deviceAddress` -- MAC address
- `primaryDeviceType` -- WPS device type (type `10` = Displays/cameras)
- `status` -- `AVAILABLE`, `INVITED`, `CONNECTED`, `FAILED`, `UNAVAILABLE`

**Surveillance use**: Hidden cameras and surveillance devices use WiFi Direct for configuration. Scanning for P2P peers reveals devices not visible in normal WiFi scans.

---

## 3. Bluetooth System APIs

### 3.1 BLE Scan Settings

| Constant | Value | Description |
|----------|-------|-------------|
| `SCAN_MODE_LOW_POWER` | 0 | ~10% duty (512ms / 5120ms) |
| `SCAN_MODE_BALANCED` | 1 | ~25% duty (1024ms / 4096ms) |
| `SCAN_MODE_LOW_LATENCY` | 2 | ~100% duty. Foreground only |
| `SCAN_MODE_OPPORTUNISTIC` | -1 | Piggyback on other scans |
| `CALLBACK_TYPE_ALL_MATCHES` | 1 | Every advertisement |
| `CALLBACK_TYPE_FIRST_MATCH` | 2 | First discovery only |
| `CALLBACK_TYPE_MATCH_LOST` | 4 | Device lost |
| `MATCH_MODE_AGGRESSIVE` | 1 | Quick match even with weak signals |
| `MATCH_MODE_STICKY` | 2 | Strong signal + multiple sightings |

**System restrictions**: Android 7+ auto-downgrades to `OPPORTUNISTIC` after 30 min continuous scanning. 5 start/stops in 30 seconds disables scanning. `BLUETOOTH_PRIVILEGED` (signature-level) bypasses restrictions.

### 3.2 ScanFilter Builder Methods

| Method | Description |
|--------|-------------|
| `setDeviceName(String)` | Exact name match |
| `setDeviceAddress(String)` | Exact MAC match |
| `setServiceUuid(ParcelUuid)` | Service UUID match |
| `setServiceUuid(ParcelUuid, ParcelUuid mask)` | UUID with mask |
| `setManufacturerData(int, byte[])` | Manufacturer ID + data |
| `setManufacturerData(int, byte[], byte[] mask)` | With mask |
| `setAdvertisingDataType(int)` | By ad data type (API 33) |

**Key manufacturer IDs**:
- `0x004C` -- Apple (AirTag, Find My)
- `0x0075` -- Samsung (SmartTag)
- `0x00C7` -- Tile (Life360 trackers)
- `0x00E0` -- Google (Fast Pair)
- `0x0059` -- Nordic Semiconductor (Flipper BLE)
- `0x0006` -- Microsoft (Swift Pair)
- `0x038F` -- Chipolo

### 3.3 ScanRecord Methods

| Method | Return | Description |
|--------|--------|-------------|
| `getServiceUuids()` | List\<ParcelUuid\> | All service UUIDs |
| `getManufacturerSpecificData()` | SparseArray\<byte[]\> | All manufacturer data |
| `getManufacturerSpecificData(int)` | byte[] | Specific manufacturer data |
| `getServiceData()` | Map\<ParcelUuid, byte[]\> | All service data |
| `getTxPowerLevel()` | int | TX power in dBm |
| `getDeviceName()` | String | Local name |
| `getAdvertiseFlags()` | int | Advertising flags |
| `getBytes()` | byte[] | Raw packet bytes |

**Apple (0x004C) manufacturer data**: Byte 0 = type (0x07 = AirTag/Find My, 0x12 = FindMy, 0x10 = Nearby).

### 3.4 BLE ScanResult Fields

| Method | Return | API | Description |
|--------|--------|-----|-------------|
| `getDevice()` | BluetoothDevice | 21 | Device info |
| `getRssi()` | int | 21 | RSSI in dBm |
| `getScanRecord()` | ScanRecord | 21 | Advertising data |
| `getTimestampNanos()` | long | 21 | Timestamp since boot |
| `getAdvertisingSid()` | int | 26 | Advertising set ID |
| `getTxPower()` | int | 26 | TX power in dBm |
| `getPrimaryPhy()` | int | 26 | Primary PHY |
| `getSecondaryPhy()` | int | 26 | Secondary PHY |
| `getPeriodicAdvertisingInterval()` | int | 26 | Periodic interval |
| `isConnectable()` | boolean | 26 | Accepts connections |
| `isLegacy()` | boolean | 26 | Legacy advertisement |

### 3.5 Companion Device Manager (API 26)

**Class**: `android.companion.CompanionDeviceManager`

| Method | API | Description |
|--------|-----|-------------|
| `associate(AssociationRequest, Callback, Handler)` | 26 | Associate with device |
| `startObservingDevicePresence(String)` | 31 | **Monitor device presence** |
| `stopObservingDevicePresence(String)` | 31 | Stop monitoring |

**`startObservingDevicePresence()` (API 31)**: Once associated with a suspicious BLE device, the system delivers `CompanionDeviceService.onDeviceAppeared()` and `onDeviceDisappeared()` callbacks even in the background. Survives Doze mode and app standby. Does not consume scan quota.

---

## 4. Location & GNSS APIs

### 4.1 GnssStatus (API 24)

Per-satellite methods:

| Method | Description |
|--------|-------------|
| `getSatelliteCount()` | Total visible satellites |
| `getConstellationType(int)` | `CONSTELLATION_GPS` (1), `_SBAS` (2), `_GLONASS` (3), `_QZSS` (4), `_BEIDOU` (5), `_GALILEO` (6), `_IRNSS` (7) |
| `getSvid(int)` | Satellite Vehicle ID |
| `getCn0DbHz(int)` | Carrier-to-noise in dB-Hz |
| `getElevationDegrees(int)` | Elevation (0-90) |
| `getAzimuthDegrees(int)` | Azimuth (0-360) |
| `hasEphemerisData(int)` | Ephemeris available |
| `hasAlmanacData(int)` | Almanac available |
| `usedInFix(int)` | Used in position fix |
| `hasCarrierFrequencyHz(int)` | Carrier freq available (API 26) |
| `getCarrierFrequencyHz(int)` | Carrier frequency (API 26) |
| `hasBasebandCn0DbHz(int)` | Baseband C/N0 available (API 30) |
| `getBasebandCn0DbHz(int)` | Baseband C/N0 (API 30) |

### 4.2 GnssMeasurement -- Raw Measurements (API 24)

**Permission**: `ACCESS_FINE_LOCATION`
**Mandatory**: API 29+ (devices with hardware year 2016+)

| Method | Return | API | Description |
|--------|--------|-----|-------------|
| `getCn0DbHz()` | double | 24 | Carrier-to-noise (10-50 dB-Hz) |
| `getBasebandCn0DbHz()` | double | 30 | Baseband C/N0 |
| `getAutomaticGainControlLevelDb()` | double | 24 | **AGC level -- KEY for spoofing** |
| `getConstellationType()` | int | 24 | GPS, GLONASS, Galileo, BeiDou, etc. |
| `getSvid()` | int | 24 | Satellite vehicle ID |
| `getCarrierFrequencyHz()` | double | 24 | Carrier frequency |
| `getCodeType()` | String | 31 | Signal code type ("C1C", "L1C", "B1I") |
| `getMultipathIndicator()` | int | 24 | `MULTIPATH_INDICATOR_DETECTED`, `_NOT_DETECTED`, `_UNKNOWN` |
| `getPseudorangeRateMetersPerSecond()` | double | 24 | Pseudorange rate |
| `getAccumulatedDeltaRangeMeters()` | double | 24 | Carrier phase |
| `getFullInterSignalBiasNanos()` | long | 30 | Inter-signal bias |

### GNSS Spoofing Detection Methods

1. **AGC Analysis**: `getAutomaticGainControlLevelDb()` -- During spoofing, AGC is elevated because the receiver processes both real and spoofed signals. Sudden AGC increase across all satellites simultaneously = strong spoofing indicator.

2. **C/N0 Uniformity**: Calculate variance of `getCn0DbHz()` across visible satellites. Normal: 5-15 dB spread. Spoofed: variance < 2 dB (single transmitter generates all fake signals).

3. **GNSS vs Network Location**: Compare GNSS-derived position with `LocationManager.NETWORK_PROVIDER`. Significant divergence indicates spoofing.

4. **Constellation Cross-Check**: Compare positions from different constellations independently (GPS-only vs Galileo-only vs BeiDou-only). Spoofer targeting one constellation produces inconsistent positions.

### 4.3 GnssClock (API 24)

| Method | Return | Description |
|--------|--------|-------------|
| `getTimeNanos()` | long | Receiver internal time |
| `getFullBiasNanos()` | long | Full bias (hardware clock vs GPS time) |
| `getBiasNanos()` | double | Sub-nanosecond bias |
| `getDriftNanosPerSecond()` | double | Clock drift rate |
| `getHardwareClockDiscontinuityCount()` | int | **Clock discontinuity counter -- spoofing re-lock** |

**`getHardwareClockDiscontinuityCount()`**: Increments on clock discontinuities. During spoofing, the receiver re-locks to the spoofed signal, causing discontinuities. Increasing count during stable conditions is suspicious.

### 4.4 GnssNavigationMessage (API 24)

Navigation message type constants:
- `TYPE_GPS_L1CA` (0x0101) -- GPS L1 C/A
- `TYPE_GPS_L2CNAV` (0x0102) -- GPS L2 CNAV
- `TYPE_GPS_L5CNAV` (0x0103) -- GPS L5 CNAV
- `TYPE_GAL_I` (0x0601) -- **Galileo I/NAV (used by OSNMA)**
- `TYPE_GAL_F` (0x0602) -- Galileo F/NAV
- `TYPE_GLO_L1CA` (0x0301) -- GLONASS L1 C/A
- `TYPE_BDS_D1` (0x0501) -- BeiDou D1
- `TYPE_BDS_CNAV1` (0x0503) -- BeiDou CNAV1

**Galileo OSNMA**: By capturing `TYPE_GAL_I` messages and parsing raw `getData()` bytes, OSNMA authentication data can be extracted from Galileo I/NAV subframes. OSNMA became operational July 24, 2025. No public Android API for OSNMA directly -- requires implementing the verification algorithm from raw navigation message bytes.

### 4.5 Barometric Altitude Verification

**TYPE_PRESSURE** sensor returns atmospheric pressure in hPa.

```
altitude = 44330 * (1 - (pressure / 1013.25)^(1/5.255))
```

Compare with GNSS altitude. Discrepancy >50m (accounting for weather) = strong spoofing indicator.

---

## 5. Audio & Ultrasonic APIs

### 5.1 AudioRecord for Ultrasonic Detection

**Permission**: `RECORD_AUDIO`

**AudioSource types** (ordered by preference for ultrasonic):

| Source | Value | API | Description |
|--------|-------|-----|-------------|
| `UNPROCESSED` | 9 | 24 | **Best for ultrasonic** -- bypasses AGC, NS, AEC |
| `VOICE_RECOGNITION` | 6 | 1 | Minimal processing, no AGC |
| `MIC` | 1 | 1 | Default with full processing |
| `CAMCORDER` | 5 | 1 | Camera mic, good frequency response |

`UNPROCESSED` is critical: Android's audio pipeline filters frequencies above ~16 kHz. Check availability with `AudioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)`.

**Configuration**: Sample rate 44100 Hz, `CHANNEL_IN_MONO`, `ENCODING_PCM_FLOAT` (API 21). FFT window 4096 samples = ~10.7 Hz resolution. Monitor bins 1669-2042 (18-22 kHz range).

**Goertzel algorithm**: More efficient than FFT when detecting specific frequencies (e.g., SilverPush beacon frequencies). O(N) per frequency bin.

### 5.2 Visualizer (API 9)

**Class**: `android.media.audiofx.Visualizer`

Captures audio **output** (what's being played, not microphone). Can detect if apps are emitting ultrasonic tracking beacons through the speaker. Uses session ID 0 for global audio mix (elevated privileges needed).

### 5.3 SoundTrigger (System API)

`@SystemApi` / `@hide`. Hardware-accelerated always-on audio detection on dedicated DSP. System/OEM apps can register custom sound models for ultrasonic beacon detection with minimal battery impact.

---

## 6. Sensor APIs for Surveillance Detection

### 6.1 Relevant Sensor Types

| Sensor | Constant | API | Surveillance Use |
|--------|----------|-----|-----------------|
| `TYPE_ACCELEROMETER` | 1 | 3 | Motion detection, verify movement matches cell handoffs |
| `TYPE_GYROSCOPE` | 4 | 3 | Rotation detection, complement accelerometer |
| `TYPE_MAGNETIC_FIELD` | 2 | 3 | **Detect strong RF emitters, magnetic card skimmers** |
| `TYPE_MAGNETIC_FIELD_UNCALIBRATED` | 14 | 18 | Raw magnetometer, better for anomaly detection |
| `TYPE_PRESSURE` | 6 | 3 | Barometric altitude, verify GNSS for spoofing |
| `TYPE_PROXIMITY` | 8 | 3 | Pocket detection (alert context) |
| `TYPE_LIGHT` | 5 | 3 | Camera flash / IR illuminator detection |
| `TYPE_SIGNIFICANT_MOTION` | 17 | 18 | Wake-up on significant movement |
| `TYPE_STATIONARY_DETECT` | 29 | 24 | Device stationary confirmation |
| `TYPE_MOTION_DETECT` | 30 | 24 | Device in motion confirmation |

### 6.2 Magnetometer for Surveillance

Earth's natural field: ~25-65 uT. Readings >100 uT near payment terminals suggest NFC skimmer overlay. High-power RF transmitters create detectable magnetic fields. Hidden cameras with motors/speakers create magnetic signatures.

`TYPE_MAGNETIC_FIELD_UNCALIBRATED` (API 18): Returns raw data without calibration offset. Better for detecting small anomalies. Returns 6 values: xyz_uncalibrated + xyz_bias.

### 6.3 Motion + Cellular Correlation

Cell tower handoffs should correlate with movement. If `CellInfo` changes rapidly while `TYPE_STATIONARY_DETECT` confirms the device is stationary, this strongly suggests a cell simulator probing the device.

---

## 7. Network & Connectivity APIs

### 7.1 NetworkCapabilities Key Constants

| Capability | Description | Surveillance Use |
|-----------|-------------|-----------------|
| `NET_CAPABILITY_NOT_VPN` | Not a VPN | Detect VPN traffic routing |
| `NET_CAPABILITY_VALIDATED` | Internet confirmed | Detect captive portals (rogue APs) |
| `NET_CAPABILITY_CAPTIVE_PORTAL` | Behind captive portal | Rogue AP detection |
| `NET_CAPABILITY_NOT_ROAMING` | Not roaming | Roaming detection |

**Transport types**: `TRANSPORT_WIFI` (0), `TRANSPORT_CELLULAR` (1), `TRANSPORT_BLUETOOTH` (2), `TRANSPORT_ETHERNET` (3), `TRANSPORT_VPN` (4), `TRANSPORT_WIFI_AWARE` (5), `TRANSPORT_USB` (8, API 31), **`TRANSPORT_SATELLITE` (9, API 34)**.

### 7.2 LinkProperties

| Method | Return | Description |
|--------|--------|-------------|
| `getDnsServers()` | List\<InetAddress\> | **DNS hijacking detection** |
| `getRoutes()` | List\<RouteInfo\> | Network routes |
| `getHttpProxy()` | ProxyInfo | **Proxy injection detection** |
| `isPrivateDnsActive()` | boolean | Private DNS (DoT) active (API 28) |
| `getPrivateDnsServerName()` | String | Private DNS server (API 28) |
| `getMtu()` | int | MTU |

### 7.3 NetworkStatsManager (API 23)

**Permission**: `PACKAGE_USAGE_STATS` (granted via Settings)

Per-app traffic monitoring for exfiltration detection. An app with no need for network access transmitting significant data is suspicious.

### 7.4 NsdManager (Network Service Discovery, API 16)

Service types for surveillance detection:
- `_rtsp._tcp` -- RTSP streams (security cameras)
- `_http._tcp` -- Web servers (hidden camera config pages)
- `_ssh._tcp` -- SSH servers (compromised devices)
- `_airplay._tcp` -- AirPlay
- `_googlecast._tcp` -- Chromecast

**Surveillance use**: Hidden cameras often expose RTSP or HTTP services on the LAN.

### 7.5 VpnService (API 14)

Creates a TUN interface capturing all device network traffic without root. Enables DNS monitoring, TLS SNI inspection, traffic flow analysis, and exfiltration detection.

---

## 8. USB & NFC APIs

### 8.1 UsbManager (API 12)

**UsbDevice** fields: `getVendorId()`, `getProductId()`, `getManufacturerName()` (API 21), `getProductName()` (API 21), `getSerialNumber()` (API 21).

**Key VID/PID for surveillance tools**:
- Flipper Zero: VID `0x0483`, PID `0x5740`
- HackRF One: VID `0x1D50`, PID `0x604B` / `0x6089`
- RTL-SDR: VID `0x0BDA`, PID `0x2832` / `0x2838`
- Proxmark3: VID `0x9AC4`, PID `0x4B8F`
- FTDI: VID `0x0403`
- CP2102: VID `0x10C4`, PID `0xEA60`

**Broadcasts**: `ACTION_USB_DEVICE_ATTACHED`, `ACTION_USB_DEVICE_DETACHED`.

### 8.2 NFC (API 10+)

Reader mode flags: `FLAG_READER_NFC_A` (ISO 14443-3A), `FLAG_READER_NFC_B`, `FLAG_READER_NFC_F` (FeliCa), `FLAG_READER_NFC_V` (ISO 15693).

**NFC skimmer detection**: Use `IsoDep.transceive(byte[])` to send APDU commands to NFC devices. A legitimate payment terminal responds with specific sequences; a skimmer overlay responds differently or fails protocol validation.

---

## 9. Security APIs

### 9.1 PackageManager for Stalkerware Detection

Check for known stalkerware packages and suspicious permission combinations:
- App with `RECORD_AUDIO` + `ACCESS_FINE_LOCATION` + `READ_SMS` + `READ_CALL_LOG` + no launcher icon
- `ApplicationInfo.flags & FLAG_SYSTEM == 0` (non-system)
- `getLaunchIntentForPackage(packageName) == null` (no launcher entry)

### 9.2 DevicePolicyManager for MDM Detection

| Method | API | Description |
|--------|-----|-------------|
| `isDeviceOwnerApp(String)` | 18 | Check device owner |
| `isProfileOwnerApp(String)` | 21 | Check profile owner |
| `getActiveAdmins()` | 8 | List active device admins |

Unexpected device admin entries indicate potential surveillance MDM.

### 9.3 Certificate Monitoring

Detect user-installed CA certificates (corporate MITM):
```kotlin
val ks = KeyStore.getInstance("AndroidCAStore")
ks.load(null)
// Aliases starting with "user:" are user-installed certificates
```

---

## 10. System & Process APIs

### 10.1 AppOpsManager (API 19)

Monitor when apps use sensitive permissions in real-time:

| Op String | Description |
|-----------|-------------|
| `OPSTR_CAMERA` | Camera access |
| `OPSTR_RECORD_AUDIO` | Microphone access |
| `OPSTR_FINE_LOCATION` | Fine location |
| `OPSTR_READ_PHONE_STATE` | Phone state |
| `OPSTR_READ_SMS` | SMS access |
| `OPSTR_READ_CONTACTS` | Contacts access |
| `OPSTR_READ_CALL_LOG` | Call log access |

`startWatchingMode(op, packageName, callback)` registers real-time notifications when any app uses a permission.

### 10.2 UsageStatsManager (API 21)

**Permission**: `PACKAGE_USAGE_STATS`

Identify suspicious app patterns: always background/never foreground, launches at boot, runs for extended periods, frequent foreground/background transitions.

### 10.3 Build Class

| Field/Method | Description |
|-------------|-------------|
| `Build.MANUFACTURER` | Device manufacturer |
| `Build.MODEL` | Device model |
| `Build.getRadioVersion()` | **Baseband firmware version** (API 14) |
| `Build.VERSION.SECURITY_PATCH` | Security patch date (API 23) |

`getRadioVersion()`: Different baseband versions have different IMSI catcher vulnerabilities and cellular security feature support.

---

## 11. Power & Battery APIs

### 11.1 BatteryManager

**`ACTION_BATTERY_CHANGED` extras**: `EXTRA_STATUS`, `EXTRA_LEVEL`, `EXTRA_VOLTAGE`, `EXTRA_TEMPERATURE`.

**Property queries** (API 21): `BATTERY_PROPERTY_CURRENT_NOW` (instantaneous uA), `BATTERY_PROPERTY_CURRENT_AVERAGE` (average uA).

**Anomaly detection**: Rapid drain indicates surveillance app accessing GPS/camera/mic, radio jammer forcing increased transmit power, or IMSI catcher causing repeated registration attempts.

### 11.2 Foreground Service Types (Android 14+)

| Type | Constant | Use Case |
|------|----------|----------|
| `FOREGROUND_SERVICE_TYPE_LOCATION` | 8 | GPS/cell monitoring |
| `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` | 16 | BLE scanning, USB |
| `FOREGROUND_SERVICE_TYPE_MICROPHONE` | 128 | Ultrasonic detection |
| `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` | 1073741824 | Catch-all |

---

## 12. Privilege Mode API Access Summary

| API | Sideload | System | OEM |
|-----|----------|--------|-----|
| `getAllCellInfo()` | Yes | Yes | Yes |
| `getServiceState()` | Yes | Yes | Yes |
| `isUsingCarrierAggregation()` | Yes | Yes | Yes |
| `getTimingAdvance()` | Yes | Yes | Yes |
| `RegistrationFailedListener` | No | Yes | Yes |
| `BarringInfoListener` | No | Yes | Yes |
| `getImei()` / `getSubscriberId()` | No | No | Yes |
| `setScanThrottleEnabled()` | No | Yes | Yes |
| `WifiScanner` continuous scan | No | Yes | Yes |
| `BLUETOOTH_PRIVILEGED` | No | Yes | Yes |
| GnssMeasurement (raw GNSS) | Yes | Yes | Yes |
| `SoundTrigger` | No | No | Yes |
| `AppOpsManager.startWatchingMode()` | Yes | Yes | Yes |
| `NetworkStatsManager` (all UIDs) | No | No | Yes |

---

## 13. Top Underutilized APIs

1. **`TelephonyCallback.RegistrationFailedListener`** (API 31, system) -- Registration failure cause codes reveal IMSI catcher interaction
2. **`ServiceState.isUsingCarrierAggregation()`** (API 28, public) -- IMSI catchers cannot perform CA
3. **`CellSignalStrengthLte.getTimingAdvance()`** (API 17, public) -- Distance estimation to cell tower
4. **`CellSignalStrengthNr.getTimingAdvanceMicros()`** (API 34, public) -- 5G distance estimation
5. **`ScanResult.informationElements`** (API 30, public) -- Raw 802.11 IE parsing for evil twin detection
6. **`GnssMeasurement.getAutomaticGainControlLevelDb()`** (API 24, public) -- Key GNSS spoofing indicator
7. **`GnssClock.getHardwareClockDiscontinuityCount()`** (API 24, public) -- Spoofing signal re-lock
8. **`WifiP2pManager.discoverPeers()`** (API 14, public) -- Find P2P devices invisible in WiFi scans
9. **`CompanionDeviceManager.startObservingDevicePresence()`** (API 31, public) -- Power-efficient tracker monitoring
10. **`NsdManager.discoverServices("_rtsp._tcp")`** (API 16, public) -- Find hidden cameras on LAN
11. **`GnssNavigationMessage` TYPE_GAL_I** (API 24, public) -- Raw Galileo I/NAV for OSNMA
12. **`LinkProperties.getDnsServers()`** (API 21, public) -- DNS hijacking detection
13. **`AppOpsManager.startWatchingMode()`** (API 19, public) -- Real-time surveillance app monitoring
14. **`NetworkCapabilities.TRANSPORT_SATELLITE`** (API 34, public) -- Detect forced satellite connection
15. **`NetworkRegistrationInfo.getNrState()`** (API 30, public) -- 5G NSA/SA state transitions

---

## Sources

- [TelephonyManager API](https://developer.android.com/reference/android/telephony/TelephonyManager)
- [CellIdentityLte API](https://developer.android.com/reference/android/telephony/CellIdentityLte)
- [CellIdentityNr API](https://developer.android.com/reference/android/telephony/CellIdentityNr)
- [TelephonyCallback API](https://developer.android.com/reference/android/telephony/TelephonyCallback)
- [NetworkRegistrationInfo API](https://developer.android.com/reference/android/telephony/NetworkRegistrationInfo)
- [ServiceState API](https://developer.android.com/reference/android/telephony/ServiceState)
- [GnssMeasurement API](https://developer.android.com/reference/android/location/GnssMeasurement)
- [Raw GNSS Measurements](https://developer.android.com/develop/sensors-and-location/sensors/gnss)
- [Android Raw GNSS Anti-Spoofing (Stanford)](https://web.stanford.edu/group/scpnt/gpslab/pubs/papers/MirallesIONGNSS2018Android_AntiSpoof.pdf)
- [GNSS Jamming and Spoofing Detection (ION)](https://navi.ion.org/content/69/3/navi.537)
- [GnssNavigationMessage API](https://developer.android.com/reference/android/location/GnssNavigationMessage)
- [GnssStatus API](https://developer.android.com/reference/android/location/GnssStatus)
- [GnssClock API](https://developer.android.com/reference/android/location/GnssClock)
- [Galileo OSNMA Operational](https://defence-industry-space.ec.europa.eu/galileo-leads-way-gnss-spoofing-protection-osnma-2025-07-22_en)
- [WifiManager API](https://developer.android.com/reference/android/net/wifi/WifiManager)
- [WiFi Scanning](https://developer.android.com/develop/connectivity/wifi/wifi-scan)
- [WifiRttManager API](https://developer.android.com/reference/android/net/wifi/rtt/WifiRttManager)
- [ScanResult API](https://developer.android.com/reference/android/net/wifi/ScanResult)
- [BluetoothLeScanner API](https://developer.android.com/reference/android/bluetooth/le/BluetoothLeScanner)
- [ScanSettings API](https://developer.android.com/reference/android/bluetooth/le/ScanSettings)
- [ScanRecord API](https://developer.android.com/reference/android/bluetooth/le/ScanRecord)
- [CompanionDeviceManager](https://developer.android.com/develop/connectivity/bluetooth/companion-device-pairing)
- [WifiP2pManager API](https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pManager)
- [NsdManager API](https://developer.android.com/reference/android/net/nsd/NsdManager)
- [NetworkCapabilities API](https://developer.android.com/reference/android/net/NetworkCapabilities)
- [LinkProperties API](https://developer.android.com/reference/android/net/LinkProperties)
- [VpnService API](https://developer.android.com/reference/android/net/VpnService)
- [AppOpsManager API](https://developer.android.com/reference/android/app/AppOpsManager)
- [DevicePolicyManager API](https://developer.android.com/reference/android/app/admin/DevicePolicyManager)
- [UsageStatsManager API](https://developer.android.com/reference/android/app/usage/UsageStatsManager)
- [BatteryManager API](https://developer.android.com/reference/android/os/BatteryManager)
- [Foreground Service Types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Privileged Permission Allowlist](https://source.android.com/docs/core/permissions/perms-allowlist)
- [AIMSICD Project](https://github.com/CellularPrivacy/Android-IMSI-Catcher-Detector)
- [NetMonster Core -- 5G NR NSA](https://deepwiki.com/mroczis/netmonster-core/7.2-5g-nr-nsa-detection)
- [PilferShush Ultrasonic Detection](https://github.com/YalePrivacyLab/PilferShush_prod)
- [PCAPdroid Network Monitor](https://github.com/emanuele-f/PCAPdroid)
