package com.zestyy.bytetrack.util

import com.zestyy.bytetrack.data.local.DataUsageSample
import com.zestyy.bytetrack.data.local.NetworkType
import java.util.concurrent.TimeUnit

/**
 * One row in the Timeline tab: "this app used this much data, from this time to that time".
 *
 * Raw [DataUsageSample] rows are written every ~60s poll cycle, which is too granular to read
 * directly (a single 10-minute YouTube session would be ten separate rows). [mergeIntoTimeline]
 * collapses consecutive same-app samples into one entry as long as the gap between them is
 * small enough to plausibly be the same continuous usage burst.
 */
data class TimelineEntry(
    val packageName: String,
    val startedAt: Long,
    val endedAt: Long,
    val totalBytes: Long,
    val dominantNetworkType: NetworkType,
    val isMixedNetwork: Boolean,
)

private val MERGE_GAP_MS = TimeUnit.MINUTES.toMillis(3)

/** Below both of these, a merged entry is noise (a background app waking up for a second to
 * check for a push notification, etc.) rather than something worth showing the user. An entry
 * is kept if it clears EITHER bar - a quick-but-heavy burst (5MB in 10s) and a long-but-light
 * session (a chat app idling open for 10 minutes on a few KB) should both still show up; only
 * things that are both short AND tiny get dropped. */
private const val MIN_REPORTABLE_DURATION_MS = 60_000L // 1 minute
private const val MIN_REPORTABLE_BYTES = 1_000_000L // 1 MB

fun List<DataUsageSample>.mergeIntoTimeline(): List<TimelineEntry> {
    if (isEmpty()) return emptyList()

    // Samples come in already sorted by periodStart ASC from the DAO query; group by package
    // first so we only ever merge a package's samples with its own recent history, not another
    // app's that happened to poll in the same window.
    val openEntries = LinkedHashMap<String, MutableList<DataUsageSample>>()
    val finished = mutableListOf<TimelineEntry>()

    fun flush(pkg: String) {
        val samples = openEntries.remove(pkg) ?: return
        if (samples.isEmpty()) return
        val start = samples.minOf { it.periodStart }
        val end = samples.maxOf { it.periodEnd }
        val total = samples.sumOf { it.rxBytes + it.txBytes }
        val byType = samples.groupBy { it.networkType }.mapValues { (_, v) -> v.sumOf { it.rxBytes + it.txBytes } }
        val dominant = byType.maxByOrNull { it.value }?.key ?: samples.first().networkType
        finished += TimelineEntry(
            packageName = pkg,
            startedAt = start,
            endedAt = end,
            totalBytes = total,
            dominantNetworkType = dominant,
            isMixedNetwork = byType.keys.size > 1,
        )
    }

    for (sample in this) {
        val open = openEntries[sample.packageName]
        if (open == null) {
            openEntries[sample.packageName] = mutableListOf(sample)
        } else {
            val lastEnd = open.last().periodEnd
            if (sample.periodStart - lastEnd <= MERGE_GAP_MS) {
                open += sample
            } else {
                flush(sample.packageName)
                openEntries[sample.packageName] = mutableListOf(sample)
            }
        }
    }
    openEntries.keys.toList().forEach { flush(it) }

    return finished
        .filter { entry ->
            val durationMs = entry.endedAt - entry.startedAt
            durationMs >= MIN_REPORTABLE_DURATION_MS || entry.totalBytes >= MIN_REPORTABLE_BYTES
        }
        .sortedByDescending { it.endedAt }
}
