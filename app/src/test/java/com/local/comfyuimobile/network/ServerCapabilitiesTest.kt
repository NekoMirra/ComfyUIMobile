package com.local.comfyuimobile.network

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerCapabilitiesTest {
    private val systemStatsJson = """
        {
          "system": {
            "comfyui_version": "0.43.2",
            "required_frontend_version": "1.57.0",
            "installed_templates_version": "1.0.2",
            "python_version": "3.12.3",
            "pytorch_version": "2.5.1",
            "ram_total": 67108864000,
            "ram_free": 32212254720,
            "os": "posix"
          },
          "devices": [
            {"name": "NVIDIA GeForce RTX 4090", "type": "cuda", "vram_total": 25757220864, "vram_free": 12878610432}
          ]
        }
    """.trimIndent()

    private val featuresJson = """
        {"assets": true, "supports_preview_metadata": true, "max_upload_size": 104857600}
    """.trimIndent()

    private val queueJson = """
        {"queue_running": [[0, "running-id"], [1, "running-id-2"]], "queue_pending": [[0, "pending-id"]]}
    """.trimIndent()

    @Test fun systemStatsParsingReadsEveryReportedField() {
        val runtime = ServerCapabilitiesClient.parseSystemStats(JSONObject(systemStatsJson))

        assertEquals("0.43.2", runtime.comfyVersion)
        assertEquals("1.57.0", runtime.frontendVersion)
        assertEquals("1.0.2", runtime.templatesVersion)
        assertEquals("3.12.3", runtime.pythonVersion)
        assertEquals("2.5.1", runtime.pytorchVersion)
        assertEquals(67108864000L, runtime.ramTotal)
        assertEquals(32212254720L, runtime.ramFree)
        assertEquals("NVIDIA GeForce RTX 4090", runtime.deviceName)
        assertEquals(25757220864L, runtime.vramTotal)
        assertEquals(12878610432L, runtime.vramFree)
    }

    @Test fun systemStatsParsingToleratesAnEmptyDocument() {
        val runtime = ServerCapabilitiesClient.parseSystemStats(JSONObject("{}"))

        assertEquals("", runtime.comfyVersion)
        assertEquals("", runtime.deviceName)
        assertEquals(0L, runtime.ramTotal)
        assertEquals(0L, runtime.vramTotal)
    }

    @Test fun featureParsingReadsFlagsAndUploadLimit() {
        val features = ServerCapabilitiesClient.parseFeatures(JSONObject(featuresJson))

        assertTrue(features.assetsEnabled)
        assertTrue(features.supportsPreviewMetadata)
        assertEquals(104857600L, features.maxUploadSize)
    }

    @Test fun queueParsingCountsRunningAndPendingEntries() {
        val queue = ServerCapabilitiesClient.parseQueue(JSONObject(queueJson))

        assertEquals(2, queue.runningCount)
        assertEquals(1, queue.pendingCount)
    }

    @Test fun fetchReadsAllThreeEndpointsFromARealServer() {
        val server = startServer { path ->
            when (path) {
                "/system_stats" -> 200 to systemStatsJson
                "/features" -> 200 to featuresJson
                "/queue" -> 200 to queueJson
                else -> 404 to "{\"error\":\"not found\"}"
            }
        }
        try {
            val capabilities = runBlocking { ServerCapabilitiesClient.fetch("http://127.0.0.1:${server.port}") }

            assertEquals("0.43.2", capabilities.comfyVersion)
            assertEquals("1.57.0", capabilities.frontendVersion)
            assertEquals("1.0.2", capabilities.templatesVersion)
            assertEquals("3.12.3", capabilities.pythonVersion)
            assertEquals("2.5.1", capabilities.pytorchVersion)
            assertEquals(67108864000L, capabilities.ramTotal)
            assertEquals(32212254720L, capabilities.ramFree)
            assertEquals("NVIDIA GeForce RTX 4090", capabilities.deviceName)
            assertEquals(12878610432L, capabilities.vramFree)
            assertTrue(capabilities.assetsEnabled)
            assertTrue(capabilities.supportsPreviewMetadata)
            assertEquals(104857600L, capabilities.maxUploadSize)
            assertEquals(2, capabilities.queueRunningCount)
            assertEquals(1, capabilities.queuePendingCount)
            assertTrue(capabilities.checkedAt > 0L)
        } finally {
            server.stop()
        }
    }

    @Test fun fetchReportsAnUnsupportedEndpointWithItsOwnException() {
        val server = startServer { path ->
            when (path) {
                "/system_stats" -> 200 to systemStatsJson
                "/features" -> 404 to "{\"error\":\"not found\"}"
                "/queue" -> 200 to queueJson
                else -> 404 to "{}"
            }
        }
        try {
            val error = assertThrows(UnsupportedEndpointException::class.java) {
                runBlocking { ServerCapabilitiesClient.fetch("http://127.0.0.1:${server.port}") }
            }
            assertEquals("/features", error.endpoint)
            assertEquals(404, error.statusCode)
            assertTrue(error.message!!.contains("不受支持"))
        } finally {
            server.stop()
        }
    }

    @Test fun fetchReportsAnHtmlAnswerAsMalformedInsteadOfGuessingValues() {
        val server = startServer { path ->
            when (path) {
                "/system_stats" -> 200 to "<!doctype html><html><body>login</body></html>"
                else -> 200 to "{}"
            }
        }
        try {
            val error = assertThrows(MalformedResponseException::class.java) {
                runBlocking { ServerCapabilitiesClient.fetch("http://127.0.0.1:${server.port}") }
            }
            assertEquals("/system_stats", error.endpoint)
        } finally {
            server.stop()
        }
    }

    @Test fun fetchReportsAnUnreachableHostSeparatelyFromAMissingEndpoint() {
        val closedPort = ServerSocket(0).use { it.localPort }

        val error = assertThrows(ServerUnreachableException::class.java) {
            runBlocking { ServerCapabilitiesClient.fetch("http://127.0.0.1:$closedPort") }
        }
        assertTrue(error.message!!.contains("无法连接服务器"))
    }

    @Test fun frontendCompatibilityUsesSemanticVersionOrder() {
        assertTrue(isSupportedFrontend("1.57.0", "1.57.0"))
        assertTrue(isSupportedFrontend("1.57.0", "1.57.1"))
        assertTrue(isSupportedFrontend("1.57.0", "1.58.0"))
        assertTrue(isSupportedFrontend("1.57", "1.57.0"))
        assertTrue(isSupportedFrontend("1.57.0", "v1.58.0"))
        assertFalse(isSupportedFrontend("1.57.0", "1.56.9"))
        assertFalse(isSupportedFrontend("1.57.0", "1.56"))
        assertFalse(isSupportedFrontend("1.57.0", ""))
        assertTrue(isSupportedFrontend("", ""))
        assertTrue(isSupportedFrontend("", "1.0.0"))
    }

    @Test fun stalenessIsDescribedInChineseBuckets() {
        val now = 1_700_000_000_000L

        assertEquals("尚未更新", describeStaleness(0L, now))
        assertEquals("刚刚更新", describeStaleness(now - 30_000L, now))
        assertEquals("5 分钟前更新", describeStaleness(now - 5 * 60_000L, now))
        assertEquals("3 小时前更新", describeStaleness(now - 3 * 3_600_000L, now))
        assertEquals("2 天前更新", describeStaleness(now - 2 * 86_400_000L, now))
    }

    private fun startServer(handler: (String) -> Pair<Int, String>): TestHttpServer = TestHttpServer(handler)

    /** Minimal HTTP/1.1 server so the probe runs against real sockets. */
    private class TestHttpServer(private val handler: (String) -> Pair<Int, String>) {
        private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        private val worker = thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (error: IOException) {
                    break
                }
                thread(isDaemon = true) { respond(socket) }
            }
        }

        val port: Int get() = server.localPort

        fun stop() {
            server.close()
            worker.join(1_000L)
        }

        private fun respond(socket: Socket) {
            socket.use { open ->
                val reader = open.getInputStream().bufferedReader()
                val requestLine = reader.readLine() ?: return
                val path = requestLine.split(' ').getOrNull(1)?.substringBefore('?') ?: "/"
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }
                val (code, body) = handler(path)
                val bytes = body.toByteArray()
                val contentType = if (body.trimStart().startsWith("<")) "text/html" else "application/json"
                val header = buildString {
                    append("HTTP/1.1 ").append(code).append(' ').append(if (code == 200) "OK" else "Not Found").append("\r\n")
                    append("Content-Type: ").append(contentType).append("\r\n")
                    append("Content-Length: ").append(bytes.size).append("\r\n")
                    append("Connection: close\r\n\r\n")
                }
                open.getOutputStream().apply {
                    write(header.toByteArray())
                    write(bytes)
                    flush()
                }
            }
        }
    }
}
