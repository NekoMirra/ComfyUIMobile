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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.local.comfyuimobile.network.ServerAssetsClient
import com.local.comfyuimobile.network.ServerAssetsDisabledException
import com.local.comfyuimobile.network.ServerAssetSupport
import com.local.comfyuimobile.network.ServerCapabilities
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 60

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServerAssetPickerSheet(
    client: ServerAssetsClient,
    baseUrl: String,
    capabilities: ServerCapabilities? = null,
    onDismiss: () -> Unit,
    onPicked: (ServerAsset) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf(AssetTag.INPUT) }
    var assets by remember { mutableStateOf<List<ServerAsset>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableStateOf(0) }

    suspend fun loadPage(offset: Int): List<ServerAsset> {
        val page = client.listAssets(baseUrl, tag, query.trim().ifEmpty { null }, PAGE_SIZE, offset)
        total = page.total
        hasMore = page.hasMore
        return page.assets
    }

    LaunchedEffect(query, tag, reloadToken) {
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

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("选择服务器图片", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "关闭") }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("按名称搜索") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                SegmentedButton(
                    selected = tag == AssetTag.INPUT,
                    onClick = { tag = AssetTag.INPUT },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("输入") }
                SegmentedButton(
                    selected = tag == AssetTag.OUTPUT,
                    onClick = { tag = AssetTag.OUTPUT },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("输出") }
            }
            Text(
                text = if (loading) "加载中…" else "共 $total 项",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Box(Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 420.dp)) {
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
                        Text("没有符合条件的服务器图片", fontSize = 13.sp)
                    }

                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(96.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(assets, key = { it.id }) { asset ->
                            val type = asset.assetTag()?.toTagValue() ?: "input"
                            Column(
                                Modifier
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onPicked(asset) }
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
}
