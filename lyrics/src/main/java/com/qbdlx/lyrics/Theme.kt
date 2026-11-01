package com.qbdlx.lyrics

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils

/** A colour scheme built around [seed] (an ARGB colour, 0 for the default one), so the screen follows the playing app's colour. */
fun schemeFor(seed: Int, dark: Boolean): ColorScheme {
    if (seed == 0) return if (dark) darkColorScheme() else lightColorScheme()
    val hsl = FloatArray(3).also { ColorUtils.colorToHSL(seed, it) }
    val h = hsl[0]; val s = hsl[1].coerceIn(0.35f, 1f)
    fun c(sat: Float, light: Float) = Color.hsl(h, sat.coerceIn(0f, 1f), light)
    return if (dark) darkColorScheme(
        primary = c(s, 0.72f), onPrimary = c(s, 0.1f), background = c(s * 0.5f, 0.08f), surface = c(s * 0.5f, 0.08f),
        onBackground = Color(0xFFF2F2F2), onSurface = Color(0xFFF2F2F2), onSurfaceVariant = c(0.2f, 0.72f),
        secondaryContainer = c(s * 0.5f, 0.2f), outline = c(0.2f, 0.5f),
    ) else lightColorScheme(
        primary = c(s, 0.35f), onPrimary = Color.White, background = c(s * 0.6f, 0.96f), surface = c(s * 0.6f, 0.96f),
        onSurfaceVariant = c(0.2f, 0.35f),
    )
}
