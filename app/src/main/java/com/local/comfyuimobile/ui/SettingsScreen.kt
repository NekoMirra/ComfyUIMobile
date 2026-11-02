package com.local.comfyuimobile.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.local.comfyuimobile.BuildConfig
import com.local.comfyuimobile.MainViewModel
import com.local.comfyuimobile.model.AppUiState
import com.local.comfyuimobile.model.ConnectionStatus
import com.local.comfyuimobile.model.ServerProfile
import com.local.comfyuimobile.network.ServerCapabilities
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private enum class SettingsPage(val title: String) {
    OVERVIEW("设置"), SERVER("服务器连接"), SERVER_PROFILES("服务器档案"), SERVER_CAPABILITIES("服务器能力"),
    RESULTS("本地作品"), DRAFTS("工作流草稿"), LOGS("诊断日志"), LOG_VIEW("日志内容"), UPDATE("软件更新"),
}

private enum class SettingsConfirmation { DELETE_RESULTS, CLEAR_DRAFTS, CLEAR_LOGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    state: AppUiState,
    viewModel: MainViewModel,
    snackbar: SnackbarHostState,
    onOpenGuide: () -> Unit = {},
    capabilities: ServerCapabilities? = null,
    capabilitiesLoading: Boolean = false,
    capabilitiesError: String? = null,
    onOpenCapabilities: () -> Unit = {},
    onRefreshCapabilities: () -> Unit = {},
    onCopyDiagnostics: (String) -> Unit = {},
    onRenameServer: (String, String) -> Unit = { _, _ -> },
    onSetServerPinned: (String, Boolean) -> Unit = { _, _ -> },
    onRemoveServer: (String) -> Unit = {},
    pinnedServerUrls: Set<String> = emptySet(),
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableStateOf(SettingsPage.OVERVIEW) }
    val expanded = LocalConfiguration.current.screenWidthDp >= 720
    var confirmation by remember { mutableStateOf<SettingsConfirmation?>(null) }
    var renamingProfile by remember { mutableStateOf<ServerProfile?>(null) }
    var diagnosticLog by remember { mutableStateOf("") }
    var pendingLogExport by remember { mutableStateOf("") }
    val logExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val success = uri != null && runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(pendingLogExport) }
                ?: error("无法创建日志文件")
        }.isSuccess
        pendingLogExport = ""
        if (uri != null) viewModel.reportDiagnosticLogExport(success)
    }
    val goBack: () -> Unit = {
        when (page) {
            SettingsPage.OVERVIEW -> onDismiss()
            SettingsPage.LOG_VIEW -> page = SettingsPage.LOGS
            SettingsPage.SERVER_PROFILES, SettingsPage.SERVER_CAPABILITIES -> page = SettingsPage.SERVER
            else -> page = SettingsPage.OVERVIEW
        }
    }
    BackHandler(onBack = goBack)
    LaunchedEffect(Unit) { viewModel.refreshLocalDraftCount() }
    LaunchedEffect(page) {
        if (page == SettingsPage.LOG_VIEW) diagnosticLog = viewModel.diagnosticLog()
    }

    Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GlasmioToolbarBar(
                    title = page.title,
                    actions = {
                        GlasmioIconAction(
                            onClick = goBack,
                            contentDescription = if (page == SettingsPage.OVERVIEW) "关闭设置" else "返回设置",
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                        )
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (expanded) {
                    Column(Modifier.width(192.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 8.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsPage.entries.filter { it != SettingsPage.LOG_VIEW }.forEach { target ->
                            val selected = page == target || page == SettingsPage.LOG_VIEW && target == SettingsPage.LOGS
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { page = target },
                                shape = glasmioShapes().medium,
                                color = if (selected) glasmioColors().prominentContainer else Color.Transparent,
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(when (target) {
                                        SettingsPage.SERVER -> Icons.Default.Wifi
                                        SettingsPage.SERVER_PROFILES -> Icons.Default.Folder
                                        SettingsPage.SERVER_CAPABILITIES -> Icons.Default.Insights
                                        SettingsPage.RESULTS -> Icons.Default.Image
                                        SettingsPage.DRAFTS -> Icons.Default.Save
                                        SettingsPage.LOGS, SettingsPage.LOG_VIEW -> Icons.Default.Description
                                        SettingsPage.UPDATE -> Icons.Default.SystemUpdate
                                        SettingsPage.OVERVIEW -> Icons.Default.Settings
                                    }, null, Modifier.size(20.dp), tint = if (selected) glasmioColors().accent else glasmioColors().secondaryContent)
                                    Text(
                                        if (target == SettingsPage.OVERVIEW) "设置概览" else target.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 2,
                                        color = if (selected) glasmioColors().accent else glasmioColors().content,
                                    )
                                }
                            }
                        }
                    }
                    VerticalDivider(color = glasmioColors().separator)
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    key(page) {
                        LazyColumn(
                            modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = GlasmioPageHorizontalPadding,
                                end = GlasmioPageHorizontalPadding,
                                top = 12.dp,
                                bottom = 32.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            when (page) {
                                SettingsPage.OVERVIEW -> {
                                    item {
                                        SettingsGroup("连接") {
                                            SettingsLink(Icons.Default.Wifi, "服务器连接", serverSummary(state)) { page = SettingsPage.SERVER }
                                        }
                                    }
                                    item {
                                        SettingsGroup("本地数据") {
                                            SettingsLink(
                                                Icons.Default.Image, "本地作品",
                                                "${state.localResults.size} 项作品 · ${state.cacheOutputRules.count { it.enabled }} 条保存规则已启用",
                                            ) { page = SettingsPage.RESULTS }
                                            SettingsLink(
                                                Icons.Default.Save, "工作流草稿",
                                                "${if (state.localDraftsEnabled) "已开启" else "已关闭"} · ${state.localDraftCount} 个草稿",
                                            ) { page = SettingsPage.DRAFTS }
                                        }
                                    }
                                    item {
                                        SettingsGroup("应用") {
                                            SettingsLink(Icons.Default.Description, "新手引导", "连接、工作流、作品保存与常见问题", onOpenGuide)
                                            SettingsLink(
                                                Icons.Default.Description, "诊断日志",
                                                if (state.loggingEnabled) "正在记录运行和闪退日志" else "日志记录已关闭",
                                            ) { page = SettingsPage.LOGS }
                                            SettingsLink(
                                                Icons.Default.SystemUpdate, "软件更新",
                                                when {
                                                    state.updateDownloading -> "正在下载更新"
                                                    state.updateInfo != null -> "发现新版本 ${state.updateInfo.tag}"
                                                    else -> "当前版本 ${BuildConfig.VERSION_NAME}"
                                                },
                                            ) { page = SettingsPage.UPDATE }
                                            SettingsLink(Icons.Default.OpenInNew, "GitHub 项目", "源码、版本与问题反馈") {
                                                runCatching { uriHandler.openUri("https://github.com/${BuildConfig.GITHUB_REPOSITORY}") }
                                                    .onFailure { scope.launch { snackbar.showSnackbar("无法打开浏览器，请稍后重试") } }
                                            }
                                        }
                                    }
                                    item {
                                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                "ComfyUI Mobile",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = glasmioColors().secondaryContent,
                                            )
                                            SettingsHint("版本 ${BuildConfig.VERSION_NAME}")
                                        }
                                    }
                                }
                                SettingsPage.SERVER -> {
                                    item {
                                        SettingsGroup("当前连接") {
                                            SettingsDetail("状态", serverSummary(state))
                                            state.activeServer?.let { server ->
                                                SettingsDetail("服务器地址", server.baseUrl)
                                                if (server.lastSeen > 0L) {
                                                    SettingsDetail("最后在线", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(server.lastSeen)))
                                                }
                                            }
                                        }
                                    }
                                    state.systemStats?.let { stats ->
                                        item {
                                            SettingsGroup("服务器信息") {
                                                SettingsDetail("ComfyUI", stats.comfyVersion.ifBlank { "未知" })
                                                SettingsDetail("前端版本", stats.frontendVersion.ifBlank { "未知" })
                                                stats.devices.forEach { device ->
                                                    SettingsDetail(device.name, "显存可用 ${settingsSize(device.vramFree)} / ${settingsSize(device.vramTotal)}")
                                                }
                                            }
                                        }
                                    }
                                    item {
                                        if (state.activeServer != null) {
                                            GlasmioSecondaryButton(
                                                onClick = { viewModel.disconnect(); onDismiss() },
                                                modifier = Modifier.fillMaxWidth(),
                                                text = "断开连接",
                                            )
                                        } else {
                                            SettingsHint("返回连接页可添加服务器或扫描局域网。")
                                        }
                                    }
                                    item {
                                        SettingsGroup("档案与诊断") {
                                            SettingsLink(
                                                Icons.Default.Folder, "服务器档案管理",
                                                "${state.savedServers.size} 台已保存 · 可重命名、置顶或删除",
                                            ) { page = SettingsPage.SERVER_PROFILES }
                                            SettingsLink(
                                                Icons.Default.Insights, "服务器能力与诊断",
                                                capabilities?.summaryLine() ?: "尚未读取服务器能力",
                                            ) { page = SettingsPage.SERVER_CAPABILITIES }
                                            SettingsLink(
                                                Icons.Default.Save, "输出白名单规则",
                                                "${state.cacheOutputRules.count { it.enabled }} 条已启用 / ${state.cacheOutputRules.size} 条",
                                            ) { page = SettingsPage.RESULTS }
                                            SettingsLink(
                                                Icons.Default.Description, "诊断日志",
                                                if (state.loggingEnabled) "正在记录运行和闪退日志" else "日志记录已关闭",
                                            ) { page = SettingsPage.LOGS }
                                        }
                                    }
                                }
                                SettingsPage.SERVER_PROFILES -> {
                                    item {
                                        SettingsGroup("已保存的服务器") {
                                            SettingsDetail("${state.savedServers.size} 台已保存", "置顶的服务器会排在最前面，最近连接的排在前面。")
                                        }
                                    }
                                    if (state.savedServers.isEmpty()) {
                                        item {
                                            SettingsGroup {
                                                SettingsDetail("尚未保存服务器", "返回连接页添加地址或扫描局域网。")
                                            }
                                        }
                                    }
                                    items(state.savedServers) { profile ->
                                        SettingsGroup {
                                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text(
                                                        profile.name,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        modifier = Modifier.weight(1f),
                                                        color = glasmioColors().content,
                                                    )
                                                    if (profile.baseUrl in pinnedServerUrls) GlasmioBadge("已置顶")
                                                }
                                                Text(
                                                    profile.baseUrl,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = glasmioColors().secondaryContent,
                                                )
                                                Text(
                                                    serverProfileMeta(profile),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = glasmioColors().tertiaryContent,
                                                )
                                            }
                                            SettingsDivider()
                                            Row(
                                                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                GlasmioSecondaryButton(
                                                    onClick = { renamingProfile = profile },
                                                    modifier = Modifier.weight(1f),
                                                    text = "重命名",
                                                )
                                                GlasmioSecondaryButton(
                                                    onClick = { onSetServerPinned(profile.baseUrl, profile.baseUrl !in pinnedServerUrls) },
                                                    modifier = Modifier.weight(1f),
                                                    text = if (profile.baseUrl in pinnedServerUrls) "取消置顶" else "置顶",
                                                )
                                                TextButton(onClick = { onRemoveServer(profile.baseUrl) }) {
                                                    Text("删除", color = glasmioAppColors().danger)
                                                }
                                            }
                                        }
                                    }
                                    item {
                                        SettingsHint("删除档案只清理本机记录，不会影响服务器上的工作流和作品。")
                                    }
                                }
                                SettingsPage.SERVER_CAPABILITIES -> {
                                    item {
                                        SettingsGroup("服务器能力") {
                                            if (capabilities == null) {
                                                SettingsDetail(
                                                    "尚未读取服务器能力",
                                                    capabilitiesError ?: "连接服务器后可以读取版本、队列与显卡状态。",
                                                )
                                            } else {
                                                SettingsDetail("ComfyUI", capabilities.comfyVersion.ifBlank { "未知" })
                                                SettingsDetail("前端版本", capabilities.frontendVersion.ifBlank { "未报告" })
                                                SettingsDetail("Python / PyTorch", listOf(capabilities.pythonVersion, capabilities.pytorchVersion).filter { it.isNotBlank() }.joinToString(" / ").ifBlank { "未报告" })
                                                if (capabilities.ramTotal > 0L) {
                                                    SettingsDetail("内存", "${settingsSize(capabilities.ramFree)} 可用 / ${settingsSize(capabilities.ramTotal)}")
                                                }
                                                if (capabilities.deviceName.isNotBlank()) {
                                                    SettingsDetail(
                                                        capabilities.deviceName,
                                                        if (capabilities.vramTotal > 0L) {
                                                            "显存可用 ${settingsSize(capabilities.vramFree)} / ${settingsSize(capabilities.vramTotal)}"
                                                        } else {
                                                            "显存信息未报告"
                                                        },
                                                    )
                                                }
                                                SettingsDetail("队列", "运行中 ${capabilities.queueRunningCount} · 等待中 ${capabilities.queuePendingCount}")
                                                SettingsDetail(
                                                    "assets 能力",
                                                    buildString {
                                                        append(if (capabilities.assetsEnabled) "已启用" else "未启用")
                                                        append(" · 预览元数据")
                                                        append(if (capabilities.supportsPreviewMetadata) "支持" else "不支持")
                                                        if (capabilities.maxUploadSize > 0L) {
                                                            append(" · 上传上限 ${settingsSize(capabilities.maxUploadSize)}")
                                                        }
                                                    },
                                                )
                                            }
                                        }
                                    }
                                    item {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            GlasmioSecondaryButton(
                                                onClick = onOpenCapabilities,
                                                modifier = Modifier.weight(1f),
                                                enabled = capabilities != null,
                                                text = "打开能力面板",
                                            )
                                            GlasmioSecondaryButton(
                                                onClick = onRefreshCapabilities,
                                                modifier = Modifier.weight(1f),
                                                enabled = !capabilitiesLoading,
                                                text = if (capabilitiesLoading) "正在读取…" else "刷新",
                                            )
                                        }
                                    }
                                    item {
                                        SettingsHint("能力数据来自 /system_stats、/features 与 /queue；读取失败时会在能力面板中显示具体原因。")
                                    }
                                }
                                SettingsPage.RESULTS -> {
                                    item {
                                        SettingsGroup("自动保存规则") {
                                            SettingsDetail("已启用 ${state.cacheOutputRules.count { it.enabled }} / ${state.cacheOutputRules.size} 条", "在参数页长按输出部件，即可加入保存白名单。")
                                        }
                                    }
                                    item {
                                        SettingsHint("规则按服务器和输出部件类型生效，适用于该服务器的所有工作流。仅自动保存本 App 提交或你明确接管且命中白名单的任务输出。")
                                    }
                                    if (state.cacheOutputRules.isEmpty()) {
                                        item {
                                            SettingsGroup {
                                                SettingsDetail("尚未添加保存规则", "添加后，符合规则的新作品会自动保存到手机。")
                                            }
                                        }
                                    }
                                    items(state.cacheOutputRules) { rule ->
                                        SettingsGroup {
                                            Row(
                                                Modifier.fillMaxWidth().toggleable(value = rule.enabled, role = Role.Switch, onValueChange = { viewModel.setCacheRuleEnabled(rule, it) }).padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            ) {
                                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(
                                                        rule.nodeTitle.ifBlank { rule.nodeType },
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = glasmioColors().content,
                                                    )
                                                    SettingsHint(rule.nodeType)
                                                    SettingsHint(rule.serverUrl)
                                                }
                                                Switch(rule.enabled, onCheckedChange = null)
                                            }
                                            SettingsDivider()
                                            GlasmioSecondaryButton(
                                                onClick = { viewModel.removeCacheRule(rule) },
                                                modifier = Modifier.align(Alignment.End).padding(end = 8.dp),
                                                text = "移除规则",
                                            )
                                        }
                                    }
                                    item {
                                        SettingsGroup("存储管理") {
                                            SettingsDetail("${state.localResults.size} 项本地作品", "删除后无法从手机恢复，电脑端作品和自动保存规则不受影响。")
                                            SettingsDangerButton("删除全部本地作品", state.localResults.isNotEmpty()) { confirmation = SettingsConfirmation.DELETE_RESULTS }
                                        }
                                    }
                                }
                                SettingsPage.DRAFTS -> {
                                    item {
                                        SettingsGroup {
                                            SettingsToggle("保存本地草稿", "保留工作流未保存的修改，下次打开时继续编辑。", state.localDraftsEnabled, viewModel::setLocalDraftsEnabled)
                                        }
                                    }
                                    item {
                                        SettingsHint("关闭后，打开工作流会直接读取服务器版本，不再保存或恢复未保存修改。图片和文件导入的临时工作流仅在当前会话保留。")
                                    }
                                    item {
                                        SettingsGroup("草稿管理") {
                                            SettingsDetail("${state.localDraftCount} 个工作流有本地草稿", "清除草稿不影响服务器上已保存的工作流文件。")
                                            SettingsDangerButton("清除全部本地草稿", state.localDraftCount > 0) { confirmation = SettingsConfirmation.CLEAR_DRAFTS }
                                        }
                                    }
                                }
                                SettingsPage.LOGS -> {
                                    item {
                                        SettingsGroup {
                                            SettingsToggle("记录运行和闪退日志", "用于排查连接、任务和应用异常。", state.loggingEnabled, viewModel::setLoggingEnabled)
                                        }
                                    }
                                    item { SettingsHint("不记录提示词、工作流正文或生成图片。关闭记录后，仍可查看和导出已有日志。") }
                                    item {
                                        SettingsGroup("日志文件") {
                                            SettingsLink(Icons.Default.Description, "查看日志", "读取当前设备上已有的记录") {
                                                page = SettingsPage.LOG_VIEW
                                            }
                                            SettingsLink(Icons.Default.Download, "导出日志", "保存为文本文件，便于反馈问题") {
                                                pendingLogExport = viewModel.diagnosticLog()
                                                logExportLauncher.launch("ComfyUIMobile-${System.currentTimeMillis()}.log")
                                            }
                                        }
                                    }
                                    item {
                                        SettingsDangerButton("清空日志") { confirmation = SettingsConfirmation.CLEAR_LOGS }
                                    }
                                }
                                SettingsPage.LOG_VIEW -> {
                                    item {
                                        SelectionContainer {
                                            Text(diagnosticLog.ifBlank { "暂无日志" }, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                }
                                SettingsPage.UPDATE -> {
                                    item {
                                        SettingsGroup {
                                            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Icon(Icons.Default.SystemUpdate, null, Modifier.size(40.dp), tint = glasmioColors().accent)
                                                Text(
                                                    "ComfyUI Mobile",
                                                    style = MaterialTheme.typography.titleLarge,
                                                    color = glasmioColors().content,
                                                )
                                                SettingsHint("当前版本 ${BuildConfig.VERSION_NAME}")
                                            }
                                        }
                                    }
                                    item {
                                        GlasmioSecondaryButton(
                                            onClick = { viewModel.checkUpdate() },
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = !state.updateDownloading && !state.updateChecking,
                                            text = if (state.updateChecking) "正在检查更新…" else "检查更新",
                                        )
                                    }
                                    if (state.updateChecking) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                                    state.updateCheckMessage?.let { message -> item { SettingsHint(message) } }
                                    state.updateInfo?.let { info ->
                                        item {
                                            SettingsGroup("可用更新") {
                                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                    Text(
                                                        "新版本 ${info.tag}",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = glasmioColors().content,
                                                    )
                                                    if (state.updateDownloading) {
                                                        val progress = state.updateDownloadProgress
                                                        if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                                                        else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                                                        SettingsHint("下载中 ${progress?.let { "${(it * 100).toInt()}%" }.orEmpty()} · ${state.updateDownloadSource.orEmpty()}")
                                                    } else {
                                                        GlasmioPrimaryButton(
                                                            onClick = viewModel::downloadUpdate,
                                                            modifier = Modifier.fillMaxWidth(),
                                                            text = "下载并安装",
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    item { SettingsHint("更新来自 GitHub Release，安装前会校验文件、包名、版本号和签名证书。未连接服务器时也可检查更新。") }
                                }
                            }
                        }
                    }
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "名称",
                        style = MaterialTheme.typography.labelMedium,
                        color = glasmioColors().secondaryContent,
                    )
                    GlasmioTextInput(
                        value = draftName,
                        onValueChange = { draftName = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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

    confirmation?.let { action ->
        val title = when (action) {
            SettingsConfirmation.DELETE_RESULTS -> "删除全部本地作品？"
            SettingsConfirmation.CLEAR_DRAFTS -> "清除全部本地草稿？"
            SettingsConfirmation.CLEAR_LOGS -> "清空诊断日志？"
        }
        val message = when (action) {
            SettingsConfirmation.DELETE_RESULTS -> "将删除手机中现有的 ${state.localResults.size} 项作品。电脑端作品不会被删除，保存规则仍会生效，之后符合规则的新作品仍会自动保存。"
            SettingsConfirmation.CLEAR_DRAFTS -> "将删除全部 ${state.localDraftCount} 个本地草稿及其中未保存的修改。服务器上的工作流文件不受影响。"
            SettingsConfirmation.CLEAR_LOGS -> "将清除当前设备上已有的诊断日志。日志记录开关保持不变。"
        }
        GlasmioAlertDialog(
            visible = true,
            onDismissRequest = { confirmation = null },
            title = title,
            message = message,
            confirmText = "确认清除",
            dismissText = "取消",
            destructive = true,
            onConfirm = {
                when (action) {
                    SettingsConfirmation.DELETE_RESULTS -> viewModel.clearLocalCache()
                    SettingsConfirmation.CLEAR_DRAFTS -> viewModel.clearAllWorkflowDrafts()
                    SettingsConfirmation.CLEAR_LOGS -> viewModel.clearDiagnosticLog()
                }
                confirmation = null
            },
        )
    }
}

@Composable
private fun SettingsGroup(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    GlasmioListSection(title = title) {
        content()
    }
}

@Composable
private fun SettingsLink(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    GlasmioListRow(
        title = title,
        subtitle = summary,
        icon = icon,
        onClick = onClick,
    )
}

@Composable
private fun SettingsToggle(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    GlasmioToggleRow(
        title = title,
        summary = summary,
        checked = checked,
        onCheckedChange = onCheckedChange,
    )
}

@Composable
private fun SettingsDetail(title: String, value: String) {
    GlasmioListRow(
        title = title,
        subtitle = value,
    )
}

@Composable
private fun SettingsHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = glasmioColors().secondaryContent)
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = glasmioColors().separator)
}

@Composable
private fun SettingsDangerButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = glasmioShapes().medium,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = glasmioAppColors().danger),
    ) {
        Icon(Icons.Default.DeleteOutline, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

private fun serverSummary(state: AppUiState): String = when (state.status) {
    ConnectionStatus.CONNECTED -> "已连接 · ${state.activeServer?.name.orEmpty()}"
    ConnectionStatus.CONNECTING -> "正在连接服务器"
    ConnectionStatus.RECONNECTING -> "正在重新连接"
    ConnectionStatus.ERROR -> "连接异常 · ${state.connectionMessage}"
    ConnectionStatus.DISCONNECTED -> "尚未连接服务器"
}

private fun settingsSize(bytes: Long): String = if (bytes >= 1024L * 1024L * 1024L) {
    String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
} else {
    String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024))
}

/** Version and last successful use of a saved server. */
private fun serverProfileMeta(profile: ServerProfile): String {
    val version = profile.comfyVersion.takeIf { it.isNotBlank() }?.let { "ComfyUI $it" } ?: "版本未知"
    val seen = if (profile.lastSeen > 0L) {
        "最后使用 ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(profile.lastSeen))}"
    } else {
        "尚未连接成功"
    }
    return "$version · $seen"
}
