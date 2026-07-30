package com.zestyy.bytetrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zestyy.bytetrack.data.repository.PermissionsRepository
import com.zestyy.bytetrack.ui.components.GlassCard
import com.zestyy.bytetrack.ui.theme.ByteOrange
import com.zestyy.bytetrack.ui.theme.TextSecondary
import com.zestyy.bytetrack.ui.theme.VoidBlack

@Composable
fun PermissionsScreen(onGranted: () -> Unit) {
    val context = LocalContext.current
    val permissionsRepo = remember(context) { PermissionsRepository(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "byte!track",
            style = MaterialTheme.typography.displayLarge,
            color = ByteOrange,
            fontWeight = FontWeight.Bold
        )
        androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
        Text(
            "One permission unlocks everything",
            style = MaterialTheme.typography.titleMedium,
            color = TextSecondary
        )

        androidx.compose.foundation.layout.Spacer(Modifier.height(32.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    "Usage Access needed",
                    style = MaterialTheme.typography.titleLarge,
                    color = com.zestyy.bytetrack.ui.theme.TextPrimary
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                Text(
                    "byte!track reads per-app data usage and foreground time from Android's system usage stats. This requires the Usage Access permission, which Android makes you grant manually in Settings — there's no in-app popup for it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { context.startActivity(permissionsRepo.usageAccessSettingsIntent()) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Usage Access Settings")
                }
            }
        }

        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))

        Button(onClick = { if (permissionsRepo.hasUsageAccess()) onGranted() }) {
            Text("I've granted it — continue")
        }
    }
}
