package com.flockyou.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.flockyou.BuildConfig

/**
 * Receives boot completed broadcasts and restarts the scanning service
 * if it was running before the device was rebooted.
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
        private const val PREFS_NAME = "flockyou_prefs"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_AUTO_START_ON_BOOT = "auto_start_on_boot"
        
        fun setServiceEnabled(context: Context, enabled: Boolean) {
            getPrefs(context).edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
        }
        
        fun isServiceEnabled(context: Context): Boolean {
            // OEM builds default to service always enabled for continuous background scanning
            val defaultEnabled = BuildConfig.IS_OEM_BUILD
            return getPrefs(context).getBoolean(KEY_SERVICE_ENABLED, defaultEnabled)
        }

        fun setAutoStartOnBoot(context: Context, enabled: Boolean) {
            getPrefs(context).edit().putBoolean(KEY_AUTO_START_ON_BOOT, enabled).apply()
        }

        fun isAutoStartOnBoot(context: Context): Boolean {
            // OEM builds always auto-start on boot for continuous background scanning
            return getPrefs(context).getBoolean(KEY_AUTO_START_ON_BOOT, true)
        }

        /**
         * Initialize OEM defaults on first run.
         * For OEM builds, this ensures the service is enabled and will auto-start.
         */
        fun initializeOemDefaults(context: Context) {
            if (!BuildConfig.IS_OEM_BUILD) return

            val prefs = getPrefs(context)
            val initialized = prefs.getBoolean("oem_defaults_initialized", false)
            if (!initialized) {
                prefs.edit()
                    .putBoolean(KEY_SERVICE_ENABLED, true)
                    .putBoolean(KEY_AUTO_START_ON_BOOT, true)
                    .putBoolean("oem_defaults_initialized", true)
                    .apply()
                Log.i(TAG, "OEM defaults initialized: service enabled, auto-start on boot")
            }
        }
        
        private fun getPrefs(context: Context): SharedPreferences {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Received broadcast: $action")
        
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                handleBootCompleted(context)
            }
        }
    }
    
    private fun handleBootCompleted(context: Context) {
        Log.d(TAG, "Boot completed, checking if service should start")
        
        // Check if auto-start is enabled and service was running
        if (isAutoStartOnBoot(context) && isServiceEnabled(context)) {
            Log.d(TAG, "Starting scanning service after boot")
            startScanningService(context)
        } else {
            Log.d(TAG, "Service auto-start disabled or service was not running")
        }
    }
    
    private fun startScanningService(context: Context) {
        try {
            val serviceIntent = Intent(context, ScanningService::class.java).apply {
                action = "com.flockyou.START_SCANNING"
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            Log.d(TAG, "Service start requested successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service after boot", e)
        }
    }
}
