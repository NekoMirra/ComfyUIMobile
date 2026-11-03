package com.local.comfyuimobile.network

import com.local.comfyuimobile.model.AssetTag
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

class ServerAssetsClientTest {
    private val client = ServerAssetsClient()

    @Test
    fun featuresReportsAssetsCapabilityFromServerResponse() {
        val features = client.parseFeatures(
            """{"supports_preview_metadata":true,"max_upload_size":104857600,"assets":true}"""
        )
        assertTrue(features.assetsEnabled)
        assertTrue(features.supportsPreviewMetadata)
        assertEquals(104857600L, features.maxUploadSize)
    }

    @Test
    fun featuresRejectMissingAssetsFlagInsteadOfGuessing() {
        val failure = runCatching { client.parseFeatures("""{"supports_preview_metadata":true}""") }.exceptionOrNull()
        assertTrue(failure is ServerAssetParseException)
        assertEquals("assets", (failure as ServerAssetParseException).field)
    }

    @Test
    fun assetsPageParsesEveryFieldAndPagination() {
        val page = client.parseAssetsPage(
            """
            {"assets":[
              {"id":"8b2f4fcd-6f52-4b9d-95be-ed7657ab70f8","name":"千问图生图2.1_00153_.png",
               "loader_path":"images/qwen2.1/pose_gallery/results/pose_edit_00007_.png",
               "size":1450708,"mime_type":"image/png","tags":["output"],
               "preview_url":"/api/view?type=output&filename=pose_edit_00007_.png&subfolder=images%2Fqwen2.1",
               "metadata":{"kind":"image","width":896,"height":1184},
               "job_id":"b9045f2e-a920-4763-a2a6-a4815aa1be15","prompt_id":"b9045f2e-a920-4763-a2a6-a4815aa1be15",
               "created_at":"2026-11-02T07:25:03.124697"}
            ],
             "total":807,"has_more":true,"next_cursor":"eyJzIjoiY3JlYXRlZF9hdCIsInYiOiIxNzg5NzM2Njg4MTUzMzkzIiwiaWQiOiIyODEzMzVjMC1kMTA5LTQzMTEtYWEzZS04MjAxNDI4OGNiZWQiLCJvIjoiZGVzYyJ9"}
            """.trimIndent()
        )
        assertEquals(807, page.total)
        assertTrue(page.hasMore)
        assertEquals("eyJzIjoiY3JlYXRlZF9hdCIsInYiOiIxNzg5NzM2Njg4MTUzMzkzIiwiaWQiOiIyODEzMzVjMC1kMTA5LTQzMTEtYWEzZS04MjAxNDI4OGNiZWQiLCJvIjoiZGVzYyJ9", page.nextCursor)
        val asset = page.assets.single()
        assertEquals("8b2f4fcd-6f52-4b9d-95be-ed7657ab70f8", asset.id)
        assertEquals("千问图生图2.1_00153_.png", asset.name)
        assertEquals("images/qwen2.1/pose_gallery/results/pose_edit_00007_.png", asset.relativePath)
        assertEquals(1450708L, asset.sizeBytes)
        assertEquals("image/png", asset.mimeType)
        assertEquals(listOf("output"), asset.tags)
        assertEquals("/api/view?type=output&filename=pose_edit_00007_.png&subfolder=images%2Fqwen2.1", asset.previewUrl)
        assertEquals(896, asset.width)
        assertEquals(1184, asset.height)
        assertEquals("b9045f2e-a920-4763-a2a6-a4815aa1be15", asset.jobId)
        assertEquals("b9045f2e-a920-4763-a2a6-a4815aa1be15", asset.promptId)
        assertEquals(1793604303124L, asset.createdAt)
    }

    @Test
    fun assetsPageKeepsAssetsWithoutMetadataButRejectsMissingName() {
        val page = client.parseAssetsPage(
            """{"assets":[{"id":"0b199a29-596b-4f9b-b003-bdefbbd525b1","name":"千问图生图2.1_00136_.png",
               "loader_path":"千问图生图2.1_00136_.png","size":1314733,"mime_type":"image/png",
               "tags":["missing","output"],"created_at":"2026-11-01T14:48:21.345457"}],
               "total":1,"has_more":false}"""
        )
        val asset = page.assets.single()
        assertNull(asset.previewUrl)
        assertNull(asset.width)
        assertNull(asset.height)
        assertEquals(listOf("missing", "output"), asset.tags)

        val failure = runCatching {
            client.parseAssetsPage(
                """{"assets":[{"id":"x","loader_path":"a.png","size":1,"mime_type":"image/png",
                   "tags":[],"created_at":"2026-11-01T14:48:21.345457"}],"total":1,"has_more":false}"""
            )
        }.exceptionOrNull()
        assertTrue(failure is ServerAssetParseException)
        assertEquals("name", (failure as ServerAssetParseException).field)
    }

    @Test
    fun assetsPageRejectsWronglyTypedFields() {
        val failure = runCatching {
            client.parseAssetsPage(
                """{"assets":[{"id":"x","name":"a.png","loader_path":"a.png","size":"12",
                   "mime_type":"image/png","tags":[],"created_at":"2026-11-01T14:48:21.345457"}],
                   "total":1,"has_more":false}"""
            )
        }.exceptionOrNull()
        assertTrue(failure is ServerAssetParseException)
        assertEquals("size", (failure as ServerAssetParseException).field)
    }

    @Test
    fun jobsPageParsesJobFieldsAndPreviewOutput() {
        val page = client.parseJobsPage(
            """
            {"jobs":[{"id":"454f31cf-6c5b-4a45-b084-bc392b5fb0e9","status":"completed","priority":6,
               "create_time":1793606683617,"execution_start_time":1793606683620,
               "execution_end_time":1793606733763,"outputs_count":1,"previewable_outputs_count":1,
               "preview_output":{"filename":"千问图生图2.1_00153_.png","subfolder":"","type":"output",
                 "metadata":{"kind":"image","width":1184,"height":896},
                 "id":"8b2f4fcd-6f52-4b9d-95be-ed7657ab70f8","nodeId":"9","mediaType":"images"},
               "workflow_id":"b058853e-412f-4f02-95e2-abe2a40ebc2f"}],
             "pagination":{"offset":0,"limit":1,"total":12,"has_more":true}}
            """.trimIndent()
        )
        assertEquals(12, page.total)
        assertTrue(page.hasMore)
        val job = page.jobs.single()
        assertEquals("454f31cf-6c5b-4a45-b084-bc392b5fb0e9", job.id)
        assertEquals("completed", job.status)
        assertEquals(6, job.priority)
        assertEquals(1793606683617L, job.createTime)
        assertEquals(1793606683620L, job.executionStartTime)
        assertEquals(1793606733763L, job.executionEndTime)
        assertEquals(1, job.outputsCount)
        assertEquals(1, job.previewableOutputsCount)
        assertEquals("b058853e-412f-4f02-95e2-abe2a40ebc2f", job.workflowId)
        val preview = job.previewOutput!!
        assertEquals("千问图生图2.1_00153_.png", preview.filename)
        assertEquals("", preview.subfolder)
        assertEquals("output", preview.type)
        assertEquals(1184, preview.width)
        assertEquals(896, preview.height)
        assertEquals("8b2f4fcd-6f52-4b9d-95be-ed7657ab70f8", preview.assetId)
        assertEquals("9", preview.nodeId)
        assertEquals("images", preview.mediaType)
    }

    @Test
    fun jobsPageAcceptsJobWithoutPreviewOutputAndRejectsMissingStatus() {
        val page = client.parseJobsPage(
            """{"jobs":[{"id":"j1","status":"pending","priority":0,"create_time":1793606683617,
               "outputs_count":0,"previewable_outputs_count":0}],
               "pagination":{"offset":0,"limit":1,"total":1,"has_more":false}}"""
        )
        val job = page.jobs.single()
        assertNull(job.previewOutput)
        assertNull(job.executionStartTime)
        assertNull(job.executionEndTime)
        assertNull(job.workflowId)

        val failure = runCatching {
            client.parseJobsPage(
                """{"jobs":[{"id":"j1","priority":0,"create_time":1,"outputs_count":0,"previewable_outputs_count":0}],
                   "pagination":{"offset":0,"limit":1,"total":1,"has_more":false}}"""
            )
        }.exceptionOrNull()
        assertTrue(failure is ServerAssetParseException)
        assertEquals("status", (failure as ServerAssetParseException).field)
    }

    @Test
    fun liveRequestParsesFeaturesFromServer() = runBlocking {
        val server = ScriptedServer(
            200, """{"assets":true,"supports_preview_metadata":true,"max_upload_size":104857600}""",
        )
        server.use {
            val features = client.features(it.baseUrl)
            assertTrue(features.assetsEnabled)
            assertTrue(features.supportsPreviewMetadata)
            assertEquals(104857600L, features.maxUploadSize)
            assertEquals("/features", it.requests.single().first)
        }
    }

    @Test
    fun liveRequestParsesAssetPageAndSendsTagAndSearchFilters() = runBlocking {
        val body = """{"assets":[{"id":"a1","name":"a.png","loader_path":"a.png","size":10,
            "mime_type":"image/png","tags":["input","uploaded"],"metadata":{"width":8,"height":9},
            "created_at":"2026-11-02T07:25:03.124697"}],"total":1,"has_more":false}"""
        val server = ScriptedServer(200, body)
        server.use {
            val page = client.listAssets(it.baseUrl, AssetTag.INPUT, "猫", 30, 60)
            assertEquals(1, page.total)
            assertEquals("a1", page.assets.single().id)
            assertEquals(8, page.assets.single().width)
            val path = it.requests.single().first
            assertTrue(path.startsWith("/api/assets?"))
            assertTrue(path.contains("tags_any=input"))
            assertTrue(path.contains("name_contains=%E7%8C%AB"))
            assertTrue(path.contains("limit=30"))
            assertTrue(path.contains("offset=60"))
        }
    }

    @Test
    fun liveRequestParsesJobPageAndSendsStatusFilter() = runBlocking {
        val body = """{"jobs":[{"id":"j1","status":"completed","priority":6,
            "create_time":1793606683617,"outputs_count":1,"previewable_outputs_count":1,
            "preview_output":{"filename":"a.png","subfolder":"","type":"output"}}],
            "pagination":{"offset":0,"limit":40,"total":1,"has_more":false}}"""
        val server = ScriptedServer(200, body)
        server.use {
            val page = client.listJobs(it.baseUrl, "pending,in_progress,completed,failed", 40, 0)
            assertEquals(1, page.total)
            assertEquals("j1", page.jobs.single().id)
            val path = it.requests.single().first
            assertTrue(path.startsWith("/api/jobs?"))
            assertTrue(path.contains("status=pending%2Cin_progress%2Ccompleted%2Cfailed"))
            assertTrue(path.contains("limit=40"))
        }
    }

    @Test
    fun serviceDisabledResponseRaisesItsOwnFailureInsteadOfAnEmptyPage() = runBlocking {
        val server = ScriptedServer(503, """{"error":{"code":"SERVICE_DISABLED","message":"Assets system is disabled.","details":{}}}""",
        )
        server.use {
            val failure = runCatching { client.listAssets(it.baseUrl, AssetTag.INPUT, null, 20, 0) }.exceptionOrNull()
            assertTrue(failure is ServerAssetsDisabledException)
            assertTrue((failure as ServerAssetsDisabledException).message!!.contains("未启用"))
        }
    }

    @Test
    fun otherErrorResponsesKeepTheServerMessage() = runBlocking {
        val server = ScriptedServer(400, """{"error":{"code":"INVALID_QUERY","message":"Validation failed.","details":{}}}""")
        server.use {
            val failure = runCatching { client.listAssets(it.baseUrl, AssetTag.INPUT, null, 0, 0) }.exceptionOrNull()
            assertTrue(failure is ServerAssetException)
            assertTrue(failure !is ServerAssetsDisabledException)
            assertTrue(failure!!.message!!.contains("HTTP 400"))
            assertTrue(failure.message!!.contains("Validation failed."))
        }
    }

    @Test
    fun deletingAHistoryEntryPostsTheDeleteBody() = runBlocking {
        val server = ScriptedServer(200, "")
        server.use {
            assertTrue(client.deleteHistoryEntry(it.baseUrl, "prompt-1"))
            val request = it.requests.single()
            assertEquals("/history", request.first)
            assertEquals("POST", request.second)
            assertEquals(
                listOf("prompt-1"),
                JSONObject(request.third).getJSONArray("delete").let { array -> List(array.length()) { array.getString(it) } },
            )
        }
    }

    @Test
    fun jobDetailReadsASingleJob() = runBlocking {
        val server = ScriptedServer(
            200, """{"id":"j1","status":"completed","priority":6,"create_time":1793606683617,
                "outputs_count":1,"previewable_outputs_count":1,"workflow_id":"w1"}""",
        )
        server.use {
            val job = client.jobDetail(it.baseUrl, "j1")
            assertEquals("j1", job.id)
            assertEquals("w1", job.workflowId)
            assertEquals("/api/jobs/j1", it.requests.single().first)
        }
    }

    @Test
    fun viewUrlEncodesChineseNamesAndSubfolders() {
        val url = client.viewUrl("http://127.0.0.1:8188", "千问图生图2.1_00153_.png", "images/qwen2.1", "output")
        assertEquals(
            "http://127.0.0.1:8188/view?filename=%E5%8D%83%E9%97%AE%E5%9B%BE%E7%94%9F%E5%9B%BE2.1_00153_.png" +
                "&subfolder=images%2Fqwen2.1&type=output",
            url,
        )
    }

    @Test
    fun webpPreviewUrlAddsConvertedPreviewParameter() {
        val url = client.webpPreviewUrl("http://127.0.0.1:8188", "a.png", "", "output")
        assertEquals("http://127.0.0.1:8188/view?filename=a.png&subfolder=&type=output&preview=webp%3B90", url)
    }

    @Test
    fun thumbUrlUsesFolderAnnotationForSubfoldersAndKeepsForwardSlashes() {
        val url = client.thumbUrl("http://127.0.0.1:8188", "pose_edit_00007_.png", "images/qwen2.1/pose_gallery/results", "output")
        assertEquals(
            "http://127.0.0.1:8188/Apt_Preset_IO_LoadImgList_thumb" +
                "?filename=images%2Fqwen2.1%2Fpose_gallery%2Fresults%2Fpose_edit_00007_.png%20%5Boutput%5D&size=256",
            url,
        )
    }

    @Test
    fun thumbUrlUsesBareNameForRootInputFilesAndAnnotatesTheRest() {
        val rootInput = client.thumbUrl("http://127.0.0.1:8188", "a.png", "", "input", size = 10)
        assertEquals(
            "http://127.0.0.1:8188/Apt_Preset_IO_LoadImgList_thumb?filename=a.png&size=32",
            rootInput,
        )
        val rootOutput = client.thumbUrl("http://127.0.0.1:8188", "a.png", "", "output")
        assertEquals(
            "http://127.0.0.1:8188/Apt_Preset_IO_LoadImgList_thumb?filename=a.png%20%5Boutput%5D&size=256",
            rootOutput,
        )
        val large = client.thumbUrl("http://127.0.0.1:8188", "a.png", "", "output", size = 5000)
        assertTrue(large.endsWith("&size=2048"))
    }

    @Test
    fun assetContentUrlPointsAtInlineAssetContent() {
        assertEquals(
            "http://127.0.0.1:8188/api/assets/8b2f4fcd-6f52-4b9d-95be-ed7657ab70f8/content?disposition=inline",
            client.assetContentUrl("http://127.0.0.1:8188", "8b2f4fcd-6f52-4b9d-95be-ed7657ab70f8"),
        )
    }

    @Test
    fun assetTagConvertsBetweenEnumAndServerValues() {
        assertEquals("input", AssetTag.INPUT.toTagValue())
        assertEquals("output", AssetTag.OUTPUT.toTagValue())
        assertEquals(AssetTag.INPUT, AssetTag.fromTagValue("input"))
        assertEquals(AssetTag.OUTPUT, AssetTag.fromTagValue("OUTPUT"))
        assertNull(AssetTag.fromTagValue("preview"))
    }
}

/** Minimal HTTP server that replays one scripted response and records the requests it received. */
private class ScriptedServer(private val status: Int, private val body: String) : AutoCloseable {
    private val serverSocket = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
    val requests = CopyOnWriteArrayList<Triple<String, String, String>>()
    private val worker = thread(isDaemon = true) { serve() }

    val baseUrl: String get() = "http://127.0.0.1:${serverSocket.localPort}"

    private fun serve() {
        try {
            while (!serverSocket.isClosed) {
                val socket = serverSocket.accept()
                handle(socket)
            }
        } catch (error: Throwable) {
            if (!serverSocket.isClosed) throw error
        }
    }

    private fun handle(socket: Socket) {
        socket.use { connection ->
            connection.soTimeout = 5_000
            val input = connection.getInputStream()
            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(' ')
            val method = parts.getOrNull(0) ?: return
            val path = parts.getOrNull(1) ?: return
            var contentLength = 0
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line.substringAfter(':').trim().toInt()
                }
            }
            val payload = ByteArray(contentLength)
            var read = 0
            while (read < contentLength) {
                val count = input.read(payload, read, contentLength - read)
                if (count < 0) break
                read += count
            }
            requests += Triple(path, method, String(payload, Charsets.UTF_8))
            val response = body.toByteArray(Charsets.UTF_8)
            connection.getOutputStream().apply {
                write(
                    ("HTTP/1.1 $status ${reason(status)}\r\nContent-Type: application/json\r\n" +
                        "Content-Length: ${response.size}\r\nConnection: close\r\n\r\n").toByteArray()
                )
                write(response)
                flush()
            }
        }
    }

    private fun readLine(input: java.io.InputStream): String? {
        val builder = StringBuilder()
        while (true) {
            val value = input.read()
            if (value < 0) return if (builder.isEmpty()) null else builder.toString()
            if (value == '\n'.code) return builder.toString().removeSuffix("\r")
            builder.append(value.toChar())
        }
    }

    private fun reason(code: Int): String = when (code) {
        200 -> "OK"
        400 -> "Bad Request"
        503 -> "Service Unavailable"
        else -> "Status"
    }

    override fun close() {
        serverSocket.close()
        worker.join(1_000)
    }
}
