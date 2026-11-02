package com.local.comfyuimobile.data

import com.local.comfyuimobile.model.AssetTag
import com.local.comfyuimobile.model.MediaKind
import com.local.comfyuimobile.model.ResultMedia
import com.local.comfyuimobile.model.ResultSource
import com.local.comfyuimobile.model.ServerAsset
import com.local.comfyuimobile.model.ServerJob
import com.local.comfyuimobile.network.ServerAssetUrls

/**
 * 服务器图片引用：把一个字段值 / 服务器资产拆成 ComfyUI 能识别的三段。
 */
data class ServerImageRef(
    val filename: String,
    val subfolder: String,
    val type: String,
)

/**
 * 作品页的统一视图。
 *
 * 服务器历史（/api/assets 与 /api/jobs）和手机本地 / 云端作品说的是同一批文件，
 * 合并时按 [ResultMedia.stableKey] 里描述"这是哪个文件"的那一段去重：
 * `type/subfolder/filename`。同一张图不管来自云端历史、服务器资产还是手机缓存，
 * 在统一列表里只出现一次。
 *
 * 优先级：云端作品 → 手机本地作品 → 服务器资产 → 服务器历史预览。
 * 云端排在前面，是因为云端条目带 jobId 与 nodeId，能定位到具体任务与部件。
 */
object ServerGalleryMerge {
    /** 文件身份：[ResultMedia.stableKey] 中描述文件本身的那一段。 */
    fun fileKey(type: String, subfolder: String, filename: String): String =
        listOf(type.lowercase(), subfolder, filename).joinToString("/")

    fun fileKeyOf(media: ResultMedia): String = fileKey(media.type, media.subfolder, media.filename)

    fun fileKeyOf(asset: ServerAsset): String = fileKey(
        asset.assetTag()?.toTagValue() ?: AssetTag.INPUT.toTagValue(),
        asset.subFolder(),
        asset.fileName(),
    )

    /**
     * LoadImage 能直接吃的字段值。
     *
     * ComfyUI 的 folder_paths.annotated_filepath 约定：没有注解时相对 input 目录解析，
     * 结尾带 `[output]` / `[input]` / `[temp]` 时改用对应目录（注解前面有一个空格）。
     */
    fun loadImageValue(type: String, subfolder: String, filename: String): String {
        val relative = if (subfolder.isBlank()) filename else "$subfolder/$filename"
        return when (type.lowercase()) {
            "output" -> "$relative [output]"
            "temp" -> "$relative [temp]"
            else -> relative
        }
    }

    /** 把字段值反向解析回服务器图片引用，解析不出来时返回 null。 */
    fun parseImageValue(value: String): ServerImageRef? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        val type = when {
            trimmed.endsWith("[output]") -> "output"
            trimmed.endsWith("[temp]") -> "temp"
            trimmed.endsWith("[input]") -> "input"
            else -> "input"
        }
        val raw = if (type != "input" || trimmed.endsWith("[input]")) {
            trimmed.substringBeforeLast(" [").trim()
        } else {
            trimmed
        }
        if (raw.isBlank()) return null
        val filename = raw.substringAfterLast('/')
        if (filename.isBlank()) return null
        return ServerImageRef(filename = filename, subfolder = raw.substringBeforeLast('/', ""), type = type)
    }

    fun assetToMedia(baseUrl: String, asset: ServerAsset): ResultMedia {
        val type = asset.assetTag()?.toTagValue() ?: AssetTag.INPUT.toTagValue()
        val subfolder = asset.subFolder()
        val filename = asset.fileName()
        return ResultMedia(
            jobId = asset.jobId ?: asset.promptId.orEmpty(),
            nodeId = "",
            nodeType = "",
            nodeTitle = "",
            filename = filename,
            subfolder = subfolder,
            type = type,
            kind = kindOf(asset.mimeType, null),
            url = ServerAssetUrls.viewUrl(baseUrl, filename, subfolder, type),
            createdAt = asset.createdAt,
            taskNumber = 0L,
            source = ResultSource.CLOUD,
            localPath = null,
        )
    }

    fun jobPreviewToMedia(baseUrl: String, job: ServerJob): ResultMedia? {
        val preview = job.previewOutput ?: return null
        return ResultMedia(
            jobId = job.id,
            nodeId = preview.nodeId.orEmpty(),
            nodeType = "",
            nodeTitle = "",
            filename = preview.filename,
            subfolder = preview.subfolder,
            type = preview.type,
            kind = kindOf(null, preview.mediaType),
            url = ServerAssetUrls.viewUrl(baseUrl, preview.filename, preview.subfolder, preview.type),
            createdAt = job.createTime,
            taskNumber = 0L,
            source = ResultSource.CLOUD,
            localPath = null,
        )
    }

    /**
     * 合并四个来源，得到一份按时间倒序的统一作品列表。
     *
     * 只有 output 标签的服务器资产会进入结果列表：input 目录里的文件是素材，
     * 不是生成结果。
     */
    fun merge(
        baseUrl: String,
        cloud: List<ResultMedia>,
        local: List<ResultMedia>,
        serverAssets: List<ServerAsset>,
        serverJobs: List<ServerJob>,
    ): List<ResultMedia> {
        val seen = HashSet<String>()
        val merged = ArrayList<ResultMedia>(cloud.size + local.size + serverAssets.size + serverJobs.size)
        fun push(media: ResultMedia) {
            if (seen.add(fileKeyOf(media))) merged += media
        }
        cloud.forEach { push(it) }
        local.forEach { push(it) }
        serverAssets.filter { it.assetTag() == AssetTag.OUTPUT }.forEach { push(assetToMedia(baseUrl, it)) }
        serverJobs.forEach { job -> jobPreviewToMedia(baseUrl, job)?.let { push(it) } }
        return merged.sortedByDescending { it.createdAt }
    }

    private fun kindOf(mimeType: String?, mediaType: String?): MediaKind {
        val mime = mimeType?.lowercase().orEmpty()
        if (mime.startsWith("video")) return MediaKind.VIDEO
        if (mime.startsWith("image")) return MediaKind.IMAGE
        val media = mediaType?.lowercase().orEmpty()
        if (media.contains("video")) return MediaKind.VIDEO
        return MediaKind.IMAGE
    }
}
