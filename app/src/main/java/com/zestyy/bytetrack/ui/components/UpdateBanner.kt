package com.zestyy.bytetrack.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zestyy.bytetrack.data.repository.UpdateInfo
import com.zestyy.bytetrack.ui.theme.ByteOrange
import com.zestyy.bytetrack.ui.theme.GlassOrange18
import com.zestyy.bytetrack.ui.theme.TextPrimary
import com.zestyy.bytetrack.ui.theme.TextSecondary
import com.zestyy.bytetrack.ui.theme.TextTertiary

/** Shown on the Dashboard when UpdateRepository finds a newer signed release - the whole point
 * is a one-tap path to updating instead of the user having to go find the APK on GitHub. */
@Composable
fun UpdateBanner(
    update: UpdateInfo,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier.fillMaxWidth(), tint = GlassOrange18) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Update available",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "byte!track v${update.versionName} is ready to download",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Later",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary,
                    modifier = Modifier.clickable(onClick = onDismiss),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    "Update",
                    style = MaterialTheme.typography.labelLarge,
                    color = ByteOrange,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onInstall),
                )
            }
        }
    }
}
