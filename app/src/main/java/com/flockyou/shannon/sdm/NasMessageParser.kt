package com.flockyou.shannon.sdm

/**
 * Parses SDM frame payloads into NAS/RRC signaling events relevant to
 * IMSI catcher detection.
 *
 * This parser focuses on the specific NAS message types that are definitive
 * indicators of cell site simulators (StingRay, Hailstorm, etc.):
 *
 * - Identity Request with IMSI type: Real networks use TMSI/GUTI; requesting IMSI
 *   over the air is a hallmark of IMSI catchers
 * - Security Mode Command with null cipher (EEA0/A5/0): Disabling encryption
 *   allows interception of all traffic
 * - Silent SMS (Type 0): Used by law enforcement to ping device location without
 *   user notification
 * - Forced 2G redirect: RRC redirect to GSM bands enables easier interception
 * - Authentication anomalies: Malformed RAND/AUTN parameters
 *
 * NAS message IDs are from 3GPP TS 24.301 (LTE), TS 24.008 (GSM), and
 * TS 24.501 (5G NR).
 */
class NasMessageParser {

    companion object {
        // LTE NAS EMM message IDs (3GPP TS 24.301)
        private const val LTE_ATTACH_REJECT: Byte = 0x44
        private const val LTE_TAU_REJECT: Byte = 0x4B
        private const val LTE_AUTH_REQUEST: Byte = 0x52
        private const val LTE_IDENTITY_REQUEST: Byte = 0x55
        private const val LTE_SECURITY_MODE_COMMAND: Byte = 0x5D

        // GSM MM message IDs (3GPP TS 24.008)
        private const val GSM_IDENTITY_REQUEST: Byte = 0x18
        private const val GSM_AUTH_REQUEST: Byte = 0x12
        private const val GSM_CM_SERVICE_ACCEPT: Byte = 0x21
        private const val GSM_CIPHERING_MODE_COMMAND: Byte = 0x35

        // 5G NR NAS message IDs (3GPP TS 24.501)
        private const val NR_IDENTITY_REQUEST: Byte = 0x5C
        private const val NR_AUTH_REQUEST: Byte = 0x56
        private const val NR_SECURITY_MODE_COMMAND: Byte = 0x5D
        private const val NR_REGISTRATION_REJECT: Byte = 0x44

        // SMS CP message types (3GPP TS 24.011)
        private const val CP_DATA: Byte = 0x01

        // Identity types
        private const val IDENTITY_TYPE_IMSI: Byte = 0x01
        private const val IDENTITY_TYPE_IMEI: Byte = 0x02
        private const val IDENTITY_TYPE_TMSI: Byte = 0x04
        private const val IDENTITY_TYPE_SUCI: Byte = 0x07 // 5G

        // LTE RRC redirect type
        private const val RRC_REDIRECT_GERAN: Byte = 0x01 // Redirect to GSM/EDGE
    }

    /**
     * Parse an SDM frame into a NAS signaling event, if it contains a
     * security-relevant message.
     *
     * Returns null for frames that don't contain IMSI catcher indicators.
     */
    fun parse(frame: SdmFrame): NasSignalingEvent? {
        if (frame.payload.isEmpty()) return null

        return when (frame.type) {
            SdmFrameParser.TYPE_LTE_NAS_EMM -> parseLteEmm(frame)
            SdmFrameParser.TYPE_NR_NAS -> parseNrNas(frame)
            SdmFrameParser.TYPE_GSM_MM -> parseGsmMm(frame)
            SdmFrameParser.TYPE_LTE_NAS_ESM -> parseLteEsm(frame)
            SdmFrameParser.TYPE_LTE_RRC, SdmFrameParser.TYPE_NR_RRC -> parseRrc(frame)
            else -> null
        }
    }

    /**
     * Parse LTE NAS EMM (EPS Mobility Management) messages.
     */
    private fun parseLteEmm(frame: SdmFrame): NasSignalingEvent? {
        val payload = frame.payload
        if (payload.isEmpty()) return null

        // First byte of NAS payload is the message type
        // (after security header, which the SDM frame strips for us)
        val messageType = findNasMessageType(payload) ?: return null

        return when (messageType) {
            LTE_IDENTITY_REQUEST -> parseIdentityRequest(frame, payload, AuthType.LTE)
            LTE_SECURITY_MODE_COMMAND -> parseLteSecurityModeCommand(frame, payload)
            LTE_AUTH_REQUEST -> parseLteAuthRequest(frame, payload)
            LTE_ATTACH_REJECT -> parseRegistrationReject(frame, payload, "ATTACH_REJECT")
            LTE_TAU_REJECT -> parseRegistrationReject(frame, payload, "TAU_REJECT")
            else -> null
        }
    }

    /**
     * Parse 5G NR NAS messages.
     */
    private fun parseNrNas(frame: SdmFrame): NasSignalingEvent? {
        val payload = frame.payload
        if (payload.isEmpty()) return null

        val messageType = findNasMessageType(payload) ?: return null

        return when (messageType) {
            NR_IDENTITY_REQUEST -> parseIdentityRequest(frame, payload, AuthType.NR_5G)
            NR_SECURITY_MODE_COMMAND -> parseNrSecurityModeCommand(frame, payload)
            NR_AUTH_REQUEST -> parseNrAuthRequest(frame, payload)
            NR_REGISTRATION_REJECT -> parseRegistrationReject(frame, payload, "5G_REGISTRATION_REJECT")
            else -> null
        }
    }

    /**
     * Parse GSM MM (Mobility Management) messages.
     */
    private fun parseGsmMm(frame: SdmFrame): NasSignalingEvent? {
        val payload = frame.payload
        if (payload.isEmpty()) return null

        val messageType = findNasMessageType(payload) ?: return null

        return when (messageType) {
            GSM_IDENTITY_REQUEST -> parseIdentityRequest(frame, payload, AuthType.GSM)
            GSM_CIPHERING_MODE_COMMAND -> parseGsmCipheringModeCommand(frame, payload)
            GSM_AUTH_REQUEST -> parseGsmAuthRequest(frame, payload)
            else -> null
        }
    }

    /**
     * Parse LTE ESM messages -- check for silent SMS in piggybacked NAS.
     */
    private fun parseLteEsm(frame: SdmFrame): NasSignalingEvent? {
        // SMS CP-DATA can be carried in ESM containers
        return checkForSilentSms(frame)
    }

    /**
     * Parse RRC messages for forced 2G redirects.
     */
    private fun parseRrc(frame: SdmFrame): NasSignalingEvent? {
        val payload = frame.payload
        if (payload.size < 2) return null

        // Look for RRC Connection Release with redirect indication
        // The exact byte position depends on the RRC message structure
        // We look for redirect-to-GERAN patterns
        return checkForForced2gRedirect(frame, payload)
    }

    // ==================== Identity Request Parsing ====================

    private fun parseIdentityRequest(
        frame: SdmFrame,
        payload: ByteArray,
        authType: AuthType
    ): NasSignalingEvent? {
        val identityTypeByte = findIdentityType(payload) ?: return null
        val identityType = IdentityType.fromByte(identityTypeByte) ?: return null

        // Only flag suspicious identity types -- TMSI/GUTI requests are normal operation
        if (identityType == IdentityType.TMSI || identityType == IdentityType.UNKNOWN) return null

        return NasSignalingEvent.IdentityRequest(
            timestamp = frame.timestamp,
            wallClockMs = System.currentTimeMillis(),
            identityType = identityType,
            authType = authType,
            isDownlink = frame.isDownlink
        )
    }

    // ==================== Security Mode Command Parsing ====================

    private fun parseLteSecurityModeCommand(frame: SdmFrame, payload: ByteArray): NasSignalingEvent? {
        // NAS security algorithms IE: byte after message type
        // Bits 4-6: cipher algorithm (EEA0-EEA3)
        // Bits 0-2: integrity algorithm (EIA0-EIA3)
        val algosByte = findSecurityAlgorithmsByte(payload) ?: return null

        val cipherAlgoId = (algosByte.toInt() shr 4) and 0x07
        val integrityAlgoId = algosByte.toInt() and 0x07

        val cipherAlgorithm = CipherAlgorithm.fromLteId(cipherAlgoId)
        val integrityAlgorithm = IntegrityAlgorithm.fromLteId(integrityAlgoId)

        return NasSignalingEvent.SecurityModeCommand(
            timestamp = frame.timestamp,
            wallClockMs = System.currentTimeMillis(),
            cipherAlgorithm = cipherAlgorithm,
            integrityAlgorithm = integrityAlgorithm,
            authType = AuthType.LTE,
            isDownlink = frame.isDownlink
        )
    }

    private fun parseNrSecurityModeCommand(frame: SdmFrame, payload: ByteArray): NasSignalingEvent? {
        val algosByte = findSecurityAlgorithmsByte(payload) ?: return null

        val cipherAlgoId = (algosByte.toInt() shr 4) and 0x07
        val integrityAlgoId = algosByte.toInt() and 0x07

        val cipherAlgorithm = CipherAlgorithm.fromNrId(cipherAlgoId)
        val integrityAlgorithm = IntegrityAlgorithm.fromNrId(integrityAlgoId)

        return NasSignalingEvent.SecurityModeCommand(
            timestamp = frame.timestamp,
            wallClockMs = System.currentTimeMillis(),
            cipherAlgorithm = cipherAlgorithm,
            integrityAlgorithm = integrityAlgorithm,
            authType = AuthType.NR_5G,
            isDownlink = frame.isDownlink
        )
    }

    private fun parseGsmCipheringModeCommand(frame: SdmFrame, payload: ByteArray): NasSignalingEvent? {
        // GSM Ciphering Mode Command: cipher algorithm in bits 1-3 of byte after msg type
        val algosByte = findSecurityAlgorithmsByte(payload) ?: return null
        val cipherAlgoId = (algosByte.toInt() shr 1) and 0x07

        val cipherAlgorithm = CipherAlgorithm.fromGsmId(cipherAlgoId)

        return NasSignalingEvent.SecurityModeCommand(
            timestamp = frame.timestamp,
            wallClockMs = System.currentTimeMillis(),
            cipherAlgorithm = cipherAlgorithm,
            integrityAlgorithm = null, // GSM doesn't have separate integrity
            authType = AuthType.GSM,
            isDownlink = frame.isDownlink
        )
    }

    // ==================== Authentication Request Parsing ====================

    private fun parseLteAuthRequest(frame: SdmFrame, payload: ByteArray): NasSignalingEvent? {
        // LTE Authentication Request contains RAND (16 bytes) and AUTN (16 bytes)
        // Check for malformed values that indicate a fake base station
        val (randValid, autnValid) = validateAuthParams(payload)

        // Only flag as anomaly if parameters look wrong
        if (randValid && autnValid) return null

        return NasSignalingEvent.AuthenticationRequest(
            timestamp = frame.timestamp,
            wallClockMs = System.currentTimeMillis(),
            authType = AuthType.LTE,
            isRandValid = randValid,
            isAutnValid = autnValid,
            isDownlink = frame.isDownlink
        )
    }

    private fun parseNrAuthRequest(frame: SdmFrame, payload: ByteArray): NasSignalingEvent? {
        val (randValid, autnValid) = validateAuthParams(payload)
        if (randValid && autnValid) return null

        return NasSignalingEvent.AuthenticationRequest(
            timestamp = frame.timestamp,
            wallClockMs = System.currentTimeMillis(),
            authType = AuthType.NR_5G,
            isRandValid = randValid,
            isAutnValid = autnValid,
            isDownlink = frame.isDownlink
        )
    }

    private fun parseGsmAuthRequest(frame: SdmFrame, payload: ByteArray): NasSignalingEvent? {
        val (randValid, _) = validateAuthParams(payload)
        if (randValid) return null

        return NasSignalingEvent.AuthenticationRequest(
            timestamp = frame.timestamp,
            wallClockMs = System.currentTimeMillis(),
            authType = AuthType.GSM,
            isRandValid = randValid,
            isAutnValid = true, // GSM doesn't have AUTN
            isDownlink = frame.isDownlink
        )
    }

    // ==================== Silent SMS Detection ====================

    private fun checkForSilentSms(frame: SdmFrame): NasSignalingEvent? {
        val payload = frame.payload
        if (payload.size < 5) return null

        // Look for SMS CP-DATA containing TP-PID = 0x40 (Type 0 SMS)
        // Type 0 SMS: silently received, no notification, used for location pinging
        for (i in 0 until payload.size - 2) {
            if (payload[i] == CP_DATA) {
                // Search for TP-PID field
                for (j in i + 1 until minOf(payload.size, i + 20)) {
                    if (payload[j] == 0x40.toByte()) {
                        return NasSignalingEvent.SilentSms(
                            timestamp = frame.timestamp,
                            wallClockMs = System.currentTimeMillis(),
                            isDownlink = frame.isDownlink
                        )
                    }
                }
            }
        }
        return null
    }

    // ==================== Forced 2G Redirect Detection ====================

    private fun checkForForced2gRedirect(frame: SdmFrame, payload: ByteArray): NasSignalingEvent? {
        // RRC Connection Release with redirect to GERAN (2G)
        // Look for redirect indication in RRC payload
        if (payload.size < 3) return null

        // Check for GERAN redirect target in RRC release messages
        // The exact encoding varies but we look for the redirect-to-GERAN IE
        for (i in 0 until payload.size - 1) {
            if (payload[i] == RRC_REDIRECT_GERAN) {
                // Verify this is in a redirect context (check surrounding bytes)
                // This is a simplified check -- real implementation would need full ASN.1 parsing
                val hasRedirectContext = i > 0 && (
                    payload[i - 1].toInt() and 0xF0 == 0x20 || // RRC release redirect IE
                    payload[i - 1].toInt() and 0xF0 == 0x30    // Mobility from EUTRA IE
                )
                if (hasRedirectContext) {
                    return NasSignalingEvent.Forced2gRedirect(
                        timestamp = frame.timestamp,
                        wallClockMs = System.currentTimeMillis(),
                        sourceRat = if (frame.type == SdmFrameParser.TYPE_NR_RRC) "NR" else "LTE",
                        isDownlink = frame.isDownlink
                    )
                }
            }
        }
        return null
    }

    // ==================== Registration Reject ====================

    private fun parseRegistrationReject(
        frame: SdmFrame,
        payload: ByteArray,
        rejectType: String
    ): NasSignalingEvent? {
        // Extract EMM/5GMM cause value
        val cause = if (payload.size >= 2) payload[1].toInt() and 0xFF else 0

        return NasSignalingEvent.RegistrationReject(
            timestamp = frame.timestamp,
            wallClockMs = System.currentTimeMillis(),
            rejectType = rejectType,
            causeCode = cause,
            isDownlink = frame.isDownlink
        )
    }

    // ==================== Helper Methods ====================

    /**
     * Find the NAS message type byte in the payload.
     * SDM frames deliver decoded NAS messages with security headers already stripped,
     * so the first byte is always the NAS message type.
     */
    private fun findNasMessageType(payload: ByteArray): Byte? {
        if (payload.isEmpty()) return null
        return payload[0]
    }

    /**
     * Find the identity type from an Identity Request payload.
     * Byte after the message type contains the identity type in the lower 3 bits.
     */
    private fun findIdentityType(payload: ByteArray): Byte? {
        if (payload.size < 2) return null
        return (payload[1].toInt() and 0x07).toByte()
    }

    /**
     * Find the security algorithms byte from a Security Mode Command payload.
     * The algorithms IE is the byte immediately after the message type.
     */
    private fun findSecurityAlgorithmsByte(payload: ByteArray): Byte? {
        if (payload.size < 2) return null
        return payload[1]
    }

    /**
     * Validate RAND and AUTN authentication parameters.
     * Returns (randValid, autnValid).
     *
     * Fake base stations sometimes send all-zero or patterned RAND values.
     */
    private fun validateAuthParams(payload: ByteArray): Pair<Boolean, Boolean> {
        // RAND (16 bytes) starts after message type (1) + KSI (1)
        val offset = 2
        if (payload.size < offset + 16) return Pair(true, true) // Can't validate

        // Check RAND for suspicious patterns
        val rand = payload.copyOfRange(offset, offset + 16)
        val randValid = !isAllZeros(rand) && !isRepeatingPattern(rand)

        // Check AUTN (16 bytes after RAND)
        val autnValid = if (payload.size >= offset + 32) {
            val autn = payload.copyOfRange(offset + 16, offset + 32)
            !isAllZeros(autn)
        } else {
            true // Can't validate
        }

        return Pair(randValid, autnValid)
    }

    private fun isAllZeros(data: ByteArray): Boolean = data.all { it == 0.toByte() }

    private fun isRepeatingPattern(data: ByteArray): Boolean {
        if (data.size < 4) return false
        // Check if first 2 bytes repeat throughout
        val pattern = (data[0].toInt() shl 8) or (data[1].toInt() and 0xFF)
        for (i in 2 until data.size - 1 step 2) {
            val value = (data[i].toInt() shl 8) or (data[i + 1].toInt() and 0xFF)
            if (value != pattern) return false
        }
        return true
    }
}

// ==================== NAS Signaling Events ====================

/**
 * Sealed class hierarchy for security-relevant NAS signaling events
 * observed from the Shannon modem diagnostic interface.
 */
sealed class NasSignalingEvent {
    abstract val timestamp: Long
    abstract val wallClockMs: Long
    abstract val isDownlink: Boolean

    /**
     * Identity Request: Network asking the device to identify itself.
     * CRITICAL if requesting IMSI -- legitimate networks use TMSI/GUTI.
     */
    data class IdentityRequest(
        override val timestamp: Long,
        override val wallClockMs: Long,
        val identityType: IdentityType,
        val authType: AuthType,
        override val isDownlink: Boolean
    ) : NasSignalingEvent()

    /**
     * Security Mode Command: Network configuring encryption/integrity.
     * DEFINITIVE IMSI catcher indicator if cipher is EEA0/NEA0/A5/0 (null cipher).
     */
    data class SecurityModeCommand(
        override val timestamp: Long,
        override val wallClockMs: Long,
        val cipherAlgorithm: CipherAlgorithm,
        val integrityAlgorithm: IntegrityAlgorithm?,
        val authType: AuthType,
        override val isDownlink: Boolean
    ) : NasSignalingEvent()

    /**
     * Silent SMS (Type 0): Received without user notification.
     * Used by law enforcement to confirm device location.
     */
    data class SilentSms(
        override val timestamp: Long,
        override val wallClockMs: Long,
        override val isDownlink: Boolean
    ) : NasSignalingEvent()

    /**
     * Forced redirect from LTE/5G to 2G (GERAN).
     * Downgrade attack enabling easier interception.
     */
    data class Forced2gRedirect(
        override val timestamp: Long,
        override val wallClockMs: Long,
        val sourceRat: String,
        override val isDownlink: Boolean
    ) : NasSignalingEvent()

    /**
     * Authentication Request with anomalous parameters.
     * May indicate a fake base station with poor authentication implementation.
     */
    data class AuthenticationRequest(
        override val timestamp: Long,
        override val wallClockMs: Long,
        val authType: AuthType,
        val isRandValid: Boolean,
        val isAutnValid: Boolean,
        override val isDownlink: Boolean
    ) : NasSignalingEvent()

    /**
     * Registration/Attach reject from the network.
     * Context-dependent -- may indicate IMSI catcher forcing re-registration.
     */
    data class RegistrationReject(
        override val timestamp: Long,
        override val wallClockMs: Long,
        val rejectType: String,
        val causeCode: Int,
        override val isDownlink: Boolean
    ) : NasSignalingEvent()
}

// ==================== Supporting Enums ====================

enum class CipherAlgorithm(val displayName: String, val isNullCipher: Boolean) {
    // LTE (EEA)
    EEA0("EEA0 (Null)", true),
    EEA1("EEA1 (SNOW 3G)", false),
    EEA2("EEA2 (AES)", false),
    EEA3("EEA3 (ZUC)", false),
    // 5G NR (NEA)
    NEA0("NEA0 (Null)", true),
    NEA1("NEA1 (SNOW 3G)", false),
    NEA2("NEA2 (AES)", false),
    NEA3("NEA3 (ZUC)", false),
    // GSM (A5)
    A5_0("A5/0 (Null)", true),
    A5_1("A5/1", false),
    A5_2("A5/2 (Weak)", false), // Deprecated, easily broken
    A5_3("A5/3 (KASUMI)", false),
    A5_4("A5/4 (KASUMI-256)", false),
    UNKNOWN("Unknown", false);

    companion object {
        fun fromLteId(id: Int): CipherAlgorithm = when (id) {
            0 -> EEA0; 1 -> EEA1; 2 -> EEA2; 3 -> EEA3; else -> UNKNOWN
        }
        fun fromNrId(id: Int): CipherAlgorithm = when (id) {
            0 -> NEA0; 1 -> NEA1; 2 -> NEA2; 3 -> NEA3; else -> UNKNOWN
        }
        fun fromGsmId(id: Int): CipherAlgorithm = when (id) {
            0 -> A5_0; 1 -> A5_1; 2 -> A5_2; 3 -> A5_3; 4 -> A5_4; else -> UNKNOWN
        }
    }
}

enum class IntegrityAlgorithm(val displayName: String) {
    EIA0("EIA0 (Null)"),
    EIA1("EIA1 (SNOW 3G)"),
    EIA2("EIA2 (AES)"),
    EIA3("EIA3 (ZUC)"),
    NIA0("NIA0 (Null)"),
    NIA1("NIA1 (SNOW 3G)"),
    NIA2("NIA2 (AES)"),
    NIA3("NIA3 (ZUC)"),
    UNKNOWN("Unknown");

    companion object {
        fun fromLteId(id: Int): IntegrityAlgorithm = when (id) {
            0 -> EIA0; 1 -> EIA1; 2 -> EIA2; 3 -> EIA3; else -> UNKNOWN
        }
        fun fromNrId(id: Int): IntegrityAlgorithm = when (id) {
            0 -> NIA0; 1 -> NIA1; 2 -> NIA2; 3 -> NIA3; else -> UNKNOWN
        }
    }
}

enum class IdentityType(val displayName: String) {
    IMSI("IMSI"),
    IMEI("IMEI"),
    IMEISV("IMEISV"),
    TMSI("TMSI/P-TMSI"),
    SUCI("SUCI"),
    UNKNOWN("Unknown");

    companion object {
        fun fromByte(b: Byte): IdentityType? = when (b.toInt() and 0x07) {
            1 -> IMSI
            2 -> IMEI
            3 -> IMEISV
            4 -> TMSI
            7 -> SUCI
            else -> UNKNOWN
        }
    }
}

enum class AuthType(val displayName: String) {
    GSM("GSM/2G"),
    LTE("LTE/4G"),
    NR_5G("5G NR")
}
