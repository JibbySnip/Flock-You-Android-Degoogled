# Samsung Shannon Modem Diagnostic Support: Implementation Plan

An implementation plan for adding Samsung Shannon/Exynos modem diagnostic capabilities to Flock-You, enabling IMSI catcher detection on Pixel 6-9 and Samsung Exynos devices.

---

## Table of Contents

1. [Correction: "SAEL" Does Not Exist](#correction-sael-does-not-exist)
2. [What Actually Exists: The Shannon Diagnostic Stack](#what-actually-exists)
3. [Open-Source Tooling Inventory](#open-source-tooling-inventory)
4. [Device Node Map](#device-node-map)
5. [Shannon SDM Protocol vs Qualcomm QCDM](#shannon-sdm-protocol-vs-qualcomm-qcdm)
6. [Implementation Plan](#implementation-plan)
   - [Phase 1: Standard Android APIs (No Root)](#phase-1-standard-android-apis-no-root)
   - [Phase 2: Root-Level Shannon DM Access](#phase-2-root-level-shannon-dm-access)
   - [Phase 3: Custom OS Integration](#phase-3-custom-os-integration)
   - [Phase 4: Future Android APIs (IRadio 3.0)](#phase-4-future-android-apis-iradio-30)
7. [Target Hardware Matrix](#target-hardware-matrix)
8. [The Magic Number Problem](#the-magic-number-problem)
9. [SCAT Integration Architecture](#scat-integration-architecture)
10. [SIPC Protocol Interception](#sipc-protocol-interception)
11. [Security Considerations](#security-considerations)
12. [References & Sources](#references--sources)

---

## Correction: "SAEL" Does Not Exist

**"SAEL" (Samsung Abstraction/Access Layer) is not a real interface.** Exhaustive search across academic papers, security research (Google Project Zero, Comsecuris, taszk.io, FirmWire), GitHub repositories, XDA forums, firmware analysis tools, and Samsung kernel source trees yields zero references to "SAEL" as an acronym or interface name.

The term was speculative -- likely a conflation of real internal Shannon module names:

| Actual Name | What It Is |
|---|---|
| **PAL** (Platform Abstraction Layer) | Shannon RTOS abstraction. Provides `pal_MsgSendTo()`, `pal_MemAlloc()`, `pal_Sleep()`. Internal to modem firmware. |
| **SAE** modules (SAEMM, SAECOMM, SAERC, SAEQM) | Layer 3 / NAS protocol entities within Shannon firmware. SAEMM = SAE Mobility Management (LTE attach, authentication, TAU). |
| **SIPC** (Samsung IPC) | The actual AP-CP transport protocol. This is the real diagnostic interface. |
| **SDM** (Samsung Diagnostic Message) | The diagnostic output format from `/dev/umts_dm0`. Shannon's equivalent of Qualcomm DIAG/QMDL. |

**The real interfaces for surveillance detection are SIPC (for IPC interception) and SDM (for diagnostic capture via `/dev/umts_dm0`).** The rest of this document uses these correct names.

---

## What Actually Exists

Samsung Shannon modems expose a rich set of interfaces between the Application Processor (AP) and Communication Processor (CP). The AP and CP share a ~120MB memory region, with interrupt-based mailbox signaling.

### Communication Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│ Application Processor (Android)                                     │
│                                                                     │
│  ┌──────────────┐  ┌───────────────┐  ┌──────────────────────────┐ │
│  │ Flock-You    │  │ libsec-ril.so │  │ cbd (CP Boot Daemon)     │ │
│  │ Detection    │  │ (Samsung RIL) │  │                          │ │
│  │ Daemon       │  │               │  │                          │ │
│  └──────┬───────┘  └───────┬───────┘  └────────────┬─────────────┘ │
│         │                  │                        │               │
│  /dev/umts_dm0      /dev/umts_ipc0          /dev/umts_boot0        │
│  (Diagnostic)       (Primary IPC)           (Firmware Upload)      │
│         │                  │                        │               │
│  ┌──────┴──────────────────┴────────────────────────┴──────────────┐│
│  │              CPIF Kernel Driver (cpif.ko)                       ││
│  │              Shared Memory IPC  (shm_ipc.ko)                   ││
│  └─────────────────────────┬───────────────────────────────────────┘│
└────────────────────────────┼────────────────────────────────────────┘
                    ┌────────┴────────┐
                    │  Mailbox IRQs   │
                    │  Shared Memory  │
                    │  (~120MB SBD)   │
                    └────────┬────────┘
┌────────────────────────────┼────────────────────────────────────────┐
│ Communication Processor (Shannon Baseband RTOS)                     │
│                                                                     │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌─────────────────┐  │
│  │ PAL (RTOS  │ │ SAEMM (NAS │ │ RRC (Radio │ │ PHY (Physical   │  │
│  │ Abstraction│ │ Mobility   │ │ Resource   │ │ Layer, Modem    │  │
│  │ Layer)     │ │ Management)│ │ Control)   │ │ DSP)            │  │
│  └────────────┘ └────────────┘ └────────────┘ └─────────────────┘  │
│                                                                     │
│  Closed-source firmware. QCDM-style observability via SDM output.  │
│  No control -- modem will always respond to network commands.       │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Open-Source Tooling Inventory

### Tools That Interact With Shannon Modems

| Tool | Purpose | Maturity | Link |
|---|---|---|---|
| **SCAT** | Signaling capture from Shannon DM port → GSMTAP for Wireshark | Production-grade | [fgsect/scat](https://github.com/fgsect/scat) |
| **SCAT (HandyMenny fork)** | Improved 5G/Exynos support, split item handling | Active fork | [HandyMenny/scat](https://github.com/HandyMenny/scat) |
| **libsamsung-ipc** | Open-source SIPC protocol implementation | Mature (legacy devices) | [morphis/libsamsung-ipc](https://github.com/morphis/libsamsung-ipc) |
| **ShannonBaseband** | Ghidra loader, BTL parser, firmware analysis | Research-grade | [grant-h/ShannonBaseband](https://github.com/grant-h/ShannonBaseband) |
| **shannon_s5000** | Reconstructed code skeleton (150K+ debug strings) | Reference | [grant-h/shannon_s5000](https://github.com/grant-h/shannon_s5000) |
| **shannon_S5123** | 5G modem code skeleton | Reference | [grant-h/shannon_S5123](https://github.com/grant-h/shannon_S5123) |
| **FirmWire** | Full-system Shannon baseband emulator (NDSS 2022) | Research-grade | [FirmWire/FirmWire](https://github.com/FirmWire/FirmWire) |
| **BaseMirror** | Automated RIL command extraction (CCS 2024) | Research-grade | [OSUSecLab/BaseMirror](https://github.com/OSUSecLab/BaseMirror) |
| **shannonRE** | IDA/010 Editor RE scripts (REcon 2016) | Legacy | [Comsecuris/shannonRE](https://github.com/Comsecuris/shannonRE) |
| **shannon_modem_loader** | IDA Pro 8.x/9.x firmware loader | Active | [alexander-pick/shannon_modem_loader](https://github.com/alexander-pick/shannon_modem_loader) |
| **shannon-ril** | WIP open-source Shannon RIL | Early WIP | [cult-of-mari/shannon-ril](https://github.com/cult-of-mari/shannon-ril) |
| **Pixel modem NV scripts** | AT command NV item tools for Pixel Shannon | Utility | [davwheat/shannon-pixel-modem-nvitem-enabler-scripts](https://github.com/davwheat/shannon-pixel-modem-nvitem-enabler-scripts) |
| **Pixel modem tweaks app** | Android app for Shannon modem configuration | Utility | [davwheat/shannon-pixel-modem-tweaks-app](https://github.com/davwheat/shannon-pixel-modem-tweaks-app) |
| **SM-A217F forensics** | Samsung AT command documentation over USB | Reference | [Tomiwa-Ot/SM-A217F_forensics](https://github.com/Tomiwa-Ot/SM-A217F_forensics) |
| **galaxy-at-tool** | USB AT modem tool for Galaxy S8/S9 | Utility | [apeppels/galaxy-at-tool](https://github.com/apeppels/galaxy-at-tool) |

### Proprietary Tools (Not Publicly Distributed)

| Tool | Purpose | Availability |
|---|---|---|
| **ShannonDM** | Samsung's official diagnostic monitor (equivalent to Qualcomm QXDM) | [Internet Archive (v1.5.1.2)](https://archive.org/details/exynos-log-tool.-7z) |
| **Network Signal Guru (NSG)** | Commercial Layer 3 signaling viewer, supports Shannon | Play Store (paid, requires root + Samsung token for Exynos) |

---

## Device Node Map

Samsung Shannon modems on both Samsung Galaxy and Google Pixel devices expose these character device nodes via the CPIF kernel driver:

| Device Node | Channel ID | Format | Purpose | SELinux Context (Stock) |
|---|---|---|---|---|
| `/dev/umts_ipc0` | 235 | IPC_FMT | **Primary RIL IPC** -- all solicited/unsolicited modem commands | `rild` only |
| `/dev/umts_ipc1` | 236 | IPC_FMT | **Secondary RIL IPC** | `rild` only |
| `/dev/umts_rfs0` | 245 | IPC_RFS | **Remote File System** -- modem NV data, EFS access | `rild` only |
| `/dev/umts_dm0` | 28 | IPC_RAW | **Diagnostic Monitor** -- SDM-format diagnostic output (128 DL buffers) | Blocked |
| `/dev/umts_router` | 25 | IPC_RAW | **AT command interface** -- `AT+GOOGSETNV`/`AT+GOOGGETNV` on Pixel | Root only |
| `/dev/umts_boot0` | 215 | IPC_BOOT | **CP Boot** -- firmware upload (used by `cbd` daemon) | `cbd` only |
| `/dev/umts_ramdump0` | 225 | IPC_DUMP | **CP Crash Dump** -- modem RAM dump on crash | `cbd` only |
| `/dev/umts_csd` | 1 | IPC_RAW | Circuit-switched data (CSVT) | Restricted |
| `/dev/umts_cass` | 35 | IPC_RAW | Audio streaming | Restricted |
| `/dev/umts_ciq0` | 26 | IPC_RAW | Carrier IQ / diagnostics (US carrier-specific) | Restricted |
| `/dev/rmnet0`-`rmnet7` | 10-17 | IPC_RAW | Mobile data network interfaces | `system` |

### Pixel-Specific Details

| Pixel Gen | SoC | Modem | Modem Codename | Kernel CPIF Path |
|---|---|---|---|---|
| Pixel 6/6 Pro/6a | Tensor G1 (GS101) | Exynos Modem 5123 | s5123 | `drivers/soc/google/cpif/` |
| Pixel 7/7 Pro/7a | Tensor G2 (GS201) | Exynos Modem 5300 | g5300b | `drivers/soc/google/cpif/` |
| Pixel 8/8 Pro/8a | Tensor G3 (Zuma) | Exynos Modem 5300 | g5300b | `drivers/soc/google/cpif/` |
| Pixel 9/9 Pro | Tensor G4 | Exynos Modem 5400 | - | `drivers/soc/google/cpif/` |

Google uses the **exact same CPIF driver and device node naming** as Samsung. The RIL library is also Samsung's `libsec-ril.so`. The kernel modules loaded at boot are `cpif.ko`, `cpif_page.ko`, and `shm_ipc.ko`.

---

## Shannon SDM Protocol vs Qualcomm QCDM

| Feature | Qualcomm DIAG (QCDM) | Samsung SDM (Shannon DM) |
|---|---|---|
| **Device node** | `/dev/diag` | `/dev/umts_dm0` |
| **Protocol** | HDLC-like framing with CRC | Proprietary Samsung framing |
| **Authentication** | None (open once SELinux allows) | **Requires magic number** to start diagnostic session over USB |
| **USB access** | Standard `qcserial`/`option` kernel module | No standard module -- Samsung CPIF driver |
| **Log format** | QMDL files | SDM files (`.sdm` extension) |
| **Vendor tool** | QXDM (Qualcomm) | ShannonDM (Samsung, not publicly distributed) |
| **Open-source parser** | QCSuper (P1sec) | **SCAT** (fgsect) |
| **Output capability** | Raw 2G/3G/4G/5G radio frames, NV items, EFS, memory dumps | Control plane signaling → GSMTAP |
| **Wireshark integration** | GSMTAP via QCSuper | GSMTAP via SCAT |
| **Protocol documentation** | Partially documented (leaked docs, QCSuper) | Undocumented, reverse-engineered |
| **Autodetection** | USB VID/PID | SCAT 1.2+ autodetects Shannon baseband type |
| **5G support** | Mature | Partial (SCAT HandyMenny fork improving) |

**Key difference**: Qualcomm DIAG is essentially open once SELinux permits access -- no authentication required. Samsung SDM requires a **device-specific magic number** to initiate diagnostic sessions over USB. The magic numbers are not publicly documented.

---

## Implementation Plan

### Phase 1: Standard Android APIs (No Root)

**Goal**: Maximize IMSI catcher detection using only public Android APIs. This is the current Flock-You approach, refined for Shannon-specific behavior.

**What works today on Pixel 6-9:**

| Detection Method | API | Reliability | Notes |
|---|---|---|---|
| RAT downgrade (4G/5G → 2G) | `TelephonyCallback.ServiceStateListener` | HIGH | Single strongest non-root indicator |
| Rapid cell tower switching | `TelephonyCallback.CellInfoListener` | HIGH | Threshold: 3/min stationary, 8/min moving |
| Signal strength anomaly | `TelephonyCallback.SignalStrengthsListener` | MEDIUM | >25 dBm change in 5s |
| Unknown cell tower | `TelephonyManager.getAllCellInfo()` + local DB | MEDIUM | CellGuard-validated approach |
| LAC/TAC change without movement | `CellInfoListener` + location | MEDIUM | |
| Test MCC/MNC (001-01, 999-99) | `CellIdentity.getMccString()` | HIGH | Only catches misconfigured IMSI catchers |
| PCI change on same frequency | `CellInfoListener` | MEDIUM | |
| Neighboring cell disappearance | `getAllCellInfo()` | LOW-MEDIUM | |
| 2G disable status check | `UserManager.hasUserRestriction(DISALLOW_CELLULAR_2G)` | N/A | Recommend enabling |

**Implementation tasks:**

1. **Add Shannon-aware timing to `CellularMonitor`**: Shannon modems on Pixel report cell info updates at slightly different cadences than Qualcomm. Test and calibrate thresholds on Pixel 6-9 hardware.

2. **Implement `DISALLOW_CELLULAR_2G` status check**: Query whether 2G is disabled and include this in the security posture assessment. Recommend users enable it if available.

3. **Track ARFCN/EARFCN/NRARFCN transitions**: Shannon modems report these reliably. Unusual frequency changes can indicate forced handover to an IMSI catcher.

4. **Build Shannon-specific cell trust database**: Cell tower parameters (PCI, EARFCN, TAC) reported by Shannon modems may have different precision than Qualcomm. Validate and adjust trust thresholds.

**What cannot be detected without root:**
- Null cipher usage (A5/0, EEA0)
- IMSI/IMEI disclosure events
- NAS Security Mode Command details
- Authentication algorithm selection
- Encryption downgrade within the same RAT
- Silent SMS (Type 0)

---

### Phase 2: Root-Level Shannon DM Access

**Goal**: On rooted devices, capture Shannon diagnostic data via `/dev/umts_dm0` for definitive IMSI catcher detection.

**Architecture:**

```
┌─────────────────────────────────────────────────┐
│ Flock-You App (Android)                         │
│  ┌───────────────────────────────────────────┐  │
│  │ ShannonDiagService (Native Daemon)        │  │
│  │                                           │  │
│  │  ┌─────────────┐  ┌───────────────────┐   │  │
│  │  │ SDM Parser  │  │ GSMTAP Generator  │   │  │
│  │  │ (from SCAT) │  │                   │   │  │
│  │  └──────┬──────┘  └────────┬──────────┘   │  │
│  │         │                  │               │  │
│  │  ┌──────┴──────┐  ┌───────┴───────────┐   │  │
│  │  │ NAS Analyzer│  │ RRC Analyzer      │   │  │
│  │  │ - A5/0 det. │  │ - Handover cmds   │   │  │
│  │  │ - IMSI page │  │ - Reselection     │   │  │
│  │  │ - Auth vect │  │ - Security Mode   │   │  │
│  │  │ - Silent SMS│  │ - Config changes  │   │  │
│  │  └─────────────┘  └───────────────────┘   │  │
│  └───────────────────────┬───────────────────┘  │
│                          │ JNI / Unix Socket     │
│  ┌───────────────────────┴───────────────────┐  │
│  │ CellularDetectionHandler                  │  │
│  │ (Existing detection pipeline)             │  │
│  └───────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
         │
    /dev/umts_dm0
         │
┌────────┴────────┐
│ Shannon Modem   │
│ (SDM output)    │
└─────────────────┘
```

**Implementation tasks:**

#### 2.1: Port SCAT SDM Parser to Kotlin/Native

SCAT (Python) parses Samsung SDM diagnostic messages. Port the relevant parsing code to a native library callable from Flock-You:

```
scat/parsers/samsung/
  sdmcmd.py          -- SDM command definitions
  sdmhdr.py          -- SDM header parsing
  sdmltephy.py       -- LTE physical layer messages
  sdmlterrc.py       -- LTE RRC messages
  sdmltepdcp.py      -- LTE PDCP layer
  sdmltenas.py       -- LTE NAS messages  ← CRITICAL: cipher mode, auth, identity
  sdm5grrc.py        -- 5G NR RRC messages
  sdm5gnas.py        -- 5G NAS messages   ← CRITICAL: 5G security mode
  sdmcommonl2dl.py   -- Common L2 downlink
  sdmedge.py         -- 2G/EDGE messages
  sdmhspa.py         -- 3G HSPA messages
```

**Priority files to port**: `sdmltephy.py`, `sdmlterrc.py`, `sdmltenas.py`, `sdm5grrc.py`, `sdm5gnas.py`. These contain the signaling message parsers that expose cipher mode selection and authentication events.

**Language choice**: C/C++ native library with JNI bridge, or Kotlin/Native. The parsing is byte-level protocol decoding -- well-suited to native code for performance.

#### 2.2: Implement SDM Capture Daemon

A native daemon that:
1. Opens `/dev/umts_dm0` for reading (requires root or custom SELinux policy)
2. Parses SDM framing (proprietary Samsung header format)
3. Extracts control plane messages (NAS, RRC)
4. Generates structured events for the detection pipeline

```kotlin
// Pseudo-code for the Shannon diagnostic interface
class ShannonDiagCapture {
    private val dmDevice = "/dev/umts_dm0"

    // SDM message types relevant to IMSI catcher detection
    enum class SdmEventType {
        NAS_SECURITY_MODE_COMMAND,    // Cipher algorithm selection
        NAS_IDENTITY_REQUEST,         // Network requesting IMSI/IMEI
        NAS_AUTHENTICATION_REQUEST,   // Authentication challenge
        RRC_SECURITY_MODE_COMMAND,    // RRC-level security config
        RRC_RECONFIGURATION,          // Cell handover/reselection
        SMS_TYPE_0_RECEIVED,          // Silent SMS
        CIPHER_MODE_CHANGE,           // GSM cipher mode (A5/0, A5/1, A5/3)
        ATTACH_REJECT,                // Network rejecting attach (cause analysis)
        TAU_REJECT,                   // Tracking area update reject
    }

    data class SdmEvent(
        val type: SdmEventType,
        val timestamp: Long,
        val rawBytes: ByteArray,
        val parsed: Map<String, Any>  // Structured parsed fields
    )

    // Detections derivable from SDM events
    fun analyzeForImsiCatcher(event: SdmEvent): CellularAnomaly? {
        return when (event.type) {
            SdmEventType.CIPHER_MODE_CHANGE -> {
                val cipher = event.parsed["cipher_algorithm"] as String
                if (cipher == "A5/0" || cipher == "EEA0") {
                    // DEFINITIVE IMSI catcher indicator
                    CellularAnomaly(
                        method = DetectionMethod.NULL_CIPHER_DETECTED,
                        confidence = 0.95,
                        details = "Null cipher $cipher selected by network"
                    )
                } else null
            }
            SdmEventType.NAS_IDENTITY_REQUEST -> {
                val identityType = event.parsed["identity_type"] as Int
                if (identityType == 1) { // IMSI request
                    CellularAnomaly(
                        method = DetectionMethod.IMSI_PAGING_DETECTED,
                        confidence = 0.7,
                        details = "Network requested IMSI (should use TMSI)"
                    )
                } else null
            }
            SdmEventType.SMS_TYPE_0_RECEIVED -> {
                CellularAnomaly(
                    method = DetectionMethod.SILENT_SMS_DETECTED,
                    confidence = 0.85,
                    details = "Type 0 (silent) SMS received"
                )
            }
            else -> null
        }
    }
}
```

#### 2.3: Enable DM Port on Rooted Pixel

On a rooted Pixel 6-9, the diagnostic port can be enabled with:

```bash
# Enable DM port
resetprop ro.bootmode usbradio
resetprop ro.build.type userdebug
stop DM-daemon && start DM-daemon
setprop sys.usb.config acm,dm,adb
setprop persist.vendor.usb.usbradio.config dm
```

For on-device capture (no USB needed), directly read from `/dev/umts_dm0`:

```bash
# Verify device node exists
ls -la /dev/umts_dm0

# Test read (root required)
cat /dev/umts_dm0 | hexdump -C | head
```

#### 2.4: AT Command Interface for NV Items

On Pixel devices, Shannon modem NV items can be queried via `/dev/umts_router`:

```bash
# Read NV item (root required)
echo 'AT+GOOGGETNV="NR.CONFIG.MODE"' > /dev/umts_router
cat /dev/umts_router

# List supported AT commands
echo 'AT+CLAC' > /dev/umts_router
cat /dev/umts_router
```

**Useful NV items for surveillance detection:**
- `NR.CONFIG.MODE` -- 5G configuration
- `DS.VOICE.ALLOW.2G` -- Whether 2G voice is allowed
- Band lock/unlock settings
- Carrier aggregation configuration

**Warning**: On production devices with `ro.product_ship=true`, most AT commands are **whitelisted to information-gathering only**. Non-whitelisted commands return generic OK regardless of validity.

---

### Phase 3: Custom OS Integration

**Goal**: Full Shannon diagnostic integration as a system service in a custom AOSP fork.

#### 3.1: SELinux Policy for `/dev/umts_dm0`

Create a custom SELinux domain for the Shannon diagnostic daemon:

```
# file: sepolicy/shannon_diag.te

# Define the shannon_diag domain
type shannon_diag, domain;
type shannon_diag_exec, exec_type, file_type, system_file_type;

# Transition from init
init_daemon_domain(shannon_diag)

# Grant access to the diagnostic monitor device
allow shannon_diag umts_dm_device:chr_file { open read write ioctl };

# Grant access to AT command interface
allow shannon_diag umts_router_device:chr_file { open read write ioctl };

# Allow IPC with the detection service
allow shannon_diag flockyou_service:unix_stream_socket { connectto read write };

# Networking for GSMTAP output (localhost only, for Wireshark debugging)
allow shannon_diag self:udp_socket { create connect write };
allow shannon_diag node:udp_socket { node_bind };

# Deny everything else (principle of least privilege)
neverallow shannon_diag { file_type -umts_dm_device -umts_router_device }:chr_file *;
```

Device node labeling:

```
# file: sepolicy/file_contexts
/dev/umts_dm0       u:object_r:umts_dm_device:s0
/dev/umts_router     u:object_r:umts_router_device:s0
/dev/umts_ipc[0-9]   u:object_r:umts_ipc_device:s0
```

#### 3.2: System Service for Continuous Shannon Monitoring

Register the Shannon diagnostic capture as a native system service started by init:

```
# file: init.shannon_diag.rc

service shannon_diag /system/bin/shannon_diag_daemon
    class main
    user system
    group system diag radio
    capabilities NET_RAW
    seclabel u:r:shannon_diag:s0
    disabled

on property:persist.flockyou.shannon_diag=1
    start shannon_diag

on property:persist.flockyou.shannon_diag=0
    stop shannon_diag
```

#### 3.3: IPC Interception via SIPC Protocol Parsing

For deeper observability, intercept all AP-CP communication on `/dev/umts_ipc0`:

The SIPC message format (from BaseMirror CCS 2024 research and taszk.io vulnerability analysis):

```c
struct sipc_fmt_hdr {
    uint16_t length;      // Total packet length (self-describing)
    uint8_t  msg_seq;     // Message sequence number
    uint8_t  ack_seq;     // Acknowledgment sequence number
    uint8_t  main_cmd;    // Primary command group
    uint8_t  sub_cmd;     // Subcommand within group
    uint8_t  cmd_type;    // IPC_TYPE_EXEC, IPC_TYPE_NOTI, etc.
    // ... data payload follows
};

// Command types
#define IPC_TYPE_EXEC   0x01  // Execute command
#define IPC_TYPE_GET    0x02  // Get value
#define IPC_TYPE_SET    0x03  // Set value
#define IPC_TYPE_CFRM   0x04  // Confirm
#define IPC_TYPE_EVENT  0x05  // Event notification
#define IPC_TYPE_INDI   0x01  // Indication (unsolicited)
#define IPC_TYPE_RESP   0x02  // Response (solicited)
#define IPC_TYPE_NOTI   0x03  // Notification
```

**IPC command groups relevant to surveillance detection:**

| main_cmd | Group | Relevant sub_cmds |
|---|---|---|
| NET | Network | PLMN selection, registration, band selection, identity, RRC status |
| SEC | Security | PIN/SIM status, authentication, lock info |
| MISC | Miscellaneous | IMEI/IMSI, serial number, debug level |
| SMS | SMS | Send/receive/delete, **Type 0 SMS detection** |
| CALL | Call | Call state, DTMF, emergency |

BaseMirror (CCS 2024) extracted **873 unique vendor-specific commands** from 28 Samsung firmware versions. Modern Ipc41X (5G-era) firmware contains 3,300-4,580 commands per device including a ~160-command "Security" module. These undocumented security commands likely include cipher mode queries, but their exact format requires per-firmware reverse engineering.

**Implementation approach**: Rather than reverse-engineering individual SIPC commands (fragile across firmware versions), use a **RIL shim wrapper** approach:

```
┌──────────────┐     ┌─────────────────┐     ┌──────────────┐
│ Android      │     │ RIL Shim        │     │ libsec-ril   │
│ Telephony    │────▶│ (libril_shim.so)│────▶│ (Samsung)    │
│ Framework    │     │                 │     │              │
│              │◀────│ Log + Forward   │◀────│              │
└──────────────┘     └─────────────────┘     └──────────────┘
                            │
                            ▼
                     ┌──────────────┐
                     │ Detection    │
                     │ Pipeline     │
                     └──────────────┘
```

The shim intercepts all `RIL_REQUEST_*` and `RIL_UNSOL_*` messages, logging them and forwarding transparently. This provides:
- Real-time cipher mode change notifications (when the RIL reports them)
- Cell reselection and handover command logging
- PLMN search results with full signal details
- Network registration events with security context

#### 3.4: `/dev/umts_dm0` vs `/dev/umts_ipc0` -- Which to Use

| Approach | Interface | What You Get | Effort |
|---|---|---|---|
| **SDM Diagnostic Capture** | `/dev/umts_dm0` | Pre-formatted diagnostic messages including L3 signaling. SCAT-compatible. | Medium (port SCAT parser) |
| **IPC Interception** | `/dev/umts_ipc0` | Raw AP-CP commands. All 873+ command types. | High (need SIPC protocol RE per firmware) |
| **RIL Shim** | `libril_shim.so` | RIL-level abstractions of modem events. | Medium (well-understood API boundary) |
| **eBPF on CPIF driver** | Kernel tracepoints | Message timing, sizes, patterns (not content) | Low (but limited utility for cipher detection) |

**Recommendation**: Start with SDM diagnostic capture (`/dev/umts_dm0` + SCAT parser port). This provides the richest signaling data with the most mature open-source tooling. Add RIL shim for complementary real-time event capture. Use IPC interception only for research/development.

---

### Phase 4: Future Android APIs (IRadio 3.0)

**Goal**: Prepare for Android 16+ APIs that will provide cipher algorithm notifications without root.

Android 14+ defined but does not yet have hardware support for:

| API | Purpose | Required HAL | Status |
|---|---|---|---|
| `TelephonyCallback.SecurityAlgorithmsListener` | Cipher algorithm change notifications | IRadio 3.0 | **No hardware support as of Pixel 9** |
| `SecurityAlgorithmUpdate` | Cipher algorithm details | IRadio 3.0 | **No hardware support** |
| `setCellularIdentifierTransparencyEnabled` | IMSI/IMEI disclosure notifications | IRadio 3.0 | **Added to Android 15, then removed from UI** -- no hardware support |
| `cellularIdentifierDisclosed` | Notification when network requests identity | IRadio 3.0 | **Expected on Pixel 10 (2026)** |

**Implementation tasks:**

1. **Runtime capability detection**: Check for IRadio 3.0 support at startup:
```kotlin
// Check if SecurityAlgorithmsListener is supported
val hasCipherNotifications = try {
    TelephonyCallback.SecurityAlgorithmsListener::class.java
    telephonyManager.registerTelephonyCallback(executor, securityCallback)
    true
} catch (e: UnsupportedOperationException) {
    false
}
```

2. **Register listeners when available**: When IRadio 3.0 hardware is detected, register for cipher algorithm change and identifier disclosure callbacks.

3. **Merge with SDM data**: When both SDM capture (Phase 2/3) and IRadio 3.0 APIs are available, cross-validate for consistency. Disagreement between the two sources indicates either a modem bug or a sophisticated attack.

**Timeline estimate**: IRadio 3.0 hardware support is expected in devices launching with Android 16, likely starting with Pixel 10 (late 2026). Samsung Galaxy S-series may follow 6-12 months later.

---

## Target Hardware Matrix

| Device | Modem | `/dev/umts_dm0` | SCAT Support | AT Commands | Recommended | Notes |
|---|---|---|---|---|---|---|
| **Pixel 6/6a/6 Pro** | Exynos 5123 | Yes | Yes | `AT+GOOGSETNV/GETNV` | Yes | Good AOSP support, active community |
| **Pixel 7/7a/7 Pro** | Exynos 5300 | Yes | Yes | `AT+GOOGSETNV/GETNV` | Yes | Project Zero validated (CVE research) |
| **Pixel 8/8a/8 Pro** | Exynos 5300 | Yes | Likely | `AT+GOOGSETNV/GETNV` | Yes | Google baseband hardening applied |
| **Pixel 9/9 Pro** | Exynos 5400 | Yes | Untested | `AT+GOOGSETNV/GETNV` | Proceed with caution | Newest modem, SCAT may need updates |
| **Samsung S20 (Exynos)** | Exynos 5123 | Yes | Yes | Samsung AT set | Yes | Good Nexmon WiFi + Shannon combo |
| **Samsung S21 (Exynos)** | Exynos 5123 | Yes | Yes | Samsung AT set | Yes | |
| **Samsung S22 (Exynos)** | Exynos 5300 | Yes | Yes | Samsung AT set | Yes | |
| **Samsung S23/S24** | Qualcomm X70/X75 | **No** (QCDM) | **No** (use QCSuper) | Qualcomm AT | Different path | These use Qualcomm modem, not Shannon |

**Key insight**: Samsung Galaxy S23 and S24 globally switched to Qualcomm modems. For Shannon modem work, target Pixel 6-9 or Samsung S20-S22 Exynos variants.

---

## The Magic Number Problem

Samsung's SDM diagnostic protocol over USB requires a **device-specific magic number** to initiate the diagnostic session. This is a form of authentication that prevents casual access.

**What is known:**
- The magic number is sent as part of the initial USB diagnostic session handshake
- SCAT handles magic numbers internally but does not publicly document them
- Different Shannon modem generations may use different magic numbers
- The magic numbers may be derived from device-specific identifiers

**For on-device access** (reading `/dev/umts_dm0` directly from a root shell or system service), the magic number is **not required**. The magic number authentication only applies to the USB diagnostic path. This means:

- **Phase 2 (rooted device)**: Read `/dev/umts_dm0` directly -- no magic number needed
- **Phase 3 (custom OS)**: System service reads `/dev/umts_dm0` directly -- no magic number needed
- **USB debugging/development**: Use SCAT with its built-in magic number handling

This is a significant advantage of on-device capture over USB-tethered analysis.

---

## SCAT Integration Architecture

SCAT is the most mature open-source tool for Shannon diagnostic parsing. The integration strategy:

### Option A: Port SCAT Parsers to Native Code

**Pros**: No Python dependency, better performance, tighter integration
**Cons**: Significant development effort, must track SCAT updates manually

Port priority:
1. `sdmhdr.py` → `sdm_header.c` (SDM framing/header parsing)
2. `sdmcmd.py` → `sdm_commands.c` (SDM command definitions)
3. `sdmltephy.py` → `sdm_lte_phy.c` (LTE physical layer)
4. `sdmlterrc.py` → `sdm_lte_rrc.c` (LTE RRC signaling)
5. `sdmltenas.py` → `sdm_lte_nas.c` (LTE NAS -- **cipher mode, authentication**)
6. `sdm5grrc.py` → `sdm_5g_rrc.c` (5G NR RRC)
7. `sdm5gnas.py` → `sdm_5g_nas.c` (5G NAS -- **5G security mode**)

### Option B: Embed SCAT via Chaquopy or Subprocess

**Pros**: Reuse SCAT directly, automatic updates
**Cons**: Python runtime on Android, performance overhead, complexity

### Option C: SCAT as a Companion Process with GSMTAP Bridge

**Pros**: Cleanest separation, SCAT runs unmodified, Wireshark-compatible output
**Cons**: Requires Python on device, IPC overhead

```
SCAT (Python) → GSMTAP (UDP :4729) → Flock-You GSMTAP Listener → Detection Pipeline
```

**Recommendation**: **Option A** (native port) for production. Use **Option C** (GSMTAP bridge) for development and validation. The GSMTAP format is well-defined and Wireshark-compatible, making it ideal for debugging. The native port ensures production performance and eliminates the Python dependency.

---

## SIPC Protocol Interception

For maximum observability, intercept the SIPC IPC protocol between `libsec-ril.so` and the Shannon modem.

### libsamsung-ipc Reference

The Replicant project's [libsamsung-ipc](https://github.com/morphis/libsamsung-ipc) provides the most complete open-source documentation of the Samsung IPC protocol. IPC message groups:

| Group | Category | Surveillance-Relevant Sub-commands |
|---|---|---|
| **NET** | Network | PLMN selection, registration state, band info, cell identity, RRC status |
| **SEC** | Security | SIM authentication, PIN status, lock info |
| **MISC** | Miscellaneous | IMEI, IMSI, serial number, debug level |
| **SMS** | SMS | Send/receive, **Type 0 silent SMS** |
| **CALL** | Call | Call state, emergency |
| **GPRS** | Data | PDP context, QoS |

**Caveat**: libsamsung-ipc supports the legacy Ipc41 protocol (pre-2019 devices: Galaxy S through Note 9). Modern devices use Ipc41X, which is modularized and expanded (3,300-4,580 commands vs ~500). The message group structure is similar but command IDs and payload formats differ.

### Samsung Modem Backdoor Context

The Replicant project discovered (2014) that Samsung's proprietary RIL implements **RFS (Remote File System) commands** giving the modem read/write access to the phone's storage via path traversal. Commands include `IPC_RFS_READ_FILE`, `IPC_RFS_WRITE_FILE`, `IPC_RFS_OPEN_FILE`, etc. The implementation appends `/efs/root/` but allows `../../` traversal.

This was documented as a security vulnerability, but for a custom OS it represents an additional monitoring surface: if the modem is compromised (e.g., by a sophisticated IMSI catcher exploiting Shannon vulnerabilities), RFS commands could be used for data exfiltration. Monitoring RFS traffic on a custom OS would detect this.

### OEM RIL Hooks (Roberto Paleari Research)

Samsung's `libsec-ril.so` exposes undocumented OEM methods via `InvokeOemRequestHookRaw`:
- `DoOemRawIpc` -- provides direct access to the IPC channel
- Raw APDU sending to UICC (SIM card)
- Network traffic capture into local PCAP
- Modem power control

On Samsung devices, a UNIX socket named "Multiclient" can be used to interact with RILD without standard Android constraints (research by Roberto Paleari, `nocve-2016-0005`).

---

## Security Considerations

### Responsible Use

Shannon modem diagnostic access exposes sensitive cellular signaling data. On a custom OS:

1. **All captured data stays on-device.** No diagnostic data should ever leave the device. This is consistent with Flock-You's zero-cloud architecture.

2. **Minimize captured data.** Only parse and retain signaling events relevant to surveillance detection. Discard raw SDM frames after analysis. Do not log full NAS messages that may contain user plane data.

3. **Protect the diagnostic daemon.** The `shannon_diag` SELinux domain should be tightly constrained (see Phase 3). Access to `/dev/umts_dm0` should not grant broader system access.

4. **User transparency.** If Shannon diagnostic capture is enabled, the user should be informed that baseband signaling is being monitored for security purposes.

### Attack Surface

Running a daemon that reads from `/dev/umts_dm0` introduces risk:
- **Malformed SDM messages**: The modem could (via exploit) send crafted diagnostic messages designed to exploit bugs in the parser. All SDM parsing must be fuzz-tested.
- **Resource exhaustion**: The modem could flood the diagnostic channel. Rate limiting and buffer management are essential.
- **Privilege escalation**: The diagnostic daemon has access to sensitive device nodes. Compromise of this daemon could provide a stepping stone to full system compromise. Run with minimal capabilities.

### Testing with FirmWire

[FirmWire](https://github.com/FirmWire/FirmWire) enables testing Shannon modem behavior without real hardware:
- Emulate Shannon modem firmware images
- Inject crafted protocol messages (LTE, GSM, 5G NR)
- Test SDM parser against known-malformed input
- Validate detection logic against simulated IMSI catcher scenarios

This is valuable for CI/CD testing of the Shannon diagnostic integration without requiring physical devices.

---

## References & Sources

### Primary Open-Source Tools
- [SCAT - Signaling Collection and Analysis Tool](https://github.com/fgsect/scat)
- [SCAT (HandyMenny fork with 5G improvements)](https://github.com/HandyMenny/scat)
- [libsamsung-ipc (Replicant/LineageOS)](https://github.com/morphis/libsamsung-ipc)
- [ShannonBaseband (Grant Hernandez)](https://github.com/grant-h/ShannonBaseband)
- [FirmWire Baseband Emulator](https://github.com/FirmWire/FirmWire)
- [BaseMirror (OSU SecLab)](https://github.com/OSUSecLab/BaseMirror)
- [Shannon Pixel Modem NV Scripts](https://github.com/davwheat/shannon-pixel-modem-nvitem-enabler-scripts)
- [Shannon Pixel Modem Tweaks App](https://github.com/davwheat/shannon-pixel-modem-tweaks-app)
- [shannonRE (Comsecuris)](https://github.com/Comsecuris/shannonRE)
- [Shannon Modem Loader for IDA Pro](https://github.com/alexander-pick/shannon_modem_loader)
- [Shannon S5000 Code Skeleton](https://github.com/grant-h/shannon_s5000)
- [Shannon S5123 (5G) Code Skeleton](https://github.com/grant-h/shannon_S5123)

### Academic Papers
- BaseMirror: "Automatic Reverse Engineering of Baseband Commands from Android's Radio Interface Layer" -- [ACM CCS 2024](https://dl.acm.org/doi/10.1145/3658644.3690254), [arXiv](https://arxiv.org/html/2409.00475v1)
- FirmWire: "Transparent Dynamic Analysis for Cellular Baseband Firmware" -- [NDSS 2022](https://www.ndss-symposium.org/wp-content/uploads/2022-136-paper.pdf)
- FirmState: "Bringing Cellular Protocol States to Shannon Baseband Emulation" -- [WiSec 2025](https://dl.acm.org/doi/10.1145/3734477.3734726)
- BaseSpec: "Comparative Analysis of Baseband Software and Cellular Specifications" -- [NDSS 2021](https://insuyun.github.io/pubs/2021/kim:basespec.pdf)
- BVFINDER: "Semantic-Enhanced Static Vulnerability Detection in Baseband Firmware" -- [ICSE 2024](https://dl.acm.org/doi/10.1145/3597503.3639158)

### Security Research
- Google Project Zero: [18 Zero-Days in Exynos Modems](https://projectzero.google/2023/03/multiple-internet-to-baseband-remote-rce.html)
- Natalie Silvanovich: [How to Hack Shannon Baseband](https://cfp.recon.cx/2023/talk/H78TBV/) (REcon 2023)
- Comsecuris: [Breaking Band - Shannon RE](https://comsecuris.com/slides/recon2016-breaking_band.pdf) (REcon 2016)
- Grant Hernandez: [Emulating Samsung's Baseband](https://i.blackhat.com/USA-20/Wednesday/us-20-Hernandez-Emulating-Samsungs-Baseband-For-Security-Testing.pdf) (Black Hat 2020)
- TASZK.io: [Samsung SIPC RIL Vulnerabilities](https://labs.taszk.io/blog/tags/samsung/) (CVE-2023-30644 through CVE-2023-30649)
- Synacktiv: [How to Design a Baseband Debugger](https://www.sstic.org/2020/presentation/how_to_design_a_baseband_debugger/) (SSTIC 2020)
- Roberto Paleari: [Samsung RIL / Multiclient Socket Research](https://roberto.greyhats.it/2016/05/samsung-access-rild.html)
- Replicant: [Samsung Galaxy Backdoor (RFS)](https://redmine.replicant.us/projects/replicant/wiki/SamsungGalaxyBackdoor)

### Google Baseband Security
- [Hardening Cellular Basebands in Android](https://security.googleblog.com/2023/12/hardening-cellular-basebands-in-android.html) (December 2023)
- [Pixel's Proactive Approach to Security: Cellular Modems](https://security.googleblog.com/2024/10/pixel-proactive-security-cellular-modems.html) (October 2024)
- [Android Mobile Network Security](https://source.android.com/docs/security/features/cellular-security/mobile-network-security)
- [Disable 2G](https://source.android.com/docs/security/features/cellular-security/disable-2g)

### Samsung Debug Interfaces
- [Samsung SysDump Guide](https://techblogs.42gears.com/generate-dumpstate-logs-on-samsung-android-devices/)
- [Samsung ServiceMode Reference (XDA)](https://xdaforums.com/t/ref-servicemode-how-to-make-your-samsung-perform-dog-tricks.2734094/)
- [Reverse Engineering Samsung SysDump Utilities](https://nickvsnetworking.com/reverse-engineering-samsung-sysdump-utils-to-unlock-ims-debug-tcpdump-on-samsung-phones/)
- [ShannonDM 1.5.1.2 (Internet Archive)](https://archive.org/details/exynos-log-tool.-7z)
- [Samsung S5000AP SIPC Device Tree](https://github.com/ianmacd/d2s/blob/master/arch/arm64/boot/dts/samsung/modem-s5000ap-sipc-pdata.dtsi)
- [Pixel 6a DM Port Discussion (XDA)](https://xdaforums.com/t/pixel-6a-diag-dm-port.4491121/)
- [Booting the Samsung Galaxy S7 Modem](https://eighty-twenty.org/2020/09/10/booting-samsung-galaxy-s7-modem)

### Android APIs
- [CellInfo API](https://developer.android.com/reference/android/telephony/CellInfo)
- [TelephonyManager API](https://developer.android.com/reference/android/telephony/TelephonyManager)
- [Android 14 Cellular Security](https://9to5google.com/2023/08/08/android-14-cellular-security/)
- [Android 15 Mobile Network Security Delay](https://www.androidauthority.com/android-15-mobile-network-security-pixel-3490909/)
- [Android 16 Mobile Network Security](https://www.androidauthority.com/android-16-mobile-network-security-3571497/)
- [Null Ciphers and Android (RWC 2023)](https://iacr.org/submit/files/slides/2023/rwc/rwc2023/3/slides.pdf)

### Curated Resource Lists
- [awesome-baseband-research](https://github.com/lololosys/awesome-baseband-research)
- [Cellular-Security-Papers](https://github.com/onehouwong/Cellular-Security-Papers)
- [FuzzingLabs: Shannon In A Nutshell](https://fuzzinglabs.com/breaking-down-the-baseband-shannon-in-a-nutshell/)
