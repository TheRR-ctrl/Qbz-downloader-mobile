package com.qbdlx.mobile

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// The two brand colours. Cyan marks what is selected or active and the main controls; gold is the accent
// (the active tab's label, section titles, Hi-Res).
val Cyan = Color(0xFF7FD6E8)
val Gold = Color(0xFFE5B800)
val Panel = Color(0xFF0D1319)

/** Dark teal for text and icons on top of [Cyan]; Material's default there is a purple that clashes. */
private val OnCyan = Color(0xFF00363F)
private val OnGold = Color(0xFF3A2D00)
/** The tinted pill behind a selected tab or chip. */
private val CyanContainer = Color(0xFF0E3A44)

// Light theme: the pale cyan and gold above are unreadable on white, so it gets darker shades of the same two colours.
private val CyanLight = Color(0xFF006B7D)
private val CyanContainerLight = Color(0xFFBDEBF5)
private val OnCyanContainerLight = Color(0xFF003640)
private val GoldLight = Color(0xFF7A5C00)

/** Height of the translucent bottom bar (mini player + tabs); lists pad their end by it so nothing hides behind. */
val LocalBottomInset = compositionLocalOf { 0.dp }

@Composable
fun QbdlxTheme(amoled: Boolean, content: @Composable () -> Unit) {
    val dark = amoled || isSystemInDarkTheme()
    val scheme = when {
        dark -> darkColorScheme(
            primary = Cyan, onPrimary = OnCyan,
            primaryContainer = CyanContainer, onPrimaryContainer = Cyan,
            secondary = Gold, onSecondary = OnGold,
            secondaryContainer = CyanContainer, onSecondaryContainer = Cyan,
            tertiary = Gold, onTertiary = OnGold,
        ).let { if (amoled) it.copy(background = Color.Black, surface = Color.Black, surfaceVariant = Panel) else it }
        else -> lightColorScheme(
            primary = CyanLight, onPrimary = Color.White,
            primaryContainer = CyanContainerLight, onPrimaryContainer = OnCyanContainerLight,
            secondary = GoldLight, onSecondary = Color.White,
            secondaryContainer = CyanContainerLight, onSecondaryContainer = OnCyanContainerLight,
            tertiary = GoldLight, onTertiary = Color.White,
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
