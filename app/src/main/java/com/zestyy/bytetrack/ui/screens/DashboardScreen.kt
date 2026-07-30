package com.zestyy.bytetrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zestyy.bytetrack.ui.MainViewModel
import com.zestyy.bytetrack.ui.components.CreditsFooter
import com.zestyy.bytetrack.ui.components.GlassCard
import com.zestyy.bytetrack.ui.components.NetworkBreakdownBar
import com.zestyy.bytetrack.ui.components.NetworkTypeProgressRows
import com.zestyy.bytetrack.ui.components.PeriodBreakdownList
import com.zestyy.bytetrack.ui.components.RangeSwitcher
import com.zestyy.bytetrack.ui.components.UpdateBanner
import com.zestyy.bytetrack.ui.theme.ByteOrange
import com.zestyy.bytetrack.ui.theme.TextPrimary
import com.zestyy.bytetrack.ui.theme.TextSecondary
import com.zestyy.bytetrack.ui.theme.VoidBlack
import com.zestyy.bytetrack.util.RangeType
import com.zestyy.bytetrack.util.toReadableBytes
import com.zestyy.bytetrack.util.toReadableDuration

@Composable
fun DashboardScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val isDayView = state.selectedRange.type == RangeType.DAY

    // Ambient mesh-gradient backdrop the glass cards blur against - flat backgrounds give the
    // RenderEffect blur nothing to distort, so this gradient is what actually sells the effect.
    val backdrop = Brush.radialGradient(
        colors = listOf(ByteOrange.copy(alpha = 0.18f), VoidBlack, VoidBlack),
        center = Offset(200f, 0f),
        radius = 1200f,
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(backdrop)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("byte!track", style = MaterialTheme.typography.headlineMedium, color = ByteOrange, fontWeight = FontWeight.Bold)
            Text("Your activity", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }

        state.updateInfo?.let { update ->
            item {
                UpdateBanner(
                    update = update,
                    onInstall = viewModel::installUpdate,
                    onDismiss = viewModel::dismissUpdate,
                )
            }
        }

        item {
            RangeSwitcher(
                selected = state.selectedRange.type,
                label = state.rangeLabel,
                canGoNext = state.canGoNext,
                onTypeSelected = viewModel::selectRangeType,
                onPrevious = viewModel::goToPrevious,
                onNext = viewModel::goToNext,
            )
        }

        item {
            // THE BUG THIS FIXES ("data used and screen time bentos should be same size"): both
            // cards used weight(1f) for equal WIDTH, but nothing forced equal HEIGHT - each
            // GlassCard just wrap-contents to its own Column, so whichever value happened to
            // render as a longer/wrapped string (e.g. "1,024.0 MB" vs "3h 42m") made that card
            // taller than its sibling. Row(Modifier.height(IntrinsicSize.Min)) measures both
            // children's natural height first and gives the row exactly the taller one; each
            // card then fillMaxHeight()s to match it. maxLines + ellipsis on the value keeps a
            // single freak-long number from blowing the shared height back up again.
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(IntrinsicSize.Min),
            ) {
                GlassCard(modifier = Modifier.fillMaxWidth().fillMaxHeight().weight(1f)) {
                    Column {
                        Text("Data used", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.totalDataToday.toReadableBytes(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                GlassCard(modifier = Modifier.fillMaxWidth().fillMaxHeight().weight(1f)) {
                    Column {
                        Text("Screen time", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.totalScreenTimeToday.toReadableDuration(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Wi-Fi / Mobile / Hotspot", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(Modifier.height(12.dp))
                    NetworkTypeProgressRows(
                        wifiBytes = state.totalWifiToday,
                        mobileBytes = state.totalMobileToday,
                        hotspotBytes = state.totalHotspotToday,
                        formatBytes = { it.toReadableBytes() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (isDayView) {
            item {
                Text("Top apps", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            }

            items(state.topApps) { app ->
                GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(app.label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(app.dataBytes.toReadableBytes(), style = MaterialTheme.typography.bodyMedium, color = ByteOrange)
                        }
                        Spacer(Modifier.height(8.dp))
                        NetworkBreakdownBar(
                            wifiBytes = app.wifiBytes,
                            mobileBytes = app.mobileBytes,
                            hotspotBytes = 0L,
                        )
                    }
                }
            }
        } else {
            item {
                val breakdownTitle = if (state.selectedRange.type == RangeType.YEAR) "By month" else "By day"
                Text(breakdownTitle, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Text(
                    "Tap any row to open its own view",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }

            item {
                PeriodBreakdownList(
                    buckets = state.periodBreakdown,
                    isLoading = state.isLoadingBreakdown,
                    onBucketClick = viewModel::drillInto,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            CreditsFooter()
        }
    }
}
