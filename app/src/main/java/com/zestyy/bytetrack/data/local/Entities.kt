package com.zestyy.bytetrack.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class NetworkType { WIFI, MOBILE, HOTSPOT }

/**
 * One delta sample: "package X used Y bytes of network Z between timestamps".
 * Written every poll interval by UsageTrackingService / SyncWorker so we can reconstruct any
 * timeline granularity (hour/day/week) purely by summing rows in a date range.
 */
@Entity(tableName = "data_usage_samples")
data class DataUsageSample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val networkType: NetworkType,
    val rxBytes: Long,
    val txBytes: Long,
    val periodStart: Long, // epoch millis
    val periodEnd: Long,   // epoch millis
)

/** One foreground session for an app, used to derive screen time. */
@Entity(tableName = "screen_time_sessions")
data class ScreenTimeSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val startedAt: Long,
    val endedAt: Long,
) {
    val durationMs: Long get() = (endedAt - startedAt).coerceAtLeast(0)
}

/** Cached app metadata so we don't hit PackageManager on every list render. */
@Entity(tableName = "app_info")
data class AppInfoEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
)

/** Aggregated view used by the timeline chart — one row per hour bucket. */
data class HourlyBucket(
    val hourStart: Long,
    val wifiBytes: Long,
    val mobileBytes: Long,
    val hotspotBytes: Long,
)
