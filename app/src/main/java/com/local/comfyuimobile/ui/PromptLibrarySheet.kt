package com.local.comfyuimobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.comfyuimobile.data.PromptEntry
import com.local.comfyuimobile.data.PromptHistory
import com.local.comfyuimobile.data.PromptLibraryLogic
import com.local.comfyuimobile.data.PromptLibraryStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PromptLibrarySheet(
    store: PromptLibraryStore,
    onDismiss: () -> Unit,
    onConfirmed: (List<PromptEntry>) -> Unit,
) {
    var entries by remember { mutableStateOf(store.load()) }
    var keyword by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        entries = store.load()
    }

    val visible = PromptLibraryLogic.search(entries, keyword, category)
    val categories = remember(entries) {
        listOf(PromptLibraryLogic.CATEGORY_HISTORY, PromptLibraryLogic.CATEGORY_FAVORITE) +
            entries.map { it.category }.filter { it.isNotBlank() }.distinct()
    }
    val recent = remember(entries) {
        entries.map { it.text }.fold(emptyList<String>()) { acc, text -> PromptHistory.add(acc, text) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("提示词库", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "关闭") }
            }
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                label = { Text("搜索提示词") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = category == null,
                    onClick = { category = null },
                    label = { Text("全部") },
                )
                categories.forEach { name ->
                    FilterChip(
                        selected = category == name,
                        onClick = { category = if (category == name) null else name },
                        label = { Text(name) },
                    )
                }
            }
            error?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (recent.isNotEmpty()) {
                Text(
                    text = "最近使用",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    recent.take(6).forEach { text ->
                        FilterChip(
                            selected = entries.firstOrNull { it.text == text }?.id in selected,
                            onClick = {
                                val entry = entries.firstOrNull { it.text == text } ?: return@FilterChip
                                selected = if (entry.id in selected) selected - entry.id else selected + entry.id
                            },
                            label = { Text(text.take(12), fontSize = 12.sp, maxLines = 1) },
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 400.dp)) {
                if (visible.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("提示词库还是空的", fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(Modifier.fillMaxWidth()) {
                        items(visible, key = { it.id }) { entry ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selected = if (entry.id in selected) selected - entry.id else selected + entry.id
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = entry.id in selected,
                                    onCheckedChange = { checked ->
                                        selected = if (checked) selected + entry.id else selected - entry.id
                                    },
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = entry.text,
                                        fontSize = 13.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${entry.category} · 用过 ${entry.useCount} 次",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = {
                                    try {
                                        entries = if (entry.pinned) store.unpin(entry.id) else store.pin(entry.id)
                                        error = null
                                    } catch (failure: Throwable) {
                                        error = failure.message ?: "更新收藏状态失败"
                                    }
                                }) {
                                    Icon(
                                        imageVector = if (entry.pinned) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = if (entry.pinned) "取消收藏" else "收藏",
                                    )
                                }
                                IconButton(onClick = {
                                    try {
                                        entries = store.remove(entry.id)
                                        selected = selected - entry.id
                                        error = null
                                    } catch (failure: Throwable) {
                                        error = failure.message ?: "删除提示词失败"
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除提示词")
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                TextButton(onClick = { selected = emptySet() }) { Text("清空选择") }
                Box(Modifier.weight(1f))
                TextButton(
                    enabled = selected.isNotEmpty(),
                    onClick = {
                        val picked = entries.filter { it.id in selected }
                        onConfirmed(picked)
                    },
                ) { Text("确认（${selected.size}）") }
            }
        }
    }
}
