package com.local.comfyuimobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Segmented choice row.
 *
 * The Glasmio segmented control provides the glass capsule, the selected thumb and the
 * accessibility semantics, so this only forwards to it.
 */
@Composable
internal fun StudioChoices(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    GlasmioSegmentedChoice(
        labels = labels,
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
    )
}

/**
 * Small status badge.
 *
 * The colour defaults to the Glasmio accent so the badge reads correctly in light and dark mode.
 */
@Composable
internal fun StudioBadge(label: String, modifier: Modifier = Modifier, color: Color = glasmioColors().accent) {
    GlasmioBadge(
        label = label,
        modifier = modifier,
        color = color,
    )
}
