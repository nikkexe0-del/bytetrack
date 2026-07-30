package com.zestyy.bytetrack.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val ByteTrackColorScheme = darkColorScheme(
    primary = ByteOrange,
    onPrimary = Color.Black,
    secondary = ByteOrangeBright,
    background = VoidBlack,
    onBackground = TextPrimary,
    surface = CarbonBlack,
    onSurface = TextPrimary,
    surfaceVariant = SlateBlack,
    onSurfaceVariant = TextSecondary,
    error = Danger,
    outline = GlassStroke,
)

// iOS-shadcn style: generously rounded, consistent radius scale
val ByteTrackShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun ByteTrackTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as? android.app.Activity)?.window
        window?.let {
            it.statusBarColor = VoidBlack.toArgb()
            it.navigationBarColor = VoidBlack.toArgb()
            WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = ByteTrackColorScheme,
        typography = ByteTrackTypography,
        shapes = ByteTrackShapes,
        content = content
    )
}
