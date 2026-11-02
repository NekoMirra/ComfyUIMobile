package com.local.comfyuimobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.emptyBackdrop
import io.github.neoruaa.glasmio.icons.GlasmioIcon
import io.github.neoruaa.glasmio.icons.GlasmioIconProvider
import io.github.neoruaa.glasmio.ui.GlasmioButton
import io.github.neoruaa.glasmio.ui.GlasmioButtonSize
import io.github.neoruaa.glasmio.ui.GlasmioIconButton
import io.github.neoruaa.glasmio.ui.GlasmioSegmentedControl
import io.github.neoruaa.glasmio.ui.GlasmioTextField
import io.github.neoruaa.glasmio.ui.GlasmioTopBar
import io.github.neoruaa.glasmio.ui.GlasmioCard as GlasmioNativeCard
import io.github.neoruaa.glasmio.ui.GlasmioListValuePill as GlasmioNativeValuePill

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
 *
 * Outside a backdrop host the button falls back to the Material3 form, so a control rendered in
 * its own window keeps working.
 */
@Composable
fun GlasmioPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String,
) {
    if (LocalGlasmioHosted.current) {
        GlasmioButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            emphasis = GlassEmphasis.Prominent,
            size = GlasmioButtonSize.Medium,
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    } else {
        Button(onClick = onClick, modifier = modifier, enabled = enabled) {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Secondary button, rendered with the regular glass emphasis.
 *
 * Outside a backdrop host the button falls back to the Material3 form.
 */
@Composable
fun GlasmioSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String,
) {
    if (LocalGlasmioHosted.current) {
        GlasmioButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            emphasis = GlassEmphasis.Regular,
            size = GlasmioButtonSize.Medium,
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Icon-only action, for example a toolbar button.
 *
 * Outside a backdrop host the action falls back to the Material3 form.
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
    if (LocalGlasmioHosted.current) {
        GlasmioIconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(22.dp),
                tint = tint,
            )
        }
    } else {
        IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(22.dp),
                tint = tint,
            )
        }
    }
}

/**
 * Glass card. Mirrors the Material3 `Card` call shape.
 *
 * A card is a glass surface, so it must live inside the Glasmio backdrop host. Outside a host the
 * card falls back to the Material3 surface with the same container colour and corner radius.
 *
 * [sampleBackdrop] controls whether the card refracts the page backdrop. Turn it off for surfaces
 * that cover a large area or that host an animating progress bar: such a surface would otherwise
 * sample its own pixels, which smears the content and lags behind the animation.
 */
@Composable
fun GlasmioCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    sampleBackdrop: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    if (LocalGlasmioHosted.current) {
        GlasmioNativeCard(
            modifier = modifier,
            backdrop = if (sampleBackdrop) LocalGlassBackdrop.current else emptyBackdrop(),
            onClick = onClick,
            content = content,
        )
    } else {
        Surface(
            modifier = modifier.then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            ),
            shape = glasmioShapes().large,
            color = glasmioColors().container,
        ) {
            Box { content() }
        }
    }
}

/**
 * Value pill shown at the trailing edge of a list row. Mirrors the Material3 `AssistChip` shape.
 *
 * Outside a backdrop host the pill falls back to a Material3 surface with the same tokens.
 */
@Composable
fun GlasmioValuePill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (LocalGlasmioHosted.current) {
        GlasmioNativeValuePill(
            text = text,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        )
    } else {
        Surface(
            modifier = modifier.clickable(enabled = enabled, onClick = onClick),
            shape = glasmioShapes().extraSmall,
            color = glasmioColors().container,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = glasmioColors().content,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * Segmented choice row. Mirrors the existing `StudioChoices` call shape so a screen can be
 * migrated by swapping the composable name.
 *
 * Outside a backdrop host the row falls back to Material3 text buttons with the same tokens.
 */
@Composable
fun GlasmioSegmentedChoice(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalGlasmioHosted.current) {
        val items = labels.mapIndexed { index, label -> index to label }
        GlasmioSegmentedControl(
            items = items,
            selected = selected,
            onSelected = onSelect,
            modifier = modifier.fillMaxWidth(),
        )
    } else {
        val glass = glasmioColors()
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            labels.forEachIndexed { index, label ->
                TextButton(
                    onClick = { onSelect(index) },
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (index == selected) glass.prominentContainer else Color.Transparent,
                        contentColor = if (index == selected) glass.content else glass.secondaryContent,
                    ),
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
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
 *
 * Outside a backdrop host the toolbar falls back to a Material3 surface with the same tokens.
 */
@Composable
fun GlasmioToolbarBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (LocalGlasmioHosted.current) {
        GlasmioTopBar(
            title = title,
            modifier = modifier,
            subtitle = subtitle,
            actions = actions,
            actionsStyle = GlasmioToolbarActionStyle.Merged,
        )
    } else {
        val glass = glasmioColors()
        Surface(modifier = modifier, color = glass.elevatedBackground) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = glass.content)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = glass.secondaryContent)
                    }
                }
                actions()
            }
        }
    }
}

/**
 * Text input field. Mirrors the Material3 `OutlinedTextField` call shape.
 *
 * Outside a backdrop host the field falls back to the Material3 outlined text field.
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
    if (LocalGlasmioHosted.current) {
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
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            placeholder = { Text(placeholder) },
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
        )
    }
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
