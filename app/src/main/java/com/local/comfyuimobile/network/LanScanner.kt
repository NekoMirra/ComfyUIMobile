package com.local.comfyuimobile.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.NetworkCapabilities
import com.local.comfyuimobile.model.ServerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.Inet4Address
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Outcome of a single host probe. A host that never answered is not the same
 * problem as a host that answered but is not a ComfyUI instance, and the user
 * needs to see that difference.
 */
sealed class LanScanResult {
    abstract val host: String

    /** The host answered /system_stats and reported a ComfyUI version. */
    data class ComfyUi(override val host: String, val version: String) : LanScanResult()

    /** The host answered /system_stats but did not report a version. */
    data class ConnectedUnknownVersion(override val host: String) : LanScanResult()

    /** The host answered, but it is not a ComfyUI instance. */
    data class NotComfyUi(override val host: String, val reason: String) : LanScanResult()

    /** The host did not answer at all. */
    data class Unreachable(override val host: String, val reason: String) : LanScanResult()
}

class LanScanner(
    private val context: Context,
    @Suppress("unused") private val client: ComfyClient,
) {
    /** Short timeouts: a /24 sweep must not stall the connection screen. */
    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(1, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .callTimeout(3, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    /** Servers that answered a ComfyUI probe, newest discovery first. */
    suspend fun scan(): List<ServerProfile> = scanDetailed().mapNotNull { it.toProfile() }

    /** Every probe outcome, including hosts that did not answer. */
    suspend fun scanDetailed(): List<LanScanResult> = withContext(Dispatchers.IO) {
        val addresses = privateInterfaceAddresses()
        if (addresses.isEmpty()) return@withContext emptyList()
        val semaphore = Semaphore(MAX_CONCURRENT_PROBES)
        coroutineScope {
            addresses.flatMap { LanAddress.subnet24(it.address.hostAddress.orEmpty()) }
                .distinct()
                .map { host ->
                    async {
                        semaphore.withPermit { probeHost(host) }
                    }
                }
                .awaitAll()
        }
    }

    /** Probes one host and reports what answered. */
    suspend fun probeHost(host: String): LanScanResult = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("http://$host:$PORT/system_stats").get().build()
        try {
            probeClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.code == 404 || response.code == 405 ->
                        LanScanResult.NotComfyUi(host, "HTTP ${response.code}")
                    !response.isSuccessful ->
                        LanScanResult.NotComfyUi(host, "HTTP ${response.code}")
                    looksLikeHtml(body) ->
                        LanScanResult.NotComfyUi(host, "返回的是网页而不是 ComfyUI 接口")
                    else -> {
                        val version = runCatching {
                            JSONObject(body).optJSONObject("system")?.optString("comfyui_version").orEmpty()
                        }.getOrDefault("")
                        if (version.isBlank()) LanScanResult.ConnectedUnknownVersion(host) else LanScanResult.ComfyUi(host, version)
                    }
                }
            }
        } catch (error: IOException) {
            LanScanResult.Unreachable(host, error.message ?: error.javaClass.simpleName)
        }
    }

    private fun privateInterfaceAddresses(): List<LinkAddress> {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        return manager.allNetworks.filter { network ->
            manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }.flatMap { network ->
            manager.getLinkProperties(network)?.linkAddresses.orEmpty()
        }.filter { link ->
            val address = link.address
            address is Inet4Address && LanAddress.isTrustedHost(address.hostAddress.orEmpty()) &&
                !address.isLoopbackAddress && link.prefixLength >= 16
        }
    }

    private fun looksLikeHtml(body: String): Boolean {
        val head = body.trimStart()
        return head.startsWith("<!doctype html", ignoreCase = true) ||
            head.startsWith("<html", ignoreCase = true) ||
            head.startsWith("<head", ignoreCase = true)
    }

    private companion object {
        const val PORT = 8188
        const val MAX_CONCURRENT_PROBES = 32
    }
}

/** Turns a probe outcome into a list entry, or null when the host is not usable. */
fun LanScanResult.toProfile(port: Int = 8188): ServerProfile? = when (this) {
    is LanScanResult.ComfyUi -> profile(port, version)
    is LanScanResult.ConnectedUnknownVersion -> profile(port, "")
    is LanScanResult.NotComfyUi, is LanScanResult.Unreachable -> null
}

private fun LanScanResult.profile(port: Int, version: String): ServerProfile {
    val baseUrl = "http://$host:$port"
    return ServerProfile(
        id = UUID.nameUUIDFromBytes(baseUrl.toByteArray()).toString(),
        name = host,
        baseUrl = baseUrl,
        lastSeen = System.currentTimeMillis(),
        comfyVersion = version,
    )
}
