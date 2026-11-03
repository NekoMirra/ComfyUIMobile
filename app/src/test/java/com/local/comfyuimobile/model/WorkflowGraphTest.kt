package com.local.comfyuimobile.model

import com.local.comfyuimobile.bridge.WorkflowManifestParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowGraphTest {
    private val rawWorkflow = """
        {"nodes":[{"id":"1","type":"KSampler"}],"links":[]}
    """.trimIndent()

    private val manifestWithGraph = """
        {"ok":true,
         "fields":[{"key":"1/steps","nodeId":"1","nodeTitle":"采样","nodeType":"KSampler",
                    "name":"steps","label":"步数","widgetType":"number","value":20,"widgetIndex":0,"nodeOrder":0}],
         "nodes":[
           {"id":"1","title":"采样","type":"KSampler","order":0,"isController":false,"isOutput":false,
            "posX":120.5,"posY":-40,"sizeX":260,"sizeY":120,
            "inputMarkers":[],"outputMarkers":[{"label":"1","type":"MODEL","color":"#fff","portName":"MODEL"}]},
           {"id":"2","title":"保存","type":"SaveImage","order":1,"isController":false,"isOutput":true,
            "posX":640,"posY":80,"sizeX":300,"sizeY":140}
         ],
         "links":[
           {"linkId":"7","originId":"1","originSlot":0,"targetId":"2","targetSlot":0},
           {"linkId":"8","originId":"2","originSlot":1,"targetId":"3","targetSlot":2}
         ]}
    """.trimIndent()

    @Test fun parsesNodeGeometry() {
        val manifest = WorkflowManifestParser.parse(rawWorkflow, manifestWithGraph)
        val sampler = manifest.nodes.first { it.id == "1" }
        assertEquals(120.5f, sampler.posX, 0.001f)
        assertEquals(-40f, sampler.posY, 0.001f)
        assertEquals(260f, sampler.sizeX, 0.001f)
        assertEquals(120f, sampler.sizeY, 0.001f)
    }

    @Test fun parsesLinks() {
        val manifest = WorkflowManifestParser.parse(rawWorkflow, manifestWithGraph)
        assertEquals(2, manifest.links.size)
        val first = manifest.links.first()
        assertEquals("7", first.linkId)
        assertEquals("1", first.originId)
        assertEquals(0, first.originSlot)
        assertEquals("2", first.targetId)
        assertEquals(0, first.targetSlot)
        assertEquals("3", manifest.links[1].targetId)
        assertEquals(2, manifest.links[1].targetSlot)
    }

    @Test fun legacyManifestWithoutLinksStillParses() {
        val legacy = """
            {"ok":true,
             "fields":[],
             "nodes":[{"id":"1","title":"采样","type":"KSampler","order":0}]}
        """.trimIndent()
        val manifest = WorkflowManifestParser.parse(rawWorkflow, legacy)
        assertEquals(1, manifest.nodes.size)
        assertTrue(manifest.links.isEmpty())
        assertEquals(0f, manifest.nodes.first().posX, 0.001f)
        assertEquals(0f, manifest.nodes.first().sizeY, 0.001f)
    }

    @Test fun missingLinkFieldsFallBackToEmptyStringsAndZeroSlots() {
        val partial = """{"ok":true,"fields":[],"nodes":[],"links":[{"linkId":"5"}]}"""
        val manifest = WorkflowManifestParser.parse(rawWorkflow, partial)
        assertEquals(1, manifest.links.size)
        assertEquals("5", manifest.links.first().linkId)
        assertEquals("", manifest.links.first().originId)
        assertEquals(0, manifest.links.first().originSlot)
        assertEquals("", manifest.links.first().targetId)
        assertEquals(0, manifest.links.first().targetSlot)
    }

    @Test fun keepsFieldsAndNodeOrder() {
        val manifest = WorkflowManifestParser.parse(rawWorkflow, manifestWithGraph)
        assertEquals(1, manifest.fields.size)
        assertEquals("1/steps", manifest.fields.first().key)
        assertEquals("20", manifest.fields.first().valueJson)
        assertEquals(listOf("1", "2"), manifest.nodes.map { it.id })
        assertTrue(manifest.nodes.first().outputMarkers.isNotEmpty())
        assertEquals("MODEL", manifest.nodes.first().outputMarkers.first().type)
    }

    @Test fun failedManifestRaises() {
        val error = runCatching {
            WorkflowManifestParser.parse(rawWorkflow, """{"ok":false,"error":"工作流解析失败"}""")
        }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
    }
}
