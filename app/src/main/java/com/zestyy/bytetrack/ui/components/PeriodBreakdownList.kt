package com.zestyy.bytetrack.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zestyy.bytetrack.ui.theme.ByteOrange
import com.zestyy.bytetrack.ui.theme.TextPrimary
import com.zestyy.bytetrack.ui.theme.TextSecondary
import com.zestyy.bytetrack.ui.theme.TextTertiary
import com.zestyy.bytetrack.util.PeriodBucket
import com.zestyy.bytetrack.util.toReadableBytes
import com.zestyy.bytetrack.util.toReadableDuration

/**
 * "Week/Month/Year" drill-down list: one row per day (inside a week or month view) or per month
 * (inside a year view), each tappable to jump straight into that day's/month's own view. Future
 * buckets (days/months that haven't happened yet within the currently-in-progress period) are
 * dimmed and not clickable - there's nothing to show yet.
 */
@Composable
fun PeriodBreakdownList(
    buckets: List<PeriodBucket>,
    isLoading: Boolean,
    onBucketClick: (PeriodBucket) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (isLoading && buckets.isEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = ByteOrange, modifier = Modifier.width(20.dp))
            }
            return@Column
        }
        buckets.forEach { bucket ->
            PeriodBucketRow(bucket, onClick = { if (!bucket.isFuture) onBucketClick(bucket) })
        }
    }
}

@Composable
private fun PeriodBucketRow(bucket: PeriodBucket, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (bucket.isFuture) 0.35f else 1f),
        contentPadding = 12.dp,
        onClick = if (bucket.isFuture) null else onClick,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.width(56.dp)) {
                Text(
                    bucket.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (bucket.isToday) ByteOrange else TextPrimary,
                    fontWeight = if (bucket.isToday) FontWeight.Bold else FontWeight.Medium,
                )
                if (bucket.subLabel != null) {
                    Text(bucket.subLabel, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                }
            }

            Spacer(Modifier.width(8.dp))

            if (bucket.dataBytes <= 0 && bucket.screenTimeMs <= 0) {
                Text(
                    if (bucket.isFuture) "—" else "No usage",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        bucket.dataBytes.toReadableBytes(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                    )
                    Text(
                        "Screen ${bucket.screenTimeMs.toReadableDuration()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}
