package com.local.comfyuimobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.comfyuimobile.model.AssetTag
import com.local.comfyuimobile.model.ServerAsset
import com.local.comfyuimobile.model.ServerJob
import com.local.comfyuimobile.network.ServerAssetsClient
import com.local.comfyuimobile.network.ServerAssetsDisabledException
import com.local.comfyuimobile.network.ServerAssetSupport
import com.local.comfyuimobile.network.ServerCapabilities
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val ASSET_PAGE_SIZE = 60
private const val JOB_PAGE_SIZE = 40

@Composable
internal fun ServerGalleryView(
    client: ServerAssetsClient,
    baseUrl: String,
    modifier: Modifier = Modifier,
    capabilities: ServerCapabilities? = null,
    onOpenAsset: (ServerAsset) -> Unit,
    onOpenJobPreview: (ServerJob) -> Unit,
) {
    var segment by remember { mutableStateOf(GallerySegment.ASSETS) }
    Column(modifier.fillMaxWidth()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            SegmentedButton(
                selected = segment == GallerySegment.ASSETS,
                onClick = { segment = GallerySegment.ASSETS },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("服务器图片") }
            SegmentedButton(
                selected = segment == GallerySegment.JOBS,
                onClick = { segment = GallerySegment.JOBS },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("历史生成") }
        }
        when (segment) {
            GallerySegment.ASSETS -> ServerAssetGrid(client, baseUrl, capabilities, onOpenAsset)
            GallerySegment.JOBS -> ServerJobList(client, baseUrl, capabilities, onOpenJobPreview)
        }
    }
}

@Composable
private fun ServerAssetGrid(
    client: ServerAssetsClient,
    baseUrl: String,
    capabilities: ServerCapabilities?,
    onOpenAsset: (ServerAsset) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var assets by remember { mutableStateOf<List<ServerAsset>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableStateOf(0) }

    suspend fun loadPage(offset: Int): List<ServerAsset> {
        val page = client.listAssetsByTags(
            baseUrl = baseUrl,
            tags = listOf(AssetTag.INPUT, AssetTag.OUTPUT),
            nameContains = query.trim().ifEmpty { null },
            limit = ASSET_PAGE_SIZE,
            offset = offset,
        )
        total = page.total
        hasMore = page.hasMore
        return page.assets
    }

    LaunchedEffect(query, reloadToken) {
        loading = true
        error = null
        // 服务端没启用资源功能（或版本太旧）时直接给明确提示，不发注定失败的请求。
        val disabled = ServerAssetSupport.assetsDisabledMessage(capabilities)
        if (disabled != null) {
            error = disabled
            assets = emptyList()
            hasMore = false
            loading = false
            return@LaunchedEffect
        }
        try {
            assets = loadPage(0)
        } catch (failure: ServerAssetsDisabledException) {
            error = ServerAssetSupport.DISABLED_MESSAGE
            assets = emptyList()
            hasMore = false
        } catch (failure: Throwable) {
            error = failure.message ?: "读取服务器图片失败"
            assets = emptyList()
            hasMore = false
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("按名称过滤") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )
        Text(
            text = if (loading) "加载中…" else "共 $total 张",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Box(Modifier.fillMaxWidth().heightIn(min = 160.dp)) {
            when {
                loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                error != null -> Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    TextButton(onClick = { reloadToken += 1 }) { Text("重新加载") }
                }

                assets.isEmpty() -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("服务器上还没有图片", fontSize = 13.sp)
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(112.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(assets, key = { it.id }) { asset ->
                        val type = asset.assetTag()?.toTagValue() ?: "input"
                        Column(
                            Modifier
                                .padding(4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenAsset(asset) }
                        ) {
                            ServerImageThumb(
                                baseUrl = baseUrl,
                                filename = asset.fileName(),
                                subfolder = asset.subFolder(),
                                type = type,
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                contentDescription = asset.name,
                            )
                            Text(
                                text = asset.name,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }
        if (hasMore) {
            TextButton(
                enabled = !loadingMore,
                onClick = {
                    loadingMore = true
                    scope.launch {
                        try {
                            assets = assets + loadPage(assets.size)
                        } catch (failure: Throwable) {
                            error = failure.message ?: "加载更多失败"
                        } finally {
                            loadingMore = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) { Text(if (loadingMore) "加载中…" else "加载更多") }
        }
    }
}

@Composable
private fun ServerJobList(
    client: ServerAssetsClient,
    baseUrl: String,
    capabilities: ServerCapabilities?,
    onOpenJobPreview: (ServerJob) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var jobs by remember { mutableStateOf<List<ServerJob>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableStateOf(0) }
    var deletingId by remember { mutableStateOf<String?>(null) }

    suspend fun loadPage(offset: Int): List<ServerJob> {
        val page = client.listJobs(
            baseUrl = baseUrl,
            status = "pending,in_progress,completed,failed",
            limit = JOB_PAGE_SIZE,
            offset = offset,
        )
        total = page.total
        hasMore = page.hasMore
        return page.jobs
    }

    LaunchedEffect(reloadToken) {
        loading = true
        error = null
        // 历史生成同样依赖服务端的 /api/jobs，门槛与服务器图片一致。
        val disabled = ServerAssetSupport.assetsDisabledMessage(capabilities)
        if (disabled != null) {
            error = disabled
            jobs = emptyList()
            hasMore = false
            loading = false
            return@LaunchedEffect
        }
        try {
            jobs = loadPage(0)
        } catch (failure: ServerAssetsDisabledException) {
            error = ServerAssetSupport.DISABLED_MESSAGE
            jobs = emptyList()
            hasMore = false
        } catch (failure: Throwable) {
            error = failure.message ?: "读取历史生成失败"
            jobs = emptyList()
            hasMore = false
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = if (loading) "加载中…" else "共 $total 条",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Box(Modifier.fillMaxWidth().heightIn(min = 160.dp)) {
            when {
                loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                error != null -> Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    TextButton(onClick = { reloadToken += 1 }) { Text("重新加载") }
                }

                jobs.isEmpty() -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("服务器上没有历史生成记录", fontSize = 13.sp)
                }

                else -> LazyColumn(Modifier.fillMaxWidth()) {
                    items(jobs, key = { it.id }) { job ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            job.previewOutput?.let { preview ->
                                ServerImageThumb(
                                    baseUrl = baseUrl,
                                    filename = preview.filename,
                                    subfolder = preview.subfolder,
                                    type = preview.type,
                                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)),
                                    contentDescription = preview.filename,
                                    onClick = { onOpenJobPreview(job) },
                                )
                                Box(Modifier.size(12.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = job.previewOutput?.filename ?: job.id,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${job.status} · ${formatTime(job.createTime)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                enabled = deletingId != job.id,
                                onClick = {
                                    deletingId = job.id
                                    scope.launch {
                                        try {
                                            client.deleteHistoryEntry(baseUrl, job.id)
                                            jobs = jobs.filterNot { it.id == job.id }
                                            total = (total - 1).coerceAtLeast(0)
                                        } catch (failure: Throwable) {
                                            error = failure.message ?: "删除历史记录失败"
                                        } finally {
                                            deletingId = null
                                        }
                                    }
                                },
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "删除历史记录")
                            }
                        }
                    }
                }
            }
        }
        if (hasMore) {
            TextButton(
                enabled = !loadingMore,
                onClick = {
                    loadingMore = true
                    scope.launch {
                        try {
                            jobs = jobs + loadPage(jobs.size)
                        } catch (failure: Throwable) {
                            error = failure.message ?: "加载更多失败"
                        } finally {
                            loadingMore = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) { Text(if (loadingMore) "加载中…" else "加载更多") }
        }
    }
}

private enum class GallerySegment { ASSETS, JOBS }

private fun formatTime(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
