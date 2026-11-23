package app.focuslite

import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import app.focuslite.engine.accessibility.FocusAccessibilityService
import app.focuslite.usage.UsageStatsSource

/** Reads the permissions and settings FocusLite depends on. */
data class SystemStatus(
    val accessibilityEnabled: Boolean,
    val serviceRunning: Boolean,
    val batteryUnrestricted: Boolean,
    val usageAccess: Boolean,
) {
    val blocking get() = accessibilityEnabled && serviceRunning

    companion object {
        fun read(context: Context): SystemStatus = SystemStatus(
            accessibilityEnabled = isAccessibilityEnabled(context),
            serviceRunning = FocusAccessibilityService.instance != null,
            batteryUnrestricted = context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName),
            usageAccess = UsageStatsSource(context).hasAccess(),
        )

        private fun isAccessibilityEnabled(context: Context): Boolean {
            val ours = ComponentName(context, FocusAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
        }
    }
}
