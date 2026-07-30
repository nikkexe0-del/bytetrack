package com.zestyy.bytetrack.ui.theme

import androidx.compose.ui.graphics.Color

// Brand
val ByteOrange = Color(0xFFFF6A1A)
val ByteOrangeBright = Color(0xFFFF8C42)
val ByteOrangeDim = Color(0xFFB84E12)

// Base surfaces (deep black, not pure #000 so glass blur has something to grab)
val VoidBlack = Color(0xFF0A0A0B)
val CarbonBlack = Color(0xFF141416)
val SlateBlack = Color(0xFF1C1C1F)

// Glass tints layered over the blur for the "liquid glass" look. Biased dark-and-translucent
// rather than light-and-translucent: a real backdrop blur samples whatever's behind it (which
// can be a bright gradient), so a light tint risks washing out text contrast unpredictably. A
// dark scrim guarantees TextPrimary/TextSecondary stay legible no matter what's blurred behind.
val GlassScrim = Color(0x8A141416)
val GlassWhite12 = Color(0x1FFFFFFF)
val GlassWhite08 = Color(0x0DFFFFFF)
val GlassOrange18 = Color(0x2EFF6A1A)
val GlassStroke = Color(0x33FFFFFF)

val TextPrimary = Color(0xFFF5F5F7)
val TextSecondary = Color(0xFFA0A0A6)
val TextTertiary = Color(0xFF6E6E76)

val Success = Color(0xFF34D399)
val Warning = Color(0xFFFBBF24)
val Danger = Color(0xFFF87171)

// Network category colors for charts
val WifiColor = Color(0xFF3AA6FF)
val MobileColor = ByteOrange
val HotspotColor = Color(0xFFB57BFF)
