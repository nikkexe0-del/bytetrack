package com.zestyy.bytetrack.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Everything here operates in the device's LOCAL timezone, deliberately.
 *
 * THE BUG THIS FIXES: the app used to compute "start of today" as
 * `now - (now % TimeUnit.DAYS.toMillis(1))`, which is midnight UTC, not midnight local time.
 * For anyone outside UTC (e.g. IST, UTC+5:30) that means "today" actually flipped over at
 * 5:30am local time instead of 12:00am - usage between midnight and 5:30am got counted as
 * "yesterday", and the day's totals reset at the wrong moment. All day/week/month/year boundary
 * math now goes through [ZONE] + java.time so it always matches the calendar the user is
 * actually looking at their phone in.
 */
private val ZONE: ZoneId = ZoneId.systemDefault()

enum class RangeType { DAY, WEEK, MONTH, YEAR }

/** The range currently being viewed: a type (day/week/month/year) plus an anchor date inside it. */
data class SelectedRange(
    val type: RangeType,
    val anchor: LocalDate = LocalDate.now(ZONE),
)

fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZONE).toLocalDate()

fun LocalDate.startOfDayMillis(): Long = atStartOfDay(ZONE).toInstant().toEpochMilli()

/** Start-of-local-day for "now" - the correct replacement for the old UTC-based calculation. */
fun startOfLocalDay(epochMillis: Long = System.currentTimeMillis()): Long =
    epochMillis.toLocalDate().startOfDayMillis()

/** [start, endExclusive) epoch-millis window this range covers, in local time. */
fun SelectedRange.bounds(): Pair<Long, Long> = when (type) {
    RangeType.DAY -> {
        val start = anchor.startOfDayMillis()
        val end = anchor.plusDays(1).startOfDayMillis()
        start to end
    }
    RangeType.WEEK -> {
        val monday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val start = monday.startOfDayMillis()
        val end = monday.plusWeeks(1).startOfDayMillis()
        start to end
    }
    RangeType.MONTH -> {
        val first = anchor.withDayOfMonth(1)
        val start = first.startOfDayMillis()
        val end = first.plusMonths(1).startOfDayMillis()
        start to end
    }
    RangeType.YEAR -> {
        val firstOfYear = LocalDate.of(anchor.year, 1, 1)
        val start = firstOfYear.startOfDayMillis()
        val end = firstOfYear.plusYears(1).startOfDayMillis()
        start to end
    }
}

/** True if there's a "next" period worth navigating to (i.e. we're not already viewing the
 * current, still-ongoing period). Prevents paging into the future. */
fun SelectedRange.canGoNext(now: Long = System.currentTimeMillis()): Boolean {
    val (_, end) = bounds()
    return end <= now
}

fun SelectedRange.previous(): SelectedRange = copy(anchor = type.step(anchor, forward = false))

fun SelectedRange.next(): SelectedRange = copy(anchor = type.step(anchor, forward = true))

fun SelectedRange.jumpToToday(): SelectedRange = copy(anchor = LocalDate.now(ZONE))

private fun RangeType.step(anchor: LocalDate, forward: Boolean): LocalDate = when (this) {
    RangeType.DAY -> if (forward) anchor.plusDays(1) else anchor.minusDays(1)
    RangeType.WEEK -> if (forward) anchor.plusWeeks(1) else anchor.minusWeeks(1)
    RangeType.MONTH -> if (forward) anchor.plusMonths(1) else anchor.minusMonths(1)
    RangeType.YEAR -> if (forward) anchor.plusYears(1) else anchor.minusYears(1)
}

private val dayLabelFmt = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
private val weekEndFmt = DateTimeFormatter.ofPattern("MMM d, yyyy")
private val weekStartFmt = DateTimeFormatter.ofPattern("MMM d")
private val monthLabelFmt = DateTimeFormatter.ofPattern("MMMM yyyy")

fun SelectedRange.label(): String = when (type) {
    RangeType.DAY -> {
        val today = LocalDate.now(ZONE)
        when (anchor) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> anchor.format(dayLabelFmt)
        }
    }
    RangeType.WEEK -> {
        val monday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sunday = monday.plusDays(6)
        "${monday.format(weekStartFmt)} – ${sunday.format(weekEndFmt)}"
    }
    RangeType.MONTH -> anchor.withDayOfMonth(1).format(monthLabelFmt)
    RangeType.YEAR -> anchor.year.toString()
}

/**
 * One row in a week/month/year "period breakdown" list: either a single day (week/month view)
 * or a single month (year view), with the totals for that slice and where tapping it drills into.
 */
data class PeriodBucket(
    val label: String,
    val subLabel: String?,
    val anchor: LocalDate,
    val drillInto: RangeType,
    val dataBytes: Long,
    val screenTimeMs: Long,
    val isToday: Boolean,
    val isFuture: Boolean,
)

private val dayBucketLabelFmt = DateTimeFormatter.ofPattern("EEE")
private val dayBucketSubLabelFmt = DateTimeFormatter.ofPattern("MMM d")
private val monthBucketLabelFmt = DateTimeFormatter.ofPattern("MMMM")

/** The list of drill-down anchors this range should render as tappable rows: days for
 * week/month, months for year. Day view has no breakdown (it's already the finest grain). */
fun SelectedRange.bucketAnchors(): List<LocalDate> = when (type) {
    RangeType.DAY -> emptyList()
    RangeType.WEEK -> {
        val monday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        (0..6).map { monday.plusDays(it.toLong()) }
    }
    RangeType.MONTH -> {
        val first = anchor.withDayOfMonth(1)
        (0 until first.lengthOfMonth()).map { first.plusDays(it.toLong()) }
    }
    RangeType.YEAR -> (1..12).map { LocalDate.of(anchor.year, it, 1) }
}

fun SelectedRange.bucketFor(bucketAnchor: LocalDate, dataBytes: Long, screenTimeMs: Long): PeriodBucket {
    val today = LocalDate.now(ZONE)
    val now = System.currentTimeMillis()
    return when (type) {
        RangeType.YEAR -> PeriodBucket(
            label = bucketAnchor.format(monthBucketLabelFmt),
            subLabel = null,
            anchor = bucketAnchor,
            drillInto = RangeType.MONTH,
            dataBytes = dataBytes,
            screenTimeMs = screenTimeMs,
            isToday = bucketAnchor.year == today.year && bucketAnchor.month == today.month,
            isFuture = bucketAnchor.withDayOfMonth(1).startOfDayMillis() > now,
        )
        else -> PeriodBucket(
            label = bucketAnchor.format(dayBucketLabelFmt),
            subLabel = bucketAnchor.format(dayBucketSubLabelFmt),
            anchor = bucketAnchor,
            drillInto = RangeType.DAY,
            dataBytes = dataBytes,
            screenTimeMs = screenTimeMs,
            isToday = bucketAnchor == today,
            isFuture = bucketAnchor.startOfDayMillis() > now,
        )
    }
}
