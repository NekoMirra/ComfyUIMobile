package com.local.comfyuimobile.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisibleExecutionStateTest {
    @Test fun idleHasNoHighlight() {
        val idle = VisibleExecutionState.idle()
        assertTrue(idle.executingNodeIds.isEmpty())
        assertTrue(idle.nodeProgress.isEmpty())
    }

    @Test fun runningNodeIsHighlightedWithoutProgressValue() {
        val state = VisibleExecutionState.update("158", null)
        assertEquals(setOf("158"), state.executingNodeIds)
        assertTrue(state.nodeProgress.isEmpty())
    }

    @Test fun runningNodeCarriesItsProgress() {
        val state = VisibleExecutionState.update("158", 0.35f)
        assertEquals(setOf("158"), state.executingNodeIds)
        assertEquals(0.35f, state.nodeProgress.getValue("158"), 0f)
    }

    @Test fun previousNodeTurnsOffWhenTheNextOneStarts() {
        val first = VisibleExecutionState.update("158", 0.35f)
        val second = VisibleExecutionState.update("305", 0.6f)
        assertEquals(setOf("305"), second.executingNodeIds)
        assertEquals(0.6f, second.nodeProgress.getValue("305"), 0f)
        assertTrue(second.nodeProgress.containsKey("158").not())
        assertEquals(first.executingNodeIds.size, 1)
    }

    @Test fun progressIsClampedIntoUnitRange() {
        val high = VisibleExecutionState.update("1", 1.8f)
        assertEquals(1f, high.nodeProgress.getValue("1"), 0f)
        val low = VisibleExecutionState.update("1", -0.4f)
        assertEquals(0f, low.nodeProgress.getValue("1"), 0f)
    }

    @Test fun blankNodeIdKeepsTheCurrentStateUntouched() {
        val current = VisibleExecutionState.update("158", 0.35f)
        val blank = VisibleExecutionState.update("   ", 0.9f)
        assertEquals(current, blank)
    }
}
