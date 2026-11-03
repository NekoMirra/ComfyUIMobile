package com.local.comfyuimobile.model

enum class AssetTag(val value: String) {
    INPUT("input"),
    OUTPUT("output");

    fun toTagValue(): String = value

    companion object {
        fun fromTagValue(value: String): AssetTag? = when (value.trim().lowercase()) {
            INPUT.value -> INPUT
            OUTPUT.value -> OUTPUT
            else -> null
        }
    }
}

data class ServerAsset(
    val id: String,
    val name: String,
    val relativePath: String,
    val sizeBytes: Long,
    val mimeType: String,
    val tags: List<String>,
    val previewUrl: String?,
    val width: Int?,
    val height: Int?,
    val createdAt: Long,
    val jobId: String? = null,
    val promptId: String? = null,
) {
    fun assetTag(): AssetTag? = tags.firstNotNullOfOrNull { AssetTag.fromTagValue(it) }

    fun fileName(): String = relativePath.substringAfterLast('/')

    fun subFolder(): String = relativePath.substringBeforeLast('/', "")
}

data class ServerAssetRef(
    val filename: String,
    val subfolder: String,
    val type: String,
    val width: Int?,
    val height: Int?,
    val assetId: String?,
    val nodeId: String?,
    val mediaType: String?,
)

data class ServerJob(
    val id: String,
    val status: String,
    val priority: Int,
    val createTime: Long,
    val executionStartTime: Long?,
    val executionEndTime: Long?,
    val outputsCount: Int,
    val previewableOutputsCount: Int,
    val previewOutput: ServerAssetRef?,
    val workflowId: String?,
)

data class ServerAssetsPage(
    val assets: List<ServerAsset>,
    val total: Int,
    val hasMore: Boolean,
    val nextCursor: String?,
)

data class ServerJobsPage(
    val jobs: List<ServerJob>,
    val total: Int,
    val hasMore: Boolean,
)

data class ServerFeatures(
    val assetsEnabled: Boolean,
    val supportsPreviewMetadata: Boolean,
    val maxUploadSize: Long,
)
