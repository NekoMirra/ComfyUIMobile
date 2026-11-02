package com.local.comfyuimobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.local.comfyuimobile.model.AppUiState
import com.local.comfyuimobile.model.ConnectionStatus
import com.local.comfyuimobile.model.ServerProfile
import com.local.comfyuimobile.network.Diagnosis
import com.local.comfyuimobile.network.ServerCapabilities
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConnectionScreen(
    state: AppUiState,
    snackbar: SnackbarHostState,
    onOpenSettings: () -> Unit,
    onServerInput: (String) -> Unit,
    onConnect: (String) -> Unit,
    onScan: () -> Unit,
    onRemoveServer: (String) -> Unit,
    onOpenGuide: () -> Unit = {},
    capabilities: ServerCapabilities? = null,
    capabilitiesLoading: Boolean = false,
    capabilitiesError: String? = null,
    onOpenCapabilities: () -> Unit = {},
    onRefreshCapabilities: () -> Unit = {},
    onCopyDiagnostics: (String) -> Unit = {},
    diagnosis: Diagnosis? = null,
    pinnedServerUrls: Set<String> = emptySet(),
    onRenameServer: (String, String) -> Unit = { _, _ -> },
    onSetServerPinned: (String, Boolean) -> Unit = { _, _ -> },
) {
    var renamingProfile by remember { mutableStateOf<ServerProfile?>(null) }
    var capabilitySheetVisible by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Hub, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("ComfyUI Mobile", style = MaterialTheme.typography.titleMedium)
                    }
                },
                actions = {
                    TextButton(onClick = onOpenGuide) { Text("使用指南") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "设置") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val expanded = maxWidth >= 720.dp
            val controls: @Composable () -> Unit = {
                ConnectionControls(
                    state = state,
                    capabilities = capabilities,
                    diagnosis = diagnosis,
                    pinnedServerUrls = pinnedServerUrls,
                    onServerInput = onServerInput,
                    onConnect = onConnect,
                    onScan = onScan,
                    onRemoveServer = onRemoveServer,
                    onSetServerPinned = onSetServerPinned,
                    onRequestRename = { renamingProfile = it },
                    onOpenCapabilities = { capabilitySheetVisible = true },
                    onRefreshCapabilities = onRefreshCapabilities,
                )
            }
            if (expanded) {
                Row(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    Column(Modifier.weight(0.42f).padding(top = 28.dp)) { ConnectionIntro(true) }
                    Column(Modifier.weight(0.58f), verticalArrangement = Arrangement.spacedBy(24.dp)) { controls() }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    ConnectionIntro(false)
                    controls()
                }
            }
        }
    }

    renamingProfile?.let { profile ->
        var draftName by remember(profile.baseUrl) { mutableStateOf(profile.name) }
        AlertDialog(
            onDismissRequest = { renamingProfile = null },
            title = { Text("重命名服务器") },
            text = {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    singleLine = true,
                    label = { Text("名称") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameServer(profile.baseUrl, draftName.trim())
                        renamingProfile = null
                    },
                    enabled = draftName.isNotBlank(),
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renamingProfile = null }) { Text("取消") } },
        )
    }

    if (capabilitySheetVisible) {
        ServerCapabilitySheet(
            capabilities = capabilities,
            loading = capabilitiesLoading,
            error = capabilitiesError,
            onRefresh = {
                capabilitySheetVisible = true
                onRefreshCapabilities()
            },
            onCopyDiagnostics = onCopyDiagnostics,
            onDismiss = { capabilitySheetVisible = false },
        )
    }
}

@Composable
private fun ConnectionIntro(expanded: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier.size(if (expanded) 88.dp else 56.dp)
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer)), RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Hub, null, Modifier.size(if (expanded) 42.dp else 28.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(
            if (expanded) "连接你的\nComfyUI 工作站" else "连接你的创作空间",
            style = if (expanded) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
        )
        Text("从工作流到作品，在这里继续。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        if (expanded) {
            Spacer(Modifier.height(24.dp))
            ConnectionFeature(Icons.Default.Folder, "你的工作流", "读取、管理和继续编辑")
            ConnectionFeature(Icons.Default.Tune, "专注创作", "用适合触控的表单调整参数")
            ConnectionFeature(Icons.Default.Image, "作品随行", "查看生成结果并保存原图")
        }
    }
}

@Composable
private fun ConnectionFeature(icon: ImageVector, title: String, description: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ConnectionControls(
    state: AppUiState,
    capabilities: ServerCapabilities?,
    diagnosis: Diagnosis?,
    pinnedServerUrls: Set<String>,
    onServerInput: (String) -> Unit,
    onConnect: (String) -> Unit,
    onScan: () -> Unit,
    onRemoveServer: (String) -> Unit,
    onSetServerPinned: (String, Boolean) -> Unit,
    onRequestRename: (ServerProfile) -> Unit,
    onOpenCapabilities: () -> Unit,
    onRefreshCapabilities: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("添加连接", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.serverInput,
                onValueChange = onServerInput,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("服务器地址") },
                placeholder = { Text("http://192.168.1.20:8188") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (!state.loading && state.serverInput.isNotBlank()) onConnect(state.serverInput) }),
            )
            Button(
                onClick = { onConnect(state.serverInput) },
                enabled = !state.loading && state.serverInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp, horizontal = 20.dp),
            ) {
                if (state.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Wifi, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (state.loading) "正在连接…" else "连接工作站")
                Spacer(Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
            }
            TextButton(onClick = onScan, enabled = !state.scanning, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                if (state.scanning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (state.scanning) "正在发现设备…" else "扫描局域网")
            }
        }
    }
    if (capabilities != null || state.status == ConnectionStatus.CONNECTED) {
        ServerCapabilitySummaryRow(
            capabilities = capabilities,
            onOpen = onOpenCapabilities,
            onRefresh = onRefreshCapabilities,
        )
    }
    diagnosis?.let { ConnectionDiagnosisCard(it) }
    if (state.status == ConnectionStatus.CONNECTING || state.status == ConnectionStatus.ERROR) ConnectionProgressCard(state)
    if (state.savedServers.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ConnectionSectionLabel("已保存的工作站", state.savedServers.size)
            state.savedServers.forEach { profile ->
                ConnectionServerTile(
                    profile = profile,
                    onClick = { onConnect(profile.baseUrl) },
                    pinned = profile.baseUrl in pinnedServerUrls,
                    meta = savedServerMeta(profile),
                    onRename = { onRequestRename(profile) },
                    onTogglePin = { onSetServerPinned(profile.baseUrl, profile.baseUrl !in pinnedServerUrls) },
                    onDelete = { onRemoveServer(profile.baseUrl) },
                )
            }
        }
    }
    if (state.discoveredServers.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ConnectionSectionLabel("发现的设备", state.discoveredServers.size)
            state.discoveredServers.forEach { profile ->
                ConnectionServerTile(
                    profile = profile,
                    onClick = { onConnect(profile.baseUrl) },
                    meta = discoveredServerMeta(profile),
                )
            }
        }
    }
    Text("支持局域网 / VPN 的 HTTP 地址，以及云端完整 ComfyUI 实例的 HTTPS 根地址。云端链接按平台原样粘贴；需要平台登录或 API Key 的服务暂不支持。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ServerCapabilitySummaryRow(
    capabilities: ServerCapabilities?,
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("服务器能力", style = MaterialTheme.typography.titleSmall)
                Text(
                    capabilities?.summaryLine() ?: "点击查看服务器版本、队列与显卡状态",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "刷新服务器能力", Modifier.size(20.dp)) }
            TextButton(onClick = onOpen) { Text("详情") }
        }
    }
}

@Composable
private fun ConnectionDiagnosisCard(diagnosis: Diagnosis) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                diagnosis.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                diagnosis.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (diagnosis.actions.isNotEmpty()) {
                Text("建议处理", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onErrorContainer)
                diagnosis.actions.forEach { action ->
                    Text(
                        "· $action",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionSectionLabel(title: String, count: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text("$count 台", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ConnectionServerTile(
    profile: ServerProfile,
    onClick: () -> Unit,
    pinned: Boolean = false,
    meta: String = "",
    onRename: (() -> Unit)? = null,
    onTogglePin: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(38.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Wifi, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(profile.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (pinned) Icon(Icons.Default.PushPin, "已置顶", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Text(profile.baseUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (onRename != null || onTogglePin != null || onDelete != null) {
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "更多操作", Modifier.size(20.dp)) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (onRename != null) {
                            DropdownMenuItem(
                                text = { Text("重命名") },
                                onClick = {
                                    menuOpen = false
                                    onRename()
                                },
                            )
                        }
                        if (onTogglePin != null) {
                            DropdownMenuItem(
                                text = { Text(if (pinned) "取消置顶" else "置顶") },
                                onClick = {
                                    menuOpen = false
                                    onTogglePin()
                                },
                            )
                        }
                        if (onDelete != null) {
                            DropdownMenuItem(
                                text = { Text("删除") },
                                onClick = {
                                    menuOpen = false
                                    onDelete()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Saved servers show the last known ComfyUI version and the last successful use. */
private fun savedServerMeta(profile: ServerProfile): String {
    val version = profile.comfyVersion.takeIf { it.isNotBlank() }?.let { "ComfyUI $it" } ?: "版本未知"
    val seen = if (profile.lastSeen > 0L) {
        "最后使用 ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(profile.lastSeen))}"
    } else {
        "尚未连接成功"
    }
    return "$version · $seen"
}

/** Discovered servers report the probed version, or state that it is unknown. */
private fun discoveredServerMeta(profile: ServerProfile): String =
    profile.comfyVersion.takeIf { it.isNotBlank() }?.let { "ComfyUI $it" } ?: "已连通，版本未知"
