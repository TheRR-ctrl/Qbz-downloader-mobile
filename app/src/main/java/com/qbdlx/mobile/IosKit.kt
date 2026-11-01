package com.qbdlx.mobile

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.dp

/** Small iOS-looking controls for the iPod section (hand-made: no extra library). */
val IosGreen = Color(0xFF34C759)

/** UISwitch: green when on, grey when off, white thumb with no icon or outline. */
@Composable
fun IosSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    Switch(
        checked, onCheckedChange, modifier, enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White, checkedTrackColor = IosGreen, checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0x5578788F), uncheckedBorderColor = Color.Transparent,
        ),
    )

/** The round selection mark of iOS lists: an empty ring, or a filled blue circle with a check. */
@Composable
fun IosCheck(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val tint = MaterialTheme.colorScheme.primary
    val bg by animateColorAsState(if (checked) tint else Color.Transparent, tween(160), label = "iosCheck")
    Box(
        modifier.size(48.dp).clickable(onClick = { onCheckedChange(!checked) }, indication = null, interactionSource = null),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(bg)
                .then(if (checked) Modifier else Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)),
            contentAlignment = Alignment.Center,
        ) { if (checked) Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = Color.White) }
    }
}

/** iOS "tinted" button: soft blue fill, blue text, no outline. */
@Composable
fun IosTintButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val c = MaterialTheme.colorScheme.primary
    Button(
        onClick, modifier, enabled, shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = c.copy(alpha = .15f), contentColor = c, disabledContainerColor = c.copy(alpha = .06f), disabledContentColor = c.copy(alpha = .4f)),
        elevation = null, content = content,
    )
}
