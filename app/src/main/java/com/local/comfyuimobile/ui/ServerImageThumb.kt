package com.local.comfyuimobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.local.comfyuimobile.network.ServerAssetUrls

/**
 * Renders a server file thumbnail. The small thumbnail endpoint is tried first and the
 * webp preview is used when it is unavailable or fails.
 */
@Composable
internal fun ServerImageThumb(
    baseUrl: String,
    filename: String,
    subfolder: String,
    type: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    var stage by remember(baseUrl, filename, subfolder, type) { mutableStateOf(ThumbStage.THUMBNAIL) }
    val model = when (stage) {
        ThumbStage.THUMBNAIL -> ServerAssetUrls.thumbUrl(baseUrl, filename, subfolder, type)
        ThumbStage.PREVIEW -> ServerAssetUrls.webpPreviewUrl(baseUrl, filename, subfolder, type)
        ThumbStage.FAILED -> null
    }
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = {
                    stage = when (stage) {
                        ThumbStage.THUMBNAIL -> ThumbStage.PREVIEW
                        else -> ThumbStage.FAILED
                    }
                },
            )
        }
        if (stage == ThumbStage.FAILED) {
            Text(
                text = "加载失败",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}

private enum class ThumbStage { THUMBNAIL, PREVIEW, FAILED }
