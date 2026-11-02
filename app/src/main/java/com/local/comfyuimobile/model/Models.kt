package com.local.comfyuimobile.model

import com.local.comfyuimobile.data.PromptEntry
import com.local.comfyuimobile.data.ServerGalleryMerge
import com.local.comfyuimobile.network.Diagnosis
import com.local.comfyuimobile.network.ServerCapabilities

enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, ERROR }

data class ServerProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val lastSeen: Long = 0L,
    val comfyVersion: String = "",
)

data class DeviceStats(
    val name: String,
    val vramTotal: Long,
    val vramFree: Long,
)

data class SystemStats(
    val comfyVersion: String,
    val frontendVersion: String,
    val devices: List<DeviceStats>,
)

data class WorkflowEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val modified: Double = 0.0,
)

enum class ParameterKind { TEXT, MULTILINE, INTEGER, DECIMAL, BOOLEAN, COMBO, IMAGE, IMAGE_LIST, VIDEO, UNSUPPORTED }
enum class ParameterSection { PRIMARY, MORE }

data class ParameterAction(val label: String, val payloadJson: String, val enabled: Boolean = true)

data class ParameterField(
    val key: String,
    val nodeId: String,
    val nodeTitle: String,
    val nodeType: String,
    val name: String,
    val label: String,
    val widgetType: String,
    val kind: ParameterKind,
    val valueJson: String,
    val originalValueJson: String = valueJson,
    val displayValue: String,
    val options: List<String> = emptyList(),
    val minimum: Double? = null,
    val maximum: Double? = null,
    val step: Double? = null,
    val linked: Boolean = false,
    val visible: Boolean = true,
    val section: ParameterSection = ParameterSection.MORE,
    val order: Int = 0,
    val warning: String? = null,
    val widgetIndex: Int = -1,
    val refreshesWorkflow: Boolean = false,
    val nodeOrder: Int = 0,
    val actions: List<ParameterAction> = emptyList(),
)

data class WorkflowNode(
    val id: String,
    val title: String,
    val type: String,
    val order: Int,
    val isController: Boolean = false,
    val isOutput: Boolean = false,
    val inputMarkers: List<WorkflowConnectionMarker> = emptyList(),
    val outputMarkers: List<WorkflowConnectionMarker> = emptyList(),
    val posX: Float = 0f,
    val posY: Float = 0f,
    val sizeX: Float = 0f,
    val sizeY: Float = 0f,
)

/**
 * 节点在画布上的一条连线。
 *
 * ComfyUI 前端的 rootGraph.links 是 linkId → {origin_id, origin_slot, target_id, target_slot}
 * 的映射；工作流 JSON 自身的 links 则是 [id, origin_id, origin_slot, target_id, target_slot, type]
 * 数组。这里统一成扁平结构，供节点图直接绘制。
 */
data class WorkflowLink(
    val linkId: String,
    val originId: String,
    val originSlot: Int,
    val targetId: String,
    val targetSlot: Int,
)

/**
 * 节点的执行模式，对应 ComfyUI API prompt 里节点对象的 "mode" 字段。
 */
enum class NodeMode(val wireValue: Int, val displayName: String) {
    ALWAYS(0, "始终执行"),
    MUTE(2, "静音"),
    BYPASS(4, "绕过"),
    ;

    companion object {
        fun fromWireValue(value: Int): NodeMode =
            entries.firstOrNull { it.wireValue == value } ?: ALWAYS
    }
}

data class WorkflowConnectionMarker(
    val label: String,
    val type: String,
    val color: String,
    val portName: String,
)

data class WorkflowManifest(
    val fields: List<ParameterField>,
    val nodes: List<WorkflowNode>,
    val links: List<WorkflowLink> = emptyList(),
)

data class WorkflowDocument(
    val entry: WorkflowEntry,
    val rawJson: String,
    val fields: List<ParameterField>,
    val nodes: List<WorkflowNode> = emptyList(),
    val links: List<WorkflowLink> = emptyList(),
    val serverUrl: String = "",
    val baseModified: Double = entry.modified,
    val hasUnsavedChanges: Boolean = false,
    val dirtyFieldKeys: Set<String> = emptySet(),
    val sourceJobId: String? = null,
    val isTemporary: Boolean = false,
)

enum class JobState { RUNNING, PENDING, SUCCESS, ERROR, CANCELLED, UNKNOWN }

data class JobSummary(
    val id: String,
    val state: JobState,
    val workflowName: String = "",
    val workflowPath: String = "",
    val workflowJson: String? = null,
    val progress: Float? = null,
    val currentNode: String? = null,
    val submittedByApp: Boolean = false,
    val message: String = "",
    val durationMillis: Long? = null,
)

enum class AppDestination { PARAMETERS, RESULTS }

data class AppNavigationRequest(
    val id: Long,
    val destination: AppDestination,
    val jobId: String? = null,
)

enum class MediaKind { IMAGE, VIDEO }
enum class ResultSource { LOCAL, CLOUD }

data class ResultMedia(
    val jobId: String,
    val nodeId: String,
    val nodeType: String = "",
    val nodeTitle: String = "",
    val filename: String,
    val subfolder: String,
    val type: String,
    val kind: MediaKind,
    val url: String,
    val createdAt: Long = 0L,
    val taskNumber: Long = 0L,
    val workflowPath: String = "",
    val workflowName: String = "",
    val source: ResultSource = ResultSource.CLOUD,
    val localPath: String? = null,
) {
    fun stableKey(): String = listOf(jobId, nodeId, type, subfolder, filename).joinToString("/")
}

data class CacheOutputRule(
    val serverUrl: String,
    val nodeType: String,
    val nodeTitle: String,
    val workflowPath: String = "",
    val workflowName: String = "",
    val nodeId: String = "",
    val enabled: Boolean = true,
)

data class FieldProblem(val fieldKey: String, val nodeId: String, val message: String)

data class GeneratedPrompt(
    val promptJson: String,
    val workflowJson: String,
    val editableWorkflowJson: String = workflowJson,
)

data class UpdateInfo(
    val tag: String,
    val apkUrl: String,
    val sha256Url: String?,
    val releaseUrl: String,
)

data class AppUiState(
    val preferencesLoaded: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val connectionMessage: String = "尚未连接",
    val connectionStep: Int = 0,
    val connectionTotalSteps: Int = 6,
    val serverInput: String = "",
    val activeServer: ServerProfile? = null,
    val savedServers: List<ServerProfile> = emptyList(),
    val discoveredServers: List<ServerProfile> = emptyList(),
    val systemStats: SystemStats? = null,
    val queueRemaining: Int = 0,
    val workflows: List<WorkflowEntry> = emptyList(),
    val recentWorkflowPaths: List<String> = emptyList(),
    val selectedWorkflow: WorkflowDocument? = null,
    val previewWorkflow: WorkflowDocument? = null,
    val fields: List<ParameterField> = emptyList(),
    val jobs: List<JobSummary> = emptyList(),
    val results: List<ResultMedia> = emptyList(),
    val localResults: List<ResultMedia> = emptyList(),
    val cacheOutputRules: List<CacheOutputRule> = emptyList(),
    val cacheClearedAt: Long = 0L,
    val localDraftCount: Int = 0,
    val favoriteResultKeys: Set<String> = emptySet(),
    val nodeProblems: Map<String, List<String>> = emptyMap(),
    val activeJobId: String? = null,
    val currentExecutingNodeId: String? = null,
    val generationProgress: Float? = null,
    val selectedNodeId: String? = null,
    val nodeModes: Map<String, NodeMode> = emptyMap(),
    val executingNodeIds: Set<String> = emptySet(),
    val nodeProgress: Map<String, Float> = emptyMap(),
    val generationMessage: String = "",
    val navigationRequest: AppNavigationRequest? = null,
    val promptHistory: List<String> = emptyList(),
    val submittedJobIds: Set<String> = emptySet(),
    val autoSaveResults: Boolean = false,
    val localDraftsEnabled: Boolean = false,
    val loggingEnabled: Boolean = false,
    val loading: Boolean = false,
    val scanning: Boolean = false,
    val generating: Boolean = false,
    val bridgeReady: Boolean = false,
    val advancedEditor: Boolean = false,
    val workflowOverwriteRequired: Boolean = false,
    val workflowOverwriteReason: String = "",
    val workflowDraftConflictRequired: Boolean = false,
    val workflowDraftConflictReason: String = "",
    val error: String? = null,
    val notice: String? = null,
    val updateInfo: UpdateInfo? = null,
    val updateChecking: Boolean = false,
    val updateCheckMessage: String? = null,
    val updateDownloading: Boolean = false,
    val updateDownloadProgress: Float? = null,
    val updateDownloadSource: String? = null,

    // ---- 服务器能力与连接诊断 ----
    val serverCapabilities: ServerCapabilities? = null,
    val serverCapabilitiesLoading: Boolean = false,
    val serverCapabilitiesError: String? = null,
    val connectionDiagnosis: Diagnosis? = null,

    // ---- 服务器档案 ----
    val pinnedServerUrls: Set<String> = emptySet(),

    // ---- 服务器资产与历史 ----
    val serverAssets: List<ServerAsset> = emptyList(),
    val serverAssetsTotal: Int = 0,
    val serverAssetsHasMore: Boolean = false,
    val serverAssetsLoading: Boolean = false,
    val serverAssetsError: String? = null,
    val serverAssetTag: AssetTag? = null,
    val serverAssetQuery: String = "",
    val serverJobs: List<ServerJob> = emptyList(),
    val serverJobsTotal: Int = 0,
    val serverJobsHasMore: Boolean = false,
    val serverJobsLoading: Boolean = false,

    // ---- 提示词库 ----
    val promptLibrary: List<PromptEntry> = emptyList(),
    val promptLibraryLoading: Boolean = false,

    // ---- 节点面板 ----
    val nodePanelNodeId: String? = null,
    val nodePanelLoading: Boolean = false,
    val nodePanelError: String? = null,
    val nodePanelChanged: Map<String, String> = emptyMap(),
) {
    /**
     * 作品页的统一列表：服务器历史与手机本地 / 云端作品按文件身份去重后合并。
     *
     * 是派生值而不是状态字段，避免各处忘记刷新导致列表与数据源不一致。
     */
    val mergedResults: List<ResultMedia>
        get() = ServerGalleryMerge.merge(
            baseUrl = activeServer?.baseUrl.orEmpty(),
            cloud = results,
            local = localResults,
            serverAssets = serverAssets,
            serverJobs = serverJobs,
        )
}
