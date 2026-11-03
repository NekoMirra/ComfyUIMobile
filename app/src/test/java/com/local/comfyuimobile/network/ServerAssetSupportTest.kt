package com.local.comfyuimobile.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerAssetSupportTest {
    private fun capabilities(assetsEnabled: Boolean) = ServerCapabilities(
        comfyVersion = "0.43.2",
        frontendVersion = "",
        templatesVersion = "",
        pythonVersion = "",
        pytorchVersion = "",
        ramTotal = 0L,
        ramFree = 0L,
        deviceName = "",
        vramTotal = 0L,
        vramFree = 0L,
        assetsEnabled = assetsEnabled,
        supportsPreviewMetadata = false,
        maxUploadSize = 0L,
        queueRunningCount = 0,
        queuePendingCount = 0,
        checkedAt = 0L,
    )

    @Test fun enabledServerNeedsNoWarning() {
        assertNull(ServerAssetSupport.assetsDisabledMessage(capabilities(assetsEnabled = true)))
    }

    @Test fun disabledServerGetsTheSharedDowngradeMessage() {
        assertEquals(
            "服务器未启用资源功能，无法读取服务器图片",
            ServerAssetSupport.assetsDisabledMessage(capabilities(assetsEnabled = false)),
        )
    }

    @Test fun unknownCapabilityIsNotTreatedAsDisabled() {
        assertNull(ServerAssetSupport.assetsDisabledMessage(null))
    }

    @Test fun sharedMessageConstantMatchesTheDowngradeText() {
        assertEquals("服务器未启用资源功能，无法读取服务器图片", ServerAssetSupport.DISABLED_MESSAGE)
    }
}
