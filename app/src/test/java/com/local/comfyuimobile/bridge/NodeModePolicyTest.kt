package com.local.comfyuimobile.bridge

import com.local.comfyuimobile.model.NodeMode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeModePolicyTest {
    private val workflow = """
        {"nodes":[
           {"id":1,"type":"CheckpointLoaderSimple","mode":0,"widgets_values":["x.safetensors"]},
           {"id":2,"type":"LoraLoader","mode":0,"inputs":[{"name":"model","link":10}]},
           {"id":"3","type":"KSampler","mode":0}
         ],
         "links":[[10,1,0,2,0,"MODEL"]]}
    """.trimIndent()

    @Test fun writesModeIntoTheMatchingNodeOnly() {
        val result = JSONObject(NodeModePolicy.applyToWorkflowJson(workflow, mapOf("2" to NodeMode.BYPASS)))
        val nodes = result.getJSONArray("nodes")
        assertEquals(4, nodes.getJSONObject(1).getInt("mode"))
        assertEquals(0, nodes.getJSONObject(0).getInt("mode"))
        assertEquals(0, nodes.getJSONObject(2).getInt("mode"))
        // 其余字段保持不变
        assertEquals("LoraLoader", nodes.getJSONObject(1).getString("type"))
        assertEquals(10, nodes.getJSONObject(1).getJSONArray("inputs").getJSONObject(0).getInt("link"))
        assertEquals("x.safetensors", nodes.getJSONObject(0).getJSONArray("widgets_values").getString(0))
        assertEquals(10, result.getJSONArray("links").getJSONArray(0).getInt(0))
    }

    @Test fun matchesNumericAndStringNodeIds() {
        val numeric = JSONObject(NodeModePolicy.applyToWorkflowJson(workflow, mapOf("1" to NodeMode.MUTE)))
        assertEquals(2, numeric.getJSONArray("nodes").getJSONObject(0).getInt("mode"))
        val string = JSONObject(NodeModePolicy.applyToWorkflowJson(workflow, mapOf("3" to NodeMode.MUTE)))
        assertEquals(2, string.getJSONArray("nodes").getJSONObject(2).getInt("mode"))
    }

    @Test fun everyModeHasItsOwnWireValue() {
        val result = JSONObject(
            NodeModePolicy.applyToWorkflowJson(
                workflow,
                mapOf("1" to NodeMode.ALWAYS, "2" to NodeMode.MUTE, "3" to NodeMode.BYPASS),
            ),
        )
        val nodes = result.getJSONArray("nodes")
        assertEquals(0, nodes.getJSONObject(0).getInt("mode"))
        assertEquals(2, nodes.getJSONObject(1).getInt("mode"))
        assertEquals(4, nodes.getJSONObject(2).getInt("mode"))
    }

    @Test fun unknownNodeIdIsReportedInsteadOfBeingIgnored() {
        val error = runCatching {
            NodeModePolicy.applyToWorkflowJson(workflow, mapOf("99" to NodeMode.MUTE))
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("99"))
    }

    @Test fun illegalModeFallsBackToAlways() {
        assertEquals(NodeMode.ALWAYS, NodeModePolicy.sanitize(0))
        assertEquals(NodeMode.MUTE, NodeModePolicy.sanitize(2))
        assertEquals(NodeMode.BYPASS, NodeModePolicy.sanitize(4))
        assertEquals(NodeMode.ALWAYS, NodeModePolicy.sanitize(1))
        assertEquals(NodeMode.ALWAYS, NodeModePolicy.sanitize(3))
        assertEquals(NodeMode.ALWAYS, NodeModePolicy.sanitize(-1))
        assertEquals(NodeMode.ALWAYS, NodeModePolicy.sanitize(99))
    }

    @Test fun collectModesReadsEveryNodeAndDefaultsToAlways() {
        val marked = NodeModePolicy.applyToWorkflowJson(
            workflow,
            mapOf("2" to NodeMode.MUTE, "3" to NodeMode.BYPASS),
        )
        val modes = NodeModePolicy.collectModesFromWorkflowJson(marked)
        assertEquals(NodeMode.ALWAYS, modes["1"])
        assertEquals(NodeMode.MUTE, modes["2"])
        assertEquals(NodeMode.BYPASS, modes["3"])
    }

    @Test fun collectModesTreatsMissingModeAsAlways() {
        val withoutMode = """{"nodes":[{"id":5,"type":"KSampler"}]}"""
        assertEquals(mapOf("5" to NodeMode.ALWAYS), NodeModePolicy.collectModesFromWorkflowJson(withoutMode))
    }

    @Test fun mergeModesKeepsOtherNodesAndNormalisesTheNewValue() {
        val current = mapOf("1" to NodeMode.MUTE, "2" to NodeMode.BYPASS)
        val merged = NodeModePolicy.mergeModes(current, "3", NodeModePolicy.sanitize(42))
        assertEquals(NodeMode.ALWAYS, merged["3"])
        assertEquals(NodeMode.MUTE, merged["1"])
        assertEquals(NodeMode.BYPASS, merged["2"])
        assertEquals(2, current.size)
        assertFalse(current.containsKey("3"))
    }

    @Test fun invalidWorkflowJsonRaisesAClearError() {
        val error = runCatching {
            NodeModePolicy.applyToWorkflowJson("not a json", mapOf("1" to NodeMode.MUTE))
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("工作流"))
    }

    @Test fun workflowWithoutNodesRaisesAClearError() {
        val error = runCatching {
            NodeModePolicy.applyToWorkflowJson("""{"links":[]}""", mapOf("1" to NodeMode.MUTE))
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("nodes"))
    }

    @Test fun modeScriptSetsTheModeOnTheCanvasNodeAndReportsMissingNodes() {
        val script = buildNodeModeScript(mapOf("7" to NodeMode.BYPASS))
        assertTrue(script.contains("getNodeById"))
        assertTrue(script.contains("node.mode = Number(payload[id])"))
        assertTrue(script.contains("找不到节点"))
        assertTrue(script.contains("ok:false"))
        assertTrue(script.contains("ok:true"))
    }
}
