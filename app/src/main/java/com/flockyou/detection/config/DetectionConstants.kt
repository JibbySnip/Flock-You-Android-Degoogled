package com.flockyou.detection.config

/**
 * Centralized detection threshold and timing constants for all 7 detection protocols.
 *
 * All tunable numeric constants are collected here for easy review and adjustment.
 * Constants that are NOT centralized here (kept per-handler):
 *   - TAG strings
 *   - UUID values (runtime-allocated, not compile-time constants)
 *   - Cached sets/collections (runtime-allocated)
 *   - Multi-line documentation strings used for LLM prompts
 */
object DetectionConstants {

    // ==================== SHARED CONSTANTS ====================

    /** Constants used by multiple detection protocols. */
    object Common {
        /** Minimum RSSI for detection consideration (-100 dBm = very weak, -30 dBm = very strong) */
        const val DEFAULT_RSSI_THRESHOLD = -90

        /** Strong signal threshold for proximity alerts */
        const val STRONG_SIGNAL_RSSI = -50

        /** Very close proximity threshold */
        const val IMMEDIATE_PROXIMITY_RSSI = -40

        /** Rate limit between detections of the same device (milliseconds) */
        const val DETECTION_RATE_LIMIT_MS = 30000L
    }

    // ==================== BLE PROTOCOL ====================

    /** BLE-specific detection constants from BleDetectionHandler. */
    object Ble {
        // --- Advertising rate thresholds ---

        /** Advertising rate threshold for Signal trigger detection (packets per second) */
        const val ADVERTISING_RATE_SPIKE_THRESHOLD = 20f

        /** Normal advertising rate for most BLE devices (packets per second) */
        const val NORMAL_ADVERTISING_RATE = 1f

        /** Time window for rate calculation (milliseconds) */
        const val RATE_CALCULATION_WINDOW_MS = 5000L

        // --- Manufacturer IDs ---

        /** Apple manufacturer ID (used in AirTags and as BLE wrapper) */
        const val MANUFACTURER_ID_APPLE = 0x004C

        /** Nordic Semiconductor manufacturer ID (used in Axon devices) */
        const val MANUFACTURER_ID_NORDIC = 0x0059

        /** Samsung manufacturer ID */
        const val MANUFACTURER_ID_SAMSUNG = 0x0075

        /** Tile manufacturer ID */
        const val MANUFACTURER_ID_TILE = 0x00C7

        /** Google manufacturer ID (used in Fast Pair) */
        const val MANUFACTURER_ID_GOOGLE = 0x00E0

        // --- Flipper Zero BLE spam detection ---
        // Thresholds tuned to reduce false positives in busy BLE environments
        // (malls, airports, conferences, etc.)

        /** Time window for BLE spam detection (milliseconds) - extended to reduce transient triggers */
        const val BLE_SPAM_DETECTION_WINDOW_MS = 20_000L

        /** Threshold for Apple device advertisements in spam window to trigger spam detection */
        const val APPLE_SPAM_THRESHOLD = 30

        /** Threshold for Fast Pair advertisements in spam window to trigger spam detection */
        const val FAST_PAIR_SPAM_THRESHOLD = 25

        /** Threshold for unique device names in spam window (rapid name changing) */
        const val DEVICE_NAME_CHANGE_THRESHOLD = 12

        /** Minimum threat score to trigger spam detection */
        const val SPAM_THREAT_SCORE_THRESHOLD = 70
    }

    // ==================== WIFI PROTOCOL ====================

    /** WiFi-specific detection constants from WifiDetectionHandler. */
    object Wifi {
        // WiFi currently uses only Common constants (DEFAULT_RSSI_THRESHOLD,
        // STRONG_SIGNAL_RSSI, IMMEDIATE_PROXIMITY_RSSI, DETECTION_RATE_LIMIT_MS).
        // This object is reserved for future WiFi-specific thresholds.
    }

    // ==================== CELLULAR PROTOCOL ====================

    /** Cellular-specific detection constants from CellularDetectionHandler. */
    object Cellular {
        // IMSI catcher score thresholds - aligned with severity levels
        // Score 90-100 = CRITICAL (immediate threat)
        // Score 70-89 = HIGH (confirmed surveillance indicators)
        // Score 50-69 = MEDIUM (likely surveillance equipment)
        // Score 30-49 = LOW (possible, continue monitoring)
        // Score 0-29 = INFO (notable but not threatening)

        /** IMSI catcher score threshold for CRITICAL severity */
        const val IMSI_CRITICAL_THRESHOLD = 90

        /** IMSI catcher score threshold for HIGH severity */
        const val IMSI_HIGH_THRESHOLD = 70

        /** IMSI catcher score threshold for MEDIUM severity */
        const val IMSI_MEDIUM_THRESHOLD = 50

        /** IMSI catcher score threshold for LOW severity */
        const val IMSI_LOW_THRESHOLD = 30
    }

    // ==================== GNSS PROTOCOL ====================

    /** GNSS-specific detection constants from GnssDetectionHandler. */
    object Gnss {
        // --- Spoofing/jamming score thresholds ---

        /** Spoofing score threshold for HIGH severity */
        const val SPOOFING_HIGH_THRESHOLD = 85f

        /** Spoofing score threshold for MEDIUM severity */
        const val SPOOFING_MEDIUM_THRESHOLD = 75f

        /** Jamming score threshold for HIGH severity */
        const val JAMMING_HIGH_THRESHOLD = 85f

        /** Jamming score threshold for MEDIUM severity */
        const val JAMMING_MEDIUM_THRESHOLD = 75f

        // --- C/N0 thresholds ---
        // Calibrated based on real-world GNSS behavior.
        // Normal GNSS signals have variance of 0.5-5.0 due to different elevation angles,
        // atmospheric conditions, multipath, etc. Only extremely low variance is suspicious.

        /** C/N0 variance below which spoofing is indicated (dB-Hz) */
        const val CN0_VARIANCE_SUSPICIOUS = 0.15

        /** C/N0 variance warning level - flag only with other indicators (dB-Hz) */
        const val CN0_VARIANCE_WARNING = 0.5

        /** C/N0 deviation from baseline considered significant (standard deviations) */
        const val CN0_DEVIATION_SIGNIFICANT = 3.0

        // --- Geometry thresholds ---

        /** Geometry score below which geometry is considered poor */
        const val GEOMETRY_POOR_THRESHOLD = 0.4f

        /** Count of low-elevation high-signal satellites to flag geometry anomaly */
        const val LOW_ELEV_HIGH_SIGNAL_THRESHOLD = 2

        // --- Clock drift thresholds ---

        /** Number of clock drift jumps considered suspicious */
        const val DRIFT_JUMP_SUSPICIOUS = 3

        /** Cumulative clock drift considered suspicious (milliseconds) */
        const val DRIFT_CUMULATIVE_SUSPICIOUS_MS = 10

        // --- Satellite count thresholds ---

        /** Minimum satellites for a good fix */
        const val GOOD_FIX_SATELLITES = 10

        /** Satellite count indicating a strong fix */
        const val STRONG_FIX_SATELLITES = 30

        /** Maximum satellites to plausibly claim jamming */
        const val JAMMING_MAX_SATELLITES = 8
    }

    // ==================== RF PROTOCOL ====================

    /** RF-specific detection constants from RfDetectionHandler. */
    object Rf {
        /** Minimum hidden network count to consider suspicious */
        const val HIDDEN_NETWORK_SUSPICIOUS_COUNT = 10

        /** Minimum hidden network ratio to consider suspicious */
        const val HIDDEN_NETWORK_SUSPICIOUS_RATIO = 0.35f

        /** Minimum surveillance camera count for area detection */
        const val SURVEILLANCE_CAMERA_MIN_COUNT = 5

        /** Strong signal threshold for RF detection (dBm) */
        const val STRONG_SIGNAL_THRESHOLD = -50

        /** Signal variance threshold below which variance is suspiciously low */
        const val SIGNAL_VARIANCE_LOW_THRESHOLD = 50f
    }

    // ==================== ULTRASONIC PROTOCOL ====================

    /** Ultrasonic-specific detection constants from UltrasonicDetectionHandler. */
    object Ultrasonic {
        // --- Frequency matching ---

        /** Frequency tolerance for beacon signature matching (Hz) */
        const val FREQUENCY_TOLERANCE_HZ = 100

        // --- Known beacon source frequencies (Hz) ---

        const val FREQ_SILVERPUSH = 18000
        const val FREQ_SILVERPUSH_ALT = 18200
        const val FREQ_ALPHONSO = 18500
        const val FREQ_ZAPR = 17500
        const val FREQ_SIGNAL360 = 19000
        const val FREQ_REALEYES = 19200
        const val FREQ_LISNR = 19500
        const val FREQ_TVISION = 19800
        const val FREQ_SHOPKICK = 20000
        const val FREQ_SAMBA_TV = 20200
        const val FREQ_RETAIL_BAND_1 = 20500
        const val FREQ_RETAIL_BAND_2 = 21000
        const val FREQ_INSCAPE = 21500
        const val FREQ_DATA_PLUS_MATH = 22000

        // --- Risk assessment thresholds ---

        /** Persistence score above which risk is elevated */
        const val HIGH_PERSISTENCE_THRESHOLD = 0.7f

        /** Number of detected locations above which following is indicated */
        const val FOLLOWING_LOCATION_THRESHOLD = 2

        /** SNR above which signal is considered strong (dB) */
        const val HIGH_SNR_THRESHOLD_DB = 20.0

        /** Amplitude above which signal is considered strong (dB) */
        const val STRONG_AMPLITUDE_THRESHOLD_DB = -35.0
    }

    // ==================== SATELLITE PROTOCOL ====================

    /** Satellite-specific detection constants from SatelliteDetectionHandler. */
    object Satellite {
        // SatelliteDetectionHandler currently has no numeric threshold constants
        // beyond TAG. Timing/threshold values are configured via SatelliteThresholds
        // in DetectionSettings. This object is reserved for future satellite-specific
        // constants.
    }
}
