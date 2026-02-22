package com.flockyou.shannon.sdm

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SdmFrameParserTest {

    private lateinit var parser: SdmFrameParser

    @Before
    fun setup() {
        parser = SdmFrameParser()
    }

    // ==================== Byte Unstuffing ====================

    @Test
    fun `unstuff passes through normal bytes`() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val result = parser.unstuff(data, data.size)
        assertNotNull(result)
        assertArrayEquals(data, result)
    }

    @Test
    fun `unstuff converts 7D 5E to 7E (end marker)`() {
        val data = byteArrayOf(0x01, 0x7D, 0x5E, 0x02)
        val result = parser.unstuff(data, data.size)
        assertNotNull(result)
        assertArrayEquals(byteArrayOf(0x01, 0x7E, 0x02), result)
    }

    @Test
    fun `unstuff converts 7D 5D to 7D (escape byte)`() {
        val data = byteArrayOf(0x01, 0x7D, 0x5D, 0x02)
        val result = parser.unstuff(data, data.size)
        assertNotNull(result)
        assertArrayEquals(byteArrayOf(0x01, 0x7D, 0x02), result)
    }

    @Test
    fun `unstuff converts 7D 5F to 7F (start marker)`() {
        val data = byteArrayOf(0x01, 0x7D, 0x5F, 0x02)
        val result = parser.unstuff(data, data.size)
        assertNotNull(result)
        assertArrayEquals(byteArrayOf(0x01, 0x7F, 0x02), result)
    }

    @Test
    fun `unstuff returns null for truncated escape`() {
        val data = byteArrayOf(0x01, 0x7D)
        val result = parser.unstuff(data, data.size)
        assertNull(result)
    }

    @Test
    fun `unstuff returns null for invalid escape sequence`() {
        val data = byteArrayOf(0x01, 0x7D, 0x00, 0x02)
        val result = parser.unstuff(data, data.size)
        assertNull(result)
    }

    @Test
    fun `unstuff handles multiple escapes`() {
        val data = byteArrayOf(0x7D, 0x5E, 0x7D, 0x5D, 0x7D, 0x5F)
        val result = parser.unstuff(data, data.size)
        assertNotNull(result)
        assertArrayEquals(byteArrayOf(0x7E, 0x7D, 0x7F), result)
    }

    // ==================== Frame Parsing ====================

    @Test
    fun `feed parses complete frame`() {
        val frame = buildSdmFrame(
            type = SdmFrameParser.TYPE_LTE_NAS_EMM,
            subtype = 0x00,
            direction = SdmFrameParser.DIRECTION_DL,
            timestamp = 12345L,
            payload = byteArrayOf(0x55, 0x01) // Identity Request, IMSI type
        )

        val frames = parser.feed(frame)
        assertEquals(1, frames.size)
        assertEquals(SdmFrameParser.TYPE_LTE_NAS_EMM, frames[0].type)
        assertEquals(SdmFrameParser.DIRECTION_DL, frames[0].direction)
        assertEquals(12345L, frames[0].timestamp)
        assertTrue(frames[0].isDownlink)
        assertTrue(frames[0].isNasFrame)
    }

    @Test
    fun `feed handles multiple frames in one buffer`() {
        val frame1 = buildSdmFrame(
            type = SdmFrameParser.TYPE_LTE_NAS_EMM,
            subtype = 0x00,
            direction = SdmFrameParser.DIRECTION_DL,
            timestamp = 100L,
            payload = byteArrayOf(0x55, 0x01)
        )
        val frame2 = buildSdmFrame(
            type = SdmFrameParser.TYPE_NR_NAS,
            subtype = 0x00,
            direction = SdmFrameParser.DIRECTION_DL,
            timestamp = 200L,
            payload = byteArrayOf(0x5C, 0x01)
        )

        val combined = frame1 + frame2
        val frames = parser.feed(combined)
        assertEquals(2, frames.size)
        assertEquals(SdmFrameParser.TYPE_LTE_NAS_EMM, frames[0].type)
        assertEquals(SdmFrameParser.TYPE_NR_NAS, frames[1].type)
    }

    @Test
    fun `feed handles partial frames across calls`() {
        val full = buildSdmFrame(
            type = SdmFrameParser.TYPE_LTE_NAS_EMM,
            subtype = 0x00,
            direction = SdmFrameParser.DIRECTION_DL,
            timestamp = 100L,
            payload = byteArrayOf(0x55, 0x01)
        )

        // Split in the middle
        val half = full.size / 2
        val part1 = full.copyOfRange(0, half)
        val part2 = full.copyOfRange(half, full.size)

        val frames1 = parser.feed(part1)
        assertEquals(0, frames1.size) // No complete frame yet

        val frames2 = parser.feed(part2)
        assertEquals(1, frames2.size) // Now we have the complete frame
    }

    @Test
    fun `feed ignores bytes outside frames`() {
        val garbage = byteArrayOf(0x01, 0x02, 0x03) // No start marker
        val frames = parser.feed(garbage)
        assertEquals(0, frames.size)
    }

    @Test
    fun `feed recovers from corrupt frame on new start marker`() {
        // Start a frame but then start a new one before ending the first
        val corruptStart = byteArrayOf(0x7F, 0x01, 0x02) // Start without end
        val validFrame = buildSdmFrame(
            type = SdmFrameParser.TYPE_LTE_NAS_EMM,
            subtype = 0x00,
            direction = SdmFrameParser.DIRECTION_DL,
            timestamp = 100L,
            payload = byteArrayOf(0x55, 0x01)
        )

        val combined = corruptStart + validFrame
        val frames = parser.feed(combined)
        assertEquals(1, frames.size) // Should recover and parse the valid frame
        assertTrue(parser.framesCorrupt >= 1) // Should have counted the corrupt frame
    }

    @Test
    fun `feed tracks statistics`() {
        val frame = buildSdmFrame(
            type = SdmFrameParser.TYPE_LTE_NAS_EMM,
            subtype = 0x00,
            direction = SdmFrameParser.DIRECTION_DL,
            timestamp = 100L,
            payload = byteArrayOf(0x55, 0x01)
        )

        parser.feed(frame)
        assertEquals(1L, parser.framesDecoded)
        assertTrue(parser.bytesProcessed > 0)
    }

    @Test
    fun `reset clears parser state`() {
        // Feed a partial frame
        val partial = byteArrayOf(0x7F, 0x01, 0x02, 0x03)
        parser.feed(partial)

        parser.reset()

        // After reset, a new frame should parse correctly
        val frame = buildSdmFrame(
            type = SdmFrameParser.TYPE_LTE_NAS_EMM,
            subtype = 0x00,
            direction = SdmFrameParser.DIRECTION_DL,
            timestamp = 100L,
            payload = byteArrayOf(0x55, 0x01)
        )
        val frames = parser.feed(frame)
        assertEquals(1, frames.size)
    }

    @Test
    fun `frame isNasFrame returns true for NAS types`() {
        val nasTypes = listOf(
            SdmFrameParser.TYPE_LTE_NAS_EMM,
            SdmFrameParser.TYPE_LTE_NAS_ESM,
            SdmFrameParser.TYPE_NR_NAS,
            SdmFrameParser.TYPE_GSM_MM
        )

        for (type in nasTypes) {
            val frame = buildAndParse(type)
            assertTrue("$type should be NAS frame", frame.isNasFrame)
        }
    }

    @Test
    fun `frame isRrcFrame returns true for RRC types`() {
        val rrcTypes = listOf(
            SdmFrameParser.TYPE_LTE_RRC,
            SdmFrameParser.TYPE_NR_RRC,
            SdmFrameParser.TYPE_GSM_RR
        )

        for (type in rrcTypes) {
            val frame = buildAndParse(type)
            assertTrue("$type should be RRC frame", frame.isRrcFrame)
        }
    }

    // ==================== Helpers ====================

    /**
     * Build a raw SDM frame with proper framing (start/end markers, no stuffing needed
     * since we avoid marker bytes in the payload).
     */
    private fun buildSdmFrame(
        type: Byte,
        subtype: Byte,
        direction: Byte,
        timestamp: Long,
        payload: ByteArray
    ): ByteArray {
        val headerSize = 13 // 2 (length) + 1 (type) + 1 (subtype) + 1 (direction) + 8 (timestamp)
        val totalLength = headerSize + payload.size

        val header = ByteBuffer.allocate(headerSize).order(ByteOrder.LITTLE_ENDIAN)
        header.putShort(totalLength.toShort())
        header.put(type)
        header.put(subtype)
        header.put(direction)
        header.putLong(timestamp)

        val frameContent = header.array() + payload

        // Wrap with start and end markers
        return byteArrayOf(SdmFrameParser.START_MARKER) + frameContent + byteArrayOf(SdmFrameParser.END_MARKER)
    }

    private fun buildAndParse(type: Byte): SdmFrame {
        val frame = buildSdmFrame(
            type = type,
            subtype = 0x00,
            direction = SdmFrameParser.DIRECTION_DL,
            timestamp = 100L,
            payload = byteArrayOf(0x01)
        )
        val frames = SdmFrameParser().feed(frame)
        assertEquals(1, frames.size)
        return frames[0]
    }
}
