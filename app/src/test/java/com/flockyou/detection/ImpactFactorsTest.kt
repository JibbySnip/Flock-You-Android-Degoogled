package com.flockyou.detection

import com.flockyou.data.model.DeviceType
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImpactFactorsTest {

    @Test
    fun `every DeviceType has an explicit impact factor`() {
        val covered = ImpactFactors.coveredTypes()
        val missing = DeviceType.entries.filter { it !in covered }

        assertTrue(
            "DeviceType values missing from ImpactFactors: ${missing.joinToString { it.name }}. " +
                "Add them to ImpactFactors.kt to prevent silent defaults.",
            missing.isEmpty()
        )
    }

    @Test
    fun `all impact factors are within valid range`() {
        for (type in DeviceType.entries) {
            val factor = ImpactFactors.get(type)
            assertTrue(
                "${type.name} has impact factor $factor outside valid range [0.1, 3.0]",
                factor in 0.1..3.0
            )
        }
    }

    @Test
    fun `ThreatScoring delegates to ImpactFactors`() {
        for (type in DeviceType.entries) {
            val fromImpactFactors = ImpactFactors.get(type)
            val fromThreatScoring = ThreatScoring.getImpactFactor(type)
            assertTrue(
                "${type.name}: ThreatScoring(${fromThreatScoring}) != ImpactFactors(${fromImpactFactors})",
                fromThreatScoring == fromImpactFactors
            )
        }
    }

    @Test
    fun `hacking tools have appropriate impact factors`() {
        val hackingTools = mapOf(
            DeviceType.FLIPPER_ZERO to 1.5,
            DeviceType.FLIPPER_ZERO_SPAM to 1.9,
            DeviceType.HACKRF_SDR to 1.6,
            DeviceType.PROXMARK to 1.7,
            DeviceType.USB_RUBBER_DUCKY to 1.8,
            DeviceType.LAN_TURTLE to 1.7,
            DeviceType.BASH_BUNNY to 1.8,
            DeviceType.KEYCROC to 1.8,
            DeviceType.SHARK_JACK to 1.7,
            DeviceType.SCREEN_CRAB to 1.6,
            DeviceType.GENERIC_HACKING_TOOL to 1.5
        )

        for ((type, expected) in hackingTools) {
            val actual = ImpactFactors.get(type)
            assertTrue(
                "${type.name}: expected $expected but got $actual",
                actual == expected
            )
        }
    }

    @Test
    fun `maximum impact devices score 2_0`() {
        val maxImpact = listOf(
            DeviceType.STINGRAY_IMSI,
            DeviceType.CELLEBRITE_FORENSICS,
            DeviceType.GRAYKEY_DEVICE,
            DeviceType.MAN_IN_MIDDLE
        )
        for (type in maxImpact) {
            assertTrue(
                "${type.name} should have impact factor 2.0",
                ImpactFactors.get(type) == 2.0
            )
        }
    }
}
