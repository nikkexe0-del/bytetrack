package com.zestyy.bytetrack.data.repository

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.zestyy.bytetrack.data.local.AppDatabase
import com.zestyy.bytetrack.data.local.ScreenTimeSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Derives per-app foreground sessions from [UsageStatsManager]'s raw event stream
 * (MOVE_TO_FOREGROUND / MOVE_TO_BACKGROUND pairs), which is far more accurate than
 * queryUsageStats' pre-aggregated totals for building a real timeline.
 *
 * THE BUG THIS FIXES: each poll only queries events inside its own small window (e.g. the last
 * 60s). If an app was already in the foreground *before* that window started - which is the
 * common case for anything you're continuously using - there's no MOVE_TO_FOREGROUND event
 * inside the window to anchor a session to, so a naive per-window scan loses all time on that
 * app except the one poll where it actually got focused. That's why screen time was reading
 * "always under 1 minute" no matter how long an app was actually open.
 *
 * Fix: persist which package (if any) was in the foreground at the end of the previous poll
 * (via SharedPreferences) and seed this poll's open-session map with it, anchored at `since`
 * rather than the original foreground timestamp - so we never double count time already written
 * in a prior poll, but we also never silently drop the continuing session.
 */
class ScreenTimeRepository(private val context: Context) {

    private val usageStatsManager by lazy {
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    }
    private val db by lazy { AppDatabase.get(context) }
    private val prefs by lazy { context.getSharedPreferences("byte_track_screen_time", Context.MODE_PRIVATE) }

    suspend fun pollAndStore(sinceMillis: Long, nowMillis: Long) = withContext(Dispatchers.IO) {
        val sessions = extractSessions(sinceMillis, nowMillis)
        val dao = db.screenTimeDao()
        sessions.forEach { dao.insert(it) }
    }

    private fun extractSessions(since: Long, now: Long): List<ScreenTimeSession> {
        val events = try {
            usageStatsManager.queryEvents(since, now)
        } catch (e: SecurityException) {
            return emptyList()
        }

        // Seed with whatever was still in the foreground when the previous poll ended, anchored
        // at `since` (this window's start) so we pick up exactly the time that elapsed in THIS
        // window, not a re-count of time already written last poll.
        val openSessions = mutableMapOf<String, Long>()
        val persistedForeground = prefs.getString(KEY_CURRENT_FOREGROUND_PACKAGE, null)
        if (persistedForeground != null) {
            openSessions[persistedForeground] = since
        }

        val closed = mutableListOf<ScreenTimeSession>()
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    // If a different app was already marked open (e.g. from the seed above),
                    // close it out at this new app's foreground timestamp before overwriting.
                    val previouslyOpenPkg = openSessions.keys.firstOrNull { it != pkg }
                    if (previouslyOpenPkg != null) {
                        val start = openSessions.remove(previouslyOpenPkg)
                        if (start != null && event.timeStamp > start) {
                            closed += ScreenTimeSession(packageName = previouslyOpenPkg, startedAt = start, endedAt = event.timeStamp)
                        }
                    }
                    openSessions[pkg] = event.timeStamp
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val start = openSessions.remove(pkg)
                    if (start != null && event.timeStamp > start) {
                        closed += ScreenTimeSession(packageName = pkg, startedAt = start, endedAt = event.timeStamp)
                    }
                }
            }
        }

        // Anything still open at the end of this window is genuinely still in the foreground -
        // write a session covering just this window's contribution, then persist it as the seed
        // for next poll so the next window picks up where this one left off.
        val stillOpenPkg = openSessions.keys.firstOrNull()
        if (stillOpenPkg != null) {
            val start = openSessions.getValue(stillOpenPkg)
            if (now > start) {
                closed += ScreenTimeSession(packageName = stillOpenPkg, startedAt = start, endedAt = now)
            }
        }
        prefs.edit().putString(KEY_CURRENT_FOREGROUND_PACKAGE, stillOpenPkg).apply()

        return closed
    }

    companion object {
        private const val KEY_CURRENT_FOREGROUND_PACKAGE = "current_foreground_package"
    }
}
