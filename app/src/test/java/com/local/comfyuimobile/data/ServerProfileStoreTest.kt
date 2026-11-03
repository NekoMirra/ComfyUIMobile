package com.local.comfyuimobile.data

import com.local.comfyuimobile.model.ServerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerProfileStoreTest {
    private val oldFormatJson = """
        [
          {"id":"one","name":"工作站","baseUrl":"http://192.168.1.20:8188","lastSeen":1700000000000,"comfyVersion":"0.43.2"},
          {"id":"two","name":"云端","baseUrl":"https://cloud.example.com","lastSeen":0,"comfyVersion":""}
        ]
    """.trimIndent()

    private val newFormatJson = """
        [
          {"id":"one","name":"工作站","baseUrl":"http://192.168.1.20:8188","lastSeen":1700000000000,"comfyVersion":"0.43.2","pinned":true,"lastConnectedAt":1700000000000},
          {"id":"two","name":"云端","baseUrl":"https://cloud.example.com","lastSeen":0,"comfyVersion":"","pinned":false,"lastConnectedAt":0}
        ]
    """.trimIndent()

    @Test fun oldEntriesDecodeWithSafeDefaults() {
        val stored = ServerProfileStore.decode(oldFormatJson)

        assertEquals(2, stored.size)
        assertEquals("工作站", stored[0].profile.name)
        assertEquals("http://192.168.1.20:8188", stored[0].profile.baseUrl)
        assertEquals(1700000000000L, stored[0].profile.lastSeen)
        assertEquals("0.43.2", stored[0].profile.comfyVersion)
        assertFalse(stored[0].pinned)
        assertEquals(1700000000000L, stored[0].lastConnectedAt)
        assertFalse(stored[1].pinned)
        assertEquals(0L, stored[1].lastConnectedAt)
    }

    @Test fun oldEntriesAreRewrittenInTheCurrentFormat() {
        val stored = ServerProfileStore.decode(oldFormatJson)
        val roundTrip = ServerProfileStore.decode(ServerProfileStore.encode(stored))

        assertEquals(stored.size, roundTrip.size)
        assertEquals(stored[0].profile.baseUrl, roundTrip[0].profile.baseUrl)
        assertEquals(stored[0].profile.name, roundTrip[0].profile.name)
        assertEquals(stored[0].lastConnectedAt, roundTrip[0].lastConnectedAt)
        assertFalse(roundTrip[0].pinned)
    }

    @Test fun newFormatKeepsThePinFlagAndTheConnectionTime() {
        val stored = ServerProfileStore.decode(newFormatJson)

        assertTrue(stored[0].pinned)
        assertEquals(1700000000000L, stored[0].lastConnectedAt)
        assertFalse(stored[1].pinned)
    }

    @Test fun brokenDataDoesNotThrow() {
        assertTrue(ServerProfileStore.decode("not json").isEmpty())
        assertTrue(ServerProfileStore.decode("").isEmpty())
        assertTrue(ServerProfileStore.decode("[{\"name\":\"缺少地址\"}]").isEmpty())
    }

    @Test fun renamingOnlyChangesTheName() {
        val stored = ServerProfileStore.decode(newFormatJson)
        val renamed = ServerProfileStore.rename(stored, "http://192.168.1.20:8188", " 客厅工作站 ")

        assertEquals("客厅工作站", renamed[0].profile.name)
        assertEquals("http://192.168.1.20:8188", renamed[0].profile.baseUrl)
        assertTrue(renamed[0].pinned)
        assertEquals(1700000000000L, renamed[0].lastConnectedAt)
        assertEquals("云端", renamed[1].profile.name)
    }

    @Test fun pinningMovesTheServerToTheTop() {
        val stored = ServerProfileStore.decode(oldFormatJson)
        val pinned = ServerProfileStore.setPinned(stored, "https://cloud.example.com", true)
        val ordered = ServerProfileStore.order(pinned)

        assertEquals("https://cloud.example.com", ordered[0].profile.baseUrl)
        assertTrue(ordered[0].pinned)
        assertEquals("http://192.168.1.20:8188", ordered[1].profile.baseUrl)
    }

    @Test fun aPinnedServerStaysAboveMoreRecentlyUsedOnes() {
        val stored = ServerProfileStore.decode(newFormatJson)
        val pinned = ServerProfileStore.setPinned(stored, "https://cloud.example.com", true)
        val ordered = ServerProfileStore.order(pinned)

        assertEquals("http://192.168.1.20:8188", ordered[0].profile.baseUrl)
        assertEquals("https://cloud.example.com", ordered[1].profile.baseUrl)
        assertTrue(ordered.all { it.pinned })
    }

    @Test fun unpinnedServersAreOrderedByTheLastConnection() {
        val stored = ServerProfileStore.decode(
            """
            [
              {"id":"a","name":"A","baseUrl":"http://a:8188","lastSeen":1,"comfyVersion":"","pinned":false,"lastConnectedAt":100},
              {"id":"b","name":"B","baseUrl":"http://b:8188","lastSeen":2,"comfyVersion":"","pinned":false,"lastConnectedAt":900},
              {"id":"c","name":"C","baseUrl":"http://c:8188","lastSeen":3,"comfyVersion":"","pinned":false,"lastConnectedAt":500}
            ]
            """.trimIndent(),
        )

        val ordered = ServerProfileStore.order(stored)

        assertEquals(listOf("http://b:8188", "http://c:8188", "http://a:8188"), ordered.map { it.profile.baseUrl })
    }

    @Test fun mergingKeepsThePinFlagAndMovesTheServerToTheFront() {
        val stored = ServerProfileStore.decode(newFormatJson)
        val merged = ServerProfileStore.merge(stored, ServerProfile("one", "工作站", "http://192.168.1.20:8188", 1700000009999L, "0.43.3"))

        assertEquals("http://192.168.1.20:8188", merged[0].profile.baseUrl)
        assertTrue(merged[0].pinned)
        assertEquals(1700000009999L, merged[0].lastConnectedAt)
        assertEquals("0.43.3", merged[0].profile.comfyVersion)
        assertEquals(2, merged.size)
    }

    @Test fun mergingAnUnknownServerCreatesAnEntryWithoutAPin() {
        val merged = ServerProfileStore.merge(emptyList(), ServerProfile("new", "新服务器", "http://10.0.0.5:8188", 42L, "0.43.2"))

        assertEquals(1, merged.size)
        assertFalse(merged[0].pinned)
        assertEquals(42L, merged[0].lastConnectedAt)
    }
}
