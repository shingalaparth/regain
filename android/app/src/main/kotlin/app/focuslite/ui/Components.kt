package app.focuslite.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import app.focuslite.ui.theme.extra
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface as M3Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.composed
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** The app's real launcher icon from this phone, or null when none of [packages] is installed. */
@Composable
fun rememberAppIcon(packages: Set<String>): ImageBitmap? {
    val context = LocalContext.current
    return remember(packages) {
        packages.firstNotNullOfOrNull { pkg ->
            try {
                context.packageManager.getApplicationIcon(pkg).toBitmap(192, 192).asImageBitmap()
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
    }
}

/** A platform's real icon, rounded. */
@Composable
fun PlatformIcon(platform: Platform, packages: Set<String>, size: Dp, modifier: Modifier = Modifier) {
    val icon = rememberAppIcon(packages)
    val shape = RoundedCornerShape(size * 0.24f)
    if (icon != null) {
        Image(
            icon, contentDescription = platform.label,
            modifier = modifier.size(size).clip(shape).border(1.dp, MaterialTheme.extra.hairline, shape),
        )
    } else {
        Box(modifier.size(size).clip(shape).background(platform.tile), contentAlignment = Alignment.Center) {
            Text(platform.glyph, color = platform.onTile, fontSize = (size.value * 0.38f).sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

/** A grey rounded card, the basic container used everywhere. */
@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    M3Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.extra.hairline),
        modifier = modifier.fillMaxWidth(),
        content = content,
    )
}

@Composable
fun CardColumn(modifier: Modifier = Modifier, spacing: Dp = 12.dp, content: @Composable () -> Unit) {
    Card(modifier) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(spacing)) { content() }
    }
}

@Composable
fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun BodyMuted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A − value + control for numbers and clock times. */
@Composable
fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, enabled: Boolean = true) {
    val haptic = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick); onMinus() },
            enabled = enabled, modifier = Modifier.size(48.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp), shape = CircleShape,
        ) { Text("−", fontSize = 20.sp) }
        Text(value, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 12.dp))
        OutlinedButton(
            onClick = { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick); onPlus() },
            enabled = enabled, modifier = Modifier.size(48.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp), shape = CircleShape,
        ) { Text("+", fontSize = 20.sp) }
    }
}

fun clockText(minuteOfDay: Int): String =
    LocalTime.of(Math.floorMod(minuteOfDay, 1440) / 60, Math.floorMod(minuteOfDay, 1440) % 60)
        .format(DateTimeFormatter.ofPattern("h:mm a"))

/** "12:05" for a countdown. */
fun countdownText(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun Spacer(h: Dp) = Spacer(Modifier.size(h))

/**
 * A title with an optional line under it and a switch, as ONE touch target: tapping the words toggles it too.
 * Gives a small tick of haptic feedback, the only place besides steppers that vibrates.
 */
@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch) {
                haptic.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                onChange(it)
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) BodyMuted(subtitle)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Presses shrink the element a little (0.96 over 150 ms) so taps feel physical. Never changes layout size. */
fun Modifier.pressScale(onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(150), label = "press")
    this.scale(scale).clickable(interactionSource = source, indication = null, onClick = onClick)
}
