package com.zestyy.bytetrack.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zestyy.bytetrack.ui.theme.HotspotColor
import com.zestyy.bytetrack.ui.theme.MobileColor
import com.zestyy.bytetrack.ui.theme.TextTertiary
import com.zestyy.bytetrack.ui.theme.WifiColor

/**
 * Thin horizontal stacked bar showing an app's Wi-Fi (blue) / Mobile (orange) / Hotspot (purple)
 * proportion at a glance - same color key as the timeline chart and dashboard legend, so colors
 * mean the same thing everywhere in the app.
 */
@Composable
fun NetworkBreakdownBar(
    wifiBytes: Long,
    mobileBytes: Long,
    hotspotBytes: Long,
    modifier: Modifier = Modifier,
) {
    val total = (wifiBytes + mobileBytes + hotspotBytes).coerceAtLeast(1L)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
    ) {
        var x = 0f
        // background track so zero-usage apps still show a visible baseline
        drawRoundRect(color = Color.White.copy(alpha = 0.06f), size = size, cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()))

        fun drawSeg(bytes: Long, color: Color) {
            if (bytes <= 0) return
            val w = (bytes.toFloat() / total) * size.width
            drawRoundRect(
                color = color,
                topLeft = Offset(x, 0f),
                size = Size(w, size.height),
            )
            x += w
        }
        drawSeg(wifiBytes, WifiColor)
        drawSeg(mobileBytes, MobileColor)
        drawSeg(hotspotBytes, HotspotColor)
    }
}

/**
 * Wi-Fi / Mobile / Hotspot each as their own labeled progress bar, with the data amount written
 * next to it - one row per network type, bar length shows that type's share of the total.
 */
@Composable
fun NetworkTypeProgressRows(
    wifiBytes: Long,
    mobileBytes: Long,
    hotspotBytes: Long,
    formatBytes: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    val total = (wifiBytes + mobileBytes + hotspotBytes).coerceAtLeast(1L)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NetworkTypeProgressRow("Wi-Fi", WifiColor, wifiBytes, total, formatBytes)
        NetworkTypeProgressRow("Mobile", MobileColor, mobileBytes, total, formatBytes)
        NetworkTypeProgressRow("Hotspot", HotspotColor, hotspotBytes, total, formatBytes)
    }
}

@Composable
private fun NetworkTypeProgressRow(
    label: String,
    color: Color,
    bytes: Long,
    total: Long,
    formatBytes: (Long) -> String,
) {
    val fraction = (bytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            modifier = Modifier.width(58.dp),
        )
        Spacer(Modifier.width(8.dp))
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        ) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.06f),
                size = size,
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            )
            if (bytes > 0) {
                drawRoundRect(
                    color = color,
                    size = Size(size.width * fraction, size.height),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            formatBytes(bytes),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.width(58.dp),
            textAlign = TextAlign.End,
        )
    }
}

/** Compact colored byte readout under an app row, e.g. "● Wi-Fi 12MB   ● Mobile 4MB". */
@Composable
fun NetworkBreakdownLabel(
    wifiBytes: Long,
    mobileBytes: Long,
    hotspotBytes: Long,
    formatBytes: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (wifiBytes > 0) ColoredByteChip(WifiColor, formatBytes(wifiBytes))
        if (mobileBytes > 0) ColoredByteChip(MobileColor, formatBytes(mobileBytes))
        if (hotspotBytes > 0) ColoredByteChip(HotspotColor, formatBytes(hotspotBytes))
        if (wifiBytes <= 0 && mobileBytes <= 0 && hotspotBytes <= 0) {
            Text("No usage", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        }
    }
}

@Composable
private fun ColoredByteChip(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
