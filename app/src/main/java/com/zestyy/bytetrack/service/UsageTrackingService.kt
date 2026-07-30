package com.zestyy.bytetrack.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.zestyy.bytetrack.MainActivity
import com.zestyy.bytetrack.R
import com.zestyy.bytetrack.data.local.AppDatabase
import com.zestyy.bytetrack.data.repository.NetworkUsageRepository
import com.zestyy.bytetrack.data.repository.PermissionsRepository
import com.zestyy.bytetrack.data.repository.ScreenTimeRepository
import com.zestyy.bytetrack.widget.ByteTrackWidgetProvider
import com.zestyy.bytetrack.util.PollScheduleStore
import com.zestyy.bytetrack.util.startOfLocalDay
import com.zestyy.bytetrack.util.toReadableBytes
import com.zestyy.bytetrack.util.toReadableDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Keeps byte!track alive as a foreground service so polling continues while the app is in the
 * background - Android throttles/kills background work aggressively otherwise, and NetworkStats
 * / UsageStats deltas are cheapest to reconcile on a tight, predictable interval rather than
 * relying solely on WorkManager's minimum-15-minute periodic window.
 *
 * The foreground notification is deliberately non-dismissable (`setOngoing(true)`) and refreshed
 * every poll cycle with live progress bars for today's data usage and screen time - it's meant
 * to be a persistent glanceable readout, the same way a music player's notification stays put.
 *
 * SyncWorker (see worker/SyncWorker.kt) is registered as a belt-and-suspenders backup in case
 * the user or OEM battery manager kills this service - it guarantees at least one poll every
 * 15 minutes even if the foreground service gets killed.
 */
class UsageTrackingService : Service() {

    private val scope = CoroutineScope(SupervisorJob())
    private var loopJob: Job? = null

    private lateinit var networkRepo: NetworkUsageRepository
    private lateinit var screenTimeRepo: ScreenTimeRepository
    private lateinit var permissionsRepo: PermissionsRepository

    companion object {
        const val CHANNEL_ID = "byte_track_tracking"
        const val NOTIFICATION_ID = 1001
        const val POLL_INTERVAL_MS = 60_000L // 1 minute - tight enough for a real timeline

        // How far back the FIRST poll after a (re)start is allowed to catch up. Generous on
        // purpose: NetworkStatsManager/UsageStatsManager data survives our process being killed,
        // so recovering a long gap (aggressive OEM battery managers can kill this service for
        // hours) is strictly better than silently losing it. 12h keeps a single catch-up query
        // cheap while covering the realistic worst case of "phone sat overnight with the app
        // battery-restricted."
        val CATCH_UP_LOOKBACK_MS = TimeUnit.HOURS.toMillis(12)

        // Daily reference goals the notification's progress bars are measured against. There's
        // no settings screen yet to customize these per-user - these are sane defaults so the
        // bars mean something out of the box. Wiring a settings screen to override them (stored
        // in SharedPreferences, read here instead of the constant) is a natural next step.
        const val DEFAULT_DAILY_DATA_GOAL_BYTES = 2L * 1024 * 1024 * 1024 // 2 GB
        val DEFAULT_DAILY_SCREEN_TIME_GOAL_MS = TimeUnit.HOURS.toMillis(4)
    }

    override fun onCreate() {
        super.onCreate()
        networkRepo = NetworkUsageRepository(applicationContext)
        screenTimeRepo = ScreenTimeRepository(applicationContext)
        permissionsRepo = PermissionsRepository(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification(dataBytes = 0, screenTimeMs = 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (loopJob == null || loopJob?.isActive != true) {
            loopJob = scope.launch { pollLoop() }
        }
        return START_STICKY
    }

    private suspend fun pollLoop() {
        val db = AppDatabase.get(applicationContext)
        var isFirstIteration = true
        while (true) {
            if (permissionsRepo.hasUsageAccess()) {
                val now = System.currentTimeMillis()

                // Claim the window since the last successful poll from EITHER this service or
                // SyncWorker - see PollScheduleStore for why this is what stops double counting.
                //
                // THE BUG THIS FIXES ("data/screen time isn't close to what I actually used"):
                // this used to ALWAYS cap the lookback at POLL_INTERVAL_MS (60s), including on
                // the very first iteration after the service (re)starts. But the service gets
                // killed and restarted all the time in practice - OEM battery managers, the app
                // being swiped away, a phone reboot. NetworkStatsManager/UsageStatsManager keep
                // recording in the background the whole time regardless of whether OUR service is
                // alive, so that history is genuinely recoverable - a 60s cap on the first poll
                // after a restart was throwing away everything since the last successful poll and
                // permanently advancing the shared cursor past it, so it could never be recovered
                // later either. Only the FIRST iteration after (re)start uses a generous catch-up
                // window; every iteration after that goes back to the tight 60s cap, since at that
                // point the service is the steady heartbeat and there's nothing to catch up on.
                val lookbackCap = if (isFirstIteration) CATCH_UP_LOOKBACK_MS else POLL_INTERVAL_MS
                isFirstIteration = false
                val window = PollScheduleStore.claimWindow(applicationContext, now, lookbackCap)
                if (window != null) {
                    val (since, claimedNow) = window
                    networkRepo.pollAndStore(since, claimedNow)
                    screenTimeRepo.pollAndStore(since, claimedNow)
                }

                // Local-day boundary, not UTC - see util/TimeRange.kt for why this matters.
                val startOfDay = startOfLocalDay(now)
                val dataToday = db.dataUsageDao().totalBytesBetweenOnce(startOfDay, now)
                val screenTimeToday = db.screenTimeDao().totalScreenTimeBetweenOnce(startOfDay, now)
                updateNotification(dataToday, screenTimeToday)
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun updateNotification(dataBytes: Long, screenTimeMs: Long) {
        val notification = buildNotification(dataBytes, screenTimeMs)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
        // Keep the home-screen widget (if placed) as fresh as the notification, rather than
        // waiting on the OS's 30-minute widget update floor - see ByteTrackWidgetProvider.
        ByteTrackWidgetProvider.pushUpdate(applicationContext, dataBytes, screenTimeMs)
    }

    private fun buildNotification(dataBytes: Long, screenTimeMs: Long): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, openAppIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val dataProgress = ((dataBytes.toDouble() / DEFAULT_DAILY_DATA_GOAL_BYTES) * 100)
            .toInt().coerceIn(0, 100)
        val screenProgress = ((screenTimeMs.toDouble() / DEFAULT_DAILY_SCREEN_TIME_GOAL_MS) * 100)
            .toInt().coerceIn(0, 100)

        val views = RemoteViews(packageName, R.layout.notification_usage).apply {
            setTextViewText(R.id.notif_data_value, dataBytes.toReadableBytes())
            setProgressBar(R.id.notif_data_progress, 100, dataProgress, false)
            setTextViewText(R.id.notif_screen_value, screenTimeMs.toReadableDuration())
            setProgressBar(R.id.notif_screen_progress, 100, screenProgress, false)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // non-dismissable - swiping it away does nothing while tracking is active
            .setAutoCancel(false)
            .setOnlyAlertOnce(true) // don't re-alert/re-buzz on every 60s refresh
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(views)
            .setCustomBigContentView(views)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Usage tracking",
                NotificationManager.IMPORTANCE_LOW // visible but silent - this updates every minute
            ).apply {
                description = "Ongoing readout of today's data usage and screen time"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        loopJob?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
