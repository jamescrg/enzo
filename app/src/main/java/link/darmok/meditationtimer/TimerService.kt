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
 * Foreground service that exists while the timer is running or paused, so Android doesn't kill
 * the process and lose it. Holds a CPU wake lock only while the timer is counting down,
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
            NotificationChannel(CHANNEL, "Running timer", NotificationManager.IMPORTANCE_LOW)
        )
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MeditationTimer:countdown")
            .apply { setReferenceCounted(false) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(TimerEngine.timer.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        if (watcher == null) watcher = scope.launch { TimerEngine.timer.collect(::onTimerChanged) }
        return START_NOT_STICKY
    }

    private fun onTimerChanged(timer: TimerState) {
        if (timer.status == Status.IDLE) {
            stopSelf()
            return
        }
        if (timer.status == Status.RUNNING) {
            @Suppress("WakelockTimeout") // held exactly as long as the timer is counting down
            wakeLock.acquire()
        } else if (wakeLock.isHeld) {
            wakeLock.release()
        }
        notifications.notify(NOTIFICATION_ID, buildNotification(timer))
    }

    private fun buildNotification(timer: TimerState): Notification {
        val running = timer.status == Status.RUNNING
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(if (running) "Timer running" else "Timer paused")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)

        // Live countdown to the chime.
        if (running) {
            builder
                .setWhen(System.currentTimeMillis() + (timer.endAt - SystemClock.elapsedRealtime()))
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
