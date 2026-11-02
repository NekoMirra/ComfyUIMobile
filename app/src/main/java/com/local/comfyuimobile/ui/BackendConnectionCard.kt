package com.local.comfyuimobile.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.local.comfyuimobile.model.AppUiState
import com.local.comfyuimobile.model.ConnectionStatus
import com.local.comfyuimobile.network.ServerCapabilities

internal fun backendConnectionLabel(state: AppUiState): String = when (state.status) {
    ConnectionStatus.CONNECTED -> if (state.bridgeReady) "在线" else "初始化中"
    ConnectionStatus.CONNECTING -> "连接中"
    ConnectionStatus.RECONNECTING -> "重连中"
    ConnectionStatus.ERROR -> "连接异常"
    ConnectionStatus.DISCONNECTED -> "未连接"
}

@Composable
internal fun BackendConnectionCard(
    state: AppUiState,
    onReconnect: () -> Unit,
    capabilities: ServerCapabilities? = null,
) {
    var details by rememberSaveable { mutableStateOf(false) }
    val online = state.status == ConnectionStatus.CONNECTED && state.bridgeReady
    val badges = backendCapabilityBadges(state, capabilities)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.activeServer?.name.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                StudioBadge(backendConnectionLabel(state), color = if (online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
            SelectionContainer {
                Text(state.activeServer?.baseUrl.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (badges.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    badges.forEach { badge -> StudioBadge(badge) }
                }
            }
            if (!online && state.connectionMessage.isNotBlank()) {
                Text(state.connectionMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { details = !details }) {
                    Text(if (details) "收起服务器信息" else "服务器信息")
                    Icon(if (details) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(18.dp))
                }
                TextButton(onClick = onReconnect, enabled = state.status != ConnectionStatus.CONNECTING) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Text("重连", maxLines = 1)
                }
            }
            if (details) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val stats = state.systemStats
                val comfyVersion = capabilities?.comfyVersion?.takeIf { it.isNotBlank() }
                    ?: stats?.comfyVersion?.takeIf { it.isNotBlank() }
                Text("ComfyUI ${comfyVersion ?: "版本未知"}", style = MaterialTheme.typography.bodyMedium)
                val frontendVersion = capabilities?.frontendVersion?.takeIf { it.isNotBlank() }
                    ?: stats?.frontendVersion?.takeIf { it.isNotBlank() }
                frontendVersion?.let {
                    Text("前端 $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (capabilities != null) {
                    Text(
                        "Python ${capabilities.pythonVersion.ifBlank { "未知" }} · PyTorch ${capabilities.pytorchVersion.ifBlank { "未知" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (capabilities.ramTotal > 0L) {
                        Text(
                            "内存可用 ${formatCapacityBytes(capabilities.ramFree)} / ${formatCapacityBytes(capabilities.ramTotal)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val capabilityDevice = capabilities?.takeIf { it.deviceName.isNotBlank() }
                if (capabilityDevice != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(capabilityDevice.deviceName, style = MaterialTheme.typography.bodySmall)
                        if (capabilityDevice.vramTotal > 0L) {
                            Text(
                                "显存可用 ${formatCapacityBytes(capabilityDevice.vramFree)} / ${formatCapacityBytes(capabilityDevice.vramTotal)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    stats?.devices?.forEach { device ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(device.name, style = MaterialTheme.typography.bodySmall)
                            if (device.vramTotal > 0L) {
                                Text(
                                    "显存可用 ${formatCapacityBytes(device.vramFree)} / ${formatCapacityBytes(device.vramTotal)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                val queueText = capabilities?.let { "服务器队列：运行中 ${it.queueRunningCount} · 等待中 ${it.queuePendingCount}" }
                    ?: "服务器等待队列：${state.queueRemaining}"
                Text(queueText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Short badges for the card header. A value that the server did not report is
 * left out entirely instead of showing an empty placeholder.
 */
private fun backendCapabilityBadges(state: AppUiState, capabilities: ServerCapabilities?): List<String> = buildList {
    if (capabilities != null) {
        add("队列 ${capabilities.queueRunningCount}/${capabilities.queuePendingCount}")
        if (capabilities.vramTotal > 0L) {
            add("显存 ${formatCapacityBytes(capabilities.vramFree)}/${formatCapacityBytes(capabilities.vramTotal)}")
        }
        if (capabilities.assetsEnabled) add("assets 已启用")
        if (capabilities.supportsPreviewMetadata) add("预览元数据")
        capabilities.frontendVersion.takeIf { it.isNotBlank() }?.let { add("前端 $it") }
    } else {
        state.queueRemaining.takeIf { it > 0 }?.let { add("队列 $it") }
        state.systemStats?.frontendVersion?.takeIf { it.isNotBlank() }?.let { add("前端 $it") }
        state.systemStats?.devices?.firstOrNull { it.vramTotal > 0L }?.let { device ->
            add("显存 ${formatCapacityBytes(device.vramFree)}/${formatCapacityBytes(device.vramTotal)}")
        }
    }
}
