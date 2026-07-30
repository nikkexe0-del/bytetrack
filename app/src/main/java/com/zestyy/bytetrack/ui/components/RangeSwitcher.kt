package com.zestyy.bytetrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zestyy.bytetrack.ui.theme.ByteOrange
import com.zestyy.bytetrack.ui.theme.GlassWhite08
import com.zestyy.bytetrack.ui.theme.GlassWhite12
import com.zestyy.bytetrack.ui.theme.TextPrimary
import com.zestyy.bytetrack.ui.theme.TextTertiary
import com.zestyy.bytetrack.util.RangeType

/**
 * Lets the user switch between Day / Week / Month / Year granularity and page back and forth
 * through history within whichever granularity is selected. The `›` chevron disables itself
 * once you're viewing the current, still-in-progress period - there's nothing to page forward
 * into beyond "now".
 */
@Composable
fun RangeSwitcher(
    selected: RangeType,
    label: String,
    canGoNext: Boolean,
    onTypeSelected: (RangeType) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(GlassWhite08),
        ) {
            RangeType.entries.forEach { type ->
                val isSelected = type == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .then(if (isSelected) Modifier.background(ByteOrange) else Modifier)
                        .clickable { onTypeSelected(type) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        type.displayName(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) TextPrimary else TextTertiary,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavChevron(icon = Icons.Filled.ChevronLeft, enabled = true, onClick = onPrevious)

            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )

            NavChevron(icon = Icons.Filled.ChevronRight, enabled = canGoNext, onClick = onNext)
        }
    }
}

@Composable
private fun NavChevron(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) GlassWhite12 else GlassWhite08)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) TextPrimary else TextTertiary,
        )
    }
}

private fun RangeType.displayName(): String = when (this) {
    RangeType.DAY -> "Day"
    RangeType.WEEK -> "Week"
    RangeType.MONTH -> "Month"
    RangeType.YEAR -> "Year"
}
