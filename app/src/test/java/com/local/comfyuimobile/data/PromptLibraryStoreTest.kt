package com.local.comfyuimobile.data

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

class PromptLibraryLogicTest {
    private fun entry(
        id: String,
        text: String,
        category: String = PromptLibraryLogic.CATEGORY_HISTORY,
        pinned: Boolean = false,
        usedAt: Long = 0L,
        useCount: Int = 1,
    ) = PromptEntry(id, text, category, pinned, usedAt, useCount)

    @Test
    fun reusingTheSameTextUpdatesTheEntryInsteadOfAddingASecondOne() {
        val first = PromptLibraryLogic.addFromHistory(emptyList(), " 猫 ", 10L)
        val second = PromptLibraryLogic.addFromHistory(first, "猫", 20L)

        assertEquals(1, second.size)
        assertEquals("猫", second.single().text)
        assertEquals(2, second.single().useCount)
        assertEquals(20L, second.single().usedAt)
    }

    @Test
    fun addingFromHistoryPinsTheEntryToTheTop() {
        val entries = PromptLibraryLogic.addFromHistory(emptyList(), "猫", 10L)
        val withSecond = PromptLibraryLogic.addFromHistory(entries, "狗", 20L)

        assertEquals(listOf("狗", "猫"), withSecond.map { it.text })
        assertTrue(withSecond.all { it.pinned })
    }

    @Test
    fun blankTextIsIgnored() {
        val entries = PromptLibraryLogic.addFromHistory(emptyList(), "   ", 10L)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun theHistoryCategoryIsCappedAtFiftyEntries() {
        val entries = (1..60).fold(emptyList<PromptEntry>()) { acc, index ->
            PromptLibraryLogic.addFromHistory(acc, "p$index", index.toLong())
        }
        val trimmed = PromptLibraryLogic.trimToCapacity(entries)

        val history = trimmed.filter { it.category == PromptLibraryLogic.CATEGORY_HISTORY }
        assertEquals(50, history.size)
        assertTrue(history.any { it.text == "p60" })
        assertFalse(history.any { it.text == "p1" })
    }

    @Test
    fun favoritesAndCustomCategoriesAreNeverEvicted() {
        val base = listOf(
            entry("fav", "收藏的提示词", PromptLibraryLogic.CATEGORY_FAVORITE, pinned = true, usedAt = 1L),
            entry("custom", "自定义提示词", "人物", pinned = true, usedAt = 2L),
        )
        val history = (1..60).map { index ->
            entry("h$index", "p$index", PromptLibraryLogic.CATEGORY_HISTORY, usedAt = index.toLong())
        }
        val trimmed = PromptLibraryLogic.trimToCapacity(history + base)

        assertTrue(trimmed.any { it.id == "fav" })
        assertTrue(trimmed.any { it.id == "custom" })
        assertEquals(50, trimmed.count { it.category == PromptLibraryLogic.CATEGORY_HISTORY })
        assertEquals(52, trimmed.size)
    }

    @Test
    fun pinningAndUnpinningChangesOnlyTheTargetEntry() {
        val entries = listOf(entry("a", "甲"), entry("b", "乙"))
        val pinned = PromptLibraryLogic.pin(entries, "b")
        assertFalse(pinned.first { it.id == "a" }.pinned)
        assertTrue(pinned.first { it.id == "b" }.pinned)

        val unpinned = PromptLibraryLogic.unpin(pinned, "b")
        assertFalse(unpinned.first { it.id == "b" }.pinned)
    }

    @Test
    fun settingTheCategoryMovesTheEntry() {
        val entries = listOf(entry("a", "甲"))
        val moved = PromptLibraryLogic.setCategory(entries, "a", " 人物 ")
        assertEquals("人物", moved.single().category)
    }

    @Test
    fun searchMatchesTheKeywordAndTheCategory() {
        val entries = listOf(
            entry("a", "赛博朋克城市", PromptLibraryLogic.CATEGORY_HISTORY),
            entry("b", "赛博朋克街道", PromptLibraryLogic.CATEGORY_FAVORITE),
            entry("c", "水彩猫", "动物"),
        )

        assertEquals(listOf("a", "b"), PromptLibraryLogic.search(entries, "赛博", null).map { it.id })
        assertEquals(listOf("b"), PromptLibraryLogic.search(entries, "", PromptLibraryLogic.CATEGORY_FAVORITE).map { it.id })
        assertEquals(listOf("c"), PromptLibraryLogic.search(entries, "水彩", null).map { it.id })
        assertTrue(PromptLibraryLogic.search(entries, "不存在", null).isEmpty())
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PromptLibraryStoreTest {
    private fun store(): PromptLibraryStore = PromptLibraryStore(RuntimeEnvironment.getApplication())

    @Test
    fun historyIsDeduplicatedAndPinnedOnTop() {
        val store = store()
        store.addFromHistory("猫")
        store.addFromHistory("狗")
        val entries = store.addFromHistory(" 猫 ")

        assertEquals(2, entries.size)
        assertEquals("猫", entries.first().text)
        assertTrue(entries.first().pinned)
        assertEquals(2, entries.first().useCount)
    }

    @Test
    fun theStoreKeepsAtMostFiftyHistoryEntries() {
        val store = store()
        val entries = (1..60).fold(emptyList<PromptEntry>()) { acc, index ->
            store.addFromHistory("p$index")
        }
        assertEquals(50, entries.size)
        assertTrue(entries.any { it.text == "p60" })
        assertFalse(entries.any { it.text == "p1" })
    }

    @Test
    fun favoritesSurviveTheHistoryCap() {
        val store = store()
        store.addFromHistory("收藏的提示词")
        val stored = store.load().single()
        store.setCategory(stored.id, PromptLibraryLogic.CATEGORY_FAVORITE)

        (1..60).fold(emptyList<PromptEntry>()) { acc, index -> store.addFromHistory("p$index") }

        val entries = store.load()
        assertTrue(entries.any { it.text == "收藏的提示词" })
        assertEquals(50, entries.count { it.category == PromptLibraryLogic.CATEGORY_HISTORY })
    }

    @Test
    fun entriesArePersistedAcrossStoreInstances() {
        val context = RuntimeEnvironment.getApplication()
        PromptLibraryStore(context).addFromHistory("猫")

        val reloaded = PromptLibraryStore(context).load()
        assertEquals(1, reloaded.size)
        assertEquals("猫", reloaded.single().text)
        assertTrue(reloaded.single().pinned)
    }

    @Test
    fun removingAnEntryDeletesItFromTheStore() {
        val store = store()
        val stored = store.addFromHistory("猫").single()
        store.remove(stored.id)
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun clearingRemovesEverything() {
        val store = store()
        store.addFromHistory("猫")
        store.clear()
        assertTrue(store.load().isEmpty())
    }
}
