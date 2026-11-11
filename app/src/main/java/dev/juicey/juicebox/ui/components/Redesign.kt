package dev.juicey.juicebox.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.juicey.juicebox.core.model.LinkVariant
import dev.juicey.juicebox.ui.formatTtl
import dev.juicey.juicebox.ui.formatTtlShort
import dev.juicey.juicebox.ui.icons.JuiceboxIcons
import dev.juicey.juicebox.ui.icons.juiceboxIcon
import dev.juicey.juicebox.ui.theme.JuiceOrange
import dev.juicey.juicebox.ui.theme.TitleFont
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The Juicebox wordmark: the site's pixel title face in caps, with the
 * same heavy black keyline as the box icon.
 */
@Composable
fun TitleWordmark(
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
    color: Color = JuiceOrange,
) {
    val outline = with(LocalDensity.current) { 3.dp.toPx() }
    val base = style.copy(
        fontFamily = TitleFont,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    )
    Box(modifier) {
        Text(
            text = "JUICEBOX2",
            style = base.copy(drawStyle = Stroke(width = outline)),
            color = Color.Black,
            maxLines = 1,
        )
        Text(
            text = "JUICEBOX2",
            style = base,
            color = color,
            maxLines = 1,
        )
    }
}

/** A soft sweep used while a thumbnail or preview is still loading. */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1150, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-shift",
    )
    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                val width = size.width.coerceAtLeast(1f)
                val start = shift * width - width * 0.6f
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(color, color.copy(alpha = 0.45f), color),
                        start = Offset(start, 0f),
                        end = Offset(start + width * 1.2f, size.height.coerceAtLeast(1f)),
                    ),
                )
            },
    )
}

/** Stock Material 3 slider over the server's allowed retention stops. */
@Composable
fun RetentionSlider(
    values: List<Double>,
    selected: Double,
    onSelect: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (values.isEmpty()) return
    val haptics = LocalJuiceHaptics.current
    val index = values.indexOf(selected).takeIf { it >= 0 }
        ?: values.indices.minByOrNull { abs(values[it] - selected) }
        ?: 0

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Retention",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                formatTtl(values[index]),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (values.size > 1) {
            Slider(
                value = index.toFloat(),
                onValueChange = { raw ->
                    val next = raw.roundToInt().coerceIn(0, values.lastIndex)
                    if (next != index) {
                        haptics.select()
                        onSelect(values[next])
                    }
                },
                valueRange = 0f..values.lastIndex.toFloat(),
                steps = (values.size - 2).coerceAtLeast(0),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Spacer(Modifier.height(4.dp))
        }
        // Slider ticks sit at i/(n-1) across the track, so place each label's
        // centre on the same fractions instead of giving labels equal widths.
        Layout(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            content = {
                values.forEachIndexed { stopIndex, value ->
                    Text(
                        formatTtlShort(value),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = if (stopIndex == index) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        },
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            },
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(Constraints()) }
            val width = constraints.maxWidth
            val height = placeables.maxOfOrNull { it.height } ?: 0
            layout(width, height) {
                placeables.forEachIndexed { stopIndex, placeable ->
                    val fraction = if (placeables.size == 1) {
                        0.5f
                    } else {
                        stopIndex / (placeables.size - 1).toFloat()
                    }
                    val x = (width * fraction - placeable.width / 2f).roundToInt()
                        .coerceIn(0, (width - placeable.width).coerceAtLeast(0))
                    placeable.place(x, 0)
                }
            }
        }
    }
}

/** Preview / raw / download as a standard full-width segmented control. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkVariantSelector(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalJuiceHaptics.current
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        LinkVariant.all.forEachIndexed { index, variant ->
            SegmentedButton(
                selected = variant == selected,
                onClick = {
                    haptics.select()
                    onSelect(variant)
                },
                shape = SegmentedButtonDefaults.itemShape(index, LinkVariant.all.size),
                modifier = Modifier.weight(1f),
                label = {
                    Text(
                        LinkVariant.label(variant),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                },
            )
        }
    }
}

/** The whole row is the copy target; the icon is feedback, not a second button. */
@Composable
fun LinkCopyRow(
    link: String,
    onCopy: (String) -> Boolean,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    var copied by remember(link) { mutableStateOf(false) }
    val haptics = LocalJuiceHaptics.current
    Surface(
        onClick = {
            val ok = onCopy(link)
            if (ok) haptics.confirm() else haptics.reject()
            copied = ok
        },
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = if (label == null) 14.dp else 11.dp),
        ) {
            if (label != null) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(3.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = link,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    ),
                    color = if (copied) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    juiceboxIcon(if (copied) JuiceboxIcons.check else JuiceboxIcons.copy),
                    contentDescription = if (copied) "Copied" else "Copy link",
                    tint = if (copied) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** One grouped, tonal surface per settings submenu. */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(content = content)
    }
}

@Composable
fun SettingsNavRow(
    icon: Int,
    title: String,
    value: String? = null,
    onClick: () -> Unit,
) {
    val haptics = LocalJuiceHaptics.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptics.tick()
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            juiceboxIcon(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!value.isNullOrBlank()) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            juiceboxIcon(JuiceboxIcons.chevronRight),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    body: String? = null,
) {
    val haptics = LocalJuiceHaptics.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptics.toggle(!checked)
                onCheckedChange(!checked)
            }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!body.isNullOrBlank()) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = {
                haptics.toggle(it)
                onCheckedChange(it)
            },
        )
    }
}

@Composable
fun SettingsActionRow(
    icon: Int,
    title: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val haptics = LocalJuiceHaptics.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptics.tick()
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            juiceboxIcon(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (destructive) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun SettingsInfoRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SettingsChoiceRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    val haptics = LocalJuiceHaptics.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptics.select()
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            juiceboxIcon(JuiceboxIcons.chevronRight),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Label plus a dropdown; used for the compact media options. */
@Composable
fun SettingPicker(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val haptics = LocalJuiceHaptics.current
    val shown = options.firstOrNull { it.first == value }?.second ?: value
    // The menu hangs off the value chip at the row's end, so on a wide screen
    // it opens where the user tapped rather than back at the left margin.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box(contentAlignment = Alignment.TopEnd) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        haptics.tick()
                        open = true
                    }
                    .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    shown,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    juiceboxIcon(JuiceboxIcons.chevronDown),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                options.forEach { (key, text) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text,
                                color = if (key == value) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = {
                            haptics.select()
                            open = false
                            onSelect(key)
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    icon: Int,
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val haptics = LocalJuiceHaptics.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                juiceboxIcon(icon),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        },
        title = { Text(title) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = {
                if (destructive) haptics.reject() else haptics.confirm()
                onConfirm()
            }) {
                Text(confirmLabel, color = tint)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                haptics.tick()
                onDismiss()
            }) { Text("Cancel") }
        },
    )
}


