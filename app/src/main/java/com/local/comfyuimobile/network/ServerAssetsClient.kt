package com.local.comfyuimobile.network

import com.local.comfyuimobile.model.AssetTag
import com.local.comfyuimobile.model.ServerAsset
import com.local.comfyuimobile.model.ServerAssetRef
import com.local.comfyuimobile.model.ServerAssetsPage
import com.local.comfyuimobile.model.ServerFeatures
import com.local.comfyuimobile.model.ServerJob
import com.local.comfyuimobile.model.ServerJobsPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

open class ServerAssetException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class ServerAssetsDisabledException(message: String) : ServerAssetException(message)

class ServerAssetParseException(val field: String, message: String) : ServerAssetException(message)

/** Pure URL builders for the ComfyUI asset, view and thumbnail endpoints. */
object ServerAssetUrls {
    fun viewUrl(baseUrl: String, filename: String, subfolder: String, type: String): String =
        "$baseUrl/view?filename=${encode(filename)}&subfolder=${encode(subfolder)}&type=${encode(type)}"

    fun webpPreviewUrl(baseUrl: String, filename: String, subfolder: String, type: String, quality: Int = 90): String =
        viewUrl(baseUrl, filename, subfolder, type) + "&preview=${encode("webp;$quality")}"

    /**
     * The thumbnail endpoint resolves a bare name against the input folder only, so a folder
     * annotation (`<relative path> [output]`) is needed for output files and for subfolders.
     */
    fun thumbUrl(baseUrl: String, filename: String, subfolder: String, type: String, size: Int = 256): String {
        val relative = if (subfolder.isBlank()) filename else "$subfolder/$filename"
        val folder = annotation(type)
        val value = if (folder == "input" && subfolder.isBlank()) relative else "$relative [$folder]"
        val clamped = size.coerceIn(32, 2048)
        return "$baseUrl/Apt_Preset_IO_LoadImgList_thumb?filename=${encode(value)}&size=$clamped"
    }

    fun assetContentUrl(baseUrl: String, assetId: String): String =
        "$baseUrl/api/assets/${encode(assetId)}/content?disposition=inline"

    fun annotation(type: String): String = when (type.lowercase()) {
        "output" -> "output"
        "temp" -> "temp"
        else -> "input"
    }

    fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}

class ServerAssetsClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build(),
    private val headers: Map<String, String> = emptyMap(),
) {
    private val apiClient = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun features(baseUrl: String): ServerFeatures = withContext(Dispatchers.IO) {
        parseFeatures(execute(request(baseUrl, "/features").get().build()))
    }

    suspend fun listAssets(
        baseUrl: String,
        tag: AssetTag?,
        nameContains: String?,
        limit: Int,
        offset: Int,
    ): ServerAssetsPage = listAssetsByTags(baseUrl, listOfNotNull(tag), nameContains, limit, offset)

    suspend fun listAssetsByTags(
        baseUrl: String,
        tags: List<AssetTag>,
        nameContains: String?,
        limit: Int,
        offset: Int,
        sort: String = "created_at",
        order: String = "desc",
    ): ServerAssetsPage = withContext(Dispatchers.IO) {
        val query = StringBuilder("/api/assets?")
        if (tags.isNotEmpty()) {
            query.append(tags.joinToString("&", prefix = "tags_any=") { ServerAssetUrls.encode(it.toTagValue()) })
            query.append('&')
        }
        if (!nameContains.isNullOrBlank()) {
            query.append("name_contains=").append(ServerAssetUrls.encode(nameContains)).append('&')
        }
        query.append("limit=").append(limit.coerceIn(1, 500)).append('&')
        query.append("offset=").append(offset.coerceAtLeast(0)).append('&')
        query.append("sort=").append(ServerAssetUrls.encode(sort)).append('&')
        query.append("order=").append(ServerAssetUrls.encode(order))
        parseAssetsPage(execute(request(baseUrl, query.toString()).get().build()))
    }

    suspend fun listJobs(
        baseUrl: String,
        status: String?,
        limit: Int,
        offset: Int,
        sortBy: String = "created_at",
        sortOrder: String = "desc",
        workflowId: String? = null,
    ): ServerJobsPage = withContext(Dispatchers.IO) {
        val query = StringBuilder("/api/jobs?")
        if (!status.isNullOrBlank()) query.append("status=").append(ServerAssetUrls.encode(status)).append('&')
        if (!workflowId.isNullOrBlank()) query.append("workflow_id=").append(ServerAssetUrls.encode(workflowId)).append('&')
        query.append("sort_by=").append(ServerAssetUrls.encode(sortBy)).append('&')
        query.append("sort_order=").append(ServerAssetUrls.encode(sortOrder)).append('&')
        query.append("limit=").append(limit.coerceAtLeast(1)).append('&')
        query.append("offset=").append(offset.coerceAtLeast(0))
        parseJobsPage(execute(request(baseUrl, query.toString()).get().build()))
    }

    suspend fun jobDetail(baseUrl: String, jobId: String): ServerJob = withContext(Dispatchers.IO) {
        val root = JSONObject(execute(request(baseUrl, "/api/jobs/${ServerAssetUrls.encode(jobId)}").get().build()))
        parseJob(root)
    }

    suspend fun deleteHistoryEntry(baseUrl: String, promptId: String): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject().put("delete", JSONArray().put(promptId)).toString()
        val request = request(baseUrl, "/history").post(body.toRequestBody(jsonMedia)).build()
        val (code, _) = executeRaw(request, allowedCodes = setOf(200, 204))
        code == 200 || code == 204
    }

    fun viewUrl(baseUrl: String, filename: String, subfolder: String, type: String): String =
        ServerAssetUrls.viewUrl(baseUrl, filename, subfolder, type)

    fun webpPreviewUrl(baseUrl: String, filename: String, subfolder: String, type: String): String =
        ServerAssetUrls.webpPreviewUrl(baseUrl, filename, subfolder, type)

    fun thumbUrl(baseUrl: String, filename: String, subfolder: String, type: String, size: Int = 256): String =
        ServerAssetUrls.thumbUrl(baseUrl, filename, subfolder, type, size)

    fun assetContentUrl(baseUrl: String, assetId: String): String =
        ServerAssetUrls.assetContentUrl(baseUrl, assetId)

    fun parseFeatures(body: String): ServerFeatures {
        val root = JSONObject(body)
        return ServerFeatures(
            assetsEnabled = root.requiredBoolean("assets", "features"),
            supportsPreviewMetadata = root.optionalBoolean("supports_preview_metadata") ?: false,
            maxUploadSize = root.optionalLong("max_upload_size") ?: 0L,
        )
    }

    fun parseAssetsPage(body: String): ServerAssetsPage {
        val root = JSONObject(body)
        return ServerAssetsPage(
            assets = root.requiredArray("assets", "assets").mapObjects { parseAsset(it) },
            total = root.requiredInt("total", "assets"),
            hasMore = root.requiredBoolean("has_more", "assets"),
            nextCursor = root.optionalString("next_cursor"),
        )
    }

    fun parseJobsPage(body: String): ServerJobsPage {
        val root = JSONObject(body)
        val pagination = root.requiredObject("pagination", "jobs")
        return ServerJobsPage(
            jobs = root.requiredArray("jobs", "jobs").mapObjects { parseJob(it) },
            total = pagination.requiredInt("total", "jobs.pagination"),
            hasMore = pagination.requiredBoolean("has_more", "jobs.pagination"),
        )
    }

    fun parseJob(root: JSONObject): ServerJob {
        val preview = root.optJSONObject("preview_output")?.let { output ->
            val metadata = output.optJSONObject("metadata")
            ServerAssetRef(
                filename = output.requiredString("filename", "job.preview_output"),
                subfolder = output.optionalString("subfolder").orEmpty(),
                type = output.requiredString("type", "job.preview_output"),
                width = metadata?.optionalInt("width"),
                height = metadata?.optionalInt("height"),
                assetId = output.optionalString("id"),
                nodeId = output.optionalString("nodeId"),
                mediaType = output.optionalString("mediaType"),
            )
        }
        return ServerJob(
            id = root.requiredString("id", "job"),
            status = root.requiredString("status", "job"),
            priority = root.requiredInt("priority", "job"),
            createTime = root.requiredLong("create_time", "job"),
            executionStartTime = root.optionalLong("execution_start_time"),
            executionEndTime = root.optionalLong("execution_end_time"),
            outputsCount = root.requiredInt("outputs_count", "job"),
            previewableOutputsCount = root.requiredInt("previewable_outputs_count", "job"),
            previewOutput = preview,
            workflowId = root.optionalString("workflow_id"),
        )
    }

    private fun parseAsset(item: JSONObject): ServerAsset {
        val metadata = item.optJSONObject("metadata")
        return ServerAsset(
            id = item.requiredString("id", "asset"),
            name = item.requiredString("name", "asset"),
            relativePath = item.requiredString("loader_path", "asset"),
            sizeBytes = item.requiredLong("size", "asset"),
            mimeType = item.requiredString("mime_type", "asset"),
            tags = item.requiredArray("tags", "asset").mapStrings(),
            previewUrl = item.optionalString("preview_url"),
            width = metadata?.optionalInt("width"),
            height = metadata?.optionalInt("height"),
            createdAt = parseTimestamp(item.requiredString("created_at", "asset")),
            jobId = item.optionalString("job_id"),
            promptId = item.optionalString("prompt_id"),
        )
    }

    private fun parseTimestamp(value: String): Long = try {
        LocalDateTime.parse(value).toInstant(ZoneOffset.UTC).toEpochMilli()
    } catch (error: RuntimeException) {
        throw ServerAssetParseException("created_at", "服务器返回的时间格式无法解析：$value")
    }

    private fun request(baseUrl: String, path: String): Request.Builder {
        val builder = Request.Builder().url(LanAddress.normalize(baseUrl).trimEnd('/') + path)
        headers.forEach { (name, value) -> builder.header(name, value) }
        return builder
    }

    private fun execute(request: Request, allowedCodes: Set<Int> = setOf(200)): String =
        executeRaw(request, allowedCodes).second

    private fun executeRaw(request: Request, allowedCodes: Set<Int> = setOf(200)): Pair<Int, String> {
        apiClient.newCall(request).execute().use { response ->
            require(LanAddress.sameOrigin(response.request.url.toString(), request.url.toString())) {
                "服务跳转到了其他站点，请填写最终 ComfyUI 实例地址；当前不支持平台登录"
            }
            val body = response.body?.string().orEmpty()
            if (response.code in setOf(301, 302, 303, 307, 308)) {
                throw ServerAssetException("服务要求跳转，请直接填写最终的 HTTPS / HTTP 实例地址")
            }
            if (response.code == 401 || response.code == 403) {
                throw ServerAssetException("HTTP ${response.code}：访问被拒绝，可能需要平台登录、访问授权或被代理拦截")
            }
            if (response.code == 503 && isServiceDisabled(body)) {
                throw ServerAssetsDisabledException("服务器未启用资源（assets）功能，请在服务端开启后重试")
            }
            if (response.header("Content-Type").orEmpty().contains("text/html", true) ||
                body.trimStart().startsWith("<!doctype html", true) || body.trimStart().startsWith("<html", true)
            ) {
                throw ServerAssetException("该地址返回了网页或登录页，而非 ComfyUI 接口；请使用实例直连地址")
            }
            if (response.code !in allowedCodes) {
                throw ServerAssetException("HTTP ${response.code}: ${errorMessage(body).take(600)}")
            }
            return response.code to body
        }
    }

    private fun isServiceDisabled(body: String): Boolean = runCatching {
        JSONObject(body).optJSONObject("error")?.optString("code") == "SERVICE_DISABLED"
    }.getOrDefault(false)

    private fun errorMessage(body: String): String = runCatching {
        when (val error = JSONObject(body).opt("error")) {
            is JSONObject -> error.optString("message").ifBlank { error.optString("code") }
            is String -> error
            else -> body
        }
    }.getOrDefault(body)
}

private fun JSONObject.requiredString(key: String, scope: String): String {
    if (!has(key) || isNull(key)) throw ServerAssetParseException(key, "$scope 缺少字段 $key")
    val value = opt(key)
    if (value !is String) throw ServerAssetParseException(key, "$scope 字段 $key 不是字符串")
    return value
}

private fun JSONObject.optionalString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val value = opt(key)
    if (value !is String) throw ServerAssetParseException(key, "字段 $key 不是字符串")
    return value
}

private fun JSONObject.requiredLong(key: String, scope: String): Long {
    if (!has(key) || isNull(key)) throw ServerAssetParseException(key, "$scope 缺少字段 $key")
    val value = opt(key)
    if (value !is Number) throw ServerAssetParseException(key, "$scope 字段 $key 不是数字")
    return value.toLong()
}

private fun JSONObject.optionalLong(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    val value = opt(key)
    if (value !is Number) throw ServerAssetParseException(key, "字段 $key 不是数字")
    return value.toLong()
}

private fun JSONObject.requiredInt(key: String, scope: String): Int {
    if (!has(key) || isNull(key)) throw ServerAssetParseException(key, "$scope 缺少字段 $key")
    val value = opt(key)
    if (value !is Number) throw ServerAssetParseException(key, "$scope 字段 $key 不是数字")
    return value.toInt()
}

private fun JSONObject.optionalInt(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    val value = opt(key)
    if (value !is Number) throw ServerAssetParseException(key, "字段 $key 不是数字")
    return value.toInt()
}

private fun JSONObject.requiredBoolean(key: String, scope: String): Boolean {
    if (!has(key) || isNull(key)) throw ServerAssetParseException(key, "$scope 缺少字段 $key")
    val value = opt(key)
    if (value !is Boolean) throw ServerAssetParseException(key, "$scope 字段 $key 不是布尔值")
    return value
}

private fun JSONObject.optionalBoolean(key: String): Boolean? {
    if (!has(key) || isNull(key)) return null
    val value = opt(key)
    if (value !is Boolean) throw ServerAssetParseException(key, "字段 $key 不是布尔值")
    return value
}

private fun JSONObject.requiredObject(key: String, scope: String): JSONObject {
    if (!has(key) || isNull(key)) throw ServerAssetParseException(key, "$scope 缺少字段 $key")
    val value = opt(key)
    if (value !is JSONObject) throw ServerAssetParseException(key, "$scope 字段 $key 不是对象")
    return value
}

private fun JSONObject.requiredArray(key: String, scope: String): JSONArray {
    if (!has(key) || isNull(key)) throw ServerAssetParseException(key, "$scope 缺少字段 $key")
    val value = opt(key)
    if (value !is JSONArray) throw ServerAssetParseException(key, "$scope 字段 $key 不是数组")
    return value
}

private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> = buildList {
    for (index in 0 until length()) {
        val item = optJSONObject(index) ?: throw ServerAssetParseException("[$index]", "数组第 $index 项不是对象")
        add(transform(item))
    }
}

private fun JSONArray.mapStrings(): List<String> = buildList {
    for (index in 0 until length()) {
        val item = opt(index)
        if (item !is String) throw ServerAssetParseException("[$index]", "数组第 $index 项不是字符串")
        add(item)
    }
}
