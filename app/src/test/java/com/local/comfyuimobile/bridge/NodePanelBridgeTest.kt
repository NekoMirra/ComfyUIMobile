package com.local.comfyuimobile.bridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodePanelBridgeTest {
    @Test fun readyProbeRequiresBothGraphAndRegisteredNodeTypes() {
        val probe = NodePanelBridge.readyProbe()
        assertTrue(probe.contains("window.comfyAPI"))
        assertTrue(probe.contains("window.comfyAPI.app.app.graph"))
        assertTrue(probe.contains("window.LiteGraph"))
        assertTrue(probe.contains("registered_node_types"))
        assertTrue(probe.contains("Object.keys(window.LiteGraph.registered_node_types).length > 0"))
        assertTrue(probe.contains("'1' : '0'"))
    }

    @Test fun openScriptLocatesTheNodeAndWritesInitialValues() {
        val script = NodePanelBridge.buildOpenPanelScript("7", null, mapOf("pose" to "a.png"))
        assertTrue(script.contains("getNodeById"))
        assertTrue(script.contains("_nodes"))
        assertTrue(script.contains("\"7\""))
        assertTrue(script.contains("a.png"))
        assertTrue(script.contains("widget.setValue"))
        assertTrue(script.contains("widget.value = value"))
    }

    @Test fun openScriptSkipsButtonAndDomWidgets() {
        val script = NodePanelBridge.buildOpenPanelScript("7", null, mapOf("a" to "1", "b" to "2"))
        assertTrue(script.contains("widget.type === 'button'"))
        assertTrue(script.contains("widget.element"))
    }

    @Test fun openScriptMovesTheCanvasToTheNode() {
        val script = NodePanelBridge.buildOpenPanelScript("7", null, emptyMap())
        assertTrue(script.contains("canvas.ds"))
        assertTrue(script.contains("canvas.ds.offset"))
        assertTrue(script.contains("setDirty"))
        assertTrue(script.contains("canvas.draw"))
    }

    @Test fun openScriptTriggersTheRequestedPanelButton() {
        val script = NodePanelBridge.buildOpenPanelScript("7", "打开图库", emptyMap())
        assertTrue(script.contains("panelWidget.callback"))
        assertTrue(script.contains("panelWidget.click"))
        assertTrue(script.contains("节点上没有该面板"))
    }

    @Test fun openScriptRecordsABaselineForTheChangedValueDiff() {
        val script = NodePanelBridge.buildOpenPanelScript("7", null, emptyMap())
        assertTrue(script.contains("__comfyMobileNodePanelBaselines"))
        assertTrue(script.contains("changed"))
    }

    @Test fun readScriptSkipsButtonAndElementWidgets() {
        val script = NodePanelBridge.buildReadValuesScript("7")
        assertTrue(script.contains("widget.type === 'button'"))
        assertTrue(script.contains("widget.element"))
        assertTrue(script.contains("__comfyMobileNodePanelBaselines"))
        assertTrue(script.contains("changed"))
        assertTrue(script.contains("\"7\""))
    }

    @Test fun focusScriptOnlyMovesTheCanvas() {
        val script = NodePanelBridge.buildFocusNodeScript("7")
        assertTrue(script.contains("centerOnNode"))
        assertTrue(script.contains("canvas.ds"))
        assertFalse(script.contains("panelWidget"))
    }

    @Test fun everyScriptReportsFailuresExplicitly() {
        val scripts = listOf(
            NodePanelBridge.buildOpenPanelScript("7", null, emptyMap()),
            NodePanelBridge.buildReadValuesScript("7"),
            NodePanelBridge.buildFocusNodeScript("7"),
        )
        scripts.forEach { script ->
            assertTrue(script.contains("ok:false"))
            assertTrue(script.contains("error"))
            assertTrue(script.contains("JSON.stringify"))
        }
    }

    @Test fun illegalNodeIdNeverReachesTheScript() {
        val illegal = "7'; alert(1)//"
        val builders = listOf<(String) -> String>(
            { id -> NodePanelBridge.buildOpenPanelScript(id, null, emptyMap()) },
            { id -> NodePanelBridge.buildReadValuesScript(id) },
            { id -> NodePanelBridge.buildFocusNodeScript(id) },
        )
        builders.forEach { build ->
            val error = runCatching { build(illegal) }.exceptionOrNull()
            assertTrue(error is IllegalArgumentException)
            assertTrue(error!!.message!!.contains("非法节点 id"))
        }
    }

    @Test fun nodeIdWithSpacesOrQuotesIsRejected() {
        listOf("", " 7", "7 8", "\"7\"", "7;drop", "a/b").forEach { illegal ->
            val error = runCatching { NodePanelBridge.buildFocusNodeScript(illegal) }.exceptionOrNull()
            assertTrue("节点 id 应当被拒绝：$illegal", error is IllegalArgumentException)
        }
    }

    @Test fun nodeIdIsQuotedInsideTheScript() {
        val script = NodePanelBridge.buildFocusNodeScript("12")
        assertTrue(script.contains("\"12\""))
        assertFalse(script.contains("graph, 12)"))
    }
}
