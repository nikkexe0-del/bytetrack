package com.zestyy.bytetrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zestyy.bytetrack.data.local.NetworkType
import com.zestyy.bytetrack.ui.MainViewModel
import com.zestyy.bytetrack.ui.TimelineRow
import com.zestyy.bytetrack.ui.components.CreditsFooter
import com.zestyy.bytetrack.ui.components.GlassCard
import com.zestyy.bytetrack.ui.components.PeriodBreakdownList
import com.zestyy.bytetrack.ui.components.RangeSwitcher
import com.zestyy.bytetrack.ui.theme.ByteOrange
import com.zestyy.bytetrack.ui.theme.HotspotColor
import com.zestyy.bytetrack.ui.theme.MobileColor
import com.zestyy.bytetrack.ui.theme.TextPrimary
import com.zestyy.bytetrack.ui.theme.TextSecondary
import com.zestyy.bytetrack.ui.theme.TextTertiary
import com.zestyy.bytetrack.ui.theme.VoidBlack
import com.zestyy.bytetrack.ui.theme.WifiColor
import com.zestyy.bytetrack.util.RangeType
import com.zestyy.bytetrack.util.timeRangeLabel
import com.zestyy.bytetrack.util.toReadableBytes

private fun colorFor(type: NetworkType) = when (type) {
    NetworkType.WIFI -> WifiColor
    NetworkType.MOBILE -> MobileColor
    NetworkType.HOTSPOT -> HotspotColor
}

private fun labelFor(type: NetworkType) = when (type) {
    NetworkType.WIFI -> "Wi-Fi"
    NetworkType.MOBILE -> "Mobile"
    NetworkType.HOTSPOT -> "Hotspot"
}

@Composable
fun TimelineScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val isDayView = state.selectedRange.type == RangeType.DAY

    Column(modifier = Modifier.fillMaxSize().background(VoidBlack).padding(horizontal = 16.dp, vertical = 20.dp)) {
        Text("Timeline", style = MaterialTheme.typography.headlineMedium, color = ByteOrange, fontWeight = FontWeight.Bold)
        Text(
            if (isDayView) {
                if (state.timeline.isEmpty()) "Nothing tracked ${state.rangeLabel.lowercase()}" else "${state.timeline.size} sessions · ${state.rangeLabel}"
            } else {
                "Pick a day to see its sessions"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(Modifier.height(12.dp))

        RangeSwitcher(
            selected = state.selectedRange.type,
            label = state.rangeLabel,
            canGoNext = state.canGoNext,
            onTypeSelected = viewModel::selectRangeType,
            onPrevious = viewModel::goToPrevious,
            onNext = viewModel::goToNext,
        )

        Spacer(Modifier.height(12.dp))

        if (isDayView) {
            if (state.timeline.isEmpty()) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "As you use apps, byte!track will list each session here — which app, how long, and how much data it pulled, color-coded by Wi-Fi / Mobile / Hotspot. Sessions under 1 minute and under 1MB are filtered out as noise.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    items(state.timeline, key = { it.packageName + it.startedAt }) { entry ->
                        TimelineRowCard(entry)
                    }
                    item { CreditsFooter() }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                item {
                    PeriodBreakdownList(
                        buckets = state.periodBreakdown,
                        isLoading = state.isLoadingBreakdown,
                        onBucketClick = viewModel::drillInto,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item { CreditsFooter() }
            }
        }
    }
}

@Composable
private fun TimelineRowCard(entry: TimelineRow) {
    val color = colorFor(entry.dominantNetworkType)

    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Colored network-type indicator dot - same color key used everywhere else in the app
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(entry.label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(
                    timeRangeLabel(entry.startedAt, entry.endedAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(entry.totalBytes.toReadableBytes(), style = MaterialTheme.typography.titleMedium, color = color)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (entry.isMixedNetwork) "Mixed" else labelFor(entry.dominantNetworkType),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
        }
    }
}
