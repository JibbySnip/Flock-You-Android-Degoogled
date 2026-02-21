# Android AOSP Satellite Connectivity: Technical Reference

## 1. How AOSP Distinguishes Between Satellite Providers

Android does **not** have a single unified provider-identification API. Satellite providers are distinguished through a combination of signals: PLMN identifiers, network operator name strings, NTN radio technology type, NRARFCN band ranges, and signal characteristics.

### PLMN Identifiers (MCC + MNC)

**Satellite-Specific PLMNs (MCC 901 International Shared Codes)**

| PLMN | Operator | Protocol |
|------|----------|----------|
| 901-03 | Iridium | Proprietary |
| 901-05 | Thuraya RMSS | Proprietary |
| 901-06 | Thuraya | Proprietary |
| 901-08 | SpaceX | D2D (future global) |
| 901-10 | Omnispace | NTN |
| 901-11 | Inmarsat | Proprietary/GEO |
| 901-98 | Skylo | NB-IoT NTN |

Source: [ITU-T E.212 MCC 901 Registry](https://www.itu.int/net/ITU-T/inrdb/e212_901.aspx)

**Satellite Services Using Terrestrial Carrier PLMNs**

| PLMN | Carrier | Satellite Partner |
|------|---------|-------------------|
| 310-260 | T-Mobile US | SpaceX Starlink |
| 310-830 | T-Mobile/Starlink | SpaceX (Starlink-specific MNC) |
| 310-210 | T-Mobile/Starlink | SpaceX (alternate MNC) |
| 310-410 | AT&T | AST SpaceMobile |
| 001-001 | Test PLMN | Any (test mode) |

**Key implication:** Because D2D satellite services (Starlink, AST SpaceMobile) use the same PLMNs as their terrestrial carrier partners, PLMN-based detection alone is insufficient. Apps must combine PLMN with `ServiceState.isUsingNonTerrestrialNetwork()`, operator name string matching, NRARFCN/EARFCN band identification, and signal strength characteristics.

### Network Name Detection

The operator name string from `ServiceState` or `TelephonyDisplayInfo` reveals the provider:

| Provider | Known Network Names |
|----------|-------------------|
| T-Mobile Starlink | "T-Mobile SpaceX", "T-Sat+Starlink", "T-Satellite", "T-SAT" |
| Skylo | "Skylo", "Skylo NTN", "Satellite SOS" |
| Generic | "SAT", "Satellite", "NTN", "D2D" |

### NTN Radio Technology Type

`SatelliteManager` defines `NTRadioTechnology` constants:

| Constant | Value | Usage |
|----------|-------|-------|
| `NT_RADIO_TECHNOLOGY_UNKNOWN` | 0 | Default |
| `NT_RADIO_TECHNOLOGY_NB_IOT_NTN` | 1 | Skylo/Pixel (NB-IoT over NTN) |
| `NT_RADIO_TECHNOLOGY_NR_NTN` | 2 | Future NR over NTN |
| `NT_RADIO_TECHNOLOGY_EMTC_NTN` | 3 | eMTC over NTN |
| `NT_RADIO_TECHNOLOGY_PROPRIETARY` | 4 | Apple/Globalstar |

---

## 2. Android Satellite APIs by Version

### Android 14 (API 34) - Introduction of SatelliteManager

- **`android.telephony.satellite.SatelliteManager`**: New system service (`Context.SATELLITE_SERVICE`) introduced behind `@SystemApi` and `PackageManager.FEATURE_TELEPHONY_SATELLITE`.
- Most methods require `Manifest.permission.SATELLITE_COMMUNICATION` (system-level).
- Key methods: `requestIsSupported()`, `requestEnabled()`, `requestCapabilities()`, `registerForModemStateChanged()`, `sendDatagram()`, `provisionService()`

### Android 15 (API 35) - Satellite Connectivity for Apps

- **`ServiceState.isUsingNonTerrestrialNetwork()`**: New public API that returns `true` if any `NetworkRegistrationInfo` reports a non-terrestrial network. Flagged with `FLAG_CARRIER_ENABLED_SATELLITE_FLAG`.
- **`NetworkCapabilities.TRANSPORT_SATELLITE = 10`**: New transport type constant to identify satellite network transport.
- **`NetworkCapabilities.NET_CAPABILITY_NOT_BANDWIDTH_CONSTRAINED = 37`**: Set by default on all networks; removed on bandwidth-constrained satellite networks.
- SMS/MMS/RCS over satellite supported.
- System notification shown when connected to satellite.

### Android 16 (API 36) - Expanded Satellite App Support

- **`PROPERTY_SATELLITE_DATA_OPTIMIZED`**: Manifest metadata key for apps to declare satellite optimization.
- **Satellite Connectivity Hub**: New Settings page listing satellite-capable apps, differentiated by NB-NTN vs LTE-NTN.
- **`SatelliteStateChangeListener`**: New **public** (non-@SystemApi) callback interface for monitoring satellite state changes.
- **Quick Settings Tile**: "Satellite" tile with On/Available/Not available states.
- **Satellite location sharing**: First satellite-based location sharing (beyond SOS).

---

## 3. SatelliteManager Deep Dive

Class: `android.telephony.satellite.SatelliteManager`

```java
@SystemService(Context.SATELLITE_SERVICE)
@FlaggedApi(Flags.FLAG_SATELLITE_STATE_CHANGE_LISTENER)
@RequiresFeature(PackageManager.FEATURE_TELEPHONY_SATELLITE)
public final class SatelliteManager
```

### Modem State Constants (`@SatelliteModemState`)

| Constant | Value | Description |
|----------|-------|-------------|
| `SATELLITE_MODEM_STATE_IDLE` | 0 | Modem idle |
| `SATELLITE_MODEM_STATE_LISTENING` | 1 | Scanning for satellites |
| `SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING` | 2 | Actively transferring |
| `SATELLITE_MODEM_STATE_DATAGRAM_RETRYING` | 3 | Retrying failed transfer |
| `SATELLITE_MODEM_STATE_OFF` | 4 | Modem off |
| `SATELLITE_MODEM_STATE_UNAVAILABLE` | 5 | Modem unavailable |
| `SATELLITE_MODEM_STATE_NOT_CONNECTED` | 6 | Active but not connected |
| `SATELLITE_MODEM_STATE_CONNECTED` | 7 | Connected to satellite |
| `SATELLITE_MODEM_STATE_ENABLING_SATELLITE` | 8 | Enabling in progress |
| `SATELLITE_MODEM_STATE_DISABLING_SATELLITE` | 9 | Disabling in progress |
| `SATELLITE_MODEM_STATE_UNKNOWN` | -1 | Unknown state |

### Modem State Machine

```
UNKNOWN -> IDLE -> LISTENING -> NOT_CONNECTED -> CONNECTED
  |          |        |             |                |
  v          v        v             |                v
DISABLED <-- IDLE <-- IDLE <--------+      DATAGRAM_TRANSFERRING
                                                     |
                                                     v
                                              DATAGRAM_RETRYING
```

### Datagram Transfer States (`@SatelliteDatagramTransferState`)

| Constant | Value |
|----------|-------|
| `SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE` | 0 |
| `SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING` | 1 |
| `SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS` | 2 |
| `SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED` | 3 |
| `SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING` | 4 |
| `SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS` | 5 |
| `SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_NONE` | 6 |
| `SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_FAILED` | 7 |
| `SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT` | 8 |
| `SATELLITE_DATAGRAM_TRANSFER_STATE_UNKNOWN` | -1 |

### Datagram Types (`@DatagramType`)

| Constant | Value | Description |
|----------|-------|-------------|
| `DATAGRAM_TYPE_UNKNOWN` | 0 | Unknown |
| `DATAGRAM_TYPE_SOS_MESSAGE` | 1 | Emergency SOS |
| `DATAGRAM_TYPE_LOCATION_SHARING` | 2 | Location sharing |
| `DATAGRAM_TYPE_KEEP_ALIVE` | 3 | Keep alive |
| `DATAGRAM_TYPE_LAST_SOS_MESSAGE_STILL_NEED_HELP` | 4 | SOS follow-up (still need help) |
| `DATAGRAM_TYPE_LAST_SOS_MESSAGE_NO_HELP_NEEDED` | 5 | SOS follow-up (resolved) |
| `DATAGRAM_TYPE_SMS` | 6 | SMS message |
| `DATAGRAM_TYPE_CHECK_PENDING_INCOMING_SMS` | 7 | Check for incoming SMS |

### Key Public Methods

**State Management (Public in API 36+):**
```java
public void registerStateChangeListener(
    @NonNull Executor executor,
    @NonNull SatelliteStateChangeListener listener)

public void unregisterStateChangeListener(
    @NonNull SatelliteStateChangeListener listener)
```

**Enable/Disable (@SystemApi, requires SATELLITE_COMMUNICATION):**
```java
public void requestEnabled(
    @NonNull EnableRequestAttributes attributes,
    @NonNull Executor executor,
    @SatelliteResult @NonNull Consumer<Integer> resultListener)

public void requestIsEnabled(
    @NonNull Executor executor,
    @NonNull OutcomeReceiver<Boolean, SatelliteException> callback)
```

**Support & Capabilities (@SystemApi):**
```java
public void requestIsSupported(
    @NonNull Executor executor,
    @NonNull OutcomeReceiver<Boolean, SatelliteException> callback)

public void requestCapabilities(
    @NonNull Executor executor,
    @NonNull OutcomeReceiver<SatelliteCapabilities, SatelliteException> callback)
```

**Modem State Monitoring (@SystemApi):**
```java
public int registerForModemStateChanged(
    @NonNull Executor executor,
    @NonNull SatelliteModemStateCallback callback)

public void unregisterForModemStateChanged(
    @NonNull SatelliteModemStateCallback callback)
```

**Datagram Operations (@SystemApi):**
```java
public void sendDatagram(
    @NonNull SatelliteDatagram datagram,
    @SatelliteResult @NonNull Consumer<Integer> resultListener)

public int registerForIncomingDatagram(
    @NonNull Executor executor,
    @NonNull SatelliteDatagramCallback callback)

public void pollPendingDatagrams(
    @NonNull Executor executor,
    @SatelliteResult @NonNull Consumer<Integer> resultListener)
```

### Result Codes (`@SatelliteResult`)

30 result codes (0-30), including:
- `SATELLITE_RESULT_SUCCESS = 0`
- `SATELLITE_RESULT_MODEM_ERROR = 4`
- `SATELLITE_RESULT_SERVICE_NOT_PROVISIONED = 13`
- `SATELLITE_RESULT_NOT_AUTHORIZED = 19`
- `SATELLITE_RESULT_NO_VALID_SATELLITE_SUBSCRIPTION = 30`

---

## 4. ServiceState NTN Reporting

### isUsingNonTerrestrialNetwork() Implementation

```java
@FlaggedApi(Flags.FLAG_CARRIER_ENABLED_SATELLITE_FLAG)
public boolean isUsingNonTerrestrialNetwork() {
    synchronized (mNetworkRegistrationInfos) {
        for (NetworkRegistrationInfo nri : mNetworkRegistrationInfos) {
            if (nri.isNonTerrestrialNetwork()) return true;
        }
    }
    return false;
}
```

### NetworkRegistrationInfo NTN Members

```java
private boolean mIsNonTerrestrialNetwork;

@FlaggedApi(Flags.FLAG_CARRIER_ENABLED_SATELLITE_FLAG)
public boolean isNonTerrestrialNetwork()

public void setIsNonTerrestrialNetwork(boolean isNonTerrestrialNetwork)

// Builder
@FlaggedApi(Flags.FLAG_CARRIER_ENABLED_SATELLITE_FLAG)
public Builder setIsNonTerrestrialNetwork(boolean isNonTerrestrialNetwork)

// Display: returns "NON-TERRESTRIAL" or "TERRESTRIAL"
public static String isNonTerrestrialNetworkToString(boolean isNonTerrestrialNetwork)
```

### Related ServiceState Constants

```java
RIL_RADIO_TECHNOLOGY_NB_IOT_NTN = 21  // "3GPP NB-IOT over Non-Terrestrial-Networks"
```

---

## 5. Direct-to-Device (D2D) vs Satellite SOS

### T-Mobile Starlink Direct-to-Cell (D2D)

| Attribute | Detail |
|-----------|--------|
| Protocol | **Non-standard** (not 3GPP NTN). Uses conventional LTE eNodeB payloads on Starlink satellites |
| Spectrum | PCS G Block (UL: 1910-1915, DL: 1990-1995 MHz, 2x5 MHz). Recently acquired AWS-4 (2x20 MHz) and PCS H Block (2x5 MHz). AWS-4 standardized as **3GPP NR NTN band n252** (UL: 2000-2020, DL: 2180-2200 MHz) |
| EARFCN | Standard LTE EARFCN values (not NTN-specific NRARFCN) |
| RSRP | Median -121 dBm (vs -97 dBm terrestrial) |
| Capabilities | SMS initially (Oct 2024 beta), voice/data planned. Limited data apps as of early 2026 |
| Coverage | US, Canada initially |
| Max throughput | ~3.1 Mbps per beam |
| Phone modification | None required - works with existing T-Mobile phones |
| PLMN | Same as T-Mobile terrestrial (310-260, 310-830, 310-210) |

### Skylo/Google Pixel Satellite SOS (NB-IoT NTN)

| Attribute | Detail |
|-----------|--------|
| Protocol | **3GPP Release 17** NB-IoT NTN (standards-compliant) |
| Spectrum | Licensed satellite bands (L-band/S-band) |
| Capabilities | Emergency SOS, location sharing, 2-way text messaging. Low data rate only |
| PLMN | 901-98 (Skylo global) |
| Modem | Requires NTN-capable modem (Exynos 5400, MediaTek MT6825, Snapdragon X80) |
| Coverage | US, Canada, EU, Australia, Japan |

### Comparison

| Feature | Starlink D2D | Skylo Satellite SOS |
|---------|-------------|-------------------|
| Standard | Non-3GPP NTN | 3GPP Rel-17 NB-IoT NTN |
| Modem requirement | None (standard LTE) | NTN-capable modem required |
| PLMN | Same as carrier | 901-98 (dedicated satellite) |
| Capacity | ~3 Mbps/beam | Low (kbps) |
| Use case | General messaging, apps | Emergency SOS, location |
| Phone modification | None needed | NTN modem hardware |
| Detection difficulty | High (looks like terrestrial LTE) | Low (dedicated PLMN + NTN flag) |

---

## 6. 3GPP NTN Frequency Bands

### FR1 NTN Bands (Release 17/18)

| Band | Uplink (MHz) | Downlink (MHz) | Duplex | Spectrum | Release |
|------|-------------|----------------|--------|----------|---------|
| **n252** | 2000-2020 | 2180-2200 | FDD | AWS-4 (Starlink) | Rel-18 |
| **n254** | 1610-1626.5 (L-band) | 2483.5-2500 (S-band) | FDD | L/S hybrid | Rel-18 |
| **n255** | 1626.5-1660.5 (L-band) | 1525-1559 (L-band) | FDD | L-band | Rel-17 |
| **n256** | 1980-2010 (S-band) | 2170-2200 (S-band) | FDD | S-band | Rel-17 |

### FR2 NTN Bands (Release 18, Ka-band)

| Band | Uplink (GHz) | Downlink (GHz) | Duplex | Release |
|------|-------------|----------------|--------|---------|
| **n510** | 27.5-28.35 | 17.7-20.2 | FDD | Rel-18 |
| **n511** | 28.35-30.0 | 17.7-20.2 | FDD | Rel-18 |
| **n512** | 27.5-30.0 | 17.7-20.2 | FDD | Rel-18 |

### Channel Bandwidth Support

n255 and n256 support: 5, 10, 15, 20 MHz (Rel-17) and 30 MHz (Rel-18), with subcarrier spacings of 15, 30, and 60 kHz.

### NRARFCN Ranges for NTN

| Band | NRARFCN Range |
|------|--------------|
| L-band (n253-n255) | 422000-434000 |
| S-band (n256) | 434001-440000 |

**Important:** Starlink D2D does **not** use NR-NTN NRARFCN values because it uses standard LTE (not NR-NTN). Only true 3GPP NTN connections (Skylo NB-IoT NTN, future NR-NTN services) use the dedicated NTN band NRARFCN ranges. Some devices may not expose NRARFCN for NB-IoT NTN connections at all, as NB-IoT uses EARFCN rather than NRARFCN.

---

## 7. Satellite Handoff/Switchover from Terrestrial

### 3GPP Standard Handoff

Per 3GPP Release 17, mobility procedures over satellite work the same as in terrestrial networks unless specified otherwise. The network decides when to hand over based on signal quality and availability.

### Android Detection of Handoff

1. **`TelephonyCallback.ServiceStateListener.onServiceStateChanged(ServiceState)`**: Called on every service state change. Check `serviceState.isUsingNonTerrestrialNetwork()` to detect transition.
2. **`NetworkRegistrationInfo` inspection**: After handoff, the `NetworkRegistrationInfo` list in `ServiceState` will have `isNonTerrestrialNetwork() == true` for the NTN registration.
3. **`ConnectivityManager` NetworkCallback**: When a satellite network becomes available, `onCapabilitiesChanged()` fires with `TRANSPORT_SATELLITE` and without `NET_CAPABILITY_NOT_BANDWIDTH_CONSTRAINED`.
4. **SatelliteManager modem state**: Transitions from `IDLE` -> `LISTENING` -> `NOT_CONNECTED` -> `CONNECTED`.

### Emergency Handover Types

```java
EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_SOS = 1   // Emergency SOS
EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_T911 = 2  // T911 (text to 911)
```

---

## 8. Telephony Callbacks for NTN Detection

### 1. TelephonyCallback.ServiceStateListener (API 31+)

```kotlin
val callback = object : TelephonyCallback(), TelephonyCallback.ServiceStateListener {
    override fun onServiceStateChanged(serviceState: ServiceState) {
        // API 35+: Direct NTN check
        if (Build.VERSION.SDK_INT >= 35) {
            val isNTN = serviceState.isUsingNonTerrestrialNetwork()
        }
        // API 30+: Inspect NetworkRegistrationInfo list
        serviceState.getNetworkRegistrationInfoList()?.forEach { nri ->
            // Check for NTN indicators
        }
    }
}
telephonyManager.registerTelephonyCallback(executor, callback)
```

### 2. ConnectivityManager.NetworkCallback

```kotlin
val request = NetworkRequest.Builder()
    .addCapability(NET_CAPABILITY_INTERNET)
    .removeCapability(NET_CAPABILITY_NOT_BANDWIDTH_CONSTRAINED)
    .build()

val callback = object : ConnectivityManager.NetworkCallback() {
    override fun onCapabilitiesChanged(network: Network, nc: NetworkCapabilities) {
        val isSatellite = nc.hasTransport(NetworkCapabilities.TRANSPORT_SATELLITE) // = 10
        val isConstrained = !nc.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_NOT_BANDWIDTH_CONSTRAINED) // = 37
    }
}
```

### 3. SatelliteManager.SatelliteModemStateCallback (@SystemApi)

```java
satelliteManager.registerForModemStateChanged(executor, state -> {
    // IDLE -> LISTENING -> NOT_CONNECTED -> CONNECTED -> DATAGRAM_TRANSFERRING
});
```

### 4. SatelliteStateChangeListener (API 36+, public)

```java
satelliteManager.registerStateChangeListener(executor, listener);
```

### Detection for Non-System Apps

Since most `SatelliteManager` methods require system-level `SATELLITE_COMMUNICATION` permission, non-system (sideloaded) apps detect satellite via:

1. **`ServiceState.isUsingNonTerrestrialNetwork()`** - requires `READ_PHONE_STATE`
2. **`NetworkCapabilities.hasTransport(TRANSPORT_SATELLITE)`** - via `ConnectivityManager`
3. **`TelephonyCallback.ServiceStateListener`** - receive `ServiceState` changes
4. **CellInfo inspection** - `CellInfoNr` with NRARFCN in NTN band ranges

---

## 9. Vendor-Specific Extensions

### Qualcomm (Snapdragon X75/X80/X85)

| Modem | NTN Support | Notes |
|-------|-------------|-------|
| X75 | None | Snapdragon 8 Gen 3 |
| X80 | **Integrated NB-NTN** | Snapdragon 8 Gen 4 (Elite). 3GPP Rel-17, Rel-18 ready. Partners with Skylo |
| X85 | Expected NR-NTN broadband | Announced, not yet shipped |

Qualcomm's satellite support is exposed through standard Android telephony HAL (IRadio AIDL v2.1+) with vendor-specific extensions. No public NTN-specific Qualcomm API surface.

### MediaTek

| Chipset | NTN Support | Notes |
|---------|-------------|-------|
| MT6825 | **5G IoT-NTN** | World's first commercial 5G IoT-NTN chipset. 3GPP Rel-17, L-band & S-band. Used in Motorola Defy 2, CAT S75 |
| Dimensity 9400 | NTN modem | Partnership with Skylo |
| MT2739 (Dimensity Auto Connect) | NB-NTN + NR-NTN | Automotive modem, 5G-Advanced, Rel-17/18 |

### Samsung Exynos

| Modem | NTN Support | Devices |
|-------|-------------|---------|
| Exynos 5400 | **NB-IoT NTN** | Galaxy S24 series (some markets), Galaxy S25. Skylo certified |
| Exynos 2500 | NB-IoT NTN | Galaxy Z Flip 7. Skylo certified |
| Exynos 5410 | **NB-IoT NTN + LTE D2C + NR-NTN** | Expected Galaxy S26 (early 2026). Supports three satellite technologies |

All vendor implementations surface through standard Android `SatelliteManager` and `ServiceState` APIs; no vendor-specific public APIs for satellite beyond carrier config extensions.

---

## 10. Key Constants Summary

| Constant | Value | API Level | Location |
|----------|-------|-----------|----------|
| `TRANSPORT_SATELLITE` | 10 | 35 | `NetworkCapabilities` |
| `NET_CAPABILITY_NOT_BANDWIDTH_CONSTRAINED` | 37 | 36 | `NetworkCapabilities` |
| `NT_RADIO_TECHNOLOGY_NB_IOT_NTN` | 1 | 34 (@SystemApi) | `SatelliteManager` |
| `NT_RADIO_TECHNOLOGY_NR_NTN` | 2 | 34 (@SystemApi) | `SatelliteManager` |
| `NT_RADIO_TECHNOLOGY_EMTC_NTN` | 3 | 34 (@SystemApi) | `SatelliteManager` |
| `NT_RADIO_TECHNOLOGY_PROPRIETARY` | 4 | 34 (@SystemApi) | `SatelliteManager` |
| `SATELLITE_MODEM_STATE_CONNECTED` | 7 | 34 (@SystemApi) | `SatelliteManager` |
| `RIL_RADIO_TECHNOLOGY_NB_IOT_NTN` | 21 | Internal | `ServiceState` |
| Skylo PLMN | 901-98 | N/A | ITU-T E.212 |
| SpaceX PLMN | 901-08 | N/A | ITU-T E.212 |
| Starlink D2C MNC (US) | 310-830, 310-210 | N/A | Crowdsourced |

---

## 11. Flock-You Implications

### Detection Challenges by Provider

| Provider | Detection Difficulty | Why |
|----------|---------------------|-----|
| Skylo (Pixel SOS) | **Easy** | Dedicated PLMN 901-98, NTN flag set, NB-IoT NTN radio tech |
| T-Mobile Starlink | **Hard** | Same PLMNs as terrestrial T-Mobile, standard LTE EARFCN, no NTN NRARFCN. Must rely on operator name strings and signal characteristics |
| AST SpaceMobile | **Hard** | Same PLMNs as AT&T/Verizon, standard LTE/NR protocols |
| Apple/Globalstar | **N/A on Android** | Proprietary protocol, iOS only |

### API Availability by Privilege Level

| Detection Method | Sideload | System | OEM |
|-----------------|----------|--------|-----|
| `ServiceState.isUsingNonTerrestrialNetwork()` (API 35+) | Yes (with `READ_PHONE_STATE`) | Yes | Yes |
| `NetworkCapabilities.TRANSPORT_SATELLITE` (API 35+) | Yes | Yes | Yes |
| `TelephonyCallback.ServiceStateListener` | Yes (with `READ_PHONE_STATE`) | Yes | Yes |
| `SatelliteManager.registerForModemStateChanged()` | No (@SystemApi) | Yes | Yes |
| `SatelliteManager.requestCapabilities()` | No (@SystemApi) | Yes | Yes |
| `SatelliteStateChangeListener` (API 36+) | Yes (public) | Yes | Yes |
| Operator name string matching | Yes | Yes | Yes |
| NRARFCN/EARFCN inspection | Yes (with `READ_PHONE_STATE`) | Yes | Yes |

### Signal Characteristics for Anomaly Detection

- Starlink D2D RSRP: median **-121 dBm** (24 dB weaker than terrestrial -97 dBm)
- If a "satellite" connection shows RSRP stronger than -100 dBm, it is likely spoofed or a false detection
- NTN round-trip times: LEO (Starlink) ~20-40ms, MEO (O3b) ~125ms, GEO (Inmarsat) ~600ms
- NTN timing advance values should be > 200km equivalent for true satellite

---

## Sources

- [SatelliteManager API Reference](https://developer.android.com/reference/android/telephony/satellite/SatelliteManager)
- [android.telephony.satellite Package](https://developer.android.com/reference/android/telephony/satellite/package-summary)
- [Develop for Constrained Satellite Networks](https://developer.android.com/develop/connectivity/satellite/constrained-networks)
- [Android 15 Features](https://developer.android.com/about/versions/15/features)
- [Android 16 Features](https://developer.android.com/about/versions/16/features)
- [AOSP ServiceState.java](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/telephony/java/android/telephony/ServiceState.java)
- [AOSP NetworkRegistrationInfo.java](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/telephony/java/android/telephony/NetworkRegistrationInfo.java)
- [Direct-to-Cell: Starlink's D2D RAN (arXiv)](https://arxiv.org/html/2506.00283v6)
- [3GPP NTN Overview](https://www.3gpp.org/technologies/ntn-overview)
- [NTN Frequency Bands (RF Wireless World)](https://www.rfwireless-world.com/terminology/ntn-frequency-bands)
- [5G NR NTN Frequency Bands (5G-Tools)](https://5g-tools.com/frequency-bands-for-5g-nr-ntn/)
- [ITU-T E.212 MCC 901 Registry](https://www.itu.int/net/ITU-T/inrdb/e212_901.aspx)
- [Snapdragon X80 5G Modem (Qualcomm)](https://www.qualcomm.com/modems/products/snapdragon-x80-5g-modem-rf-system)
- [MediaTek 5G Satellite NTN](https://www.mediatek.com/technology/5g/5g-satellite-ntn)
- [MediaTek MT6825](https://www.mediatek.com/technology/5g/5g-satellite-ntn/mt6825)
- [Skylo Certifies Samsung Exynos 5400](https://www.skylo.tech/newsroom/skylo-certifies-the-samsung-exynos-modem-5400-on-its-non-terrestrial-network)
- [Samsung Exynos 5410](https://news.satnews.com/2025/12/30/samsung-expands-satellite-connectivity-with-standalone-exynos-5410-modem/)
- [T-Mobile Starlink Guide](https://www.t-mobile.com/coverage/satellite-phone-service)
- [T-Mobile Satellite Data Apps](https://www.t-mobile.com/news/network/t-satellite-data-ready-app-expansion)
- [AST SpaceMobile](https://ast-science.com/how-it-works/)
- [TRANSPORT_SATELLITE Reference](https://learn.microsoft.com/en-us/dotnet/api/android.net.networkcapabilities.transportsatellite?view=net-android-36.0)
- [Ericsson: Satellite Direct to Device](https://www.ericsson.com/en/reports-and-papers/ericsson-technology-review/articles/satellite-direct-to-device-communication)
