package com.zestyy.bytetrack.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zestyy.bytetrack.R
import com.zestyy.bytetrack.ui.theme.ByteOrange
import com.zestyy.bytetrack.ui.theme.TextSecondary
import com.zestyy.bytetrack.ui.theme.TextTertiary

/**
 * App credits footer: byte!track icon on the left, creator attribution + links on the right.
 * Meant to sit as the last item in a scrollable screen (Dashboard/Apps), not pinned chrome.
 */
@Composable
fun CreditsFooter(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    fun openUrl(url: String) {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
        context.startActivity(intent)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = "byte!track",
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
        )

        Column(horizontalAlignment = Alignment.End) {
            Text(
                "built and maintained by Nikshep Doggalli",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.padding(top = 3.dp))
            Text(
                "follow on instagram · instagram.com/nikkk.exe",
                style = MaterialTheme.typography.labelSmall,
                color = ByteOrange,
                modifier = Modifier.clickable { openUrl("https://instagram.com/nikkk.exe") }
            )
            Spacer(Modifier.padding(top = 2.dp))
            Text(
                "nikshep.vercel.app",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                modifier = Modifier.clickable { openUrl("https://nikshep.vercel.app") }
            )
        }
    }
}
