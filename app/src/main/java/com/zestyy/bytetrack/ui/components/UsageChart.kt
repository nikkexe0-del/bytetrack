package com.zestyy.bytetrack.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zestyy.bytetrack.data.local.HourlyBucket
import com.zestyy.bytetrack.ui.theme.HotspotColor
import com.zestyy.bytetrack.ui.theme.MobileColor
import com.zestyy.bytetrack.ui.theme.WifiColor

/**
 * A stacked bar timeline: one bar per hour, segmented by Wi-Fi / Mobile / Hotspot bytes.
 * Deliberately dependency-free (plain Canvas) to keep the build lean — swap in Vico/MPAndroidChart
 * later if you want scrubbing, tooltips, or pinch-zoom.
 */
@Composable
fun UsageTimelineChart(buckets: List<HourlyBucket>, modifier: Modifier = Modifier) {
    val maxTotal = (buckets.maxOfOrNull { it.wifiBytes + it.mobileBytes + it.hotspotBytes } ?: 1L).coerceAtLeast(1L)

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(horizontal = 4.dp)
        ) {
            if (buckets.isEmpty()) return@Canvas
            val barCount = buckets.size
            val gap = 4.dp.toPx()
            val barWidth = (size.width - gap * (barCount - 1)) / barCount

            buckets.forEachIndexed { index, bucket ->
                val x = index * (barWidth + gap)
                var yCursor = size.height

                fun drawSegment(bytes: Long, color: Color) {
                    if (bytes <= 0) return
                    val segHeight = (bytes.toFloat() / maxTotal) * size.height
                    yCursor -= segHeight
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, yCursor),
                        size = Size(barWidth, segHeight),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                }

                drawSegment(bucket.mobileBytes, MobileColor)
                drawSegment(bucket.wifiBytes, WifiColor)
                drawSegment(bucket.hotspotBytes, HotspotColor)
            }
        }
    }
}
