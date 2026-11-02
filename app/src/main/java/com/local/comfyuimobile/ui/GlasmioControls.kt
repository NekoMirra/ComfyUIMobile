package com.local.comfyuimobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.glass.GlassEmphasis
import io.github.neoruaa.glasmio.glass.GlasmioToolbarActionStyle
import io.github.neoruaa.glasmio.icons.GlasmioIcon
import io.github.neoruaa.glasmio.icons.GlasmioIconProvider
import io.github.neoruaa.glasmio.ui.GlasmioButton
import io.github.neoruaa.glasmio.ui.GlasmioButtonSize
import io.github.neoruaa.glasmio.ui.GlasmioIconButton
import io.github.neoruaa.glasmio.ui.GlasmioSegmentedControl
import io.github.neoruaa.glasmio.ui.GlasmioTextField
import io.github.neoruaa.glasmio.ui.GlasmioTopBar

/**
 * Installs the Glasmio icon resolver for the whole subtree.
 *
 * Without a licensed SF Symbols font the bundled Material resolver maps the semantic symbol names
 * to Material icons, so the app renders icons without owning any font asset.
 */
@Composable
fun GlasmioIconHost(content: @Composable () -> Unit) {
    GlasmioIconProvider { content() }
}

/**
 * Primary call-to-action button.
 */
@Composable
fun GlasmioPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String,
) {
    GlasmioButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        emphasis = GlassEmphasis.Prominent,
        size = GlasmioButtonSize.Medium,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Secondary button, rendered with the regular glass emphasis.
 */
@Composable
fun GlasmioSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String,
) {
    GlasmioButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        emphasis = GlassEmphasis.Regular,
        size = GlasmioButtonSize.Medium,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Icon-only action, for example a toolbar button.
 */
@Composable
fun GlasmioIconAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String?,
    icon: ImageVector,
    tint: Color = Color.Unspecified,
) {
    GlasmioIconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
            tint = tint,
        )
    }
}

/**
 * Segmented choice row. Mirrors the existing `StudioChoices` call shape so a screen can be
 * migrated by swapping the composable name.
 */
@Composable
fun GlasmioSegmentedChoice(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = labels.mapIndexed { index, label -> index to label }
    GlasmioSegmentedControl(
        items = items,
        selected = selected,
        onSelected = onSelect,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Small status badge. Mirrors the existing `StudioBadge` call shape.
 */
@Composable
fun GlasmioBadge(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = GlasmioTheme.colors.accent,
) {
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.12f), GlasmioTheme.shapes.extraSmall)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

/**
 * Top toolbar with a title and a trailing action slot.
 */
@Composable
fun GlasmioToolbarBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    GlasmioTopBar(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        actions = actions,
        actionsStyle = GlasmioToolbarActionStyle.Merged,
    )
}

/**
 * Text input field. Mirrors the Material3 `OutlinedTextField` call shape.
 */
@Composable
fun GlasmioTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    GlasmioTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        enabled = enabled,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
    )
}

/**
 * Horizontal icon plus label, used by toolbar action slots.
 */
@Composable
fun GlasmioLabeledAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * Semantic symbol icon rendered through the Glasmio icon resolver.
 */
@Composable
fun GlasmioSymbolIcon(
    systemName: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    size: Dp = 22.dp,
) {
    GlasmioIcon(
        systemName = systemName,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
        size = size,
    )
}
