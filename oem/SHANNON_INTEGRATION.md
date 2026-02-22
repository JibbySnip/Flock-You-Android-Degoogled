# Shannon Modem Diagnostic Integration Guide

## Overview

Flock-You can capture raw NAS/RRC signaling from Samsung Shannon modems via the
SDM (Samsung Diagnostic Monitor) interface at `/dev/umts_dm0`. This provides
**definitive** IMSI catcher detection -- not heuristic inference.

## Compatible Devices

| Device | SoC | Shannon Modem | Chipname |
|--------|-----|---------------|----------|
| Pixel 6/6 Pro | Tensor G1 | Shannon 5123 | s5123ap |
| Pixel 7/7 Pro | Tensor G2 | Shannon 5300 | s5300ap |
| Pixel 8/8 Pro | Tensor G3 | Shannon 5400 | s5400 |
| Pixel 9/9 Pro | Tensor G4 | Shannon 6000 | s6000 |
| Samsung Galaxy (Exynos) | Exynos variants | Various | exynos* |

Qualcomm-based devices (most US Samsung Galaxy models) use Qualcomm modems and
are **not compatible** with this feature. The app gracefully detects this and
disables Shannon diagnostics.

## Requirements

1. **OEM build**: Shannon diagnostics requires `FEATURE_SHANNON_DIAG_ENABLED=true`,
   which is only set in the OEM flavor
2. **Platform signing**: The APK must be signed with the device's platform certificate
3. **SELinux policy**: A policy granting `platform_app` read access to `/dev/umts_dm0`
4. **System/priv-app installation**: The app must be in `/system/priv-app/` or
   `/system_ext/priv-app/`

## SELinux Setup

### 1. Add the policy file

Copy `oem/sepolicy/flockyou_shannon.te` to your device tree:

```
device/<vendor>/<device>/sepolicy/flockyou_shannon.te
```

### 2. Add file_contexts entry

Add to `device/<vendor>/<device>/sepolicy/file_contexts`:

```
/dev/umts_dm0    u:object_r:umts_dm0_device:s0
```

### 3. Include in build

Add to `device/<vendor>/<device>/BoardConfig.mk`:

```makefile
BOARD_SEPOLICY_DIRS += device/<vendor>/<device>/sepolicy
```

## Build Configuration

### Enable (default)

```properties
OEM_FEATURE_SHANNON_DIAG_ENABLED=true
```

### Disable

```bash
./gradlew assembleOemRelease -POEM_FEATURE_SHANNON_DIAG_ENABLED=false
```

## Verification

### 1. Check device compatibility

```bash
adb shell getprop ro.hardware.chipname  # Should be s5123ap, s5300ap, s5400, or s6000
adb shell ls -la /dev/umts_dm0          # Should exist
```

### 2. Check SELinux access

```bash
adb shell su -c "ls -laZ /dev/umts_dm0"
# Should show: u:object_r:umts_dm0_device:s0

adb shell su -c "cat /dev/umts_dm0 | head -c 100 | xxd"
# Should output hex data (SDM frames start with 0x7F)
```

### 3. Check app status

In the app's Service Health screen, look for:
- "Shannon: Active" -- working correctly
- "Shannon: NO_SHANNON_MODEM" -- device doesn't have a Shannon modem
- "Shannon: Access denied (SELinux)" -- SELinux policy not applied
- "Shannon: Device node missing" -- /dev/umts_dm0 doesn't exist

## What It Detects

| Detection | Confidence | Severity | Description |
|-----------|-----------|----------|-------------|
| Null Cipher | DEFINITIVE (100%) | CRITICAL | Network negotiated EEA0/NEA0/A5/0 |
| IMSI Paging | HIGH (85%) | CRITICAL | Network requested IMSI instead of TMSI |
| Silent SMS | HIGH (85%) | HIGH | Invisible Type 0 SMS for location tracking |
| Forced 2G | HIGH (85%) | HIGH | RRC redirect from LTE/5G to 2G |
| Auth Anomaly | MEDIUM (60%) | MEDIUM | Malformed RAND/AUTN parameters |

## Graceful Degradation

The Shannon diagnostic feature is designed to degrade gracefully:

- **Sideload/System builds**: Feature flag is `false`, code is present but never executes
- **OEM build on Qualcomm device**: `ShannonCapabilityDetector` returns `NO_SHANNON_MODEM`
- **OEM build without SELinux policy**: Returns `ACCESS_DENIED`
- **Device node disappears at runtime**: Monitor reconnects with backoff (5 attempts)
- **Standard cellular detection continues independently** -- Shannon is additive, never replaces

## SDM Wire Format Reference

Based on [SCAT](https://github.com/fgsect/scat) by FGSect (TU Berlin):

- Start marker: `0x7F`
- End marker: `0x7E`
- Byte stuffing: `0x7D 0x5E` -> `0x7E`, `0x7D 0x5D` -> `0x7D`, `0x7D 0x5F` -> `0x7F`
- Header: `[Length:2LE][Type:1][Subtype:1][Direction:1][Timestamp:8LE]`

Key SDM types: `0x50` (LTE NAS EMM), `0x60` (LTE RRC), `0x70` (5G NR NAS),
`0x80` (5G NR RRC), `0x13` (GSM MM)
