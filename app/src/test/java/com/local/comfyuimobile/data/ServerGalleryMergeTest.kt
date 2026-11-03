package com.local.comfyuimobile.data

import com.local.comfyuimobile.model.AssetTag
import com.local.comfyuimobile.model.MediaKind
import com.local.comfyuimobile.model.ResultMedia
import com.local.comfyuimobile.model.ResultSource
import com.local.comfyuimobile.model.ServerAsset
import com.local.comfyuimobile.model.ServerAssetRef
import com.local.comfyuimobile.model.ServerJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 作品页统一列表的合并去重规则。
 */
class ServerGalleryMergeTest {
    private val baseUrl = "http://127.0.0.1:8188"

    private fun media(
        jobId: String = "job-1",
        nodeId: String = "9",
        filename: String = "shot.png",
        subfolder: String = "",
        type: String = "output",
        createdAt: Long = 100L,
        source: ResultSource = ResultSource.CLOUD,
    ) = ResultMedia(
        jobId = jobId,
        nodeId = nodeId,
        filename = filename,
        subfolder = subfolder,
        type = type,
        kind = MediaKind.IMAGE,
        url = "$baseUrl/view?filename=$filename",
        createdAt = createdAt,
        source = source,
    )

    private fun asset(
        id: String = "asset-1",
        relativePath: String = "shot.png",
        tags: List<String> = listOf("output"),
        jobId: String? = "job-1",
        createdAt: Long = 100L,
    ) = ServerAsset(
        id = id,
        name = relativePath.substringAfterLast('/'),
        relativePath = relativePath,
        sizeBytes = 1024L,
        mimeType = "image/png",
        tags = tags,
        previewUrl = null,
        width = null,
        height = null,
        createdAt = createdAt,
        jobId = jobId,
        promptId = jobId,
    )

    private fun job(
        id: String = "job-1",
        filename: String = "shot.png",
        subfolder: String = "",
        type: String = "output",
        createTime: Long = 100L,
    ) = ServerJob(
        id = id,
        status = "completed",
        priority = 1,
        createTime = createTime,
        executionStartTime = null,
        executionEndTime = null,
        outputsCount = 1,
        previewableOutputsCount = 1,
        previewOutput = ServerAssetRef(
            filename = filename,
            subfolder = subfolder,
            type = type,
            width = null,
            height = null,
            assetId = null,
            nodeId = "9",
            mediaType = "images",
        ),
        workflowId = null,
    )

    @Test
    fun theSameFileFromCloudAndServerIsListedOnlyOnce() {
        val merged = ServerGalleryMerge.merge(
            baseUrl = baseUrl,
            cloud = listOf(media()),
            local = emptyList(),
            serverAssets = listOf(asset()),
            serverJobs = listOf(job()),
        )

        assertEquals(1, merged.size)
        assertEquals("shot.png", merged.first().filename)
        // 云端条目优先：它带 jobId 与 nodeId，能定位到具体任务与部件。
        assertEquals("job-1", merged.first().jobId)
        assertEquals("9", merged.first().nodeId)
    }

    @Test
    fun theLocalCopyOfACloudFileIsNotListedTwice() {
        val merged = ServerGalleryMerge.merge(
            baseUrl = baseUrl,
            cloud = listOf(media()),
            local = listOf(media(source = ResultSource.LOCAL)),
            serverAssets = emptyList(),
            serverJobs = emptyList(),
        )

        assertEquals(1, merged.size)
        assertEquals(ResultSource.CLOUD, merged.first().source)
    }

    @Test
    fun filesWithDifferentNamesAreAllKept() {
        val merged = ServerGalleryMerge.merge(
            baseUrl = baseUrl,
            cloud = listOf(media(filename = "a.png")),
            local = listOf(media(filename = "b.png", source = ResultSource.LOCAL)),
            serverAssets = listOf(asset(id = "asset-2", relativePath = "c.png")),
            serverJobs = emptyList(),
        )

        assertEquals(listOf("a.png", "b.png", "c.png"), merged.map { it.filename })
    }

    @Test
    fun theSameNameInDifferentFoldersIsNotADuplicate() {
        val merged = ServerGalleryMerge.merge(
            baseUrl = baseUrl,
            cloud = listOf(media(filename = "shot.png", subfolder = "")),
            local = listOf(media(filename = "shot.png", subfolder = "sub")),
            serverAssets = emptyList(),
            serverJobs = emptyList(),
        )

        assertEquals(2, merged.size)
    }

    @Test
    fun onlyOutputAssetsBecomeResults() {
        val merged = ServerGalleryMerge.merge(
            baseUrl = baseUrl,
            cloud = emptyList(),
            local = emptyList(),
            serverAssets = listOf(
                asset(id = "in", relativePath = "reference.png", tags = listOf("input")),
                asset(id = "out", relativePath = "result.png", tags = listOf("output")),
            ),
            serverJobs = emptyList(),
        )

        assertEquals(listOf("result.png"), merged.map { it.filename })
    }

    @Test
    fun mergedResultsAreSortedByTimeDescending() {
        val merged = ServerGalleryMerge.merge(
            baseUrl = baseUrl,
            cloud = listOf(media(filename = "old.png", createdAt = 10L)),
            local = emptyList(),
            serverAssets = listOf(asset(id = "new", relativePath = "new.png", createdAt = 99L)),
            serverJobs = emptyList(),
        )

        assertEquals(listOf("new.png", "old.png"), merged.map { it.filename })
    }

    @Test
    fun serverAssetsAndJobsAreConvertedIntoPlayableMedia() {
        val fromAsset = ServerGalleryMerge.assetToMedia(baseUrl, asset(relativePath = "sub/pic.png"))
        assertEquals("pic.png", fromAsset.filename)
        assertEquals("sub", fromAsset.subfolder)
        assertEquals("output", fromAsset.type)
        assertEquals(MediaKind.IMAGE, fromAsset.kind)
        assertTrue(fromAsset.url.startsWith("$baseUrl/view?filename=pic.png"))

        val fromJob = ServerGalleryMerge.jobPreviewToMedia(baseUrl, job(filename = "pic.png", subfolder = "sub"))
        assertEquals("job-1", fromJob?.jobId)
        assertEquals("sub", fromJob?.subfolder)
        assertEquals("pic.png", fromJob?.filename)
    }

    @Test
    fun aJobWithoutPreviewHasNoMedia() {
        val withoutPreview = job().copy(previewOutput = null)
        assertNull(ServerGalleryMerge.jobPreviewToMedia(baseUrl, withoutPreview))
    }

    @Test
    fun theFileKeyIsTheFilePartOfTheStableKey() {
        val item = media(jobId = "job-1", nodeId = "9", filename = "shot.png", subfolder = "sub", type = "output")
        assertEquals(
            "output/sub/shot.png",
            ServerGalleryMerge.fileKeyOf(item),
        )
    }

    @Test
    fun theLoadImageValueUsesTheAnnotationForOutputFiles() {
        assertEquals(
            "sub/pic.png [output]",
            ServerGalleryMerge.loadImageValue("output", "sub", "pic.png"),
        )
        assertEquals(
            "pic.png",
            ServerGalleryMerge.loadImageValue("input", "", "pic.png"),
        )
        assertEquals(
            "sub/pic.png",
            ServerGalleryMerge.loadImageValue("input", "sub", "pic.png"),
        )
    }

    @Test
    fun theFieldValueCanBeParsedBackIntoAServerReference() {
        val output = ServerGalleryMerge.parseImageValue("sub/pic.png [output]")
        assertEquals("pic.png", output?.filename)
        assertEquals("sub", output?.subfolder)
        assertEquals("output", output?.type)

        val input = ServerGalleryMerge.parseImageValue("ComfyUIMobile/uuid/pic.jpg")
        assertEquals("pic.jpg", input?.filename)
        assertEquals("ComfyUIMobile/uuid", input?.subfolder)
        assertEquals("input", input?.type)

        assertNull(ServerGalleryMerge.parseImageValue("   "))
    }

    @Test
    fun theTagOfAnAssetDecidesItsFolder() {
        assertEquals(AssetTag.OUTPUT, asset(tags = listOf("output")).assetTag())
        assertEquals(AssetTag.INPUT, asset(tags = listOf("input")).assetTag())
    }
}
