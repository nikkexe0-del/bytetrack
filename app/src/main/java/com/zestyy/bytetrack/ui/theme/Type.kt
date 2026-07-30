package com.zestyy.bytetrack.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zestyy.bytetrack.R

/*
 * Real Inter (OFL-licensed) is bundled as a single variable font at
 * res/font/inter_variable.ttf — no runtime download, no Play Services dependency.
 * Each weight below just dials the font's "wght" variation axis, so we get the full
 * Inter weight range from one ~850KB file instead of four separate static files.
 */
@OptIn(ExperimentalTextApi::class)
private fun interWeight(weight: Int, fontWeight: FontWeight) = Font(
    resId = R.font.inter_variable,
    weight = fontWeight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

val InterFontFamily = FontFamily(
    interWeight(400, FontWeight.Normal),
    interWeight(500, FontWeight.Medium),
    interWeight(600, FontWeight.SemiBold),
    interWeight(700, FontWeight.Bold),
)

// shadcn/ui-inspired scale: tight tracking on headings, generous line-height on body text
val ByteTrackTypography = Typography(
    displayLarge = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.1.sp),
    labelSmall = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.2.sp),
)
