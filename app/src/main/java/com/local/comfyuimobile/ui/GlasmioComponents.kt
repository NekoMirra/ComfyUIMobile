package com.local.comfyuimobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonRole
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonSpec
import io.github.neoruaa.glasmio.ui.GlasmioBottomSheet
import io.github.neoruaa.glasmio.ui.GlasmioGroupedList
import io.github.neoruaa.glasmio.ui.GlasmioSectionTitle
import io.github.neoruaa.glasmio.ui.GlasmioSwitch
import io.github.neoruaa.glasmio.ui.GlasmioAlertDialog as GlasmioNativeAlertDialog
import io.github.neoruaa.glasmio.ui.GlasmioAlertSurface as GlasmioNativeAlertSurface
import io.github.neoruaa.glasmio.ui.GlasmioListRow as GlasmioNativeListRow

/**
 * Grouped list section. Mirrors the existing `SettingsGroup` call shape.
 *
 * Outside a backdrop host the section falls back to a plain Material3 column with the same
 * section title tokens.
 */
@Composable
fun GlasmioListSection(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) {
            if (LocalGlasmioHosted.current) {
                GlasmioSectionTitle(title)
            } else {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = glasmioColors().secondaryContent,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
        if (LocalGlasmioHosted.current) {
            GlasmioGroupedList(modifier = Modifier.fillMaxWidth(), content = content)
        } else {
            content()
        }
    }
}

/**
 * List row with an optional leading icon and a trailing slot. Mirrors the existing
 * `SettingsLink` and `SettingsToggle` call shapes.
 *
 * Outside a backdrop host the row falls back to a Material3 row with the same tokens.
 */
@Composable
fun GlasmioListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    detail: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    if (LocalGlasmioHosted.current) {
        GlasmioNativeListRow(
            title = title,
            modifier = modifier,
            subtitle = subtitle,
            detail = detail,
            leading = icon?.let { vector ->
                {
                    Icon(
                        imageVector = vector,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = GlasmioTheme.colors.accent,
                    )
                }
            },
            onClick = onClick,
            trailing = trailing,
        )
    } else {
        val glass = glasmioColors()
        Row(
            modifier = modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = glass.accent,
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = glass.content)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = glass.secondaryContent)
                }
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.labelSmall, color = glass.tertiaryContent)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
    }
}

/**
 * Switch row for the settings pages.
 *
 * Outside a backdrop host the row falls back to a Material3 row with the Material3 switch.
 */
@Composable
fun GlasmioToggleRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalGlasmioHosted.current) {
        GlasmioListRow(
            title = title,
            modifier = modifier,
            subtitle = summary,
            trailing = {
                GlasmioSwitch(checked = checked, onCheckedChange = onCheckedChange)
            },
        )
    } else {
        val glass = glasmioColors()
        Row(
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = glass.content)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = glass.secondaryContent)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

/**
 * Bottom sheet host.
 *
 * Outside a backdrop host the sheet falls back to the Material3 bottom sheet, because the Glasmio
 * sheet samples a backdrop that a separate window does not have.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlasmioSheetHost(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "",
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalGlasmioHosted.current) {
        GlasmioBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            visible = visible,
            title = title,
            content = content,
        )
    } else {
        ModalBottomSheet(onDismissRequest = onDismissRequest, modifier = modifier) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                if (title.isNotBlank()) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
                content()
            }
        }
    }
}

/**
 * Alert dialog with one primary and one cancel action.
 *
 * Outside a backdrop host the dialog falls back to the Material3 alert with the same wording and
 * button order.
 */
@Composable
fun GlasmioAlertDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    message: String? = null,
    confirmText: String = "确定",
    dismissText: String = "取消",
    destructive: Boolean = false,
    onConfirm: () -> Unit,
) {
    if (!visible) return
    if (LocalGlasmioHosted.current) {
        GlasmioNativeAlertDialog(
            onDismissRequest = onDismissRequest,
            title = title,
            message = message,
            visible = true,
            buttons = listOf(
                GlasmioAlertButtonSpec(
                    label = dismissText,
                    role = GlasmioAlertButtonRole.Cancel,
                    onClick = onDismissRequest,
                ),
                GlasmioAlertButtonSpec(
                    label = confirmText,
                    role = if (destructive) GlasmioAlertButtonRole.Destructive else GlasmioAlertButtonRole.Default,
                    onClick = onConfirm,
                ),
            ),
        )
    } else {
        GlasmioMaterialAlertDialog(
            onDismissRequest = onDismissRequest,
            title = title,
            message = message,
            confirmText = confirmText,
            dismissText = dismissText,
            onConfirm = onConfirm,
        )
    }
}

/**
 * Alert surface rendered inline in the current window instead of in a separate dialog window.
 *
 * A dialog or a bottom sheet lives in its own window, so the page backdrop is not sampled by the
 * alert. Inlining the alert keeps the content in the same window, where the glass material has a
 * backdrop to sample. Use it for confirmations that belong to a page.
 *
 * Outside a backdrop host the alert falls back to a Material3 surface with the same tokens.
 */
@Composable
fun GlasmioInlineAlert(
    modifier: Modifier = Modifier,
    title: String,
    message: String? = null,
    confirmText: String = "确定",
    dismissText: String = "取消",
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    if (LocalGlasmioHosted.current) {
        GlasmioNativeAlertSurface(
            modifier = modifier,
            title = title,
            message = message,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlasmioSecondaryButton(onClick = onDismissRequest, text = dismissText)
                GlasmioPrimaryButton(onClick = onConfirm, text = confirmText)
            }
        }
    } else {
        val glass = glasmioColors()
        Surface(
            modifier = modifier,
            shape = glasmioShapes().large,
            color = glass.container,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = glass.content)
                if (message != null) {
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = glass.secondaryContent)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlasmioSecondaryButton(onClick = onDismissRequest, text = dismissText)
                    GlasmioPrimaryButton(onClick = onConfirm, text = confirmText)
                }
            }
        }
    }
}

/**
 * Material3 alert used when the Glasmio alert cannot be hosted, for example in a Robolectric
 * environment without a backdrop host. It keeps the same wording and button order.
 */
@Composable
fun GlasmioMaterialAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    message: String? = null,
    confirmText: String = "确定",
    dismissText: String = "取消",
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = { if (message != null) Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismissRequest) { Text(dismissText) } },
    )
}
