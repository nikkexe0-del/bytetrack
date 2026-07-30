package com.zestyy.bytetrack.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.zestyy.bytetrack.MainActivity
import com.zestyy.bytetrack.R
import com.zestyy.bytetrack.data.local.AppDatabase
import com.zestyy.bytetrack.util.startOfLocalDay
import com.zestyy.bytetrack.util.toReadableBytes
import com.zestyy.bytetrack.util.toReadableDuration
import com.zestyy.bytetrack.util.toTimeOfDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Home-screen widget - the same "Data used" / "Screen time" bento the Dashboard shows for today,
 * pinned to the launcher. See res/layout/widget_usage.xml for the shared-lookalike layout and
 * res/xml/widget_info.xml for why there are two update paths:
 *
 *  1. [onUpdate] - the standard AppWidgetProvider callback. Fires on first placement and then on
 *     the OS-mandated 30-minute floor (android:updatePeriodMillis). Queries the DB itself since
 *     there's no guarantee UsageTrackingService just polled.
 *  2. [pushUpdate] - called directly from UsageTrackingService at the end of every ~60s poll
 *     cycle (same place it refreshes the notification), so the widget stays as fresh as the
 *     notification instead of only refreshing every 30 minutes.
 */
class ByteTrackWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val db = AppDatabase.get(context.applicationContext)
        CoroutineScope(Dispatchers.IO).launch {
            val now = System.currentTimeMillis()
            val startOfDay = startOfLocalDay(now)
            val dataBytes = db.dataUsageDao().totalBytesBetweenOnce(startOfDay, now)
            val screenTimeMs = db.screenTimeDao().totalScreenTimeBetweenOnce(startOfDay, now)
            val views = buildViews(context, dataBytes, screenTimeMs, now)
            appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, views) }
        }
    }

    companion object {
        /** Push a fresh readout to every placed instance of this widget, no DB query needed since
         * the caller (UsageTrackingService) already has today's totals from its own poll cycle. */
        fun pushUpdate(context: Context, dataBytes: Long, screenTimeMs: Long) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, ByteTrackWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return // no widgets placed - nothing to do
            val views = buildViews(context, dataBytes, screenTimeMs, System.currentTimeMillis())
            ids.forEach { id -> manager.updateAppWidget(id, views) }
        }

        private fun buildViews(context: Context, dataBytes: Long, screenTimeMs: Long, now: Long): RemoteViews {
            val openAppIntent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            return RemoteViews(context.packageName, R.layout.widget_usage).apply {
                setTextViewText(R.id.widget_data_value, dataBytes.toReadableBytes())
                setTextViewText(R.id.widget_screen_value, screenTimeMs.toReadableDuration())
                setTextViewText(R.id.widget_updated_at, now.toTimeOfDay())
                setOnClickPendingIntent(R.id.widget_data_card, pendingIntent)
                setOnClickPendingIntent(R.id.widget_screen_card, pendingIntent)
            }
        }
    }
}
