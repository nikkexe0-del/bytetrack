package com.zestyy.bytetrack.ui.components

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zestyy.bytetrack.ui.theme.ByteOrange
import com.zestyy.bytetrack.ui.theme.GlassScrim
import com.zestyy.bytetrack.ui.theme.GlassStroke
import com.zestyy.bytetrack.ui.theme.GlassWhite08

/**
 * The "Liquid Glass" acrylic surface used across byte!track.
 *
 * IMPORTANT structural point: the blur is applied ONLY to a background [Box] sitting behind
 * [content] — never to content itself. An earlier version of this component put the blur
 * graphicsLayer on the same Box that held the text/icons, which blurred the actual UI content,
 * not just what's visually behind the card. Real backdrop glass keeps foreground content sharp
 * while blurring what's underneath it.
 *
 * How the blur works:
 *  - API 31+ (Android 12+): a real [android.graphics.RenderEffect] backdrop blur is applied to
 *    the background layer via graphicsLayer — genuine iOS-style glassmorphism.
 *  - Below API 31: RenderEffect isn't available, so the background falls back to a layered
 *    translucent scrim + soft highlight. Reads "glassy" without the real distortion.
 *
 * Layer something visually rich behind this card (a gradient mesh, blurred wallpaper, or
 * scrolling content) for the blur to actually have something to distort — a flat background
 * makes the effect invisible either way.
 *
 * `tint` defaults to a dark scrim (not a light one) specifically so foreground text stays
 * legible no matter what colors get sampled from whatever's blurred behind the card.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    tint: Color = GlassScrim,
    borderColor: Color = GlassStroke,
    blurRadius: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 20.dp,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    // Fluid micro-interaction: subtle scale + glow on press, spring back on release
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "glassCardScale"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.35f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "glassCardGlow"
    )

    val backdropBlurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.graphicsLayer {
            renderEffect = android.graphics.RenderEffect
                .createBlurEffect(blurRadius.toPx(), blurRadius.toPx(), android.graphics.Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
            clip = true
        }
    } else Modifier

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = rememberRipple(color = ByteOrange, bounded = true),
                    onClick = onClick
                ) else Modifier
            )
    ) {
        // --- Background layer: this is the ONLY thing that gets blurred ---
        // matchParentSize() (not fillMaxSize()) is required here: it sizes this layer to match
        // whatever the Box resolves to based on the foreground content, without itself forcing
        // the Box to fill all available space - using fillMaxSize() here would crash with an
        // infinite-height constraint the moment this card sits inside an unbounded-height
        // parent like a LazyColumn item.
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(backdropBlurModifier)
                .background(
                    Brush.linearGradient(
                        colors = listOf(tint, tint.copy(alpha = tint.alpha * 0.6f)),
                        start = Offset(0f, 0f),
                        end = Offset(400f, 400f)
                    )
                )
                // faux acrylic sheen: soft diagonal highlight, like light hitting frosted glass
                .background(
                    Brush.linearGradient(
                        colors = listOf(GlassWhite08, Color.Transparent, Color.Transparent),
                        start = Offset(0f, 0f),
                        end = Offset(300f, 600f)
                    )
                )
                // ambient glow on press to sell the "liquid" reactivity
                .background(ByteOrange.copy(alpha = glowAlpha * 0.15f))
                .border(1.dp, borderColor, shape)
        )

        // --- Foreground layer: real content, always sharp, never touched by blur ---
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}
