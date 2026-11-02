package com.local.comfyuimobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import io.github.neoruaa.glasmio.ui.GlasmioListRow as GlasmioNativeListRow

/**
 * Grouped list section. Mirrors the existing `SettingsGroup` call shape.
 */
@Composable
fun GlasmioListSection(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) {
            GlasmioSectionTitle(title)
        }
        GlasmioGroupedList(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

/**
 * List row with an optional leading icon and a trailing slot. Mirrors the existing
 * `SettingsLink` and `SettingsToggle` call shapes.
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
}

/**
 * Switch row for the settings pages.
 */
@Composable
fun GlasmioToggleRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlasmioListRow(
        title = title,
        modifier = modifier,
        subtitle = summary,
        trailing = {
            GlasmioSwitch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
}

/**
 * Bottom sheet host.
 */
@Composable
fun GlasmioSheetHost(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "",
    content: @Composable ColumnScope.() -> Unit,
) {
    GlasmioBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        visible = visible,
        title = title,
        content = content,
    )
}

/**
 * Alert dialog with one primary and one cancel action.
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
