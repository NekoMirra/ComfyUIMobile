package com.local.comfyuimobile.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** One stored prompt. [category] is a free-form label; [pinned] marks it as pinned to the top. */
data class PromptEntry(
    val id: String,
    val text: String,
    val category: String,
    val pinned: Boolean,
    val usedAt: Long,
    val useCount: Int,
)

/** Pure prompt-library logic, kept free of Android types so it can be unit tested directly. */
object PromptLibraryLogic {
    const val CATEGORY_HISTORY = "历史"
    const val CATEGORY_FAVORITE = "收藏"
    const val MAX_HISTORY = PromptHistory.MAX_SIZE

    fun normalize(text: String): String = text.trim()

    fun idOf(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(normalize(text).toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    /** Reuses an entry with the same trimmed text, otherwise creates one; the result is pinned on top. */
    fun addFromHistory(entries: List<PromptEntry>, text: String, now: Long): List<PromptEntry> {
        val normalized = normalize(text)
        if (normalized.isEmpty()) return ordered(entries)
        val existing = entries.firstOrNull { normalize(it.text) == normalized }
        val updated = if (existing == null) {
            PromptEntry(
                id = idOf(normalized),
                text = normalized,
                category = CATEGORY_HISTORY,
                pinned = true,
                usedAt = now,
                useCount = 1,
            )
        } else {
            existing.copy(pinned = true, usedAt = now, useCount = existing.useCount + 1)
        }
        return ordered(entries.filterNot { it.id == updated.id } + updated)
    }

    fun pin(entries: List<PromptEntry>, id: String): List<PromptEntry> =
        entries.map { if (it.id == id) it.copy(pinned = true) else it }

    fun unpin(entries: List<PromptEntry>, id: String): List<PromptEntry> =
        entries.map { if (it.id == id) it.copy(pinned = false) else it }

    fun setCategory(entries: List<PromptEntry>, id: String, category: String): List<PromptEntry> =
        entries.map { if (it.id == id) it.copy(category = category.trim().ifEmpty { CATEGORY_HISTORY }) else it }

    fun remove(entries: List<PromptEntry>, id: String): List<PromptEntry> =
        entries.filterNot { it.id == id }

    /** Pinned entries first, then the most recently used ones. */
    fun ordered(entries: List<PromptEntry>): List<PromptEntry> =
        entries.sortedWith(compareByDescending<PromptEntry> { it.pinned }.thenByDescending { it.usedAt }.thenBy { it.id })

    /**
     * Only the history category is capped; favorites and custom categories are never evicted.
     * Unpinned history entries are dropped first, oldest first.
     */
    fun trimToCapacity(entries: List<PromptEntry>, maxHistory: Int = MAX_HISTORY): List<PromptEntry> {
        val history = entries.filter { it.category == CATEGORY_HISTORY }
        if (history.size <= maxHistory) return entries
        val overflow = history.size - maxHistory
        val evicted = history.sortedWith(compareBy<PromptEntry> { it.pinned }.thenBy { it.usedAt })
            .take(overflow)
            .map { it.id }
            .toSet()
        return entries.filter { it.category != CATEGORY_HISTORY || it.id !in evicted }
    }

    fun search(entries: List<PromptEntry>, keyword: String, category: String?): List<PromptEntry> {
        val needle = normalize(keyword)
        return entries.filter { entry ->
            val matchesCategory = category == null || category.isBlank() || entry.category == category
            val matchesKeyword = needle.isEmpty() || entry.text.contains(needle, ignoreCase = true)
            matchesCategory && matchesKeyword
        }
    }
}

class PromptLibraryStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): List<PromptEntry> {
        val raw = preferences.getString(KEY_ENTRIES, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        val array = try {
            JSONArray(raw)
        } catch (error: Throwable) {
            throw PromptLibraryException("提示词库数据已损坏，无法读取", error)
        }
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index)
                    ?: throw PromptLibraryException("提示词库第 $index 项不是对象")
                add(
                    PromptEntry(
                        id = item.getString("id"),
                        text = item.getString("text"),
                        category = item.getString("category"),
                        pinned = item.getBoolean("pinned"),
                        usedAt = item.getLong("usedAt"),
                        useCount = item.getInt("useCount"),
                    )
                )
            }
        }
    }

    fun addFromHistory(text: String): List<PromptEntry> {
        val updated = PromptLibraryLogic.addFromHistory(load(), text, System.currentTimeMillis())
        return persist(updated)
    }

    fun pin(id: String): List<PromptEntry> = persist(PromptLibraryLogic.pin(load(), id))

    fun unpin(id: String): List<PromptEntry> = persist(PromptLibraryLogic.unpin(load(), id))

    fun setCategory(id: String, category: String): List<PromptEntry> =
        persist(PromptLibraryLogic.setCategory(load(), id, category))

    fun remove(id: String): List<PromptEntry> = persist(PromptLibraryLogic.remove(load(), id))

    fun clear(): Unit {
        preferences.edit().remove(KEY_ENTRIES).apply()
    }

    private fun persist(entries: List<PromptEntry>): List<PromptEntry> {
        val trimmed = PromptLibraryLogic.trimToCapacity(entries)
        val array = JSONArray()
        trimmed.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("text", entry.text)
                    .put("category", entry.category)
                    .put("pinned", entry.pinned)
                    .put("usedAt", entry.usedAt)
                    .put("useCount", entry.useCount)
            )
        }
        preferences.edit().putString(KEY_ENTRIES, array.toString()).apply()
        return trimmed
    }

    companion object {
        const val PREFERENCES_NAME = "comfy_mobile_prompt_library"
        private const val KEY_ENTRIES = "entries"
    }
}

class PromptLibraryException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)
