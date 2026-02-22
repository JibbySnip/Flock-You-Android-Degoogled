package com.flockyou.shannon

import android.content.Context
import android.util.Log
import com.flockyou.shannon.sdm.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.FileInputStream
import java.io.IOException
import java.util.UUID

/**
 * Monitors the Samsung Shannon modem diagnostic interface (/dev/umts_dm0)
 * for NAS/RRC signaling events that indicate IMSI catcher presence.
 *
 * This monitor follows the same lifecycle pattern as CellularMonitor:
 * - Constructor takes a Context and optional error callback
 * - startMonitoring() / stopMonitoring() / destroy() lifecycle
 * - Exposes StateFlows for status, anomalies, and statistics
 *
 * The monitor runs a read loop on Dispatchers.IO that:
 * 1. Opens /dev/umts_dm0 as a FileInputStream
 * 2. Reads raw bytes into a buffer
 * 3. Feeds them to SdmFrameParser for frame reassembly
 * 4. Passes NAS frames to NasMessageParser for event extraction
 * 5. Converts security-relevant events into ShannonAnomaly objects
 *
 * Reconnection with exponential backoff (5s * attempt, max 5 attempts)
 * handles transient device node errors.
 */
class ShannonDiagMonitor(
    private val context: Context,
    private val errorCallback: ((String, String) -> Unit)? = null
) {
    companion object {
        private const val TAG = "ShannonDiagMonitor"
        private const val DIAG_DEVICE_PATH = "/dev/umts_dm0"
        private const val READ_BUFFER_SIZE = 4096
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val BASE_RECONNECT_DELAY_MS = 5000L
        private const val MAX_ANOMALIES_RETAINED = 200
    }

    // ==================== Public State ====================

    private val _status = MutableStateFlow(ShannonDiagStatus.IDLE)
    val status: StateFlow<ShannonDiagStatus> = _status.asStateFlow()

    private val _events = MutableSharedFlow<NasSignalingEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )
    val events: SharedFlow<NasSignalingEvent> = _events.asSharedFlow()

    private val _anomalies = MutableStateFlow<List<ShannonAnomaly>>(emptyList())
    val anomalies: StateFlow<List<ShannonAnomaly>> = _anomalies.asStateFlow()

    private val _statistics = MutableStateFlow(ShannonDiagStatistics())
    val statistics: StateFlow<ShannonDiagStatistics> = _statistics.asStateFlow()

    // ==================== Internal State ====================

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var readJob: Job? = null
    private val frameParser = SdmFrameParser()
    private val nasParser = NasMessageParser()
    private var reconnectAttempts = 0

    // ==================== Lifecycle ====================

    fun startMonitoring() {
        if (_status.value == ShannonDiagStatus.ACTIVE ||
            _status.value == ShannonDiagStatus.STARTING) {
            Log.w(TAG, "Already monitoring or starting")
            return
        }

        val capability = ShannonCapabilityDetector.detect()
        if (capability != ShannonCapabilityDetector.ShannonStatus.AVAILABLE) {
            _status.value = ShannonDiagStatus.UNAVAILABLE
            Log.w(TAG, "Shannon diagnostics unavailable: ${capability.displayName}")
            errorCallback?.invoke("Shannon Diagnostics", "Unavailable: ${capability.displayName}")
            return
        }

        _status.value = ShannonDiagStatus.STARTING
        reconnectAttempts = 0
        frameParser.reset()
        startReadLoop()
    }

    fun stopMonitoring() {
        readJob?.cancel()
        readJob = null
        _status.value = ShannonDiagStatus.IDLE
        Log.d(TAG, "Shannon diagnostic monitoring stopped")
    }

    fun destroy() {
        stopMonitoring()
        scope.cancel()
        Log.d(TAG, "Shannon diagnostic monitor destroyed")
    }

    // ==================== Read Loop ====================

    private fun startReadLoop() {
        readJob?.cancel()
        readJob = scope.launch {
            var stream: FileInputStream? = null
            try {
                stream = FileInputStream(DIAG_DEVICE_PATH)
                _status.value = ShannonDiagStatus.ACTIVE
                reconnectAttempts = 0
                Log.i(TAG, "Connected to Shannon diagnostic interface")

                val buffer = ByteArray(READ_BUFFER_SIZE)
                while (isActive) {
                    val bytesRead = try {
                        stream.read(buffer)
                    } catch (e: IOException) {
                        if (isActive) {
                            Log.e(TAG, "Read error: ${e.message}")
                            handleReadError()
                        }
                        break
                    }

                    if (bytesRead <= 0) {
                        delay(10) // Avoid busy-wait on empty reads
                        continue
                    }

                    // Parse frames from raw bytes
                    val frames = frameParser.feed(buffer, 0, bytesRead)

                    // Update statistics
                    updateStatistics()

                    // Process NAS frames for security events
                    for (frame in frames) {
                        if (frame.isNasFrame || frame.isRrcFrame) {
                            val event = nasParser.parse(frame)
                            if (event != null) {
                                _events.tryEmit(event)
                                processEvent(event)
                            }
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SELinux denied access to $DIAG_DEVICE_PATH: ${e.message}")
                _status.value = ShannonDiagStatus.ERROR
                errorCallback?.invoke("Shannon Diagnostics", "SELinux denied access: ${e.message}")
            } catch (e: IOException) {
                Log.e(TAG, "Failed to open $DIAG_DEVICE_PATH: ${e.message}")
                if (isActive) handleReadError()
            } catch (e: CancellationException) {
                throw e // Don't catch cancellation
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in Shannon read loop: ${e.message}", e)
                _status.value = ShannonDiagStatus.ERROR
                errorCallback?.invoke("Shannon Diagnostics", "Error: ${e.message}")
            } finally {
                try { stream?.close() } catch (_: Exception) {}
            }
        }
    }

    private suspend fun handleReadError() {
        reconnectAttempts++
        if (reconnectAttempts > MAX_RECONNECT_ATTEMPTS) {
            _status.value = ShannonDiagStatus.ERROR
            errorCallback?.invoke("Shannon Diagnostics", "Max reconnect attempts reached")
            Log.e(TAG, "Max reconnect attempts ($MAX_RECONNECT_ATTEMPTS) reached, giving up")
            return
        }

        _status.value = ShannonDiagStatus.RECONNECTING
        val delayMs = BASE_RECONNECT_DELAY_MS * reconnectAttempts
        Log.w(TAG, "Reconnecting in ${delayMs}ms (attempt $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS)")
        delay(delayMs)
        frameParser.reset()
        startReadLoop()
    }

    // ==================== Event Processing ====================

    private fun processEvent(event: NasSignalingEvent) {
        val anomaly = convertToAnomaly(event) ?: return

        val current = _anomalies.value.toMutableList()
        current.add(0, anomaly)

        // Trim old anomalies
        if (current.size > MAX_ANOMALIES_RETAINED) {
            current.subList(MAX_ANOMALIES_RETAINED, current.size).clear()
        }

        _anomalies.value = current
        Log.w(TAG, "SHANNON ANOMALY: ${anomaly.type.displayName} - ${anomaly.description}")
    }

    /**
     * Convert a NAS signaling event into a ShannonAnomaly if it represents
     * a security concern.
     */
    private fun convertToAnomaly(event: NasSignalingEvent): ShannonAnomaly? {
        return when (event) {
            is NasSignalingEvent.SecurityModeCommand -> {
                if (event.cipherAlgorithm.isNullCipher) {
                    ShannonAnomaly(
                        id = UUID.randomUUID().toString(),
                        timestamp = event.wallClockMs,
                        type = ShannonAnomalyType.NULL_CIPHER,
                        severity = ShannonSeverity.CRITICAL,
                        confidence = 1.0f,
                        description = "Null cipher ${event.cipherAlgorithm.displayName} negotiated via ${event.authType.displayName}. " +
                                "All traffic is unencrypted and can be intercepted.",
                        details = mapOf(
                            "cipher" to event.cipherAlgorithm.displayName,
                            "integrity" to (event.integrityAlgorithm?.displayName ?: "N/A"),
                            "rat" to event.authType.displayName
                        )
                    )
                } else null
            }
            is NasSignalingEvent.IdentityRequest -> {
                if (event.identityType == IdentityType.IMSI) {
                    ShannonAnomaly(
                        id = UUID.randomUUID().toString(),
                        timestamp = event.wallClockMs,
                        type = ShannonAnomalyType.IMSI_PAGING,
                        severity = ShannonSeverity.CRITICAL,
                        confidence = 0.85f,
                        description = "Network requested IMSI identity via ${event.authType.displayName}. " +
                                "Legitimate networks use TMSI/GUTI. This is a strong IMSI catcher indicator.",
                        details = mapOf(
                            "identityType" to event.identityType.displayName,
                            "rat" to event.authType.displayName
                        )
                    )
                } else null
            }
            is NasSignalingEvent.SilentSms -> {
                ShannonAnomaly(
                    id = UUID.randomUUID().toString(),
                    timestamp = event.wallClockMs,
                    type = ShannonAnomalyType.SILENT_SMS,
                    severity = ShannonSeverity.HIGH,
                    confidence = 0.85f,
                    description = "Silent SMS (Type 0) received. This message is invisible to the user " +
                            "and is used by law enforcement to confirm device location.",
                    details = emptyMap()
                )
            }
            is NasSignalingEvent.Forced2gRedirect -> {
                ShannonAnomaly(
                    id = UUID.randomUUID().toString(),
                    timestamp = event.wallClockMs,
                    type = ShannonAnomalyType.FORCED_2G,
                    severity = ShannonSeverity.HIGH,
                    confidence = 0.85f,
                    description = "Forced redirect from ${event.sourceRat} to 2G (GSM). " +
                            "2G has weaker encryption and is easier to intercept.",
                    details = mapOf("sourceRat" to event.sourceRat)
                )
            }
            is NasSignalingEvent.AuthenticationRequest -> {
                if (!event.isRandValid || !event.isAutnValid) {
                    ShannonAnomaly(
                        id = UUID.randomUUID().toString(),
                        timestamp = event.wallClockMs,
                        type = ShannonAnomalyType.AUTH_ANOMALY,
                        severity = ShannonSeverity.MEDIUM,
                        confidence = 0.6f,
                        description = "Authentication request with anomalous parameters via ${event.authType.displayName}. " +
                                "RAND valid: ${event.isRandValid}, AUTN valid: ${event.isAutnValid}.",
                        details = mapOf(
                            "randValid" to event.isRandValid.toString(),
                            "autnValid" to event.isAutnValid.toString(),
                            "rat" to event.authType.displayName
                        )
                    )
                } else null
            }
            is NasSignalingEvent.RegistrationReject -> null // Context-dependent, not standalone anomaly
        }
    }

    private fun updateStatistics() {
        _statistics.value = ShannonDiagStatistics(
            framesDecoded = frameParser.framesDecoded,
            bytesProcessed = frameParser.bytesProcessed,
            framesCorrupt = frameParser.framesCorrupt,
            anomalyCount = _anomalies.value.size,
            isActive = _status.value == ShannonDiagStatus.ACTIVE
        )
    }
}

// ==================== Data Classes ====================

enum class ShannonDiagStatus(val displayName: String) {
    IDLE("Idle"),
    STARTING("Starting"),
    ACTIVE("Active"),
    RECONNECTING("Reconnecting"),
    UNAVAILABLE("Unavailable"),
    ERROR("Error")
}

enum class ShannonAnomalyType(val displayName: String) {
    NULL_CIPHER("Null Cipher Negotiated"),
    IMSI_PAGING("IMSI Identity Request"),
    SILENT_SMS("Silent SMS Received"),
    FORCED_2G("Forced 2G Redirect"),
    AUTH_ANOMALY("Authentication Anomaly")
}

enum class ShannonSeverity(val displayName: String) {
    CRITICAL("Critical"),
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low")
}

data class ShannonAnomaly(
    val id: String,
    val timestamp: Long,
    val type: ShannonAnomalyType,
    val severity: ShannonSeverity,
    val confidence: Float,
    val description: String,
    val details: Map<String, String> = emptyMap()
)

data class ShannonDiagStatistics(
    val framesDecoded: Long = 0L,
    val bytesProcessed: Long = 0L,
    val framesCorrupt: Long = 0L,
    val anomalyCount: Int = 0,
    val isActive: Boolean = false
)
