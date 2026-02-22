package com.flockyou.shannon

import android.util.Log
import com.flockyou.config.OemFeatureFlags
import java.io.File

/**
 * Detects whether the device has a Samsung Shannon modem with an accessible
 * diagnostic interface at /dev/umts_dm0.
 *
 * Shannon modems are used in Google Pixel 6-9 (Tensor SoC with Samsung modem)
 * and Samsung Exynos devices. The diagnostic interface outputs SDM (Samsung
 * Diagnostic Monitor) framed messages containing raw NAS/RRC signaling.
 *
 * Access requires either root or an OEM SELinux policy granting platform_app
 * read access to the character device.
 */
object ShannonCapabilityDetector {

    private const val TAG = "ShannonCapability"
    private const val DIAG_DEVICE_PATH = "/dev/umts_dm0"

    /** Known Shannon modem chipname values from ro.hardware.chipname */
    private val SHANNON_CHIPNAMES = setOf(
        "s5123ap",  // Pixel 6/6 Pro (Tensor G1)
        "s5300ap",  // Pixel 7/7 Pro (Tensor G2)
        "s5400",    // Pixel 8/8 Pro (Tensor G3)
        "s6000"     // Pixel 9/9 Pro (Tensor G4)
    )

    /** Known platform values indicating Exynos SoC with Shannon modem */
    private val EXYNOS_PLATFORMS = setOf(
        "exynos",
        "exynos5",
        "exynos7",
        "exynos9",
        "exynos990",
        "exynos2100",
        "exynos2200"
    )

    /** Known SoC model values indicating Tensor (Samsung-derived) */
    private val TENSOR_SOC_MODELS = setOf(
        "Tensor",
        "Tensor G2",
        "Tensor G3",
        "Tensor G4"
    )

    /**
     * Result of Shannon modem capability detection.
     */
    enum class ShannonStatus(val displayName: String) {
        /** Feature flag is disabled in this build */
        FEATURE_DISABLED("Feature disabled"),
        /** No Shannon modem detected on this device */
        NO_SHANNON_MODEM("No Shannon modem"),
        /** Shannon modem present but /dev/umts_dm0 does not exist */
        NO_DEVICE_NODE("Device node missing"),
        /** Device node exists but not readable (SELinux denial) */
        ACCESS_DENIED("Access denied (SELinux)"),
        /** Shannon diagnostic interface is available and readable */
        AVAILABLE("Available")
    }

    /**
     * Detect Shannon modem diagnostic capability.
     * Checks are ordered from cheapest to most expensive.
     */
    fun detect(): ShannonStatus {
        // 1. Feature flag check (compile-time, essentially free)
        if (!OemFeatureFlags.SHANNON_DIAG_ENABLED) {
            Log.d(TAG, "Shannon diagnostics feature flag disabled")
            return ShannonStatus.FEATURE_DISABLED
        }

        // 2. Shannon modem presence check via system properties
        if (!hasShannonModem()) {
            Log.d(TAG, "No Shannon modem detected")
            return ShannonStatus.NO_SHANNON_MODEM
        }

        // 3. Device node existence check
        val deviceNode = File(DIAG_DEVICE_PATH)
        if (!deviceNode.exists()) {
            Log.d(TAG, "Shannon modem present but $DIAG_DEVICE_PATH not found")
            return ShannonStatus.NO_DEVICE_NODE
        }

        // 4. Readability check (will fail if SELinux denies access)
        if (!deviceNode.canRead()) {
            Log.d(TAG, "Shannon diagnostic node exists but not readable (SELinux?)")
            return ShannonStatus.ACCESS_DENIED
        }

        Log.i(TAG, "Shannon diagnostic interface available at $DIAG_DEVICE_PATH")
        return ShannonStatus.AVAILABLE
    }

    /**
     * Check if this device has a Samsung Shannon modem by inspecting system properties.
     */
    private fun hasShannonModem(): Boolean {
        // Check ro.hardware.chipname (Pixel Tensor devices)
        val chipname = getSystemProperty("ro.hardware.chipname")
        if (chipname != null && chipname.lowercase() in SHANNON_CHIPNAMES) {
            Log.d(TAG, "Shannon modem detected via chipname: $chipname")
            return true
        }

        // Check ro.board.platform (Samsung Exynos devices)
        val platform = getSystemProperty("ro.board.platform")
        if (platform != null && EXYNOS_PLATFORMS.any { platform.lowercase().startsWith(it) }) {
            Log.d(TAG, "Shannon modem detected via platform: $platform")
            return true
        }

        // Check ro.soc.model (Tensor SoC branding)
        val socModel = getSystemProperty("ro.soc.model")
        if (socModel != null && TENSOR_SOC_MODELS.any { socModel.contains(it, ignoreCase = true) }) {
            Log.d(TAG, "Shannon modem detected via SoC model: $socModel")
            return true
        }

        return false
    }

    /**
     * Read an Android system property via reflection.
     * Uses android.os.SystemProperties which is a hidden API.
     */
    @Suppress("PrivateApi")
    private fun getSystemProperty(key: String): String? {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            val value = method.invoke(null, key) as? String
            if (value.isNullOrBlank()) null else value
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read system property $key: ${e.message}")
            null
        }
    }
}
