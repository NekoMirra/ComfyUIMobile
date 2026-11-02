package com.local.comfyuimobile.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * Snapshot of what a ComfyUI instance can do. Every field is read from the
 * server, nothing is guessed: a missing value stays empty / zero so the UI can
 * say "unknown" instead of inventing a version number.
 */
data class ServerCapabilities(
    val comfyVersion: String,
    val frontendVersion: String,
    val templatesVersion: String,
    val pythonVersion: String,
    val pytorchVersion: String,
    val ramTotal: Long,
    val ramFree: Long,
    val deviceName: String,
    val vramTotal: Long,
    val vramFree: Long,
    val assetsEnabled: Boolean,
    val supportsPreviewMetadata: Boolean,
    val maxUploadSize: Long,
    val queueRunningCount: Int,
    val queuePendingCount: Int,
    val checkedAt: Long,
)

/** Runtime information reported by GET /system_stats. */
data class ServerRuntimeInfo(
    val comfyVersion: String,
    val frontendVersion: String,
    val templatesVersion: String,
    val pythonVersion: String,
    val pytorchVersion: String,
    val ramTotal: Long,
    val ramFree: Long,
    val deviceName: String,
    val vramTotal: Long,
    val vramFree: Long,
)

/** Optional capabilities reported by GET /features. */
data class ServerFeatureInfo(
    val assetsEnabled: Boolean,
    val supportsPreviewMetadata: Boolean,
    val maxUploadSize: Long,
)

/** Queue depth reported by GET /queue. */
data class ServerQueueInfo(
    val runningCount: Int,
    val pendingCount: Int,
)

/** Base class for every failure of the capability probe. */
open class ServerCapabilityException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The endpoint exists but the server does not implement it (HTTP 404 / 405). */
class UnsupportedEndpointException(
    val endpoint: String,
    val statusCode: Int,
    message: String,
) : ServerCapabilityException(message)

/** The host could not be reached at all (refused, unknown host, no route). */
class ServerUnreachableException(message: String, cause: Throwable? = null) : ServerCapabilityException(message, cause)

/** The server accepted the connection but did not answer in time. */
class ServerTimeoutException(message: String, cause: Throwable? = null) : ServerCapabilityException(message, cause)

/** The TLS handshake failed, typically an untrusted or mismatched certificate. */
class ServerTlsException(message: String, cause: Throwable? = null) : ServerCapabilityException(message, cause)

/** The answer was not the JSON document we asked for (HTML login page, garbage). */
class MalformedResponseException(
    val endpoint: String,
    message: String,
) : ServerCapabilityException(message)

/**
 * Reads the capability endpoints of a ComfyUI instance.
 *
 * The client is deliberately separate from [ComfyClient]: the capability probe
 * is a diagnostic path that must keep working while the regular client is
 * pointed at another server, and it must not share the websocket state.
 */
object ServerCapabilitiesClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    /**
     * Reads /system_stats, /features and /queue concurrently.
     *
     * @throws ServerUnreachableException when the host cannot be reached.
     * @throws ServerTimeoutException when the server does not answer in time.
     * @throws ServerTlsException when the certificate cannot be verified.
     * @throws UnsupportedEndpointException when the server does not implement the endpoint.
     * @throws MalformedResponseException when the answer is not the expected JSON.
     */
    suspend fun fetch(baseUrl: String): ServerCapabilities = withContext(Dispatchers.IO) {
        val (runtime, features, queue) = fetchAll(baseUrl)
        ServerCapabilities(
            comfyVersion = runtime.comfyVersion,
            frontendVersion = runtime.frontendVersion,
            templatesVersion = runtime.templatesVersion,
            pythonVersion = runtime.pythonVersion,
            pytorchVersion = runtime.pytorchVersion,
            ramTotal = runtime.ramTotal,
            ramFree = runtime.ramFree,
            deviceName = runtime.deviceName,
            vramTotal = runtime.vramTotal,
            vramFree = runtime.vramFree,
            assetsEnabled = features.assetsEnabled,
            supportsPreviewMetadata = features.supportsPreviewMetadata,
            maxUploadSize = features.maxUploadSize,
            queueRunningCount = queue.runningCount,
            queuePendingCount = queue.pendingCount,
            checkedAt = System.currentTimeMillis(),
        )
    }

    suspend fun fetchRuntime(baseUrl: String): ServerRuntimeInfo {
        val normalized = LanAddress.normalize(baseUrl)
        return parseSystemStats(getJson(normalized, "/system_stats"))
    }

    suspend fun fetchFeatures(baseUrl: String): ServerFeatureInfo {
        val normalized = LanAddress.normalize(baseUrl)
        return parseFeatures(getJson(normalized, "/features"))
    }

    suspend fun fetchQueue(baseUrl: String): ServerQueueInfo {
        val normalized = LanAddress.normalize(baseUrl)
        return parseQueue(getJson(normalized, "/queue"))
    }

    /** Reads the three endpoints in parallel; the first failure aborts the probe. */
    suspend fun fetchAll(baseUrl: String): Triple<ServerRuntimeInfo, ServerFeatureInfo, ServerQueueInfo> =
        withContext(Dispatchers.IO) {
            val normalized = LanAddress.normalize(baseUrl)
            coroutineScope {
                val runtime = async { parseSystemStats(getJson(normalized, "/system_stats")) }
                val features = async { parseFeatures(getJson(normalized, "/features")) }
                val queue = async { parseQueue(getJson(normalized, "/queue")) }
                Triple(runtime.await(), features.await(), queue.await())
            }
        }

    fun parseSystemStats(root: JSONObject): ServerRuntimeInfo {
        val system = root.optJSONObject("system")
        val devices = root.optJSONArray("devices")
        var deviceName = ""
        var vramTotal = 0L
        var vramFree = 0L
        if (devices != null) {
            var bestVram = -1L
            for (index in 0 until devices.length()) {
                val device = devices.optJSONObject(index) ?: continue
                val total = device.optLong("vram_total", 0L)
                val name = device.optString("name")
                if (name.isNotBlank() && (bestVram < 0L || total > bestVram)) {
                    bestVram = total
                    deviceName = name
                    vramTotal = total
                    vramFree = device.optLong("vram_free", 0L)
                }
            }
        }
        return ServerRuntimeInfo(
            comfyVersion = system?.optString("comfyui_version").orEmpty(),
            frontendVersion = system?.optString("required_frontend_version").orEmpty(),
            templatesVersion = system?.optString("installed_templates_version").orEmpty(),
            pythonVersion = system?.optString("python_version").orEmpty(),
            pytorchVersion = system?.optString("pytorch_version").orEmpty(),
            ramTotal = system?.optLong("ram_total", 0L) ?: 0L,
            ramFree = system?.optLong("ram_free", 0L) ?: 0L,
            deviceName = deviceName,
            vramTotal = vramTotal,
            vramFree = vramFree,
        )
    }

    fun parseFeatures(root: JSONObject): ServerFeatureInfo = ServerFeatureInfo(
        assetsEnabled = root.optBoolean("assets", false),
        supportsPreviewMetadata = root.optBoolean("supports_preview_metadata", false),
        maxUploadSize = root.optLong("max_upload_size", 0L),
    )

    fun parseQueue(root: JSONObject): ServerQueueInfo = ServerQueueInfo(
        runningCount = root.optJSONArray("queue_running")?.length() ?: 0,
        pendingCount = root.optJSONArray("queue_pending")?.length() ?: 0,
    )

    private fun getJson(baseUrl: String, path: String): JSONObject {
        val request = Request.Builder().url(baseUrl + path).get().build()
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    if (response.code == 404 || response.code == 405) {
                        throw UnsupportedEndpointException(path, response.code, "端点 $path 不受支持（HTTP ${response.code}）")
                    }
                    throw ServerCapabilityException("HTTP ${response.code}：${body.take(300)}")
                }
                if (looksLikeHtml(body)) {
                    throw MalformedResponseException(path, "端点 $path 返回的是网页而不是 JSON，可能是平台登录页")
                }
                return try {
                    JSONObject(body)
                } catch (error: Exception) {
                    throw MalformedResponseException(path, "端点 $path 返回的内容不是合法 JSON")
                }
            }
        } catch (error: ServerCapabilityException) {
            throw error
        } catch (error: SSLException) {
            throw ServerTlsException("TLS 证书校验失败：${error.message ?: "证书不可信"}", error)
        } catch (error: java.net.SocketTimeoutException) {
            throw ServerTimeoutException("请求 $path 超时：${error.message ?: "服务器未在超时时间内响应"}", error)
        } catch (error: IOException) {
            throw ServerUnreachableException("无法连接服务器读取 $path：${error.message ?: error.javaClass.simpleName}", error)
        }
    }

    private fun looksLikeHtml(body: String): Boolean {
        val head = body.trimStart()
        return head.startsWith("<!doctype html", ignoreCase = true) ||
            head.startsWith("<html", ignoreCase = true) ||
            head.startsWith("<head", ignoreCase = true)
    }
}

/**
 * Compares two frontend versions with semantic versioning rules.
 *
 * A blank [required] means the server did not state a requirement, so nothing
 * can be wrong. A blank [installed] means the version is unknown, which can
 * never be proven compatible.
 */
fun isSupportedFrontend(required: String, installed: String): Boolean {
    val requiredVersion = parseVersion(required)
    if (requiredVersion == null) return true
    val installedVersion = parseVersion(installed)
        ?: return false
    return compareVersions(installedVersion, requiredVersion) >= 0
}

/** Human readable age of a capability snapshot, in Chinese. */
fun describeStaleness(checkedAt: Long, now: Long): String {
    if (checkedAt <= 0L) return "尚未更新"
    val elapsed = (now - checkedAt).coerceAtLeast(0L)
    return when {
        elapsed < 60_000L -> "刚刚更新"
        elapsed < 3_600_000L -> "${elapsed / 60_000L} 分钟前更新"
        elapsed < 86_400_000L -> "${elapsed / 3_600_000L} 小时前更新"
        else -> "${elapsed / 86_400_000L} 天前更新"
    }
}

private data class SemanticVersion(val major: Int, val minor: Int, val patch: Int)

private fun parseVersion(value: String): SemanticVersion? {
    val trimmed = value.trim().removePrefix("v").removePrefix("V")
    if (trimmed.isEmpty()) return null
    val core = trimmed.takeWhile { it.isDigit() || it == '.' }
    val parts = core.split('.').filter { it.isNotEmpty() }
    if (parts.isEmpty()) return null
    val numbers = parts.map { part -> part.toIntOrNull() ?: return null }
    return SemanticVersion(
        major = numbers.getOrElse(0) { 0 },
        minor = numbers.getOrElse(1) { 0 },
        patch = numbers.getOrElse(2) { 0 },
    )
}

private fun compareVersions(left: SemanticVersion, right: SemanticVersion): Int {
    if (left.major != right.major) return left.major.compareTo(right.major)
    if (left.minor != right.minor) return left.minor.compareTo(right.minor)
    return left.patch.compareTo(right.patch)
}
