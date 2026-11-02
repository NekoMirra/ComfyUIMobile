package com.local.comfyuimobile.bridge

import com.local.comfyuimobile.model.NodeMode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeModePolicyTest {
    private val prompt = """
        {"1":{"class_type":"KSampler","inputs":{"seed":42,"steps":20}},
         "2":{"class_type":"CLIPTextEncode","inputs":{"text":"hello"}},
         "3":{"class_type":"SaveImage","inputs":{"images":["2",0]}}}
    """.trimIndent()

    @Test fun writesModeOnlyForTheGivenNode() {
        val result = JSONObject(NodeModePolicy.applyToPrompt(prompt, mapOf("2" to NodeMode.BYPASS)))
        assertEquals(4, result.getJSONObject("2").getInt("mode"))
        assertEquals("KSampler", result.getJSONObject("1").getString("class_type"))
        assertEquals(42, result.getJSONObject("1").getJSONObject("inputs").getInt("seed"))
        assertEquals(20, result.getJSONObject("1").getJSONObject("inputs").getInt("steps"))
        assertEquals("hello", result.getJSONObject("2").getJSONObject("inputs").getString("text"))
        assertEquals("SaveImage", result.getJSONObject("3").getString("class_type"))
        assertEquals("2", result.getJSONObject("3").getJSONObject("inputs").getJSONArray("images").getString(0))
    }

    @Test fun everyModeHasItsOwnWireValue() {
        val result = JSONObject(
            NodeModePolicy.applyToPrompt(
                prompt,
                mapOf(
                    "1" to NodeMode.ALWAYS,
                    "2" to NodeMode.MUTE,
                    "3" to NodeMode.BYPASS,
                ),
            ),
        )
        assertEquals(0, result.getJSONObject("1").getInt("mode"))
        assertEquals(2, result.getJSONObject("2").getInt("mode"))
        assertEquals(4, result.getJSONObject("3").getInt("mode"))
    }

    @Test fun unknownNodeIdIsIgnoredInsteadOfBreakingThePrompt() {
        val result = JSONObject(NodeModePolicy.applyToPrompt(prompt, mapOf("99" to NodeMode.MUTE)))
        assertEquals(3, result.length())
        assertFalse(result.has("99"))
        assertFalse(result.getJSONObject("2").has("mode"))
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

    @Test fun illegalModeInTheMapIsNormalisedBeforeWriting() {
        val result = JSONObject(NodeModePolicy.applyToPrompt(prompt, mapOf("2" to NodeModePolicy.sanitize(7))))
        assertEquals(0, result.getJSONObject("2").getInt("mode"))
    }

    @Test fun collectBypassedNodesReturnsOnlyNonAlwaysNodes() {
        val marked = NodeModePolicy.applyToPrompt(
            prompt,
            mapOf("1" to NodeMode.ALWAYS, "2" to NodeMode.MUTE, "3" to NodeMode.BYPASS),
        )
        assertEquals(setOf("2", "3"), NodeModePolicy.collectBypassedNodes(marked))
        assertEquals(emptySet<String>(), NodeModePolicy.collectBypassedNodes(prompt))
    }

    @Test fun collectBypassedNodesReadsModesWrittenByComfyUi() {
        val fromServer = """{"1":{"class_type":"KSampler","mode":2},"2":{"class_type":"SaveImage","mode":0}}"""
        assertEquals(setOf("1"), NodeModePolicy.collectBypassedNodes(fromServer))
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

    @Test fun invalidPromptJsonRaisesAClearError() {
        val error = runCatching { NodeModePolicy.applyToPrompt("not a json", mapOf("1" to NodeMode.MUTE)) }
            .exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("prompt"))
    }

    @Test fun invalidPromptJsonAlsoFailsWhenCollecting() {
        val error = runCatching { NodeModePolicy.collectBypassedNodes("[1,2]") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
