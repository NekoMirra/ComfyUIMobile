package com.local.comfyuimobile.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** A server asset the app has already seen, kept on disk because the server forgets on restart. */
data class ServerAssetCacheEntry(
    val assetId: String,
    val filename: String,
    val subfolder: String,
    val type: String,
    val promptId: String,
    val jobId: String,
    val width: Int,
    val height: Int,
    val seenAt: Long,
    val sizeBytes: Long,
) {
    /** Same identity as ResultMedia.stableKey(): the folder triple identifies the file. */
    fun stableKey(): String = listOf(type, subfolder, filename).joinToString("/")
}

class ServerAssetCacheException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

class ServerAssetCache(context: Context) {
    private val root = File(context.filesDir, "server_asset_cache")
    private val indexFile = File(root, "index.json")

    suspend fun load(): List<ServerAssetCacheEntry> = withContext(Dispatchers.IO) {
        mutex.withLock { readIndex().sortedByDescending { it.seenAt } }
    }

    suspend fun append(entries: List<ServerAssetCacheEntry>): List<ServerAssetCacheEntry> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val existing = readIndex().associateBy { it.stableKey() }.toMutableMap()
                entries.forEach { existing[it.stableKey()] = it }
                val merged = existing.values.sortedByDescending { it.seenAt }
                writeIndex(merged)
                merged
            }
        }

    suspend fun remove(key: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val records = readIndex()
            val kept = records.filterNot { it.stableKey() == key }
            if (kept.size == records.size) return@withLock false
            writeIndex(kept)
            true
        }
    }

    suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (root.exists()) root.deleteRecursively()
        }
    }

    suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        if (!root.exists()) 0L else root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun readIndex(): List<ServerAssetCacheEntry> {
        if (!indexFile.isFile) return emptyList()
        val text = try {
            indexFile.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (error: Throwable) {
            throw ServerAssetCacheException("读取服务器作品缓存失败：${indexFile.absolutePath}", error)
        }
        if (text.isBlank()) return emptyList()
        return try {
            val array = JSONArray(text)
            List(array.length()) { index ->
                val item = array.optJSONObject(index)
                    ?: throw ServerAssetCacheException("服务器作品缓存第 $index 项不是对象")
                decodeRecord(item)
            }
        } catch (error: ServerAssetCacheException) {
            throw error
        } catch (error: Throwable) {
            throw ServerAssetCacheException("服务器作品缓存已损坏：${indexFile.absolutePath}", error)
        }
    }

    /**
     * The index is written to a staging file that then replaces the previous one in a single
     * rename. `File.renameTo` cannot replace an existing file on Windows, which is where the
     * JVM tests run, so the replace-capable move is used instead of `AtomicFile`.
     */
    private fun writeIndex(records: List<ServerAssetCacheEntry>) {
        root.mkdirs()
        val staged = File(root, "index.json.staged")
        val payload = JSONArray().apply { records.forEach { put(encodeRecord(it)) } }.toString().toByteArray(Charsets.UTF_8)
        try {
            FileOutputStream(staged).use { stream ->
                stream.write(payload)
                stream.flush()
                stream.fd.sync()
            }
            Files.move(staged.toPath(), indexFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (error: Throwable) {
            staged.delete()
            throw ServerAssetCacheException("写入服务器作品缓存失败：${indexFile.absolutePath}", error)
        }
    }

    private fun encodeRecord(entry: ServerAssetCacheEntry): JSONObject = JSONObject()
        .put("assetId", entry.assetId)
        .put("filename", entry.filename)
        .put("subfolder", entry.subfolder)
        .put("type", entry.type)
        .put("promptId", entry.promptId)
        .put("jobId", entry.jobId)
        .put("width", entry.width)
        .put("height", entry.height)
        .put("seenAt", entry.seenAt)
        .put("sizeBytes", entry.sizeBytes)

    private fun decodeRecord(item: JSONObject): ServerAssetCacheEntry = ServerAssetCacheEntry(
        assetId = item.getString("assetId"),
        filename = item.getString("filename"),
        subfolder = item.getString("subfolder"),
        type = item.getString("type"),
        promptId = item.getString("promptId"),
        jobId = item.getString("jobId"),
        width = item.getInt("width"),
        height = item.getInt("height"),
        seenAt = item.getLong("seenAt"),
        sizeBytes = item.getLong("sizeBytes"),
    )

    companion object {
        private val mutex = Mutex()
    }
}
