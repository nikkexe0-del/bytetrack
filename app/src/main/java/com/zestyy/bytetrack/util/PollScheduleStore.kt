package com.zestyy.bytetrack.util

import android.content.Context

/**
 * THE BUG THIS FIXES (this was the main "data calculation is wrong" report):
 *
 * [UsageTrackingService] polls every 60s using `since = <its own lastPoll var>`. Separately,
 * [SyncWorker] runs every ~15 minutes and used to poll with a *hardcoded* `since = now - 20min`
 * window, with no idea when the service last polled. Neither poll diffs against rows already
 * written to the DB - [NetworkUsageRepository.pollAndStore] just inserts whatever
 * NetworkStatsManager reports for the exact `[since, now)` window it's given. So whenever
 * SyncWorker's 20-minute lookback overlapped with a window the service had already recorded
 * (which was basically always, since the service polls every minute), those overlapping bytes
 * got written to the database TWICE - inflating "today's data" by however much double-counted
 * overlap there was. Same problem applied to screen-time sessions.
 *
 * The fix: one shared, persisted cursor (`lastPollEnd`) that BOTH the service and the worker
 * claim from atomically via [claimWindow]. Whoever polls first "wins" the window and advances
 * the cursor; if the other one fires moments later, it sees there's no unclaimed time left and
 * skips instead of re-querying a window that was already recorded. If the service was killed and
 * the worker is catching up after a gap, it correctly picks up exactly where the last successful
 * poll (by either party) left off - no gap, no overlap.
 */
object PollScheduleStore {
    private const val PREFS = "byte_track_poll_schedule"
    private const val KEY_LAST_POLL_END = "last_poll_end"

    private val lock = Any()

    /**
     * Atomically claims the unpolled window since the last successful poll (by either caller),
     * capped to at most [maxLookbackMs] in the past (so a very stale cursor - e.g. first run, or
     * the app having been force-stopped for days - doesn't try to query a huge historical range).
     *
     * Returns the [since, now) window to poll, or null if there's nothing new to poll (another
     * caller already claimed up to `now`).
     */
    fun claimWindow(context: Context, now: Long, maxLookbackMs: Long): Pair<Long, Long>? {
        synchronized(lock) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val storedLastEnd = prefs.getLong(KEY_LAST_POLL_END, 0L)
            val earliestAllowed = now - maxLookbackMs
            val since = if (storedLastEnd <= 0L) earliestAllowed else maxOf(storedLastEnd, earliestAllowed)

            if (since >= now) return null

            prefs.edit().putLong(KEY_LAST_POLL_END, now).apply()
            return since to now
        }
    }
}
