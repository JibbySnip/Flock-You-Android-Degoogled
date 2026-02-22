package com.flockyou.shannon.sdm

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Streaming parser for Samsung Diagnostic Monitor (SDM) wire format.
 *
 * SDM framing (derived from SCAT's sdmcmd.py):
 * - Start marker: 0x7F
 * - End marker: 0x7E
 * - Byte stuffing: 0x7D 0x5E -> 0x7E, 0x7D 0x5D -> 0x7D, 0x7D 0x5F -> 0x7F
 * - Header after unstuffing: [Length:2LE][Type:1][Subtype:1][Direction:1][Timestamp:8LE]
 * - Direction: 0 = UL (device->network), 1 = DL (network->device)
 *
 * The parser handles partial frames across multiple feed() calls and
 * recovers from corrupt frames by resyncing to the next start marker.
 */
class SdmFrameParser {

    companion object {
        const val START_MARKER: Byte = 0x7F
        const val END_MARKER: Byte = 0x7E
        const val ESCAPE_BYTE: Byte = 0x7D

        // SDM message types (from SCAT)
        const val TYPE_GSM_L1: Byte = 0x10
        const val TYPE_GSM_RR: Byte = 0x12
        const val TYPE_GSM_MM: Byte = 0x13
        const val TYPE_LTE_NAS_EMM: Byte = 0x50
        const val TYPE_LTE_NAS_ESM: Byte = 0x51
        const val TYPE_LTE_RRC: Byte = 0x60
        const val TYPE_NR_NAS: Byte = 0x70
        const val TYPE_NR_RRC: Byte = 0x80.toByte()

        // Directions
        const val DIRECTION_UL: Byte = 0x00 // Device -> Network (uplink)
        const val DIRECTION_DL: Byte = 0x01 // Network -> Device (downlink)

        private const val MIN_HEADER_SIZE = 13 // 2 (length) + 1 (type) + 1 (subtype) + 1 (direction) + 8 (timestamp)
        private const val MAX_FRAME_SIZE = 65536
    }

    private val buffer = ByteArray(MAX_FRAME_SIZE * 2)
    private var bufferPos = 0
    private var inFrame = false
    private var frameBuffer = ByteArray(MAX_FRAME_SIZE)
    private var framePos = 0

    /** Statistics for monitoring */
    var framesDecoded: Long = 0L
        private set
    var bytesProcessed: Long = 0L
        private set
    var framesCorrupt: Long = 0L
        private set

    /**
     * Feed raw bytes from the diagnostic device into the parser.
     * Returns a list of complete, parsed SDM frames.
     */
    fun feed(data: ByteArray, offset: Int = 0, length: Int = data.size): List<SdmFrame> {
        val frames = mutableListOf<SdmFrame>()
        bytesProcessed += length

        for (i in offset until offset + length) {
            val b = data[i]
            when {
                b == START_MARKER && !inFrame -> {
                    // Start of a new frame
                    inFrame = true
                    framePos = 0
                }
                b == START_MARKER && inFrame -> {
                    // New start marker while already in frame -- previous frame was corrupt
                    // Restart with new frame
                    framesCorrupt++
                    framePos = 0
                }
                b == END_MARKER && inFrame -> {
                    // End of frame -- try to parse
                    inFrame = false
                    val unstuffed = unstuff(frameBuffer, framePos)
                    if (unstuffed != null && unstuffed.size >= MIN_HEADER_SIZE) {
                        parseFrame(unstuffed)?.let {
                            frames.add(it)
                            framesDecoded++
                        }
                    } else if (framePos > 0) {
                        framesCorrupt++
                    }
                    framePos = 0
                }
                inFrame -> {
                    // Accumulate frame data
                    if (framePos < MAX_FRAME_SIZE) {
                        frameBuffer[framePos++] = b
                    } else {
                        // Frame too large, discard
                        inFrame = false
                        framePos = 0
                        framesCorrupt++
                    }
                }
                // else: byte outside of frame, ignore
            }
        }

        return frames
    }

    /**
     * Reset parser state. Call when reconnecting to the device.
     */
    fun reset() {
        inFrame = false
        framePos = 0
        bufferPos = 0
    }

    /**
     * Perform byte unstuffing on the raw frame data.
     * Returns null if the data is malformed (odd escape sequence).
     */
    internal fun unstuff(data: ByteArray, length: Int): ByteArray? {
        val result = ByteArray(length) // max possible size
        var outPos = 0
        var i = 0

        while (i < length) {
            if (data[i] == ESCAPE_BYTE) {
                i++
                if (i >= length) return null // Truncated escape sequence
                when (data[i]) {
                    0x5E.toByte() -> result[outPos++] = END_MARKER
                    0x5D.toByte() -> result[outPos++] = ESCAPE_BYTE
                    0x5F.toByte() -> result[outPos++] = START_MARKER
                    else -> return null // Invalid escape sequence
                }
            } else {
                result[outPos++] = data[i]
            }
            i++
        }

        return result.copyOf(outPos)
    }

    /**
     * Parse an unstuffed frame into an SdmFrame.
     */
    private fun parseFrame(data: ByteArray): SdmFrame? {
        if (data.size < MIN_HEADER_SIZE) return null

        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        val length = buf.short.toInt() and 0xFFFF
        val type = buf.get()
        val subtype = buf.get()
        val direction = buf.get()
        val timestamp = buf.long

        // Validate direction
        if (direction != DIRECTION_UL && direction != DIRECTION_DL) {
            return null
        }

        // Extract payload (everything after header)
        val payloadSize = data.size - MIN_HEADER_SIZE
        val payload = if (payloadSize > 0) {
            data.copyOfRange(MIN_HEADER_SIZE, data.size)
        } else {
            ByteArray(0)
        }

        return SdmFrame(
            type = type,
            subtype = subtype,
            direction = direction,
            timestamp = timestamp,
            payload = payload,
            rawLength = length
        )
    }
}

/**
 * A parsed SDM frame from the Shannon modem diagnostic interface.
 */
data class SdmFrame(
    /** SDM message type (e.g., TYPE_LTE_NAS_EMM, TYPE_NR_NAS) */
    val type: Byte,
    /** SDM message subtype */
    val subtype: Byte,
    /** Direction: 0 = UL (device->network), 1 = DL (network->device) */
    val direction: Byte,
    /** Modem timestamp */
    val timestamp: Long,
    /** Message payload after header */
    val payload: ByteArray,
    /** Raw length field from header */
    val rawLength: Int
) {
    val isDownlink: Boolean get() = direction == SdmFrameParser.DIRECTION_DL
    val isUplink: Boolean get() = direction == SdmFrameParser.DIRECTION_UL

    /** Check if this is a NAS signaling frame (LTE EMM, LTE ESM, 5G NAS, or GSM MM) */
    val isNasFrame: Boolean get() = type == SdmFrameParser.TYPE_LTE_NAS_EMM ||
            type == SdmFrameParser.TYPE_LTE_NAS_ESM ||
            type == SdmFrameParser.TYPE_NR_NAS ||
            type == SdmFrameParser.TYPE_GSM_MM

    /** Check if this is an RRC signaling frame */
    val isRrcFrame: Boolean get() = type == SdmFrameParser.TYPE_LTE_RRC ||
            type == SdmFrameParser.TYPE_NR_RRC ||
            type == SdmFrameParser.TYPE_GSM_RR

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SdmFrame) return false
        return type == other.type && subtype == other.subtype &&
                direction == other.direction && timestamp == other.timestamp &&
                payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = type.toInt()
        result = 31 * result + subtype
        result = 31 * result + direction
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
