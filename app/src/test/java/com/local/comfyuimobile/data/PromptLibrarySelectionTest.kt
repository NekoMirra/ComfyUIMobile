package com.local.comfyuimobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 提示词库在界面上的筛选、去重与置顶顺序。
 */
class PromptLibrarySelectionTest {
    private fun entry(
        id: String,
        text: String,
        category: String = PromptLibraryLogic.CATEGORY_HISTORY,
        pinned: Boolean = false,
        usedAt: Long = 0L,
    ) = PromptEntry(id, text, category, pinned, usedAt, useCount = 1)

    @Test
    fun theKeywordFiltersTheEntries() {
        val entries = listOf(
            entry("a", "a red dress", usedAt = 3L),
            entry("b", "a blue sky", usedAt = 2L),
        )

        val visible = PromptLibrarySelection.visible(entries, "dress", null)

        assertEquals(listOf("a"), visible.map { it.id })
    }

    @Test
    fun theCategoryFiltersTheEntries() {
        val entries = listOf(
            entry("a", "first", category = PromptLibraryLogic.CATEGORY_FAVORITE),
            entry("b", "second", category = PromptLibraryLogic.CATEGORY_HISTORY),
        )

        assertEquals(
            listOf("a"),
            PromptLibrarySelection.visible(entries, "", PromptLibraryLogic.CATEGORY_FAVORITE).map { it.id },
        )
        assertEquals(
            listOf("b"),
            PromptLibrarySelection.visible(entries, "", PromptLibraryLogic.CATEGORY_HISTORY).map { it.id },
        )
    }

    @Test
    fun pinnedEntriesComeFirst() {
        val entries = listOf(
            entry("old", "old", usedAt = 100L),
            entry("pinned", "pinned", pinned = true, usedAt = 1L),
        )

        assertEquals(listOf("pinned", "old"), PromptLibrarySelection.visible(entries, "", null).map { it.id })
    }

    @Test
    fun theSameTextIsStoredOnlyOnce() {
        val entries = PromptLibraryLogic.addFromHistory(emptyList(), "a red dress", now = 10L)
        val again = PromptLibraryLogic.addFromHistory(entries, "  a red dress  ", now = 20L)

        assertEquals(1, again.size)
        assertEquals("a red dress", again.first().text)
        assertEquals(2, again.first().useCount)
    }

    @Test
    fun addingFromHistoryPinsTheEntryToTheTop() {
        val entries = PromptLibraryLogic.addFromHistory(
            listOf(entry("other", "other", usedAt = 100L)),
            "a red dress",
            now = 10L,
        )

        assertEquals("a red dress", entries.first().text)
        assertTrue(entries.first().pinned)
    }

    @Test
    fun unpinningMovesTheEntryBackBelowThePinnedOnes() {
        val entries = PromptLibraryLogic.addFromHistory(emptyList(), "a red dress", now = 10L)
        val unpinned = PromptLibraryLogic.unpin(entries, entries.first().id)

        assertEquals(false, unpinned.first().pinned)
    }

    @Test
    fun theSelectedEntriesAreJoinedWithoutDuplicates() {
        val entries = listOf(
            entry("a", "first line"),
            entry("b", "second line"),
            entry("c", "first line"),
        )

        assertEquals("first line\nsecond line", PromptLibrarySelection.join(entries))
    }

    @Test
    fun joiningBlankEntriesProducesAnEmptyText() {
        assertEquals("", PromptLibrarySelection.join(listOf(entry("a", "   "))))
    }
}
