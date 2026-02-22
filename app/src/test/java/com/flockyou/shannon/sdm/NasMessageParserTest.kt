package com.flockyou.shannon.sdm

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NasMessageParserTest {

    private lateinit var parser: NasMessageParser

    @Before
    fun setup() {
        parser = NasMessageParser()
    }

    // ==================== Identity Request Tests ====================

    @Test
    fun `parse LTE Identity Request with IMSI type returns IdentityRequest`() {
        val payload = byteArrayOf(
            0x55, // LTE Identity Request message type
            0x01  // Identity type: IMSI (lower 3 bits = 001)
        )
        val frame = makeDlFrame(SdmFrameParser.TYPE_LTE_NAS_EMM, payload)

        val event = parser.parse(frame)
        assertNotNull(event)
        assertTrue(event is NasSignalingEvent.IdentityRequest)
        val idReq = event as NasSignalingEvent.IdentityRequest
        assertEquals(IdentityType.IMSI, idReq.identityType)
        assertEquals(AuthType.LTE, idReq.authType)
        assertTrue(idReq.isDownlink)
    }

    @Test
    fun `parse LTE Identity Request with TMSI type returns null (not suspicious)`() {
        val payload = byteArrayOf(
            0x55, // LTE Identity Request message type
            0x04  // Identity type: TMSI (lower 3 bits = 100)
        )
        val frame = makeDlFrame(SdmFrameParser.TYPE_LTE_NAS_EMM, payload)

        val event = parser.parse(frame)
        // TMSI is normal -- should not trigger an event
        assertNull(event)
    }

    @Test
    fun `parse GSM Identity Request with IMSI type returns IdentityRequest`() {
        val payload = byteArrayOf(
            0x18, // GSM Identity Request
            0x01  // IMSI
        )
        val frame = makeDlFrame(SdmFrameParser.TYPE_GSM_MM, payload)

        val event = parser.parse(frame)
        assertNotNull(event)
        assertTrue(event is NasSignalingEvent.IdentityRequest)
        assertEquals(AuthType.GSM, (event as NasSignalingEvent.IdentityRequest).authType)
    }

    @Test
    fun `parse 5G NR Identity Request with IMSI type returns IdentityRequest`() {
        val payload = byteArrayOf(
            0x5C, // NR Identity Request
            0x01  // IMSI
        )
        val frame = makeDlFrame(SdmFrameParser.TYPE_NR_NAS, payload)

        val event = parser.parse(frame)
        assertNotNull(event)
        assertTrue(event is NasSignalingEvent.IdentityRequest)
        assertEquals(AuthType.NR_5G, (event as NasSignalingEvent.IdentityRequest).authType)
    }

    // ==================== Security Mode Command Tests ====================

    @Test
    fun `parse LTE Security Mode Command with null cipher returns SecurityModeCommand`() {
        val payload = byteArrayOf(
            0x5D, // LTE Security Mode Command
            0x02  // Algorithms: cipher = EEA0 (0 << 4), integrity = EIA2 (2)
            // Actually: upper nibble = 0 (EEA0), lower nibble = 2 (EIA2)
        )
        val frame = makeDlFrame(SdmFrameParser.TYPE_LTE_NAS_EMM, payload)

        val event = parser.parse(frame)
        assertNotNull(event)
        assertTrue(event is NasSignalingEvent.SecurityModeCommand)
        val smc = event as NasSignalingEvent.SecurityModeCommand
        assertEquals(CipherAlgorithm.EEA0, smc.cipherAlgorithm)
        assertTrue(smc.cipherAlgorithm.isNullCipher)
        assertEquals(AuthType.LTE, smc.authType)
    }

    @Test
    fun `parse LTE Security Mode Command with EEA2 returns SecurityModeCommand`() {
        val payload = byteArrayOf(
            0x5D, // LTE Security Mode Command
            0x22  // Algorithms: cipher = EEA2 (2 << 4 = 0x20), integrity = EIA2 (2)
        )
        val frame = makeDlFrame(SdmFrameParser.TYPE_LTE_NAS_EMM, payload)

        val event = parser.parse(frame)
        assertNotNull(event)
        assertTrue(event is NasSignalingEvent.SecurityModeCommand)
        val smc = event as NasSignalingEvent.SecurityModeCommand
        assertEquals(CipherAlgorithm.EEA2, smc.cipherAlgorithm)
        assertFalse(smc.cipherAlgorithm.isNullCipher)
    }

    @Test
    fun `parse GSM Ciphering Mode with A5_0 returns SecurityModeCommand`() {
        val payload = byteArrayOf(
            0x35, // GSM Ciphering Mode Command
            0x00  // A5/0 (bits 1-3 = 000)
        )
        val frame = makeDlFrame(SdmFrameParser.TYPE_GSM_MM, payload)

        val event = parser.parse(frame)
        assertNotNull(event)
        assertTrue(event is NasSignalingEvent.SecurityModeCommand)
        val smc = event as NasSignalingEvent.SecurityModeCommand
        assertEquals(CipherAlgorithm.A5_0, smc.cipherAlgorithm)
        assertTrue(smc.cipherAlgorithm.isNullCipher)
    }

    @Test
    fun `parse 5G NR Security Mode Command with NEA0 returns SecurityModeCommand`() {
        val payload = byteArrayOf(
            0x5D, // NR Security Mode Command
            0x01  // Algorithms: cipher = NEA0 (0 << 4), integrity = NIA1 (1)
        )
        val frame = makeDlFrame(SdmFrameParser.TYPE_NR_NAS, payload)

        val event = parser.parse(frame)
        assertNotNull(event)
        assertTrue(event is NasSignalingEvent.SecurityModeCommand)
        val smc = event as NasSignalingEvent.SecurityModeCommand
        assertEquals(CipherAlgorithm.NEA0, smc.cipherAlgorithm)
        assertTrue(smc.cipherAlgorithm.isNullCipher)
    }

    // ==================== Registration Reject Tests ====================

    @Test
    fun `parse LTE Attach Reject returns RegistrationReject`() {
        val payload = byteArrayOf(
            0x44, // LTE Attach Reject
            0x07  // Cause: EPS services not allowed
        )
        val frame = makeDlFrame(SdmFrameParser.TYPE_LTE_NAS_EMM, payload)

        val event = parser.parse(frame)
        assertNotNull(event)
        assertTrue(event is NasSignalingEvent.RegistrationReject)
        val reject = event as NasSignalingEvent.RegistrationReject
        assertEquals("ATTACH_REJECT", reject.rejectType)
        assertEquals(7, reject.causeCode)
    }

    @Test
    fun `parse LTE TAU Reject returns RegistrationReject`() {
        val payload = byteArrayOf(
            0x4B, // LTE TAU Reject
            0x0A  // Cause value 10
        )
        val frame = makeDlFrame(SdmFrameParser.TYPE_LTE_NAS_EMM, payload)

        val event = parser.parse(frame)
        assertNotNull(event)
        assertTrue(event is NasSignalingEvent.RegistrationReject)
        assertEquals("TAU_REJECT", (event as NasSignalingEvent.RegistrationReject).rejectType)
    }

    // ==================== Authentication Request Tests ====================

    @Test
    fun `parse LTE Auth Request with all-zero RAND returns AuthenticationRequest`() {
        // Build a payload with all-zero RAND
        val payload = ByteArray(2 + 16 + 16) // msg_type + KSI + RAND + AUTN
        payload[0] = 0x52 // LTE Auth Request
        payload[1] = 0x00 // KSI
        // RAND (16 bytes) = all zeros
        // AUTN (16 bytes) = non-zero
        for (i in 18 until 34) payload[i] = (i % 256).toByte()

        val frame = makeDlFrame(SdmFrameParser.TYPE_LTE_NAS_EMM, payload)

        val event = parser.parse(frame)
        assertNotNull(event)
        assertTrue(event is NasSignalingEvent.AuthenticationRequest)
        val auth = event as NasSignalingEvent.AuthenticationRequest
        assertFalse(auth.isRandValid) // All-zero RAND should be flagged
        assertEquals(AuthType.LTE, auth.authType)
    }

    @Test
    fun `parse LTE Auth Request with valid params returns null`() {
        // Build a payload with valid (non-zero, non-repeating) RAND and AUTN
        val payload = ByteArray(2 + 16 + 16)
        payload[0] = 0x52 // LTE Auth Request
        payload[1] = 0x00 // KSI
        // Fill RAND with varied data
        for (i in 2 until 18) payload[i] = ((i * 17 + 3) % 256).toByte()
        // Fill AUTN with varied data
        for (i in 18 until 34) payload[i] = ((i * 31 + 7) % 256).toByte()

        val frame = makeDlFrame(SdmFrameParser.TYPE_LTE_NAS_EMM, payload)

        val event = parser.parse(frame)
        // Valid auth params should not trigger an event
        assertNull(event)
    }

    // ==================== Null/Edge Cases ====================

    @Test
    fun `parse empty payload returns null`() {
        val frame = SdmFrame(
            type = SdmFrameParser.TYPE_LTE_NAS_EMM,
            subtype = 0x00,
            direction = SdmFrameParser.DIRECTION_DL,
            timestamp = 100L,
            payload = ByteArray(0),
            rawLength = 13
        )
        assertNull(parser.parse(frame))
    }

    @Test
    fun `parse unknown message type returns null`() {
        val payload = byteArrayOf(0x99.toByte(), 0x01) // Unknown message type
        val frame = makeDlFrame(SdmFrameParser.TYPE_LTE_NAS_EMM, payload)

        val event = parser.parse(frame)
        assertNull(event)
    }

    @Test
    fun `parse unknown SDM frame type returns null`() {
        val payload = byteArrayOf(0x55, 0x01)
        val frame = SdmFrame(
            type = 0x99.toByte(), // Unknown SDM type
            subtype = 0x00,
            direction = SdmFrameParser.DIRECTION_DL,
            timestamp = 100L,
            payload = payload,
            rawLength = 15
        )
        assertNull(parser.parse(frame))
    }

    // ==================== Cipher Algorithm Enum Tests ====================

    @Test
    fun `CipherAlgorithm null cipher detection`() {
        assertTrue(CipherAlgorithm.EEA0.isNullCipher)
        assertTrue(CipherAlgorithm.NEA0.isNullCipher)
        assertTrue(CipherAlgorithm.A5_0.isNullCipher)
        assertFalse(CipherAlgorithm.EEA1.isNullCipher)
        assertFalse(CipherAlgorithm.EEA2.isNullCipher)
        assertFalse(CipherAlgorithm.A5_1.isNullCipher)
        assertFalse(CipherAlgorithm.A5_3.isNullCipher)
    }

    @Test
    fun `CipherAlgorithm fromLteId mapping`() {
        assertEquals(CipherAlgorithm.EEA0, CipherAlgorithm.fromLteId(0))
        assertEquals(CipherAlgorithm.EEA1, CipherAlgorithm.fromLteId(1))
        assertEquals(CipherAlgorithm.EEA2, CipherAlgorithm.fromLteId(2))
        assertEquals(CipherAlgorithm.EEA3, CipherAlgorithm.fromLteId(3))
        assertEquals(CipherAlgorithm.UNKNOWN, CipherAlgorithm.fromLteId(7))
    }

    @Test
    fun `CipherAlgorithm fromGsmId mapping`() {
        assertEquals(CipherAlgorithm.A5_0, CipherAlgorithm.fromGsmId(0))
        assertEquals(CipherAlgorithm.A5_1, CipherAlgorithm.fromGsmId(1))
        assertEquals(CipherAlgorithm.A5_3, CipherAlgorithm.fromGsmId(3))
    }

    @Test
    fun `IdentityType fromByte mapping`() {
        assertEquals(IdentityType.IMSI, IdentityType.fromByte(1))
        assertEquals(IdentityType.IMEI, IdentityType.fromByte(2))
        assertEquals(IdentityType.TMSI, IdentityType.fromByte(4))
        assertEquals(IdentityType.SUCI, IdentityType.fromByte(7))
    }

    // ==================== Helpers ====================

    private fun makeDlFrame(type: Byte, payload: ByteArray): SdmFrame {
        return SdmFrame(
            type = type,
            subtype = 0x00,
            direction = SdmFrameParser.DIRECTION_DL,
            timestamp = System.currentTimeMillis(),
            payload = payload,
            rawLength = 13 + payload.size
        )
    }
}
