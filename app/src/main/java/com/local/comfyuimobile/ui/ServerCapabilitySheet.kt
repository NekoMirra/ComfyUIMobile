package com.local.comfyuimobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.local.comfyuimobile.network.ServerCapabilities
import com.local.comfyuimobile.network.describeStaleness
import java.util.Locale

/**
 * Panel that shows what the connected server can do.
 *
 * The sheet owns no state: the caller passes the snapshot, the loading flag and
 * the error message, and receives refresh / copy requests through callbacks.
 */
@Composable
internal fun ServerCapabilitySheet(
    capabilities: ServerCapabilities?,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onCopyDiagnostics: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("服务器能力", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRefresh, enabled = !loading) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("刷新")
                    }
                }
                when {
                    loading -> CapabilityLoading()
                    error != null -> CapabilityError(error, onRefresh)
                    capabilities != null -> CapabilityDetails(capabilities)
                    else -> CapabilityError("尚未读取到服务器能力，请点击刷新重试。", onRefresh)
                }
                if (capabilities != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onCopyDiagnostics(buildCapabilityDiagnostics(capabilities)) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("复制诊断信息")
                        }
                        TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("关闭") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CapabilityLoading() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        Text("正在读取服务器能力…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CapabilityError(message: String, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("读取服务器能力失败", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
        Text(message, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onRefresh) { Text("重试") }
    }
}

@Composable
private fun CapabilityDetails(capabilities: ServerCapabilities) {
    val now = remember(capabilities.checkedAt) { System.currentTimeMillis() }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CapabilityRow("ComfyUI 版本", capabilities.comfyVersion.ifBlank { "未知" })
        CapabilityRow("前端版本", capabilities.frontendVersion.ifBlank { "未报告" })
        CapabilityRow("模板版本", capabilities.templatesVersion.ifBlank { "未报告" })
        CapabilityRow("Python / PyTorch", listOf(capabilities.pythonVersion, capabilities.pytorchVersion).filter { it.isNotBlank() }.joinToString(" / ").ifBlank { "未报告" })
        if (capabilities.ramTotal > 0L) {
            CapabilityRow("内存", "${formatCapacityBytes(capabilities.ramFree)} 可用 / ${formatCapacityBytes(capabilities.ramTotal)}")
        }
        if (capabilities.deviceName.isNotBlank()) {
            CapabilityRow(
                "GPU",
                buildString {
                    append(capabilities.deviceName)
                    if (capabilities.vramTotal > 0L) {
                        append(" · 显存 ${formatCapacityBytes(capabilities.vramFree)} / ${formatCapacityBytes(capabilities.vramTotal)}")
                    }
                },
            )
        }
        CapabilityRow("队列", "运行中 ${capabilities.queueRunningCount} · 等待中 ${capabilities.queuePendingCount}")
        CapabilityRow("assets 能力", if (capabilities.assetsEnabled) "已启用" else "未启用")
        CapabilityRow("预览元数据", if (capabilities.supportsPreviewMetadata) "支持" else "不支持")
        if (capabilities.maxUploadSize > 0L) {
            CapabilityRow("上传上限", formatCapacityBytes(capabilities.maxUploadSize))
        }
        CapabilityRow("数据更新时间", describeStaleness(capabilities.checkedAt, now))
    }
}

@Composable
private fun CapabilityRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), overflow = TextOverflow.Ellipsis)
    }
}

/** One line summary used by the connection page and the backend card. */
internal fun ServerCapabilities.summaryLine(): String = buildList {
    add("ComfyUI ${comfyVersion.ifBlank { "版本未知" }}")
    add("队列 $queueRunningCount/$queuePendingCount")
    if (vramTotal > 0L) {
        add("显存 ${formatCapacityBytes(vramFree)}/${formatCapacityBytes(vramTotal)}")
    } else if (deviceName.isNotBlank()) {
        add(deviceName)
    }
}.joinToString(" · ")

/** Plain text report that can be pasted into a bug report. */
internal fun buildCapabilityDiagnostics(capabilities: ServerCapabilities, now: Long = System.currentTimeMillis()): String =
    buildString {
        appendLine("ComfyUI Mobile 服务器能力诊断")
        appendLine("ComfyUI 版本：${capabilities.comfyVersion.ifBlank { "未知" }}")
        appendLine("前端版本：${capabilities.frontendVersion.ifBlank { "未报告" }}")
        appendLine("模板版本：${capabilities.templatesVersion.ifBlank { "未报告" }}")
        appendLine("Python：${capabilities.pythonVersion.ifBlank { "未报告" }}")
        appendLine("PyTorch：${capabilities.pytorchVersion.ifBlank { "未报告" }}")
        appendLine("内存：${formatCapacityBytes(capabilities.ramFree)} 可用 / ${formatCapacityBytes(capabilities.ramTotal)}")
        appendLine("GPU：${capabilities.deviceName.ifBlank { "未报告" }}")
        appendLine("显存：${formatCapacityBytes(capabilities.vramFree)} 可用 / ${formatCapacityBytes(capabilities.vramTotal)}")
        appendLine("队列：运行中 ${capabilities.queueRunningCount}，等待中 ${capabilities.queuePendingCount}")
        appendLine("assets：${if (capabilities.assetsEnabled) "已启用" else "未启用"}")
        appendLine("预览元数据：${if (capabilities.supportsPreviewMetadata) "支持" else "不支持"}")
        appendLine("上传上限：${if (capabilities.maxUploadSize > 0L) formatCapacityBytes(capabilities.maxUploadSize) else "未报告"}")
        appendLine("更新时间：${describeStaleness(capabilities.checkedAt, now)}")
    }.trimEnd()

internal fun formatCapacityBytes(bytes: Long): String {
    if (bytes <= 0L) return "未知"
    val gib = 1024.0 * 1024.0 * 1024.0
    val mib = 1024.0 * 1024.0
    return when {
        bytes >= gib -> String.format(Locale.getDefault(), "%.1f GB", bytes / gib)
        bytes >= mib -> String.format(Locale.getDefault(), "%.0f MB", bytes / mib)
        bytes >= 1024L -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
