package com.qbdlx.lyrics

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** The look shared by the QBDLX apps: dark grey surfaces, gold accent, rounded cards and pill buttons (Qobuz-like). */
val QobuzGold = Color(0xFFFFC555)
private val CardGrey = Color(0xFF262626)

fun qobuzDarkScheme(): ColorScheme = darkColorScheme(
    primary = QobuzGold, onPrimary = Color(0xFF2B1F00), primaryContainer = Color(0xFF3B2F12), onPrimaryContainer = QobuzGold,
    secondaryContainer = Color.White, onSecondaryContainer = Color.Black,
    background = Color(0xFF111111), surface = Color(0xFF111111), onBackground = Color.White, onSurface = Color.White,
    surfaceVariant = CardGrey, surfaceContainer = CardGrey, surfaceContainerHigh = Color(0xFF303030),
    onSurfaceVariant = Color(0xFFB8B8B8), outline = Color(0xFF8A8A8A), outlineVariant = Color(0xFF333333),
)

/** Emil Kowalski's strong ease-out: quick start, soft landing. */
val UiEaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** A grouped section: rounded card with even spacing between its children. */
@Composable
fun QCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark()) 1f else .6f)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }

@Composable
private fun isDark() = MaterialTheme.colorScheme.background.red < .5f

@Composable
private fun pressScale(src: MutableInteractionSource): Modifier {
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.97f else 1f, tween(160, easing = UiEaseOut), label = "press")
    return Modifier.graphicsLayer { scaleX = s; scaleY = s }
}

/** Filled pill button (gold in dark mode). Presses scale to 0.97 like the main app. */
@Composable
fun PillButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val src = remember { MutableInteractionSource() }
    Button(onClick, modifier.then(pressScale(src)), enabled, shape = RoundedCornerShape(50), interactionSource = src, content = content)
}

/** Quiet pill button: soft grey fill, no outline. */
@Composable
fun PillTonal(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val src = remember { MutableInteractionSource() }
    Button(
        onClick, modifier.then(pressScale(src)), enabled, shape = RoundedCornerShape(50), interactionSource = src,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f), contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .06f), disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .38f),
        ),
        elevation = null, content = content,
    )
}
