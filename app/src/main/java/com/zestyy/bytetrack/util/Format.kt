package com.zestyy.bytetrack.util

import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

fun Long.toReadableBytes(): String {
    if (abs(this) < 1024) return "$this B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    val exp = (ln(abs(this).toDouble()) / ln(1024.0)).toInt().coerceIn(1, units.size)
    val value = this / 1024.0.pow(exp)
    return String.format("%.1f %s", value, units[exp - 1])
}

fun Long.toReadableDuration(): String {
    val hours = TimeUnit.MILLISECONDS.toHours(this)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(this) % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}

// java.time's DateTimeFormatter (unlike SimpleDateFormat) is immutable and thread-safe, so this
// can safely be a shared top-level instance even though poll/DB work happens off the main thread.
private val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.getDefault())

fun Long.toTimeOfDay(): String =
    java.time.Instant.ofEpochMilli(this).atZone(java.time.ZoneId.systemDefault()).format(timeFormatter)

fun timeRangeLabel(start: Long, end: Long): String {
    val startStr = start.toTimeOfDay()
    val endStr = end.toTimeOfDay()
    return if (startStr == endStr) startStr else "$startStr – $endStr"
}
