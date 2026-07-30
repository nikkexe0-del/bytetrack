package com.zestyy.bytetrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zestyy.bytetrack.ui.MainViewModel
import com.zestyy.bytetrack.ui.components.CreditsFooter
import com.zestyy.bytetrack.ui.components.GlassCard
import com.zestyy.bytetrack.ui.components.NetworkBreakdownBar
import com.zestyy.bytetrack.ui.theme.ByteOrange
import com.zestyy.bytetrack.ui.theme.TextPrimary
import com.zestyy.bytetrack.ui.theme.TextSecondary
import com.zestyy.bytetrack.ui.theme.VoidBlack
import com.zestyy.bytetrack.util.toReadableBytes
import com.zestyy.bytetrack.util.toReadableDuration

@Composable
fun AppsScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(VoidBlack).padding(horizontal = 16.dp, vertical = 20.dp)) {
        Text("Apps", style = MaterialTheme.typography.headlineMedium, color = ByteOrange, fontWeight = FontWeight.Bold)
        Text("${state.topApps.size} tracked · ${state.rangeLabel}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

        Spacer(Modifier.padding(top = 12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            items(state.topApps) { app ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(app.label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("Screen: ${app.screenTimeMs.toReadableDuration()}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                        Spacer(Modifier.height(10.dp))
                        NetworkBreakdownBar(
                            wifiBytes = app.wifiBytes,
                            mobileBytes = app.mobileBytes,
                            hotspotBytes = app.hotspotBytes,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Wi-Fi ${app.wifiBytes.toReadableBytes()}", style = MaterialTheme.typography.labelSmall, color = com.zestyy.bytetrack.ui.theme.WifiColor)
                            Text("Mobile ${app.mobileBytes.toReadableBytes()}", style = MaterialTheme.typography.labelSmall, color = com.zestyy.bytetrack.ui.theme.MobileColor)
                            Text("Hotspot ${app.hotspotBytes.toReadableBytes()}", style = MaterialTheme.typography.labelSmall, color = com.zestyy.bytetrack.ui.theme.HotspotColor)
                        }
                    }
                }
            }

            item {
                CreditsFooter()
            }
        }
    }
}
