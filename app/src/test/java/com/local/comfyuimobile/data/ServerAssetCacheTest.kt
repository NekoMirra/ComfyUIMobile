package com.local.comfyuimobile.data

import android.app.Application
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ServerAssetCacheTest {
    private fun cache(): ServerAssetCache = ServerAssetCache(RuntimeEnvironment.getApplication())

    private fun entry(
        assetId: String = "a1",
        filename: String = "a.png",
        subfolder: String = "",
        type: String = "output",
        seenAt: Long = 1_000L,
    ) = ServerAssetCacheEntry(
        assetId = assetId,
        filename = filename,
        subfolder = subfolder,
        type = type,
        promptId = "p1",
        jobId = "j1",
        width = 1184,
        height = 896,
        seenAt = seenAt,
        sizeBytes = 1_300_681L,
    )

    @Test
    fun writtenEntriesAreReadBackUnchanged() = runBlocking {
        val cache = cache()
        val written = listOf(
            entry(assetId = "a1", filename = "a.png", seenAt = 10L),
            entry(assetId = "a2", filename = "b.png", subfolder = "images/qwen2.1", seenAt = 20L),
        )
        cache.append(written)

        val loaded = cache.load()
        assertEquals(2, loaded.size)
        val newest = loaded.first()
        assertEquals("a2", newest.assetId)
        assertEquals("b.png", newest.filename)
        assertEquals("images/qwen2.1", newest.subfolder)
        assertEquals("output", newest.type)
        assertEquals("p1", newest.promptId)
        assertEquals("j1", newest.jobId)
        assertEquals(1184, newest.width)
        assertEquals(896, newest.height)
        assertEquals(20L, newest.seenAt)
        assertEquals(1_300_681L, newest.sizeBytes)
    }

    @Test
    fun theSameFolderTripleIsStoredOnlyOnce() = runBlocking {
        val cache = cache()
        cache.append(listOf(entry(assetId = "a1", filename = "a.png", seenAt = 10L)))
        cache.append(listOf(entry(assetId = "a2", filename = "a.png", seenAt = 30L)))

        val loaded = cache.load()
        assertEquals(1, loaded.size)
        assertEquals("a2", loaded.single().assetId)
        assertEquals(30L, loaded.single().seenAt)
    }

    @Test
    fun differentFoldersKeepDifferentEntries() = runBlocking {
        val cache = cache()
        cache.append(listOf(entry(assetId = "a1", filename = "a.png", seenAt = 10L)))
        cache.append(listOf(entry(assetId = "a2", filename = "a.png", subfolder = "sub", seenAt = 20L)))
        cache.append(listOf(entry(assetId = "a3", filename = "a.png", type = "input", seenAt = 30L)))

        assertEquals(3, cache.load().size)
    }

    @Test
    fun removingAnEntryDropsOnlyThatFolderTriple() = runBlocking {
        val cache = cache()
        cache.append(
            listOf(
                entry(assetId = "a1", filename = "a.png", seenAt = 10L),
                entry(assetId = "a2", filename = "b.png", seenAt = 20L),
            )
        )

        assertTrue(cache.remove("output//a.png"))
        val loaded = cache.load()
        assertEquals(1, loaded.size)
        assertEquals("b.png", loaded.single().filename)
        assertFalse(cache.remove("output//a.png"))
    }

    @Test
    fun clearingRemovesEverythingAndTheIndexFile() = runBlocking {
        val cache = cache()
        cache.append(listOf(entry()))
        assertTrue(cache.sizeBytes() > 0L)

        cache.clear()

        assertTrue(cache.load().isEmpty())
        assertEquals(0L, cache.sizeBytes())
        val index = File(
            RuntimeEnvironment.getApplication().filesDir,
            "server_asset_cache/index.json",
        )
        assertFalse(index.exists())
    }

    @Test
    fun theIndexIsWrittenAtomicallyAsASingleFile() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val cache = ServerAssetCache(context)
        cache.append(listOf(entry()))
        cache.append(listOf(entry(assetId = "a2", filename = "b.png", seenAt = 20L)))

        val folder = File(context.filesDir, "server_asset_cache")
        assertTrue(folder.isDirectory)
        val files = folder.listFiles().orEmpty().map { it.name }
        assertTrue(files.contains("index.json"))
        assertFalse(files.any { it.endsWith(".staged") })
        val index = File(folder, "index.json")
        assertTrue(index.isFile)
        assertTrue(index.length() > 0L)
        assertTrue(index.readText().contains("b.png"))
    }

    @Test
    fun entriesSurviveANewCacheInstanceOnTheSameFilesDirectory() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        ServerAssetCache(context).append(listOf(entry(assetId = "a1", filename = "a.png", seenAt = 42L)))

        val reopened = ServerAssetCache(context).load()
        assertEquals(1, reopened.size)
        assertEquals("a1", reopened.single().assetId)
        assertEquals(42L, reopened.single().seenAt)
    }
}
