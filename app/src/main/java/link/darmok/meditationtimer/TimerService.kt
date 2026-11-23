package link.darmok.meditationtimer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that exists while any timer is running or paused, so Android doesn't kill
 * the process and lose the timers. Holds a CPU wake lock only while something is counting down,
 * so the chime fires on time with the screen off.
 */
class TimerService : Service() {
    private val scope = MainScope()
    private var watcher: Job? = null
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var notifications: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "Running timers", NotificationManager.IMPORTANCE_LOW)
        )
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MeditationTimer:countdown")
            .apply { setReferenceCounted(false) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(TimerEngine.timers.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        if (watcher == null) watcher = scope.launch { TimerEngine.timers.collect(::onTimersChanged) }
        return START_NOT_STICKY
    }

    private fun onTimersChanged(timers: List<TimerState>) {
        if (timers.all { it.status == Status.IDLE }) {
            stopSelf()
            return
        }
        if (timers.any { it.status == Status.RUNNING }) {
            @Suppress("WakelockTimeout") // held exactly as long as a timer is counting down
            wakeLock.acquire()
        } else if (wakeLock.isHeld) {
            wakeLock.release()
        }
        notifications.notify(NOTIFICATION_ID, buildNotification(timers))
    }

    private fun buildNotification(timers: List<TimerState>): Notification {
        val running = timers.filter { it.status == Status.RUNNING }
        val text = when (running.size) {
            0 -> "Paused"
            1 -> "1 timer running"
            else -> "${running.size} timers running"
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)

        // Live countdown to the next chime.
        running.minOfOrNull { it.endAt }?.let { nextEnd ->
            builder
                .setWhen(System.currentTimeMillis() + (nextEnd - SystemClock.elapsedRealtime()))
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        }
        return builder.build()
    }

    override fun onDestroy() {
        scope.cancel()
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL = "timers"
        const val NOTIFICATION_ID = 1
    }
}
