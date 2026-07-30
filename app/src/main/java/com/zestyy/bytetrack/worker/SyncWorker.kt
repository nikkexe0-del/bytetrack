package com.zestyy.bytetrack.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zestyy.bytetrack.data.repository.NetworkUsageRepository
import com.zestyy.bytetrack.data.repository.PermissionsRepository
import com.zestyy.bytetrack.data.repository.ScreenTimeRepository
import com.zestyy.bytetrack.util.PollScheduleStore
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val permissionsRepo = PermissionsRepository(applicationContext)
        if (!permissionsRepo.hasUsageAccess()) return Result.success()

        val networkRepo = NetworkUsageRepository(applicationContext)
        val screenTimeRepo = ScreenTimeRepository(applicationContext)

        val now = System.currentTimeMillis()

        // Claim whatever window is left unpolled since the last successful poll from EITHER this
        // worker or UsageTrackingService (see PollScheduleStore). This used to hardcode
        // `since = now - 20min` on every run regardless of whether the foreground service had
        // already just polled a nearly-identical window seconds earlier - that overlap got
        // written to the DB twice every ~15 minutes and was the main source of inflated totals.
        //
        // The cap ALSO used to be exactly 20 minutes even when the cursor was genuinely stale
        // (service killed for hours by an OEM battery manager) - but NetworkStatsManager and
        // UsageStatsManager keep recording regardless of whether our process is alive, so a
        // 20-minute cap wasn't protecting anything, it was just permanently discarding real,
        // recoverable history and advancing the cursor past it. 12h matches the service's own
        // catch-up window (see UsageTrackingService.CATCH_UP_LOOKBACK_MS) - generous enough to
        // recover a genuinely long gap, still cheap as a single querySummary call.
        val window = PollScheduleStore.claimWindow(applicationContext, now, TimeUnit.HOURS.toMillis(12))
            ?: return Result.success()
        val (since, claimedNow) = window

        networkRepo.pollAndStore(since, claimedNow)
        screenTimeRepo.pollAndStore(since, claimedNow)

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "byte_track_sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
