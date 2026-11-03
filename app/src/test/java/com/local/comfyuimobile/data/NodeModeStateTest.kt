package com.local.comfyuimobile.data

import com.local.comfyuimobile.bridge.NodeModePolicy
import com.local.comfyuimobile.model.NodeMode
import com.local.comfyuimobile.model.WorkflowNode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 节点模式的状态机，以及"写入工作副本 JSON 并在生成时透传"这条完整链路。
 */
class NodeModeStateTest {
    private val workflowJson = """
        {"nodes":[
           {"id":1,"type":"CheckpointLoaderSimple","mode":0,"widgets_values":["x.safetensors"]},
           {"id":2,"type":"KSampler","mode":0,"inputs":[{"name":"model","link":10}]}
         ],
         "links":[[10,1,0,2,0,"MODEL"]]}
    """.trimIndent()

    private fun node(id: String, type: String = "KSampler") = WorkflowNode(
        id = id,
        title = type,
        type = type,
        order = 0,
    )

    @Test
    fun switchingToBypassKeepsTheOtherNodesUntouched() {
        val change = NodeModeState.apply(
            current = emptyMap(),
            nodeId = "1",
            mode = NodeMode.BYPASS,
            knownNodeIds = setOf("1", "2"),
        )

        assertTrue(change is NodeModeChange.Applied)
        val modes = (change as NodeModeChange.Applied).modes
        assertEquals(mapOf("1" to NodeMode.BYPASS), modes)
    }

    @Test
    fun switchingToMuteIsKeptInTheState() {
        val change = NodeModeState.apply(
            current = mapOf("1" to NodeMode.BYPASS),
            nodeId = "2",
            mode = NodeMode.MUTE,
            knownNodeIds = setOf("1", "2"),
        )

        val modes = (change as NodeModeChange.Applied).modes
        assertEquals(NodeMode.BYPASS, modes["1"])
        assertEquals(NodeMode.MUTE, modes["2"])
    }

    @Test
    fun anUnknownNodeIsRejectedWithAReadableReason() {
        val change = NodeModeState.apply(
            current = emptyMap(),
            nodeId = "404",
            mode = NodeMode.BYPASS,
            knownNodeIds = setOf("1", "2"),
        )

        assertTrue(change is NodeModeChange.Rejected)
        val reason = (change as NodeModeChange.Rejected).reason
        assertTrue(reason.contains("404"))
        assertTrue(reason.contains("找不到节点"))
    }

    @Test
    fun aBlankNodeIdIsRejected() {
        val change = NodeModeState.apply(
            current = emptyMap(),
            nodeId = "",
            mode = NodeMode.BYPASS,
            knownNodeIds = setOf("1"),
        )

        assertTrue(change is NodeModeChange.Rejected)
    }

    @Test
    fun theModeIsWrittenIntoTheWorkingCopyAndPassedOnToThePrompt() {
        val nodes = listOf(node("1", "CheckpointLoaderSimple"), node("2"))
        val change = NodeModeState.apply(
            current = emptyMap(),
            nodeId = "2",
            mode = NodeMode.BYPASS,
            knownNodeIds = nodes.mapTo(HashSet()) { it.id },
        )
        val modes = (change as NodeModeChange.Applied).modes

        // 1) 写入工作副本 JSON：只有目标节点带上 mode，其余字段不变。
        val updatedJson = NodeModePolicy.applyToWorkflowJson(workflowJson, modes)
        val updated = JSONObject(updatedJson)
        val updatedNodes = updated.getJSONArray("nodes")
        assertEquals(4, updatedNodes.getJSONObject(1).getInt("mode"))
        assertEquals(0, updatedNodes.getJSONObject(0).getInt("mode"))
        assertEquals("CheckpointLoaderSimple", updatedNodes.getJSONObject(0).getString("type"))
        assertEquals("x.safetensors", updatedNodes.getJSONObject(0).getJSONArray("widgets_values").getString(0))
        assertEquals(1, updated.getJSONArray("links").length())

        // 2) 生成时透传：交给 bridge.buildPrompt 的模式表就是这张表。
        val modesForBuild = NodeModeState.modesForBuild(modes, nodes)
        assertEquals(mapOf("2" to NodeMode.BYPASS), modesForBuild)
    }

    @Test
    fun nodesThatNoLongerExistAreDroppedFromThePromptAndReported() {
        val nodes = listOf(node("2"))
        val modes = mapOf("1" to NodeMode.BYPASS, "2" to NodeMode.MUTE)

        val modesForBuild = NodeModeState.modesForBuild(modes, nodes)

        assertEquals(mapOf("2" to NodeMode.MUTE), modesForBuild)
        assertEquals(listOf("1"), NodeModeState.droppedNodeIds(modes, nodes))
    }

    @Test
    fun anIllegalModeFallsBackToAlways() {
        val change = NodeModeState.apply(
            current = emptyMap(),
            nodeId = "1",
            mode = NodeMode.fromWireValue(7),
            knownNodeIds = setOf("1"),
        )

        val modes = (change as NodeModeChange.Applied).modes
        assertEquals(NodeMode.ALWAYS, modes["1"])
    }
}
