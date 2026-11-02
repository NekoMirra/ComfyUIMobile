package com.local.comfyuimobile.data

import com.local.comfyuimobile.model.ServerAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 服务器资产分页的状态推进。
 */
class ServerAssetPaginationTest {
    private fun asset(id: String) = ServerAsset(
        id = id,
        name = "$id.png",
        relativePath = "$id.png",
        sizeBytes = 1L,
        mimeType = "image/png",
        tags = listOf("output"),
        previewUrl = null,
        width = null,
        height = null,
        createdAt = 0L,
    )

    @Test
    fun theOffsetAdvancesByOnePage() {
        assertEquals(60, ServerAssetPagination.nextOffset(currentOffset = 0, pageSize = 60))
        assertEquals(120, ServerAssetPagination.nextOffset(currentOffset = 60, pageSize = 60))
    }

    @Test
    fun mergingPagesKeepsTheOrderAndDropsRepeatedIds() {
        val first = listOf(asset("a"), asset("b"))
        val second = listOf(asset("b"), asset("c"))

        val merged = ServerAssetPagination.merge(first, second)

        assertEquals(listOf("a", "b", "c"), merged.map { it.id })
    }

    @Test
    fun mergingIntoAnEmptyListReturnsThePageAsIs() {
        val page = listOf(asset("a"))
        assertEquals(page, ServerAssetPagination.merge(emptyList(), page))
    }

    @Test
    fun thereIsNoNextPageWhenEverythingHasBeenLoaded() {
        assertTrue(ServerAssetPagination.hasMore(loaded = 60, total = 120, serverHasMore = true))
        assertFalse(ServerAssetPagination.hasMore(loaded = 120, total = 120, serverHasMore = true))
        assertFalse(ServerAssetPagination.hasMore(loaded = 60, total = 120, serverHasMore = false))
    }

    @Test
    fun jobsUseTheSamePaginationRules() {
        val first = listOf("job-1")
        val second = listOf("job-1", "job-2")

        val merged = ServerAssetPagination.merge(first, second) { it }

        assertEquals(listOf("job-1", "job-2"), merged)
        assertEquals(40, ServerAssetPagination.nextOffset(currentOffset = 0, pageSize = 40))
    }
}
