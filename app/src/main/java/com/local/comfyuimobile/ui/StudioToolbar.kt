package com.local.comfyuimobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.local.comfyuimobile.bridge.FieldValidator
import com.local.comfyuimobile.model.AppUiState

/**
 * Text shadow used by the overlay text that sits on top of the glass chrome.
 *
 * The shadow colour is derived from the Glasmio palette, so it stays legible in light and dark
 * mode without a hardcoded black.
 */
@Composable
internal fun toolbarTextShadow() = Shadow(glasmioColors().content.copy(alpha = 0.28f), Offset(0f, 1.5f), 3f)

/**
 * Toolbar icon rendered with the Glasmio palette.
 *
 * The icon keeps the Material `ImageVector` overload: [GlasmioSymbolIcon] resolves semantic
 * SF-Symbol names, not vector resources.
 */
@Composable
internal fun FloatingToolbarIcon(vector: ImageVector, description: String?, modifier: Modifier = Modifier, tint: Color = glasmioMaterialColors().onSurfaceVariant) {
    Box(modifier) {
        Icon(vector, description, Modifier.size(24.dp), tint = tint)
    }
}

internal fun parameterProblemMessage(state: AppUiState): String? {
    val workflow = state.selectedWorkflow ?: return null
    val problem = FieldValidator.detailedProblems(state.fields).firstOrNull()
    val nodeId = problem?.nodeId ?: state.nodeProblems.entries.firstOrNull { it.value.isNotEmpty() }?.key ?: return null
    val message = problem?.message ?: state.nodeProblems[nodeId]?.firstOrNull() ?: return null
    val title = workflow.nodes.firstOrNull { it.id == nodeId }?.title ?: "节点 $nodeId"
    return "$title：$message"
}

/**
 * One feedback line under the toolbar title.
 *
 * The colours come from the Glasmio semantic palette: a failed run is destructive, a finished run
 * is a success and everything else is a warning tone, so the text reads the same in light and
 * dark mode.
 */
@Composable
private fun StudioChromeFeedback(
    message: String,
    icon: ImageVector,
    tint: Color,
    maxLines: Int,
    onDetails: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    progress: Float? = null,
) {
    Column(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            if (onDetails != null) Modifier.clickable(onClickLabel = "查看完整提示", onClick = onDetails) else Modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FloatingToolbarIcon(icon, null, Modifier.size(18.dp), tint = tint)
            Text(
                message,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall.copy(shadow = toolbarTextShadow()),
                color = tint,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (onDismiss != null) {
                GlasmioIconAction(
                    onClick = onDismiss,
                    modifier = Modifier.size(40.dp),
                    contentDescription = "关闭提示",
                    icon = Icons.Default.Close,
                )
            }
        }
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = glasmioColors().accent,
                trackColor = glasmioColors().subtleStroke,
            )
        }
    }
}

@Composable
internal fun StudioToolbar(
    title: String,
    state: AppUiState?,
    snackbar: SnackbarHostState,
    connectionLabel: String?,
    onOpenBackend: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var details by remember { mutableStateOf<String?>(null) }
    val appColors = glasmioAppColors()
    Column(modifier.fillMaxWidth().testTag("studio-toolbar")) {
        GlasmioToolbarBar(
            title = title,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GlasmioPageHorizontalPadding, vertical = 8.dp),
        ) {
            if (connectionLabel != null) {
                // The label is only shown while the backend is unreachable, so it keeps the
                // destructive tone of the original error-coloured button.
                CompositionLocalProvider(LocalContentColor provides appColors.danger) {
                    GlasmioLabeledAction(
                        icon = Icons.Default.Cloud,
                        label = connectionLabel,
                        onClick = onOpenBackend,
                        modifier = Modifier.clickable(onClickLabel = "打开后端", onClick = onOpenBackend),
                    )
                }
            }
            GlasmioIconAction(
                onClick = onOpenSettings,
                contentDescription = "设置",
                icon = Icons.Default.Settings,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = GlasmioPageHorizontalPadding)
                .testTag("toolbar-feedback"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (state != null) {
                val problem = parameterProblemMessage(state)
                val generation = state.generationMessage
                val transient = snackbar.currentSnackbarData?.visuals?.message
                val count = listOfNotNull(problem, generation.takeIf { it.isNotBlank() }, transient?.takeIf { it != problem && it != generation }).size
                val maxLines = if (count > 1) 1 else 2
                problem?.let {
                    StudioChromeFeedback(
                        message = it,
                        icon = Icons.Default.ErrorOutline,
                        tint = appColors.danger,
                        maxLines = maxLines,
                        onDetails = { details = it },
                    )
                }
                if (generation.isNotBlank()) {
                    val failed = generation.contains("失败")
                    val completed = state.generationProgress == 1f && !generation.contains("正在确认")
                    StudioChromeFeedback(
                        message = generation,
                        icon = when {
                            failed -> Icons.Default.ErrorOutline
                            completed -> Icons.Default.CheckCircle
                            else -> Icons.Default.Info
                        },
                        tint = when {
                            failed -> appColors.danger
                            completed -> appColors.success
                            else -> glasmioColors().accent
                        },
                        maxLines = maxLines,
                        onDetails = { details = generation },
                        progress = if (failed || completed) null else state.generationProgress,
                    )
                }
                if (snackbar.currentSnackbarData != null) SnackbarHost(snackbar) { data ->
                    if (data.visuals.message != problem && data.visuals.message != generation) {
                        StudioChromeFeedback(
                            message = data.visuals.message,
                            icon = if (state.error != null) Icons.Default.ErrorOutline else Icons.Default.Info,
                            tint = if (state.error != null) appColors.danger else appColors.warning,
                            maxLines = maxLines,
                            onDetails = { details = data.visuals.message },
                            onDismiss = data::dismiss,
                        )
                    }
                }
            }
        }
    }
    details?.let { message ->
        // A dialog lives in its own window, so it keeps the Material3 surfaces: the sampled glass
        // backdrop does not exist there.
        AlertDialog(
            onDismissRequest = { details = null },
            title = { Text("运行提示") },
            text = { androidx.compose.foundation.text.selection.SelectionContainer { Text(message) } },
            confirmButton = { TextButton(onClick = { details = null }) { Text("关闭") } },
        )
    }
}
