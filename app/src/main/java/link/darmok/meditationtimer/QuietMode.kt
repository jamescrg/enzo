package link.darmok.meditationtimer

import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Turns Do Not Disturb on for the length of a session and puts it back afterwards.
 *
 * Uses the "alarms only" level: calls and notifications are silenced, but media sound (the
 * chime) and alarm vibrations (the vibrate options) still come through.
 */
object QuietMode {
    private const val OWNED = "dndOwned"

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)
    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Whether the user has granted this app Do Not Disturb access in system settings. */
    fun hasAccess(context: Context): Boolean = manager(context).isNotificationPolicyAccessGranted

    fun begin(context: Context) {
        val nm = manager(context)
        if (!nm.isNotificationPolicyAccessGranted || prefs(context).getBoolean(OWNED, false)) return
        // If the user already has Do Not Disturb on, leave their setting alone.
        if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) return
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
        // Remembered on disk so a session cut short by the process dying is undone on next launch.
        prefs(context).edit().putBoolean(OWNED, true).apply()
    }

    /** Undoes [begin]; does nothing if we didn't turn Do Not Disturb on. */
    fun end(context: Context) {
        if (!prefs(context).getBoolean(OWNED, false)) return
        prefs(context).edit().putBoolean(OWNED, false).apply()
        val nm = manager(context)
        if (!nm.isNotificationPolicyAccessGranted) return
        // From Android 15 our request is a separate mode owned by this app, so switching it off
        // can't disturb anything else. Before that it is the global setting, so only restore it
        // if the user hasn't changed it in the meantime.
        if (Build.VERSION.SDK_INT >= 35 ||
            nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALARMS
        ) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
    }
}
